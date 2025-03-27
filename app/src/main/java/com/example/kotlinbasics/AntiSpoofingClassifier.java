package com.example.kotlinbasics;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.Log;

import org.tensorflow.lite.DataType;
import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.support.common.FileUtil;
import org.tensorflow.lite.support.common.ops.NormalizeOp;
import org.tensorflow.lite.support.image.ImageProcessor;
import org.tensorflow.lite.support.image.TensorImage;
import org.tensorflow.lite.support.image.ops.ResizeOp;
import org.tensorflow.lite.support.image.ops.ResizeWithCropOrPadOp;
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;

public class AntiSpoofingClassifier {
    private static final String TAG = "AntiSpoofing";

    // Model configuration - adjust these based on your model's requirements
    private static final int INPUT_SIZE = 224; // Expected input size
    private static final float PROBABILITY_THRESHOLD = 0.8f; // Confidence threshold
    private static final float IMAGE_MEAN = 0.0f; // Normalization mean
    private static final float IMAGE_STD = 255.0f; // Normalization std

    private final Interpreter interpreter;
    private final ImageProcessor imageProcessor;

    public AntiSpoofingClassifier(File modelFile) throws IOException {
        // Initialize TFLite interpreter
        Interpreter.Options options = new Interpreter.Options();
        options.setNumThreads(4); // Use 4 threads for inference
        this.interpreter = new Interpreter(loadModelFile(modelFile), options);

        // Create image processor for preprocessing
        this.imageProcessor = new ImageProcessor.Builder()
                .add(new ResizeWithCropOrPadOp(INPUT_SIZE, INPUT_SIZE)) // Crop or pad to square
                .add(new ResizeOp(INPUT_SIZE, INPUT_SIZE, ResizeOp.ResizeMethod.BILINEAR))
                .add(new NormalizeOp(IMAGE_MEAN, IMAGE_STD)) // Normalize to [-1,1] or [0,1]
                .build();

        Log.d(TAG, "Anti-spoofing model loaded successfully");
    }

    // Load model file from storage
    private MappedByteBuffer loadModelFile(File file) throws IOException {
        FileInputStream inputStream = new FileInputStream(file);
        FileChannel fileChannel = inputStream.getChannel();
        long startOffset = 0;
        long declaredLength = fileChannel.size();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }

    // Classify whether the face is real or spoof
    public boolean isRealFace(Bitmap bitmap) {
        if (bitmap == null) {
            Log.e(TAG, "Null input bitmap");
            return false;
        }

        try {
            // Convert bitmap to TensorImage
            TensorImage tensorImage = new TensorImage(DataType.FLOAT32);
            tensorImage.load(bitmap);

            // Preprocess the image
            tensorImage = imageProcessor.process(tensorImage);

            // Create output tensor
            TensorBuffer outputBuffer = TensorBuffer.createFixedSize(
                    new int[]{1, 1}, DataType.FLOAT32);

            // Run inference
            interpreter.run(tensorImage.getBuffer(), outputBuffer.getBuffer());

            // Get prediction score (probability of being real)
            float probability = outputBuffer.getFloatArray()[0];
            Log.d(TAG, "Anti-spoofing probability: " + probability);

            // Return true if probability exceeds threshold
            return probability > PROBABILITY_THRESHOLD;
        } catch (Exception e) {
            Log.e(TAG, "Error during anti-spoofing classification", e);
            return false; // Default to false if error occurs
        }
    }

    // Close the interpreter when done
    public void close() {
        if (interpreter != null) {
            interpreter.close();
            Log.d(TAG, "Anti-spoofing model closed");
        }
    }
}