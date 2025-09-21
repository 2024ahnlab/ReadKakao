package com.readkakaotalk.app.activity;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceManager;

import com.readkakaotalk.app.R;
import com.readkakaotalk.app.model.OnnxModelManager;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";

    // 설정 키
    public static final String KEY_USE_EMOTION_MODEL = "use_emotion_model";
    public static final String KEY_THRESHOLD = "fraud_threshold";

    private OnnxModelManager model;
    private SharedPreferences prefs;

    // UI
    private EditText inputEditText;
    private Button analyzeButton;
    private TextView scoreText;
    private TextView labelText;

    // 모델/레이블 파일명 (assets/)
    private String modelAssetName;
    private String labelMapAssetName;

    // 기본값
    private static final float DEFAULT_THRESHOLD = 0.6f; // 필요시 조정
    private static final int DEFAULT_SEQ_LEN = 64;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main); // TODO(id) 네 레이아웃으로 유지

        // UI 바인딩
        inputEditText = findViewById(R.id.editTextMessage);   // TODO(id)
        analyzeButton = findViewById(R.id.buttonAnalyze);     // TODO(id)
        scoreText = findViewById(R.id.textViewScore);         // TODO(id)
        labelText = findViewById(R.id.textViewLabel);         // TODO(id)

        prefs = PreferenceManager.getDefaultSharedPreferences(this);
        pickModelFromSettings(this);

        try {
            model = new OnnxModelManager(
                    this,
                    modelAssetName,
                    labelMapAssetName
            );
            Log.i(TAG, "ONNX model loaded: " + modelAssetName);
        } catch (Exception e) {
            Log.e(TAG, "Failed to load ONNX model", e);
            showResult("load error", "model load failed");
            return;
        }

        analyzeButton.setOnClickListener(v -> {
            String text = inputEditText.getText().toString();
            if (TextUtils.isEmpty(text)) {
                showResult("0.0", "EMPTY");
                return;
            }
            analyze(text);
        });
    }

    private void analyze(String text) {
        try {
            OnnxModelManager.Prediction p;

            // 먼저 문자열 입력 가능한 모델이면 그대로 호출
            try {
                p = model.predictText(text);
            } catch (IllegalStateException notString) {
                // 정수 입력 모델인 경우: 간이 토크나이즈(정식 SentencePiece 붙이기 전 임시)
                int[] ids = tokenizeFallback(text, DEFAULT_SEQ_LEN);
                int[] mask = buildAttentionMask(ids);
                p = model.predictIds(ids, mask);
            }

            // 사기탐지(2클래스 가정)일 때는 index 1 확률을 threshold 비교
            float threshold = prefs.getFloat(KEY_THRESHOLD, DEFAULT_THRESHOLD);
            float fraudScore = takeFraudScore(p);
            boolean isFraud = fraudScore >= threshold;

            scoreText.setText(String.format("score: %.4f (th=%.3f)", fraudScore, threshold));
            labelText.setText(p.label + (isFraud ? "  [ALERT]" : ""));

            Log.d(TAG, "pred index=" + p.index + " label=" + p.label + " score=" + fraudScore);
        } catch (Exception e) {
            Log.e(TAG, "analyze failed", e);
            showResult("error", e.getClass().getSimpleName());
        }
    }

    private void pickModelFromSettings(Context ctx) {
        boolean useEmotion = prefs.getBoolean(KEY_USE_EMOTION_MODEL, false);
        if (useEmotion) {
            // 감정 44클래스
            modelAssetName = "distilkobert_emotion_sc.int8.onnx";
            labelMapAssetName = "label_map_emotion.json";
        } else {
            // 이진 사기탐지
            modelAssetName = "distilkobert_sc.int8.onnx";
            labelMapAssetName = "label_map.json";
        }
        // assets/ 에 위 파일들이 있어야 함
    }

    private void showResult(String score, String label) {
        scoreText.setText(score);
        labelText.setText(label);
    }

    // 이진 사기탐지 모델일 때: 보통 index 1이 "fraud" 라벨이라는 전제
    private float takeFraudScore(OnnxModelManager.Prediction p) {
        if (p.probs == null || p.probs.length == 0) return 0f;
        // 감정 모델일 때는 단일 score 개념이 없으므로 최고 확률을 표시하는 정도로 둠
        if (p.probs.length == 2) {
            return p.probs[1]; // [0]=normal, [1]=fraud 로 가정
        } else {
            return p.probs[p.index];
        }
    }

    // ===== 임시 토크나이저(배포 전 SentencePiece JNI로 교체할 것) =====
    private static int[] tokenizeFallback(String text, int seqLen) {
        final int CLS = 101, SEP = 102, PAD = 0;
        int[] out = new int[seqLen];
        java.util.Arrays.fill(out, PAD);

        String[] toks = text.trim().split("\\s+");
        int pos = 0;
        out[pos++] = CLS;
        for (String tk : toks) {
            if (pos >= seqLen - 1) break;
            out[pos++] = pseudoVocabHash(tk);
        }
        if (pos < seqLen) {
            out[pos] = SEP;
        } else {
            out[seqLen - 1] = SEP;
        }
        return out;
    }

    private static int[] buildAttentionMask(int[] ids) {
        int[] m = new int[ids.length];
        for (int i = 0; i < ids.length; i++) m[i] = (ids[i] == 0 ? 0 : 1);
        return m;
    }

    private static int pseudoVocabHash(String t) {
        return (Math.abs(t.hashCode()) % 30000) + 1000;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            if (model != null) model.close();
        } catch (Exception ignored) {}
    }
}
