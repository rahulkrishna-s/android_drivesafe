package com.example.drivesafe;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.button.MaterialButton;

public class GetStartedActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_get_started);

        MaterialButton btn = findViewById(R.id.btnGetStarted);
        btn.setOnClickListener(v -> {
            // Mark as started so this screen never shows again
            SharedPreferences prefs = getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE);
            prefs.edit().putBoolean("is_first_launch", false).apply();

            startActivity(new Intent(GetStartedActivity.this, MainActivity.class));
            finish();
        });
    }
}
