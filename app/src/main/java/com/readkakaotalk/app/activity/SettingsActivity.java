package com.readkakaotalk.app.activity;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.SeekBar;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import com.readkakaotalk.app.R;

public class SettingsActivity extends AppCompatActivity {

    private SeekBar fraudThresholdSeekBar;
    private TextView fraudText;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        // 툴바 설정 (뒤로가기 버튼)
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> onBackPressed());

        prefs = getSharedPreferences("settings", MODE_PRIVATE);

        fraudThresholdSeekBar = findViewById(R.id.fraudThresholdSeekBar);
        fraudText = findViewById(R.id.fraudText);

        // 저장된 값 불러오기 (없으면 기본값 70%)
        float fraudThreshold = prefs.getFloat("fraud_threshold", 0.7f);
        initSeekBar(fraudThresholdSeekBar, fraudText, "사기 탐지 민감도", "fraud_threshold", fraudThreshold);
    }

    private void initSeekBar(SeekBar bar, TextView label, String labelPrefix, String key, float initValue) {
        int initialProgress = (int)(initValue * 100);
        bar.setProgress(initialProgress);
        label.setText(labelPrefix + ": " + initialProgress + "%");

        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                // 실시간으로 텍스트 업데이트
                label.setText(labelPrefix + ": " + progress + "%");
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                // 터치가 끝나면 값 저장
                float val = seekBar.getProgress() / 100f;
                prefs.edit().putFloat(key, val).apply();
            }
        });
    }
}
