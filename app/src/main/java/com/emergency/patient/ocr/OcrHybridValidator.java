package com.emergency.patient.ocr;

import android.content.Context;
import android.util.Log;

import com.emergency.patient.db.AppDatabaseProvider;
import com.emergency.patient.db.OcrCorrectionDao;
import com.emergency.patient.db.OcrCorrectionEntity;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class OcrHybridValidator {

    private static final String TAG = "OcrHybridValidator";
    private static final Set<String> MARKETING_WORDS = new HashSet<>(Arrays.asList(
            "RESEARCH", "INNOVATIONS", "ENERGY", "IMMUNITY", "DRIVEN",
            "POWER", "ADVANCE", "PLUS", "FORTE"
    ));

    // Known drug classes that receive a hard +30 boost (Step 5)
    private static final Set<String> DRUG_CLASS_BOOSTED = new HashSet<>(Arrays.asList(
            "MULTIVITAMIN", "PARACETAMOL", "IBUPROFEN", "AMOXICILLIN"
    ));

    public static MedicationData runHybridPipeline(Context context, String rawText, MedicationData parserData) {
        
        // 1. Mandatory Grounding: Fuzzy normalization and ranked candidates
        List<DrugNormalizer.Candidate> candidates = DrugNormalizer.getRankedCandidates(context, rawText);
        Set<String> fuzzyCandidates = new HashSet<>();
        Map<String, Double> candidateConfidences = new HashMap<>();
        
        for (DrugNormalizer.Candidate c : candidates) {
            fuzzyCandidates.add(c.name.toUpperCase());
            candidateConfidences.put(c.name.toUpperCase(), c.confidence);
        }
        
        // Inject fragments as fallback
        fuzzyCandidates.addAll(OcrValidator.reconstructFragmentedDrugs(rawText));

        // 🧠 STEP 2: HARD OVERRIDE CHECK (Short-circuit)
        MedicationData override = HardOverrideManager.checkOverride(context, rawText);
        if (override != null) {
            override.fuzzyCandidates.addAll(fuzzyCandidates);
            override.normalizedText = rawText;
            return override; // Instant return, skip Parser, Gemini, Groq, and Validator
        }
        
        // 2. User Learning: Suggest corrections from previous sessions
        List<OcrCorrectionEntity> history = new ArrayList<>();
        try {
            OcrCorrectionDao dao = AppDatabaseProvider.getInstance(context).ocrCorrectionDao();
            history = dao.getAll();
        } catch (Exception e) {
            Log.e(TAG, "Failed to load user corrections", e);
        }
        
        for (OcrCorrectionEntity c : history) {
            if (rawText.toUpperCase().contains(c.wrongText.toUpperCase())) {
                fuzzyCandidates.add(c.correctText.toUpperCase());
                candidateConfidences.put(c.correctText.toUpperCase(), 0.9); // High confidence boost for learned data
                Log.d(TAG, "[UserLearning] Found past correction: " + c.wrongText + " -> " + c.correctText);
            }
        }
        
        // 2. Pattern Knowledge Injection (New Layer)
        List<DrugNormalizer.PatternKnowledge> matchedPatterns = DrugNormalizer.detectMatchedPatterns(context, rawText);
        for (DrugNormalizer.PatternKnowledge p : matchedPatterns) {
            // Inject Brand/Drug as HIGH confidence grounded candidates
            fuzzyCandidates.add(p.brandName.toUpperCase());
            fuzzyCandidates.add(p.drugName.toUpperCase());
            
            // Map the pattern boost to the confidence map
            candidateConfidences.put(p.brandName.toUpperCase(), (double) p.confidenceBoost / 100.0);
            candidateConfidences.put(p.drugName.toUpperCase(), (double) p.confidenceBoost / 100.0);
            
            Log.d(TAG, "[PATTERN_INJECTION] Injected hints for: " + p.id);
        }

        // ─── NEW: Pattern Dominance Mode Detection (Step 1 & 4) ────────────
        boolean dominanceMode = false;
        DrugNormalizer.PatternKnowledge dominantPattern = null;
        List<DrugNormalizer.PatternKnowledge> strongPatterns = new ArrayList<>();
        
        for (DrugNormalizer.PatternKnowledge p : matchedPatterns) {
            if (p.matchRatio >= 0.75) {
                strongPatterns.add(p);
            }
        }

        if (!strongPatterns.isEmpty()) {
            // Check for multi-pattern conflict (different brand or drug)
            boolean conflict = false;
            dominantPattern = strongPatterns.get(0);
            for (int i = 1; i < strongPatterns.size(); i++) {
                DrugNormalizer.PatternKnowledge other = strongPatterns.get(i);
                if (!dominantPattern.brandName.equalsIgnoreCase(other.brandName) ||
                    !dominantPattern.drugName.equalsIgnoreCase(other.drugName)) {
                    conflict = true;
                    break;
                }
            }

            if (conflict) {
                Log.w(TAG, "[DOMINANCE_CONFLICT] Multiple conflicting patterns found. Disabling dominance.");
                dominantPattern = null;
            } else {
                dominanceMode = true;
                Log.d(TAG, "[DOMINANCE_MODE] Activated for: " + dominantPattern.id);
            }
        }

        Log.d(TAG, "[FUZZY_CANDIDATES] Detected: " + fuzzyCandidates.toString());

        // 3. Parallel Interpretation (Using Grounded Input)
        final EngineResult[] nanoRes = new EngineResult[1];
        final EngineResult[] groqRes = new EngineResult[1];

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch latch = new CountDownLatch(2);

        executor.execute(() -> {
            try {
                nanoRes[0] = GeminiNanoInterpreter.extractWithTimeout(context, rawText, fuzzyCandidates, 3);
            } catch (Exception e) {
                Log.e(TAG, "Nano failed", e);
            } finally {
                latch.countDown();
            }
        });

        executor.execute(() -> {
            try {
                groqRes[0] = GroqInterpreter.extractWithTimeout(rawText, fuzzyCandidates, 5);
            } catch (Exception e) {
                Log.e(TAG, "[GROQ_ERROR] Groq executor crashed unexpectedly", e);
            } finally {
                latch.countDown();
            }
        });

        // Re-parse grounded parser data
        MedicationData groundedParserData = MedicationParser.parse(rawText, fuzzyCandidates);
        EngineResult parserRes = new EngineResult(groundedParserData.brandName, groundedParserData.drugName, 
                groundedParserData.dosage, groundedParserData.expiry, 85, "parser");
        parserRes.isGrounded = true;

        try {
            // Wait up to 8s — Groq has a 5s read timeout + network overhead budget
            latch.await(8, java.util.concurrent.TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Log.e(TAG, "[GROQ_ERROR] Parallel engine latch interrupted", e);
        }
        
        List<EngineResult> allResults = new ArrayList<>();
        allResults.add(parserRes);
        if (nanoRes[0] != null) allResults.add(nanoRes[0]);
        if (groqRes[0] != null) allResults.add(groqRes[0]);

        // Step 5: Validator Integration Check
        Log.d(TAG, "[VALIDATOR_INPUT]");
        Log.d(TAG, "Parser: " + (parserRes != null ? parserRes.drug_name : "NULL"));
        Log.d(TAG, "Gemini: " + (nanoRes[0] != null ? nanoRes[0].drug_name : "NULL"));
        Log.d(TAG, "Groq:   " + (groqRes[0] != null ? groqRes[0].drug_name : "NULL"));

        MedicationData finalData = selectBestOutput(allResults, fuzzyCandidates, candidateConfidences, matchedPatterns, rawText, dominanceMode, dominantPattern);
        finalData.fuzzyCandidates.addAll(fuzzyCandidates);
        finalData.normalizedText = rawText;
        return finalData;
    }

    private static MedicationData selectBestOutput(List<EngineResult> results, Set<String> fuzzyCandidates, Map<String, Double> confidences, List<DrugNormalizer.PatternKnowledge> matchedPatterns, String rawText, boolean dominanceMode, DrugNormalizer.PatternKnowledge dominantPattern) {
        String bestBrand = scoreAndSelectField(results, "brand_name", fuzzyCandidates, confidences, matchedPatterns, rawText, dominanceMode, dominantPattern);
        String bestDrug = scoreAndSelectField(results, "drug_name", fuzzyCandidates, confidences, matchedPatterns, rawText, dominanceMode, dominantPattern);
        String bestDosage = scoreAndSelectField(results, "dosage", fuzzyCandidates, confidences, matchedPatterns, rawText, dominanceMode, dominantPattern);
        String bestExpiry = scoreAndSelectField(results, "expiry_date", fuzzyCandidates, confidences, matchedPatterns, rawText, dominanceMode, dominantPattern);
        
        // Pattern Dosage Hint Fallback (Step 6)
        if (bestDosage.isEmpty() || bestDosage.equalsIgnoreCase("NOT_DECLARED")) {
            for (DrugNormalizer.PatternKnowledge p : matchedPatterns) {
                if (p.dosageHint != null && !p.dosageHint.isEmpty()) {
                    bestDosage = p.dosageHint;
                    Log.d(TAG, "[PATTERN_DOSAGE] Fallback applied: " + bestDosage);
                    break;
                }
            }
        }
        
        // HARD CONSTRAINTS
        if (bestBrand.equalsIgnoreCase(bestDrug)) {
            bestBrand = ""; // brand_name != drug_name
        }

        // Map Confidence Grading (Step 8)
        int minScore = 0;
        int agreementCount = 0;
        String finalConfidence = "LOW";
        String finalSource = "unknown";
        int topEngineScore = -999;

        for (EngineResult res : results) {
            if (res.drug_name != null && res.drug_name.equalsIgnoreCase(bestDrug)) {
                agreementCount++;
                if (res.confidence > topEngineScore) {
                    topEngineScore = res.confidence;
                    finalSource = res.source;
                }
            }
        }

        if (agreementCount >= 3) minScore = 85; 
        else if (agreementCount == 2) minScore = 60;
        else minScore = 30;

        if (minScore >= 80) finalConfidence = "HIGH";
        else if (minScore >= 50) finalConfidence = "MEDIUM";
        else finalConfidence = "LOW";

        // Step 3: Confidence Adjustment for Dominance
        if (dominanceMode && finalConfidence.equals("LOW")) {
            // If dominance triggered, result should be at least MEDIUM if winner is a pattern match
            boolean winnerIsPattern = false;
            if (dominantPattern != null && (bestDrug.equalsIgnoreCase(dominantPattern.drugName) || bestBrand.equalsIgnoreCase(dominantPattern.brandName))) {
                winnerIsPattern = true;
            }
            if (winnerIsPattern) {
                finalConfidence = "MEDIUM";
                Log.d(TAG, "[DOMINANCE_CONFIDENCE] Upgraded LOW to MEDIUM due to pattern match dominance.");
            }
        }

        MedicationData finalData = new MedicationData(
                bestBrand.isEmpty() ? "NOT_DECLARED" : bestBrand,
                bestDrug.isEmpty() ? "NOT_DECLARED" : bestDrug,
                bestDosage.isEmpty() ? "NOT_DECLARED" : bestDosage,
                bestExpiry.isEmpty() ? "NOT_DECLARED" : bestExpiry
        );
        finalData.confidenceLevel = finalConfidence;
        finalData.provenance = finalSource;

        Log.d(TAG, "[FINAL_SELECTION] Brand: " + finalData.brandName);
        Log.d(TAG, "[FINAL_SELECTION] Drug: " + finalData.drugName);
        Log.d(TAG, "[FINAL_SELECTION] Dosage: " + finalData.dosage);
        Log.d(TAG, "[FINAL_SELECTION] Expiry: " + finalData.expiry);
        Log.d(TAG, "[FINAL_SELECTION] Confidence Grade: " + finalConfidence);

        return finalData;
    }

    private static String scoreAndSelectField(List<EngineResult> results, String field, Set<String> fuzzyCandidates, Map<String, Double> confidences, List<DrugNormalizer.PatternKnowledge> matchedPatterns, String rawText, boolean dominanceMode, DrugNormalizer.PatternKnowledge dominantPattern) {
        // Ranked list: (score, candidateRaw) — used to enforce final guard rule (Step 6)
        List<int[]> scoreList = new ArrayList<>();
        List<String> rawList = new ArrayList<>();
        String rawUpper = rawText.toUpperCase();

        for (EngineResult res : results) {
            String candidateRaw = getField(res, field);
            if (candidateRaw == null || candidateRaw.trim().isEmpty() || candidateRaw.equalsIgnoreCase("NOT_DECLARED") || candidateRaw.equalsIgnoreCase("NULL")) {
                continue;
            }

            String candidate = candidateRaw.trim().toUpperCase();
            int score = 0;

            // ─── STEP 1: NEGATIVE FILTER — Marketing word hard reject ────────────
            if (isMarketingWord(candidate)) {
                score = -100;
                res.confidence = score;
                Log.w(TAG, "[NegativeFilter] Hard-rejected marketing word: " + candidate);
                scoreList.add(new int[]{score});
                rawList.add(candidateRaw);
                continue; // Do NOT let this candidate proceed further in scoring
            }

            // 1. Base Score
            if (res.source.equals("parser")) score += 40;
            else if (res.source.equals("nano")) score += 30;
            else if (res.source.equals("groq")) score += 30;

            // 2. Multi-Engine Agreement Boost (+25)
            int agreementCount = 0;
            for (EngineResult other : results) {
                String otherVal = getField(other, field);
                if (otherVal != null && otherVal.toUpperCase().equals(candidate)) {
                    agreementCount++;
                }
            }
            if (agreementCount >= 2) score += 25;

            // ─── STEP 4: MULTI-WORD PRIORITY BOOST (+20) ────────────────────────
            if (candidateRaw.trim().contains(" ")) {
                score += 20;
                Log.d(TAG, "[MultiWordBoost] +20 for multi-word candidate: " + candidate);
            }

            // ─── STEP 5: FULL DRUG CLASS BOOST (+30) ────────────────────────────
            if ((field.equals("drug_name") || field.equals("brand_name")) && DRUG_CLASS_BOOSTED.contains(candidate)) {
                score += 30;
                Log.d(TAG, "[DrugClassBoost] +30 applied to: " + candidate);
            }

            // 3. Grounding Boost (+20 per rank signal)
            if (fuzzyCandidates.contains(candidate)) {
                score += 20;
                Double conf = confidences.get(candidate);

                // Pattern Knowledge Boost
                boolean matchedAnyPattern = false;
                for (DrugNormalizer.PatternKnowledge p : matchedPatterns) {
                    if (candidate.equalsIgnoreCase(p.brandName) || candidate.equalsIgnoreCase(p.drugName)) {
                        score += p.confidenceBoost;
                        matchedAnyPattern = true;
                        Log.d(TAG, "[PATTERN_BOOST] Applied +" + p.confidenceBoost + " to candidate: " + candidate);
                        break;
                    }
                }

                if (!matchedAnyPattern && conf != null && conf > 0.9) score += 30; // learned/override boost

                // ─── STEP 2: PATTERN MATCH BOOST (+70 fuzzy, +20 if in roster) ──
                score += 70;
                Log.d(TAG, "[PatternMatchBoost] +70 fuzzy match boost for: " + candidate);
                // Already confirmed in fuzzy roster (+20)
                score += 20;
                Log.d(TAG, "[PatternMatchBoost] +20 fuzzy roster confirmation for: " + candidate);

                // ─── NEW: DOMINANCE BOOST (+100) (Step 2) ───────────────────
                if (dominanceMode && dominantPattern != null) {
                    if (candidate.equalsIgnoreCase(dominantPattern.brandName) || candidate.equalsIgnoreCase(dominantPattern.drugName)) {
                        score += 100;
                        Log.d(TAG, "[DOMINANCE_BOOST] +100 for dominant pattern match: " + candidate);
                    }
                }
            } else {
                // Dominance Penalty for non-pattern candidates (Step 2)
                if (dominanceMode && dominantPattern != null) {
                    score -= 30;
                    Log.d(TAG, "[DOMINANCE_PENALTY] -30 for non-pattern candidate in dominance mode: " + candidate);
                }
            }

            // Pattern Agreement Boost
            for (DrugNormalizer.PatternKnowledge p : matchedPatterns) {
                if (candidate.equalsIgnoreCase(p.brandName) || candidate.equalsIgnoreCase(p.drugName)) {
                    if (agreementCount >= 2) score += 15;
                }
            }

            // Dosage / Expiry format boosts
            if (field.equals("dosage") && isValidDosagePattern(candidate)) score += 20;
            if (field.equals("expiry_date") && isValidDateFormat(candidate)) score += 20;

            // Context keyword boost
            if (isMedicalKeyword(candidate)) score += 10;

            // ─── STEP 3: PARSER NOISE PENALTY (-30) ──────────────────────────────
            // Apply -30 if not grounded in fuzzy list and not medically relevant
            boolean inFuzzyRoster = fuzzyCandidates != null && fuzzyCandidates.contains(candidate);
            boolean isMedicallyRelevant = isMedicalKeyword(candidate) ||
                    candidate.contains("VITAMIN") || candidate.contains("MULTI") ||
                    candidate.contains("TABLET") || candidate.contains("CAPSULE");
            if (!inFuzzyRoster && !isMedicallyRelevant) {
                score -= 30;
                Log.d(TAG, "[NoisePenalty] -30 parser noise penalty for: " + candidate);
            }

            // Marketing penalty for brand field (secondary, after hard-reject)
            if (field.equals("brand_name") && isMarketingWord(candidate)) score -= 30;
            if (agreementCount == 1) score -= 15;

            // GROUNDING PENALTY (-25)
            if ((field.equals("drug_name") || field.equals("brand_name")) && !res.isGrounded) {
                if (!inFuzzyRoster) {
                    score -= 25;
                    Log.d(TAG, "[SCORING_PENALTY] Non-grounded selection: " + candidate);
                }
            }

            if (!rawUpper.contains(candidate) && !fuzzyCandidates.contains(candidate)) score -= 10;

            res.confidence = score;
            scoreList.add(new int[]{score});
            rawList.add(candidateRaw);

            Log.d(TAG, "[SCORING] Field: " + field + " | Engine: " + res.source + " | Candidate: '" + candidateRaw + "' | Score: " + score);
        }

        // ─── STEP 6: FINAL GUARD — skip marketing-word winner, pick next best ──
        // Build sorted result list
        String bestCandidate = "";
        int highestScore = -999;
        for (int i = 0; i < rawList.size(); i++) {
            String cRaw = rawList.get(i);
            int s = scoreList.get(i)[0];
            String cUp = cRaw.trim().toUpperCase();
            // Skip marketing words as final selection
            if (isMarketingWord(cUp)) {
                Log.w(TAG, "[FinalGuard] Skipping marketing-word winner: " + cUp);
                continue;
            }
            if (s > highestScore) {
                highestScore = s;
                bestCandidate = cRaw;
            }
        }

        Log.d(TAG, "[WINNER] Field: " + field + " -> Selected: '" + bestCandidate + "' with Score: " + highestScore);
        return bestCandidate;
    }

    private static String getField(EngineResult res, String field) {
        switch(field) {
            case "brand_name": return res.brand_name;
            case "drug_name": return res.drug_name;
            case "dosage": return res.dosage;
            case "expiry_date": return res.expiry_date;
        }
        return "";
    }

    private static boolean isMedicalKeyword(String text) {
        return text.contains("VITAMIN") || text.contains("MULTI") || text.contains("TABLET") || text.contains("MG") || text.contains("ML");
    }

    private static boolean isMarketingWord(String text) {
        for (String w : MARKETING_WORDS) {
            if (text.contains(w)) return true;
        }
        return false;
    }

    private static boolean isValidDosagePattern(String text) {
        return text.matches(".*\\d+\\s*(MG|ML|MCG|G|IU).*");
    }

    private static boolean isValidDateFormat(String text) {
        return text.matches(".*\\d{2,4}[/-]\\d{2,4}.*");
    }
}
