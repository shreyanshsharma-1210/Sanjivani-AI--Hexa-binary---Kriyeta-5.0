package com.emergency.patient.security;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;

import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

import org.json.JSONObject;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.HashSet;
import java.util.Set;

/**
 * TokenManager — Secure credential storage using EncryptedSharedPreferences.
 * Stores JWT and Patient UUID; never use plain SharedPreferences for these values.
 */
public class TokenManager {

    private static final String PREFS_FILE = "patient_secure_prefs";
    private static final String KEY_JWT = "jwt";
    private static final String KEY_UUID = "patient_uuid";
    private static final String KEY_FIREBASE_UID = "firebase_uid";
    private static final String KEY_PATIENT_NAME = "patient_name";
    private static final String KEY_PATIENT_DOB = "patient_dob";
    private static final String KEY_PATIENT_CONDITIONS = "patient_conditions";
    private static final String KEY_ONBOARDING_COMPLETE = "onboarding_complete";

    private static SharedPreferences encryptedPrefs;

    private static SharedPreferences getPrefs(Context context) {
        if (encryptedPrefs == null) {
            try {
                MasterKey masterKey = new MasterKey.Builder(context.getApplicationContext())
                        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                        .build();

                encryptedPrefs = EncryptedSharedPreferences.create(
                        context.getApplicationContext(),
                        PREFS_FILE,
                        masterKey,
                        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                );
            } catch (GeneralSecurityException | IOException e) {
                throw new RuntimeException("Failed to initialize EncryptedSharedPreferences", e);
            }
        }
        return encryptedPrefs;
    }

    // ─── JWT ────────────────────────────────────────────────────────────────

    public static void saveJWT(Context context, String token) {
        getPrefs(context).edit().putString(KEY_JWT, token).apply();
    }

    public static String getJWT(Context context) {
        return getPrefs(context).getString(KEY_JWT, null);
    }

    /**
     * Returns true if the stored JWT is still valid (not expired or within 5 minutes of expiry).
     */
    public static boolean isJWTValid(Context context) {
        String jwt = getJWT(context);
        if (jwt == null) return false;
        try {
            String[] parts = jwt.split("\\.");
            if (parts.length < 2) return false;
            byte[] payload = Base64.decode(parts[1], Base64.URL_SAFE | Base64.NO_PADDING);
            JSONObject json = new JSONObject(new String(payload));
            long exp = json.getLong("exp");
            long nowSeconds = System.currentTimeMillis() / 1000L;
            // Consider expired if within 5 minutes of expiry
            return (exp - nowSeconds) > 300;
        } catch (Exception e) {
            return false;
        }
    }

    // ─── UUID ────────────────────────────────────────────────────────────────

    public static void saveUUID(Context context, String uuid) {
        getPrefs(context).edit().putString(KEY_UUID, uuid).apply();
    }

    public static String getUUID(Context context) {
        return getPrefs(context).getString(KEY_UUID, null);
    }

    // ─── Firebase UID ────────────────────────────────────────────────────────

    public static void saveFirebaseUID(Context context, String uid) {
        getPrefs(context).edit().putString(KEY_FIREBASE_UID, uid).apply();
    }

    public static String getFirebaseUID(Context context) {
        return getPrefs(context).getString(KEY_FIREBASE_UID, null);
    }

    // ─── Patient Profile ─────────────────────────────────────────────────────

    public static void savePatientName(Context context, String name) {
        getPrefs(context).edit().putString(KEY_PATIENT_NAME, name).apply();
    }

    public static String getPatientName(Context context) {
        return getPrefs(context).getString(KEY_PATIENT_NAME, "");
    }

    /** Returns the patient's first name only, for the lock screen display. */
    public static String getPatientFirstName(Context context) {
        String fullName = getPatientName(context);
        if (fullName.isEmpty()) return "";
        return fullName.split("\\s+")[0];
    }

    public static void saveDOB(Context context, long dobMillis) {
        getPrefs(context).edit().putLong(KEY_PATIENT_DOB, dobMillis).apply();
    }

    public static long getDOB(Context context) {
        return getPrefs(context).getLong(KEY_PATIENT_DOB, 0);
    }

    public static void saveConditions(Context context, Set<String> conditions) {
        getPrefs(context).edit().putStringSet(KEY_PATIENT_CONDITIONS, conditions).apply();
    }

    public static Set<String> getConditions(Context context) {
        return getPrefs(context).getStringSet(KEY_PATIENT_CONDITIONS, new HashSet<>());
    }

    // ─── Onboarding State ────────────────────────────────────────────────────

    public static void setOnboardingComplete(Context context, boolean complete) {
        getPrefs(context).edit().putBoolean(KEY_ONBOARDING_COMPLETE, complete).apply();
    }

    public static boolean isOnboardingComplete(Context context) {
        return getPrefs(context).getBoolean(KEY_ONBOARDING_COMPLETE, false);
    }

    public static void saveGender(Context context, String gender) {
        getPrefs(context).edit().putString("gender", gender).apply();
    }

    public static String getGender(Context context) {
        return getPrefs(context).getString("gender", "");
    }

    public static void saveBloodGroup(Context context, String bloodGroup) {
        getPrefs(context).edit().putString("blood_group", bloodGroup).apply();
    }

    public static String getBloodGroup(Context context) {
        return getPrefs(context).getString("blood_group", "");
    }

    public static void saveEmergencyContacts(Context context, String contactsJson) {
        getPrefs(context).edit().putString("emergency_contacts", contactsJson).apply();
    }

    public static String getEmergencyContacts(Context context) {
        return getPrefs(context).getString("emergency_contacts", "[]");
    }

    // ─── Clear ───────────────────────────────────────────────────────────────

    /** Clears all stored credentials; call on logout. */
    public static void clearAll(Context context) {
        getPrefs(context).edit().clear().apply();
    }
}
