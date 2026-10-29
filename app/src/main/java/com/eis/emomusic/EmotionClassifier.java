package com.eis.emomusic;

import android.app.Activity;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;

import org.tensorflow.lite.DataType;
import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.support.image.ImageProcessor;
import org.tensorflow.lite.support.image.TensorImage;
import org.tensorflow.lite.support.image.ops.ResizeOp;
import org.tensorflow.lite.support.common.ops.NormalizeOp;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;

public class EmotionClassifier {

    private static final int IMG_SIZE = 224; 
    private static final int NUM_CLASSES = 7;
    private static final String MODEL_FILE = "mobilevit_emotion.tflite";

    public static final String[] LABELS = {"Angry", "Disgust", "Fear", "Happy", "Sad", "Surprise", "Neutral"};
    public static final String[] EMOJIS = {"😠", "🤢", "😨", "😄", "😢", "😲", "😐"};
    public static final int[] COLORS = {0xFFE53935, 0xFFFF6F00, 0xFF7B1FA2, 0xFFFDD835, 0xFF1E88E5, 0xFFE91E63, 0xFF757575};

    private final Interpreter interpreter;
    private final ImageProcessor imageProcessor;

    public EmotionClassifier(Activity activity) throws IOException {
        Interpreter.Options options = new Interpreter.Options();
        options.setNumThreads(4);
        interpreter = new Interpreter(loadModelFile(activity), options);

        // Optimized preprocessing using TFLite Support Library
        // normalization: (x - 0) / 255.0 = x / 255.0 (range 0-1)
        imageProcessor = new ImageProcessor.Builder()
                .add(new ResizeOp(IMG_SIZE, IMG_SIZE, ResizeOp.ResizeMethod.BILINEAR))
                .add(new NormalizeOp(0f, 255f)) 
                .build();
    }

    private MappedByteBuffer loadModelFile(Activity activity) throws IOException {
        AssetFileDescriptor fileDescriptor = activity.getAssets().openFd(MODEL_FILE);
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, fileDescriptor.getStartOffset(), fileDescriptor.getDeclaredLength());
    }

    public EmotionResult classify(Bitmap bitmap) {
        if (interpreter == null) return null;

        TensorImage tensorImage = new TensorImage(DataType.FLOAT32);
        tensorImage.load(bitmap);
        tensorImage = imageProcessor.process(tensorImage);

        float[][] output = new float[1][NUM_CLASSES];
        interpreter.run(tensorImage.getBuffer(), output);

        float[] probabilities = output[0];
        int topIndex = 0;
        for (int i = 1; i < NUM_CLASSES; i++) {
            if (probabilities[i] > probabilities[topIndex]) {
                topIndex = i;
            }
        }

        return new EmotionResult(LABELS[topIndex], EMOJIS[topIndex], COLORS[topIndex], probabilities, topIndex);
    }

    public void close() {
        if (interpreter != null) interpreter.close();
    }
}
