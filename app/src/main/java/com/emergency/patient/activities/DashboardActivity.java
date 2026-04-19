package com.emergency.patient.activities;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import android.net.Uri;
import android.provider.Settings;
import android.app.NotificationManager;
import androidx.appcompat.app.AlertDialog;

import com.emergency.patient.R;
import com.emergency.patient.db.AppDatabaseProvider;
import com.emergency.patient.db.PatientEntity;
import com.emergency.patient.network.FcmTokenSyncManager;
import com.emergency.patient.security.TokenManager;

import com.emergency.patient.services.EmergencyBackgroundService;
import com.emergency.patient.services.FallDetectionService;
import com.emergency.patient.utils.PermissionHelper;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.ExistingPeriodicWorkPolicy;
import java.util.concurrent.TimeUnit;
import com.emergency.patient.scheduling.MedicationExpiryWorker;

public class DashboardActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Route to onboarding if not yet complete
        String uuid = TokenManager.getUUID(this);
        
        new Thread(() -> {
            boolean isComplete;
            try {
                PatientEntity patient = AppDatabaseProvider.getInstance(this).patientDao().getPatient(uuid);
                isComplete = (patient != null && patient.isOnboardingComplete) || TokenManager.isOnboardingComplete(this);
            } catch (Exception e) {
                isComplete = TokenManager.isOnboardingComplete(this);
            }
            
            final boolean finalIsComplete = isComplete;
            runOnUiThread(() -> {
                if (!finalIsComplete) {
                    startActivity(new Intent(this, Step1BasicInfoActivity.class));
                    finish();
                    return;
                }

                PermissionHelper.requestAllPermissions(this);
                PermissionHelper.enforceBatteryOptimizationBypass(this);
                FcmTokenSyncManager.syncCurrentToken(this);
                
                // Schedule Expiry Check (Daily)
                PeriodicWorkRequest expiryWork = new PeriodicWorkRequest.Builder(MedicationExpiryWorker.class, 24, TimeUnit.HOURS).build();
                WorkManager.getInstance(this).enqueueUniquePeriodicWork("med_expiry_check", ExistingPeriodicWorkPolicy.KEEP, expiryWork);

                setContentView(R.layout.activity_main);
                EmergencyBackgroundService.start(this);

                // 1. Request Activity Recognition and Body Sensors permissions, then start service
                checkAndStartFallDetection();

                // 2. Prompt battery whitelist if not already granted
                checkBatteryOptimizationWhitelist();

                BottomNavigationView bottomNav = findViewById(R.id.bottom_navigation);
                bottomNav.setOnItemSelectedListener(item -> {
                    Fragment selectedFragment = null;
                    int itemId = item.getItemId();

                    if (itemId == R.id.nav_home) {
                        selectedFragment = new MedicationSafetyHomeFragment();
                    } else if (itemId == R.id.nav_health_resume) {
                        selectedFragment = new HealthResumeFragment();
                    } else if (itemId == R.id.nav_medical_id) {
                        selectedFragment = new MedicalIdFragment();
                    } else if (itemId == R.id.nav_ai_assistant) {
                        selectedFragment = new HealthAssistantFragment();
                    } else if (itemId == R.id.nav_dashboard) {
                        selectedFragment = new MedicationDashboardFragment();
                    }

                    if (selectedFragment != null) {
                        getSupportFragmentManager().beginTransaction()
                                .replace(R.id.fragment_container, selectedFragment)
                                .commit();
                        return true;
                    }
                    return false;
                });

                // Set default selection — Home dashboard is now the landing screen
                if (savedInstanceState == null) {
                    int defaultNav = getIntent().getIntExtra("selected_nav_item", R.id.nav_home);
                    bottomNav.setSelectedItemId(defaultNav);
                }
            });
        }).start();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        int navItem = intent.getIntExtra("selected_nav_item", -1);
        if (navItem != -1) {
            BottomNavigationView bottomNav = findViewById(R.id.bottom_navigation);
            if (bottomNav != null) {
                bottomNav.setSelectedItemId(navItem);
            }
        }
    }

    // ─── Fall Detection Permission Gate ──────────────────────────────────────

    private static final int FALL_DETECTION_PERMISSION_CODE = 201;
    private static final String TAG = "DashboardActivity";

    /**
     * Hard gate: Only starts FallDetectionService after confirming runtime grants.
     * ACTIVITY_RECOGNITION and BODY_SENSORS are dangerous permissions on Android 10+.
     */
    private void checkAndStartFallDetection() {
        boolean hasActivity = ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED;
        boolean hasBodySensors = ContextCompat.checkSelfPermission(this,
                Manifest.permission.BODY_SENSORS) == PackageManager.PERMISSION_GRANTED;

        if (hasActivity || hasBodySensors) {
            // Permissions already granted — start immediately
            startFallDetectionService();
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Request permissions; service will start in onRequestPermissionsResult
            androidx.core.app.ActivityCompat.requestPermissions(this,
                    new String[]{
                        Manifest.permission.ACTIVITY_RECOGNITION,
                        Manifest.permission.BODY_SENSORS
                    },
                    FALL_DETECTION_PERMISSION_CODE);
        } else {
            // Pre-Android 10: no runtime request needed
            startFallDetectionService();
        }
    }

    private void startFallDetectionService() {
        String fallPreset = getSharedPreferences("prefs", MODE_PRIVATE)
                .getString("fall_sensitivity", "NORMAL");
        FallDetectionService.start(this, fallPreset);
        Log.d(TAG, "FallDetectionService started with preset=" + fallPreset);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
            @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == FALL_DETECTION_PERMISSION_CODE) {
            boolean anyGranted = false;
            for (int result : grantResults) {
                if (result == PackageManager.PERMISSION_GRANTED) {
                    anyGranted = true;
                    break;
                }
            }
            if (anyGranted) {
                checkFullScreenIntentPermission();
                startFallDetectionService();
            } else {
                Toast.makeText(this,
                    "Fall detection requires sensor permissions. Enable in Settings for full protection.",
                    Toast.LENGTH_LONG).show();
                Log.w(TAG, "Fall detection disabled — sensor permissions denied.");
            }
        }
    }

    private void requestNotificationPermission() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (androidx.core.content.ContextCompat.checkSelfPermission(this,
                    android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                androidx.core.app.ActivityCompat.requestPermissions(this,
                        new String[] { android.Manifest.permission.POST_NOTIFICATIONS }, 101);
            }
        }
    }

    /**
     * Android 14+ Security: Checks if the app is allowed to show full-screen alerts.
     * This is required to bypass Background Activity Launch (BAL) restrictions.
     */
    private void checkFullScreenIntentPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null && !nm.canUseFullScreenIntent()) {
                new AlertDialog.Builder(this)
                        .setTitle("Enable Emergency Alerts")
                        .setMessage("To ensure fall detection alerts show immediately on your lock screen, please enable 'Full Screen Intent' in settings.")
                        .setPositiveButton("Open Settings", (d, w) -> {
                            Intent intent = new Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
                                    Uri.parse("package:" + getPackageName()));
                            startActivity(intent);
                        })
                        .setNegativeButton("Later", null)
                        .show();
            }
        }
    }

    /**
     * Requests ACTIVITY_RECOGNITION permission (required on Android 10+).
     * Without this, fall detection runs always-on at SENSOR_DELAY_GAME (higher battery).
     */
    private void requestActivityRecognitionPermission() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            if (androidx.core.content.ContextCompat.checkSelfPermission(this,
                    android.Manifest.permission.ACTIVITY_RECOGNITION)
                    != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                androidx.core.app.ActivityCompat.requestPermissions(this,
                        new String[]{android.Manifest.permission.ACTIVITY_RECOGNITION}, 201);
            }
        }
    }

    /**
     * Checks if battery optimization is blocking the fall detection service.
     * Shows a one-time dialog explaining WHY this is critical for a safety app.
     *
     * 3-Layer Battery Defense:
     *   1. This whitelist → prevents Doze Mode killing sensor loop.
     *   2. PARTIAL_WAKE_LOCK in service → keeps CPU alive with screen off.
     *   3. ActivityRecognition gating → reduces sensor rate when STILL.
     */
    private void checkBatteryOptimizationWhitelist() {
        android.os.PowerManager pm = (android.os.PowerManager) getSystemService(POWER_SERVICE);
        if (pm == null) return;
        if (pm.isIgnoringBatteryOptimizations(getPackageName())) return; // Already whitelisted

        // Only prompt once — check a flag
        boolean prompted = getSharedPreferences("prefs", MODE_PRIVATE)
                .getBoolean("battery_whitelist_prompted", false);
        if (prompted) return;
        getSharedPreferences("prefs", MODE_PRIVATE).edit()
                .putBoolean("battery_whitelist_prompted", true).apply();

        new androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Enable Reliable Fall Guard")
            .setMessage(
                "ArogyaSaarthi needs unrestricted background access to monitor " +
                "for falls while your screen is off. Without this, Android may " +
                "pause the sensor during deep sleep, causing missed detections.\n\n" +
                "Tap 'Allow' → select 'Unrestricted' to ensure 24/7 protection."
            )
            .setPositiveButton("Allow", (d, w) -> {
                android.content.Intent intent = new android.content.Intent(
                        android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                intent.setData(android.net.Uri.parse("package:" + getPackageName()));
                startActivity(intent);
            })
            .setNegativeButton("Later", null)
            .setCancelable(false)
            .show();
    }
}
