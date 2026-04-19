package com.emergency.patient.activities;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.emergency.patient.R;
import com.emergency.patient.models.PatientProfile;
import com.emergency.patient.network.FcmTokenSyncManager;
import com.emergency.patient.db.AppDatabase;
import com.emergency.patient.db.AppDatabaseProvider;
import com.emergency.patient.db.EmergencyContactEntity;
import com.emergency.patient.db.PatientEntity;
import com.emergency.patient.security.TokenManager;
import com.emergency.patient.services.EmergencyBackgroundService;
import com.emergency.patient.utils.PermissionHelper;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Step3VerifyResumeActivity — Final onboarding summary and profile creation.
 * Moved medication logic to MedicationConfirmationActivity for cleaner separation of concerns.
 */
public class Step3VerifyResumeActivity extends AppCompatActivity {

    private PatientProfile profile;
    private TextView tvName, tvGenderDob, tvBlood, tvConditions, tvVerifyContacts;
    private Button btnBack, btnVerifyCreate;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_step3_verify_resume);

        if (getIntent() != null && getIntent().hasExtra("profile_data")) {
            profile = (PatientProfile) getIntent().getSerializableExtra("profile_data");
        } else {
            profile = new PatientProfile();
        }

        bindViews();
        populateSummary();
        setupListeners();
    }

    private void bindViews() {
        tvName = findViewById(R.id.tv_verify_name);
        tvGenderDob = findViewById(R.id.tv_verify_gender_dob);
        tvBlood = findViewById(R.id.tv_verify_blood);
        tvConditions = findViewById(R.id.tv_verify_conditions);
        tvVerifyContacts = findViewById(R.id.tv_verify_contacts);

        btnBack = findViewById(R.id.btn_back_step3);
        btnVerifyCreate = findViewById(R.id.btn_verify_create);
        
        // Hide OCR-specific UI if it exists in the layout (legacy compatibility)
        android.view.View ocrLayout = findViewById(R.id.layout_ocr_details);
        if (ocrLayout != null) ocrLayout.setVisibility(android.view.View.GONE);
    }

    private void populateSummary() {
        tvName.setText(profile.getFullName());

        String dobString = "";
        if (profile.getDobMillis() > 0) {
            SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());
            dobString = sdf.format(new Date(profile.getDobMillis()));
        }

        tvGenderDob.setText(profile.getGender() + " • " + dobString);
        tvBlood.setText(profile.getBloodGroup());

        List<String> conditions = profile.getActiveConditionsList();
        if (conditions == null || conditions.isEmpty()) {
            tvConditions.setText("No active conditions reported.");
        } else {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < conditions.size(); i++) {
                sb.append("• ").append(conditions.get(i));
                if (i < conditions.size() - 1) sb.append("\n");
            }
            tvConditions.setText(sb.toString());
        }

        StringBuilder contactsSb = new StringBuilder();
        if (profile.getEmergencyContacts().isEmpty()) {
            contactsSb.append("No contacts added.");
        } else {
            for (PatientProfile.EmergencyContact contact : profile.getEmergencyContacts()) {
                contactsSb.append(contact.name).append(" (").append(contact.phoneNumber).append(")\n");
            }
        }
        tvVerifyContacts.setText(contactsSb.toString());
    }

    private void setupListeners() {
        btnBack.setOnClickListener(v -> finish());
        btnVerifyCreate.setOnClickListener(v -> {
            btnVerifyCreate.setEnabled(false);
            btnVerifyCreate.setText("Finalizing...");
            new Handler(Looper.getMainLooper()).postDelayed(this::completeRegistration, 1000);
        });
    }

    private void completeRegistration() {
        String mockPatientUuid = UUID.randomUUID().toString();
        TokenManager.saveUUID(this, mockPatientUuid);

        // Handle Photo
        String persistentPhotoUri = null;
        if (profile.getProfilePhotoUri() != null) {
            persistentPhotoUri = savePhotoToInternalStorage(profile.getProfilePhotoUri());
            profile.setProfilePhotoUri(persistentPhotoUri);
        }

        // Save to Room
        AppDatabase db = AppDatabaseProvider.getInstance(this);
        PatientEntity patient = new PatientEntity(mockPatientUuid);
        patient.fullName = profile.getFullName();
        patient.dobMillis = profile.getDobMillis();
        patient.gender = profile.getGender();
        patient.bloodGroup = profile.getBloodGroup();
        patient.profilePhotoUri = profile.getProfilePhotoUri();
        patient.isOnboardingComplete = true;

        patient.pastMedicalDiagnosis = profile.getPastMedicalDiagnosis();
        patient.pharmacologicalStatus = profile.getPharmacologicalStatus();
        patient.clinicalAllergies = profile.getClinicalAllergies();
        patient.hereditaryConditions = profile.getHereditaryConditions();
        patient.lifestyleFactor = profile.getLifestyleFactor();

        db.patientDao().deleteAllPatients();
        db.patientDao().insertPatient(patient);

        if (!profile.getEmergencyContacts().isEmpty()) {
            List<EmergencyContactEntity> contactEntities = new java.util.ArrayList<>();
            for (PatientProfile.EmergencyContact c : profile.getEmergencyContacts()) {
                contactEntities.add(new EmergencyContactEntity(mockPatientUuid, c.name, c.phoneNumber));
            }
            db.emergencyContactDao().insertAll(contactEntities);
        }

        TokenManager.savePatientName(this, profile.getFullName());
        TokenManager.setOnboardingComplete(this, true);
        EmergencyBackgroundService.start(this);

        Toast.makeText(this, "Profile creation complete!", Toast.LENGTH_SHORT).show();

        Intent intent = new Intent(this, DashboardActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private String savePhotoToInternalStorage(String photoUriString) {
        try {
            Uri sourceUri = Uri.parse(photoUriString);
            InputStream inputStream = getContentResolver().openInputStream(sourceUri);
            if (inputStream == null) return null;

            File directory = getFilesDir();
            File destFile = new File(directory, "profile_photo_" + System.currentTimeMillis() + ".jpg");

            OutputStream outputStream = new FileOutputStream(destFile);
            byte[] buffer = new byte[4096];
            int length;
            while ((length = inputStream.read(buffer)) > 0) {
                outputStream.write(buffer, 0, length);
            }
            outputStream.flush();
            outputStream.close();
            inputStream.close();

            return Uri.fromFile(destFile).toString();
        } catch (Exception e) {
            return photoUriString;
        }
    }
}
