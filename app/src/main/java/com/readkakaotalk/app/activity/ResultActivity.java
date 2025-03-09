package com.readkakaotalk.app.activity;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

import com.readkakaotalk.app.R;

// 서버 응답 결과를 표시하는 액티비티
public class ResultActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_result);

        // Intent로 전달된 데이터를 받음
        String serverResponse = getIntent().getStringExtra("SERVER_RESPONSE");

        // 결과를 TextView에 표시
        TextView resultTextView = findViewById(R.id.textViewResult);
        resultTextView.setText(serverResponse != null ? serverResponse : "No response received");

        // 메인 화면으로 돌아가는 버튼 설정
        Button buttonBackToMain = findViewById(R.id.buttonBackToMain);
        buttonBackToMain.setOnClickListener(v -> {
            Intent intent = new Intent(ResultActivity.this, MainActivity.class);
            startActivity(intent);
        });
    }
}
