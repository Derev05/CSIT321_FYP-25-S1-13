package com.example.kotlinbasics;

import android.Manifest;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.OpenCVLoader;
import org.opencv.android.JavaCameraView;
import org.opencv.android.Utils;
import org.opencv.core.Mat;
import org.opencv.core.MatOfRect;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import org.opencv.objdetect.CascadeClassifier;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class FaceEnrollActivity extends AppCompatActivity implements CameraBridgeViewBase.CvCameraViewListener2 {

    private static final int CAMERA_PERMISSION_REQUEST_CODE = 200;
    private JavaCameraView javaCameraView;
    private Button enrollFaceButton;
    private Mat mRgba, grayFrame;
    private CascadeClassifier faceDetector;
    private Rect detectedFace;
    private int absoluteFaceSize;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera_enroll);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);

        javaCameraView = findViewById(R.id.camera_view);
        enrollFaceButton = findViewById(R.id.enroll_face_button);

        if(!OpenCVLoader.initDebug()){
            Log.e("OpenCV", "Initialization failed.");
        } else {
            Log.d("OpenCV", "Initialization successful.");
            requestCameraPermission();
        }

        enrollFaceButton.setOnClickListener(v -> {
            if(detectedFace != null) {
                captureAndProceed();
            } else {
                Toast.makeText(this, "No face detected, try again.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void requestCameraPermission() {
        if(ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION_REQUEST_CODE);
        } else {
            enableCamera();
        }
    }

    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults){
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if(requestCode == CAMERA_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                enableCamera();
            } else {
                Toast.makeText(this, "Camera permission denied.", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void enableCamera() {
        javaCameraView.setCameraPermissionGranted();
        javaCameraView.setCameraIndex(1);
        javaCameraView.setCvCameraViewListener(this);
        javaCameraView.enableView();
        loadCascade();
    }

    @Override
    public void onCameraViewStarted(int width, int height) {
        mRgba = new Mat();
        grayFrame = new Mat();
        absoluteFaceSize = (int) (height * 0.4);

        DisplayMetrics displayMetrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
        int screenWidth = displayMetrics.widthPixels;
        int screenHeight = displayMetrics.heightPixels;

        javaCameraView.setMaxFrameSize(screenWidth,screenHeight);
    }

    @Override
    public void onCameraViewStopped() {
        mRgba.release();
        grayFrame.release();
    }

    @Override
    public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame) {
        mRgba = inputFrame.rgba();
        Imgproc.cvtColor(mRgba, grayFrame, Imgproc.COLOR_RGBA2GRAY);

        MatOfRect faces = new MatOfRect();
        if (faceDetector != null) {
            faceDetector.detectMultiScale(grayFrame, faces, 1.1, 2, 2,
                    new Size(absoluteFaceSize,absoluteFaceSize), new Size());
        }

        Rect[] facesArray = faces.toArray();
        if(facesArray.length > 0) {
            detectedFace = facesArray[0];
            Imgproc.rectangle(mRgba, detectedFace.tl(), detectedFace.br(), new Scalar(255,255,0,255), 3);
        } else {
            detectedFace = null;
        }
        return mRgba;
    }

    private void captureAndProceed() {
        if(mRgba == null || detectedFace == null) return;

        Mat croppedFace = new Mat(mRgba,detectedFace);
        Bitmap faceBitmap = Bitmap.createBitmap(croppedFace.cols(), croppedFace.rows(), Bitmap.Config.ARGB_8888);
        Utils.matToBitmap(croppedFace,faceBitmap);

        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        faceBitmap.compress(Bitmap.CompressFormat.PNG, 100, byteArrayOutputStream);
        byte[] byteArray = byteArrayOutputStream.toByteArray();

        Intent intent = new Intent(FaceEnrollActivity.this, FaceConfirmActivity.class);
        intent.putExtra("face", new SerializableRect(detectedFace));
        intent.putExtra("image", byteArray);
        startActivity(intent);
    }

    private void loadCascade() {
        try {
            InputStream is = getResources().openRawResource(R.raw.haarcascade_frontalface_default);
            File cascadeDir = getDir("cascade", MODE_PRIVATE);
            File cascadeFile = new File(cascadeDir, "haarcascade_frontalface_default.xml");

            FileOutputStream os = new FileOutputStream(cascadeFile);
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = is.read(buffer)) != -1) {
                os.write(buffer, 0 ,bytesRead);
            }

            os.close();
            is.close();

            faceDetector = new CascadeClassifier(cascadeFile.getAbsolutePath());
            if(faceDetector.empty()) {
                Log.e("Cascade", "Failed to load face detector.");
            } else {
                Log.d("Cascade", "Face detector loaded successfully.");
            }

        } catch (IOException e){
            e.printStackTrace();
            Log.e("Cascade", "Error loading cascade:" +  e.getMessage());
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if(OpenCVLoader.initDebug()){
            if(javaCameraView != null) {
                javaCameraView.enableView();
            }
        } else {
            Log.e("OpenCV", "Initialization failed.");
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if(javaCameraView != null) {
            javaCameraView.disableView();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (javaCameraView != null){
            javaCameraView.disableView();
        }
    }

}
