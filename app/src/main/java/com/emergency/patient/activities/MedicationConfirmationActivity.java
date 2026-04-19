package com.emergency.patient.activities;

import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.emergency.patient.R;
import com.emergency.patient.db.AppDatabase;
import com.emergency.patient.db.AppDatabaseProvider;
import com.emergency.patient.db.MedicationEntity;
import com.emergency.patient.db.MedicationScheduleEntity;
import com.emergency.patient.ocr.DrugNormalizer;
import com.emergency.patient.ocr.NormalizationResult;
import com.emergency.patient.scheduling.CalendarService;
import com.emergency.patient.scheduling.GroqScheduleInterpreter;
import com.emergency.patient.scheduling.ScheduleData;
import com.emergency.patient.security.TokenManager;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.channels.FileChannel;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class MedicationConfirmationActivity extends AppCompatActivity {

    private String ocrBrand, ocrName, ocrDosage, ocrExpiry, ocrRawText, ocrImageUri;
    private TextView tvDrugName, tvBrandName, tvDosage, tvExpiry;
    private LinearLayout layoutSchedulePreview;
    private Button btnSave;
    private NormalizationResult normResult;
    private ScheduleData scheduleData;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_medication_confirmation);

        // Get data from OCR Activity
        ocrBrand = getIntent().getStringExtra("ocr_brand");
        ocrName = getIntent().getStringExtra("ocr_name");
        ocrDosage = getIntent().getStringExtra("ocr_dosage");
        ocrExpiry = getIntent().getStringExtra("ocr_expiry");
        ocrRawText = getIntent().getStringExtra("ocr_raw_text");
        ocrImageUri = getIntent().getStringExtra("ocr_image_uri");

        bindViews();
        processData();
        setupListeners();
    }

    private void bindViews() {
        tvDrugName = findViewById(R.id.tv_confirm_drug_name);
        tvBrandName = findViewById(R.id.tv_confirm_brand_name);
        tvDosage = findViewById(R.id.tv_confirm_dosage);
        tvExpiry = findViewById(R.id.tv_confirm_expiry);
        layoutSchedulePreview = findViewById(R.id.layout_schedule_preview);
        btnSave = findViewById(R.id.btn_confirm_save);
    }

    private void processData() {
        // Normalize drug name
        normResult = DrugNormalizer.normalize(this, ocrName);
        tvDrugName.setText(normResult.name);
        tvBrandName.setText(ocrBrand != null && !ocrBrand.isEmpty() ? ocrBrand : "GENERIC");
        tvDosage.setText(ocrDosage);
        tvExpiry.setText(ocrExpiry != null && !ocrExpiry.isEmpty() ? ocrExpiry : "Not set");

        // Parse schedule
        new Thread(() -> {
            scheduleData = GroqScheduleInterpreter.parseWithFallback(ocrDosage);
            runOnUiThread(this::renderSchedulePreview);
        }).start();
    }

    private void renderSchedulePreview() {
        layoutSchedulePreview.removeAllViews();
        if (scheduleData == null || scheduleData.times.isEmpty()) {
            TextView tv = new TextView(this);
            tv.setText("No daily reminders detected.");
            tv.setTextColor(getResources().getColor(R.color.color_text_secondary, getTheme()));
            tv.setPadding(0, 16, 0, 16);
            layoutSchedulePreview.addView(tv);
            return;
        }

        for (int i = 0; i < scheduleData.times.size(); i++) {
            final int index = i;
            String timeStr = scheduleData.times.get(i);
            
            View item = getLayoutInflater().inflate(R.layout.item_confirm_schedule, layoutSchedulePreview, false);
            TextView text = item.findViewById(R.id.tv_schedule_time);
            View btnAdjust = item.findViewById(R.id.btn_adjust_time);

            text.setText("🔔 Daily at " + timeStr);
            btnAdjust.setVisibility(View.GONE);

            layoutSchedulePreview.addView(item);
        }
    }

    private void setupListeners() {
        btnSave.setOnClickListener(v -> saveMedication());
    }

    private void saveMedication() {
        btnSave.setEnabled(false);
        btnSave.setText("Saving...");

        new Thread(() -> {
            try {
                String uuid = TokenManager.getUUID(this);
                if (uuid == null) uuid = "";

                MedicationEntity entity = new MedicationEntity();
                entity.patientUuid = uuid;
                entity.brandName = ocrBrand;
                entity.name = normResult.name;
                entity.dosage = ocrDosage;
                entity.expiry = ocrExpiry;
                entity.rawOcrText = ocrRawText;
                entity.ocrSource = "hybrid";

                if (ocrImageUri != null && !ocrImageUri.isEmpty()) {
                    entity.imageUri = saveImageToInternal(ocrImageUri);
                }

                // Insert into DB
                AppDatabaseProvider.getInstance(this).medicationDao().insertMedication(entity);

                // Calendar Integration
                CalendarService.addMedicationToCalendar(this, entity.name, scheduleData);

                // Schedule reminders
                scheduleReminders(entity);

                runOnUiThread(() -> {
                    Toast.makeText(this, "Medication Added Successfully! ✅", Toast.LENGTH_LONG).show();
                    Intent intent = new Intent(this, DashboardActivity.class);
                    intent.putExtra("selected_nav_item", R.id.nav_dashboard);
                    intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                    startActivity(intent);
                    finish();
                });

            } catch (Exception e) {
                Log.e("ConfirmActivity", "Save failed", e);
                runOnUiThread(() -> {
                    btnSave.setEnabled(true);
                    btnSave.setText("Confirm & Schedule");
                    Toast.makeText(this, "Failed to save medication", Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }

    private void scheduleReminders(MedicationEntity entity) {
        if (scheduleData == null) return;
        
        for (String timeStr : scheduleData.times) {
            try {
                String[] parts = timeStr.split(":");
                int hour = Integer.parseInt(parts[0]);
                int minute = Integer.parseInt(parts[1]);

                for (int day = 0; day < 30; day++) {
                    Calendar cal = Calendar.getInstance();
                    cal.add(Calendar.DAY_OF_YEAR, day);
                    cal.set(Calendar.HOUR_OF_DAY, hour);
                    cal.set(Calendar.MINUTE, minute);
                    cal.set(Calendar.SECOND, 0);

                    long occurrenceTime = cal.getTimeInMillis();
                    if (day == 0 && occurrenceTime < System.currentTimeMillis()) continue;

                    int rowId = (int) AppDatabaseProvider.getInstance(this).scheduleDao().insert(
                        new MedicationScheduleEntity(entity.name, occurrenceTime, "PENDING")
                    );

                    if (day < 7) {
                        scheduleWork(entity.name, rowId, occurrenceTime);
                    }
                }
            } catch (Exception e) {
                Log.e("ConfirmActivity", "Scheduling failed for " + timeStr, e);
            }
        }
    }

    private void scheduleWork(String drugName, int scheduleId, long time) {
        long delay = time - System.currentTimeMillis();
        androidx.work.Data data = new androidx.work.Data.Builder()
            .putString("drug", drugName)
            .putInt("scheduleId", scheduleId)
            .build();

        androidx.work.OneTimeWorkRequest work =
            new androidx.work.OneTimeWorkRequest.Builder(com.emergency.patient.scheduling.MedicationWorker.class)
                .setInitialDelay(delay, java.util.concurrent.TimeUnit.MILLISECONDS)
                .setInputData(data)
                .addTag("med_" + drugName)
                .build();

        androidx.work.WorkManager.getInstance(this).enqueue(work);
    }

    private String saveImageToInternal(String uriStr) {
        try {
            Uri sourceUri = Uri.parse(uriStr);
            File internalDir = new File(getFilesDir(), "med_images");
            if (!internalDir.exists()) internalDir.mkdirs();

            File destFile = new File(internalDir, "med_" + System.currentTimeMillis() + ".jpg");
            
            try (FileInputStream inStream = (FileInputStream) getContentResolver().openInputStream(sourceUri);
                 FileOutputStream outStream = new FileOutputStream(destFile)) {
                FileChannel inChannel = inStream.getChannel();
                FileChannel outChannel = outStream.getChannel();
                inChannel.transferTo(0, inChannel.size(), outChannel);
            }
            return destFile.getAbsoluteFile().toString();
        } catch (Exception e) {
            Log.e("ConfirmActivity", "Image copy failed", e);
            return uriStr;
        }
    }
}
