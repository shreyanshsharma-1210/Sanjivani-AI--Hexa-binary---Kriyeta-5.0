package com.emergency.patient.activities;

import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.Looper;
import android.os.Vibrator;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.ScaleAnimation;
import android.os.Build;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.emergency.patient.R;
import com.emergency.patient.db.AppDatabaseProvider;
import com.emergency.patient.db.FallEventEntity;
import com.emergency.patient.services.EmergencyDispatcher;

/**
 * FallDetectionAlertActivity — Lockscreen emergency siren and cancellation UI.
 *
 * Displayed over the lock screen immediately when the quiescence check confirms
 * a fall. The user has 30 seconds to tap "I'm Okay" to cancel a false alarm.
 * If the timer expires, EmergencyDispatcher fires the full alert.
 *
 * Key capabilities:
 *   • Shows over lock screen (setShowWhenLocked / setTurnScreenOn)
 *   • Plays alarm-grade audio that bypasses Silent / DND mode
 *   • Pulses the SOS ring with a scale animation
 *   • Vibrates continuously for tactile feedback if the user dropped face down
 */
public class FallDetectionAlertActivity extends AppCompatActivity {

    private static final String TAG = "FallDetectionAlert";

    public static final String EXTRA_PEAK_G  = "peak_g_force";
    public static final String EXTRA_SIGMA2  = "sigma2";
    public static final String EXTRA_TILT    = "tilt_angle";
    public static final String EXTRA_PRESET  = "sensitivity_preset";
    public static final int    COUNTDOWN_SEC = 30;

    // ─── UI ───────────────────────────────────────────────────────────────────
    private TextView tvCountdown;
    private TextView tvStatus;
    private View     sosRingView;
    private View     layoutRoot;
    private Button   btnOkay;

    // ─── Media ────────────────────────────────────────────────────────────────
    private MediaPlayer mediaPlayer;
    private Vibrator    vibrator;

    // ─── Timers & State ───────────────────────────────────────────────────────
    private CountDownTimer countDownTimer;
    private float peakGForce;
    private float sigma2;
    private float tilt;
    private String preset;

    // ─── Lifecycle ────────────────────────────────────────────────────────────

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Must be set BEFORE super.onCreate() for consistency on some Android versions
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        } else {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                    | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                    | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }

        super.onCreate(savedInstanceState);

        // Keep screen on during the alert countdown
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        setContentView(R.layout.activity_fall_detection_alert);

        // Dismiss keyguard so alert is visible without unlocking
        android.app.KeyguardManager km = (android.app.KeyguardManager) getSystemService(KEYGUARD_SERVICE);
        if (km != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            km.requestDismissKeyguard(this, null);
        }

        // Read fall event context from intent
        peakGForce = getIntent().getFloatExtra(EXTRA_PEAK_G, 0f);
        sigma2     = getIntent().getFloatExtra(EXTRA_SIGMA2, 0f);
        tilt       = getIntent().getFloatExtra(EXTRA_TILT, 0f);
        preset     = getIntent().getStringExtra(EXTRA_PRESET);

        bindViews();
        startAlarmAudio();
        startVibration();
        startPulseAnimation();
        startCountdown();
    }

    @Override
    protected void onDestroy() {
        try {
            if (vibrator != null) vibrator.cancel();
        } catch (SecurityException e) {
            Log.e(TAG, "Failed to cancel vibration", e);
        }
        if (countDownTimer != null) countDownTimer.cancel();
        stopAlarm();
        super.onDestroy();
    }

    // ─── UI Setup ─────────────────────────────────────────────────────────────

    private void bindViews() {
        tvCountdown  = findViewById(R.id.tv_countdown);
        tvStatus     = findViewById(R.id.tv_alert_status);
        sosRingView  = findViewById(R.id.view_sos_ring);
        layoutRoot   = findViewById(R.id.layout_root);
        btnOkay      = findViewById(R.id.btn_im_okay);

        tvStatus.setText("Possible fall detected\nPeak: " + String.format("%.1f", peakGForce / 9.8f) + "g");

        btnOkay.setOnClickListener(v -> onUserConfirmedSafe());
    }

    // ─── Countdown ────────────────────────────────────────────────────────────

    private void startCountdown() {
        countDownTimer = new CountDownTimer(COUNTDOWN_SEC * 1000L, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                int sec = (int) (millisUntilFinished / 1000);
                tvCountdown.setText(String.valueOf(sec));
                
                if (sec <= 10) {
                    tvCountdown.setTextColor(0xFFFF3B30); // Bright Red
                    // Pulse background between dark and bright red for urgency
                    if (sec % 2 == 0) {
                        layoutRoot.setBackgroundColor(0xFF8B0000); // Dark Red
                    } else {
                        layoutRoot.setBackgroundColor(0xFFFF3B30); // Bright Red
                    }
                }
            }

            @Override
            public void onFinish() {
                tvCountdown.setText("0");
                Log.w(TAG, "Countdown expired — dispatching alert!");
                triggerEmergencyDispatch();
            }
        };
        countDownTimer.start();
    }

    // ─── User Actions ─────────────────────────────────────────────────────────

    /** User confirms they are safe — logs as FALSE_POSITIVE. */
    private void onUserConfirmedSafe() {
        Log.d(TAG, "User cancelled: FALSE_POSITIVE");
        if (countDownTimer != null) countDownTimer.cancel();
        stopAlarm();

        logFallOutcome("FALSE_POSITIVE");
        finish();
    }

    /** Countdown expired — device was still for 30+ seconds. Dispatch alert. */
    private void triggerEmergencyDispatch() {
        stopAlarm();
        tvStatus.setText("Alerting emergency contacts…");
        btnOkay.setEnabled(false);

        logFallOutcome("ALERT_DISPATCHED");

        new Thread(() -> {
            EmergencyDispatcher dispatcher = new EmergencyDispatcher(this);
            dispatcher.dispatch(0); // Pass actual DB row ID if wired
            runOnUiThread(() -> {
                tvStatus.setText("✅ Contacts notified. Stay calm.");
                new Handler(Looper.getMainLooper()).postDelayed(this::finish, 4000);
            });
        }).start();
    }

    // ─── Audio ────────────────────────────────────────────────────────────────

    private void startAlarmAudio() {
        try {
            // USAGE_ALARM bypasses Do Not Disturb and Silent mode — industry standard
            AudioAttributes attrs = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build();

            // Stream to STREAM_ALARM and request full volume
            AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
            if (am != null) {
                am.setStreamVolume(AudioManager.STREAM_ALARM,
                        am.getStreamMaxVolume(AudioManager.STREAM_ALARM), 0);
            }

            Uri alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
            if (alarmUri == null) alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);

            mediaPlayer = new MediaPlayer();
            mediaPlayer.setAudioAttributes(attrs);
            mediaPlayer.setDataSource(this, alarmUri);
            mediaPlayer.setLooping(true);
            mediaPlayer.prepare();
            mediaPlayer.start();
        } catch (Exception e) {
            Log.e(TAG, "Failed to start alarm audio", e);
        }
    }

    private void stopAlarm() {
        if (mediaPlayer != null) {
            if (mediaPlayer.isPlaying()) mediaPlayer.stop();
            mediaPlayer.release();
            mediaPlayer = null;
        }
    }

    // ─── Vibration ────────────────────────────────────────────────────────────

    private void startVibration() {
        try {
            vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
            if (vibrator != null && vibrator.hasVibrator()) {
                long[] pattern = {0, 500, 300, 500}; // Off, On, Off, On (repeating)
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    vibrator.vibrate(android.os.VibrationEffect.createWaveform(pattern, 0));
                } else {
                    vibrator.vibrate(pattern, 0); 
                }
            }
        } catch (SecurityException e) {
            Log.e(TAG, "Vibration permission error or hardware failure", e);
        } catch (Exception e) {
            Log.e(TAG, "Vibration error", e);
        }
    }

    // ─── Pulse Animation ──────────────────────────────────────────────────────

    private void startPulseAnimation() {
        if (sosRingView == null) return;
        ScaleAnimation pulse = new ScaleAnimation(
                1f, 1.2f, 1f, 1.2f,
                Animation.RELATIVE_TO_SELF, 0.5f,
                Animation.RELATIVE_TO_SELF, 0.5f
        );
        pulse.setDuration(700);
        pulse.setRepeatCount(Animation.INFINITE);
        pulse.setRepeatMode(Animation.REVERSE);
        sosRingView.startAnimation(pulse);
    }

    // ─── Black Box ────────────────────────────────────────────────────────────

    private void logFallOutcome(String outcome) {
        new Thread(() -> {
            try {
                FallEventEntity e = new FallEventEntity();
                e.timestamp           = System.currentTimeMillis();
                e.peakGForce          = peakGForce;
                e.postImpactVariance  = sigma2;
                e.postImpactTiltAngle = tilt;
                e.sensitivityPreset   = preset;
                e.outcome             = outcome;
                AppDatabaseProvider.getInstance(this).fallEventDao().insert(e);
            } catch (Exception ex) {
                Log.e(TAG, "Failed to log fall outcome", ex);
            }
        }).start();
    }
}
