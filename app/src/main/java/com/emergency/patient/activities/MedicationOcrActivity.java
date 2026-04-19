package com.emergency.patient.activities;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.emergency.patient.R;
import com.emergency.patient.ocr.MedicationData;
import com.emergency.patient.ocr.MedicationParser;
import com.emergency.patient.ocr.OcrManager;
import com.emergency.patient.ocr.RxNormApiService;
import com.emergency.patient.db.AppDatabaseProvider;
import com.emergency.patient.db.MedicationEntity;
import com.emergency.patient.security.TokenManager;
import com.emergency.patient.ocr.DrugInteractionApiService;
import com.emergency.patient.ocr.Interaction;
import com.google.android.material.textfield.TextInputEditText;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * MedicationOcrActivity — Medication label OCR review screen.
 *
 * Flow (matches project-wide Upload → Extract → Validate → Save):
 *  1. Receives an image URI or file path via Intent extras.
 *  2. Runs OcrManager (ML Kit primary, Tesseract fallback) on the image.
 *  3. MedicationParser extracts drug name, dosage, and expiry from raw text.
 *  4. Presents an editable review screen — the patient can correct any field.
 *  5. On Approve → saves a MedicationEntity to Room DB.
 *  6. On Discard → returns without writing anything.
 *
 * Threading:
 *  - OCR runs on OcrManager's internal single-thread executor.
 *  - Room DB write runs on a new background Thread (matches DocumentUploadActivity pattern).
 *  - UI updates are posted to the main thread via Handler / runOnUiThread.
 *
 * Intent extras:
 *  EXTRA_IMAGE_URI  (String) — content:// or file:// URI of the label image.
 *
 * Start from DocumentUploadActivity when the picked file is an image
 * (MIME type starts with "image/").
 */
public class MedicationOcrActivity extends AppCompatActivity {

    private static final String TAG = "MedicationOcrActivity";

    public static final String EXTRA_IMAGE_URI = "ocr_image_uri";
    public static final String EXTRA_IMAGE_URIS = "ocr_image_uris";
    public static final String EXTRA_IS_MANUAL = "is_manual_entry";

    // ─── Views ────────────────────────────────────────────────────────────────

    private android.widget.ScrollView layoutReview;
    private android.widget.LinearLayout layoutScanning, layoutError, layoutActionBar, layoutDdiWarnings, ddiCardsContainer;
    private android.widget.TextView tvScanningStatus, tvSourceBadge, tvRawOcrText, tvRawOcrToggle, tvErrorMessage;
    private com.google.android.material.textfield.TextInputEditText etBrandName, etDrugName, etDosage, etExpiry;
    private android.widget.Button btnApprove, btnDiscard, btnRetry;
    private android.view.View btnBack;
    private android.widget.ImageView ivOcrImagePreview;
    private android.view.View cardOcrImagePreview;

    // ─── State ────────────────────────────────────────────────────────────────

    private String imageUriString;
    private java.util.ArrayList<String> imageUris;
    private String rawOcrText  = "";
    private String ocrSource   = "";
    
    private MedicationData originalExtractedData; // Auto-extracted data to compare with user edits
    
    private boolean isWeakResult = false;
    private boolean rawTextVisible = false;

    // FIX 7: OCR Result Caching
    private static final Map<String, MedicationData> ocrCache = new HashMap<>();

    // ─── Lifecycle ────────────────────────────────────────────────────────────

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_medication_ocr);

        imageUriString = getIntent().getStringExtra(EXTRA_IMAGE_URI);
        if (getIntent().hasExtra(EXTRA_IMAGE_URIS)) {
            imageUris = getIntent().getStringArrayListExtra(EXTRA_IMAGE_URIS);
        }

        boolean isManual = getIntent().getBooleanExtra(EXTRA_IS_MANUAL, false);

        bindViews();
        setupListeners();
        
        if (isManual) {
            startManualEntry();
        } else {
            // Load preview
            String displayUri = imageUriString != null ? imageUriString : (imageUris != null && !imageUris.isEmpty() ? imageUris.get(0) : null);
            if (displayUri != null && ivOcrImagePreview != null) {
                ivOcrImagePreview.setImageURI(Uri.parse(displayUri));
                cardOcrImagePreview.setVisibility(View.VISIBLE);
            }
            startOcr();
        }
    }

    private void startManualEntry() {
        showState(State.REVIEW);
        this.ocrSource = "manual";
        tvSourceBadge.setText("📄 Manual Entry");
        tvSourceBadge.setTextColor(getResources().getColor(R.color.color_text_secondary, getTheme()));
        
        if (cardOcrImagePreview != null) {
            cardOcrImagePreview.setVisibility(View.GONE);
        }

        etBrandName.setText("");
        etDrugName.setText("");
        etDosage.setText("");
        etExpiry.setText("");
        
        tvRawOcrText.setText("(manually added, no OCR context)");
        etDrugName.requestFocus();
    }

    // ─── OCR pipeline ─────────────────────────────────────────────────────────

    private void startOcr() {
        if ((imageUriString == null || imageUriString.isEmpty()) && (imageUris == null || imageUris.isEmpty())) {
            showError("No images provided for OCR.");
            return;
        }

        showState(State.SCANNING);
        updateScanningStatus("Analyzing images and merging extraction...");

        new Thread(() -> {
            java.util.List<Bitmap> bitmaps = new java.util.ArrayList<>();
            if (imageUris != null && !imageUris.isEmpty()) {
                for (String uri : imageUris) {
                    Bitmap bmp = decodeBitmapWithRotation(uri);
                    if (bmp != null) bitmaps.add(bmp);
                }
            } else if (imageUriString != null) {
                Bitmap bmp = decodeBitmapWithRotation(imageUriString);
                if (bmp != null) bitmaps.add(bmp);
            }

            if (bitmaps.isEmpty()) {
                runOnUiThread(() -> showError("Decoding failed. Images might be corrupt."));
                return;
            }

            // PHASE 1: Always Execute Heuristic Parser (Source of Authority)
            com.emergency.patient.ocr.MedicationData parserResult;
            String rawText;
            String source;
            int drugConfidence, brandConfidence;

            if (bitmaps.size() == 1) {
                // Shared logic for single image
                com.emergency.patient.ocr.OcrManager ocr = new com.emergency.patient.ocr.OcrManager(this);
                // Synchronous wait for parser result in background thread
                java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
                java.util.concurrent.atomic.AtomicReference<com.emergency.patient.ocr.MedicationData> dataRef = new java.util.concurrent.atomic.AtomicReference<>();
                java.util.concurrent.atomic.AtomicReference<String> rawTextRef = new java.util.concurrent.atomic.AtomicReference<>("");
                
                ocr.process(bitmaps.get(0), new com.emergency.patient.ocr.OcrManager.OcrCallback() {
                    @Override
                    public void onResult(com.emergency.patient.ocr.MedicationData data, String source, String rawText, boolean isWeak) {
                        dataRef.set(data);
                        rawTextRef.set(rawText);
                        latch.countDown();
                    }
                    @Override
                    public void onError(String error) { latch.countDown(); }
                });
                try { latch.await(10, java.util.concurrent.TimeUnit.SECONDS); } catch (Exception ignored) {}
                
                parserResult = dataRef.get() != null ? dataRef.get() : new com.emergency.patient.ocr.MedicationData("", "", "", "");
                rawText = rawTextRef.get();
                source = "single-pass-parser";
                drugConfidence = 70; 
                brandConfidence = 50; 
            } else {
                // Complex fusion for multi-image
                com.emergency.patient.ocr.OcrFusionEngine.FusionResult fuseRes = com.emergency.patient.ocr.OcrFusionEngine.fuseImages(this, bitmaps);
                parserResult = fuseRes.data;
                rawText = fuseRes.rawFlattenedText;
            }

            runOnUiThread(() -> updateScanningStatus("Validating with Hybrid AI..."));
            
            // HYBRID PIPELINE
            com.emergency.patient.ocr.MedicationData validatedData = com.emergency.patient.ocr.OcrHybridValidator.runHybridPipeline(this, rawText, parserResult);

            // UI Update Logic
            runOnUiThread(() -> {
                originalExtractedData = validatedData;
                this.rawOcrText = rawText;
                this.ocrSource = validatedData.provenance != null ? validatedData.provenance : "hybrid";
                
                boolean needsReview = "LOW".equals(validatedData.confidenceLevel) || "MEDIUM".equals(validatedData.confidenceLevel);
                populateFields(validatedData, this.ocrSource, needsReview);
                showState(State.REVIEW);
                Log.d(TAG, "[HybridDecision] Winner: " + validatedData.drugName + " | Source: " + this.ocrSource + " | Confidence: " + validatedData.confidenceLevel);
                
                // Start DDI check for interactions
                if (validatedData.drugName != null && !validatedData.drugName.trim().isEmpty()) {
                    checkDrugInteractionsHybrid(validatedData.drugName);
                }
            });
        }).start();
    }

    private boolean checkLlmTriggers(com.emergency.patient.ocr.MedicationData parser, String raw, int conf) {
        if (parser == null || parser.drugName == null) return true;
        
        boolean isWeak = conf < 75;
        boolean isNoise = parser.drugName.toUpperCase().contains("PHARMACY") || 
                         parser.drugName.toUpperCase().contains("REFILL") || 
                         parser.drugName.toUpperCase().contains("QTY");
        
        boolean complexInstructions = raw.toLowerCase().contains("take") && (parser.dosage == null || parser.dosage.isEmpty());
        
        return isWeak || isNoise || complexInstructions;
    }

    // ─── Bitmap decoding — Fix 3: EXIF Rotation ───────────────────────────────

    private Bitmap decodeBitmapWithRotation(String uriString) {
        try {
            Uri uri = Uri.parse(uriString);
            InputStream is = getContentResolver().openInputStream(uri);
            if (is == null) return null;

            // 1. Read EXIF
            android.media.ExifInterface exif = new android.media.ExifInterface(is);
            int orientation = exif.getAttributeInt(
                android.media.ExifInterface.TAG_ORIENTATION,
                android.media.ExifInterface.ORIENTATION_NORMAL
            );
            is.close();

            // 2. Decode Bitmap
            is = getContentResolver().openInputStream(uri);
            Bitmap bitmap = BitmapFactory.decodeStream(is);
            is.close();
            if (bitmap == null) return null;

            // 3. Apply Rotation (Fix 3)
            int rotation = 0;
            switch (orientation) {
                case android.media.ExifInterface.ORIENTATION_ROTATE_90:  rotation = 90;  break;
                case android.media.ExifInterface.ORIENTATION_ROTATE_180: rotation = 180; break;
                case android.media.ExifInterface.ORIENTATION_ROTATE_270: rotation = 270; break;
            }

            if (rotation != 0) {
                android.graphics.Matrix m = new android.graphics.Matrix();
                m.postRotate(rotation);
                Bitmap rotated = Bitmap.createBitmap(bitmap, 0, 0, 
                        bitmap.getWidth(), bitmap.getHeight(), m, true);
                if (rotated != bitmap) bitmap.recycle();
                return rotated;
            }

            return bitmap;
        } catch (Exception e) {
            Log.e(TAG, "decodeBitmapWithRotation error", e);
            return null;
        }
    }

    // ─── Field population ─────────────────────────────────────────────────────

    private void populateFields(MedicationData data, String source, boolean isWeak) {
        etBrandName.setText(data.brandName);
        etDrugName.setText(data.drugName);
        etDosage.setText(data.dosage);
        etExpiry.setText(data.expiry);

        // --- STEP 3: Actionable Confidence Badge ---
        String confidenceLabel;
        int badgeColor;
        
        switch (data.confidenceLevel) {
            case "HIGH":
                confidenceLabel = "🟢 Verified from label";
                badgeColor = getResources().getColor(R.color.color_success_green, getTheme());
                break;
            case "MEDIUM":
                confidenceLabel = "🟡 Please review";
                badgeColor = Color.parseColor("#FFC107");
                break;
            case "LOW":
            default:
                confidenceLabel = "🔴 Confirm or edit";
                badgeColor = getResources().getColor(R.color.color_emergency_red, getTheme());
                // STEP 8: LOW CONFIDENCE FALLBACK - Focus for correction
                etDrugName.requestFocus();
                Toast.makeText(this, "⚠ We couldn't confirm the medicine. Please edit.", Toast.LENGTH_LONG).show();
                break;
        }
        
        tvSourceBadge.setText(confidenceLabel + " (Details)");
        tvSourceBadge.setTextColor(badgeColor);

        // --- STEP 6: Provenance Display (Subtle) ---
        String hint = data.provenance.isEmpty() ? "Auto-detected via " + source : data.provenance;
        tvSourceBadge.append("\n" + hint);

        tvSourceBadge.setOnClickListener(v -> {
            StringBuilder details = new StringBuilder();
            details.append("BRAND SELECTION:\n").append(data.brandReasoning).append("\n\n");
            details.append("DRUG SELECTION:\n").append(data.drugReasoning).append("\n\n");
            details.append("CONFIDENCE SCORE: ").append(data.confidenceLevel).append("\n");
            details.append("PROVENANCE: ").append(hint);

            new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Extraction Intelligence")
                .setMessage(details.toString())
                .setPositiveButton("Dismiss", null)
                .show();
        });
        
        tvRawOcrText.setText(rawOcrText.isEmpty() ? "(no text detected)" : rawOcrText);
        renderDrugSuggestions(data);
    }

    private void renderDrugSuggestions(MedicationData data) {
        if (data.drugSuggestions.isEmpty()) return;

        // Add suggestions to the action bar area
        LinearLayout actionBar = findViewById(R.id.layout_ocr_action_bar);
        if (actionBar == null) return;

        TextView label = new TextView(this);
        label.setText("Smart Suggestions:");
        label.setTextSize(12);
        label.setPadding(16, 8, 16, 4);
        actionBar.addView(label, 0);

        LinearLayout chipsContainer = new LinearLayout(this);
        chipsContainer.setOrientation(LinearLayout.HORIZONTAL);
        chipsContainer.setPadding(16, 0, 16, 8);
        actionBar.addView(chipsContainer, 1);

        for (String suggestion : data.drugSuggestions) {
            Button btn = new Button(this, null, android.R.attr.buttonStyleSmall);
            btn.setText(suggestion);
            btn.setAllCaps(false);
            btn.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#F5F5F5")));
            btn.setTextColor(Color.BLACK);
            btn.setOnClickListener(v -> {
                etDrugName.setText(suggestion);
                // STEP 10: CONFIDENCE RECALIBRATION
                data.confidenceLevel = "HIGH";
                tvSourceBadge.setText("🟢 Verified manually");
                tvSourceBadge.setTextColor(getResources().getColor(R.color.color_success_green, getTheme()));
            });
            chipsContainer.addView(btn);
        }
    }

    private void populateFieldsWithConfidence(com.emergency.patient.ocr.OcrFusionEngine.FusionResult res) {
        etBrandName.setText(res.data.brandName);
        etDrugName.setText(res.data.drugName);
        etDosage.setText(res.data.dosage);
        etExpiry.setText(res.data.expiry);

        int minConf = Math.min(res.brandConfidence, Math.min(res.drugConfidence, Math.min(res.dosageConfidence, res.expiryConfidence)));
        
        if (minConf < 50) {
            tvSourceBadge.setText("🔴 Low confidence - please edit incorrect fields manually");
            tvSourceBadge.setTextColor(getResources().getColor(R.color.color_emergency_red, getTheme()));
        } else if (minConf < 80) {
            tvSourceBadge.setText("🟡 Medium confidence - review carefully");
            tvSourceBadge.setTextColor(Color.parseColor("#FFC107")); // Yellow
        } else {
            tvSourceBadge.setText("🟢 High confidence - " + res.sourceExplanation);
            tvSourceBadge.setTextColor(getResources().getColor(R.color.color_success_green, getTheme()));
        }
        
        tvRawOcrText.setText(rawOcrText.isEmpty() ? "(no text detected)" : rawOcrText);
    }

    // ─── Approve — delegate to Step3VerifyResumeActivity ──────────────────────

    private void onApprove() {
        String brand   = getText(etBrandName);
        String drug    = getText(etDrugName);
        String dosage  = getText(etDosage);
        String expiry  = getText(etExpiry);

        if (brand.isEmpty() && drug.isEmpty() && dosage.isEmpty() && expiry.isEmpty()) {
            Toast.makeText(this, "Please fill in at least one field.", Toast.LENGTH_SHORT).show();
            return;
        }

        btnApprove.setEnabled(false);
        btnApprove.setText("Saving…");

        // Learn from user corrections (Self-improving OCR step)
        new Thread(() -> {
            try {
                com.emergency.patient.db.OcrCorrectionDao dao = com.emergency.patient.db.AppDatabaseProvider.getInstance(this).ocrCorrectionDao();
                if (originalExtractedData != null) {
                    if (!drug.equals(originalExtractedData.drugName) && originalExtractedData.drugName.length() > 2) {
                        com.emergency.patient.db.OcrCorrectionEntity c = new com.emergency.patient.db.OcrCorrectionEntity();
                        c.wrongText = originalExtractedData.drugName;
                        c.correctText = drug;
                        c.fieldType = "drug";
                        dao.insert(c);
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to save OCR corrections to DB", e);
            }
            
            runOnUiThread(() -> {
                Intent intent = new Intent(this, MedicationConfirmationActivity.class);
                intent.putExtra("ocr_brand", brand);
                intent.putExtra("ocr_name", drug);
                intent.putExtra("ocr_dosage", dosage);
                intent.putExtra("ocr_expiry", expiry);
                intent.putExtra("ocr_raw_text", rawOcrText);
                if (imageUriString != null && !imageUriString.isEmpty()) {
                    intent.putExtra("ocr_image_uri", imageUriString);
                } else if (imageUris != null && !imageUris.isEmpty()) {
                    intent.putExtra("ocr_image_uri", imageUris.get(0));
                }
                startActivity(intent);
                finish();
            });
        }).start();
    }

    // ─── Helper ───────────────────────────────────────────────────────────────

    private String getText(TextInputEditText et) {
        return et.getText() != null ? et.getText().toString().trim() : "";
    }

    private void updateScanningStatus(String message) {
        new Handler(Looper.getMainLooper()).post(() -> tvScanningStatus.setText(message));
    }

    // ─── State machine ────────────────────────────────────────────────────────

    private enum State { SCANNING, REVIEW, ERROR }

    private void showState(State state) {
        layoutScanning.setVisibility(state == State.SCANNING ? View.VISIBLE : View.GONE);
        layoutReview.setVisibility(  state == State.REVIEW   ? View.VISIBLE : View.GONE);
        layoutError.setVisibility(   state == State.ERROR    ? View.VISIBLE : View.GONE);
        layoutActionBar.setVisibility(state == State.REVIEW  ? View.VISIBLE : View.GONE);
    }

    private void showError(String message) {
        tvErrorMessage.setText(message);
        runOnUiThread(() -> showState(State.ERROR));
    }

    // ─── View binding ─────────────────────────────────────────────────────────

    private void bindViews() {
        layoutScanning  = findViewById(R.id.layout_ocr_scanning);
        layoutReview    = (android.widget.ScrollView) findViewById(R.id.layout_ocr_review);
        layoutError     = findViewById(R.id.layout_ocr_error);
        layoutActionBar = findViewById(R.id.layout_ocr_action_bar);
        layoutDdiWarnings = findViewById(R.id.layout_ddi_warnings);
        ddiCardsContainer = findViewById(R.id.ddi_cards_container);

        tvScanningStatus = findViewById(R.id.tv_ocr_scanning_status);
        tvSourceBadge    = findViewById(R.id.tv_ocr_source_badge);
        tvRawOcrText     = findViewById(R.id.tv_raw_ocr_text);
        tvRawOcrToggle   = findViewById(R.id.tv_raw_ocr_toggle);
        tvErrorMessage   = findViewById(R.id.tv_ocr_error_message);

        etBrandName = findViewById(R.id.et_ocr_brand_name);
        etDrugName = findViewById(R.id.et_ocr_drug_name);
        etDosage   = findViewById(R.id.et_ocr_dosage);
        etExpiry   = findViewById(R.id.et_ocr_expiry);

        btnApprove = findViewById(R.id.btn_ocr_approve);
        btnDiscard = findViewById(R.id.btn_ocr_discard);
        btnRetry   = findViewById(R.id.btn_ocr_retry);
        btnBack    = findViewById(R.id.btn_ocr_back);
        
        ivOcrImagePreview = findViewById(R.id.iv_ocr_image_preview);
        cardOcrImagePreview = findViewById(R.id.card_ocr_image_preview);
    }

    // ─── Listeners ────────────────────────────────────────────────────────────

    private void setupListeners() {
        btnApprove.setOnClickListener(v -> onApprove());
        btnDiscard.setOnClickListener(v -> finish());
        btnBack.setOnClickListener(v -> finish());
        btnRetry.setOnClickListener(v -> startOcr());

        tvRawOcrToggle.setOnClickListener(v -> {
            rawTextVisible = !rawTextVisible;
            tvRawOcrText.setVisibility(rawTextVisible ? View.VISIBLE : View.GONE);
            tvRawOcrToggle.setText(rawTextVisible
                    ? getString(R.string.ocr_raw_text_hide)
                    : getString(R.string.ocr_raw_text_toggle));
        });

        etDrugName.addTextChangedListener(new android.text.TextWatcher() {
            private java.util.Timer timer = new java.util.Timer();
            private final long DELAY = 1500; // 1.5s delay to let user finish typing

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(android.text.Editable s) {
                timer.cancel();
                timer = new java.util.Timer();
                timer.schedule(
                    new java.util.TimerTask() {
                        @Override
                        public void run() {
                            String drugName = s.toString().trim();
                            if (drugName.length() >= 3) {
                                runOnUiThread(() -> checkDrugInteractionsHybrid(drugName));
                            }
                        }
                    }, 
                    DELAY
                );
            }
        });
    }

    // ─── Static factory ───────────────────────────────────────────────────────

    /**
     * Creates and returns a launch Intent for MedicationOcrActivity.
     *
     * @param context  Calling context.
     * @param imageUri URI of the medication label image (content:// or file://).
     */
    public static Intent createIntent(Context context, String imageUri) {
        Intent intent = new Intent(context, MedicationOcrActivity.class);
        intent.putExtra(EXTRA_IMAGE_URI, imageUri);
        return intent;
    }

    private void showManualEntryDialog(String message) {
        new androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Scan Unclear")
            .setMessage(message)
            .setPositiveButton("Enter Manually", (dialog, which) -> {
                // Return empty data to trigger manual fields
                populateFields(new MedicationData("", "", "", ""), "manual", true);
                showState(State.REVIEW);
            })
            .setNegativeButton("Retake Photo", (dialog, which) -> {
                finish(); // Go back to camera/upload
            })
            .setCancelable(false)
            .show();
    }

    private boolean isOcrTotalGarbage(String text) {
        if (text == null || text.trim().length() < 15) return true;
        
        String lower = text.toLowerCase();

        // 1. Detect Truly Garbage Clusters (Unreadable OCR debris)
        if (text.matches(".*[§¥¢ﬁ]{3,}.*")) return true;

        // 2. Identify Useful Content (Medical signals or readable words)
        boolean hasMedicalSignal = lower.contains("mg") || 
                                   lower.contains("ml") ||
                                   lower.contains("tablet") ||
                                   lower.contains("capsule") ||
                                   lower.contains("usp") || 
                                   lower.contains("dose") ||
                                   lower.contains("take") ||
                                   lower.contains("rx");

        boolean hasReadableWords = text.matches(".*[A-Za-z]{4,}.*");

        // ACCEPT if it has either medical keywords OR recognizable word structures
        return !(hasMedicalSignal || hasReadableWords);
    }

    private void checkDrugInteractionsHybrid(String newDrugName) {
        layoutDdiWarnings.setVisibility(View.VISIBLE);
        ddiCardsContainer.removeAllViews();
        
        TextView loadingView = new TextView(this);
        loadingView.setText("Checking safety interactions...");
        loadingView.setTextColor(getResources().getColor(R.color.color_text_secondary, getTheme()));
        loadingView.setTextSize(12);
        loadingView.setPadding(0, 8, 0, 8);
        ddiCardsContainer.addView(loadingView);

        new Thread(() -> {
            try {
                String uuid = TokenManager.getUUID(this);
                if (uuid == null) uuid = "";
                java.util.List<MedicationEntity> history = AppDatabaseProvider.getInstance(this).medicationDao().getMedicationsForPatient(uuid);
                
                java.util.List<String> existingDrugNames = new java.util.ArrayList<>();
                if (history != null) {
                    for (MedicationEntity med : history) {
                        if (med.name != null && !med.name.isEmpty()) {
                            existingDrugNames.add(med.name);
                        }
                    }
                }

                if (!existingDrugNames.isEmpty()) {
                    com.emergency.patient.ocr.DrugInteractionEngine.checkHybrid(this, newDrugName, existingDrugNames, interactions -> {
                        runOnUiThread(() -> renderInteractions(interactions));
                    });
                } else {
                    runOnUiThread(() -> layoutDdiWarnings.setVisibility(View.GONE));
                }
                
            } catch (Exception e) {
                Log.e(TAG, "DDI Check failed", e);
                runOnUiThread(() -> layoutDdiWarnings.setVisibility(View.GONE));
            }
        }).start();
    }

    private void renderInteractions(java.util.List<Interaction> interactions) {
        ddiCardsContainer.removeAllViews();
        
        if (interactions == null || interactions.isEmpty()) {
            layoutDdiWarnings.setVisibility(View.GONE);
            return;
        }

        layoutDdiWarnings.setVisibility(View.VISIBLE);

        for (Interaction inter : interactions) {
            View card = getLayoutInflater().inflate(R.layout.item_ddi_card, ddiCardsContainer, false);
            
            View severityBar = card.findViewById(R.id.view_severity_bar);
            TextView tvIcon = card.findViewById(R.id.tv_ddi_icon);
            TextView tvDrugs = card.findViewById(R.id.tv_ddi_drugs);
            TextView tvDesc = card.findViewById(R.id.tv_ddi_description);

            tvDrugs.setText(inter.drugA + " + " + inter.drugB);
            tvDesc.setText(inter.description);

            if ("HIGH".equalsIgnoreCase(inter.severity) || "SEVERE".equalsIgnoreCase(inter.severity)) {
                severityBar.setBackgroundColor(getResources().getColor(R.color.color_emergency_red, getTheme()));
                tvIcon.setText("🔴");
            } else if ("MEDIUM".equalsIgnoreCase(inter.severity) || "MODERATE".equalsIgnoreCase(inter.severity)) {
                severityBar.setBackgroundColor(Color.parseColor("#FFC107")); // Amber
                tvIcon.setText("🟡");
            } else {
                severityBar.setBackgroundColor(getResources().getColor(R.color.color_success_green, getTheme()));
                tvIcon.setText("🟢");
            }

            ddiCardsContainer.addView(card);
        }
    }
}
