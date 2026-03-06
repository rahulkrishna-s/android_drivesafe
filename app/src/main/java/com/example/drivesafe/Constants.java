package com.example.drivesafe;

/**
 * Centralized constants for the DriveSafe app.
 * Eliminates magic numbers scattered across fragments.
 */
public final class Constants {

    private Constants() { /* Non-instantiable */ }

    // ─── SharedPreferences ───────────────────────────────────────────────
    public static final String PREFS_NAME = "DriveSafePrefs";

    // Preference keys
    public static final String KEY_ALARM_SOUND = "alarm_sound";
    public static final String KEY_ALARM_VOLUME = "alarm_volume";
    public static final String KEY_EMERGENCY_NUMBER = "emergency_number";
    public static final String KEY_PROFILE_NAME = "profile_name";
    public static final String KEY_PROFILE_IMAGE_PATH = "profile_image_path";
    public static final String KEY_DARK_MODE = "dark_mode";
    public static final String KEY_SPEED_LIMIT = "speed_limit";

    // New Key for Speed Alert Toggle logic
    public static final String KEY_SPEED_ALERT_ENABLED = "speed_alert_enabled";

    // ─── Notification Constants ──────────────────────────────────────────
    // Centralized IDs for GuardianEye status messages
    public static final String CHANNEL_ID = "GuardianEye_Channel";
    public static final String CHANNEL_NAME = "GuardianEye Status Service";
    public static final int NOTIFICATION_ID = 1001;

    // Sensitivity preference key & values
    public static final String KEY_SENSITIVITY = "detection_sensitivity";
    public static final String SENSITIVITY_LOW = "low";
    public static final String SENSITIVITY_MEDIUM = "medium";
    public static final String SENSITIVITY_HIGH = "high";
    public static final String DEFAULT_SENSITIVITY = SENSITIVITY_MEDIUM;

    // Smart Detection preference keys
    public static final String KEY_SMART_DETECTION_ENABLED = "smart_detection_enabled";
    public static final String KEY_MIN_SPEED_ENABLED = "min_speed_enabled";
    public static final String KEY_MIN_SPEED_KMH = "min_speed_kmh";
    public static final String KEY_LARGEST_FACE_ONLY = "largest_face_only";

    // Default values
    public static final String DEFAULT_ALARM_SOUND = "Sound 1";
    public static final float DEFAULT_ALARM_VOLUME = 100f;
    public static final float DEFAULT_SPEED_LIMIT = 80.0f;
    public static final float DEFAULT_MIN_SPEED_KMH = 10.0f;

    // ─── Eye Tracking Thresholds ─────────────────────────────────────────
    public static final float EAR_THRESHOLD = 0.25f;
    public static final long WARNING_DURATION_MS = 1000L;
    public static final long CRITICAL_DURATION_MS = 10_000L;
    public static final float HEAD_TURN_THRESHOLD = 25f;
    public static final float HEAD_TILT_THRESHOLD = -20f;
    public static final float YAWN_RATIO = 0.35f;
    public static final long DISTRACTION_DURATION_MS = 2000L;
    public static final long BLINK_RATE_WINDOW_MS = 60_000L;

    // ─── Speed Tracking ──────────────────────────────────────────────────
    public static final long SPEED_LOG_COOLDOWN_MS = 30_000L;

    // ─── Logging Tag ─────────────────────────────────────────────────────
    public static final String TAG = "DriveSafe";

    // ─── Sensitivity Helpers ─────────────────────────────────────────────

    public static float getEarThreshold(String sensitivity) {
        if (sensitivity == null) return EAR_THRESHOLD;
        switch (sensitivity) {
            case SENSITIVITY_LOW:  return 0.20f;
            case SENSITIVITY_HIGH: return 0.30f;
            default:               return 0.25f; // MEDIUM
        }
    }

    public static long getWarningDuration(String sensitivity) {
        if (sensitivity == null) return WARNING_DURATION_MS;
        switch (sensitivity) {
            case SENSITIVITY_LOW:  return 2000L;
            case SENSITIVITY_HIGH: return 500L;
            default:               return 1000L; // MEDIUM
        }
    }
}