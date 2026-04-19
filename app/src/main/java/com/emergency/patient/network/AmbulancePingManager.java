package com.emergency.patient.network;

import android.content.Context;
import android.util.Log;

import com.emergency.patient.security.TokenManager;

import java.util.HashMap;
import java.util.Map;

import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

/**
 * AmbulancePingManager — Singleton manager for sending HTTP pings to the driver dashboard.
 */
public class AmbulancePingManager {

    private static final String TAG = "DEBUG_PING";
    private static final String WEBHOOK_URL = "https://hook.eu2.make.com/qc7vltpprummrcvn69ybvibfvyet4xvw";
    
    private static AmbulancePingManager instance;
    private final Context context;
    private final ApiService apiService;

    private AmbulancePingManager(Context context) {
        Log.wtf(TAG, "!!! CRITICAL: AmbulancePingManager INITIALIZING !!!");
        this.context = context.getApplicationContext();
        
        // We use a basic Retrofit for the full-URL webhook
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl("https://hook.eu2.make.com/") // Base URL is required but overridden by full @POST URL
                .addConverterFactory(GsonConverterFactory.create())
                .build();
        this.apiService = retrofit.create(ApiService.class);
    }

    public static synchronized AmbulancePingManager getInstance(Context context) {
        if (instance == null) {
            instance = new AmbulancePingManager(context);
        }
        return instance;
    }

    /**
     * Sends a webhook trigger for a new ambulance request.
     * 
     * @param lat Patient's current latitude
     * @param lng Patient's current longitude
     */
    public void sendPing(double lat, double lng) {
        Log.wtf(TAG, "!!! CRITICAL: sendPing() CALLED with lat=" + lat + ", lng=" + lng + " !!!");
        
        // Visual feedback on screen
        new Handler(Looper.getMainLooper()).post(() -> 
            Toast.makeText(context, "🚀 TRIGGERING CLOUD WEBHOOK...", Toast.LENGTH_LONG).show()
        );

        Map<String, Object> payload = new HashMap<>();
        payload.put("type", "NEW_REQUEST");
        payload.put("latitude", lat);
        payload.put("longitude", lng);
        payload.put("patientName", TokenManager.getPatientName(context));
        payload.put("patientUUID", TokenManager.getUUID(context));
        payload.put("timestamp", System.currentTimeMillis());

        Log.i(TAG, "🚀 Triggering cloud webhook: " + WEBHOOK_URL);
        
        apiService.triggerAmbulanceBookingWebhook(payload).enqueue(new Callback<Void>() {
            @Override
            public void onResponse(Call<Void> call, Response<Void> response) {
                if (response.isSuccessful()) {
                    Log.wtf(TAG, "Webhook SUCCESS ✅");
                    new Handler(Looper.getMainLooper()).post(() -> 
                        Toast.makeText(context, "✅ WEBHOOK TRIGGERED SUCCESSFULLY", Toast.LENGTH_SHORT).show()
                    );
                } else {
                    Log.wtf(TAG, "Webhook FAILED ❌ (Code: " + response.code() + ")");
                    new Handler(Looper.getMainLooper()).post(() -> 
                        Toast.makeText(context, "❌ WEBHOOK FAILED (" + response.code() + ")", Toast.LENGTH_SHORT).show()
                    );
                }
            }

            @Override
            public void onFailure(Call<Void> call, Throwable t) {
                Log.wtf(TAG, "Webhook NETWORK ERROR ❌", t);
                new Handler(Looper.getMainLooper()).post(() -> 
                    Toast.makeText(context, "⚠️ WEBHOOK NETWORK ERROR", Toast.LENGTH_SHORT).show()
                );
            }
        });
    }
}


