package com.emergency.patient.scheduling;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;

import com.emergency.patient.R;

public class NotificationHelper {

    public static void showExpiryNotification(Context context, String drugName, int daysRemaining) {
        String title = "Medication Expiring Soon!";
        String content = drugName + " is about to expire in " + daysRemaining + " day" + (daysRemaining == 1 ? "" : "s") + ".";
        if (daysRemaining == 0) {
            content = drugName + " expires today! Please dispose safely.";
        }

        NotificationCompat.Builder builder =
            new NotificationCompat.Builder(context, "med_channel")
                .setContentTitle(title)
                .setContentText(content)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(content));

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                return; 
            }
        }
        
        // Ensure channel exists (re-using med_channel)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            android.app.NotificationChannel channel = new android.app.NotificationChannel(
                "med_channel",
                "Medication Reminders",
                android.app.NotificationManager.IMPORTANCE_HIGH
            );
            android.app.NotificationManager manager = context.getSystemService(android.app.NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }

        // Use a high offset ID for expiry to avoid collision with schedule IDs
        int notificationId = 100000 + drugName.hashCode();
        NotificationManagerCompat.from(context).notify(notificationId, builder.build());
    }

    public static void showNotification(Context context, String drugName, int scheduleId) {

        // Taken Action
        Intent takenIntent = new Intent(context, ActionReceiver.class);
        takenIntent.putExtra("action", "TAKEN");
        takenIntent.putExtra("scheduleId", scheduleId);
        PendingIntent takenPending = PendingIntent.getBroadcast(
                context, scheduleId * 2, takenIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        // Skip Action
        Intent skipIntent = new Intent(context, ActionReceiver.class);
        skipIntent.putExtra("action", "SKIPPED");
        skipIntent.putExtra("scheduleId", scheduleId);
        PendingIntent skipPending = PendingIntent.getBroadcast(
                context, (scheduleId * 2) + 1, skipIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        NotificationCompat.Builder builder =
            new NotificationCompat.Builder(context, "med_channel")
                .setContentTitle("Medication Reminder")
                .setContentText("Time to take your " + drugName)
                .addAction(0, "Taken", takenPending)
                .addAction(0, "Skip", skipPending)
                .setSmallIcon(R.mipmap.ic_launcher) // using default icon since R.drawable.ic_notification might not exist
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                return; // Permission not granted 
            }
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            android.app.NotificationChannel channel = new android.app.NotificationChannel(
                "med_channel",
                "Medication Reminders",
                android.app.NotificationManager.IMPORTANCE_HIGH
            );
            android.app.NotificationManager manager = context.getSystemService(android.app.NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }

        NotificationManagerCompat.from(context).notify(scheduleId, builder.build());
    }
}
