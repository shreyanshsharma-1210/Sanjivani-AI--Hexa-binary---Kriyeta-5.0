package com.emergency.patient.network;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.telephony.SmsManager;
import android.util.Log;

/**
 * SmsFallbackManager — Sends an SOS SMS to the Twilio backend number
 * in the pipe-delimited format: SOS|UUID|LAT|LNG
 *
 * This is triggered automatically by SocketManager when WebSocket ACK
 * is not received within 3 seconds.
 *
 * Replace TWILIO_NUMBER with your actual Twilio backend phone number.
 */
public class SmsFallbackManager {

    private static final String TAG            = "SmsFallbackManager";
    public static final  String TWILIO_NUMBER  = "+15005550006"; // Replace with production Twilio number
    private static final String ACTION_SMS_SENT      = "SMS_SENT_EMERGENCY";
    private static final String ACTION_SMS_DELIVERED = "SMS_DELIVERED_EMERGENCY";

    /** Callback to inform the UI which result occurred. */
    public interface SmsCallback {
        void onSent(boolean success);
        void onDelivered(boolean success);
    }

    // ─── Send ─────────────────────────────────────────────────────────────────

    /**
     * Sends the SOS SMS and registers delivery callbacks.
     *
     * @param context  Application context (for SmsManager + BroadcastReceiver)
     * @param uuid     Patient UUID stored in TokenManager
     * @param lat      Latitude from GPS
     * @param lng      Longitude from GPS
     * @param callback Optional — notified on sent/delivered result. May be null.
     */
    public static void send(Context context, String uuid, double lat, double lng,
                            SmsCallback callback) {
        // Format: SOS|UUID|LAT|LNG  (pipe-delimited for backend parsing)
        String message = String.format("SOS|%s|%.6f|%.6f", uuid, lat, lng);
        Log.d(TAG, "Sending SMS fallback: " + message);

        // ─── Sent callback ────────────────────────────────────────────────────
        PendingIntent sentIntent = buildPendingBroadcast(context, ACTION_SMS_SENT);
        registerReceiver(context, ACTION_SMS_SENT, android.app.Activity.RESULT_OK, success -> {
            Log.d(TAG, "SMS sent result: " + success);
            if (callback != null) callback.onSent(success);
        });

        // ─── Delivered callback ───────────────────────────────────────────────
        PendingIntent deliveredIntent = buildPendingBroadcast(context, ACTION_SMS_DELIVERED);
        registerReceiver(context, ACTION_SMS_DELIVERED, android.app.Activity.RESULT_OK, success -> {
            Log.d(TAG, "SMS delivered result: " + success);
            if (callback != null) callback.onDelivered(success);
        });

        // ─── Send ─────────────────────────────────────────────────────────────
        try {
            SmsManager smsManager;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                smsManager = context.getSystemService(SmsManager.class);
            } else {
                //noinspection deprecation
                smsManager = SmsManager.getDefault();
            }
            if (smsManager == null) {
                Log.e(TAG, "SmsManager unavailable");
                if (callback != null) callback.onSent(false);
                return;
            }
            smsManager.sendTextMessage(
                    TWILIO_NUMBER,
                    null,
                    message,
                    sentIntent,
                    deliveredIntent
            );
        } catch (Exception e) {
            Log.e(TAG, "SMS dispatch failed", e);
            if (callback != null) callback.onSent(false);
        }
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private static PendingIntent buildPendingBroadcast(Context context, String action) {
        Intent intent = new Intent(action);
        int flags = PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_ONE_SHOT;
        return PendingIntent.getBroadcast(context, 0, intent, flags);
    }

    /** Registers a one-shot BroadcastReceiver for delivery confirmation. */
    private interface ResultListener { void onResult(boolean success); }

    private static void registerReceiver(Context context, String action,
                                         int successCode, ResultListener listener) {
        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context ctx, Intent intent) {
                listener.onResult(getResultCode() == successCode);
                ctx.unregisterReceiver(this);
            }
        };
        IntentFilter filter = new IntentFilter(action);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            context.registerReceiver(receiver, filter);
        }
    }
}
