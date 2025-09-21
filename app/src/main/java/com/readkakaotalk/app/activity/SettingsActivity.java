package com.readkakaotalk.app.activity;

import android.os.Bundle;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.Switch;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceManager;

import com.readkakaotalk.app.R;

public class SettingsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings); // TODO(id) 네 설정 레이아웃

        final android.content.SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);

        // 감정 모델 사용 스위치
        Switch useEmotionSwitch = findViewById(R.id.switchUseEmotion); // TODO(id)
        boolean useEmotion = prefs.getBoolean(MainActivity.KEY_USE_EMOTION_MODEL, false);
        useEmotionSwitch.setChecked(useEmotion);
        useEmotionSwitch.setOnCheckedChangeListener((CompoundButton buttonView, boolean isChecked) ->
                prefs.edit().putBoolean(MainActivity.KEY_USE_EMOTION_MODEL, isChecked).apply()
        );

        // 임계값 입력(사기탐지용)
        EditText thresholdEdit = findViewById(R.id.editThreshold); // TODO(id)
        float th = prefs.getFloat(MainActivity.KEY_THRESHOLD, 0.6f);
        thresholdEdit.setText(String.valueOf(th));

        findViewById(R.id.buttonSave).setOnClickListener(v -> { // TODO(id)
            try {
                float newTh = Float.parseFloat(thresholdEdit.getText().toString().trim());
                prefs.edit().putFloat(MainActivity.KEY_THRESHOLD, newTh).apply();
                finish();
            } catch (Exception e) {
                // 잘못된 값이면 무시하거나 에러 표시
                thresholdEdit.setError("0.0 ~ 1.0");
            }
        });
    }
}
