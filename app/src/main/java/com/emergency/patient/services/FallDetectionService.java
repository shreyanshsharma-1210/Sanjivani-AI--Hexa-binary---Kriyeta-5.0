package com.emergency.patient.services;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.pm.PackageManager;

import com.emergency.patient.R;
import com.emergency.patient.activities.DashboardActivity;
import com.emergency.patient.activities.FallDetectionAlertActivity;
import com.emergency.patient.db.AppDatabaseProvider;
import com.emergency.patient.db.FallEventEntity;

/**
 * FallDetectionService — Independent, safety-critical fall detection engine.
 */
public class FallDetectionService extends Service implements SensorEventListener {

    private static final String TAG = "FallDetectionService";

    public static final String CHANNEL_ID       = "fall_detection_channel";
    public static final String ALERT_CHANNEL_ID = "fall_alert_channel";
    public static final int NOTIFICATION_ID     = 2001;
    public static final int ALERT_NOTIFICATION_ID = 9001;

    public static final String ACTION_START  = "com.emergency.patient.action.START_FALL_DETECTION";
    public static final String ACTION_STOP   = "com.emergency.patient.action.STOP_FALL_DETECTION";
    public static final String EXTRA_PRESET  = "sensitivity_preset";

    private enum FallState {
        DORMANT, FREE_FALL, IMPACT_DETECTED, QUIESCENCE_CHECK
    }
    private FallState currentState = FallState.DORMANT;

    private SensorManager sensorManager;
    private Sensor accelerometer;
    private PowerManager.WakeLock wakeLock;

    private String sensitivityPreset = "NORMAL";
    private float impactThreshold;

    private final float[] gravity = {0f, 0f, 9.8f};
    private long freeFallStartMs   = 0;
    private long impactDetectedMs  = 0;

    private static final int BUFFER_SIZE = 500;
    private final float[] circularBuffer = new float[BUFFER_SIZE];
    private int bufferHead = 0;

    private static final int VARIANCE_WINDOW = 50; 
    private final float[] varianceWindow   = new float[VARIANCE_WINDOW];
    private int varianceHead = 0;
    private final Handler quiescenceHandler  = new Handler(Looper.getMainLooper());

    private float peakGForce         = 0f;
    private String preImpactActivity = "UNKNOWN";

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // ── Permission Self-Check (defensive guard) ────────────────────────────
        // If the service is restarted (START_STICKY) after being killed, permissions
        // may not be granted yet. Stop safely rather than throw a SecurityException.
        boolean hasPermission =
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION)
                == PackageManager.PERMISSION_GRANTED
            || ContextCompat.checkSelfPermission(this, Manifest.permission.BODY_SENSORS)
                == PackageManager.PERMISSION_GRANTED
            || ContextCompat.checkSelfPermission(this, Manifest.permission.HIGH_SAMPLING_RATE_SENSORS)
                == PackageManager.PERMISSION_GRANTED;

        if (!hasPermission) {
            Log.e(TAG, "Missing required sensor permissions — stopping service safely.");
            stopSelf();
            return START_NOT_STICKY;
        }

        // ── Step 1: startForeground() FIRST, before anything else ─────────────
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, buildNotification(),
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH);
        } else {
            startForeground(NOTIFICATION_ID, buildNotification());
        }

        // ── Step 2: WakeLock AFTER foreground is confirmed ────────────────────
        acquireWakeLock();

        // Handle STOP action
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopSelf();
            return START_NOT_STICKY;
        }

        // Handle null intent (system restart) — preset defaults to last known value
        if (intent != null && intent.hasExtra(EXTRA_PRESET)) {
            sensitivityPreset = intent.getStringExtra(EXTRA_PRESET);
        }
        impactThreshold = FallPhysicsEngine.getImpactThreshold(sensitivityPreset);
        Log.d(TAG, "Starting with preset=" + sensitivityPreset + " threshold=" + impactThreshold + " m/s²");

        registerSensor(SensorManager.SENSOR_DELAY_GAME);

        // Use START_REDELIVER_INTENT instead of START_STICKY.
        // START_STICKY restarts with a null intent — our permission guard then fires
        // without any context and has no valid preset. START_REDELIVER_INTENT ensures
        // the system only restarts us if it has the original intent, so the preset and
        // permission state are both valid on restart.
        return START_REDELIVER_INTENT;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        sensorManager.unregisterListener(this);
        quiescenceHandler.removeCallbacksAndMessages(null);
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() != Sensor.TYPE_ACCELEROMETER) return;
        float magnitude = FallPhysicsEngine.magnitude(event.values[0], event.values[1], event.values[2]);
        FallPhysicsEngine.applyLowPassFilter(gravity, event.values, FallPhysicsEngine.ALPHA);
        circularBuffer[bufferHead % BUFFER_SIZE] = magnitude;
        bufferHead++;
        if (magnitude > peakGForce) peakGForce = magnitude;
        processStateMachine(magnitude);
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    private void processStateMachine(float magnitude) {
        long nowMs = System.currentTimeMillis();
        switch (currentState) {
            case DORMANT:
                if (magnitude < FallPhysicsEngine.FREE_FALL_THRESHOLD) {
                    freeFallStartMs = nowMs;
                    peakGForce = 0f;
                    transitionTo(FallState.FREE_FALL);
                }
                break;
            case FREE_FALL:
                if (magnitude >= impactThreshold) {
                    if (nowMs - freeFallStartMs >= FallPhysicsEngine.MIN_FREE_FALL_MS) {
                        impactDetectedMs = nowMs;
                        transitionTo(FallState.IMPACT_DETECTED);
                    } else {
                        transitionTo(FallState.DORMANT);
                    }
                } else if (magnitude >= FallPhysicsEngine.FREE_FALL_THRESHOLD) {
                    // We've exited free-fall but haven't hit the impact spike yet.
                    // Give it a 1-second grace period to register the impact.
                    if (nowMs - freeFallStartMs > 1000) {
                        transitionTo(FallState.DORMANT);
                    }
                }
                // If magnitude is still < FREE_FALL_THRESHOLD, we remain strictly in FREE_FALL.
                break;
            case IMPACT_DETECTED:
                if (nowMs - impactDetectedMs > FallPhysicsEngine.IMPACT_WINDOW_MS) {
                    transitionTo(FallState.QUIESCENCE_CHECK);
                    startQuiescenceMonitoring();
                }
                break;
            case QUIESCENCE_CHECK:
                varianceWindow[varianceHead % VARIANCE_WINDOW] = magnitude;
                varianceHead++;
                break;
        }
    }

    private void transitionTo(FallState newState) {
        Log.d(TAG, "[StateMachine] " + currentState + " → " + newState);
        currentState = newState;
    }

    private void startQuiescenceMonitoring() {
        varianceHead = 0;
        final long[] checkCount = {0};
        final int maxChecks = 5;
        Runnable checker = new Runnable() {
            @Override
            public void run() {
                checkCount[0]++;
                float sigma2 = FallPhysicsEngine.variance(varianceWindow);
                float tilt = FallPhysicsEngine.tiltAngleDegrees(gravity);
                if (sigma2 < FallPhysicsEngine.QUIESCENCE_VARIANCE_THRESHOLD) {
                    if (checkCount[0] >= maxChecks) {
                        dispatchFallAlert(sigma2, tilt);
                    } else {
                        quiescenceHandler.postDelayed(this, 2000);
                    }
                } else {
                    transitionTo(FallState.DORMANT);
                }
            }
        };
        quiescenceHandler.postDelayed(checker, 2000);
    }

    private void dispatchFallAlert(float sigma2, float tilt) {
        logFallEvent(impactDetectedMs - freeFallStartMs, sigma2, tilt, peakGForce, "PENDING");
        
        // Android 12+ Restriction: Launch via FullScreenIntent to bypass Background Activity Launch blocks
        launchFallAlertNotification(sigma2, tilt);

        final float gx = gravity[0];
        final float gy = gravity[1];
        final float gz = gravity[2];
        new Thread(() -> {
            try {
                java.net.URL url = new java.net.URL("https://sanjeevani-backend-0raw.onrender.com/api/alert");
                java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);

                String json = String.format(java.util.Locale.US,
                        "{\"type\":\"fall\",\"lat\":22.821015,\"lng\":75.943021,\"accel\":{\"x\":%.2f,\"y\":%.2f,\"z\":%.2f}}",
                        gx, gy, gz);

                try (java.io.OutputStream os = conn.getOutputStream()) {
                    byte[] input = json.getBytes("utf-8");
                    os.write(input, 0, input.length);
                }
                Log.d(TAG, "Backend Alert Triggered. Response: " + conn.getResponseCode());
            } catch (Exception e) {
                Log.e(TAG, "Failed to post to backend", e);
            }
        }).start();

        transitionTo(FallState.DORMANT);
    }

    private void logFallEvent(long freeFallMs, float sigma2, float tilt, float peak, String outcome) {
        new Thread(() -> {
            try {
                FallEventEntity event = new FallEventEntity();
                event.timestamp = System.currentTimeMillis();
                event.peakGForce = peak;
                event.freeFallDurationMs = freeFallMs;
                event.postImpactVariance = sigma2;
                event.postImpactTiltAngle = tilt;
                event.preImpactActivity = preImpactActivity;
                event.sensitivityPreset = sensitivityPreset;
                event.outcome = outcome;
                AppDatabaseProvider.getInstance(this).fallEventDao().insert(event);
            } catch (Exception e) {
                Log.e(TAG, "Failed to log fall event", e);
            }
        }).start();
    }

    private void registerSensor(int delay) {
        sensorManager.unregisterListener(this);
        if (accelerometer != null) {
            sensorManager.registerListener(this, accelerometer, delay);
        }
    }

    private void acquireWakeLock() {
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (pm != null && (wakeLock == null || !wakeLock.isHeld())) {
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                    "EmergencyPatient::FallDetectionWakeLock");
            // Use a timeout to avoid an indefinite lock if the service ever fails
            // silently. The WakeLock auto-releases after 10 minutes and re-acquired
            // on the next sensor event cycle if still needed.
            wakeLock.acquire(10 * 60 * 1000L); // 10-minute max
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm == null) return;

            // Channel 1: Persistent monitoring (Low importance)
            NotificationChannel ch = new NotificationChannel(CHANNEL_ID, "Fall Detection Monitoring", NotificationManager.IMPORTANCE_LOW);
            nm.createNotificationChannel(ch);

            // Channel 2: Emergency Alerts (High importance / Sound / Override DND)
            NotificationChannel alertCh = new NotificationChannel(ALERT_CHANNEL_ID, "Emergency Fall Alerts", NotificationManager.IMPORTANCE_HIGH);
            alertCh.setDescription("High priority alerts when a fall is detected.");
            alertCh.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            nm.createNotificationChannel(alertCh);
        }
    }

    private void launchFallAlertNotification(float sigma2, float tilt) {
        Intent alertIntent = new Intent(this, FallDetectionAlertActivity.class);
        alertIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        alertIntent.putExtra(FallDetectionAlertActivity.EXTRA_PEAK_G, peakGForce);
        alertIntent.putExtra(FallDetectionAlertActivity.EXTRA_SIGMA2, sigma2);
        alertIntent.putExtra(FallDetectionAlertActivity.EXTRA_TILT, tilt);
        alertIntent.putExtra(FallDetectionAlertActivity.EXTRA_PRESET, sensitivityPreset);

        PendingIntent fullScreenPendingIntent = PendingIntent.getActivity(this, 101, alertIntent, 
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        androidx.core.app.NotificationCompat.Builder builder = new androidx.core.app.NotificationCompat.Builder(this, ALERT_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_sos_cross)
                .setContentTitle("⚠️ FALL DETECTED!")
                .setContentText("Are you okay? Respond immediately.")
                .setPriority(androidx.core.app.NotificationCompat.PRIORITY_MAX)
                .setCategory(androidx.core.app.NotificationCompat.CATEGORY_ALARM)
                .setVisibility(androidx.core.app.NotificationCompat.VISIBILITY_PUBLIC)
                .setAutoCancel(false)
                .setOngoing(true);

        // Android 14 (API 34) Security Check: Ensure app is allowed to use Full Screen Intent
        boolean canShowFullscreen = true;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null && !nm.canUseFullScreenIntent()) {
                canShowFullscreen = false;
                Log.w(TAG, "USE_FULL_SCREEN_INTENT permission not granted by user. Falling back to high-priority notification.");
            }
        }

        if (canShowFullscreen) {
            builder.setFullScreenIntent(fullScreenPendingIntent, true);
        } else {
            builder.setContentIntent(fullScreenPendingIntent);
        }

        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm != null) {
            nm.notify(ALERT_NOTIFICATION_ID, builder.build());
        }
    }

    private Notification buildNotification() {
        Intent tapIntent = new Intent(this, DashboardActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, tapIntent, 
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        // Required for lockscreen override on API 29+
        Intent fullScreenIntent = new Intent(this, FallDetectionAlertActivity.class);
        PendingIntent fullScreenPendingIntent = PendingIntent.getActivity(this, 0, fullScreenIntent, 
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        return new androidx.core.app.NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Fall Guard Active")
                .setContentText("Monitoring · " + sensitivityPreset)
                .setSmallIcon(R.drawable.ic_sos_cross)
                .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
                .setCategory(androidx.core.app.NotificationCompat.CATEGORY_SERVICE)
                .setVisibility(androidx.core.app.NotificationCompat.VISIBILITY_PUBLIC)
                .setFullScreenIntent(fullScreenPendingIntent, true) 
                .setOngoing(true)
                .setContentIntent(pi)
                .build();
    }

    public static void start(Context context, String preset) {
        Intent intent = new Intent(context, FallDetectionService.class);
        intent.setAction(ACTION_START);
        intent.putExtra(EXTRA_PRESET, preset != null ? preset : "NORMAL");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent);
        } else {
            context.startService(intent);
        }
    }

    public static void stop(Context context) {
        Intent intent = new Intent(context, FallDetectionService.class);
        intent.setAction(ACTION_STOP);
        context.startService(intent);
    }
}
