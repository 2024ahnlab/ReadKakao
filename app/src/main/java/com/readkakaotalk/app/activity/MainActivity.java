package com.readkakaotalk.app.activity;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.AlertDialog;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.view.accessibility.AccessibilityManager;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import com.readkakaotalk.app.R;
import com.readkakaotalk.app.model.TfLiteModelManager; // <--- 변경: TfliteModelManager 임포트
import com.readkakaotalk.app.service.MyAccessibilityService;

import org.json.JSONObject;
// import org.pytorch.Tensor; // <--- 삭제: Pytorch Tensor 불필요

import java.util.List;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";

    private AlertDialog dialog = null;

    private TextView statusText;
    private TextView fraudMessageText;
    private LinearLayout emotionContainer;
    private TextView emotionNeutralPercent;
    private TextView emotionSurprisePercent;
    private TextView emotionAnxietyPercent;
    private TextView emotionAngerPercent;
    private TfLiteModelManager modelManager; // <--- 변경: 모델 매니저 타입

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getSupportActionBar() != null) getSupportActionBar().hide();
        setContentView(R.layout.activity_main);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            getWindow().setStatusBarColor(Color.parseColor("#E8E8E8")); // 원하는 색상 코드
        }
        Log.d(TAG, "MainActivity 시작됨");

        statusText = findViewById(R.id.statusText);
        fraudMessageText = findViewById(R.id.fraudMessageText);
        emotionContainer = findViewById(R.id.emotionContainer);
        emotionNeutralPercent = findViewById(R.id.emotionNeutralPercent);
        emotionSurprisePercent = findViewById(R.id.emotionSurprisePercent);
        emotionAnxietyPercent = findViewById(R.id.emotionAnxietyPercent);
        emotionAngerPercent = findViewById(R.id.emotionAngerPercent);

        Button settingsButton = findViewById(R.id.settingsButton);
        settingsButton.setOnClickListener(v -> {
            Intent intent = new Intent(this, SettingsActivity.class);
            startActivity(intent);
        });

        // --- 모델 로딩 부분 수정 ---
        try {
            modelManager = new TfLiteModelManager("distilkobert_fp16.tflite"); // <-- TFLite 모델 파일명 지정
            modelManager.loadModel(this); // <-- 모델 로드
        } catch (Exception e) {
            Log.e(TAG, "TFLite 모델 초기화에 실패했습니다.", e);
            // 모델 로드 실패 시 사용자에게 알림 등 예외 처리 구현
        }
        // -------------------------

        registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String text = intent.getStringExtra(MyAccessibilityService.EXTRA_TEXT);
                if (text != null) analyze(text);
            }
        }, new IntentFilter(MyAccessibilityService.ACTION_NOTIFICATION_BROADCAST), Context.RECEIVER_EXPORTED);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS)
                        != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 100);
        }

        handleIntent(getIntent());
    }

    // UI 테스트용 handleIntent 함수!!!!
    private void handleIntent(Intent intent) {
        // 테스트용: 감정 감지 UI 강제 표시
        statusText.setText("매우 높음");
        statusText.setTextColor(Color.parseColor("#CC0000"));

        emotionContainer.setVisibility(View.VISIBLE);
        emotionNeutralPercent.setText("0%");
        emotionSurprisePercent.setText("0%");
        emotionAnxietyPercent.setText("0%");
        emotionAngerPercent.setText("80%");

        fraudMessageText.setText("사기 의심 없음");

        // 실제 intent 처리는 무시
        return;
    }


    private void showRecentMessages(int count) {
        List<String> messages = MyAccessibilityService.getRecentMessages(count);
        String combined = String.join("\n", messages);
        fraudMessageText.setText(combined.isEmpty() ? "사기 의심 메시지 없음" : combined);
    }

    // MainActivity.java의 analyze 메소드를 아래와 같이 수정하세요.

    private void analyze(String message) {
        if (modelManager == null) {
            Log.e(TAG, "모델이 초기화되지 않아 분석을 중단합니다.");
            return;
        }

        try {
            // --- 토크나이저 부분 (향후 실제 라이브러리로 교체 필요) ---
            // 실제 토크나이저는 input_ids와 attention_mask를 모두 생성해야 합니다.
            // 길이는 모델에 맞게 64로 맞춰야 합니다.
            int[][] inputIds = new int[1][64]; // placeholder
            int[][] attentionMask = new int[1][64]; // placeholder
            // 예시: [101, 2000, 3000, 102, 0, 0, ...] -> input_ids
            //       [1,   1,    1,    1,   0, 0, ...] -> attention_mask
            // --------------------------------------------------------

            // --- 추론 부분 수정 ---
            float[][] output = modelManager.predict(inputIds, attentionMask);
            if (output == null || output.length == 0) {
                Log.e(TAG, "모델 출력값이 유효하지 않습니다.");
                return;
            }
            float[] scores = output[0]; // [결과1, 결과2] 형태의 배열
            // --------------------

            // --- UI 업데이트 부분 (모델이 "사기 탐지"용이라고 가정) ---
            // 감정 분석 UI는 숨깁니다.
            emotionContainer.setVisibility(View.GONE);

            float notFraudScore = scores[0]; // 첫번째 결과가 '사기 아님' 확률이라고 가정
            float fraudScore = scores[1];    // 두번째 결과가 '사기' 확률이라고 가정

            Log.d(TAG, "분석 결과: 사기 아님 확률=" + notFraudScore + ", 사기 확률=" + fraudScore);

            // fraudScore가 특정 임계값(예: 0.7)을 넘으면 위험으로 판단
            if (fraudScore > 0.7) {
                statusText.setText("매우 높음 (사기 확률: " + (int)(fraudScore * 100) + "%)");
                statusText.setTextColor(Color.parseColor("#CC0000"));
                fraudMessageText.setText(message); // 사기 의심 메시지 표시
                showAlert(message, "사기 의심"); // 알림 발생
            } else {
                statusText.setText("안전 (사기 확률: " + (int)(fraudScore * 100) + "%)");
                statusText.setTextColor(Color.parseColor("#22A500"));
                fraudMessageText.setText("(최근 분석된 메시지 없음)");
            }
            // ----------------------------------------------------

        } catch (Exception e) {
            Log.e(TAG, "예측 실패", e);
        }
    }

    // ✅ tokenizer 결과가 있다고 가정한 placeholder 함수
    private int[] getTokenIdsFromTokenizer(String text) {
        // 실제 구현 시: tokenizer에서 받은 token id 배열을 반환해야 함
        // 예시: 입력 길이를 128로 맞추고 패딩을 추가하는 등의 전처리가 필요할 수 있습니다.
        return new int[]{101, 1234, 5678, 102}; // [CLS] ... [SEP] 예시
    }

    private void showAlert(String message, String analysis) {
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    "fraud_alert",
                    "Fraud Alerts",
                    NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("사기 의심 메시지 경고");
            manager.createNotificationChannel(channel);

        }

        Intent intent = new Intent(this, MainActivity.class);
        intent.putExtra("alert_type", "fraud");
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, "fraud_alert")
                .setSmallIcon(R.drawable.ic_warning)
                .setContentTitle("사기 피해를 입지 않도록 즉시 대화를 중단하세요.")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setColor(Color.rgb(255, 140, 0))
                .setAutoCancel(true)
                .setContentIntent(pendingIntent);

        manager.notify(1, builder.build());
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (dialog != null) dialog.dismiss();
        if (!checkAccessibilityPermission())
            showPermissionDialog("접근성 권한 필요", "접근성 권한이 필요합니다.",
                    new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
    }

    // --- 추가: onDestroy에서 모델 리소스 해제 ---
    @Override
    protected void onDestroy() {
        if (modelManager != null) {
            modelManager.close();
        }
        super.onDestroy();
    }
    // ------------------------------------

    private boolean checkAccessibilityPermission() {
        AccessibilityManager manager = (AccessibilityManager) getSystemService(Context.ACCESSIBILITY_SERVICE);
        List<AccessibilityServiceInfo> list = manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_GENERIC);
        for (AccessibilityServiceInfo info : list) {
            if (info.getResolveInfo().serviceInfo.packageName.equals(getPackageName())) return true;
        }
        return false;
    }

    private void showPermissionDialog(String title, String message, Intent settingIntent) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(title).setMessage(message)
                .setPositiveButton("설정", (dialog, which) -> startActivity(settingIntent));
        dialog = builder.create();
        dialog.show();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 100 && grantResults.length > 0 &&
                grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "알림 권한 허용됨");
        }
    }
}
