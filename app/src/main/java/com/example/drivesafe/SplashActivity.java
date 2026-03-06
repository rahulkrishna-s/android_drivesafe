package com.example.drivesafe;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.view.WindowManager;
import android.widget.VideoView;

import androidx.appcompat.app.AppCompatActivity;

public class SplashActivity extends AppCompatActivity {

    private VideoView videoView;
    private final Handler handler = new Handler();
    private boolean hasNavigated = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);

        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        setContentView(R.layout.activity_splash);

        videoView = findViewById(R.id.splashVideoView);
        // Ensure your video file is named splash_video in res/raw
        String videoPath = "android.resource://" + getPackageName() + "/" + R.raw.splash_activity;
        videoView.setVideoURI(Uri.parse(videoPath));

        // PRE-FETCH LOGIC: Warm up the next screen 500ms before video ends
        videoView.setOnPreparedListener(mp -> {
            int duration = videoView.getDuration();
            handler.postDelayed(this::navigateToNext, Math.max(0, duration - 500));
        });

        // Backup Listener
        videoView.setOnCompletionListener(mediaPlayer -> navigateToNext());

        videoView.setOnErrorListener((mediaPlayer, what, extra) -> {
            navigateToNext();
            return true;
        });

        videoView.start();
    }

    private synchronized void navigateToNext() {
        if (!hasNavigated && !isFinishing()) {
            hasNavigated = true;

            // Check if it's the first time the app is installed
            SharedPreferences prefs = getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE);
            boolean isFirstLaunch = prefs.getBoolean("is_first_launch", true);

            Intent intent;
            if (isFirstLaunch) {
                // Redirect to the one-time Get Started screen
                intent = new Intent(SplashActivity.this, GetStartedActivity.class);
            } else {
                // Direct to the main dashboard
                intent = new Intent(SplashActivity.this, MainActivity.class);
            }

            intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
            startActivity(intent);
            finish();

            // Smooth transition into the next screen
            overridePendingTransition(0, android.R.anim.fade_out);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
    }
}