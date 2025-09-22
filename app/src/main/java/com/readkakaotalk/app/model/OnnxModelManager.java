package com.readkakaotalk.app.model;

import android.content.Context;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.HashMap;
import java.util.Map;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OnnxValue;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import ai.onnxruntime.TensorInfo;
import ai.onnxruntime.NodeInfo;
import ai.onnxruntime.OnnxJavaType;

public class OnnxModelManager implements AutoCloseable {
    private final OrtEnvironment env;
    private OrtSession session;
    private String[] id2label;   // ← 배열로 변경
    private boolean expectsStringInput = false; // 문자열 입력 모델 여부
    private int seqLen = 64;                    // 정수 입력 모델일 때 길이
    private boolean hasAttentionMask = true;    // attention_mask 존재 여부
    public int getSeqLen() { return seqLen; }
    public void setSeqLen(int v) { this.seqLen = v; }            // <-- 추가
    public boolean hasAttentionMask() { return hasAttentionMask; } // <-- 추가
    public void setHasAttentionMask(boolean v) { this.hasAttentionMask = v; }

    public OnnxModelManager(Context ctx,
                            String onnxAssetName,
                            String labelMapAssetName) throws IOException, OrtException {
        env = OrtEnvironment.getEnvironment();
        try (OrtSession.SessionOptions opt = new OrtSession.SessionOptions()) {
            session = env.createSession(readAssetToBytes(ctx, onnxAssetName), opt);
        }
        loadLabelMap(ctx, labelMapAssetName);
        inspectInputs();
    }

    // OnnxModelManager.java 내 유틸 추가
    private static float[] softmax(float[] logits) {
        double max = Double.NEGATIVE_INFINITY;
        for (float v : logits) max = Math.max(max, v);
        double sum = 0.0;
        double[] exp = new double[logits.length];
        for (int i = 0; i < logits.length; i++) { exp[i] = Math.exp(logits[i] - max); sum += exp[i]; }
        float[] probs = new float[logits.length];
        for (int i = 0; i < logits.length; i++) probs[i] = (float)(exp[i] / sum);
        return probs;
    }
    private static float sigmoid(float x) { return (float)(1.0 / (1.0 + Math.exp(-x))); }

    private void inspectInputs() throws OrtException {
        Map<String, NodeInfo> inputs = session.getInputInfo();
        // 1) 입력 타입/shape로 seqLen 추정
        for (NodeInfo ni : inputs.values()) {
            TensorInfo ti = (TensorInfo) ni.getInfo();
            if (ti.type == OnnxJavaType.STRING) {
                expectsStringInput = true;
            } else if (ti.type == OnnxJavaType.INT32 || ti.type == OnnxJavaType.INT64) {
                long[] shape = ti.getShape();
                // ['batch','seq'] 같은 동적이면 shape[1]<=0 로 들어옴
                if (shape.length >= 2 && shape[1] > 0) {
                    seqLen = (int) shape[1];                    // 고정 길이일 때만 갱신
                }
            }
        }
        // 2) 이름 기반으로 attention_mask 유무 판별 (기존 size>=2 방식 보완)
        boolean foundMask = false;
        for (String name : inputs.keySet()) {
            String n = name.toLowerCase();
            if (n.contains("attention_mask") || n.endsWith("mask")) {
                foundMask = true; break;
            }
        }
        hasAttentionMask = foundMask; // 입력이 2개여도 이름이 mask가 아니면 false
    }

    private void loadLabelMap(Context ctx, String assetName) throws IOException {
        try (InputStream is = ctx.getAssets().open(assetName);
             InputStreamReader r = new InputStreamReader(is)) {
            JsonObject obj = JsonParser.parseReader(r).getAsJsonObject();
            // HashMap → 인덱스 배열로 변환
            id2label = buildId2Label(obj);
        }
    }

    // 새 헬퍼 추가 (클래스 내부 하단 아무 곳)
    private static String[] buildId2Label(JsonObject m) {
        int maxIdx = -1;
        for (Map.Entry<String, JsonElement> e : m.entrySet()) {
            int k = Integer.parseInt(e.getKey());
            if (k > maxIdx) maxIdx = k;
        }
        String[] id2label = new String[maxIdx + 1];
        for (Map.Entry<String, JsonElement> e : m.entrySet()) {
            int idx = Integer.parseInt(e.getKey());
            id2label[idx] = e.getValue().getAsString();
        }
        return id2label;
    }

    // 클래스 내부 아무 곳
    private static float[] toProbs(float[] logits) {
        if (logits.length == 1) {
            float p1 = sigmoid(logits[0]);          // 바이너리-시그모이드
            return new float[]{ 1.0f - p1, p1 };    // [normal, fraud]
        } else {
            return softmax(logits);                  // 다중 or 이진(2로짓) 공통
        }
    }

    private static byte[] readAssetToBytes(Context ctx, String name) throws IOException {
        try (InputStream is = ctx.getAssets().open(name);
             ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            byte[] buf = new byte[8192]; int n;
            while ((n = is.read(buf)) > 0) bos.write(buf, 0, n);
            return bos.toByteArray();
        }
    }

    // ===== 문자열 입력 모델(토크나이저 포함) =====
    public Prediction predictText(String text) throws OrtException, IOException {
        if (!expectsStringInput) throw new IllegalStateException("Model expects token IDs, not String.");

        OnnxTensor textTensor = OnnxTensor.createTensor(env, new String[]{ text });

        Map<String, OnnxTensor> feeds = new LinkedHashMap<>();
        String inputName = firstInputName();
        feeds.put(inputName, textTensor);

        try (OrtSession.Result result = session.run(feeds)) {
            float[] logits = extractFirstRow(result);   // ← logits 취득
            float[] probs  = toProbs(logits);           // ← 확률로 정규화
            return toPrediction(probs);
        } finally {
            textTensor.close();
        }
    }

    // ===== 정수 텐서 입력 모델(외부 토크나이저 필요) =====
    public Prediction predictIds(int[] inputIds, int[] attentionMaskOpt) throws OrtException {
        if (expectsStringInput) throw new IllegalStateException("Model expects String, not token IDs.");
        if (inputIds.length != seqLen) throw new IllegalArgumentException("seq_len mismatch");

        long[] shape = new long[]{1, seqLen};
        long[] ids64 = Arrays.stream(inputIds).asLongStream().toArray();
        long[][] ids2d = new long[1][seqLen];
        System.arraycopy(ids64, 0, ids2d[0], 0, seqLen);
        OnnxTensor idsTensor = OnnxTensor.createTensor(env, ids2d);

        Map<String, OnnxTensor> feeds = new LinkedHashMap<>();
        Iterator<String> it = session.getInputInfo().keySet().iterator();
        String idsName = it.next();
        feeds.put(idsName, idsTensor);

        OnnxTensor maskTensor = null;
        if (hasAttentionMask) {
            int[] mask = (attentionMaskOpt != null) ? attentionMaskOpt : ones(seqLen);
            long[] mask64 = Arrays.stream(mask).asLongStream().toArray();
            long[][] mask2d = new long[1][seqLen];
            System.arraycopy(mask64, 0, mask2d[0], 0, seqLen);
            maskTensor = OnnxTensor.createTensor(env, mask2d);
            String maskName = it.next();
            feeds.put(maskName, maskTensor);
        }

        try (OrtSession.Result result = session.run(feeds)) {
            float[] logits = extractFirstRow(result);   // ← logits
            float[] probs  = toProbs(logits);           // ← 확률
            return toPrediction(probs);
        } finally {
            idsTensor.close();
            if (maskTensor != null) maskTensor.close();
        }
    }

    private String firstInputName() throws ai.onnxruntime.OrtException {
        return session.getInputInfo().keySet().iterator().next();
    }

    private static int[] ones(int n) {
        int[] a = new int[n];
        Arrays.fill(a, 1);
        return a;
    }

    private float[] extractFirstRow(OrtSession.Result result) throws OrtException {
        OnnxValue v = result.get(0);
        float[][] arr = (float[][]) ((OnnxTensor) v).getValue();
        return arr[0];
    }

    private Prediction toPrediction(float[] probs) {
        int argmax = 0;
        float best = probs[0];
        for (int i = 1; i < probs.length; i++) {
            if (probs[i] > best) { best = probs[i]; argmax = i; }
        }
        String label = (id2label != null && argmax < id2label.length && id2label[argmax] != null)
                ? id2label[argmax] : "UNK";
        return new Prediction(label, probs, argmax);
    }

//    private Prediction toPrediction(float[] probs) {
//        int argmin = 0; // 가장 작은 값의 인덱스를 저장할 변수 (argmin)
//        float lowestProb = probs[0]; // 현재까지 가장 작은 확률값
//
//        for (int i = 1; i < probs.length; i++) {
//            // 만약 현재 확률이 이전에 찾은 가장 작은 확률보다 '작다면'
//            if (probs[i] < lowestProb) {
//                lowestProb = probs[i]; // 가장 작은 값을 갱신하고
//                argmin = i;          // 해당 인덱스를 저장
//            }
//        }
//        // 가장 확률이 낮은 감정의 라벨을 가져옴
//        String label = id2label.getOrDefault(argmin, "UNK");
//        return new Prediction(label, probs, argmin);
//    }

    @Override public void close() throws OrtException {
        if (session != null) session.close();
        if (env != null) env.close();
    }

    public static final class Prediction {
        public final String label;
        public final float[] probs;
        public final int index;
        public Prediction(String label, float[] probs, int index) {
            this.label = label;
            this.probs = probs;
            this.index = index;
        }
    }
}
