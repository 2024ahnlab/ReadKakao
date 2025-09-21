package com.readkakaotalk.app.model;

import android.content.Context;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.IntBuffer;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.HashMap;
import java.util.Map;

import ai.onnxruntime.OnnxJavaType;
import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OnnxValue;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import ai.onnxruntime.TensorInfo;
import ai.onnxruntime.NodeInfo;

public class OnnxModelManager implements AutoCloseable {
    private final OrtEnvironment env;
    private OrtSession session;
    private Map<Integer,String> id2label;
    private boolean expectsStringInput = false; // 문자열 입력 모델 여부
    private int seqLen = 64;                    // 정수 입력 모델일 때 길이
    private boolean hasAttentionMask = true;    // attention_mask 존재 여부

    public OnnxModelManager(Context ctx,
                            String onnxAssetName,
                            String labelMapAssetName) throws IOException, OrtException {
        env = OrtEnvironment.getEnvironment();
        try (OrtSession.SessionOptions opt = new OrtSession.SessionOptions()) {
            // opt.registerOrtExtensions(); // extensions AAR 씀 + 필요할 때만 사용
            session = env.createSession(readAssetToBytes(ctx, onnxAssetName), opt);
        }
        loadLabelMap(ctx, labelMapAssetName);
        inspectInputs();
    }

    private void inspectInputs() throws OrtException {
        Map<String, NodeInfo> inputs = session.getInputInfo();
        for (NodeInfo ni : inputs.values()) {
            TensorInfo ti = (TensorInfo) ni.getInfo();
            if (ti.type == OnnxJavaType.STRING) {
                expectsStringInput = true;
            } else if (ti.type == OnnxJavaType.INT32) {
                long[] shape = ti.getShape();
                if (shape.length >= 2 && shape[1] > 0) {
                    seqLen = (int) shape[1]; // [1, seq_len]
                }
            }
        }
        hasAttentionMask = inputs.size() >= 2;
    }

    private void loadLabelMap(Context ctx, String assetName) throws IOException {
        try (InputStream is = ctx.getAssets().open(assetName);
             InputStreamReader r = new InputStreamReader(is)) {
            JsonObject obj = JsonParser.parseReader(r).getAsJsonObject();
            id2label = new HashMap<>();
            for (Map.Entry<String, JsonElement> e : obj.entrySet()) {
                id2label.put(Integer.parseInt(e.getKey()), e.getValue().getAsString());
            }
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
        String inputName = firstInputName();     // ← 입력명 가져오기
        feeds.put(inputName, textTensor);

        try (OrtSession.Result result = session.run(feeds)) {
            float[] probs = extractFirstRow(result);
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
        OnnxTensor idsTensor = OnnxTensor.createTensor(env, IntBuffer.wrap(inputIds), shape);

        Map<String, OnnxTensor> feeds = new LinkedHashMap<>();
        Iterator<String> it = session.getInputInfo().keySet().iterator();
        String idsName = it.next();
        feeds.put(idsName, idsTensor);

        OnnxTensor maskTensor = null;
        if (hasAttentionMask) {
            int[] mask = (attentionMaskOpt != null) ? attentionMaskOpt : ones(seqLen);
            maskTensor = OnnxTensor.createTensor(env, IntBuffer.wrap(mask), shape);
            String maskName = it.next();
            feeds.put(maskName, maskTensor);
        }

        try (OrtSession.Result result = session.run(feeds)) {
            float[] probs = extractFirstRow(result);
            return toPrediction(probs);
        } finally {
            idsTensor.close();
            if (maskTensor != null) maskTensor.close();
        }
    }

    private String firstInputName() throws IOException, OrtException {
        return session.getInputInfo().keySet().iterator().next();
    }

    private static int[] ones(int n) { int[] a = new int[n]; Arrays.fill(a, 1); return a; }

    private float[] extractFirstRow(OrtSession.Result result) throws OrtException {
        // 출력 하나, [1, num_labels] 가정
        OnnxValue v = result.get(0);
        float[][] arr = (float[][]) ((OnnxTensor) v).getValue();
        return arr[0];
    }

    private Prediction toPrediction(float[] probs) {
        int argmax = 0; float best = probs[0];
        for (int i = 1; i < probs.length; i++) if (probs[i] > best) { best = probs[i]; argmax = i; }
        String label = id2label.getOrDefault(argmax, "UNK");
        return new Prediction(label, probs, argmax);
    }

    @Override public void close() throws OrtException {
        if (session != null) session.close();
        if (env != null) env.close();
    }

    public static final class Prediction {
        public final String label;
        public final float[] probs;
        public final int index;
        public Prediction(String label, float[] probs, int index) {
            this.label = label; this.probs = probs; this.index = index;
        }
    }
}
