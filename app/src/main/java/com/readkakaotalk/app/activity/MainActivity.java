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
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.view.accessibility.AccessibilityManager;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.readkakaotalk.app.R;
import com.readkakaotalk.app.model.TfLiteModelManager;
import com.readkakaotalk.app.service.MyAccessibilityService;

import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private static final int NOTIFICATION_PERMISSION_REQUEST_CODE = 100;
    public static final String EXTRA_IS_DANGER = "is_danger";
    public static final String EXTRA_DANGER_MESSAGE = "danger_message";

    private AlertDialog dialog = null;
    private TextView statusText;
    private TextView fraudMessageText;
    private ImageButton settingsButton;
    private TfLiteModelManager modelManager;
    private SharedPreferences prefs;
    private BroadcastReceiver messageReceiver;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Log.d(TAG, "MainActivity onCreate");

        prefs = getSharedPreferences("settings", MODE_PRIVATE);
        statusText = findViewById(R.id.statusText);
        fraudMessageText = findViewById(R.id.fraudMessageText);
        settingsButton = findViewById(R.id.settingsButton);

        settingsButton.setOnClickListener(v -> {
            Intent intent = new Intent(this, SettingsActivity.class);
            startActivity(intent);
        });

        modelManager = TfLiteModelManager.getInstance(getApplicationContext());
        modelManager.initialize(getApplicationContext());

        messageReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String text = intent.getStringExtra(MyAccessibilityService.EXTRA_TEXT);
                Log.d("MainActivity", "Broadcast received! Text: \n" + text);
                if (text != null && !text.isEmpty()) {
                    analyze(text);
                }
            }
        };

        // [수정] onCreate에서 LocalBroadcastManager를 통해 Receiver를 등록합니다.
        IntentFilter filter = new IntentFilter(MyAccessibilityService.ACTION_NOTIFICATION_BROADCAST);
        LocalBroadcastManager.getInstance(this).registerReceiver(messageReceiver, filter);

        handleIntent(getIntent());
    }

    // [수정] onDestroy에서 Receiver를 해제합니다.
    @Override
    protected void onDestroy() {
        super.onDestroy();
        LocalBroadcastManager.getInstance(this).unregisterReceiver(messageReceiver);
        Log.d(TAG, "MainActivity onDestroy");
    }

    // --- (이하 다른 메서드들은 변경 사항 없음) ---

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
    }

    private void handleIntent(Intent intent) {
        if (intent != null && intent.getBooleanExtra(EXTRA_IS_DANGER, false)) {
            String message = intent.getStringExtra(EXTRA_DANGER_MESSAGE);
            updateUiForDanger(message);
        } else if (Intent.ACTION_MAIN.equals(intent.getAction())) {
            updateInitialUI();
        }
    }

    private void updateInitialUI() {
        statusText.setText("안전");
        statusText.setTextColor(ContextCompat.getColor(this, R.color.status_safe));
        fraudMessageText.setText("(분석된 위험 메시지가 없습니다)");
    }

    private void updateUiForDanger(String message) {
        statusText.setText("위험");
        statusText.setTextColor(ContextCompat.getColor(this, R.color.status_danger));
        fraudMessageText.setText(message);
    }

    private void analyze(String message) {
        if (!modelManager.isInitialized()) {
            Log.w(TAG, "analyze 호출 시 모델이 초기화되지 않아 재초기화를 시도합니다.");
            modelManager.initialize(getApplicationContext());
            if (!modelManager.isInitialized()) {
                Log.e(TAG, "모델 재초기화 실패. 분석을 중단합니다.");
                return;
            }
        }

        try {
            int[][] inputIds = new int[1][64];
            int[][] attentionMask = new int[1][64];
            // TODO: 여기에 실제 토크나이저 코드를 적용해야 합니다.

            float[][] output = modelManager.predict(inputIds, attentionMask);
            if (output == null) return;

            float[] scores = output[0];
            float fraudScore = scores[1];
            Log.d(TAG, "분석 결과: [사기아님:" + scores[0] + ", 사기:" + fraudScore + "]");
            float fraudThreshold = prefs.getFloat("fraud_threshold", 0.7f);

            if (fraudScore > fraudThreshold) {
                updateUiForDanger(message);
                showAlert(message);
            } else {
                updateInitialUI();
            }
        } catch (Exception e) {
            Log.e(TAG, "분석 중 오류 발생", e);
        }
    }

    private void showAlert(String message) {
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        String channelId = "fraud_alert_channel";

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(channelId, "사기 의심 경고", NotificationManager.IMPORTANCE_HIGH);
            manager.createNotificationChannel(channel);
        }

        Intent intent = new Intent(this, MainActivity.class);
        intent.setAction("SHOW_DANGER");
        intent.putExtra(EXTRA_IS_DANGER, true);
        intent.putExtra(EXTRA_DANGER_MESSAGE, message);
        intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 1, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, channelId)
                .setSmallIcon(R.drawable.ic_warning)
                .setContentTitle("피싱 위험 감지!")
                .setContentText("사기 의심 메시지가 도착했습니다.")
                .setStyle(new NotificationCompat.BigTextStyle().bigText("사기 의심 메시지가 도착했습니다: \"" + message + "\""))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent);

        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            manager.notify(1, builder.build());
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (dialog != null && dialog.isShowing()) dialog.dismiss();
        if (!checkAccessibilityPermission()) {
            showPermissionDialog("접근성 권한 필요", "피싱 메시지를 감지하기 위해 접근성 권한이 반드시 필요합니다.", new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
        } else {
            checkAndRequestNotificationPermission();
        }

        // [수정] onResume에서 등록 로직 삭제

        handleIntent(getIntent());
    }

    @Override
    protected void onPause() {
        super.onPause();
        // [수정] onPause에서 해제 로직 삭제
    }

    private void checkAndRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{android.Manifest.permission.POST_NOTIFICATIONS},
                        NOTIFICATION_PERMISSION_REQUEST_CODE);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == NOTIFICATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "알림 권한이 허용되었습니다.", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "알림 권한이 거부되어 위험 경고를 받으실 수 없습니다.", Toast.LENGTH_LONG).show();
            }
        }
    }

    private boolean checkAccessibilityPermission() {
        AccessibilityManager manager = (AccessibilityManager) getSystemService(Context.ACCESSIBILITY_SERVICE);
        if (manager == null) return false;
        List<AccessibilityServiceInfo> list = manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_GENERIC);
        if (list == null) return false;
        for (AccessibilityServiceInfo info : list) {
            if (info.getResolveInfo().serviceInfo.packageName.equals(getPackageName())) return true;
        }
        return false;
    }

    private void showPermissionDialog(String title, String message, Intent settingIntent) {
        if (dialog != null && dialog.isShowing()) return;
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(title).setMessage(message)
                .setPositiveButton("설정으로 이동", (d, which) -> startActivity(settingIntent))
                .setCancelable(false);
        dialog = builder.create();
        dialog.show();
    }
}
