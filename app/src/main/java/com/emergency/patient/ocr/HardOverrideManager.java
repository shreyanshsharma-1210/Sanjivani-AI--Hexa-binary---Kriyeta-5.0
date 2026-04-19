package com.emergency.patient.ocr;

import android.content.Context;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

/**
 * HardOverrideManager
 * 
 * Implements a Pattern-Based Hard Override Mode that short-circuits the OCR pipeline 
 * when a high-confidence pattern match (≥ 0.75) is detected for specific medicines.
 */
public class HardOverrideManager {
    private static final String TAG = "HardOverrideManager";
    private static List<OverrideProfile> profiles = null;

    private static class OverrideProfile {
        String id;
        List<String> keywords;
        double threshold;
        String brand;
        String drug;
        String dosage;
        String expiry;

        OverrideProfile(String id, List<String> keywords, double threshold, String brand, String drug, String dosage, String expiry) {
            this.id = id;
            this.keywords = keywords;
            this.threshold = threshold;
            this.brand = brand;
            this.drug = drug;
            this.dosage = dosage;
            this.expiry = expiry;
        }
    }

    /**
     * Checks if the raw text matches any hard override profile.
     * Returns MedicationData if a match is found (≥ 0.75 score AND ≥ 2 keyword matches), else null.
     */
    public static MedicationData checkOverride(Context context, String rawText) {
        if (profiles == null) {
            loadProfiles(context);
        }

        if (rawText == null || rawText.isEmpty()) return null;

        String input = rawText.toLowerCase();
        
        for (OverrideProfile profile : profiles) {
            int keywordMatches = 0;
            double totalScore = 0;

            for (String kw : profile.keywords) {
                String kwLower = kw.toLowerCase();
                double bestSim = 0;

                // 1. Check exact/substring match
                if (input.contains(kwLower)) {
                    bestSim = 1.0;
                    keywordMatches++;
                } else {
                    // 2. Fuzzy match against tokens
                    String[] words = input.split("[\\s/\\-,]+");
                    for (String word : words) {
                        if (word.length() < 3) continue;
                        double sim = DrugNormalizer.similarity(word, kwLower);
                        // Also check if keyword contains multiple words (like "hk vitals")
                        if (kwLower.contains(" ")) {
                            // Simple heuristic: if any part of the keyword is similar to the word
                            for (String kwPart : kwLower.split(" ")) {
                                double partSim = DrugNormalizer.similarity(word, kwPart);
                                if (partSim > sim) sim = partSim;
                            }
                        }
                        
                        if (sim > bestSim) {
                            bestSim = sim;
                        }
                    }
                    if (bestSim >= 0.75) {
                        keywordMatches++;
                    }
                }
                totalScore += bestSim;
            }

            double finalScore = totalScore / profile.keywords.size();

            // Trigger criteria: Score >= threshold AND at least 2 keywords (Step 9)
            if (finalScore >= profile.threshold && keywordMatches >= 2) {
                Log.d(TAG, "[HARD_OVERRIDE_TRIGGERED]");
                Log.d(TAG, "Profile: " + profile.id);
                Log.d(TAG, "Score: " + String.format("%.2f", finalScore));
                Log.d(TAG, "Pipeline Skipped: YES");

                MedicationData data = new MedicationData(profile.brand, profile.drug, profile.dosage, profile.expiry);
                data.confidenceLevel = "HIGH";
                data.provenance = "hard_override";
                return data;
            }
        }

        return null;
    }

    private static void loadProfiles(Context context) {
        profiles = new ArrayList<>();
        try {
            InputStream is = context.getAssets().open("hard_override.json");
            BufferedReader reader = new BufferedReader(new InputStreamReader(is));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            JSONArray arr = new JSONArray(sb.toString());
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                JSONArray kwArr = obj.getJSONArray("keywords");
                List<String> keywords = new ArrayList<>();
                for (int j = 0; j < kwArr.length(); j++) {
                    keywords.add(kwArr.getString(j));
                }
                JSONObject data = obj.getJSONObject("data");
                profiles.add(new OverrideProfile(
                    obj.getString("id"),
                    keywords,
                    obj.getDouble("threshold"),
                    data.getString("brand_name"),
                    data.getString("drug_name"),
                    data.getString("dosage"),
                    data.getString("expiry_date")
                ));
            }
            Log.d(TAG, "Loaded " + profiles.size() + " override profiles.");
        } catch (Exception e) {
            Log.e(TAG, "Failed to load hard_override.json", e);
        }
    }
}
