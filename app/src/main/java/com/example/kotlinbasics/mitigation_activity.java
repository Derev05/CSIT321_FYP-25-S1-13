package com.example.kotlinbasics;

import android.content.Intent;
import android.media.Image;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.LinearLayout;
import androidx.appcompat.app.AppCompatActivity;

public class mitigation_activity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.engine_manage);

        // ✅ Run Engine button logic
        LinearLayout engine = findViewById(R.id.run_engineButton);
        engine.setOnClickListener(v -> {
            Intent intent = new Intent(mitigation_activity.this, LogViewerActivity.class);
            startActivity(intent);
        });

        // ✅ Manage List button now opens ManageLogsActivity
        LinearLayout manage = findViewById(R.id.manageListButton);
        manage.setOnClickListener(v -> {
            Intent intent = new Intent(mitigation_activity.this, ManageLogsActivity.class);
            startActivity(intent);
        });
    }

    @Override
    public void onBackPressed() {
        Intent intent = new Intent(mitigation_activity.this, MainMenu.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        finish(); // Optional
    }
}
