package com.emergency.patient.activities;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import com.emergency.patient.db.AppDatabase;
import com.emergency.patient.db.AppDatabaseProvider;
import com.emergency.patient.db.PatientEntity;
import com.emergency.patient.security.TokenManager;



import com.emergency.patient.R;
import com.emergency.patient.models.PatientProfile;

import java.util.Calendar;

public class Step1BasicInfoActivity extends AppCompatActivity {

    private ImageView ivProfilePhoto;
    private Button btnUploadPhoto;
    private EditText etFullName;
    private TextView tvDob;
    private Spinner spinnerGender;
    private Spinner spinnerBloodGroup;
    private Button btnNext;

    private Uri selectedPhotoUri = null;
    private long selectedDobMillis = 0;

    private final ActivityResultLauncher<String> photoPickerLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) {
                    selectedPhotoUri = uri;
                    ivProfilePhoto.setImageURI(uri);
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Check if onboarding is already complete (Check Room first, fallback to TokenManager)
        PatientEntity patient = AppDatabaseProvider.getInstance(this).patientDao().getFirstPatient();
        if ((patient != null && patient.isOnboardingComplete) || TokenManager.isOnboardingComplete(this)) {
            startActivity(new Intent(this, DashboardActivity.class));
            finish();
            return;
        }

        setContentView(R.layout.activity_step1_basic_info);

        // Ensure Firebase Auth session is active (Anonymous sign-in for zero friction)
        com.google.firebase.auth.FirebaseAuth.getInstance().signInAnonymously()
                .addOnSuccessListener(authResult -> {
                    if (authResult.getUser() != null) {
                        TokenManager.saveFirebaseUID(this, authResult.getUser().getUid());
                        android.util.Log.d("Step1BasicInfo", "Firebase Auth successful: " + authResult.getUser().getUid());
                    }
                })
                .addOnFailureListener(e -> {
                    android.util.Log.e("Step1BasicInfo", "Firebase Auth failed", e);
                    Toast.makeText(this, "Authentication failed. Cloud sync may be limited.", Toast.LENGTH_LONG).show();
                });

        bindViews();
        setupSpinners();
        setupListeners();
    }

    private void bindViews() {
        ivProfilePhoto = findViewById(R.id.iv_profile_photo);
        btnUploadPhoto = findViewById(R.id.btn_upload_photo);
        etFullName = findViewById(R.id.et_full_name);
        tvDob = findViewById(R.id.tv_dob);
        spinnerGender = findViewById(R.id.spinner_gender);
        spinnerBloodGroup = findViewById(R.id.spinner_blood_group);
        btnNext = findViewById(R.id.btn_next_step1);
    }

    private void setupSpinners() {
        String[] genders = {"Select Gender", "Male", "Female", "Non-Binary", "Prefer not to say"};
        ArrayAdapter<String> genderAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, genders);
        genderAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerGender.setAdapter(genderAdapter);

        String[] bloodGroups = {"Select Blood Group", "A+", "A-", "B+", "B-", "AB+", "AB-", "O+", "O-"};
        ArrayAdapter<String> bloodAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, bloodGroups);
        bloodAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerBloodGroup.setAdapter(bloodAdapter);
    }

    private void setupListeners() {
        btnUploadPhoto.setOnClickListener(v -> photoPickerLauncher.launch("image/*"));

        tvDob.setOnClickListener(v -> {
            Calendar calendar = Calendar.getInstance();
            if (selectedDobMillis > 0) {
                calendar.setTimeInMillis(selectedDobMillis);
            }
            new DatePickerDialog(this, (view, year, month, dayOfMonth) -> {
                Calendar selected = Calendar.getInstance();
                selected.set(year, month, dayOfMonth);
                selectedDobMillis = selected.getTimeInMillis();
                tvDob.setText(dayOfMonth + "/" + (month + 1) + "/" + year);
            }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show();
        });

        btnNext.setOnClickListener(v -> {
            if (validateInput()) {
                PatientProfile profile = new PatientProfile();
                profile.setFullName(etFullName.getText().toString().trim());
                profile.setDobMillis(selectedDobMillis);
                profile.setGender(spinnerGender.getSelectedItem().toString());
                profile.setBloodGroup(spinnerBloodGroup.getSelectedItem().toString());
                if (selectedPhotoUri != null) {
                    profile.setProfilePhotoUri(selectedPhotoUri.toString());
                }

                Intent intent = new Intent(this, Step1bEmergencyContactsActivity.class);
                intent.putExtra("profile_data", profile);
                startActivity(intent);
            }
        });
    }

    private boolean validateInput() {
        if (TextUtils.isEmpty(etFullName.getText())) {
            etFullName.setError("Name is required");
            return false;
        }
        if (selectedDobMillis == 0) {
            Toast.makeText(this, "Please select Date of Birth", Toast.LENGTH_SHORT).show();
            return false;
        }
        // Validate minimum age of 13 years
        Calendar dobCalendar = Calendar.getInstance();
        dobCalendar.setTimeInMillis(selectedDobMillis);
        Calendar minAgeCalendar = Calendar.getInstance();
        minAgeCalendar.add(Calendar.YEAR, -13);
        if (dobCalendar.after(minAgeCalendar)) {
            Toast.makeText(this, "You must be at least 13 years old to register.", Toast.LENGTH_LONG).show();
            return false;
        }
        if (spinnerGender.getSelectedItemPosition() == 0) {
            Toast.makeText(this, "Please select Gender", Toast.LENGTH_SHORT).show();
            return false;
        }
        if (spinnerBloodGroup.getSelectedItemPosition() == 0) {
            Toast.makeText(this, "Please select Blood Group", Toast.LENGTH_SHORT).show();
            return false;
        }
        return true;
    }
}
