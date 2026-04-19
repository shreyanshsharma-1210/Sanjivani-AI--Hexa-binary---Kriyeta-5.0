package com.emergency.patient.activities;

import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.emergency.patient.R;
import com.emergency.patient.db.AppDatabaseProvider;
import com.emergency.patient.db.HealthDocumentEntity;
import com.emergency.patient.security.TokenManager;
import com.emergency.patient.utils.DocumentPickerHelper;
import com.emergency.patient.utils.LocalStorageManager;
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader;
import com.emergency.patient.services.PdfExtractionService;

import java.io.File;

/**
 * DocumentUploadActivity — Simple document intake.
 *
 * States: IDLE → SELECTED → UPLOADING → ERROR
 *
 * Flow:
 *  1. Patient taps "+ Add Document" — SAF picker opens
 *  2. Selected file shown with thumbnail + file name
 *  3. "Upload" → Saves locally attached to patient's records
 */
public class DocumentUploadActivity extends AppCompatActivity {

    // ─── Views ────────────────────────────────────────────────────────────────
    private LinearLayout layoutIdle, layoutSelected, layoutUploading, layoutError;
    private ImageView ivDocumentPreview;
    private TextView tvDocumentName, tvUploadStatus, tvErrorMessage;
    private Button btnPickDocument, btnUpload, btnRetry;
    private View btnCancel;
    private ProgressBar progressUpload;

    // ─── State ────────────────────────────────────────────────────────────────
    private DocumentPickerHelper picker;
    private Uri pickedUri;
    private File pickedFile;
    private String pickedName;

    // ─── Lifecycle ────────────────────────────────────────────────────────────

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_document_upload);
        bindViews();
        showState(State.IDLE);

        // Initialize PDFBox for local extraction (Zero-Cost RAG)
        PDFBoxResourceLoader.init(getApplicationContext());

        picker = new DocumentPickerHelper(this, new DocumentPickerHelper.OnDocumentPicked() {
            @Override
            public void onPicked(Uri uri, String displayName, File cachedCopy) {
                pickedUri  = uri;
                pickedName = displayName;
                pickedFile = cachedCopy;
                onDocumentSelected();
            }

            @Override
            public void onCancelled() { /* stay on IDLE */ }
        });

        btnPickDocument.setOnClickListener(v -> picker.launch());
        btnUpload.setOnClickListener(v -> startUpload());
        btnRetry.setOnClickListener(v -> showState(State.IDLE));
        btnCancel.setOnClickListener(v -> finish());
    }

    // ─── Document Selected ────────────────────────────────────────────────────

    private void onDocumentSelected() {
        tvDocumentName.setText(pickedName);

        // Show image preview if applicable
        String mime = DocumentPickerHelper.getMimeType(this, pickedUri);
        if (mime.startsWith("image/")) {
            ivDocumentPreview.setImageURI(pickedUri);
            ivDocumentPreview.setVisibility(View.VISIBLE);
        } else {
            // PDF — show generic document icon
            ivDocumentPreview.setImageResource(android.R.drawable.ic_menu_agenda);
            ivDocumentPreview.setVisibility(View.VISIBLE);
        }
        showState(State.SELECTED);
    }

    // ─── Upload ───────────────────────────────────────────────────────────────

    private void startUpload() {
        if (pickedFile == null || !pickedFile.exists()) {
            showError("Document file not accessible. Please pick again.");
            return;
        }



        // ── PDF files → existing cloud upload + PDF extraction pipeline ───────
        showState(State.UPLOADING);
        tvUploadStatus.setText("Uploading…");

        String uuid = TokenManager.getUUID(this);

        // Save original doc to local storage and DB for tracking
        String savedPath = LocalStorageManager.saveFileToInternalStorage(
                DocumentUploadActivity.this, pickedUri, pickedName);

        if (savedPath != null) {
            HealthDocumentEntity doc = new HealthDocumentEntity(uuid, pickedName, savedPath);

            // Run database insertion on a background thread
            new Thread(() -> {
                try {
                    long id = AppDatabaseProvider.getInstance(DocumentUploadActivity.this)
                            .healthDocumentDao().insertDocument(doc);
                    doc.id = (int) id;

                    // --- Cloudinary Upload ---
                    com.emergency.patient.network.FirebaseRepository.getInstance(DocumentUploadActivity.this)
                            .uploadDocument(savedPath, pickedName,
                                    new com.emergency.patient.network.FirebaseRepository.DocumentUploadCallback() {
                                @Override
                                public void onSuccess(String cloudUrl, String publicId, String firestoreId) {
                                    new Thread(() -> {
                                        doc.cloudPublicId = publicId;
                                        doc.firestoreId   = firestoreId;

                                        // Extract text locally for PDFs via background service
                                        if (pickedName != null && pickedName.toLowerCase().endsWith(".pdf")) {
                                            PdfExtractionService.startExtraction(DocumentUploadActivity.this, doc.id);
                                        }

                                        AppDatabaseProvider.getInstance(DocumentUploadActivity.this)
                                                .healthDocumentDao().insertDocument(doc);
                                    }).start();

                                    runOnUiThread(() -> {
                                        tvUploadStatus.setText("Document Saved & Synced Successfully ✅");
                                        new android.os.Handler().postDelayed(() -> finish(), 1000);
                                    });
                                }

                                @Override
                                public void onFailure(Exception e) {
                                    runOnUiThread(() -> {
                                        tvUploadStatus.setText("Saved locally (Cloud sync delayed) ✅");
                                        new android.os.Handler().postDelayed(() -> finish(), 1500);
                                    });
                                }
                            });

                } catch (Exception e) {
                    runOnUiThread(() -> showError("Database Error: Could not save document profile. Ensure patient profile exists."));
                }
            }).start();
        } else {
            showError("Failed to save the document locally.");
        }
    }

    // Local PDF Text Extraction is now handled by PdfExtractionService

    // ─── Error ────────────────────────────────────────────────────────────────

    private void showError(String message) {
        tvErrorMessage.setText(message);
        showState(State.ERROR);
    }

    // ─── State Machine ────────────────────────────────────────────────────────

    private enum State { IDLE, SELECTED, UPLOADING, ERROR }

    private void showState(State state) {
        layoutIdle.setVisibility(    state == State.IDLE      ? View.VISIBLE : View.GONE);
        layoutSelected.setVisibility(state == State.SELECTED  ? View.VISIBLE : View.GONE);
        layoutUploading.setVisibility(state == State.UPLOADING ? View.VISIBLE : View.GONE);
        layoutError.setVisibility(   state == State.ERROR     ? View.VISIBLE : View.GONE);
    }

    // ─── View Binding ─────────────────────────────────────────────────────────

    private void bindViews() {
        layoutIdle      = findViewById(R.id.layout_idle);
        layoutSelected  = findViewById(R.id.layout_selected);
        layoutUploading = findViewById(R.id.layout_uploading);
        layoutError     = findViewById(R.id.layout_error);

        ivDocumentPreview = findViewById(R.id.iv_doc_preview);
        tvDocumentName    = findViewById(R.id.tv_doc_name);
        tvUploadStatus    = findViewById(R.id.tv_upload_status);
        tvErrorMessage    = findViewById(R.id.tv_error_message);

        btnPickDocument   = findViewById(R.id.btn_pick_document);
        btnUpload         = findViewById(R.id.btn_upload);
        btnRetry          = findViewById(R.id.btn_retry);
        btnCancel         = findViewById(R.id.btn_cancel_upload);

        progressUpload    = findViewById(R.id.progress_upload);
    }
}
