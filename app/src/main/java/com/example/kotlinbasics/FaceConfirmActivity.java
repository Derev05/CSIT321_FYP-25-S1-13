package com.example.kotlinbasics;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

public class FaceConfirmActivity extends AppCompatActivity {

    private ImageView faceImageView;
    private Button confirmBtn, cancelBtn;
    private Bitmap faceBitmap;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_face_confirm);

        faceImageView = findViewById(R.id.detected_face_image_view);
        confirmBtn = findViewById(R.id.confirm_button);
        cancelBtn = findViewById(R.id.cancel_button);

        Intent intent = getIntent();
        byte[] byteArray = intent.getByteArrayExtra("image");

        if (byteArray != null) {
            faceBitmap = BitmapFactory.decodeByteArray(byteArray, 0, byteArray.length);
            faceImageView.setImageBitmap(faceBitmap);
        } else {
            Log.e("FaceConfirmActivity", "No face image received.");
            Toast.makeText(this, "Error loading face image", Toast.LENGTH_SHORT).show();
        }

        confirmBtn.setOnClickListener(v -> {
            Toast.makeText(FaceConfirmActivity.this, "Face Confirmed!", Toast.LENGTH_SHORT).show();
            Intent confirmintent = new Intent(FaceConfirmActivity.this, FaceEnrollActivity.class);
            confirmintent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(confirmintent);
            finish();
        });

        cancelBtn.setOnClickListener(v -> {
            Toast.makeText(FaceConfirmActivity.this, "Face Rejected!", Toast.LENGTH_SHORT).show();
            Intent cancelintent = new Intent(FaceConfirmActivity.this, FaceEnrollActivity.class);
            cancelintent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(cancelintent);
            finish();
        });
    }
}
