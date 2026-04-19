package com.emergency.patient.activities;

import android.app.DatePickerDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import android.net.Uri;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import com.emergency.patient.db.AppDatabase;
import com.emergency.patient.db.AppDatabaseProvider;
import com.emergency.patient.db.PatientEntity;
import com.google.android.material.imageview.ShapeableImageView;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

import androidx.appcompat.app.AppCompatActivity;

import com.emergency.patient.R;
import com.emergency.patient.security.TokenManager;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public class EditProfileActivity extends AppCompatActivity {

    private EditText etName;
    private TextView tvDob, tvBloodGroup;
    private Spinner spinnerGender;
    private Button btnSave;
    private ImageButton btnClose;
    private ShapeableImageView ivAvatar;
    private ActivityResultLauncher<String> imagePickerLauncher;

    private long selectedDobMillis;
    private String[] genders = {"Male", "Female", "Other", "Prefer not to say"};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_profile);

        imagePickerLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                uri -> {
                    if (uri != null) {
                        saveNewProfilePhoto(uri);
                    }
                }
        );

        bindViews();
        setupGenderSpinner();
        loadExistingData();
        setupListeners();
    }

    private void bindViews() {
        etName = findViewById(R.id.et_edit_name);
        tvDob = findViewById(R.id.tv_edit_dob);
        tvBloodGroup = findViewById(R.id.tv_edit_blood_group);
        spinnerGender = findViewById(R.id.spinner_edit_gender);
        btnSave = findViewById(R.id.btn_save_profile);
        btnClose = findViewById(R.id.btn_close_edit);
        ivAvatar = findViewById(R.id.iv_edit_avatar);
    }

    private void setupGenderSpinner() {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, genders);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerGender.setAdapter(adapter);
    }

    private void loadExistingData() {
        etName.setText(TokenManager.getPatientName(this));
        
        selectedDobMillis = TokenManager.getDOB(this);
        if (selectedDobMillis > 0) {
            updateDobDisplay();
        }

        String currentGender = TokenManager.getGender(this);
        int genderIndex = 0;
        for (int i = 0; i < genders.length; i++) {
            if (genders[i].equalsIgnoreCase(currentGender)) {
                genderIndex = i;
                break;
            }
        }
        spinnerGender.setSelection(genderIndex);

        tvBloodGroup.setText(TokenManager.getBloodGroup(this));
        loadAvatar();
    }

    private void loadAvatar() {
        new Thread(() -> {
            String uuid = TokenManager.getUUID(this);
            PatientEntity patient = AppDatabaseProvider.getInstance(this).patientDao().getPatient(uuid);
            if (patient != null) {
                runOnUiThread(() -> {
                    if (patient.profilePhotoUri != null && !patient.profilePhotoUri.isEmpty()) {
                        try {
                            ivAvatar.setImageURI(Uri.parse(patient.profilePhotoUri));
                            ivAvatar.setPadding(0, 0, 0, 0);
                        } catch (Exception e) {
                            ivAvatar.setImageResource(R.drawable.ic_blank_profile);
                        }
                    } else {
                        ivAvatar.setImageResource(R.drawable.ic_blank_profile);
                    }
                });
            }
        }).start();
    }

    private void setupListeners() {
        btnClose.setOnClickListener(v -> finish());
        
        tvDob.setOnClickListener(v -> showDatePicker());

        btnSave.setOnClickListener(v -> {
            if (saveChanges()) {
                Toast.makeText(this, "Profile updated successfully", Toast.LENGTH_SHORT).show();
                finish();
            }
        });

        findViewById(R.id.btn_change_photo).setOnClickListener(v -> {
            imagePickerLauncher.launch("image/*");
        });
    }

    private void saveNewProfilePhoto(Uri sourceUri) {
        new Thread(() -> {
            try {
                InputStream inputStream = getContentResolver().openInputStream(sourceUri);
                if (inputStream == null) return;

                File directory = getFilesDir();
                File destFile = new File(directory, "profile_photo.jpg");

                OutputStream outputStream = new FileOutputStream(destFile);
                byte[] buffer = new byte[4096];
                int length;
                while ((length = inputStream.read(buffer)) > 0) {
                    outputStream.write(buffer, 0, length);
                }
                outputStream.flush();
                outputStream.close();
                inputStream.close();

                String persistentUri = Uri.fromFile(destFile).toString();
                
                // Update DB
                String uuid = TokenManager.getUUID(this);
                AppDatabase db = AppDatabaseProvider.getInstance(this);
                PatientEntity patient = db.patientDao().getPatient(uuid);
                if (patient != null) {
                    patient.profilePhotoUri = persistentUri;
                    db.patientDao().insertPatient(patient);
                }

                runOnUiThread(() -> {
                    Toast.makeText(this, "Photo updated", Toast.LENGTH_SHORT).show();
                    loadAvatar();
                });
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "Failed to update photo", Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    private void showDatePicker() {
        Calendar calendar = Calendar.getInstance();
        if (selectedDobMillis > 0) calendar.setTimeInMillis(selectedDobMillis);

        new DatePickerDialog(this, (view, year, month, dayOfMonth) -> {
            calendar.set(year, month, dayOfMonth);
            selectedDobMillis = calendar.getTimeInMillis();
            updateDobDisplay();
        }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show();
    }

    private void updateDobDisplay() {
        String dobString = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(new Date(selectedDobMillis));
        tvDob.setText(dobString);
    }

    private boolean saveChanges() {
        String name = etName.getText().toString().trim();
        if (TextUtils.isEmpty(name)) {
            Toast.makeText(this, "Name cannot be empty.", Toast.LENGTH_SHORT).show();
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
            Toast.makeText(this, "You must be at least 13 years old.", Toast.LENGTH_LONG).show();
            return false;
        }

        TokenManager.savePatientName(this, name);
        TokenManager.saveDOB(this, selectedDobMillis);
        
        String gender = spinnerGender.getSelectedItem().toString();
        TokenManager.saveGender(this, gender);

        // --- Sync to Cloud (Firestore) ---
        new Thread(() -> {
            String uuid = TokenManager.getUUID(this);
            PatientEntity patient = AppDatabaseProvider.getInstance(this).patientDao().getPatient(uuid);
            if (patient != null) {
                com.emergency.patient.network.FirebaseRepository.getInstance(EditProfileActivity.this).uploadProfile(this, patient, new com.emergency.patient.network.FirebaseRepository.SyncCallback() {
                    @Override
                    public void onSuccess() {
                        android.util.Log.d("EditProfileSync", "Profile successfully synced to Firebase");
                    }

                    @Override
                    public void onFailure(Exception e) {
                        android.util.Log.e("EditProfileSync", "Failed to sync profile to cloud", e);
                    }
                });
            }
        }).start();

        return true;
    }
}
