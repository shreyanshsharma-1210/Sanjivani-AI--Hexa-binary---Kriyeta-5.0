package com.emergency.patient.network;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.NonNull;

import com.emergency.patient.db.AppDatabase;
import com.emergency.patient.db.AppDatabaseProvider;
import com.emergency.patient.db.PatientEntity;
import com.emergency.patient.security.TokenManager;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.cloudinary.android.MediaManager;
import com.cloudinary.android.callback.ErrorInfo;
import com.cloudinary.android.callback.UploadCallback;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * FirebaseRepository — Centralizes all Cloud Firebase operations (Firestore, Storage, Auth).
 * 
 * NOTE: Emergency Contacts are intentionally left out of this repository 
 * to adhere to the local-only privacy constraint.
 */
public class FirebaseRepository {

    private static final String TAG = "FirebaseRepository";
    private static FirebaseRepository instance;

    private final FirebaseAuth auth;
    private final FirebaseFirestore db;

    // TODO: USER MUST CONFIGURE THESE IN THE FIREBASE CONSOLE / CLOUDINARY DASHBOARD
    private static final String CLOUDINARY_CLOUD_NAME = "dthqisgz9";
    private static final String CLOUDINARY_UPLOAD_PRESET = "Emergency";

    private FirebaseRepository(Context context) {
        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        
        // Initialize Cloudinary
        Map<String, String> config = new HashMap<>();
        config.put("cloud_name", CLOUDINARY_CLOUD_NAME);
        try {
            MediaManager.init(context, config);
        } catch (IllegalStateException e) {
            // Already initialized
        }
    }

    public static synchronized FirebaseRepository getInstance(Context context) {
        if (instance == null) {
            instance = new FirebaseRepository(context.getApplicationContext());
        }
        return instance;
    }

    // ─── Authentication ───────────────────────────────────────────────────────

    public FirebaseUser getCurrentUser() {
        return auth.getCurrentUser();
    }

    public void signOut() {
        auth.signOut();
    }

    // ─── Profile Sync (Room <-> Firestore) ───────────────────────────────────

    /**
     * Uploads the local Room PatientEntity to Firestore.
     */
    public void uploadProfile(Context context, @NonNull PatientEntity patient, final SyncCallback callback) {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            if (callback != null) callback.onFailure(new Exception("User not authenticated"));
            return;
        }

        Map<String, Object> profile = new HashMap<>();
        profile.put("fullName", patient.fullName);
        profile.put("dobMillis", patient.dobMillis);
        profile.put("gender", patient.gender);
        profile.put("bloodGroup", patient.bloodGroup);
        profile.put("pastMedicalDiagnosis", patient.pastMedicalDiagnosis);
        profile.put("pharmacologicalStatus", patient.pharmacologicalStatus);
        profile.put("clinicalAllergies", patient.clinicalAllergies);
        profile.put("hereditaryConditions", patient.hereditaryConditions);
        profile.put("lifestyleFactor", patient.lifestyleFactor);
        profile.put("isOnboardingComplete", patient.isOnboardingComplete);
        profile.put("updatedAt", System.currentTimeMillis());

        db.collection("patients").document(user.getUid())
                .set(profile)
                .addOnSuccessListener(aVoid -> {
                    if (callback != null) callback.onSuccess();
                })
                .addOnFailureListener(e -> {
                    if (callback != null) callback.onFailure(e);
                });
    }

    /**
     * Downloads the profile from Firestore and updates the local Room database.
     */
    public void downloadProfile(Context context, final SyncCallback callback) {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            if (callback != null) callback.onFailure(new Exception("User not authenticated"));
            return;
        }

        db.collection("patients").document(user.getUid())
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        // Update Room DB on background thread
                        new Thread(() -> {
                            AppDatabase roomDb = AppDatabaseProvider.getInstance(context);
                            String uuid = TokenManager.getUUID(context); // Legacy sync
                            PatientEntity patient = roomDb.patientDao().getPatient(uuid);
                            
                            if (patient == null) {
                                patient = new PatientEntity(uuid);
                            }

                            patient.fullName = documentSnapshot.getString("fullName");
                            patient.dobMillis = documentSnapshot.getLong("dobMillis") != null ? documentSnapshot.getLong("dobMillis") : 0;
                            patient.gender = documentSnapshot.getString("gender");
                            patient.bloodGroup = documentSnapshot.getString("bloodGroup");
                            patient.pastMedicalDiagnosis = documentSnapshot.getString("pastMedicalDiagnosis");
                            patient.pharmacologicalStatus = documentSnapshot.getString("pharmacologicalStatus");
                            patient.clinicalAllergies = documentSnapshot.getString("clinicalAllergies");
                            patient.hereditaryConditions = documentSnapshot.getString("hereditaryConditions");
                            patient.lifestyleFactor = documentSnapshot.getString("lifestyleFactor");
                            patient.isOnboardingComplete = documentSnapshot.getBoolean("isOnboardingComplete") != null && documentSnapshot.getBoolean("isOnboardingComplete");

                            roomDb.patientDao().insertPatient(patient);
                            
                            if (callback != null) callback.onSuccess();
                        }).start();
                    } else {
                        if (callback != null) callback.onFailure(new Exception("Profile not found in cloud"));
                    }
                })
                .addOnFailureListener(e -> {
                    if (callback != null) callback.onFailure(e);
                });
    }

    // ─── Document Storage (Room <-> Firebase Storage) ───────────────────────

    /**
     * Uploads a local file to Cloudinary and returns the URL.
     */
    public void uploadDocument(String localPath, String fileName, final DocumentUploadCallback callback) {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            if (callback != null) callback.onFailure(new Exception("User not authenticated"));
            return;
        }

        if (CLOUDINARY_CLOUD_NAME.equals("YOUR_CLOUD_NAME")) {
            if (callback != null) callback.onFailure(new Exception("Cloudinary not configured. Please set cloud_name in FirebaseRepository.java"));
            return;
        }

        MediaManager.get().upload(localPath)
                .unsigned(CLOUDINARY_UPLOAD_PRESET)
                .option("public_id", "patients/" + user.getUid() + "/" + fileName)
                .callback(new UploadCallback() {
                    @Override
                    public void onStart(String requestId) {
                        Log.d(TAG, "Cloudinary upload started: " + requestId);
                    }

                    @Override
                    public void onProgress(String requestId, long bytes, long totalBytes) {
                    }

                    @Override
                    public void onSuccess(String requestId, Map resultData) {
                        String downloadUrl = (String) resultData.get("secure_url");
                        String publicId = (String) resultData.get("public_id");
                        Log.d(TAG, "Cloudinary upload success: " + downloadUrl + " (public_id: " + publicId + ")");
                        registerDocumentInFirestore(user.getUid(), fileName, downloadUrl, publicId, callback);
                    }

                    @Override
                    public void onError(String requestId, ErrorInfo error) {
                        Log.e(TAG, "Cloudinary upload error: " + error.getDescription());
                        if (callback != null) callback.onFailure(new Exception(error.getDescription()));
                    }

                    @Override
                    public void onReschedule(String requestId, ErrorInfo error) {
                    }
                }).dispatch();
    }

    public void deleteCloudProfile(final SyncCallback callback) {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            if (callback != null) callback.onFailure(new Exception("User not authenticated"));
            return;
        }

        String uid = user.getUid();

        // 1. Delete all documents in the 'documents' sub-collection
        db.collection("patients").document(uid).collection("documents")
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    for (com.google.firebase.firestore.DocumentSnapshot doc : queryDocumentSnapshots.getDocuments()) {
                        doc.getReference().delete();
                    }

                    // 2. Delete the main patient document
                    db.collection("patients").document(uid)
                            .delete()
                            .addOnSuccessListener(aVoid -> {
                                if (callback != null) callback.onSuccess();
                            })
                            .addOnFailureListener(e -> {
                                if (callback != null) callback.onFailure(e);
                            });
                })
                .addOnFailureListener(e -> {
                    if (callback != null) callback.onFailure(e);
                });
    }

    private void registerDocumentInFirestore(String uid, String fileName, String downloadUrl, String publicId, final DocumentUploadCallback callback) {
        Map<String, Object> docData = new HashMap<>();
        docData.put("fileName", fileName);
        docData.put("downloadUrl", downloadUrl);
        docData.put("publicId", publicId); // Store for future deletion
        docData.put("timestamp", System.currentTimeMillis());

        db.collection("patients").document(uid)
                .collection("documents").add(docData)
                .addOnSuccessListener(documentReference -> {
                    if (callback != null) callback.onSuccess(downloadUrl, publicId, documentReference.getId());
                })
                .addOnFailureListener(e -> {
                    // Even if metadata sync fails, Storage upload was successful
                    if (callback != null) callback.onSuccess(downloadUrl, publicId, null); 
                });
    }

    public void deleteDocumentFromCloud(String publicId, String firestoreId, final SyncCallback callback) {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            if (callback != null) callback.onFailure(new Exception("User not authenticated"));
            return;
        }

        // 1. Delete from Firestore if we have ID
        if (firestoreId != null && !firestoreId.isEmpty()) {
            db.collection("patients").document(user.getUid())
                    .collection("documents").document(firestoreId)
                    .delete()
                    .addOnFailureListener(e -> Log.e(TAG, "Failed to delete Firestore record: " + e.getMessage()));
        }

        // 2. Delete from Cloudinary
        // Note: For public Cloudinary setups, simple deletion without secret isn't supported via SDK.
        // We trigger the callback immediately after attempting firestore cleanup.
        if (callback != null) callback.onSuccess();
    }

    // ─── Interfaces ──────────────────────────────────────────────────────────

    public interface SyncCallback {
        void onSuccess();
        void onFailure(Exception e);
    }

    public interface DocumentUploadCallback {
        void onSuccess(String cloudUrl, String publicId, String firestoreId);
        void onFailure(Exception e);
    }
}
