package com.readkakaotalk.app.activity;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.readkakaotalk.app.R;

public class SettingsActivity extends AppCompatActivity {

    private SeekBar ratioSeekBar, fraudThresholdSeekBar, emotionThresholdSeekBar;
    private TextView ratioText, fraudText, emotionText;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        prefs = getSharedPreferences("settings", MODE_PRIVATE);

        ratioSeekBar = findViewById(R.id.ratioSeekBar);
        fraudThresholdSeekBar = findViewById(R.id.fraudThresholdSeekBar);
        emotionThresholdSeekBar = findViewById(R.id.emotionThresholdSeekBar);

        ratioText = findViewById(R.id.ratioText);
        fraudText = findViewById(R.id.fraudText);
        emotionText = findViewById(R.id.emotionText);

        float modelRatio = prefs.getFloat("model_ratio", 0.5f);
        float fraudThreshold = prefs.getFloat("fraud_threshold", 0.7f);
        float emotionThreshold = prefs.getFloat("emotion_threshold", 0.5f);

        initSeekBar(ratioSeekBar, ratioText, "모델 비율", "model_ratio", modelRatio);
        initSeekBar(fraudThresholdSeekBar, fraudText, "사기 임계값", "fraud_threshold", fraudThreshold);
        initSeekBar(emotionThresholdSeekBar, emotionText, "감정 임계값", "emotion_threshold", emotionThreshold);
    }

    private void initSeekBar(SeekBar bar, TextView label, String labelPrefix, String key, float initValue) {
        bar.setProgress((int)(initValue * 100));
        label.setText(labelPrefix + ": " + initValue);

        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                float val = progress / 100f;
                label.setText(labelPrefix + ": " + val);
            }

            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {
                float val = seekBar.getProgress() / 100f;
                prefs.edit().putFloat(key, val).apply();
            }
        });
    }
}
