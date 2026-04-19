package com.emergency.patient.activities;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.emergency.patient.R;
import com.emergency.patient.network.SmsFallbackManager;
import com.emergency.patient.network.SocketManager;
import com.emergency.patient.security.TokenManager;
import com.emergency.patient.utils.QrGenerator;

import java.util.Set;

/**
 * QuickAccessActivity — Lock-screen accessible Medical ID & SOS launcher.
 * 
 * Features:
 * 1. Large QR Code for paramedic scanning.
 * 2. Patient Profile Summary (Name, Condition).
 * 3. Slide-to-Book Ambulance button (3s countdown).
 */
public class QuickAccessActivity extends AppCompatActivity {

    private TextView tvName, tvCountdown, tvCountdownLabel, tvSlideHint;
    private ImageView ivQr;
    private FrameLayout sliderContainer, sliderThumb, countdownOverlay;
    private Button btnCancel;

    private CountDownTimer countDownTimer;
    private ToneGenerator toneGenerator;
    private float initialX;
    private boolean isSosTriggered = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Allow activity to show over lock screen
        getWindow().addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD |
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON |
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
        );

        setContentView(R.layout.activity_quick_access);
        bindViews();
        populateData();
        setupSlider();

        btnCancel.setOnClickListener(v -> cancelCountdown());
    }

    private void bindViews() {
        tvName             = findViewById(R.id.tv_quick_name);
        ivQr               = findViewById(R.id.iv_quick_qr);
        sliderContainer    = findViewById(R.id.slider_container);
        sliderThumb        = findViewById(R.id.slider_thumb);
        tvSlideHint        = findViewById(R.id.tv_slide_hint);
        countdownOverlay   = findViewById(R.id.fl_quick_countdown_overlay);
        tvCountdown        = findViewById(R.id.tv_quick_countdown);
        tvCountdownLabel   = findViewById(R.id.tv_quick_countdown_label);
        btnCancel          = findViewById(R.id.btn_cancel_quick_sos);
    }

    private void populateData() {
        String name = TokenManager.getPatientName(this);
        tvName.setText(name.isEmpty() ? getString(android.R.string.unknownName) : name);



        String uuid = TokenManager.getUUID(this);
        if (uuid != null) {
            Bitmap qr = QrGenerator.generate(uuid, 512);
            ivQr.setImageBitmap(qr);
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private void setupSlider() {
        sliderThumb.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    initialX = event.getRawX();
                    return true;

                case MotionEvent.ACTION_MOVE:
                    float deltaX = event.getRawX() - initialX;
                    float maxTranslation = sliderContainer.getWidth() - sliderThumb.getWidth() - 16; // 8dp padding * 2
                    
                    if (deltaX < 0) deltaX = 0;
                    if (deltaX > maxTranslation) deltaX = maxTranslation;
                    
                    sliderThumb.setTranslationX(deltaX);
                    
                    // Fade out hint text as we slide
                    float alpha = 1.0f - (deltaX / (maxTranslation / 1.5f));
                    tvSlideHint.setAlpha(Math.max(0, alpha));
                    return true;

                case MotionEvent.ACTION_UP:
                    float finalDeltaX = sliderThumb.getTranslationX();
                    float threshold = (sliderContainer.getWidth() - sliderThumb.getWidth()) * 0.85f;
                    
                    if (finalDeltaX >= threshold) {
                        // Success! Trigger SOS
                        startCountdown();
                    } else {
                        // Reset slider
                        sliderThumb.animate().translationX(0).setDuration(200).start();
                        tvSlideHint.animate().alpha(1.0f).setDuration(200).start();
                    }
                    return true;
            }
            return false;
        });
    }

    private void startCountdown() {
        if (isSosTriggered) return;
        isSosTriggered = true;

        countdownOverlay.setVisibility(View.VISIBLE);

        // Audio setup
        try {
            toneGenerator = new ToneGenerator(AudioManager.STREAM_ALARM, 100);
        } catch (Exception ignored) {}

        countDownTimer = new CountDownTimer(3000, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                int seconds = (int) (millisUntilFinished / 1000) + 1;
                tvCountdown.setText(String.valueOf(seconds));
                if (toneGenerator != null) {
                    toneGenerator.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 400);
                }
            }

            @Override
            public void onFinish() {
                tvCountdown.setText("0");
                triggerEmergency();
            }
        }.start();
    }

    private void cancelCountdown() {
        if (countDownTimer != null) {
            countDownTimer.cancel();
            countDownTimer = null;
        }
        isSosTriggered = false;
        countdownOverlay.setVisibility(View.GONE);
        sliderThumb.setTranslationX(0);
        tvSlideHint.setAlpha(1.0f);
        if (toneGenerator != null) {
            toneGenerator.release();
            toneGenerator = null;
        }
    }

    private void triggerEmergency() {
        try {
            android.util.Log.wtf("DEBUG_PING", "!!! CRITICAL: triggerEmergency() CALLED !!!");

            tvCountdownLabel.setText(R.string.processing_ai);

            double lat = 22.7196;
            double lng = 75.8577;
            try {
                android.location.LocationManager locationManager = (android.location.LocationManager) getSystemService(android.content.Context.LOCATION_SERVICE);
                if (androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    android.location.Location loc = locationManager.getLastKnownLocation(android.location.LocationManager.GPS_PROVIDER);
                    if (loc == null) loc = locationManager.getLastKnownLocation(android.location.LocationManager.NETWORK_PROVIDER);
                    if (loc != null) {
                        lat = loc.getLatitude();
                        lng = loc.getLongitude();
                    }
                }
            } catch (Exception e) {
                // Ignore and use fallback
            }

            // Send ping to webhook IMMEDIATELY with actual user location
            com.emergency.patient.network.AmbulancePingManager.getInstance(this).sendPing(lat, lng);

            SocketManager.getInstance(this).connect();

            // Fetch real GPS location for the map/backend
            com.emergency.patient.utils.LocationHelper.getLastKnownLocation(this, new com.emergency.patient.utils.LocationHelper.LocationCallback() {
            @Override
            public void onLocationResult(double lat, double lng) {
                android.util.Log.e("DEBUG_PING", "!!! QuickAccess: onLocationResult received! lat=" + lat + ", lng=" + lng + " !!!");
                
                // Emit real-time emergency to backend
                SocketManager.getInstance(QuickAccessActivity.this).emitEmergency(lat, lng, new SocketManager.DispatchCallback() {


                    @Override
                    public void onSuccess(String channel) {
                        Intent intent = new Intent(QuickAccessActivity.this, EmergencyActiveActivity.class);
                        intent.putExtra(EmergencyActiveActivity.EXTRA_LAT, lat);
                        intent.putExtra(EmergencyActiveActivity.EXTRA_LNG, lng);
                        intent.putExtra(EmergencyActiveActivity.EXTRA_CHANNEL, channel);
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                        startActivity(intent);
                        finish();
                    }

                    @Override
                    public void onFallbackRequired(double lat, double lng, String uuid) {
                        // If websocket fails, still go to active page but SMS fallback will be handled by Manager
                        Intent intent = new Intent(QuickAccessActivity.this, EmergencyActiveActivity.class);
                        intent.putExtra(EmergencyActiveActivity.EXTRA_LAT, lat);
                        intent.putExtra(EmergencyActiveActivity.EXTRA_LNG, lng);
                        intent.putExtra(EmergencyActiveActivity.EXTRA_CHANNEL, "SMS Fallback");
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                        startActivity(intent);
                        finish();
                    }
                });
            }
        });
        } catch (Exception e) {
            android.util.Log.e("QuickAccess", "Emergency trigger critical failure", e);
            // Emergency fallback: just go to the map
            Intent intent = new Intent(this, EmergencyActiveActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(intent);
            finish();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cancelCountdown();
    }
}
