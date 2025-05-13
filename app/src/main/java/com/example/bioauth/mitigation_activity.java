package com.example.bioauth;

import android.content.Intent;
import android.os.Bundle;
import android.widget.LinearLayout;
import androidx.appcompat.app.AppCompatActivity;

public class mitigation_activity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.engine_manage);

        // ✅ Run Engine opens ManageLogsActivity
        LinearLayout engine = findViewById(R.id.run_engineButton);
        engine.setOnClickListener(v -> {
            Intent intent = new Intent(mitigation_activity.this, ManageLogsActivity.class);
            startActivity(intent);
        });

        // ✅ Manage List opens ManageLogsActivity too
        LinearLayout manage = findViewById(R.id.manageListButton);
        manage.setOnClickListener(v -> {
            Intent intent = new Intent(mitigation_activity.this, DeleteLogsActivity.class);
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
