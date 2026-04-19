package com.emergency.patient.services;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

/**
 * BootReceiver — Restarts EmergencyBackgroundService after device reboot.
 * Registered in AndroidManifest.xml with RECEIVE_BOOT_COMPLETED permission.
 */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            if (com.emergency.patient.security.TokenManager.isOnboardingComplete(context)) {
                EmergencyBackgroundService.start(context);
            }
        }
    }
}
