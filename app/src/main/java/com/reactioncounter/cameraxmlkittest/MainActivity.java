package com.reactioncounter.cameraxmlkittest;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.pm.PackageManager;
import android.graphics.PointF;
import android.graphics.Rect;
import android.media.Image;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;

import android.util.Size;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.Task;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceDetection;
import com.google.mlkit.vision.face.FaceDetector;
import com.google.mlkit.vision.face.FaceDetectorOptions;
import com.google.mlkit.vision.face.FaceLandmark;

import java.util.List;
import java.util.concurrent.ExecutionException;

public class MainActivity extends AppCompatActivity {
    private ImageCapture imageCapture;
    private static final int CAMERA_REQUEST = 100;

    @RequiresApi(api = Build.VERSION_CODES.M)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        PreviewView previewView = findViewById(R.id.previewView);
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, CAMERA_REQUEST);
        }

        ListenableFuture<ProcessCameraProvider> cameraProviderFuture =
                ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(() -> {
            try {
                // Camera provider is now guaranteed to be available
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();

                // Set up the view finder use case to display camera preview
                Preview preview = new Preview.Builder().build();

                // Set up the capture use case to allow users to take photos
                imageCapture = new ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                        .build();

                // Choose the camera by requiring a lens facing front
                CameraSelector cameraSelector = new CameraSelector.Builder()
                        .requireLensFacing(CameraSelector.LENS_FACING_FRONT)
                        .build();

                ImageAnalysis imageAnalysis =
                        new ImageAnalysis.Builder()
                                .setTargetResolution(new Size(previewView.getWidth(), previewView.getHeight()))
                                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                .build();

                imageAnalysis.setAnalyzer(Runnable::run, imageProxy -> {
                    @SuppressLint("UnsafeOptInUsageError") Image mediaImage = imageProxy.getImage();
                    if (mediaImage != null) {
                        InputImage image =
                                InputImage.fromMediaImage(mediaImage, imageProxy.getImageInfo().getRotationDegrees());

                        FaceDetectorOptions realTimeOpts =
                                new FaceDetectorOptions.Builder()
                                        .setContourMode(FaceDetectorOptions.CONTOUR_MODE_ALL)
                                        .build();

                        FaceDetector detector = FaceDetection.getClient(realTimeOpts);

                        Task<List<Face>> result =
                                detector.process(image)
                                        .addOnSuccessListener(
                                                faces -> {
                                                    // Task completed successfully
                                                    Log.v("CBA", "onSuccess!!");
                                                    for (Face face : faces) {
                                                        Rect bounds = face.getBoundingBox();
                                                        Log.v("CBA", "left: " + bounds.left + ", right: " + bounds.right);
                                                        float rotY = face.getHeadEulerAngleY();  // Head is rotated to the right rotY degrees
                                                        float rotZ = face.getHeadEulerAngleZ();  // Head is tilted sideways rotZ degrees

                                                        FaceLandmark leftEar = face.getLandmark(FaceLandmark.LEFT_EAR);
                                                        if (leftEar != null) {
                                                            PointF leftEarPos = leftEar.getPosition();
                                                        }

                                                        if (face.getSmilingProbability() != null) {
                                                            float smileProb = face.getSmilingProbability();
                                                        }
                                                        if (face.getRightEyeOpenProbability() != null) {
                                                            float rightEyeOpenProb = face.getRightEyeOpenProbability();
                                                        }

                                                        if (face.getTrackingId() != null) {
                                                            int id = face.getTrackingId();
                                                        }
                                                    }
                                                })
                                        .addOnFailureListener(
                                                new OnFailureListener() {
                                                    @Override
                                                    public void onFailure(@NonNull Exception e) {
                                                        Log.w("CBA", "Failure!!!");
                                                        e.printStackTrace();
                                                    }
                                                })
                                        .addOnCompleteListener(task -> imageProxy.close());
                    }
                });
                // Attach use cases to the camera with the same lifecycle owner
                Camera camera = cameraProvider.bindToLifecycle(
                        ((LifecycleOwner) this),
                        cameraSelector,
                        imageAnalysis,
                        preview,
                        imageCapture);

                // Connect the preview use case to the previewView
                preview.setSurfaceProvider(
                        previewView.getSurfaceProvider());
            } catch (InterruptedException | ExecutionException e) {
                // Currently no exceptions thrown. cameraProviderFuture.get()
                // shouldn't block since the listener is being called, so no need to
                // handle InterruptedException.
            }
        }, ContextCompat.getMainExecutor(this));
    }
}