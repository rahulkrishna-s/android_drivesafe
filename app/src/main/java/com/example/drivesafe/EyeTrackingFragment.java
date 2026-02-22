package com.example.drivesafe;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Location;
import android.location.LocationManager;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.telephony.SmsManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.camera.core.*;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.*;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class EyeTrackingFragment extends Fragment {

    private PreviewView previewView;
    private TextView statusText, eyeValueText, blinkRateText;
    private FrameLayout statusCircleFrame;
    private Button btnAction;
    private View emergencyOverlay; // NEW: The red flash view

    private FaceDetector faceDetector;
    private MediaPlayer mediaPlayer;
    private ExecutorService cameraExecutor;
    private DatabaseHelper dbHelper;

    // --- Animations ---
    private Animation pulseAnim;
    private Animation flashAnim; // NEW: The flashing animation

    private boolean isMonitoring = false;
    private int blinkCount = 0;
    private int sessionTotalBlinks = 0;
    private boolean eyesWereClosed = false;
    private long minuteStartTime = 0;
    private long eyeClosedStartTime = 0;

    // Advanced Tracking Variables
    private long distractionStartTime = 0;
    private boolean isDistracted = false;
    private boolean distractionFired = false;
    private int sessionTotalDistractions = 0;

    private boolean yawnFired = false;
    private int sessionTotalYawns = 0;

    // Session & SOS tracking
    private long sessionId = -1;
    private long sessionStartTime = 0;
    private int sessionWarningCount = 0;
    private int sessionCriticalCount = 0;
    private boolean warningFired = false;
    private boolean criticalFired = false;
    private boolean sosSent = false;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_eye_tracking, container, false);

        previewView = view.findViewById(R.id.previewView);
        statusText = view.findViewById(R.id.statusText);
        eyeValueText = view.findViewById(R.id.eyeValueText);
        blinkRateText = view.findViewById(R.id.blinkRateText);
        statusCircleFrame = view.findViewById(R.id.statusCircleFrame);
        btnAction = view.findViewById(R.id.btnAction);

        // --- EMERGENCY FLASH SETUP ---
        emergencyOverlay = view.findViewById(R.id.emergencyOverlay);
        flashAnim = AnimationUtils.loadAnimation(requireContext(), R.anim.emergency_flash);

        // Load the Pulse Animation
        pulseAnim = AnimationUtils.loadAnimation(requireContext(), R.anim.pulse);

        cameraExecutor = Executors.newSingleThreadExecutor();
        dbHelper = DatabaseHelper.getInstance(requireContext());

        FaceDetectorOptions options = new FaceDetectorOptions.Builder()
                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
                .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
                .build();

        faceDetector = FaceDetection.getClient(options);

        // --- PREMIUM BOUNCE ANIMATION FOR BUTTON ---
        btnAction.setOnTouchListener((v, event) -> {
            if (event.getAction() == android.view.MotionEvent.ACTION_DOWN) {
                v.startAnimation(AnimationUtils.loadAnimation(requireContext(), R.anim.btn_click));
            }
            return false; // Let the onClickListener below handle the actual event
        });

        btnAction.setOnClickListener(v -> {
            if (!isMonitoring) startMonitoring();
            else stopMonitoring();
        });

        return view;
    }

    private void setupMediaPlayer() {
        if (mediaPlayer != null) {
            mediaPlayer.release();
        }

        SharedPreferences prefs = requireContext().getSharedPreferences("DriveSafePrefs", Context.MODE_PRIVATE);
        String soundChoice = prefs.getString("alarm_sound", "Sound 1");

        int soundResId = R.raw.alarm1;

        if (soundChoice.equals("Sound 2")) soundResId = R.raw.alarm2;
        else if (soundChoice.equals("Sound 3")) soundResId = R.raw.alarm3;
        else if (soundChoice.equals("Sound 4")) soundResId = R.raw.alarm4;

        mediaPlayer = MediaPlayer.create(getContext(), soundResId);

        float volume = prefs.getFloat("alarm_volume", 100f) / 100f;
        if (mediaPlayer != null) {
            mediaPlayer.setVolume(volume, volume);
        }
    }

    private void startMonitoring() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED) {

            isMonitoring = true;
            minuteStartTime = System.currentTimeMillis();
            sessionStartTime = System.currentTimeMillis();
            blinkCount = 0;
            sessionTotalBlinks = 0;
            sessionTotalYawns = 0;
            sessionTotalDistractions = 0;
            sessionWarningCount = 0;
            sessionCriticalCount = 0;
            warningFired = false;
            criticalFired = false;
            yawnFired = false;
            distractionFired = false;
            isDistracted = false;
            eyeClosedStartTime = 0;
            eyesWereClosed = false;
            sosSent = false;

            // Ensure overlay is hidden when starting
            if (emergencyOverlay != null) {
                emergencyOverlay.clearAnimation();
                emergencyOverlay.setVisibility(View.GONE);
            }

            setupMediaPlayer();

            sessionId = dbHelper.startSession();
            Toast.makeText(getContext(), "Drive session #" + sessionId + " started", Toast.LENGTH_SHORT).show();

            // --- PREMIUM UI UPDATES ---
            btnAction.setText("STOP MONITORING");
            btnAction.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#FF3B30"))); // iOS Red for Stop

            // Start the breathing pulse!
            statusCircleFrame.startAnimation(pulseAnim);

            startCamera();
        } else {
            requestPermissions(new String[]{Manifest.permission.CAMERA, Manifest.permission.SEND_SMS}, 100);
        }
    }

    private void stopMonitoring() {
        isMonitoring = false;

        if (sessionId >= 0) {
            int durationSec = (int) ((System.currentTimeMillis() - sessionStartTime) / 1000);
            dbHelper.endSession(sessionId, durationSec, sessionWarningCount, sessionCriticalCount, sessionTotalBlinks, sessionTotalYawns, sessionTotalDistractions);
            Toast.makeText(getContext(), "Session saved (" + durationSec + "s)", Toast.LENGTH_LONG).show();
            sessionId = -1;
        }

        // --- PREMIUM UI UPDATES ---
        btnAction.setText("START MONITORING");
        btnAction.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#007AFF"))); // Electric Blue for Start

        statusText.setText("OFFLINE");
        statusText.setTextColor(Color.parseColor("#8E8E93")); // Slate Gray
        statusCircleFrame.setBackgroundResource(R.drawable.circular_neon_border);

        // Stop animations!
        statusCircleFrame.clearAnimation();
        if (emergencyOverlay != null) {
            emergencyOverlay.clearAnimation();
            emergencyOverlay.setVisibility(View.GONE);
        }

        if (mediaPlayer != null && mediaPlayer.isPlaying()) mediaPlayer.pause();

        try {
            ProcessCameraProvider.getInstance(requireContext()).get().unbindAll();
        } catch (Exception ignored) {}
    }

    private void startCamera() {
        ProcessCameraProvider.getInstance(requireContext()).addListener(() -> {
            try {
                ProcessCameraProvider provider = ProcessCameraProvider.getInstance(requireContext()).get();
                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());

                ImageAnalysis analysis = new ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build();

                analysis.setAnalyzer(cameraExecutor, this::analyzeImage);
                provider.bindToLifecycle(getViewLifecycleOwner(), CameraSelector.DEFAULT_FRONT_CAMERA, preview, analysis);
            } catch (Exception e) { e.printStackTrace(); }
        }, ContextCompat.getMainExecutor(requireContext()));
    }

    private void analyzeImage(@NonNull ImageProxy imageProxy) {
        if (!isMonitoring || imageProxy.getImage() == null) {
            imageProxy.close();
            return;
        }

        InputImage image = InputImage.fromMediaImage(imageProxy.getImage(), imageProxy.getImageInfo().getRotationDegrees());

        faceDetector.process(image).addOnSuccessListener(faces -> {
            if (!isAdded() || getActivity() == null) {
                imageProxy.close();
                return;
            }

            if (!faces.isEmpty()) {
                Face face = faces.get(0);

                float left = face.getLeftEyeOpenProbability() != null ? face.getLeftEyeOpenProbability() : 1.0f;
                float right = face.getRightEyeOpenProbability() != null ? face.getRightEyeOpenProbability() : 1.0f;
                float eyeAvg = (left + right) / 2.0f;

                float headTurnY = face.getHeadEulerAngleY();
                float headTiltX = face.getHeadEulerAngleX();

                boolean isYawning = false;
                if (face.getLandmark(FaceLandmark.MOUTH_BOTTOM) != null && face.getLandmark(FaceLandmark.NOSE_BASE) != null) {
                    float mouthGap = face.getLandmark(FaceLandmark.MOUTH_BOTTOM).getPosition().y - face.getLandmark(FaceLandmark.NOSE_BASE).getPosition().y;
                    if (mouthGap > (face.getBoundingBox().height() * 0.35f)) {
                        isYawning = true;
                    }
                }

                final boolean finalIsYawning = isYawning;

                requireActivity().runOnUiThread(() -> {
                    eyeValueText.setText(String.format("EAR: %.2f", eyeAvg));
                    updateDriverState(eyeAvg, headTurnY, headTiltX, finalIsYawning);
                });
            }
            imageProxy.close();
        }).addOnFailureListener(e -> imageProxy.close());
    }

    private void updateDriverState(float ear, float headTurnY, float headTiltX, boolean isYawning) {
        long currentTime = System.currentTimeMillis();

        if (headTurnY > 25 || headTurnY < -25 || headTiltX < -20) {
            if (!isDistracted) {
                distractionStartTime = currentTime;
                isDistracted = true;
            } else if (currentTime - distractionStartTime >= 2000) {
                statusText.setText("DISTRACTED!");
                statusText.setTextColor(Color.parseColor("#FF9500")); // iOS Amber
                statusCircleFrame.setBackgroundResource(R.drawable.circular_neon_yellow);

                if (!distractionFired) {
                    sessionTotalDistractions++;
                    distractionFired = true;
                }

                if (mediaPlayer != null && !mediaPlayer.isPlaying()) mediaPlayer.start();
                return;
            }
        } else {
            isDistracted = false;
            distractionFired = false;
        }

        if (isYawning) {
            if (!yawnFired) {
                sessionTotalYawns++;
                yawnFired = true;
            }
            statusText.setText("YAWNING!");
            statusText.setTextColor(Color.parseColor("#FF9500")); // iOS Amber
            statusCircleFrame.setBackgroundResource(R.drawable.circular_neon_yellow);
            return;
        } else {
            yawnFired = false;
        }

        if (ear < 0.25) {
            eyesWereClosed = true;
            if (eyeClosedStartTime == 0) eyeClosedStartTime = currentTime;
            long closedDuration = currentTime - eyeClosedStartTime;

            if (closedDuration >= 10000) { // CRITICAL: PULL OVER
                statusText.setText("PULL OVER!");
                statusText.setTextColor(Color.parseColor("#FF3B30")); // iOS System Red
                statusCircleFrame.setBackgroundResource(R.drawable.circular_neon_red);

                // --- TRIGGER THE FLASH OVERLAY ---
                if (emergencyOverlay != null && emergencyOverlay.getVisibility() == View.GONE) {
                    emergencyOverlay.setVisibility(View.VISIBLE);
                    emergencyOverlay.startAnimation(flashAnim);
                }

                if (mediaPlayer != null && !mediaPlayer.isPlaying()) mediaPlayer.start();

                if (!criticalFired) {
                    criticalFired = true;
                    sessionCriticalCount++;
                }

                if (!sosSent) {
                    sendEmergencySOS();
                    sosSent = true;
                }

            } else if (closedDuration >= 1000) { // WARNING: WAKE UP
                statusText.setText("WAKE UP!");
                statusText.setTextColor(Color.parseColor("#FF9500")); // iOS Amber
                statusCircleFrame.setBackgroundResource(R.drawable.circular_neon_yellow);
                if (mediaPlayer != null && !mediaPlayer.isPlaying()) mediaPlayer.start();

                if (!warningFired) {
                    warningFired = true;
                    sessionWarningCount++;
                }
            }
        } else {
            // DRIVER IS AWAKE
            if (eyesWereClosed) {
                blinkCount++;
                sessionTotalBlinks++;
                eyesWereClosed = false;
                warningFired = false;
                criticalFired = false;
                sosSent = false;
            }
            eyeClosedStartTime = 0;

            statusText.setText("ATTENTIVE");
            statusText.setTextColor(Color.parseColor("#32D74B")); // iOS Safe Green
            statusCircleFrame.setBackgroundResource(R.drawable.circular_neon_border);

            // --- STOP THE FLASH OVERLAY ---
            if (emergencyOverlay != null && emergencyOverlay.getVisibility() == View.VISIBLE) {
                emergencyOverlay.clearAnimation();
                emergencyOverlay.setVisibility(View.GONE);
            }

            if (mediaPlayer != null && mediaPlayer.isPlaying()) mediaPlayer.pause();
        }

        if (currentTime - minuteStartTime >= 60000) {
            blinkRateText.setText("BLINK: " + blinkCount + "/min");
            blinkCount = 0;
            minuteStartTime = currentTime;
        }
    }

    private void sendEmergencySOS() {
        try {
            SharedPreferences prefs = requireContext().getSharedPreferences("DriveSafePrefs", Context.MODE_PRIVATE);
            String savedEmergencyNumber = prefs.getString("emergency_number", "");

            if (savedEmergencyNumber.isEmpty()) {
                requireActivity().runOnUiThread(() ->
                        Toast.makeText(getContext(), "SOS FAILED: No emergency number saved!", Toast.LENGTH_LONG).show()
                );
                return;
            }

            LocationManager lm = (LocationManager) requireContext().getSystemService(Context.LOCATION_SERVICE);
            Location location = null;

            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                location = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                if (location == null) {
                    location = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
                }
            }

            String mapsLink = "Location unavailable";
            if (location != null) {
                mapsLink = "https://maps.google.com/?q=" + location.getLatitude() + "," + location.getLongitude();
            }

            String message = "EMERGENCY: Driver is unresponsive! Last known location: " + mapsLink;

            SmsManager smsManager = SmsManager.getDefault();
            smsManager.sendTextMessage(savedEmergencyNumber, null, message, null, null);

            Log.d("DriveSafe", "SOS SMS sent to " + savedEmergencyNumber);

            // --- PREMIUM VISUAL EMERGENCY ALERT ---
            requireActivity().runOnUiThread(() -> {
                new android.app.AlertDialog.Builder(requireContext(), android.R.style.Theme_DeviceDefault_Dialog_Alert)
                        .setTitle("⚠️ EMERGENCY SOS SENT")
                        .setMessage("An emergency alert with your live location has been dispatched to: " + savedEmergencyNumber)
                        .setPositiveButton("DISMISS", (dialog, which) -> dialog.dismiss())
                        .setIcon(android.R.drawable.ic_dialog_alert)
                        .show();
            });

        } catch (Exception e) {
            e.printStackTrace();
            requireActivity().runOnUiThread(() ->
                    Toast.makeText(getContext(), "Failed to send SOS", Toast.LENGTH_SHORT).show()
            );
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();

        if (isMonitoring && sessionId >= 0) {
            int durationSec = (int) ((System.currentTimeMillis() - sessionStartTime) / 1000);
            dbHelper.endSession(sessionId, durationSec, sessionWarningCount, sessionCriticalCount, sessionTotalBlinks, sessionTotalYawns, sessionTotalDistractions);
            sessionId = -1;
        }

        isMonitoring = false;

        // --- Prevent animation memory leaks ---
        if (statusCircleFrame != null) {
            statusCircleFrame.clearAnimation();
        }
        if (emergencyOverlay != null) {
            emergencyOverlay.clearAnimation();
        }

        try {
            ProcessCameraProvider.getInstance(requireContext()).get().unbindAll();
        } catch (Exception ignored) {}

        if (cameraExecutor != null) cameraExecutor.shutdown();

        if (mediaPlayer != null) {
            if (mediaPlayer.isPlaying()) mediaPlayer.stop();
            mediaPlayer.release();
            mediaPlayer = null;
        }
    }
}