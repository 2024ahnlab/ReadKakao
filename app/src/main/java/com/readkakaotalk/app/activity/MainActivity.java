package com.readkakaotalk.app.activity;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NotificationManagerCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.accessibility.AccessibilityManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import com.readkakaotalk.app.R;
import com.readkakaotalk.app.service.MyAccessibilityService;
import com.readkakaotalk.app.service.MyNotificationService;
import com.readkakaotalk.app.service.ApiService;
import com.readkakaotalk.app.service.RetrofitClient;

import org.json.JSONObject;

import okhttp3.MediaType;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import java.util.List;
import java.util.Set;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import androidx.core.app.NotificationCompat;
import android.app.PendingIntent;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    private AlertDialog dialog = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // EditText 초기화 및 텍스트 변경 감지
        final EditText messages = findViewById(R.id.editTextTextMultiLine);
        messages.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                // 텍스트 변경 전 처리할 내용이 있으면 여기에 작성
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                // 텍스트가 변경될 때마다 서버에 전송
                sendTextToServer(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {
                // 텍스트 변경 후 처리할 내용이 있으면 여기에 작성
            }
        });

        // 결과 화면으로 이동하는 버튼 설정
        Button buttonGoToResult = findViewById(R.id.buttonGoToResult);
        buttonGoToResult.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, ResultActivity.class);
            startActivity(intent);
        });

        // 알림 수신을 위한 BroadcastReceiver 등록
        LocalBroadcastManager.getInstance(this).registerReceiver(
                new BroadcastReceiver() {
                    @SuppressLint("SetTextI18n")
                    @Override
                    public void onReceive(Context context, Intent intent) {
                        String name = intent.getStringExtra(MyNotificationService.EXTRA_NAME);
                        String text = intent.getStringExtra(MyNotificationService.EXTRA_TEXT);
                        messages.setText("이름: " + name + "\n메시지: " + text + "\n\n" + messages.getText());
                    }
                }, new IntentFilter(MyNotificationService.ACTION_NOTIFICATION_BROADCAST)
        );

        // 접근성 서비스 수신을 위한 BroadcastReceiver 등록
        LocalBroadcastManager.getInstance(this).registerReceiver(
                new BroadcastReceiver() {
                    @SuppressLint("SetTextI18n")
                    @Override
                    public void onReceive(Context context, Intent intent) {
                        String text = intent.getStringExtra(MyAccessibilityService.EXTRA_TEXT);
                        messages.setText("대화내역: \n\n" + text);
                    }
                }, new IntentFilter(MyAccessibilityService.ACTION_NOTIFICATION_BROADCAST)
        );
    }

    @Override
    protected void onResume() {
        super.onResume();

        // 다이얼로그가 열려있다면 닫기
        if (this.dialog != null) {
            this.dialog.dismiss();
        }

        // 알림 접근 권한 확인 및 요청
        if (!checkNotificationPermission()) {
            final AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("알림 접근 권한 필요");
            builder.setMessage("알림 접근 권한이 필요합니다.");
            builder.setPositiveButton("설정", (dialog, which) -> {
                startActivity(new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"));
            });
            dialog = builder.create();
            dialog.show();
            return;
        }

        // 알림 서비스 실행 확인 및 시작
        if (!isMyServiceRunning(MyNotificationService.class)) {
            Intent intent = new Intent(getApplicationContext(), MyNotificationService.class);
            startService(intent);
            Toast.makeText(this.getApplicationContext(), "알림 읽기 서비스 - 시작됨", Toast.LENGTH_SHORT).show();
        }

        // 접근성 권한 확인 및 요청
        if (!checkAccessibilityPermission()) {
            final AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("접근성 권한 필요");
            builder.setMessage("접근성 권한이 필요합니다.\n\n설치된 서비스 -> 허용");
            builder.setPositiveButton("설정", (dialog, which) -> {
                startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
            });
            dialog = builder.create();
            dialog.show();
            return;
        }
    }

    // 서비스가 실행 중인지 확인하는 함수
    private boolean isMyServiceRunning(Class<?> serviceClass) {
        ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }

    // 알림 접근 권한이 있는지 확인하는 함수
    public boolean checkNotificationPermission(){
        final Set<String> sets = NotificationManagerCompat.getEnabledListenerPackages(this);
        return sets.contains(getApplicationContext().getPackageName());
    }

    // 접근성 권한이 있는지 확인하는 함수
    public boolean checkAccessibilityPermission() {
        final AccessibilityManager manager = (AccessibilityManager) getSystemService(Context.ACCESSIBILITY_SERVICE);
        final List<AccessibilityServiceInfo> list = manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_GENERIC);
        for (int i = 0; i < list.size(); i++) {
            final AccessibilityServiceInfo info = list.get(i);
            if (info.getResolveInfo().serviceInfo.packageName.equals(getApplication().getPackageName())) {
                return true;
            }
        }
        return false;
    }

    // 서버로 텍스트를 전송하는 함수
    private void sendTextToServer(String message) {
        try {
            // JSON 객체 생성
            JSONObject jsonObject = new JSONObject();
            jsonObject.put("text", message);

            // RequestBody 생성
            RequestBody requestBody = RequestBody.create(
                    MediaType.parse("application/json"), jsonObject.toString());

            // API 서비스 호출
            ApiService apiService = RetrofitClient.getApiService();
            Call<ResponseBody> call = apiService.sendText(requestBody);
            
            // 기존 서버 호출 로직 주석 처리
            /*
            call.enqueue(new Callback<ResponseBody>() {
                @Override
                public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                    if (response.isSuccessful()) {
                        try {
                            String responseText = response.body().string();
                            Log.d(TAG, "Server Response: " + responseText);

                            // 사기 의심 메시지인 경우 알림 생성
                            if (responseText.contains("사기")) {
                                showFraudAlert(message, responseText);
                            }

                            // ResultActivity로 결과 전달
                            Intent intent = new Intent(MainActivity.this, ResultActivity.class);
                            intent.putExtra("SERVER_RESPONSE", responseText);
                            startActivity(intent);
                        } catch (Exception e) {
                            Log.e(TAG, "Error reading server response", e);
                        }
                    } else {
                        Log.e(TAG, "Server request failed with code: " + response.code());
                    }
                }

                @Override
                public void onFailure(Call<ResponseBody> call, Throwable t) {
                    Log.e(TAG, "Server connection failed", t);
                    runOnUiThread(() -> {
                        Toast.makeText(MainActivity.this, "Failed to connect to server", Toast.LENGTH_SHORT).show();
                    });
                }
            });
            */

            // Mock response for testing without server
            String mockResponse = "{\"label\": \"사기\", \"confidence\": 0.87}"; // 가짜 서버 응답

            // 알림 생성
            if (mockResponse.contains("사기")) {
                showFraudAlert(message, mockResponse); // 사기 의심 메시지 감지 알림
            }

            // ResultActivity로 결과 전달
            Intent intent = new Intent(MainActivity.this, ResultActivity.class);
            intent.putExtra("SERVER_RESPONSE", mockResponse); // mockResponse 전달
            startActivity(intent);

        } catch (Exception e) {
            Log.e(TAG, "Error creating JSON object", e);
        }
    }

    // 사기 의심 메시지에 대한 알림을 생성하는 함수
    private void showFraudAlert(String message, String analysis) {
        NotificationManager notificationManager = 
            (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        
        // Android 8.0 이상을 위한 알림 채널 생성
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                "fraud_alert",
                "Fraud Alerts",
                NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("Alerts for suspected fraud messages");
            notificationManager.createNotificationChannel(channel);
        }

        // 알림 생성
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, "fraud_alert")
            .setSmallIcon(R.drawable.ic_warning) // warning 아이콘 필요
            .setContentTitle("⚠️ 사기 의심 메시지 감지 ⚠️")
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true);

        // ResultActivity를 열기 위한 PendingIntent 설정
        Intent intent = new Intent(this, ResultActivity.class);
        intent.putExtra("SERVER_RESPONSE", analysis); // 분석 결과 전달
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        builder.setContentIntent(pendingIntent);

        // 알림 표시
        notificationManager.notify(1, builder.build());
    }
}
