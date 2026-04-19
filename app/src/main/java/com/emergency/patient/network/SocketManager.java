package com.emergency.patient.network;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.emergency.patient.security.TokenManager;

import org.json.JSONObject;

import java.util.Collections;

import io.socket.client.IO;
import io.socket.client.Socket;

/**
 * SocketManager — Singleton WebSocket manager (Socket.IO).
 *
 * Emits the "emergency_request" event with a 3-second ACK timeout.
 * If no ACK is received, DispatchCallback.onFallbackRequired() is called
 * so the caller can trigger SmsFallbackManager.
 */
public class SocketManager {

    private static final String TAG        = "SocketManager";
    private static final String SERVER_URL = "https://your-backend.com"; // TODO: set production URL
    private static final long   ACK_TIMEOUT_MS = 3_000;

    // ─── DispatchCallback ─────────────────────────────────────────────────────

    /** Callback interface for activities to receive dispatch results. */
    public interface DispatchCallback {
        /** Called when WebSocket ACK was received within 3 seconds. */
        void onSuccess(String channel);

        /** Called when no ACK was received and SMS fallback should be triggered. */
        void onFallbackRequired(double lat, double lng, String uuid);
    }

    // ─── Singleton ────────────────────────────────────────────────────────────

    private static SocketManager instance;
    private Socket socket;
    private final Context context;

    private SocketManager(Context context) {
        this.context = context.getApplicationContext();
    }

    public static synchronized SocketManager getInstance(Context context) {
        if (instance == null) {
            instance = new SocketManager(context);
        }
        return instance;
    }

    // ─── Connection ───────────────────────────────────────────────────────────

    public void connect() {
        if (socket != null && socket.connected()) return;
        try {
            IO.Options options = new IO.Options();
            options.auth = Collections.singletonMap("token", TokenManager.getJWT(context));
            socket = IO.socket(SERVER_URL, options);

            socket.on(Socket.EVENT_CONNECT,       args -> Log.d(TAG, "Socket connected"));
            socket.on(Socket.EVENT_CONNECT_ERROR, args -> Log.w(TAG, "Socket connect error"));
            socket.on(Socket.EVENT_DISCONNECT,    args -> Log.d(TAG, "Socket disconnected"));

            setupAmbulanceUpdateListener();
            socket.connect();
        } catch (Exception e) {
            Log.e(TAG, "Socket init failed", e);
        }
    }

    public void disconnect() {
        if (socket != null) {
            socket.disconnect();
            socket.off();
        }
    }

    // ─── Emergency Emit ───────────────────────────────────────────────────────

    /**
     * Emits "emergency_request" via WebSocket.
     * Starts a 3-second ACK timer; calls onFallbackRequired() if it expires.
     *
     * @param lat      GPS latitude
     * @param lng      GPS longitude
     * @param callback Notified on ACK or timeout
     */
    public void emitEmergency(double lat, double lng, DispatchCallback callback) {
        String uuid = TokenManager.getUUID(context);
        String jwt  = TokenManager.getJWT(context);

        if (socket == null || !socket.connected()) {
            Log.w(TAG, "Socket not connected — going directly to SMS fallback");
            if (callback != null) callback.onFallbackRequired(lat, lng, uuid);
            return;
        }

        try {
            JSONObject payload = new JSONObject();
            payload.put("patientUUID", uuid);
            payload.put("jwt",         jwt);
            payload.put("latitude",    lat);
            payload.put("longitude",   lng);
            payload.put("timestamp",   System.currentTimeMillis());

            Handler mainHandler = new Handler(Looper.getMainLooper());
            final boolean[] ackReceived = {false};

            // ── Fallback timer (3 seconds) ─────────────────────────────────
            Runnable fallbackRunnable = () -> {
                if (!ackReceived[0]) {
                    Log.w(TAG, "No ACK in 3s — triggering SMS fallback");
                    if (callback != null) callback.onFallbackRequired(lat, lng, uuid);
                }
            };
            mainHandler.postDelayed(fallbackRunnable, ACK_TIMEOUT_MS);

            // ── Emit with ACK ─────────────────────────────────────────────
            socket.emit("emergency_request", payload, new io.socket.client.Ack() {
                @Override
                public void call(Object... args) {
                    mainHandler.post(() -> {
                        ackReceived[0] = true;
                        mainHandler.removeCallbacks(fallbackRunnable);
                        Log.d(TAG, "Emergency ACK received ✅");
                        if (callback != null) callback.onSuccess("WebSocket");
                    });
                }
            });

        } catch (Exception e) {
            Log.e(TAG, "emitEmergency failed", e);
            String uuid2 = TokenManager.getUUID(context);
            if (callback != null) callback.onFallbackRequired(lat, lng, uuid2);
        }
    }

    // ─── Ambulance Tracking ────────────────────────────────────────────────────

    public interface AmbulanceListener {
        void onLocationUpdate(double lat, double lng);
    }

    private final java.util.List<AmbulanceListener> ambulanceListeners = new java.util.concurrent.CopyOnWriteArrayList<>();

    public void addAmbulanceListener(AmbulanceListener listener) {
        if (listener != null && !ambulanceListeners.contains(listener)) {
            ambulanceListeners.add(listener);
        }
    }

    public void removeAmbulanceListener(AmbulanceListener listener) {
        ambulanceListeners.remove(listener);
    }

    private void setupAmbulanceUpdateListener() {
        if (socket == null) return;
        
        socket.on("ambulance_location_update", args -> {
            if (args.length > 0 && args[0] instanceof JSONObject) {
                try {
                    JSONObject data = (JSONObject) args[0];
                    double lat = data.optDouble("latitude", 0.0);
                    double lng = data.optDouble("longitude", 0.0);
                    
                    for (AmbulanceListener l : ambulanceListeners) {
                        l.onLocationUpdate(lat, lng);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Failed to parse ambulance update", e);
                }
            }
        });
    }

    // ─── Cancel ───────────────────────────────────────────────────────────────

    public void emitCancelEmergency() {
        if (socket != null && socket.connected()) {
            socket.emit("cancel_emergency");
        }
    }
}
