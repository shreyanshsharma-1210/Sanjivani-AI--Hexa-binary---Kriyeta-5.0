package com.emergency.patient.services;

import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import androidx.annotation.Nullable;

import com.emergency.patient.db.AppDatabase;
import com.emergency.patient.db.AppDatabaseProvider;
import com.emergency.patient.db.HealthDocumentChunkEntity;
import com.emergency.patient.db.HealthDocumentEntity;
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader;
import com.tom_roush.pdfbox.pdmodel.PDDocument;
import com.tom_roush.pdfbox.text.PDFTextStripper;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * PdfExtractionService — Background service that extracts text from medical PDFs.
 * 
 * Uses pdfbox-android for extraction and updates HealthDocumentEntity.
 */
public class PdfExtractionService extends IntentService {

    private static final String TAG = "PdfExtractionService";
    private static final String EXTRA_DOC_ID = "com.emergency.patient.extra.DOC_ID";

    public PdfExtractionService() {
        super("PdfExtractionService");
    }

    public static void startExtraction(Context context, int docId) {
        Intent intent = new Intent(context, PdfExtractionService.class);
        intent.putExtra(EXTRA_DOC_ID, docId);
        context.startService(intent);
    }

    @Override
    protected void onHandleIntent(@Nullable Intent intent) {
        if (intent == null) return;

        int docId = intent.getIntExtra(EXTRA_DOC_ID, -1);
        if (docId == -1) return;

        Log.d(TAG, "Starting extraction for document ID: " + docId);
        
        AppDatabase db = AppDatabaseProvider.getInstance(this);
        HealthDocumentEntity doc = db.healthDocumentDao().getDocumentById(docId);

        if (doc == null || doc.internalFilePath == null) {
            Log.e(TAG, "Document not found or path is null");
            return;
        }

        File file = new File(doc.internalFilePath);
        if (!file.exists()) {
            Log.e(TAG, "File does not exist at path: " + doc.internalFilePath);
            db.healthDocumentDao().updateExtractionResults(docId, "failed", null);
            return;
        }

        try {
            PDFBoxResourceLoader.init(getApplicationContext());
            PDDocument document = PDDocument.load(file);
            PDFTextStripper stripper = new PDFTextStripper();
            String fullText = stripper.getText(document);
            document.close();

            if (fullText != null && !fullText.trim().isEmpty()) {
                // Update full text in DB
                db.healthDocumentDao().updateFullText(docId, fullText);
                
                // Use RagManager to chunk and store
                doc.fullText = fullText;
                com.emergency.patient.rag.RagManager.chunkAndStore(getApplicationContext(), doc);

                db.healthDocumentDao().updateExtractionResults(docId, "success", null);
                Log.d(TAG, "Extraction successful for doc: " + doc.displayName);
            } else {
                db.healthDocumentDao().updateExtractionResults(docId, "failed", "Empty text");
            }

        } catch (IOException e) {
            Log.e(TAG, "Error extracting text from PDF", e);
            db.healthDocumentDao().updateExtractionResults(docId, "failed", e.getMessage());
        }
    }
}
