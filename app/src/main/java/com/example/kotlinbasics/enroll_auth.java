package com.example.kotlinbasics;

import android.content.Intent;
import android.os.Bundle;
import android.widget.LinearLayout;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdView;
import androidx.appcompat.app.AppCompatActivity;

public class enroll_auth extends AppCompatActivity {

    private LinearLayout enrollBtn, authBtn;
    private AdView adview;
    private AdRequest adrequest;

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

        // You can add authBtn logic here if needed
        authBtn.setOnClickListener(v ->{
            Intent authIntent = new Intent(enroll_auth.this, FaceAuthActivity.class);
            startActivity(authIntent);
        });
    }

    @Override
    public void onBackPressed() {
        // Go back to MainMenu instead of default behavior
        Intent intent = new Intent(enroll_auth.this, MainMenu.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finish(); // close current activity
    }
}
