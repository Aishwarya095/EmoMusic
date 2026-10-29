package com.eis.emomusic;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.eis.emomusic.databinding.ActivityMainBinding;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceDetection;
import com.google.mlkit.vision.face.FaceDetector;
import com.google.mlkit.vision.face.FaceDetectorOptions;

import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private static final int PERMISSION_REQUEST_CODE = 1001;

    private ActivityMainBinding binding;
    private EmotionClassifier classifier;
    private SpotifyManager spotifyManager;
    private FaceDetector faceDetector;
    private ExecutorService cameraExecutor;
    private Bitmap lastFrame;
    
    private boolean isScanning = false;
    private boolean isProcessing = false;
    private float[] accumulatedProbs = new float[7];
    private int samplingSecondsLeft = 5;
    private EmotionResult lastStableResult;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        cameraExecutor = Executors.newSingleThreadExecutor();
        spotifyManager = new SpotifyManager();

        initFaceDetector();
        try {
            classifier = new EmotionClassifier(this);
            Log.d(TAG, "Classifier loaded OK");
        } catch (Exception e) {
            Log.e(TAG, "Failed to init classifier", e);
            Toast.makeText(this, "AI Model failed to load: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }

        binding.btnScanAgain.setOnClickListener(v -> startScanFlow());
        binding.btnRetake.setOnClickListener(v -> startScanFlow());
        binding.btnSpotifyPlayNow.setOnClickListener(v -> {
            if (lastStableResult != null) {
                playMusic(lastStableResult);
            }
        });

        if (allPermissionsGranted()) {
            startCamera();
            spotifyManager.login(this);
            startScanFlow();
        } else {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, PERMISSION_REQUEST_CODE);
        }
    }

    private void initFaceDetector() {
        FaceDetectorOptions options =
                new FaceDetectorOptions.Builder()
                        .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                        .setContourMode(FaceDetectorOptions.CONTOUR_MODE_NONE)
                        .build();
        faceDetector = FaceDetection.getClient(options);
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);
        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(binding.previewView.getSurfaceProvider());

                ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                        .build();

                imageAnalysis.setAnalyzer(cameraExecutor, image -> {
                    Bitmap bitmap = toBitmap(image);
                    lastFrame = bitmap;

                    if (isScanning && !isProcessing) {
                        isProcessing = true;
                        processRealTimeFrame(bitmap);
                    }
                    image.close();
                });

                CameraSelector cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA;
                cameraProvider.unbindAll();
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalysis);
            } catch (ExecutionException | InterruptedException e) {
                Log.e(TAG, "Use case binding failed", e);
                runOnUiThread(() -> Toast.makeText(this, "Camera binding failed", Toast.LENGTH_SHORT).show());
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private Bitmap toBitmap(ImageProxy image) {
        Bitmap bitmap = Bitmap.createBitmap(image.getWidth(), image.getHeight(), Bitmap.Config.ARGB_8888);
        bitmap.copyPixelsFromBuffer(image.getPlanes()[0].getBuffer());
        Matrix matrix = new Matrix();
        matrix.postRotate(image.getImageInfo().getRotationDegrees());
        if (image.getImageInfo().getRotationDegrees() == 270 || image.getImageInfo().getRotationDegrees() == 90) {
            matrix.postScale(-1, 1); // Mirror for front camera
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
    }

    private void startScanFlow() {
        isScanning = true;
        isProcessing = false;
        accumulatedProbs = new float[7];
        samplingSecondsLeft = 5;
        lastStableResult = null;
        
        binding.spotifyPopup.setVisibility(View.GONE);
        binding.monitoringCard.setVisibility(View.VISIBLE);
        binding.btnScanAgain.setVisibility(View.GONE);
        binding.tvMainLabel.setText("Detected Mood: ...");
        binding.tvSamplingTimer.setText("Sampling: 5s left");
        
        binding.countdownView.start(5, this::finalizeMonitoring);
        startSamplingTimer();
    }
    
    private void startSamplingTimer() {
        final Handler handler = new Handler(Looper.getMainLooper());
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (!isScanning) return;
                samplingSecondsLeft--;
                if (samplingSecondsLeft >= 0) {
                    binding.tvSamplingTimer.setText("Sampling: " + samplingSecondsLeft + "s left");
                    if (samplingSecondsLeft > 0) {
                        handler.postDelayed(this, 1000);
                    }
                }
            }
        }, 1000);
    }

    private void finalizeMonitoring() {
        isScanning = false;
        binding.tvSamplingTimer.setText("Sampling: 0s left");
        
        int topIdx = 0;
        for (int i = 1; i < 7; i++) {
            if (accumulatedProbs[i] > accumulatedProbs[topIdx]) topIdx = i;
        }
        
        if (accumulatedProbs[topIdx] > 0.05f) { // Confidence filtering: must have some detection
            EmotionResult finalResult = new EmotionResult(
                EmotionClassifier.LABELS[topIdx],
                EmotionClassifier.EMOJIS[topIdx],
                EmotionClassifier.COLORS[topIdx],
                accumulatedProbs,
                topIdx
            );
            lastStableResult = finalResult;
            showRecommendationPopup(finalResult);
        } else {
            Toast.makeText(this, "Mood detection uncertain. Retrying...", Toast.LENGTH_SHORT).show();
            startScanFlow();
        }
    }

    private void processRealTimeFrame(Bitmap bitmap) {
        if (faceDetector == null || classifier == null) {
            isProcessing = false;
            return;
        }

        InputImage image = InputImage.fromBitmap(bitmap, 0);
        faceDetector.process(image)
                .addOnSuccessListener(faces -> {
                    if (faces.isEmpty()) {
                        isProcessing = false;
                        runOnUiThread(() -> {
                            binding.tvMainLabel.setText("Detected Mood: Looking for face...");
                        });
                        return;
                    }

                    Face face = faces.get(0);
                    Rect box = face.getBoundingBox();

                    // Crop with safety bounds
                    int left = Math.max(0, box.left);
                    int top = Math.max(0, box.top);
                    int width = Math.min(bitmap.getWidth() - left, box.width());
                    int height = Math.min(bitmap.getHeight() - top, box.height());
                    
                    if (width <= 0 || height <= 0) {
                        isProcessing = false;
                        return;
                    }

                    Bitmap faceCrop = Bitmap.createBitmap(bitmap, left, top, width, height);
                    
                    cameraExecutor.execute(() -> {
                        EmotionResult result = classifier.classify(faceCrop);
                        if (result != null) {
                            runOnUiThread(() -> updateLiveUI(result));
                        }
                        isProcessing = false;
                    });
                })
                .addOnFailureListener(e -> isProcessing = false);
    }

    private void updateLiveUI(EmotionResult result) {
        if (!isScanning) return;

        // Exponential moving average for temporal smoothing
        for (int i = 0; i < 7; i++) {
            accumulatedProbs[i] = (accumulatedProbs[i] * 0.7f) + (result.probabilities[i] * 0.3f);
        }

        binding.tvMainLabel.setText("Detected Mood: " + result.label + " (" + result.getConfidencePercent() + "%)");
        setupEmotionBars(new EmotionResult(
                result.label, result.emoji, result.color, accumulatedProbs, result.topIndex
        ));
    }

    private void showRecommendationPopup(EmotionResult result) {
        binding.monitoringCard.setVisibility(View.VISIBLE);
        binding.spotifyPopup.setVisibility(View.VISIBLE);
        binding.btnScanAgain.setVisibility(View.VISIBLE);
        
        binding.tvPopupMood.setText("Mood Detected: " + result.label + " " + result.emoji);
        binding.tvPopupRecommendation.setText("Recommended: " + spotifyManager.getGenreTag(result.label));
        
        // Auto-play trigger after 2 seconds
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (binding.spotifyPopup.getVisibility() == View.VISIBLE && lastStableResult == result) {
                playMusic(result);
            }
        }, 2000);
    }

    private void setupEmotionBars(EmotionResult result) {
        binding.emotionBarContainer.removeAllViews();
        for (int i = 0; i < EmotionClassifier.LABELS.length; i++) {
            View itemView = getLayoutInflater().inflate(R.layout.emotion_bar_item, binding.emotionBarContainer, false);
            TextView tvName = itemView.findViewById(R.id.tvEmotionName);
            TextView tvPercent = itemView.findViewById(R.id.tvEmotionPercent);
            ProgressBar pb = itemView.findViewById(R.id.pbEmotion);

            String label = EmotionClassifier.LABELS[i];
            int prob = Math.round(result.probabilities[i] * 100);

            tvName.setText(label);
            tvPercent.setText(prob + "%");
            pb.setProgress(prob);

            if (i == result.topIndex) {
                tvName.setTextColor(result.color);
                pb.getProgressDrawable().setTint(result.color);
            }

            binding.emotionBarContainer.addView(itemView);
        }
    }

    private void playMusic(EmotionResult result) {
        if (!spotifyManager.isConnected()) {
            spotifyManager.login(this);
            return;
        }

        spotifyManager.searchTracks(result.label, tracks -> {
            if (!tracks.isEmpty()) {
                spotifyManager.openTrack(this, tracks.get(0).uri);
            }
            
            // Still show the bottom sheet for more options
            MusicBottomSheet sheet = new MusicBottomSheet(
                    result.label,
                    spotifyManager.getGenreTag(result.label),
                    tracks,
                    spotifyManager
            );
            sheet.show(getSupportFragmentManager(), "music");
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (spotifyManager.handleAuthResponse(requestCode, resultCode, data)) {
            binding.spotifyStatusDot.setBackgroundResource(R.drawable.status_dot_connected);
        }
    }

    private boolean allPermissionsGranted() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE && allPermissionsGranted()) {
            startCamera();
            spotifyManager.login(this);
            startScanFlow();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (classifier != null) classifier.close();
        cameraExecutor.shutdown();
    }
}
