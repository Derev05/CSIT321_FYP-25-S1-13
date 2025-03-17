package com.example.kotlinbasics;

import android.content.Intent;
import android.os.Bundle;
import android.text.Html;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
public class enroll_auth extends AppCompatActivity{

    private LinearLayout enrollBtn, authBtn;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.enroll_authenticate);

        enrollBtn = findViewById(R.id.enroll_face_button);
        authBtn = findViewById(R.id.auth_face_button);

        enrollBtn.setOnClickListener(v -> {
            Intent enrollIntent = new Intent(enroll_auth.this, FaceEnrollActivity.class);
            startActivity(enrollIntent);
        });

    }

}
