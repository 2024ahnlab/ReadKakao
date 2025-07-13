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
import com.readkakaotalk.app.service.MyAccessibilityService;

import org.json.JSONObject;

import java.util.List;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";

    private AlertDialog dialog = null;

    private TextView statusText;
    private TextView fraudMessageText;
    private LinearLayout emotionContainer;
    private TextView emotionAngerPercent;

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
        emotionAngerPercent = findViewById(R.id.emotionAngerPercent);

        Button settingsButton = findViewById(R.id.settingsButton);
        settingsButton.setOnClickListener(v -> {
            Intent intent = new Intent(this, SettingsActivity.class);
            startActivity(intent);
        });

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

//    private void handleIntent(Intent intent) {
//        if (intent != null && intent.hasExtra("alert_type")) {
//            String type = intent.getStringExtra("alert_type");
//            String emotion = intent.getStringExtra("emotion_level"); // 예: "분노", "불안" 등
//
//            switch (type) {
//                case "fraud":
//                    statusText.setText("매우 높음");
//                    statusText.setTextColor(Color.parseColor("#CC0000"));
//                    emotionContainer.setVisibility(View.GONE);
//                    showRecentMessages(5);
//                    break;
//
//                case "emotion":
//                    statusText.setText("매우 높음");
//                    statusText.setTextColor(Color.parseColor("#CC0000"));
//                    emotionContainer.setVisibility(View.VISIBLE);
//                    emotionAngerPercent.setText("80%"); // 실제 값 반영 필요
//                    fraudMessageText.setText("사기 의심 없음");
//                    break;
//
//                case "fraud_emotion":
//                    statusText.setText("매우 높음");
//                    statusText.setTextColor(Color.parseColor("#CC0000"));
//                    emotionContainer.setVisibility(View.VISIBLE);
//                    emotionAngerPercent.setText("80%");
//                    showRecentMessages(5);
//                    break;
//
//                default:
//                    statusText.setText("안전");
//                    statusText.setTextColor(Color.parseColor("#22A500"));
//                    emotionContainer.setVisibility(View.GONE);
//                    fraudMessageText.setText("(메시지 없음)");
//                    break;
//            }
//        }
//    }

    // UI 테스트용 handleIntent 함수!!!!
    private void handleIntent(Intent intent) {
        // 테스트용: 감정 감지 UI 강제 표시
        statusText.setText("매우 높음");
        statusText.setTextColor(Color.parseColor("#CC0000"));

        emotionContainer.setVisibility(View.VISIBLE);
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

    private void analyze(String message) {
        try {
            JSONObject result = new JSONObject();
            result.put("label", "사기");
            result.put("confidence", 1.0);
            showAlert(message, result.toString());
        } catch (Exception e) {
            Log.e(TAG, "예측 실패", e);
        }
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
