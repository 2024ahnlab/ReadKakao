package com.readkakaotalk.app.activity;

import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.readkakaotalk.app.R;

public class ResultActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_result);

        TextView resultView = findViewById(R.id.textViewResult);
        String result = getIntent().getStringExtra("prediction_result");
        if (result != null) {
            resultView.setText("예측 결과: " + result);
        } else {
            resultView.setText("결과 없음");
        }
    }
}
