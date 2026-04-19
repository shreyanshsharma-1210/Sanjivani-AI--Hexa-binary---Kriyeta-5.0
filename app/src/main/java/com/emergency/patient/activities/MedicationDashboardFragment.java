package com.emergency.patient.activities;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.emergency.patient.R;
import com.emergency.patient.adapters.CalendarAdapter;
import com.emergency.patient.adapters.MedicationAdapter;
import com.emergency.patient.adapters.ScheduleAdapter;
import com.emergency.patient.db.AppDatabaseProvider;
import com.emergency.patient.db.MedicationEntity;
import com.emergency.patient.db.MedicationScheduleEntity;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class MedicationDashboardFragment extends Fragment {

    private TextView monthYearText;
    private TextView emptyTextView;
    private RecyclerView calendarRecyclerView;
    private RecyclerView scheduleRecyclerView;
    private RecyclerView medicationRecyclerView;
    private com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton addMedicationFab;
    private View settingsIcon;

    private View cardDdiBanner;
    private TextView tvDdiBannerTitle;
    private TextView tvDdiBannerDesc;

    private CalendarAdapter calendarAdapter;
    private ScheduleAdapter scheduleAdapter;
    private MedicationAdapter medicationAdapter;

    private Calendar selectedDate = Calendar.getInstance();
    private static final int REQUEST_CAMERA = 1001;
    private static final int REQUEST_GALLERY = 1002;
    private Uri currentPhotoUri;
    private List<String> accumulatedPhotoUris = new ArrayList<>();
    
    private boolean isEditMode = false;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_medication_dashboard, container, false);

        monthYearText = view.findViewById(R.id.monthYearText);
        emptyTextView = view.findViewById(R.id.emptyScheduleText);
        calendarRecyclerView = view.findViewById(R.id.calendarRecyclerView);
        scheduleRecyclerView = view.findViewById(R.id.scheduleRecyclerView);
        medicationRecyclerView = view.findViewById(R.id.medicationRecyclerView);
        addMedicationFab = view.findViewById(R.id.fab_add_medication);
        settingsIcon = view.findViewById(R.id.settingsIcon);

        cardDdiBanner = view.findViewById(R.id.card_ddi_banner);
        tvDdiBannerTitle = view.findViewById(R.id.tv_ddi_banner_title);
        tvDdiBannerDesc = view.findViewById(R.id.tv_ddi_banner_desc);

        calendarRecyclerView.setLayoutManager(new LinearLayoutManager(getContext(), LinearLayoutManager.HORIZONTAL, false));
        scheduleRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        medicationRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));

        setupCalendar();
        setupFab();
        setupSettings();
        loadData();

        return view;
    }

    private void setupSettings() {
        if (settingsIcon != null) {
            settingsIcon.setOnClickListener(v -> {
                isEditMode = !isEditMode;
                if (medicationAdapter != null) medicationAdapter.setEditMode(isEditMode);
                if (scheduleAdapter != null) scheduleAdapter.setEditMode(isEditMode);
                Toast.makeText(getContext(), isEditMode ? "Edit Mode Enabled" : "Edit Mode Disabled", Toast.LENGTH_SHORT).show();
            });
        }
    }

    private void setupCalendar() {
        List<Calendar> dates = new ArrayList<>();
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_YEAR, -7);
        for (int i = 0; i < 30; i++) {
            Calendar date = (Calendar) cal.clone();
            dates.add(date);
            cal.add(Calendar.DAY_OF_YEAR, 1);
        }

        calendarAdapter = new CalendarAdapter(dates, date -> {
            selectedDate = date;
            updateMonthYearText();
            loadSchedulesForSelectedDate();
        });
        calendarRecyclerView.setAdapter(calendarAdapter);
        calendarRecyclerView.scrollToPosition(7);
        updateMonthYearText();
    }

    private void updateMonthYearText() {
        SimpleDateFormat sdf = new SimpleDateFormat("MMMM yyyy", Locale.getDefault());
        monthYearText.setText(sdf.format(selectedDate.getTime()));
    }

    private void setupFab() {
        addMedicationFab.setOnClickListener(v -> showScanOptionsDialog());
    }

    private void showScanOptionsDialog() {
        String[] options = {"Take Photo", "Choose from Gallery", "Add Manually"};
        new AlertDialog.Builder(requireContext())
                .setTitle("Scan Medicine")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) openCamera();
                    else if (which == 1) openGallery();
                    else {
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
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (intent.resolveActivity(requireActivity().getPackageManager()) != null) {
            try {
                File photoFile = new File(requireActivity().getCacheDir(), "med_scan_" + System.currentTimeMillis() + ".jpg");
                currentPhotoUri = FileProvider.getUriForFile(requireContext(),
                        requireContext().getApplicationContext().getPackageName() + ".provider", photoFile);
                intent.putExtra(MediaStore.EXTRA_OUTPUT, currentPhotoUri);
                startActivityForResult(intent, REQUEST_CAMERA);
            } catch (Exception e) {
                Toast.makeText(getContext(), "Unable to launch camera", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CAMERA && resultCode == getActivity().RESULT_OK) {
            if (currentPhotoUri != null) {
                accumulatedPhotoUris.add(currentPhotoUri.toString());
                showAddAnotherImageDialog();
            }
        } else if (requestCode == REQUEST_GALLERY && resultCode == getActivity().RESULT_OK && data != null) {
            Uri imageUri = data.getData();
            if (imageUri != null) {
                accumulatedPhotoUris.add(imageUri.toString());
                showAddAnotherImageDialog();
            }
        }
    }

    private void showAddAnotherImageDialog() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_scan_prompt, null);
        AlertDialog dialog = new AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .setCancelable(false)
            .create();

        dialogView.findViewById(R.id.btn_scan_more).setOnClickListener(v -> {
            dialog.dismiss();
            showScanOptionsDialog();
        });

        dialogView.findViewById(R.id.btn_finish_scan).setOnClickListener(v -> {
            dialog.dismiss();
            startOcrActivity();
        });

        dialog.show();
        if (dialog.getWindow() != null) dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
    }

    private void startOcrActivity() {
        if (accumulatedPhotoUris.isEmpty()) return;
        Intent intent = new Intent(getContext(), MedicationOcrActivity.class);
        intent.putStringArrayListExtra(MedicationOcrActivity.EXTRA_IMAGE_URIS, new ArrayList<>(accumulatedPhotoUris));
        startActivity(intent);
        accumulatedPhotoUris.clear();
    }

    @Override
    public void onResume() {
        super.onResume();
        loadData();
    }

    private void loadData() {
        new Thread(() -> {
            List<MedicationEntity> medications = AppDatabaseProvider.getInstance(requireContext()).medicationDao().getAllMedications();
            if (getActivity() == null) return;
            getActivity().runOnUiThread(() -> {
                medicationAdapter = new MedicationAdapter(medications, this::handleDeleteMedication, this::handleMedicationClick);
                medicationAdapter.setEditMode(isEditMode);
                medicationRecyclerView.setAdapter(medicationAdapter);
            });
            loadSchedulesForSelectedDate();
            checkAndDisplayAllInteractions(medications);
        }).start();
    }

    private void handleDeleteMedication(MedicationEntity med) {
        new AlertDialog.Builder(requireContext())
            .setTitle("Delete Medication")
            .setMessage("Are you sure you want to delete " + med.name + "?")
            .setPositiveButton("Delete", (d, w) -> {
                new Thread(() -> {
                    AppDatabaseProvider.getInstance(requireContext()).medicationDao().delete(med);
                    AppDatabaseProvider.getInstance(requireContext()).scheduleDao().deleteByDrugName(med.name);
                    androidx.work.WorkManager.getInstance(requireContext()).cancelAllWorkByTag("med_" + med.name);
                    if (getActivity() != null) getActivity().runOnUiThread(this::loadData);
                }).start();
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void handleMedicationClick(MedicationEntity med) {
        if (isEditMode) return;

        AlertDialog loadingDialog = new AlertDialog.Builder(requireContext())
                .setTitle("AI Insights ✨")
                .setMessage("Fetching insights for " + med.name + "...\n\nPlease wait...")
                .setCancelable(false)
                .show();

        com.emergency.patient.ocr.GroqInterpreter.getMedicationPointers(med.name, resultText -> {
            if (getActivity() == null) return;
            getActivity().runOnUiThread(() -> {
                if (loadingDialog.isShowing()) {
                    loadingDialog.dismiss();
                }

                if (resultText == null) {
                    Toast.makeText(getContext(), "Failed to fetch insights from Groq", Toast.LENGTH_SHORT).show();
                    return;
                }

                String htmlResult = resultText
                        .replaceAll("(?m)^\\s*\\*\\s+(.*)$", "&#8226; $1<br>") // bullets
                        .replaceAll("\\*\\*(.*?)\\*\\*", "<b>$1</b>") // bold
                        .replaceAll("\\*(.*?)\\*", "<i>$1</i>") // italics
                        .replace("\n", "<br>");

                new AlertDialog.Builder(requireContext())
                        .setTitle("Why take " + med.name + "?")
                        .setMessage(android.text.Html.fromHtml(htmlResult, android.text.Html.FROM_HTML_MODE_COMPACT))
                        .setPositiveButton("Got it", null)
                        .show();
            });
        });
    }

    private void loadSchedulesForSelectedDate() {
        new Thread(() -> {
            List<MedicationScheduleEntity> allSchedules = AppDatabaseProvider.getInstance(requireContext()).scheduleDao().getAll();
            List<MedicationScheduleEntity> filtered = new ArrayList<>();
            for (MedicationScheduleEntity s : allSchedules) {
                Calendar entryCal = Calendar.getInstance();
                entryCal.setTimeInMillis(s.timeMillis);
                if (isSameDay(entryCal, selectedDate)) filtered.add(s);
            }
            java.util.Collections.sort(filtered, (a, b) -> Long.compare(a.timeMillis, b.timeMillis));

            if (getActivity() == null) return;
            getActivity().runOnUiThread(() -> {
                if (filtered.isEmpty()) {
                    emptyTextView.setVisibility(View.VISIBLE);
                    scheduleRecyclerView.setVisibility(View.GONE);
                } else {
                    emptyTextView.setVisibility(View.GONE);
                    scheduleRecyclerView.setVisibility(View.VISIBLE);
                    scheduleAdapter = new ScheduleAdapter(filtered, this::handleEditScheduleTime);
                    scheduleAdapter.setEditMode(isEditMode);
                    scheduleRecyclerView.setAdapter(scheduleAdapter);
                }
            });
        }).start();
    }

    private void handleEditScheduleTime(MedicationScheduleEntity schedule) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(schedule.timeMillis);
        new android.app.TimePickerDialog(requireContext(), (view, hourOfDay, minute) -> {
            cal.set(Calendar.HOUR_OF_DAY, hourOfDay);
            cal.set(Calendar.MINUTE, minute);
            new Thread(() -> {
                schedule.timeMillis = cal.getTimeInMillis();
                AppDatabaseProvider.getInstance(requireContext()).scheduleDao().update(schedule);

                // Reschedule all for this drug to ensure WorkManager alarms are updated
                androidx.work.WorkManager.getInstance(requireContext()).cancelAllWorkByTag("med_" + schedule.drugName);
                List<MedicationScheduleEntity> allSchedules = AppDatabaseProvider.getInstance(requireContext()).scheduleDao().getAll();
                for (MedicationScheduleEntity s : allSchedules) {
                    if (s.drugName != null && s.drugName.equals(schedule.drugName)) {
                        long delay = s.timeMillis - System.currentTimeMillis();
                        if (delay > 0 && delay < 7 * 24 * 60 * 60 * 1000L) { // Only schedule up to 7 days ahead
                            androidx.work.Data data = new androidx.work.Data.Builder()
                                    .putString("drug", s.drugName)
                                    .putInt("scheduleId", s.id)
                                    .build();

                            androidx.work.OneTimeWorkRequest work =
                                    new androidx.work.OneTimeWorkRequest.Builder(com.emergency.patient.scheduling.MedicationWorker.class)
                                            .setInitialDelay(delay, java.util.concurrent.TimeUnit.MILLISECONDS)
                                            .setInputData(data)
                                            .addTag("med_" + s.drugName)
                                            .build();

                            androidx.work.WorkManager.getInstance(requireContext()).enqueue(work);
                        }
                    }
                }

                if (getActivity() != null) getActivity().runOnUiThread(this::loadSchedulesForSelectedDate);
            }).start();
        }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), false).show();
    }

    private boolean isSameDay(Calendar cal1, Calendar cal2) {
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
               cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR);
    }

    private void checkAndDisplayAllInteractions(List<MedicationEntity> medications) {
        if (medications == null || medications.size() < 2) {
            if (getActivity() != null) getActivity().runOnUiThread(() -> cardDdiBanner.setVisibility(View.GONE));
            return;
        }

        List<String> drugNames = new ArrayList<>();
        for (MedicationEntity med : medications) {
            if (med.name != null && !med.name.trim().isEmpty()) {
                drugNames.add(med.name);
            }
        }

        com.emergency.patient.ocr.DrugInteractionEngine.checkAllInteractions(requireContext(), drugNames, interactions -> {
            if (getActivity() == null) return;
            getActivity().runOnUiThread(() -> {
                if (interactions.isEmpty()) {
                    cardDdiBanner.setVisibility(View.GONE);
                } else {
                    cardDdiBanner.setVisibility(View.VISIBLE);
                    int highCount = 0;
                    for (com.emergency.patient.ocr.Interaction i : interactions) {
                        if ("HIGH".equalsIgnoreCase(i.severity) || "SEVERE".equalsIgnoreCase(i.severity)) {
                            highCount++;
                        }
                    }
                    
                    if (highCount > 0) {
                        ((com.google.android.material.card.MaterialCardView) cardDdiBanner).setCardBackgroundColor(android.graphics.Color.parseColor("#FFE5E5"));
                        ((com.google.android.material.card.MaterialCardView) cardDdiBanner).setStrokeColor(android.graphics.Color.parseColor("#FF4B4B"));
                        tvDdiBannerTitle.setText("High Severity Interactions!");
                        tvDdiBannerTitle.setTextColor(android.graphics.Color.parseColor("#D32F2F"));
                        tvDdiBannerDesc.setText(highCount + " High Severity Interaction" + (highCount > 1 ? "s" : "") + " Detected");
                        tvDdiBannerDesc.setTextColor(android.graphics.Color.parseColor("#B71C1C"));
                    } else {
                        ((com.google.android.material.card.MaterialCardView) cardDdiBanner).setCardBackgroundColor(android.graphics.Color.parseColor("#FFF8E1"));
                        ((com.google.android.material.card.MaterialCardView) cardDdiBanner).setStrokeColor(android.graphics.Color.parseColor("#FFB300"));
                        tvDdiBannerTitle.setText("Interactions Detected");
                        tvDdiBannerTitle.setTextColor(android.graphics.Color.parseColor("#F57F17"));
                        tvDdiBannerDesc.setText(interactions.size() + " interaction" + (interactions.size() > 1 ? "s" : "") + " found in your cabinet");
                        tvDdiBannerDesc.setTextColor(android.graphics.Color.parseColor("#E65100"));
                    }

                    cardDdiBanner.setOnClickListener(v -> showInteractionsDialog(interactions));
                }
            });
        });
    }

    private void showInteractionsDialog(List<com.emergency.patient.ocr.Interaction> interactions) {
        StringBuilder sb = new StringBuilder();
        for (com.emergency.patient.ocr.Interaction i : interactions) {
            String emoji = "HIGH".equalsIgnoreCase(i.severity) || "SEVERE".equalsIgnoreCase(i.severity) ? "🔴" : "🟡";
            sb.append(emoji).append(" ").append(i.drugA).append(" + ").append(i.drugB).append("\n");
            sb.append(i.description).append("\n\n");
        }
        
        String interactionsText = sb.toString().trim();

        AlertDialog loadingDialog = new AlertDialog.Builder(requireContext())
                .setTitle("Analyzing Interactions ✨")
                .setMessage("Generating simple breakdown of the risks...\n\nPlease wait...")
                .setCancelable(false)
                .show();

        com.emergency.patient.ocr.GroqInterpreter.getDdiPointers(interactionsText, resultText -> {
            if (getActivity() == null) return;
            getActivity().runOnUiThread(() -> {
                if (loadingDialog.isShowing()) {
                    loadingDialog.dismiss();
                }

                String finalMessage = interactionsText;
                if (resultText != null) {
                    // Convert basic markdown to HTML
                    String htmlResult = resultText
                            .replaceAll("(?m)^\\s*\\*\\s+(.*)$", "&#8226; $1<br>") // bullets
                            .replaceAll("\\*\\*(.*?)\\*\\*", "<b>$1</b>") // bold
                            .replaceAll("\\*(.*?)\\*", "<i>$1</i>") // italics
                            .replace("\n", "<br>");
                    finalMessage = finalMessage.replace("\n", "<br>") + "<br><br>---<br><br>" + htmlResult;
                } else {
                    finalMessage = finalMessage.replace("\n", "<br>");
                }

                new AlertDialog.Builder(requireContext())
                    .setTitle("Medication Interactions")
                    .setMessage(android.text.Html.fromHtml(finalMessage, android.text.Html.FROM_HTML_MODE_COMPACT))
                    .setPositiveButton("Understood", null)
                    .show();
            });
        });
    }
}
