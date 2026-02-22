package com.example.drivesafe;

import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MotionEvent; // NEW IMPORT
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AnimationUtils; // NEW IMPORT
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.fragment.app.Fragment;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.imageview.ShapeableImageView;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.slider.Slider;
import com.google.android.material.textfield.TextInputEditText;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

public class SettingsFragment extends Fragment {

    // Variables matching the XML exactly
    private ShapeableImageView ivProfilePhoto;
    private TextInputEditText etUserName;
    private EditText etEmergencyNumber;
    private MaterialSwitch switchTheme;
    private Slider sliderVolume;
    private MaterialButton btnSelectSound;
    private TextView tvSelectedSound;
    private Button btnSaveSettings;

    private SharedPreferences prefs;
    private String selectedSound = "Sound 1";

    // Permanent Image Copy Logic
    private final ActivityResultLauncher<String> imagePickerLauncher = registerForActivityResult(
            new ActivityResultContracts.GetContent(),
            uri -> {
                if (uri != null) {
                    try {
                        InputStream is = requireContext().getContentResolver().openInputStream(uri);
                        File file = new File(requireContext().getFilesDir(), "profile_pic.jpg");
                        FileOutputStream fos = new FileOutputStream(file);
                        byte[] buffer = new byte[1024];
                        int length;
                        while ((length = is.read(buffer)) > 0) {
                            fos.write(buffer, 0, length);
                        }
                        fos.close();
                        is.close();

                        // Save the permanent internal path
                        prefs.edit().putString("profile_image_path", file.getAbsolutePath()).apply();
                        ivProfilePhoto.setImageURI(Uri.fromFile(file));
                    } catch (Exception e) {
                        Toast.makeText(getContext(), "Failed to save photo", Toast.LENGTH_SHORT).show();
                    }
                }
            }
    );

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_settings, container, false);

        prefs = requireActivity().getSharedPreferences("DriveSafePrefs", Context.MODE_PRIVATE);

        // 1. Bind Views perfectly mapped to XML
        ivProfilePhoto = view.findViewById(R.id.ivProfilePhoto);
        etUserName = view.findViewById(R.id.etUserName);
        etEmergencyNumber = view.findViewById(R.id.etEmergencyNumber);
        switchTheme = view.findViewById(R.id.switchTheme);
        sliderVolume = view.findViewById(R.id.sliderVolume);
        btnSelectSound = view.findViewById(R.id.btnSelectSound);
        tvSelectedSound = view.findViewById(R.id.tvSelectedSound);
        btnSaveSettings = view.findViewById(R.id.btnSaveSettings);

        // 2. Load Existing Settings
        etUserName.setText(prefs.getString("profile_name", ""));
        etEmergencyNumber.setText(prefs.getString("emergency_number", ""));
        sliderVolume.setValue(prefs.getFloat("alarm_volume", 100f));
        switchTheme.setChecked(prefs.getBoolean("dark_mode", true));

        selectedSound = prefs.getString("alarm_sound", "Sound 1");
        tvSelectedSound.setText("Selected: " + selectedSound);

        // Load profile image
        String path = prefs.getString("profile_image_path", "");
        if (!path.isEmpty()) {
            File file = new File(path);
            if (file.exists()) {
                ivProfilePhoto.setImageURI(Uri.fromFile(file));
            }
        }

        // 3. Clicks
        ivProfilePhoto.setOnClickListener(v -> imagePickerLauncher.launch("image/*"));

        btnSelectSound.setOnClickListener(v -> showSoundDialog());

        // --- PREMIUM BOUNCE ANIMATION ADDED HERE ---
        btnSaveSettings.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                v.startAnimation(AnimationUtils.loadAnimation(getContext(), R.anim.btn_click));
            }
            return false; // Returns false so the standard onClick below still executes
        });

        btnSaveSettings.setOnClickListener(v -> {
            boolean isDarkMode = switchTheme.isChecked();

            SharedPreferences.Editor editor = prefs.edit();
            if (etUserName.getText() != null) {
                editor.putString("profile_name", etUserName.getText().toString().trim());
            }
            editor.putString("emergency_number", etEmergencyNumber.getText().toString().trim());
            editor.putFloat("alarm_volume", sliderVolume.getValue());
            editor.putString("alarm_sound", selectedSound);
            editor.putBoolean("dark_mode", isDarkMode);
            editor.apply();

            // Apply Theme
            if (isDarkMode) {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
            } else {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
            }

            Toast.makeText(getContext(), "Settings Saved!", Toast.LENGTH_SHORT).show();

            // Auto-close back to the previous screen
            if (getParentFragmentManager() != null) {
                getParentFragmentManager().popBackStack();
            }
        });

        return view;
    }

    private void showSoundDialog() {
        String[] sounds = {"Sound 1", "Sound 2", "Sound 3", "Sound 4"};
        int checkedItem = 0;
        for (int i = 0; i < sounds.length; i++) {
            if (sounds[i].equals(selectedSound)) checkedItem = i;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setTitle("Select Alarm Sound");
        builder.setSingleChoiceItems(sounds, checkedItem, (dialog, which) -> {
            selectedSound = sounds[which];
            tvSelectedSound.setText("Selected: " + selectedSound);
            dialog.dismiss();
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }
}