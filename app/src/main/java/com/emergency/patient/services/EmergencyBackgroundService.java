package com.emergency.patient.services;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.IBinder;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.emergency.patient.R;
import com.emergency.patient.activities.DashboardActivity;
import com.emergency.patient.activities.QuickAccessActivity;
import com.emergency.patient.security.TokenManager;
import com.emergency.patient.utils.QrGenerator;

/**
 * EmergencyBackgroundService — Persistent foreground service.
 *
 * Keeps the app alive in the background and provides a persistent notification
 * for quick access to Medical ID and SOS from the lock screen.
 */
public class EmergencyBackgroundService extends Service {

    public static final String CHANNEL_ID = "emergency_service_channel";
    public static final int NOTIFICATION_ID = 1001;
    private static final String ACTION_START_SERVICE = "com.emergency.patient.action.START_EMERGENCY_SERVICE";
    private static final String ACTION_STOP_SERVICE = "com.emergency.patient.action.STOP_EMERGENCY_SERVICE";

    private BroadcastReceiver screenOffReceiver;
    private boolean userRequestedStop = false;

    // ─── Lifecycle ───────────────────────────────────────────────────────────

    @Override
    public void onCreate() {
        super.onCreate();
        if (!com.emergency.patient.security.TokenManager.isOnboardingComplete(this)) {
            stopSelf();
            return;
        }
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent != null ? intent.getAction() : null;
        if (ACTION_STOP_SERVICE.equals(action)) {
            if (isPersonalPersistenceMode(this)) {
                android.util.Log.d("EmergencyService", "Ignoring stop request in personal persistence mode.");
                return START_STICKY;
            }
            userRequestedStop = true;
            stopForeground(STOP_FOREGROUND_REMOVE);
            stopSelf();
            return START_NOT_STICKY;
        }

        if (!com.emergency.patient.security.TokenManager.isOnboardingComplete(this)) {
            android.util.Log.d("EmergencyService", "Onboarding not complete, stopping service.");
            stopSelf();
            return START_NOT_STICKY;
        }

        Notification notification = buildNotification();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification,
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }

        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        android.util.Log.d("EmergencyService", "Service destroyed.");

        // Restart unless user explicitly requested stop.
        if ((isPersonalPersistenceMode(this) || !userRequestedStop)
                && com.emergency.patient.security.TokenManager.isOnboardingComplete(this)) {
            scheduleRestart(this);
        }
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        super.onTaskRemoved(rootIntent);
        if ((isPersonalPersistenceMode(this) || !userRequestedStop)
                && com.emergency.patient.security.TokenManager.isOnboardingComplete(this)) {
            scheduleRestart(this);
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    // ─── Notification ─────────────────────────────────────────────────────────

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Medical ID & SOS",
                    NotificationManager.IMPORTANCE_HIGH);
            channel.setDescription("Quick access for paramedics & ambulance");
            channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null)
                nm.createNotificationChannel(channel);
        }
    }

    private Notification buildNotification() {
        // Tapping the notification opens QuickAccessActivity
        Intent quickAccessIntent = new Intent(this, QuickAccessActivity.class);
        quickAccessIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, quickAccessIntent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        Intent dismissIntent = new Intent(this, NotificationDismissReceiver.class);
        PendingIntent dismissPendingIntent = PendingIntent.getBroadcast(
                this,
                3001,
                dismissIntent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        Bitmap qrBitmap = null;
        String uuid = TokenManager.getUUID(this);
        if (uuid != null && !uuid.trim().isEmpty()) {
            qrBitmap = QrGenerator.generate(uuid, 384);
        }

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Medical ID & Emergency SOS")
                .setContentText("Tap to open. Expand to show emergency QR.")
                .setSmallIcon(R.drawable.ic_sos_cross)
                .setContentIntent(pendingIntent)
                .setDeleteIntent(dismissPendingIntent)
                .setOngoing(true)
                .setAutoCancel(false)
                .setSilent(true)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE);

        if (qrBitmap != null) {
            builder.setLargeIcon(qrBitmap)
                    .setStyle(new NotificationCompat.BigPictureStyle()
                            .bigPicture(qrBitmap)
                            .bigLargeIcon((Bitmap) null)
                            .setSummaryText("Emergency QR ready for paramedic scan"));
        }

        Notification notification = builder.build();

        // Critical: Set flags for non-dismissible behavior
        notification.flags |= Notification.FLAG_ONGOING_EVENT | Notification.FLAG_NO_CLEAR;

        return notification;
    }

    // ─── Static Helpers ───────────────────────────────────────────────────────

    /** Call from any Activity to start the service. */
    public static void start(Context context) {
        if (!com.emergency.patient.security.TokenManager.isOnboardingComplete(context)) {
            return;
        }
        Intent intent = new Intent(context, EmergencyBackgroundService.class);
        intent.setAction(ACTION_START_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent);
        } else {
            context.startService(intent);
        }
    }

    /** Stop the service (e.g. when the user fully logs out). */
    public static void stop(Context context) {
        if (isPersonalPersistenceMode(context)) {
            android.util.Log.d("EmergencyService", "Stop ignored in personal persistence mode.");
            return;
        }
        Intent intent = new Intent(context, EmergencyBackgroundService.class);
        intent.setAction(ACTION_STOP_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent);
        } else {
            context.startService(intent);
        }
    }

    private static void scheduleRestart(Context context) {
        Intent restartIntent = new Intent(context, EmergencyBackgroundService.class);
        restartIntent.setAction(ACTION_START_SERVICE);

        PendingIntent restartPendingIntent = PendingIntent.getService(
                context,
                2001,
                restartIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        android.app.AlarmManager alarmManager = (android.app.AlarmManager) context
                .getSystemService(Context.ALARM_SERVICE);
        if (alarmManager != null) {
            long triggerAtMillis = System.currentTimeMillis() + 1000;
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setExactAndAllowWhileIdle(
                            android.app.AlarmManager.RTC_WAKEUP,
                            triggerAtMillis,
                            restartPendingIntent);
                } else {
                    alarmManager.setExact(
                            android.app.AlarmManager.RTC_WAKEUP,
                            triggerAtMillis,
                            restartPendingIntent);
                }
            } catch (SecurityException ignored) {
                alarmManager.set(
                        android.app.AlarmManager.RTC_WAKEUP,
                        triggerAtMillis,
                        restartPendingIntent);
            }
        }
    }

    private static boolean isPersonalPersistenceMode(Context context) {
        return (context.getApplicationInfo().flags & android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0;
    }
}
