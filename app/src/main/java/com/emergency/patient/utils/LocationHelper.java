package com.emergency.patient.utils;

import android.annotation.SuppressLint;
import android.content.Context;
import android.location.Location;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.tasks.OnSuccessListener;

/**
 * LocationHelper — Wraps FusedLocationProviderClient to fetch GPS coordinates.
 */
public class LocationHelper {

    public interface LocationCallback {
        void onLocationResult(double lat, double lng);
    }

    /**
     * Fetches the last known location. If unavailable, falls back to (0,0) or last saved.
     */
    @SuppressLint("MissingPermission")
    public static void getLastKnownLocation(Context context, LocationCallback callback) {
        android.util.Log.e("DEBUG_PING", "!!! LocationHelper: getLastKnownLocation called !!!");
        
        // Permission Check
        if (androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION) != android.content.pm.PackageManager.PERMISSION_GRANTED &&
            androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_COARSE_LOCATION) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            android.util.Log.e("DEBUG_PING", "!!! LocationHelper: Permission DENIED !!!");
            callback.onLocationResult(22.7196, 75.8577);
            return;
        }

        FusedLocationProviderClient fusedLocationClient = LocationServices.getFusedLocationProviderClient(context);

        // Add a 5-second timeout safeguard
        final boolean[] called = {false};
        android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
        handler.postDelayed(() -> {
            if (!called[0]) {
                called[0] = true;
                android.util.Log.e("DEBUG_PING", "!!! LocationHelper: GPS timeout !!!");
                callback.onLocationResult(22.7196, 75.8577);
            }
        }, 5000);

        fusedLocationClient.getLastLocation()
                .addOnCompleteListener(task -> {
                    if (called[0]) return;
                    called[0] = true;
                    handler.removeCallbacksAndMessages(null);

                    if (task.isSuccessful() && task.getResult() != null) {
                        android.util.Log.e("DEBUG_PING", "!!! LocationHelper: GPS Success !!!");
                        Location loc = task.getResult();
                        callback.onLocationResult(loc.getLatitude(), loc.getLongitude());
                    } else {
                        android.util.Log.e("DEBUG_PING", "!!! LocationHelper: GPS failed or null !!!");
                        callback.onLocationResult(22.7196, 75.8577);
                    }
                });
    }

}
