package com.emergency.patient.ocr;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.Log;

import com.emergency.patient.db.AppDatabaseProvider;
import com.emergency.patient.db.OcrCorrectionEntity;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

public class OcrFusionEngine {

    static {
        if (!OpenCVLoader.initDebug()) {
            Log.e("OpenCV", "Initialization failed");
        } else {
            Log.d("OpenCV", "Initialized successfully");
        }
    }

    public static class ImageAnalysis {
        public String text;
        public int brandScore;
        public int drugScore;
        public int dosageScore;
        public int expiryScore;
    }

    public static class FusionResult {
        public MedicationData data;
        public int brandConfidence;
        public int drugConfidence;
        public int dosageConfidence;
        public int expiryConfidence;
        public String sourceExplanation;
        public String rawFlattenedText;
    }

    public static FusionResult fuseImages(Context context, List<Bitmap> images) {
        List<String> rawTexts = new ArrayList<>();
        
        // 1. Process all images (4-Pass Extraction per image)
        for (Bitmap bmp : images) {
            Bitmap gray = ImageHelper.toGrayscale(bmp);
            Bitmap contrast = ImageHelper.increaseContrast(gray);
            Bitmap cropped = ImageHelper.cropCenter(bmp);
            Bitmap opencvProcessed = preprocessWithOpenCv(bmp);

            String baseOcr = applyCorrections(context, runMlKitSync(bmp));
            Log.d("OcrFusionEngine", "Raw OCR (Original): " + baseOcr);
            rawTexts.add(baseOcr);

            if (gray != null) {
                String grayOcr = applyCorrections(context, runMlKitSync(gray));
                Log.d("OcrFusionEngine", "Raw OCR (Gray): " + grayOcr);
                rawTexts.add(grayOcr);
            }
            if (contrast != null) {
                String contrastOcr = applyCorrections(context, runMlKitSync(contrast));
                Log.d("OcrFusionEngine", "Raw OCR (Contrast): " + contrastOcr);
                rawTexts.add(contrastOcr);
            }
            if (cropped != null) {
                String croppedOcr = applyCorrections(context, runMlKitSync(cropped));
                Log.d("OcrFusionEngine", "Raw OCR (Cropped): " + croppedOcr);
                rawTexts.add(croppedOcr);
            }
            if (opencvProcessed != null) {
                String opencvOcr = applyCorrections(context, runMlKitSync(opencvProcessed));
                Log.d("OcrFusionEngine", "Raw OCR (OpenCV): " + opencvOcr);
                rawTexts.add(opencvOcr);
            }
        }

        // 2. Score them block by block
        List<ImageAnalysis> analyses = new ArrayList<>();
        for (String text : rawTexts) {
            ImageAnalysis a = new ImageAnalysis();
            a.text = text;
            a.brandScore = scoreBrand(text);
            a.drugScore = scoreDrug(text);
            a.dosageScore = scoreDosage(text);
            a.expiryScore = scoreExpiry(text);
            analyses.add(a);
        }

        // 3. Find Best per field
        ImageAnalysis bestBrand = null;
        ImageAnalysis bestDrug = null;
        ImageAnalysis bestDosage = null;
        ImageAnalysis bestExpiry = null;

        for (ImageAnalysis a : analyses) {
            if (bestBrand == null || a.brandScore > bestBrand.brandScore) bestBrand = a;
            if (bestDrug == null || a.drugScore > bestDrug.drugScore) bestDrug = a;
            if (bestDosage == null || a.dosageScore > bestDosage.dosageScore) bestDosage = a;
            if (bestExpiry == null || a.expiryScore > bestExpiry.expiryScore) bestExpiry = a;
        }

        // 4. Extract
        String finalBrand = "", finalDrug = "", finalDosage = "", finalExpiry = "";

        if (bestBrand != null && bestBrand.text != null) {
            finalBrand = MedicationParser.parse(bestBrand.text, null).brandName;
        }
        if (bestDrug != null && bestDrug.text != null) {
            finalDrug = MedicationParser.parse(bestDrug.text, null).drugName;
        }
        if (bestDosage != null && bestDosage.text != null) {
            finalDosage = MedicationParser.parse(bestDosage.text, null).dosage;
        }
        if (bestExpiry != null && bestExpiry.text != null) {
            finalExpiry = MedicationParser.parse(bestExpiry.text, null).expiry;
        }

        // Fallback Drug/Brand Merge
        if (finalDrug == null || finalDrug.isEmpty()) {
            Map<String, Integer> freq = new HashMap<>();
            for (String t : rawTexts) {
                String d = MedicationParser.parse(t, null).drugName;
                if (d != null && !d.isEmpty()) freq.put(d, freq.getOrDefault(d, 0) + 1);
            }
            int max = 0;
            for (Map.Entry<String, Integer> e : freq.entrySet()) {
                if (e.getValue() > max) {
                    max = e.getValue();
                    finalDrug = e.getKey();
                }
            }
        }
        if (finalBrand == null || finalBrand.isEmpty()) {
            Map<String, Integer> freq = new HashMap<>();
            for (String t : rawTexts) {
                String b = MedicationParser.parse(t, null).brandName;
                if (b != null && !b.isEmpty()) freq.put(b, freq.getOrDefault(b, 0) + 1);
            }
            int max = 0;
            for (Map.Entry<String, Integer> e : freq.entrySet()) {
                if (e.getValue() > max) {
                    max = e.getValue();
                    finalBrand = e.getKey();
                }
            }
        }

        // Apply Dictionary Reconstruction for Drug
        if (finalDrug != null && !finalDrug.isEmpty()) {
            finalDrug = DrugNormalizer.findBestMatch(context, finalDrug);
        }

        int drugFreq = 0, brandFreq = 0;
        for (String t : rawTexts) {
            MedicationData d = MedicationParser.parse(t, null);
            if (finalDrug != null && finalDrug.equals(d.drugName)) drugFreq++;
            if (finalBrand != null && finalBrand.equals(d.brandName)) brandFreq++;
        }

        FusionResult res = new FusionResult();
        res.data = new MedicationData(finalBrand, finalDrug, finalDosage, finalExpiry);
        res.brandConfidence = calculateBrandConfidence(finalBrand, brandFreq);
        res.drugConfidence = calculateDrugConfidence(finalDrug, drugFreq);
        res.dosageConfidence = calculateDosageConfidence(finalDosage);
        res.expiryConfidence = calculateExpiryConfidence(finalExpiry);
        res.sourceExplanation = "Fused from " + images.size() + " images";
        
        StringBuilder flatTextBuilder = new StringBuilder();
        for (String text : rawTexts) {
            flatTextBuilder.append(text).append(" ");
        }
        res.rawFlattenedText = flatTextBuilder.toString().trim();
        
        Log.d("OcrFusionEngine", "Fusion Final Result:");
        Log.d("OcrFusionEngine", " - Brand: " + finalBrand + " (Conf: " + res.brandConfidence + "%)");
        Log.d("OcrFusionEngine", " - Drug: " + finalDrug + " (Conf: " + res.drugConfidence + "%)");
        Log.d("OcrFusionEngine", " - Dosage: " + finalDosage + " (Conf: " + res.dosageConfidence + "%)");
        Log.d("OcrFusionEngine", " - Expiry: " + finalExpiry + " (Conf: " + res.expiryConfidence + "%)");

        return res;
    }

    private static Bitmap preprocessWithOpenCv(Bitmap bitmap) {
        try {
            Mat mat = new Mat();
            Utils.bitmapToMat(bitmap, mat);

            // Convert to grayscale (Using RGBA2GRAY since Android Bitmap is RGBA)
            Imgproc.cvtColor(mat, mat, Imgproc.COLOR_RGBA2GRAY);

            // Apply Gaussian blur
            Imgproc.GaussianBlur(mat, mat, new Size(5, 5), 0);

            // Apply threshold (binarization)
            Imgproc.threshold(mat, mat, 0, 255, Imgproc.THRESH_BINARY + Imgproc.THRESH_OTSU);

            // Optional: sharpen
            Mat kernel = new Mat(3, 3, CvType.CV_32F);
            kernel.put(0, 0,
                    0, -1, 0,
                    -1, 5, -1,
                    0, -1, 0);

            Imgproc.filter2D(mat, mat, mat.depth(), kernel);

            Bitmap processedBitmap = Bitmap.createBitmap(mat.cols(), mat.rows(), Bitmap.Config.ARGB_8888);
            Utils.matToBitmap(mat, processedBitmap);
            return processedBitmap;
        } catch (Exception e) {
            Log.e("OcrFusionEngine", "OpenCV Preprocessing failed", e);
            return null;
        }
    }

    private static String applyCorrections(Context context, String text) {
        if (text == null) return "";
        try {
            List<OcrCorrectionEntity> corrections = AppDatabaseProvider.getInstance(context).ocrCorrectionDao().getAll();
            for (OcrCorrectionEntity c : corrections) {
                if (c.wrongText != null && c.wrongText.length() >= 3 && 
                    c.correctText != null && c.correctText.length() >= 3) {
                    text = text.replace(c.wrongText, c.correctText);
                }
            }
        } catch (Exception e) {
            Log.e("OcrFusionEngine", "Failed to apply corrections", e);
        }
        return text;
    }

    private static int scoreBrand(String text) {
        if (text == null) return 0;
        int score = 0;
        String upper = text.toUpperCase();
        // Brand logic: usually first few lines, uppercase
        if (upper.length() >= 5 && upper.length() <= 15) score += 5;
        if (upper.matches("^[A-Z]+$")) score += 5;
        // Brand usually doesn't have medical keywords
        if (upper.contains("MG") || upper.contains("TABLET")) score -= 10;
        return score;
    }

    private static int scoreDrug(String text) {
        if (text == null) return 0;
        int score = 0;
        String upper = text.toUpperCase();
        if (upper.contains("TABLET") || upper.contains("CAPSULE") || upper.contains("VITAMIN") || upper.contains("MULTI")) score += 10;
        if (upper.contains("MG") || upper.contains("ML")) score += 5;
        if (upper.matches(".*[A-Z]{6,}.*")) score += 5;
        return score;
    }

    private static int scoreDosage(String text) {
        int score = 0;
        if (text == null) return 0;
        String lower = text.toLowerCase();
        if (lower.contains("take")) score += 10;
        if (lower.contains("every")) score += 5;
        if (lower.contains("daily")) score += 5;
        if (lower.contains("hour")) score += 5;
        if (lower.length() > 100) score += 5;
        return score;
    }

    private static int scoreExpiry(String text) {
        int score = 0;
        if (text == null) return 0;
        if (text.matches(".*\\d{2}/\\d{2}/\\d{2,4}.*")) score += 15;
        if (text.matches(".*EXP.*")) score += 10;
        if (text.matches(".*\\d{2}-\\d{4}.*")) score += 10;
        return score;
    }

    private static int calculateBrandConfidence(String brand, int frequency) {
        if (brand == null || brand.isEmpty()) return 0;
        int score = 20; // Base
        if (frequency >= 2) score += 40;
        if (brand.length() >= 5) score += 20;
        return Math.min(score, 100);
    }

    private static int calculateDrugConfidence(String drug, int frequency) {
        if (drug == null || drug.isEmpty()) return 0;
        int score = 30; // Base
        if (frequency >= 2) score += 40;
        if (drug.length() >= 5) score += 20;
        return Math.min(score, 100);
    }

    private static int calculateDosageConfidence(String dosage) {
        int score = 0;
        if (dosage == null || dosage.isEmpty()) return 0;
        String lower = dosage.toLowerCase();
        if (lower.contains("take")) score += 30;
        if (lower.contains("every") || lower.contains("daily")) score += 30;
        if (dosage.length() > 20) score += 20;
        return Math.min(score, 100);
    }

    private static int calculateExpiryConfidence(String expiry) {
        if (expiry == null || expiry.isEmpty()) return 0;
        if (expiry.matches(".*\\d{1,2}[/\\-]\\d{1,2}[/\\-]\\d{2,4}.*")) return 90;
        return 30;
    }

    private static String runMlKitSync(Bitmap bitmap) {
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

        try { latch.await(10, TimeUnit.SECONDS); } catch (Exception ignored) {}
        return result.get();
    }
}
