package com.example.kotlinbasics;

import android.graphics.Bitmap;
import android.util.Log;

import org.tensorflow.lite.DataType;
import org.tensorflow.lite.Interpreter;
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

    private static final int INPUT_SIZE = 224;
    private static final float PROBABILITY_THRESHOLD = 0.7f;
    private static final float IMAGE_MEAN = 0.0f;
    private static final float IMAGE_STD = 255.0f;

    private final Interpreter interpreter;
    private final ImageProcessor imageProcessor;
    private float lastProbability = 0f;

    public AntiSpoofingClassifier(File modelFile) throws IOException {
        Interpreter.Options options = new Interpreter.Options();
        options.setNumThreads(4);
        this.interpreter = new Interpreter(loadModelFile(modelFile), options);

        this.imageProcessor = new ImageProcessor.Builder()
                .add(new ResizeWithCropOrPadOp(INPUT_SIZE, INPUT_SIZE))
                .add(new ResizeOp(INPUT_SIZE, INPUT_SIZE, ResizeOp.ResizeMethod.BILINEAR))
                .add(new NormalizeOp(IMAGE_MEAN, IMAGE_STD))
                .build();

        Log.d(TAG, "Anti-spoofing model loaded successfully");
    }

    private MappedByteBuffer loadModelFile(File file) throws IOException {
        FileInputStream inputStream = new FileInputStream(file);
        FileChannel fileChannel = inputStream.getChannel();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, 0, fileChannel.size());
    }

    public boolean isRealFace(Bitmap bitmap) {
        if (bitmap == null) {
            Log.e(TAG, "Null input bitmap");
            return false;
        }

        try {
            TensorImage tensorImage = new TensorImage(DataType.FLOAT32);
            tensorImage.load(bitmap);

            tensorImage = imageProcessor.process(tensorImage);

            TensorBuffer outputBuffer = TensorBuffer.createFixedSize(
                    new int[]{1, 1}, DataType.FLOAT32);

            interpreter.run(tensorImage.getBuffer(), outputBuffer.getBuffer());

            lastProbability = outputBuffer.getFloatArray()[0];
            Log.d(TAG, "Anti-spoofing probability: " + lastProbability);

            return lastProbability > PROBABILITY_THRESHOLD;
        } catch (Exception e) {
            Log.e(TAG, "Error during anti-spoofing classification", e);
            lastProbability = 0f;
            return false;
        }
    }

    public float getSpoofProbability(Bitmap bitmap) {
        if (bitmap == null) {
            Log.e(TAG, "Null input bitmap");
            return Float.NaN;
        }

        try {
            TensorImage tensorImage = new TensorImage(DataType.FLOAT32);
            tensorImage.load(bitmap);

            tensorImage = imageProcessor.process(tensorImage);

            TensorBuffer outputBuffer = TensorBuffer.createFixedSize(
                    new int[]{1, 1}, DataType.FLOAT32);

            interpreter.run(tensorImage.getBuffer(), outputBuffer.getBuffer());

            lastProbability = outputBuffer.getFloatArray()[0];

            return lastProbability;
        } catch (Exception e) {
            Log.e(TAG, "Error during anti-spoofing classification", e);
            lastProbability = 0f;
            return Float.NaN;
        }
    }

    public float getLastProbability() {
        return lastProbability;
    }

    public void close() {
        if (interpreter != null) {
            interpreter.close();
            Log.d(TAG, "Anti-spoofing model closed");
        }
    }
}