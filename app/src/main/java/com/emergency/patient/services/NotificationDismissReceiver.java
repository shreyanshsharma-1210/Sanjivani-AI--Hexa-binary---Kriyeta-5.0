package com.emergency.patient.services;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.emergency.patient.security.TokenManager;

/**
 * Re-starts the emergency foreground service if its notification is dismissed.
 */
public class NotificationDismissReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!TokenManager.isOnboardingComplete(context)) {
            return;
        }
        EmergencyBackgroundService.start(context);
    }
}
