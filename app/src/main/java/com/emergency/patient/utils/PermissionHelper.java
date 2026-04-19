package com.emergency.patient.utils;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.PowerManager;
import android.provider.Settings;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;

/**
 * PermissionHelper — Centralizes all runtime and special permission requests.
 */
public class PermissionHelper {

    public static final int REQUEST_CODE_PERMISSIONS = 1001;

    public static String[] getDangerousPermissions() {
        List<String> permissions = new ArrayList<>();
        permissions.add(Manifest.permission.SEND_SMS);
        permissions.add(Manifest.permission.ACCESS_FINE_LOCATION);
        permissions.add(Manifest.permission.ACCESS_COARSE_LOCATION);
        permissions.add(Manifest.permission.CALL_PHONE);
        permissions.add(Manifest.permission.READ_CALENDAR);
        permissions.add(Manifest.permission.WRITE_CALENDAR);
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS);
        }
        
        // Android 10+ Activity Recognition
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            permissions.add(Manifest.permission.ACTIVITY_RECOGNITION);
        }
        
        // Body Sensors (Compliance for Health FGS on some Android versions)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH) {
            permissions.add(Manifest.permission.BODY_SENSORS);
        }
        
        return permissions.toArray(new String[0]);
    }

    public static void requestDangerousPermissions(@NonNull Activity activity) {
        ActivityCompat.requestPermissions(activity, getDangerousPermissions(), REQUEST_CODE_PERMISSIONS);
    }

    public static boolean areAllDangerousPermissionsGranted(@NonNull Context context) {
        for (String permission : getDangerousPermissions()) {
            if (ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    public static void requestBackgroundLocationPermission(@NonNull Activity activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(activity, new String[]{Manifest.permission.ACCESS_BACKGROUND_LOCATION}, REQUEST_CODE_PERMISSIONS + 1);
            }
        }
    }

    public static boolean canDrawOverlays(@NonNull Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return Settings.canDrawOverlays(context);
        }
        return true;
    }

    public static void requestOverlayPermission(@NonNull Activity activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            android.content.SharedPreferences prefs = activity.getSharedPreferences("permission_prefs", Context.MODE_PRIVATE);
            boolean prompted = prefs.getBoolean("overlay_prompted", false);
            if (!prompted) {
                prefs.edit().putBoolean("overlay_prompted", true).apply();
                Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + activity.getPackageName()));
                activity.startActivity(intent);
            }
        }
    }

    public static boolean isBatteryOptimizationIgnored(@NonNull Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            return pm != null && pm.isIgnoringBatteryOptimizations(context.getPackageName());
        }
        return true;
    }

    public static void enforceBatteryOptimizationBypass(@NonNull Activity activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !isBatteryOptimizationIgnored(activity)) {
            android.content.SharedPreferences prefs = activity.getSharedPreferences("permission_prefs", Context.MODE_PRIVATE);
            boolean prompted = prefs.getBoolean("battery_prompted_helper", false);
            if (!prompted) {
                prefs.edit().putBoolean("battery_prompted_helper", true).apply();
                Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + activity.getPackageName()));
                activity.startActivity(intent);
            }
        }
    }

    public static void requestAllPermissions(@NonNull Activity activity) {
        requestDangerousPermissions(activity);
        if (!canDrawOverlays(activity)) {
            requestOverlayPermission(activity);
        }
        if (!isBatteryOptimizationIgnored(activity)) {
            enforceBatteryOptimizationBypass(activity);
        }
    }
}
