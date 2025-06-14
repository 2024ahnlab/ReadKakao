package com.readkakaotalk.app.activity;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.annotation.SuppressLint;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.accessibility.AccessibilityManager;
import android.widget.Button;
import android.widget.EditText;

import com.readkakaotalk.app.R;
import com.readkakaotalk.app.model.TorchModelManager;
import com.readkakaotalk.app.service.MyAccessibilityService;

import org.json.JSONObject;

import java.util.List;
import java.util.Set;

import androidx.core.content.ContextCompat;
import androidx.core.app.ActivityCompat;
import androidx.annotation.NonNull;


public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    private AlertDialog dialog = null;
    private TorchModelManager modelManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Log.d(TAG, "1");

//        // PyTorch 모델 관리 클래스 초기화 및 모델 로딩 (assets/model.pt)
//        modelManager = new TorchModelManager();
//        modelManager.loadModel(this);

        EditText messages = findViewById(R.id.editTextTextMultiLine);
        Log.d(TAG, "2");

        // 텍스트가 변경될 때마다 모델을 통해 분석 수행
        messages.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void afterTextChanged(Editable s) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
//                analyzeTextWithModel(s.toString());
                Log.d(TAG, "3");
                // 테스트용 사기 처리
                Log.d(TAG, "simulateFraudDetection 호출됨: ");
                simulateFraudDetection(s.toString());
                Log.d(TAG, "4");
            }
        });

        // 결과 화면으로 이동하는 버튼 리스너 설정
        Button buttonGoToResult = findViewById(R.id.buttonGoToResult);
        Log.d(TAG, "5");
        buttonGoToResult.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, ResultActivity.class);
            startActivity(intent);
        });
        Log.d(TAG, "6");
        // 접근성 서비스에서 수집한 대화 내역 수신
        registerReceiver(
                new BroadcastReceiver() {
                    @Override
                    public void onReceive(Context context, Intent intent) {
                        Log.d(TAG, "7");
                        String text = intent.getStringExtra(MyAccessibilityService.EXTRA_TEXT);
                        Log.d(TAG, "8");
                        Log.d(TAG, "[DEBUG] Broadcast 받은 텍스트: " + text);
                        messages.setText("대화내역: \n\n" + text);
                        simulateFraudDetection(text);
//                        if (text != null && text.trim().length() > 0) {
//                            Log.d(TAG, "[DEBUG] simulateFraudDetection 호출 전");
//                            simulateFraudDetection(text);
//                        }
                    }
                },
                new IntentFilter(MyAccessibilityService.ACTION_NOTIFICATION_BROADCAST),
                Context.RECEIVER_EXPORTED
        );

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 100);
            }
        }
    }
    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == 100) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "알림 권한 허용됨");
            } else {
                Log.w(TAG, "알림 권한 거부됨");
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (dialog != null) dialog.dismiss();

        // 접근성 권한 확인 및 요청
        if (!checkAccessibilityPermission()) {
            showPermissionDialog("접근성 권한 필요", "접근성 권한이 필요합니다.\n\n설치된 서비스 -> 허용",
                    new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
            return;
        }
    }

    // 권한 요청 다이얼로그 표시
    private void showPermissionDialog(String title, String message, Intent settingIntent) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(title)
                .setMessage(message)
                .setPositiveButton("설정", (dialog, which) -> startActivity(settingIntent));
        dialog = builder.create();
        dialog.show();
    }

    // 알림 접근 권한 확인
    public boolean checkNotificationPermission() {
        Set<String> sets = NotificationManagerCompat.getEnabledListenerPackages(this);
        return sets.contains(getPackageName());
    }

    // 접근성 권한 확인
    public boolean checkAccessibilityPermission() {
        AccessibilityManager manager = (AccessibilityManager) getSystemService(Context.ACCESSIBILITY_SERVICE);
        List<AccessibilityServiceInfo> list = manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_GENERIC);
        for (AccessibilityServiceInfo info : list) {
            if (info.getResolveInfo().serviceInfo.packageName.equals(getPackageName())) {
                return true;
            }
        }
        return false;
    }

    // 모델 없이 모든 입력을 "사기"로 간주하는 테스트 함수
    private void simulateFraudDetection(String message) {
        try {
            Log.d(TAG, "simulateFraudDetection 호출됨: " + message);
            JSONObject result = new JSONObject();
            result.put("label", "사기");
            result.put("confidence", 1.0);

            showFraudAlert(message, result.toString());

//            // 앱으로 화면 전환
//            Intent intent = new Intent(MainActivity.this, ResultActivity.class);
//            intent.putExtra("SERVER_RESPONSE", result.toString());
//            startActivity(intent);

        } catch (Exception e) {
            Log.e(TAG, "테스트 사기 분석 실패", e);
        }
    }

    // 입력 텍스트를 벡터로 변환하고 모델로 예측 수행
    private void analyzeTextWithModel(String message) {
        try {
            float[] inputVector = convertTextToVector(message);
            float[] output = modelManager.predict(inputVector); // 모델 예측 결과: [label score, confidence]

            // label이 0.5보다 크면 "사기", 아니면 "정상"
            String label = output[0] > 0.5 ? "사기" : "정상";
            float confidence = output[1];

            JSONObject result = new JSONObject();
            result.put("label", label);
            result.put("confidence", confidence);

            // 사기 탐지 시 알림 표시
            if ("사기".equals(label)) {
                showFraudAlert(message, result.toString());
            }

            // 결과 화면으로 전환 (결과 전달)
            Intent intent = new Intent(MainActivity.this, ResultActivity.class);
            intent.putExtra("SERVER_RESPONSE", result.toString());
            startActivity(intent);

        } catch (Exception e) {
            Log.e(TAG, "모델 분석 오류", e);
        }
    }

    // 텍스트를 유니코드 기반으로 길이 10의 float 배열로 변환
    private float[] convertTextToVector(String input) {
        float[] vector = new float[10];
        for (int i = 0; i < Math.min(input.length(), 10); i++) {
            vector[i] = input.charAt(i); // char → float 변환
        }
        return vector;
    }

    // 사기 의심 메시지에 대해 알림(Notification)을 띄우는 함수
    private void showFraudAlert(String message, String analysis) {
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        // Android 8.0 이상에서는 채널 필요
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    "fraud_alert", "Fraud Alerts", NotificationManager.IMPORTANCE_HIGH);
            channel.setDescription("사기 의심 메시지 경고");
            manager.createNotificationChannel(channel);
        }

        // 알림 클릭 시 ResultActivity 실행
        Intent intent = new Intent(this, ResultActivity.class);
        intent.putExtra("SERVER_RESPONSE", analysis);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);

        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE);

        // 알림 구성 및 표시
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, "fraud_alert")
                .setSmallIcon(R.drawable.ic_warning)
                .setContentTitle("사기 의심 메시지 감지")
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent);

        Log.d(TAG, "알림 표시됨: " + message);

        manager.notify(1, builder.build());
    }
}
