package com.emergency.patient.ocr;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.util.Log;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * OcrManager — Robust, multi-layered OCR pipeline.
 * Performs multi-pass detection with preprocessing and fallbacks.
 */
public class OcrManager {

    private static final String TAG = "OcrManager";
    private static final String TESS_DATA_DIR = "tessdata";
    private static final String TESS_LANG = "eng";
    private static final int MAX_WIDTH_PX = 1024;

    public interface OcrCallback {
        /**
         * Called with the optimized medical data fields.
         *
         * @param rawText Full raw OCR text for validation.
         * @param isWeak  Confidence indicator.
         */
        void onResult(MedicationData data, String source, String rawText, boolean isWeak);
        void onError(String error);
    }

    private final Context context;
    private final Executor executor = Executors.newSingleThreadExecutor();

    public OcrManager(Context context) {
        this.context = context.getApplicationContext();
    }

    public void process(Bitmap source, OcrCallback callback) {
        executor.execute(() -> {
            try {
                // Perform 3-pass ML Kit detection
                Bitmap p1Bmp = resize(source, MAX_WIDTH_PX);
                Bitmap p2Bmp = preprocess(p1Bmp);
                Bitmap p3Bmp = cropCenter(source);

                String raw1 = runMlKitSync(p1Bmp);
                String raw2 = runMlKitSync(p2Bmp);
                String raw3 = runMlKitSync(p3Bmp);

                Log.d("OCR_PASS1", raw1);
                Log.d("OCR_PASS2", raw2);
                Log.d("OCR_PASS3", raw3);

                // Parse each pass independently
                MedicationData d1 = MedicationParser.parse(raw1, null);
                MedicationData d2 = MedicationParser.parse(raw2, null);
                MedicationData d3 = MedicationParser.parse(raw3, null);

                List<MedicationData> dataResults = new ArrayList<>();
                dataResults.add(d1);
                dataResults.add(d2);
                dataResults.add(d3);

                // Select Best Fields Independently (Optimization)
                String bestBrand = selectBestBrand(dataResults, raw1 + raw2 + raw3);
                String bestDrug = selectBestDrug(dataResults, raw1 + raw2 + raw3);
                String bestDosage = selectBestDosage(dataResults);
                String bestExpiry = selectBestExpiry(dataResults);

                // Context Validation
                if (!isConsistent(bestDrug, bestDosage)) {
                    Log.w(TAG, "Inconsistency detected! Falling back to best overall pass.");
                    MedicationData fallback = d1;
                    if (d2.drugName.length() > fallback.drugName.length()) fallback = d2;
                    if (d3.drugName.length() > fallback.drugName.length()) fallback = d3;
                    
                    bestDrug = fallback.drugName;
                    bestDosage = fallback.dosage;
                }

                // Handle Optional Fields
                if (bestDosage == null || bestDosage.isEmpty()) {
                    bestDosage = "Not detected";
                }
                if (bestExpiry == null || bestExpiry.isEmpty()) {
                    bestExpiry = "Not detected";
                }

                // Final Optimized Result with Hybrid Parallel Support
                MedicationData finalData = new MedicationData(bestBrand, bestDrug, bestDosage, bestExpiry);
                String fullRaw = raw1 + "\n" + raw2 + "\n" + raw3;
                Log.d("OCR_SYNTHESIZED", finalData.toString());

                boolean isWeak = !isValidDrugName(bestDrug);
                if (isWeak) {
                    Log.d("OCR_STATUS", "WARNING - Weak drug name detected, delegating to hybrid validator");
                } else {
                    Log.d("OCR_STATUS", "ACCEPTED - Valid name found");
                }
                callback.onResult(finalData, "multi-pass-optimizer", fullRaw, isWeak);

            } catch (Exception e) {
                Log.e(TAG, "Pipeline failure", e);
                callback.onError("OCR Error: " + e.getMessage());
            }
        });
    }

    private String selectBestBrand(List<MedicationData> results, String allRaw) {
        String best = "";
        int bestScore = -1;
        for (MedicationData d : results) {
            String brand = d.brandName;
            if (brand == null || brand.length() < 5) continue;
            int score = 0;
            if (brand.matches("^[A-Z]{5,}$")) score += 5;
            if (score > bestScore) {
                bestScore = score;
                best = brand;
            }
        }
        return best;
    }

    private String selectBestDrug(List<MedicationData> results, String allRaw) {
        String best = "";
        int bestScore = -1;
        String allRawUpper = allRaw.toUpperCase();

        for (MedicationData d : results) {
            String drug = d.drugName;
            if (drug == null || drug.length() < 3) continue;

            int score = 0;

            // Strong signals
            if (drug.matches("^[A-Z]{5,}$")) score += 5;
            if (drug.length() <= 15) score += 3;

            // Context boost (check for indicators in the full OCR source)
            if (allRawUpper.contains("MG")) score += 5;
            if (allRawUpper.contains("TABLET") || allRawUpper.contains("CAPSULE")) score += 5;

            // Penalize irrelevant pharmacy data misread as name
            if (drug.contains("AUTH") || drug.contains("ANYTOWN") || drug.contains("REFILL") || drug.contains("QTY")) {
                score -= 10;
            }

            if (score > bestScore) {
                bestScore = score;
                best = drug;
            }
        }
        return best;
    }

    private static final java.util.Set<String> IMPORTANT_DOSAGE_WORDS = new java.util.HashSet<>(java.util.Arrays.asList(
        "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten",
        "once", "twice", "thrice", "daily", "times", "hour", "hours", "day", "days", "needed"
    ));

    private String selectBestDosage(List<MedicationData> results) {
        if (results == null || results.isEmpty()) return "";

        java.util.Map<String, Integer> wordScores = new java.util.HashMap<>();
        MedicationData anchorPass = results.get(0);
        int maxScore = -999;

        for (MedicationData d : results) {
            if (d.dosage == null || d.dosage.isEmpty()) continue;
            
            // PRE-CLEAN: Fix systematic OCR errors before scoring/merging
            String text = d.dosage.toLowerCase()
                    .replace("the times", "three times")
                    .replace("oy", "by")
                    .replace("tabiet", "tablet");

            int passWeight = 1;
            if (text.contains("take")) passWeight += 2;
            if (text.contains("daily") || text.contains("times")) passWeight += 2;

            int currentScore = calculateDosageScore(d);
            if (currentScore > maxScore) {
                maxScore = currentScore;
                anchorPass = d;
            }

            String[] words = text.split("\\s+");
            for (String w : words) {
                if (w.length() < 2) continue;
                wordScores.put(w, wordScores.getOrDefault(w, 0) + passWeight);
            }
        }

        if (maxScore == -999) return "";

        // Reconstruct using Anchor Order, but protected by weighted scores
        StringBuilder merged = new StringBuilder();
        String anchorText = anchorPass.dosage.toLowerCase()
                .replace("the times", "three times")
                .replace("oy", "by");
        
        String[] anchorWords = anchorText.split("\\s+");
        
        for (String word : anchorWords) {
            String clean = word.toLowerCase();
            int score = wordScores.getOrDefault(clean, 0);

            // DECISION: Always keep IMPORTANT words, or words with high weighted confidence
            if (IMPORTANT_DOSAGE_WORDS.contains(clean) || score >= 2 || clean.equals("take")) {
                merged.append(word).append(" ");
            }
        }

        String finalDosage = merged.toString().trim()
                .replace("oone", "one")
                .replace("  ", " ");

        if (finalDosage.isEmpty()) return "";
        return Character.toUpperCase(finalDosage.charAt(0)) + finalDosage.substring(1);
    }

    private int calculateDosageScore(MedicationData d) {
        String lower = d.dosage.toLowerCase();
        int score = 0;
        if (lower.contains("take")) score += 10;
        if (lower.contains("daily")) score += 5;
        if (lower.contains("times") || lower.contains("every")) score += 5;
        if (d.dosageLineCount >= 2) score += 10;
        if (!lower.contains("take")) score -= 10;
        return score;
    }

    private String selectBestExpiry(List<MedicationData> results) {
        String best = "";
        int bestScore = -1;
        for (MedicationData d : results) {
            String expiry = d.expiry;
            if (expiry == null || expiry.isEmpty()) continue;

            int score = 0;
            // Valid date format (MM/DD/YY or similar)
            if (expiry.matches(".*\\d{1,2}[/\\-]\\d{1,2}[/\\-]\\d{2,4}.*")) {
                score += 10;
            }

            if (expiry.length() < 6) score -= 5;

            if (score > bestScore) {
                bestScore = score;
                best = expiry;
            }
        }
        return best;
    }

    private boolean isConsistent(String name, String dosage) {
        if (name == null || dosage == null) return true;
        String n = name.toLowerCase();
        String d = dosage.toLowerCase();

        // Cross-field form validation
        if (d.contains("ml") && n.contains("TABLET")) return false;
        if (d.contains("capsule") && n.contains("SYRUP")) return false;
        
        return true;
    }

    // ─── Image Processing ─────────────────────────────────────────────────────

    private Bitmap preprocess(Bitmap src) {
        Bitmap bmp = src.copy(Bitmap.Config.ARGB_8888, true);
        // Optimized binarization logic
        for (int x = 0; x < bmp.getWidth(); x++) {
            for (int y = 0; y < bmp.getHeight(); y++) {
                int pixel = bmp.getPixel(x, y);
                int r = Color.red(pixel);
                int g = Color.green(pixel);
                int b = Color.blue(pixel);
                int gray = (r + g + b) / 3;
                int finalGray = gray > 128 ? 255 : 0; // Hard contrast
                bmp.setPixel(x, y, Color.rgb(finalGray, finalGray, finalGray));
            }
        }
        return bmp;
    }

    private Bitmap cropCenter(Bitmap bmp) {
        int w = bmp.getWidth();
        int h = bmp.getHeight();
        Bitmap cropped = Bitmap.createBitmap(bmp, w / 6, h / 6, w * 2 / 3, h * 2 / 3);
        return resize(cropped, MAX_WIDTH_PX);
    }

    private Bitmap resize(Bitmap original, int maxWidth) {
        int w = original.getWidth();
        int h = original.getHeight();
        if (w <= maxWidth) return original;
        float scale = (float) maxWidth / w;
        Matrix m = new Matrix();
        m.postScale(scale, scale);
        return Bitmap.createBitmap(original, 0, 0, w, h, m, true);
    }

    // ─── OCR Engines ──────────────────────────────────────────────────────────

    private String runMlKitSync(Bitmap bitmap) {
        InputImage image = InputImage.fromBitmap(bitmap, 0);
        TextRecognizer recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> result = new AtomicReference<>("");

        recognizer.process(image)
                .addOnSuccessListener(visionText -> {
                    result.set(visionText.getText());
                    latch.countDown();
                })
                .addOnFailureListener(e -> latch.countDown());

        try { latch.await(5, TimeUnit.SECONDS); } catch (Exception ignored) {}
        return result.get();
    }

    private String runTesseract(Bitmap bitmap) {
        try {
            File tessDir = ensureTessData();
            if (tessDir == null) return "";

            com.googlecode.tesseract.android.TessBaseAPI tess = new com.googlecode.tesseract.android.TessBaseAPI();
            if (!tess.init(tessDir.getAbsolutePath(), TESS_LANG)) {
                tess.end();
                return "";
            }
            tess.setImage(bitmap);
            String text = tess.getUTF8Text();
            tess.end();
            return text != null ? text : "";
        } catch (Exception e) {
            return "";
        }
    }

    // ─── Text Cleaning ────────────────────────────────────────────────────────

    private String cleanOcrText(String text) {
        if (text == null) return "";
        String clean = text.toUpperCase();

        // Fix systematic errors
        clean = clean.replace('0', 'O');
        clean = clean.replace('1', 'I');
        clean = clean.replace("TABIET", "TABLET");
        clean = clean.replace("TABIETS", "TABLETS");
        clean = clean.replace("IAPAP", "/APAP");

        // Character clusters that look like garbage
        clean = clean.replaceAll("[§¥¢ﬁ]{2,}", " ");

        // Normalize spacing
        return clean.replaceAll("\\s+", " ").trim();
    }

    private boolean isValidDrugName(String drug) {
        if (drug == null || drug.isEmpty()) return false;
        
        // Allow combination drugs with special separators
        if (drug.contains("/") || drug.contains("+")) return true;

        // Mandatory: Must be at least 3 letters (e.g. B12, Zin)
        if (drug.length() < 3) return false;
        if (drug.length() > 20) return false;

        return true;
    }

    // ─── Asset Management ─────────────────────────────────────────────────────

    private File ensureTessData() {
        try {
            File dataDir = context.getFilesDir();
            File tessDataDir = new File(dataDir, TESS_DATA_DIR);
            File trainedData = new File(tessDataDir, TESS_LANG + ".traineddata");

            if (trainedData.exists()) return dataDir;

            if (!tessDataDir.exists() && !tessDataDir.mkdirs()) return null;

            java.io.InputStream is = context.getAssets().open(TESS_DATA_DIR + "/" + TESS_LANG + ".traineddata");
            java.io.FileOutputStream fos = new java.io.FileOutputStream(trainedData);
            byte[] buf = new byte[8192];
            int len;
            while ((len = is.read(buf)) != -1) fos.write(buf, 0, len);
            fos.close();
            is.close();

            return dataDir;
        } catch (Exception e) {
            return null;
        }
    }
}
