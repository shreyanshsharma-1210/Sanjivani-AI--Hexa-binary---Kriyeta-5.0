package com.emergency.patient.network;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;
import android.util.Log;

import com.emergency.patient.security.TokenManager;
import com.google.firebase.messaging.FirebaseMessaging;

import java.util.HashMap;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * FcmTokenSyncManager centralizes fetch + upload of the current FCM token.
 */
public final class FcmTokenSyncManager {

    private static final String TAG = "FcmTokenSync";
    private static final String PREFS_NAME = "fcm_sync_prefs";
    private static final String KEY_LAST_TOKEN = "last_uploaded_token";
    private static final String KEY_LAST_UUID = "last_uploaded_uuid";

    private FcmTokenSyncManager() {
        // No instances.
    }

    /**
     * Fetches the current token from Firebase and uploads it to backend when
     * needed.
     */
    public static void syncCurrentToken(Context context) {
        Context appContext = context.getApplicationContext();
        FirebaseMessaging.getInstance().getToken().addOnCompleteListener(task -> {
            if (!task.isSuccessful()) {
                Log.w(TAG, "Unable to fetch FCM token", task.getException());
                return;
            }
            uploadTokenToBackend(appContext, task.getResult());
        });
    }

    /**
     * Uploads a provided token (for example from
     * FirebaseMessagingService#onNewToken).
     */
    public static void uploadTokenToBackend(Context context, String token) {
        if (TextUtils.isEmpty(token)) {
            Log.d(TAG, "Skipping FCM sync: token is empty");
            return;
        }

        Context appContext = context.getApplicationContext();
        String patientUuid = TokenManager.getUUID(appContext);
        if (TextUtils.isEmpty(patientUuid)) {
            Log.d(TAG, "Skipping FCM sync: patient UUID not ready yet");
            return;
        }

        SharedPreferences prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String lastToken = prefs.getString(KEY_LAST_TOKEN, null);
        String lastUuid = prefs.getString(KEY_LAST_UUID, null);
        if (token.equals(lastToken) && patientUuid.equals(lastUuid)) {
            Log.d(TAG, "Skipping FCM sync: token already uploaded for this user");
            return;
        }

        ApiService api = ApiClient.getInstance(appContext).create(ApiService.class);
        Map<String, String> body = new HashMap<>();
        body.put("fcmToken", token);
        body.put("patientUUID", patientUuid);

        api.uploadFcmToken(body).enqueue(new Callback<Void>() {
            @Override
            public void onResponse(Call<Void> call, Response<Void> response) {
                if (response.isSuccessful()) {
                    prefs.edit()
                            .putString(KEY_LAST_TOKEN, token)
                            .putString(KEY_LAST_UUID, patientUuid)
                            .apply();
                    Log.d(TAG, "FCM token uploaded successfully");
                } else {
                    Log.w(TAG, "FCM token upload failed: " + response.code());
                }
            }

            @Override
            public void onFailure(Call<Void> call, Throwable t) {
                Log.e(TAG, "FCM token upload failed", t);
            }
        });
    }
}