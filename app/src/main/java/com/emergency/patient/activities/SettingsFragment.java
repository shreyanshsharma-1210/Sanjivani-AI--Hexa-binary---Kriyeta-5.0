package com.emergency.patient.activities;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.format.DateFormat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import android.net.Uri;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.emergency.patient.R;
import com.emergency.patient.db.AppDatabase;
import com.emergency.patient.db.AppDatabaseProvider;
import com.emergency.patient.db.PatientEntity;
import com.emergency.patient.network.FirebaseRepository;
import com.emergency.patient.security.TokenManager;
import com.emergency.patient.services.EmergencyBackgroundService;
import com.google.android.material.imageview.ShapeableImageView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Date;
import java.util.Set;

public class SettingsFragment extends Fragment {

    private TextView tvName, tvDob, tvBloodGroup;
    private ActivityResultLauncher<String> imagePickerLauncher;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_settings, container, false);

        tvName = view.findViewById(R.id.tv_settings_name);
        tvDob = view.findViewById(R.id.tv_settings_dob);
        tvBloodGroup = view.findViewById(R.id.tv_settings_blood_group);

        populateData();



        view.findViewById(R.id.btn_edit_profile).setOnClickListener(v -> {
            startActivity(new Intent(getContext(), EditProfileActivity.class));
        });

        view.findViewById(R.id.btn_manage_contacts).setOnClickListener(v -> {
            startActivity(new Intent(getContext(), ManageEmergencyContactsActivity.class));
        });

        Button btnReset = view.findViewById(R.id.btn_reset_profile);
        btnReset.setOnClickListener(v -> showResetConfirmationDialog());

        view.findViewById(R.id.btn_clear_chat).setOnClickListener(v -> showClearChatConfirmationDialog());

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        populateData();
    }

    private void populateData() {
        if (getContext() == null) return;
        
        String name = TokenManager.getPatientName(getContext());
        tvName.setText(name.isEmpty() ? "Unknown" : name);



        long dobMillis = TokenManager.getDOB(getContext());
        if (dobMillis > 0) {
            tvDob.setText(DateFormat.format("MMM dd, yyyy", new Date(dobMillis)).toString());
        } else {
            tvDob.setText("Not set");
        }

        // Use TokenManager for blood group as well
        String bloodGroup = TokenManager.getBloodGroup(getContext());
        tvBloodGroup.setText(bloodGroup.isEmpty() ? "Unknown" : bloodGroup);


    }



    private void showResetConfirmationDialog() {
        if (getContext() == null) return;

        new AlertDialog.Builder(getContext())
                .setTitle("Reset Profile & Void QR")
                .setMessage("Resetting your profile will void your current Emergency QR code and delete your data from the cloud. You will need to re-verify your health data permanently. Are you sure?")
                .setPositiveButton("Reset & Start Over", (dialog, which) -> {
                    EmergencyBackgroundService.stop(getContext());
                    
                    // 1. Delete from Cloud first, then local
                    FirebaseRepository.getInstance(getContext()).deleteCloudProfile(new FirebaseRepository.SyncCallback() {
                        @Override
                        public void onSuccess() {
                            performLocalReset();
                        }

                        @Override
                        public void onFailure(Exception e) {
                            // Even if cloud deletion fails (e.g. network), we proceed with local reset for privacy
                            Toast.makeText(getContext(), "Cloud cleanup failed, proceeding with local reset", Toast.LENGTH_SHORT).show();
                            performLocalReset();
                        }
                    });
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showClearChatConfirmationDialog() {
        if (getContext() == null) return;

        new AlertDialog.Builder(getContext())
                .setTitle("Clear AI Chat")
                .setMessage("Are you sure you want to delete all your previous AI assistant conversations?")
                .setPositiveButton("Clear All", (dialog, which) -> {
                    String uuid = TokenManager.getUUID(getContext());
                    new Thread(() -> {
                        AppDatabaseProvider.getInstance(getContext()).chatMessageDao().deleteMessagesForPatient(uuid);
                        if (getActivity() != null) {
                            getActivity().runOnUiThread(() -> {
                                Toast.makeText(getContext(), "Chat history cleared", Toast.LENGTH_SHORT).show();
                            });
                        }
                    }).start();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void performLocalReset() {
        if (getContext() == null) return;
        
        // Throroughly clear both TokenManager and Room DB
        TokenManager.clearAll(getContext());
        
        new Thread(() -> {
            AppDatabase db = AppDatabaseProvider.getInstance(getContext());
            db.patientDao().deleteAllPatients();
            db.emergencyContactDao().deleteAllContacts();
            db.healthDocumentDao().deleteAllDocuments();
            db.chatMessageDao().deleteAllMessages();
            
            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    Toast.makeText(getContext(), "Profile reset successfully", Toast.LENGTH_SHORT).show();
                    Intent intent = new Intent(getContext(), Step1BasicInfoActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                });
            }
        }).start();
    }
}
