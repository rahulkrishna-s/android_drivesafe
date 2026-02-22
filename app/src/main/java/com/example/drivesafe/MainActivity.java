package com.example.drivesafe;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;

import com.google.android.material.bottomnavigation.BottomNavigationView;

public class MainActivity extends AppCompatActivity {

    private Fragment eyeFragment, speedFragment, historyFragment;
    private Fragment activeFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);

        View rootView = findViewById(R.id.rootLayout);
        ViewCompat.setOnApplyWindowInsetsListener(rootView, (v, windowInsets) -> {
            Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(insets.left, insets.top, insets.right, insets.bottom);
            return WindowInsetsCompat.CONSUMED;
        });

        BottomNavigationView bottomNav = findViewById(R.id.bottom_navigation);

        if (savedInstanceState == null) {
            eyeFragment = new EyeTrackingFragment();
            speedFragment = new SpeedLimitFragment();
            historyFragment = new DriveHistoryFragment();

            getSupportFragmentManager().beginTransaction()
                    .add(R.id.nav_host_fragment, historyFragment, "history").hide(historyFragment)
                    .add(R.id.nav_host_fragment, speedFragment, "speed").hide(speedFragment)
                    .add(R.id.nav_host_fragment, eyeFragment, "eye")
                    .commit();

            activeFragment = eyeFragment;
        } else {
            eyeFragment = getSupportFragmentManager().findFragmentByTag("eye");
            speedFragment = getSupportFragmentManager().findFragmentByTag("speed");
            historyFragment = getSupportFragmentManager().findFragmentByTag("history");

            if (eyeFragment != null && !eyeFragment.isHidden()) activeFragment = eyeFragment;
            else if (speedFragment != null && !speedFragment.isHidden()) activeFragment = speedFragment;
            else if (historyFragment != null && !historyFragment.isHidden()) activeFragment = historyFragment;
            else activeFragment = eyeFragment;
        }

        // Settings Button Logic - Added Slide Animations here too!
        ImageView btnSettingsTop = findViewById(R.id.btnSettingsTop);
        if (btnSettingsTop != null) {
            btnSettingsTop.setOnClickListener(v -> {
                if (getSupportFragmentManager().findFragmentByTag("settings") == null) {
                    FragmentTransaction ft = getSupportFragmentManager().beginTransaction();

                    // Slide Settings in from the right, slide current screen out to the left
                    // The last two parameters handle the reverse animation when pressing the "Back" button
                    ft.setCustomAnimations(R.anim.slide_in_right, R.anim.slide_out_left, R.anim.slide_in_left, R.anim.slide_out_right);

                    if (activeFragment != null) ft.hide(activeFragment);
                    ft.add(R.id.nav_host_fragment, new SettingsFragment(), "settings")
                            .addToBackStack(null)
                            .commit();
                }
            });
        }

        bottomNav.setOnItemSelectedListener(item -> {
            if (getSupportFragmentManager().getBackStackEntryCount() > 0) {
                getSupportFragmentManager().popBackStack();
            }

            Fragment target = null;
            int id = item.getItemId();

            // Assign a target index based on the tab's physical position (0 = Left, 1 = Middle, 2 = Right)
            int targetIndex = 0;

            if (id == R.id.nav_eye) {
                target = eyeFragment;
                targetIndex = 0;
            } else if (id == R.id.nav_speed) {
                target = speedFragment;
                targetIndex = 1;
            } else if (id == R.id.nav_history) {
                target = historyFragment;
                targetIndex = 2;
            }

            if (target != null && target != activeFragment) {
                // Figure out the index of the currently active fragment
                int currentIndex = 0;
                if (activeFragment == speedFragment) currentIndex = 1;
                else if (activeFragment == historyFragment) currentIndex = 2;

                FragmentTransaction ft = getSupportFragmentManager().beginTransaction();

                // --- PREMIUM UX: SMART DIRECTIONAL SLIDING ---
                if (targetIndex > currentIndex) {
                    // Moving to a tab on the right (e.g., Eye -> Speed)
                    ft.setCustomAnimations(R.anim.slide_in_right, R.anim.slide_out_left);
                } else {
                    // Moving to a tab on the left (e.g., History -> Speed)
                    ft.setCustomAnimations(R.anim.slide_in_left, R.anim.slide_out_right);
                }

                ft.hide(activeFragment)
                        .show(target)
                        .commit();

                activeFragment = target;
            }
            return true;
        });

        checkPermissionsAndGps();
    }

    private void checkPermissionsAndGps() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(this,
                    new String[]{
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.CAMERA,
                            Manifest.permission.SEND_SMS
                    }, 101);
        } else {
            checkGpsEnabled();
        }
    }

    private void checkGpsEnabled() {
        LocationManager lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        boolean isGpsEnabled = false;
        try {
            if (lm != null) isGpsEnabled = lm.isProviderEnabled(LocationManager.GPS_PROVIDER);
        } catch (Exception ignored) {}

        if (!isGpsEnabled) {
            Toast.makeText(this, "Please turn on GPS/Location", Toast.LENGTH_LONG).show();
            startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 101 && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            checkGpsEnabled();
        }
    }
}