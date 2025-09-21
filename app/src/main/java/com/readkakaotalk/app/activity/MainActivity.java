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
import com.readkakaotalk.app.tokenizer.SpmEncoder;
//import com.readkakaotalk.app.tokenizer.EncodedInput;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

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

    // SentencePiece
    private static final String SPM_ASSET_NAME = "tokenizer_78b3253a26.model";
    private String spmLocalPath; // getFilesDir() 아래 실제 파일 경로
    private static final int DEFAULT_SEQ_LEN = 64; // 모델이 [1, 64]라면 64

    // 기본값
    private static final float DEFAULT_THRESHOLD = 0.6f;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main); // 레이아웃에 editTextMessage/buttonAnalyze/textViewScore/textViewLabel 있어야 함

        // UI 바인딩
        inputEditText = findViewById(R.id.editTextMessage);
        analyzeButton = findViewById(R.id.buttonAnalyze);
        scoreText = findViewById(R.id.textViewScore);
        labelText = findViewById(R.id.textViewLabel);

        prefs = PreferenceManager.getDefaultSharedPreferences(this);
        pickModelFromSettings(this);

        // tokenizer 모델을 내부 저장소로 복사
        spmLocalPath = copyTokenizerToFiles();

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

            // 문자열 입력 가능한 모델이면 그대로 호출
            try {
                p = model.predictText(text);
            } catch (IllegalStateException notString) {
                // 정수 입력 모델인 경우: SentencePiece로 전처리
                if (spmLocalPath == null) {
                    Log.e(TAG, "SentencePiece model not available");
                    showResult("0.0", "SPM MISSING");
                    return;
                }
                SpmEncoder encoder = new SpmEncoder(spmLocalPath, DEFAULT_SEQ_LEN);
                SpmEncoder.EncodedInput enc = encoder.encode(text);
                p = model.predictIds(enc.inputIds, enc.attentionMask);
            }

            // 사기탐지(2클래스 가정)일 때 index 1 확률을 threshold 비교
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
            // 감정 분류
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
        if (p.probs.length == 2) {
            return p.probs[1]; // [0]=normal, [1]=fraud 가정
        } else {
            return p.probs[p.index];
        }
    }

    // assets/의 tokenizer 모델을 내부 저장소로 복사하고 절대경로를 반환
    private String copyTokenizerToFiles() {
        File outFile = new File(getFilesDir(), SPM_ASSET_NAME);
        if (!outFile.exists()) {
            try (InputStream is = getAssets().open(SPM_ASSET_NAME);
                 FileOutputStream fos = new FileOutputStream(outFile)) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = is.read(buf)) > 0) fos.write(buf, 0, n);
                Log.i(TAG, "SentencePiece model copied to " + outFile.getAbsolutePath());
            } catch (IOException e) {
                Log.e(TAG, "Failed to copy tokenizer model", e);
                return null;
            }
        }
        return outFile.getAbsolutePath();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            if (model != null) model.close();
        } catch (Exception ignored) {}
    }
}
