package com.emergency.patient.activities;

import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.net.Uri;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.emergency.patient.R;
import com.emergency.patient.db.AppDatabaseProvider;
import com.emergency.patient.db.PatientEntity;
import com.emergency.patient.network.SmsFallbackManager;
import com.emergency.patient.network.SocketManager;
import com.emergency.patient.security.TokenManager;
import com.emergency.patient.utils.QrGenerator;

import java.util.Calendar;
import java.util.Set;

public class MedicalIdFragment extends Fragment {

    // ─── Views ────────────────────────────────────────────────────────────────
    private TextView tvPatientName, tvBloodGroup, tvPatientAge;
    private ImageView ivQrCode, ivAvatar, ivQrFullscreen;
    private FrameLayout sliderContainer, sliderThumb, countdownOverlay, qrFullscreenOverlay;
    private TextView tvSlideHint, tvCountdown, tvCountdownLabel;
    private Button btnCancelSos;

    // ─── Slider State ─────────────────────────────────────────────────────────
    private CountDownTimer countDownTimer;
    private ToneGenerator toneGenerator;
    private float initialX;
    private boolean isSosTriggered = false;

    // ─── Keep generated QR bitmap for fullscreen ───────────────────────────
    private Bitmap lastQrBitmap;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_medical_id, container, false);
        bindViews(view);
        setupQrFullscreen(view);
        setupSlider();


        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        cancelCountdown();
        populatePatientData();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        cancelCountdown();
    }

    // ─── Binding ─────────────────────────────────────────────────────────────

    private void bindViews(View view) {
        tvPatientName       = view.findViewById(R.id.tv_patient_name_med_id);
        tvBloodGroup        = view.findViewById(R.id.tv_blood_group_med_id);
        tvPatientAge        = view.findViewById(R.id.tv_patient_age_med_id);

        ivQrCode            = view.findViewById(R.id.iv_qr_code_med_id);
        ivAvatar            = view.findViewById(R.id.iv_avatar_med_id);
        ivQrFullscreen      = view.findViewById(R.id.iv_qr_fullscreen);
        qrFullscreenOverlay = view.findViewById(R.id.fl_qr_fullscreen);
        sliderContainer     = view.findViewById(R.id.slider_container_med);
        sliderThumb         = view.findViewById(R.id.slider_thumb_med);
        tvSlideHint         = view.findViewById(R.id.tv_slide_hint_med);
        countdownOverlay    = view.findViewById(R.id.fl_med_countdown_overlay);
        tvCountdown         = view.findViewById(R.id.tv_med_countdown);
        tvCountdownLabel    = view.findViewById(R.id.tv_med_countdown_label);
        btnCancelSos        = view.findViewById(R.id.btn_cancel_med_sos);

        btnCancelSos.setOnClickListener(v -> cancelCountdown());
    }

    // ─── Patient Data ────────────────────────────────────────────────────────

    private void populatePatientData() {
        if (getContext() == null) return;

        String uuid = TokenManager.getUUID(getContext());

        // Run DB query in background to avoid Main Thread violations
        new Thread(() -> {
            PatientEntity patient = AppDatabaseProvider.getInstance(getContext()).patientDao().getPatient(uuid);
            if (getActivity() == null) return;
            getActivity().runOnUiThread(() -> bindPatientToUI(patient, uuid));
        }).start();
    }

    private void bindPatientToUI(PatientEntity patient, String uuid) {
        if (getContext() == null) return;

        // 1. Full name
        String name = (patient != null && patient.fullName != null)
                ? patient.fullName : TokenManager.getPatientName(getContext());
        tvPatientName.setText(name.isEmpty() ? "Unknown" : name);

        // 2. Blood Group
        String blood = (patient != null && patient.bloodGroup != null)
                ? patient.bloodGroup : TokenManager.getBloodGroup(getContext());
        tvBloodGroup.setText("Blood Group: " + (blood != null && !blood.isEmpty() ? blood : "—"));

        // 3. Age
        long dobMillis = (patient != null && patient.dobMillis > 0)
                ? patient.dobMillis : TokenManager.getDOB(getContext());
        if (dobMillis > 0) {
            int age = calculateAge(dobMillis);
            tvPatientAge.setText(getString(R.string.years_old, age));
            tvPatientAge.setVisibility(View.VISIBLE);
        } else {
            tvPatientAge.setVisibility(View.GONE);
        }

        // 5. Profile Photo
        String photoUri = (patient != null) ? patient.profilePhotoUri : null;
        if (photoUri != null && !photoUri.isEmpty()) {
            try {
                ivAvatar.setImageURI(Uri.parse(photoUri));
                ivAvatar.setPadding(0, 0, 0, 0); // remove default padding when real photo is loaded
            } catch (Exception e) {
                ivAvatar.setImageResource(R.drawable.ic_blank_profile);
                ivAvatar.setPadding(0, 0, 0, 0);
            }
        } else {
            ivAvatar.setImageResource(R.drawable.ic_blank_profile);
            ivAvatar.setPadding(0, 0, 0, 0);
        }

        // 6. Generate QR (Encoded with Firebase UID for doctor app lookup)
        String qrData = TokenManager.getFirebaseUID(getContext());
        if (qrData == null || qrData.isEmpty()) {
            qrData = (patient != null) ? patient.uuid : uuid; // Fallback
        }

        if (qrData != null) {
            lastQrBitmap = QrGenerator.generate(qrData, 512);
            if (lastQrBitmap != null) {
                ivQrCode.setImageBitmap(lastQrBitmap);
            }
        }
    }

    // ─── QR Full Screen ──────────────────────────────────────────────────────

    private void setupQrFullscreen(View view) {
        // Tap the QR card to expand
        View qrCard = view.findViewById(R.id.card_qr_tap);
        qrCard.setOnClickListener(v -> {
            if (lastQrBitmap != null) {
                ivQrFullscreen.setImageBitmap(lastQrBitmap);
                qrFullscreenOverlay.setVisibility(View.VISIBLE);
            }
        });

        // Tap the overlay to dismiss
        qrFullscreenOverlay.setOnClickListener(v ->
                qrFullscreenOverlay.setVisibility(View.GONE));
    }

    // ─── Slider ──────────────────────────────────────────────────────────────

    @SuppressLint("ClickableViewAccessibility")
    private void setupSlider() {
        sliderThumb.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    initialX = event.getRawX();
                    return true;

                case MotionEvent.ACTION_MOVE:
                    float deltaX = event.getRawX() - initialX;
                    float maxTranslation = sliderContainer.getWidth() - sliderThumb.getWidth() - 16;

                    if (deltaX < 0) deltaX = 0;
                    if (deltaX > maxTranslation) deltaX = maxTranslation;

                    sliderThumb.setTranslationX(deltaX);

                    float alpha = 1.0f - (deltaX / (maxTranslation / 1.5f));
                    tvSlideHint.setAlpha(Math.max(0, alpha));
                    return true;

                case MotionEvent.ACTION_UP:
                    float finalDeltaX = sliderThumb.getTranslationX();
                    float threshold = (sliderContainer.getWidth() - sliderThumb.getWidth()) * 0.85f;

                    if (finalDeltaX >= threshold) {
                        startCountdown();
                    } else {
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
        if (countdownOverlay != null) countdownOverlay.setVisibility(View.GONE);
        if (sliderThumb != null) sliderThumb.setTranslationX(0);
        if (tvSlideHint != null) tvSlideHint.setAlpha(1.0f);
        if (toneGenerator != null) {
            toneGenerator.release();
            toneGenerator = null;
        }
    }

    private void triggerEmergency() {
        try {
            if (getActivity() == null) return;
            android.util.Log.wtf("DEBUG_PING", "!!! MedicalIdFragment: triggerEmergency() CALLED !!!");

            double lat = 22.7196;
            double lng = 75.8577;
            try {
                android.location.LocationManager locationManager = (android.location.LocationManager) getActivity().getSystemService(android.content.Context.LOCATION_SERVICE);
                if (androidx.core.content.ContextCompat.checkSelfPermission(getActivity(), android.Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
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

            // Trigger cloud webhook IMMEDIATELY with actual inline location
            com.emergency.patient.network.AmbulancePingManager.getInstance(getActivity()).sendPing(lat, lng);

            tvCountdownLabel.setText(R.string.processing_ai);
            SocketManager.getInstance(getActivity()).connect();

        // Fetch real GPS location

        com.emergency.patient.utils.LocationHelper.getLastKnownLocation(getActivity(), new com.emergency.patient.utils.LocationHelper.LocationCallback() {
            @Override
            public void onLocationResult(double lat, double lng) {
                if (getActivity() == null) return;
                
                // Emit real-time emergency to backend
                SocketManager.getInstance(getActivity()).emitEmergency(lat, lng, new SocketManager.DispatchCallback() {
                    @Override
                    public void onSuccess(String channel) {
                        if (getActivity() == null) return;
                        android.content.Intent intent = new android.content.Intent(getActivity(), EmergencyActiveActivity.class);
                        intent.putExtra(EmergencyActiveActivity.EXTRA_LAT, lat);
                        intent.putExtra(EmergencyActiveActivity.EXTRA_LNG, lng);
                        intent.putExtra(EmergencyActiveActivity.EXTRA_CHANNEL, channel);
                        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK | android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP);
                        startActivity(intent);
                    }

                    @Override
                    public void onFallbackRequired(double lat, double lng, String uuid) {
                        if (getActivity() == null) return;
                        android.content.Intent intent = new android.content.Intent(getActivity(), EmergencyActiveActivity.class);
                        intent.putExtra(EmergencyActiveActivity.EXTRA_LAT, lat);
                        intent.putExtra(EmergencyActiveActivity.EXTRA_LNG, lng);
                        intent.putExtra(EmergencyActiveActivity.EXTRA_CHANNEL, "SMS Fallback");
                        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK | android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP);
                        startActivity(intent);
                    }
                });
            }
        });
        } catch (Exception e) {
            android.util.Log.e("MedicalIdFragment", "SOS trigger failed", e);
            if (getActivity() != null) {
                android.content.Intent intent = new android.content.Intent(getActivity(), EmergencyActiveActivity.class);
                intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK | android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(intent);
            }
        }
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private int calculateAge(long dobMillis) {
        Calendar dob = Calendar.getInstance();
        dob.setTimeInMillis(dobMillis);
        Calendar today = Calendar.getInstance();
        int age = today.get(Calendar.YEAR) - dob.get(Calendar.YEAR);
        if (today.get(Calendar.DAY_OF_YEAR) < dob.get(Calendar.DAY_OF_YEAR)) {
            age--;
        }
        return age;
    }
}
