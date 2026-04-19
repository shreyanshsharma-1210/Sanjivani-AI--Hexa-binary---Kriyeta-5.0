package com.emergency.patient.activities;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.emergency.patient.R;
import com.emergency.patient.network.SocketManager;
import com.emergency.patient.network.ApiService;
import com.emergency.patient.security.TokenManager;
import com.emergency.patient.utils.QrGenerator;
import com.emergency.patient.db.AppDatabaseProvider;
import com.emergency.patient.db.EmergencyContactEntity;

import java.util.ArrayList;
import java.util.List;

/**
 * EmergencyActiveActivity — Screen 3 (UI Spec §15.4).
 *
 * Launched automatically by QuickAccessActivity after emergency dispatch.
 * Shows:
 *  - Emergency banner (dismissible but SOS stays active)
 *  - QR code card (left) + contacts card with CALL ALL (right)
 *  - Live Status Feed (RecyclerView)
 *  - Bottom action bar: CANCEL SOS + 102 POLICE
 */
public class EmergencyActiveActivity extends AppCompatActivity {

    // Intent extras from the SOS initiator
    public static final String EXTRA_LAT     = "extra_lat";
    public static final String EXTRA_LNG     = "extra_lng";
    public static final String EXTRA_CHANNEL = "extra_channel";

    // ─── Views ────────────────────────────────────────────────────────────────
    private LinearLayout bannerLayout, llContactsList;
    private TextView tvBannerText, tvChannelInfo;
    private Button btnCancelSos, btn102, btnCallAll;
    private ImageView ivQrEmergency, ivQrFullscreen;
    private View vBannerDot, qrFullscreenOverlay;
    private org.osmdroid.views.MapView mapView;
    private org.osmdroid.views.overlay.Marker ambulanceMarker;
    private org.osmdroid.views.overlay.Polyline routeLine;

    // ─── Data ─────────────────────────────────────────────────────────────────
    private double lat, lng;
    private String dispatchChannel;
    // Use the real entity to hold fetched contacts
    private final List<EmergencyContactEntity> emergencyContacts = new ArrayList<>();
    private android.graphics.Bitmap qrBitmap;

    // ─── Lifecycle ────────────────────────────────────────────────────────────

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Keep the emergency active screen visible over the lock screen
        getWindow().addFlags(
                android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
                android.view.WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD |
                android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON |
                android.view.WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
        );

        setContentView(R.layout.activity_emergency_active);

        // Read dispatch extras
        lat             = getIntent().getDoubleExtra(EXTRA_LAT, 0.0);
        lng             = getIntent().getDoubleExtra(EXTRA_LNG, 0.0);
        dispatchChannel = getIntent().getStringExtra(EXTRA_CHANNEL);

        // Safety: if location is missing, try to fetch it now
        if (lat == 0.0 && lng == 0.0) {
            com.emergency.patient.utils.LocationHelper.getLastKnownLocation(this, new com.emergency.patient.utils.LocationHelper.LocationCallback() {
                @Override
                public void onLocationResult(double fetchedLat, double fetchedLng) {
                    lat = fetchedLat;
                    lng = fetchedLng;

                    // Send ping to driver dashboard (Safety re-send if coordinates were missing)
                    com.emergency.patient.network.AmbulancePingManager.getInstance(EmergencyActiveActivity.this).sendPing(lat, lng);

                    if (mapView != null) {

                        org.osmdroid.util.GeoPoint point = new org.osmdroid.util.GeoPoint(lat, lng);
                        mapView.getController().setCenter(point);
                    }
                }
            });
        }

        bindViews();
        setupBanner();
        setupContactList();
        setupQrCode();
        setupBottomBar();
        setupAmbulanceMap();
        startPulsingAnimations();
    }

    // ─── Binding ──────────────────────────────────────────────────────────────

    private void bindViews() {
        bannerLayout   = findViewById(R.id.layout_emergency_banner);
        tvBannerText   = findViewById(R.id.tv_banner_text);
        tvChannelInfo  = findViewById(R.id.tv_channel_info);
        btnCancelSos   = findViewById(R.id.btn_cancel_sos);
        btn102         = findViewById(R.id.btn_102_police);
        llContactsList = findViewById(R.id.ll_contacts);
        btnCallAll     = findViewById(R.id.btn_call_all);
        ivQrEmergency  = findViewById(R.id.iv_qr_emergency);
        qrFullscreenOverlay = findViewById(R.id.fl_qr_fullscreen_emergency);
        ivQrFullscreen      = findViewById(R.id.iv_qr_fullscreen_emergency);
        mapView             = findViewById(R.id.map_view_emergency);
    }

    // ─── Emergency Banner ─────────────────────────────────────────────────────

    private void setupBanner() {
        tvBannerText.setText(R.string.emergency_activated);
        if (dispatchChannel != null) {
            tvChannelInfo.setText(getString(R.string.sent_via, dispatchChannel));
            tvChannelInfo.setVisibility(View.VISIBLE);
        }
    }

    private void startPulsingAnimations() {
        // Animation pulse = AnimationUtils.loadAnimation(this, R.anim.pulse_alpha);
    }


    // ─── Contact List ─────────────────────────────────────────────────────────

    private void setupContactList() {
        String uuid = TokenManager.getUUID(this);
        
        // Fetch real contacts from database on background thread
        new Thread(() -> {
            List<EmergencyContactEntity> dbContacts = AppDatabaseProvider.getInstance(this)
                    .emergencyContactDao().getContactsForPatient(uuid);
                    
            runOnUiThread(() -> {
                emergencyContacts.clear();
                if (dbContacts != null && !dbContacts.isEmpty()) {
                    emergencyContacts.addAll(dbContacts);
                }
                populateContactsList();
            });
        }).start();

        btnCallAll.setOnClickListener(v -> callAllContacts());
    }

    private void populateContactsList() {
        llContactsList.removeAllViews();
        
        if (emergencyContacts.isEmpty()) {
            TextView tvEmpty = new TextView(this);
            tvEmpty.setText("No emergency contacts saved.");
            tvEmpty.setTextColor(getResources().getColor(R.color.color_text_secondary));
            tvEmpty.setTextSize(14);
            llContactsList.addView(tvEmpty);
            return;
        }

        for (EmergencyContactEntity contact : emergencyContacts) {
            View itemView = getLayoutInflater().inflate(R.layout.item_contact_row, llContactsList, false);
            TextView tvName = itemView.findViewById(R.id.tv_contact_name);
            TextView tvTag  = itemView.findViewById(R.id.tv_relationship_tag);
            View btnCall    = itemView.findViewById(R.id.btn_call_contact);

            tvName.setText(contact.name);
            tvTag.setText("Emergency Contact"); // Using default tag since relationship isn't stored
            btnCall.setOnClickListener(v -> triggerCall(contact.phoneNumber));

            llContactsList.addView(itemView);
        }
    }

    private void triggerCall(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.isEmpty()) return;
        Intent intent = new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + phoneNumber));
        startActivity(intent);
    }

    private void callAllContacts() {
        if (emergencyContacts.isEmpty()) {
            android.widget.Toast.makeText(this, "No emergency contacts to notify.", android.widget.Toast.LENGTH_SHORT).show();
            return;
        }

        // 1. Prepare payload for Webhook
        java.util.Map<String, Object> payload = new java.util.HashMap<>();
        payload.put("patientUUID", TokenManager.getUUID(this));
        payload.put("patientName", TokenManager.getPatientName(this));
        payload.put("latitude", lat);
        payload.put("longitude", lng);
        
        java.util.List<java.util.Map<String, String>> contactsList = new java.util.ArrayList<>();
        for (EmergencyContactEntity contact : emergencyContacts) {
            java.util.Map<String, String> c = new java.util.HashMap<>();
            c.put("name", contact.name);
            c.put("phone", contact.phoneNumber);
            contactsList.add(c);
        }
        payload.put("contacts", contactsList);
        payload.put("timestamp", System.currentTimeMillis());

        // 2. Trigger Make.com Webhook
        // We use a temporary Retrofit instance to point to hook.eu2.make.com
        retrofit2.Retrofit makeRetrofit = new retrofit2.Retrofit.Builder()
                .baseUrl("https://hook.eu2.make.com/")
                .addConverterFactory(retrofit2.converter.gson.GsonConverterFactory.create())
                .build();

        ApiService makeService = makeRetrofit.create(ApiService.class);
        makeService.triggerCallAllWebhook(payload).enqueue(new retrofit2.Callback<Void>() {
            @Override
            public void onResponse(retrofit2.Call<Void> call, retrofit2.Response<Void> response) {
                runOnUiThread(() -> {
                    if (response.isSuccessful()) {
                        android.widget.Toast.makeText(EmergencyActiveActivity.this, "Dispatching alerts to all contacts...", android.widget.Toast.LENGTH_LONG).show();
                    } else {
                        android.util.Log.e("EmergencyActive", "Webhook failed: " + response.code());
                        // Fallback: dial first contact manually
                        triggerCall(emergencyContacts.get(0).phoneNumber);
                    }
                });
            }

            @Override
            public void onFailure(retrofit2.Call<Void> call, Throwable t) {
                runOnUiThread(() -> {
                    android.util.Log.e("EmergencyActive", "Webhook network error", t);
                    // Fallback: dial first contact manually
                    triggerCall(emergencyContacts.get(0).phoneNumber);
                });
            }
        });
    }

    // ─── QR Code ──────────────────────────────────────────────────────────────

    private void setupQrCode() {
        try {
            // Encode Firebase UID instead of local UUID so the Doctor App can fetch cloud data
            String qrData = TokenManager.getFirebaseUID(this);
            if (qrData == null || qrData.isEmpty()) {
                qrData = TokenManager.getUUID(this); // Fallback
            }

            if (qrData != null) {
                qrBitmap = QrGenerator.generate(qrData, 512);
                ivQrEmergency.setImageBitmap(qrBitmap);
            }

            ivQrEmergency.setOnClickListener(v -> {
                if (qrBitmap != null) {
                    ivQrFullscreen.setImageBitmap(qrBitmap);
                    qrFullscreenOverlay.setVisibility(View.VISIBLE);
                }
            });

            qrFullscreenOverlay.setOnClickListener(v -> qrFullscreenOverlay.setVisibility(View.GONE));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ─── Ambulance Map (OSMDroid) ──────────────────────────────────────────

    private void setupAmbulanceMap() {
        if (mapView == null) return;

        // SDK initialization (crucial for tiles to load)
        org.osmdroid.config.Configuration.getInstance().setUserAgentValue(getPackageName());

        mapView.setTileSource(org.osmdroid.tileprovider.tilesource.TileSourceFactory.MAPNIK);
        mapView.setMultiTouchControls(true);
        mapView.getController().setZoom(16.0);

        org.osmdroid.util.GeoPoint userPoint = new org.osmdroid.util.GeoPoint(lat, lng);
        mapView.getController().setCenter(userPoint);

        // Add Patient Marker
        org.osmdroid.views.overlay.Marker userMarker = new org.osmdroid.views.overlay.Marker(mapView);
        userMarker.setPosition(userPoint);
        userMarker.setAnchor(org.osmdroid.views.overlay.Marker.ANCHOR_CENTER, org.osmdroid.views.overlay.Marker.ANCHOR_BOTTOM);
        userMarker.setTitle("You are here");
        mapView.getOverlays().add(userMarker);

        // MOCK: Starting ambulance at a nearby offset for simulation
        double mockAmbLat = lat + 0.005;
        double mockAmbLng = lng + 0.005;
        updateAmbulanceOnMap(mockAmbLat, mockAmbLng);

        // Register for real updates
        SocketManager.getInstance(this).addAmbulanceListener(new SocketManager.AmbulanceListener() {
            @Override
            public void onLocationUpdate(double ambLat, double ambLng) {
                runOnUiThread(() -> updateAmbulanceOnMap(ambLat, ambLng));
            }
        });
        
        mapView.invalidate();
    }

    private void updateAmbulanceOnMap(double ambLat, double ambLng) {
        if (mapView == null) return;

        org.osmdroid.util.GeoPoint ambPoint = new org.osmdroid.util.GeoPoint(ambLat, ambLng);

        if (ambulanceMarker == null) {
            ambulanceMarker = new org.osmdroid.views.overlay.Marker(mapView);
            ambulanceMarker.setAnchor(org.osmdroid.views.overlay.Marker.ANCHOR_CENTER, org.osmdroid.views.overlay.Marker.ANCHOR_CENTER);
            ambulanceMarker.setTitle("Ambulance");
            // Set ambulance icon if available, else default
            mapView.getOverlays().add(ambulanceMarker);
        }

        ambulanceMarker.setPosition(ambPoint);
        
        // Draw real road route using OSRM
        fetchRouteFromOSRM(ambPoint, new org.osmdroid.util.GeoPoint(lat, lng));

        mapView.invalidate();
    }

    private void fetchRouteFromOSRM(org.osmdroid.util.GeoPoint start, org.osmdroid.util.GeoPoint end) {
        new Thread(() -> {
            try {
                String url = String.format(java.util.Locale.US,
                    "https://router.project-osrm.org/route/v1/driving/%.6f,%.6f;%.6f,%.6f?overview=full&geometries=geojson",
                    start.getLongitude(), start.getLatitude(), end.getLongitude(), end.getLatitude());

                java.net.URL requestUrl = new java.net.URL(url);
                java.net.HttpURLConnection conn = (java.net.HttpURLConnection) requestUrl.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("User-Agent", getPackageName());

                java.io.InputStream in = new java.io.BufferedInputStream(conn.getInputStream());
                java.util.Scanner s = new java.util.Scanner(in).useDelimiter("\\A");
                String result = s.hasNext() ? s.next() : "";

                org.json.JSONObject json = new org.json.JSONObject(result);
                org.json.JSONArray routes = json.getJSONArray("routes");
                if (routes.length() > 0) {
                    org.json.JSONArray coordinates = routes.getJSONObject(0)
                        .getJSONObject("geometry").getJSONArray("coordinates");

                    java.util.List<org.osmdroid.util.GeoPoint> routePoints = new java.util.ArrayList<>();
                    for (int i = 0; i < coordinates.length(); i++) {
                        org.json.JSONArray point = coordinates.getJSONArray(i);
                        routePoints.add(new org.osmdroid.util.GeoPoint(point.getDouble(1), point.getDouble(0)));
                    }

                    runOnUiThread(() -> {
                        if (mapView == null) return;
                        if (routeLine != null) mapView.getOverlays().remove(routeLine);
                        
                        routeLine = new org.osmdroid.views.overlay.Polyline();
                        routeLine.setPoints(routePoints);
                        routeLine.getOutlinePaint().setColor(android.graphics.Color.BLUE);
                        routeLine.getOutlinePaint().setStrokeWidth(10f);
                        mapView.getOverlays().add(routeLine);
                        mapView.invalidate();
                    });
                }
            } catch (Exception e) {
                android.util.Log.e("EmergencyActive", "Routing failed", e);
            }
        }).start();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mapView != null) mapView.onResume();
    }

    @Override
    public void onPause() {
        super.onPause();
        if (mapView != null) mapView.onPause();
    }

    // ─── Bottom Bar ───────────────────────────────────────────────────────────

    private void setupBottomBar() {
        // CANCEL SOS — requires confirmation
        btnCancelSos.setOnClickListener(v -> showCancelConfirmation());

        // 102 POLICE — pre-dials without auto-calling
        btn102.setOnClickListener(v -> {
            Intent dialIntent = new Intent(Intent.ACTION_DIAL, Uri.parse("tel:102"));
            startActivity(dialIntent);
        });
    }

    private void showCancelConfirmation() {
        new android.app.AlertDialog.Builder(this)
                .setTitle(R.string.cancel_emergency_title)
                .setMessage(R.string.cancel_emergency_message)
                .setPositiveButton(R.string.yes_cancel, (dialog, which) -> {
                    SocketManager.getInstance(this).emitCancelEmergency();
                    finish();
                })
                .setNegativeButton(R.string.keep_active, null)
                .show();
    }

}
