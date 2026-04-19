package com.emergency.patient.activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.emergency.patient.R;
import com.emergency.patient.db.AppDatabase;
import com.emergency.patient.db.AppDatabaseProvider;
import com.emergency.patient.db.HealthDocumentEntity;
import com.emergency.patient.db.PatientEntity;
import com.emergency.patient.security.TokenManager;
import android.widget.Toast;
import androidx.core.content.FileProvider;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.Date;
import android.widget.LinearLayout;
import android.webkit.MimeTypeMap;
import android.widget.ImageView;
import android.content.Context;
import android.app.AlertDialog;
import android.provider.MediaStore;
import android.net.Uri;
import android.app.Activity;
import android.util.Log;
import com.emergency.patient.db.EmergencyContactEntity;
import com.emergency.patient.db.EmergencyContactDao;

public class HealthResumeFragment extends Fragment {

    private TextView tvName, tvDobGender, tvBlood, tvConditions, tvContacts;
    
    private static final int REQUEST_CAMERA = 1002;
    private static final int REQUEST_GALLERY = 1003;
    private Uri currentPhotoUri;
    private java.util.ArrayList<String> accumulatedPhotoUris = new java.util.ArrayList<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_health_resume, container, false);

        tvName = view.findViewById(R.id.tv_resume_patient_name);
        tvDobGender = view.findViewById(R.id.tv_resume_dob_gender);
        tvBlood = view.findViewById(R.id.tv_resume_blood_group);
        tvConditions = view.findViewById(R.id.tv_resume_conditions);
        tvContacts = view.findViewById(R.id.tv_resume_contacts);

        Button btnUploadRecord = view.findViewById(R.id.btnUploadRecord);
        btnUploadRecord.setOnClickListener(v -> {
            if (getContext() != null) {
                startActivity(new Intent(getContext(), DocumentUploadActivity.class));
            }
        });

        Button btnScanMedicine = view.findViewById(R.id.btnScanMedicine);
        btnScanMedicine.setOnClickListener(v -> {
            showImageSourceDialog();
        });

        return view;
    }

    private void showImageSourceDialog() {
        if (getContext() == null) return;
        String[] options = {"Take Photo", "Choose from Gallery", "Add Manually"};
        new AlertDialog.Builder(getContext())
                .setTitle("Scan Medicine")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        openCamera();
                    } else if (which == 1) {
                        openGallery();
                    } else {
                        Intent intent = new Intent(getContext(), MedicationOcrActivity.class);
                        intent.putExtra(MedicationOcrActivity.EXTRA_IS_MANUAL, true);
                        startActivity(intent);
                    }
                })
                .show();
    }

    private void openGallery() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        startActivityForResult(intent, REQUEST_GALLERY);
    }

    private void openCamera() {
        if (getContext() == null || getActivity() == null) return;
        
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (intent.resolveActivity(getActivity().getPackageManager()) != null) {
            try {
                File photoFile = new File(getContext().getCacheDir(), "med_scan_" + System.currentTimeMillis() + ".jpg");
                currentPhotoUri = FileProvider.getUriForFile(
                        getContext(),
                        getContext().getApplicationContext().getPackageName() + ".provider",
                        photoFile);
                intent.putExtra(MediaStore.EXTRA_OUTPUT, currentPhotoUri);
                startActivityForResult(intent, REQUEST_CAMERA);
            } catch (Exception e) {
                Log.e("HealthResumeFragment", "Failed to create temp file for camera", e);
                Toast.makeText(getContext(), "Unable to launch camera", Toast.LENGTH_SHORT).show();
            }
        } else {
            Toast.makeText(getContext(), "No camera app found", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CAMERA && resultCode == Activity.RESULT_OK) {
            if (currentPhotoUri != null && getContext() != null) {
                accumulatedPhotoUris.add(currentPhotoUri.toString());
                showAddAnotherImageDialog();
            }
        } else if (requestCode == REQUEST_GALLERY && resultCode == Activity.RESULT_OK && data != null) {
            Uri imageUri = data.getData();
            if (imageUri != null && getContext() != null) {
                accumulatedPhotoUris.add(imageUri.toString());
                showAddAnotherImageDialog();
            }
        }
    }

    private void showAddAnotherImageDialog() {
        if (getContext() == null) return;
        android.view.View dialogView = getLayoutInflater().inflate(R.layout.dialog_scan_prompt, null);
        androidx.appcompat.app.AlertDialog dialog = new androidx.appcompat.app.AlertDialog.Builder(getContext())
            .setView(dialogView)
            .setCancelable(false)
            .create();

        dialogView.findViewById(R.id.btn_scan_more).setOnClickListener(v -> {
            dialog.dismiss();
            showImageSourceDialog();
        });

        dialogView.findViewById(R.id.btn_finish_scan).setOnClickListener(v -> {
            dialog.dismiss();
            Intent intent = new Intent(getContext(), MedicationOcrActivity.class);
            intent.putStringArrayListExtra(MedicationOcrActivity.EXTRA_IMAGE_URIS, accumulatedPhotoUris);
            startActivity(intent);
            accumulatedPhotoUris.clear();
        });

        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (getView() != null) {
            populateSummary();
            populateDocuments(getView());
        }
    }

    private void populateSummary() {
        if (getContext() == null) return;

        AppDatabase db = AppDatabaseProvider.getInstance(getContext());
        String uuid = TokenManager.getUUID(getContext());
        PatientEntity patient = db.patientDao().getPatient(uuid);

        if (patient == null) {
            android.util.Log.d("HealthResumeFragment", "No PatientEntity found for UUID: " + uuid);
            // Fallback to TokenManager for legacy data
            tvName.setText(TokenManager.getPatientName(getContext()));
            tvBlood.setText("Blood Group: " + TokenManager.getBloodGroup(getContext()));
            
            String gender = TokenManager.getGender(getContext());
            String dob = "--";
            long dobMillis = TokenManager.getDOB(getContext());
            if (dobMillis > 0) {
                dob = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(new Date(dobMillis));
            }
            tvDobGender.setText("DOB: " + dob + "  •  Gender: " + gender);

            Set<String> conditions = TokenManager.getConditions(getContext());
            displayConditions(conditions);
            
            // Background thread to fetch contacts for legacy lookup
            new Thread(() -> {
                List<EmergencyContactEntity> contacts = db.emergencyContactDao().getContactsForPatient(uuid);
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> displayContacts(contacts));
                }
            }).start();
            
        } else {
            android.util.Log.d("HealthResumeFragment", "Found patient: " + patient.fullName + ", Blood: " + patient.bloodGroup);
            tvName.setText(patient.fullName);
            tvBlood.setText("Blood Group: " + patient.bloodGroup);
            
            String dob = patient.dobMillis > 0 ? new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(new Date(patient.dobMillis)) : "--";
            tvDobGender.setText("DOB: " + dob + "  •  Gender: " + patient.gender);

            // Reconstruct conditions list from Entity
            Set<String> conditions = new HashSet<>();
            if (patient.pastMedicalDiagnosis != null) conditions.add("Past Diagnosis: " + patient.pastMedicalDiagnosis);
            if (patient.pharmacologicalStatus != null) conditions.add("Medications: " + patient.pharmacologicalStatus);
            if (patient.clinicalAllergies != null) conditions.add("Allergies: " + patient.clinicalAllergies);
            if (patient.hereditaryConditions != null) conditions.add("Family History: " + patient.hereditaryConditions);
            if (patient.lifestyleFactor != null) conditions.add("Lifestyle: " + patient.lifestyleFactor);

            if (conditions.isEmpty()) conditions.add("No medical history reported.");
            displayConditions(conditions);
            
            // Background fetch for contacts
            new Thread(() -> {
                List<EmergencyContactEntity> contacts = db.emergencyContactDao().getContactsForPatient(patient.uuid);
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> displayContacts(contacts));
                }
            }).start();
        }
    }

    private void displayContacts(List<EmergencyContactEntity> contacts) {
        if (contacts == null || contacts.isEmpty()) {
            tvContacts.setText("No emergency contacts saved.");
        } else {
            StringBuilder sb = new StringBuilder();
            for (EmergencyContactEntity c : contacts) {
                sb.append("• ").append(c.name).append(" (").append(c.phoneNumber).append(")\n");
            }
            tvContacts.setText(sb.toString().trim());
        }
    }

    private void displayConditions(Set<String> conditions) {
        if (conditions.isEmpty() || (conditions.size() == 1 && conditions.contains("Healthy"))) {
            tvConditions.setText("- No critical triage conditions reported.");
        } else {
            StringBuilder sb = new StringBuilder();
            for (String c : conditions) {
                if (!c.equals("Healthy")) {
                    sb.append("• ").append(c).append("\n");
                }
            }
            tvConditions.setText(sb.toString().trim());
        }
    }

    private void populateDocuments(View rootView) {
        if (getContext() == null) return;

        AppDatabase db = AppDatabaseProvider.getInstance(getContext());
        PatientEntity patient = db.patientDao().getFirstPatient();
        String uuid = (patient != null) ? patient.uuid : TokenManager.getUUID(getContext());

        List<HealthDocumentEntity> docs = db.healthDocumentDao().getDocumentsForPatient(uuid);

        LinearLayout container = rootView.findViewById(R.id.ll_documents_container);
        View emptyState = rootView.findViewById(R.id.ll_empty_state);
        View header = rootView.findViewById(R.id.tv_header_documents);

        container.removeAllViews();

        if (docs.isEmpty()) {
            emptyState.setVisibility(View.VISIBLE);
            container.setVisibility(View.GONE);
            header.setVisibility(View.GONE);
        } else {
            emptyState.setVisibility(View.GONE);
            container.setVisibility(View.VISIBLE);
            header.setVisibility(View.VISIBLE);

            LayoutInflater inflater = LayoutInflater.from(getContext());
            for (HealthDocumentEntity doc : docs) {
                View itemView = inflater.inflate(R.layout.item_health_document, container, false);
                
                TextView tvNameItem = itemView.findViewById(R.id.tv_item_doc_name);
                ImageView ivDelete = itemView.findViewById(R.id.iv_delete_doc);

                tvNameItem.setText(doc.displayName);
                
                // Allow user to open the file to view its contents
                itemView.setOnClickListener(v -> openDocument(doc.internalFilePath, doc.displayName));

                ivDelete.setOnClickListener(v -> {
                    new AlertDialog.Builder(getContext())
                            .setTitle("Delete Document")
                            .setMessage("Are you sure you want to delete '" + doc.displayName + "'?")
                            .setPositiveButton("Delete", (dialog, which) -> {
                                // 1. Trigger Cloud Deletion
                                com.emergency.patient.network.FirebaseRepository.getInstance(getContext())
                                        .deleteDocumentFromCloud(doc.cloudPublicId, doc.firestoreId, new com.emergency.patient.network.FirebaseRepository.SyncCallback() {
                                            @Override
                                            public void onSuccess() {
                                                // 2. Delete from local database on background thread
                                                new Thread(() -> {
                                                    db.healthDocumentDao().deleteDocument(doc.id);
                                                    if (getActivity() != null) {
                                                        getActivity().runOnUiThread(() -> populateDocuments(rootView));
                                                    }
                                                }).start();
                                            }

                                            @Override
                                            public void onFailure(Exception e) {
                                                // Proceed with local deletion anyway for UI consistency
                                                new Thread(() -> {
                                                    db.healthDocumentDao().deleteDocument(doc.id);
                                                    if (getActivity() != null) {
                                                        getActivity().runOnUiThread(() -> populateDocuments(rootView));
                                                    }
                                                }).start();
                                            }
                                        });
                            })
                            .setNegativeButton("Cancel", null)
                            .show();
                });

                container.addView(itemView);
            }
        }
    }

    private void openDocument(String innerPath, String docName) {
        if (getContext() == null || innerPath == null || innerPath.isEmpty()) return;
        
        File file = new File(innerPath);
        if (!file.exists()) {
            Toast.makeText(getContext(), "Document file cannot be found on device.", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            android.net.Uri uri = FileProvider.getUriForFile(
                    getContext(),
                    getContext().getApplicationContext().getPackageName() + ".provider",
                    file);
                    
            String type = getContext().getContentResolver().getType(uri);
            if (type == null) {
                String extension = MimeTypeMap.getFileExtensionFromUrl(uri.toString());
                type = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension.toLowerCase());
            }
            if (type == null) type = "*/*";
                    
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(uri, type);
            intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NO_HISTORY);
            startActivity(Intent.createChooser(intent, "Open with " + docName));
        } catch (Exception e) {
            Toast.makeText(getContext(), "Ensure a viewer app is installed to open this file format.", Toast.LENGTH_LONG).show();
        }
    }
}
