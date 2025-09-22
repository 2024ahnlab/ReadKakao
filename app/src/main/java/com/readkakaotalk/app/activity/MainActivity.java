package com.readkakaotalk.app.activity;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.AlertDialog;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.view.accessibility.AccessibilityManager;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.readkakaotalk.app.R;
import com.readkakaotalk.app.model.OnnxModelManager;
import com.readkakaotalk.app.service.MyAccessibilityService;
import com.readkakaotalk.app.tokenizer.SpmEncoder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";

    private OnnxModelManager fraudModel;
    private OnnxModelManager emotionModel;
    private SharedPreferences prefs;
    private BroadcastReceiver messageReceiver;

    // UI 컴포넌트
    private TextView statusText;
    private TextView fraudMessageText;
    private Button settingsButton;

    private SpmEncoder spmEncoder;
    private static final String SPM_ASSET_NAME = "tokenizer_78b3253a26.model";
    private static final int DEFAULT_SEQ_LEN = 64;
    private AlertDialog dialog = null;

    private static final Map<String, Float> EMO_WEIGHT = new HashMap<String, Float>() {{
        // 중간 긍정 (-0.20)
        put("환영/호의", -0.20f); put("감동/감탄", -0.20f); put("고마움", -0.20f); put("존경", -0.20f);
        put("뿌듯함", -0.20f); put("편안/쾌적", -0.20f); put("아껴주는", -0.20f); put("즐거움/신남", -0.20f);
        put("흐뭇함(귀여움/예쁨)", -0.20f); put("행복", -0.20f); put("기쁨", -0.20f); put("안심/신뢰", -0.20f);

        // 약한 긍정 (-0.10)
        put("기대감", -0.10f); put("신기함/관심", -0.10f); put("깨달음", -0.10f);

        // 중립 (0.00)
        put("비장함", 0.00f); put("없음", 0.00f); put("우쭐댐/무시함", 0.00f); put("귀찮음", 0.00f);
        put("재미없음", 0.00f); put("한심함", 0.00f); put("지긋지긋", 0.00f); put("역겨움/징그러움", 0.00f);
        put("패배/자기혐오", 0.00f);

        // 약한 부정 (+0.10)
        put("부끄러움", +0.10f); put("놀람", +0.10f); put("불평/불만", +0.10f); put("슬픔", +0.10f);
        put("안타까움/실망", +0.10f); put("어이없음", +0.10f); put("서러움", +0.10f); put("힘듦/지침", +0.10f);

        // 중간 부정 (+0.20)
        put("당황/난처", +0.20f); put("부담/안_내킴", +0.20f); put("불쌍함/연민", +0.20f); put("경악", +0.20f);
        put("증오/혐오", +0.20f);

        // 강한 부정 (+0.30)
        put("의심/불신", +0.30f); put("죄책감", +0.30f); put("화남/분노", +0.30f); put("짜증", +0.30f);
        put("불안/걱정", +0.30f); put("공포/무서움", +0.30f); put("절망", +0.30f);
    }};

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = getSharedPreferences("settings", MODE_PRIVATE);

        statusText = findViewById(R.id.statusText);
        fraudMessageText = findViewById(R.id.fraudMessageText);
        settingsButton = findViewById(R.id.settingsButton);

        settingsButton.setOnClickListener(v -> {
            Intent intent = new Intent(this, SettingsActivity.class);
            startActivity(intent);
        });

        try {
            String spmLocalPath = copyAssetToFile(SPM_ASSET_NAME);

            // 1) 모델 먼저 생성
            fraudModel = new OnnxModelManager(this, "distilkobert_sc.int8.onnx", "label_map.json");
            emotionModel = new OnnxModelManager(this, "distilkobert_emotion_sc.int8.onnx", "label_map_emotion.json");

            // 2) 동적 모델이라면 원하는 길이로 강제 지정 (예: 128)
            final int SEQ_LEN = 128;
            fraudModel.setSeqLen(SEQ_LEN);
            emotionModel.setSeqLen(SEQ_LEN);

            // 3) SpmEncoder도 같은 길이로 생성
            spmEncoder = new SpmEncoder(spmLocalPath, fraudModel.getSeqLen());

            Log.i(TAG, "ONNX models & tokenizer loaded successfully.");
        } catch (Exception e) {
            Log.e(TAG, "Failed to load ONNX model", e);
            statusText.setText("모델 로딩 실패");
        }

        messageReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String text = intent.getStringExtra(MyAccessibilityService.EXTRA_TEXT);
                if (text != null && !text.isEmpty()) {
                    Log.d(TAG, "메시지 수신: " + text);
                    analyze(text.trim());
                }
            }
        };
        LocalBroadcastManager.getInstance(this).registerReceiver(messageReceiver,
                new IntentFilter(MyAccessibilityService.ACTION_NOTIFICATION_BROADCAST));

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 100);
        }

        handleIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleIntent(intent);
    }

    private void handleIntent(Intent intent) {
        if (intent != null && intent.hasExtra("suspicious_message")) {
            Log.d(TAG, "알림 클릭으로 실행됨. UI를 업데이트합니다.");
            String message = intent.getStringExtra("suspicious_message");
            float finalScore = intent.getFloatExtra("final_score", 0f);
            updateUiWithDetails(message, finalScore, true);
        } else {
            updateUiWithDetails(null, 0, false);
        }
    }

    private void analyze(String text) {
        try {
            float fraudThreshold = prefs.getFloat("fraud_threshold", 0.7f);

            OnnxModelManager.Prediction pFraud = runPrediction(fraudModel, text, spmEncoder);
            OnnxModelManager.Prediction pEmotion = runPrediction(emotionModel, text, spmEncoder);

            float fraudScore = takeFraudScore(pFraud);
            float emotionWeight = emotionSignedWeightStrict(pEmotion.label);

            float finalScore = fraudScore + emotionWeight;
            finalScore = Math.max(0.0f, Math.min(1.0f, finalScore));

            boolean isAlert = finalScore >= fraudThreshold;

            Log.d(TAG, String.format("Score(%.3f) | Fraud(%.3f) | Emotion(%s, %.3f) | Alert(%b)",
                    finalScore, fraudScore, pEmotion.label, emotionWeight, isAlert));

            updateUiWithDetails(text, finalScore, isAlert);

            if (isAlert) {
                showAlert(text, finalScore);
            }
        } catch (Exception e) {
            Log.e(TAG, "analyze failed", e);
            statusText.setText("분석 오류");
        }
    }

    private void updateUiWithDetails(String message, float finalScore, boolean isAlert) {
        if (isAlert) {
            fraudMessageText.setText("\"" + message + "\"");
            statusText.setText("위험 (" + (int)(finalScore * 100) + "%)");
            statusText.setTextColor(Color.parseColor("#CC0000"));
        } else {
            fraudMessageText.setText("(최근 분석된 의심 메시지 없음)");
            statusText.setText("안전");
            statusText.setTextColor(Color.parseColor("#22A500"));
        }
    }

    private void showAlert(String message, float finalScore) {
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel("fraud_alert", "Fraud Alerts", NotificationManager.IMPORTANCE_HIGH);
            manager.createNotificationChannel(channel);
        }

        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        intent.putExtra("suspicious_message", message);
        intent.putExtra("final_score", finalScore);

        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, "fraud_alert")
                .setSmallIcon(R.drawable.ic_warning)
                .setContentTitle("사기 의심 메시지가 감지되었습니다!")
                .setContentText("메시지: \"" + message + "\"")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent);
        manager.notify(1, builder.build());
    }

    private OnnxModelManager.Prediction runPrediction(OnnxModelManager model, String text, SpmEncoder encoder) throws Exception {
        if (encoder == null) throw new IOException("SpmEncoder is required but not available.");
        SpmEncoder.EncodedInput enc = encoder.encode(text);
        return model.predictIds(enc.inputIds, enc.attentionMask);
    }

    private float emotionSignedWeightStrict(String label) {
        if (label == null) return 0f;
        Float w = EMO_WEIGHT.get(label);
        return (w != null) ? w : 0f;
    }

    private float takeFraudScore(OnnxModelManager.Prediction p) {
        if (p.probs == null || p.probs.length < 2) return 0f;
        return p.probs[1];
    }

    private String copyAssetToFile(String assetName) throws IOException {
        File outFile = new File(getFilesDir(), assetName);
        if (outFile.exists()) return outFile.getAbsolutePath();
        try (InputStream is = getAssets().open(assetName);
             FileOutputStream fos = new FileOutputStream(outFile)) {
            byte[] buf = new byte[8192]; int n;
            while ((n = is.read(buf)) > 0) fos.write(buf, 0, n);
        }
        return outFile.getAbsolutePath();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (dialog != null) dialog.dismiss();
        if (!checkAccessibilityPermission()) {
            showPermissionDialog("접근성 권한 필요", "메시지 내용을 읽기 위해 접근성 권한이 반드시 필요합니다.",
                    new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
        }
    }

    private boolean checkAccessibilityPermission() {
        AccessibilityManager manager = (AccessibilityManager) getSystemService(Context.ACCESSIBILITY_SERVICE);
        List<AccessibilityServiceInfo> list = manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_GENERIC);
        for (AccessibilityServiceInfo info : list) {
            if (info.getResolveInfo().serviceInfo.packageName.equals(getPackageName())) return true;
        }
        return false;
    }

    private void showPermissionDialog(String title, String message, Intent settingIntent) {
        if (dialog != null && dialog.isShowing()) return;
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(title).setMessage(message)
                .setPositiveButton("설정으로 이동", (dialog, which) -> startActivity(settingIntent))
                .setCancelable(false);
        dialog = builder.create();
        dialog.show();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (messageReceiver != null) {
            LocalBroadcastManager.getInstance(this).unregisterReceiver(messageReceiver);
        }
        try {
            if (fraudModel != null) fraudModel.close();
            if (emotionModel != null) emotionModel.close();
        } catch (Exception ignored) {}
    }
}
