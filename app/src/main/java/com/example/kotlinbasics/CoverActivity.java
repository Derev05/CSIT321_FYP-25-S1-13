package com.example.kotlinbasics;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import androidx.appcompat.app.AppCompatActivity;
public class CoverActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_cover);

        Button startButton = findViewById(R.id.startButton);

        startButton.setOnClickListener(view -> {
            Intent intent = new Intent(CoverActivity.this, LoginActivity.class);
            startActivity(intent);
        });
    }
}

