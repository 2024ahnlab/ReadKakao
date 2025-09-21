package com.readkakaotalk.app.activity;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.readkakaotalk.app.R;

public class SettingsActivity extends AppCompatActivity {

    private SeekBar fraudThresholdSeekBar;
    private TextView fraudText;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        prefs = getSharedPreferences("settings", MODE_PRIVATE);

        fraudThresholdSeekBar = findViewById(R.id.fraudThresholdSeekBar);
        fraudText = findViewById(R.id.fraudText);

        // "fraud_threshold" 키로 저장된 값을 불러옴 (기본값 0.7)
        float fraudThreshold = prefs.getFloat("fraud_threshold", 0.7f);

        initSeekBar(fraudThresholdSeekBar, fraudText, "위험 점수", "fraud_threshold", fraudThreshold);
    }

    private void initSeekBar(SeekBar bar, TextView label, String labelPrefix, String key, float initValue) {
        bar.setProgress((int)(initValue * 100));
        label.setText(String.format("%s: %.2f", labelPrefix, initValue));

        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                float val = progress / 100f;
                label.setText(String.format("%s: %.2f", labelPrefix, val));
            }

            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {
                float val = seekBar.getProgress() / 100f;
                // 변경된 값을 "fraud_threshold" 키로 저장
                prefs.edit().putFloat(key, val).apply();
            }
        });
    }
}
