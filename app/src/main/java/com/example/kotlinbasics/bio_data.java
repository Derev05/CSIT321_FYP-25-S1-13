package com.example.kotlinbasics;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;
    public class bio_data extends AppCompatActivity {
        @Override
        protected void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            setContentView(R.layout.activity_terms_condition);

            TextView termsCondition = findViewById(R.id.termsText);
            termsCondition.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    // ✅ Start Terms & Conditions Activity
                    Intent intent = new Intent(bio_data.this, activity_terms_condition.class);
                    startActivity(intent);
                }
            });

        }
    }