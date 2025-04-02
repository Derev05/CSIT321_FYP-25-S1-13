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

import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

public class FaceConfirmActivity extends AppCompatActivity {

    private ImageView faceImageView;
    private Button confirmBtn, cancelBtn;
    private Bitmap faceBitmap;
    private FaceEmbedManager embedManager;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_face_confirm);

        faceImageView = findViewById(R.id.detected_face_image_view);
        confirmBtn = findViewById(R.id.confirm_button);
        cancelBtn = findViewById(R.id.cancel_button);

        embedManager = new FaceEmbedManager(this,"face_recognition_sface_2021dec.onnx");

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
            runFaceRecognition(faceBitmap, embedManager);
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

    private void runFaceRecognition(Bitmap faceBitmap, FaceEmbedManager embedManager) {
        try {
            // Preprocess the image
            Mat mat = new Mat();
            Utils.bitmapToMat(faceBitmap, mat);
            Size inputSize = new Size(112, 112);
            Imgproc.resize(mat, mat, inputSize);
            Imgproc.cvtColor(mat, mat, Imgproc.COLOR_RGB2BGR);
            mat.convertTo(mat, CvType.CV_32F, 1.0 / 255.0);

            // SFace specific preprocessing
            Core.subtract(mat, new Scalar(127.5, 127.5, 127.5), mat);
            Core.divide(mat, new Scalar(128, 128, 128), mat);

            // Convert the image to a float array in [1, 3, 112, 112] shape
            float[] inputArray = new float[1 * 3 * 112 * 112];
            int idx = 0;
            for (int c = 0; c < 3; c++) { // Channels (RGB)
                for (int h = 0; h < 112; h++) { // Height
                    for (int w = 0; w < 112; w++) { // Width
                        double[] pixel = mat.get(h, w);
                        inputArray[idx++] = (float) pixel[c]; // Fill the array in CHW format
                    }
                }
            }

            // Save or process the embeddings
            float[] embedding = embedManager.generateEmbedding(inputArray);
            embedManager.uploadUserEmbedding(embedding);

        } catch (Exception e) {
            Log.e("FaceConfirmActivity", "Error running face recognition", e);
            Toast.makeText(this, "Error running face recognition", Toast.LENGTH_SHORT).show();
        }
    }
}