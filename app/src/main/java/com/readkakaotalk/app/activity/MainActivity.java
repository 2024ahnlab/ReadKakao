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
import android.view.accessibility.AccessibilityManager;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import com.readkakaotalk.app.R;
//import com.readkakaotalk.app.model.TorchModelManager;
import com.readkakaotalk.app.service.MyAccessibilityService;

import org.json.JSONObject;

import java.util.List;
import java.util.Set;

import android.content.SharedPreferences;
import android.content.Intent;
import android.app.AlertDialog;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
//    private TorchModelManager fraud_model, emotion_model;
    private float fraud_model_Weight = 0.5f, emotion_model_Weight = 0.5f;
    private AlertDialog dialog = null;
    private SeekBar weightSeekBar;
    private TextView ratioText, emotionTextView;
    private TextView fraudMessageTextView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Log.d(TAG, "MainActivity 시작됨");

//        // 모델 로딩
//        fraud_model = new TorchModelManager("fraud_model.pt");
//        emotion_model = new TorchModelManager("emotion_model.pt");
//        fraud_model.loadModel(this);
//        emotion_model.loadModel(this);

        weightSeekBar = findViewById(R.id.weightSeekBar);
        ratioText = findViewById(R.id.weightTextView);
        emotionTextView = findViewById(R.id.emotionTextView); // 새 감정 텍스트뷰

        findViewById(R.id.settingsButton).setOnClickListener(v -> {
            Intent intent = new Intent(this, SettingsActivity.class);
            startActivity(intent);
        });

        fraudMessageTextView = findViewById(R.id.fraudMessageTextView);

        weightSeekBar.setProgress(50);
        weightSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                fraud_model_Weight = progress / 100f;
                emotion_model_Weight = 1f - fraud_model_Weight;
                ratioText.setText("모델1: " + fraud_model_Weight + " / 모델2: " + emotion_model_Weight);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        // 접근성 서비스로부터 메시지 수신
        registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String text = intent.getStringExtra(MyAccessibilityService.EXTRA_TEXT);
                if (text != null) analyze(text);
            }
        }, new IntentFilter(MyAccessibilityService.ACTION_NOTIFICATION_BROADCAST), Context.RECEIVER_EXPORTED);

        // 알림 권한 요청
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS)
                        != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 100);
        }

        // 알림 클릭 진입 처리 (감정, 사기 결과 표시용)
        Intent intent = getIntent();
        if (intent != null && intent.hasExtra("alert_type")) {
            String type = intent.getStringExtra("alert_type");
            if ("fraud".equals(type)) {
                showRecentMessages(5);
            } else if ("emotion".equals(type)) {
                String emotion = intent.getStringExtra("emotion_level");
                emotionTextView.setText("감정 상태: " + emotion);
            } else if ("fraud_emotion".equals(type)) {
                showRecentMessages(5);
                String emotion = intent.getStringExtra("emotion_level");
                emotionTextView.setText("감정 상태: " + emotion);
            }
        }
    }

    private void analyze(String message) {
        // 모델 분석 대신 더미값으로 사기 경고 알림
        try {
            JSONObject result = new JSONObject();
            result.put("label", "사기");
            result.put("confidence", 1.0);
            showAlert(message, result.toString());
        } catch (Exception e) {
            Log.e(TAG, "예측 실패", e);
        }

//        float[] v = convertTextToVector(message);
//        float[] r1 = fraud_model.predict(v);
//        float[] r2 = emotion_model.predict(v);
//
//        float labelScore = fraud_model_Weight * r1[0] + emotion_model_Weight * r2[0];
//        float confidence = fraud_model_Weight * r1[1] + emotion_model_Weight * r2[1];
//
//        String label = labelScore > 0.5 ? "사기" : "정상";
//
//        try {
//            JSONObject result = new JSONObject();
//            result.put("label", label);
//            result.put("confidence", confidence);
//            if ("사기".equals(label)) showAlert(message, result.toString());
//        } catch (Exception e) {
//            Log.e(TAG, "예측 실패", e);
//        }
//    }
//
//    private float[] convertTextToVector(String input) {
//        float[] vector = new float[10];
//        for (int i = 0; i < Math.min(input.length(), 10); i++) vector[i] = input.charAt(i);
//        return vector;
    }

    private void showAlert(String message, String analysis) {
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel("fraud_alert", "Fraud Alerts", NotificationManager.IMPORTANCE_HIGH);
            channel.setDescription("사기 의심 메시지 경고");
            manager.createNotificationChannel(channel);
        }

        // 알림 클릭 시 MainActivity로 이동하도록 intent 설정
        Intent intent = new Intent(this, MainActivity.class);
        intent.putExtra("alert_type", "fraud"); // 또는 emotion, fraud_emotion
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, "fraud_alert")
                .setSmallIcon(R.drawable.ic_warning)
                .setContentTitle("이 대화는 피싱일 가능성이 있습니다")
//                .setContentTitle("현재 감정 상태가 매우 불안정합니다")
//                .setContentTitle("지금 이 대화는 매우 위험합니다")
                .setContentText("개인정보를 절대 입력하지 마세요")
//                .setContentText("지금 결정을 내리는 건 위험할 수 있습니다")
//                .setContentText("즉시 대화를 중단하세요")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setColor(Color.rgb(255, 140, 0)) // Orange
                .setAutoCancel(true)
                .setContentIntent(pendingIntent);

        manager.notify(1, builder.build());
    }

    private void showRecentMessages(int count) {
        List<String> messages = MyAccessibilityService.getRecentMessages(count);
        String combined = String.join("\n", messages);
        fraudMessageTextView.setText(combined.isEmpty() ? "없음" : combined);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (dialog != null) dialog.dismiss();
        if (!checkAccessibilityPermission())
            showPermissionDialog("접근성 권한 필요", "접근성 권한이 필요합니다.",
                    new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
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
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(title).setMessage(message)
                .setPositiveButton("설정", (dialog, which) -> startActivity(settingIntent));
        dialog = builder.create();
        dialog.show();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults); // ← 이 줄 추가
        if (requestCode == 100 && grantResults.length > 0 &&
                grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "알림 권한 허용됨");
        }
    }
}
