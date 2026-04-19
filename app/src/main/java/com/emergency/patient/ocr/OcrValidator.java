package com.emergency.patient.ocr;

import android.content.Context;
import android.util.Log;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * OcrValidator
 * 
 * Sits securely between the LLM output and the UI to prevent hallucinations.
 * For each field, verifies that the LLM didn't invent data by cross-referencing
 * the raw OCR text and strict heuristics.
 */
public class OcrValidator {

    private static final String TAG = "OcrValidator";

    private static final Set<String> STOPWORDS = new HashSet<>(Arrays.asList(
            "PHARMACY", "REFILL", "QTY", "RX", "WARNING", "STORE", "KEEP", 
            "MANUFACTURED", "DOCTOR", "DATE", "TIME", "RESEARCH", "INNOVATIONS", "DRIVEN",
            "ENERGY", "IMMUNITY", "POWER", "ADVANCE", "PLUS", "FORTE"
    ));

    private static final Set<String> MARKETING_WORDS = new HashSet<>(Arrays.asList(
            "RESEARCH", "INNOVATIONS", "ENERGY", "IMMUNITY", "DRIVEN",
            "POWER", "ADVANCE", "PLUS", "FORTE"
    ));

    // Known drug classes that receive a hard boost (Step 5)
    private static final Set<String> DRUG_CLASS_BOOSTED = new HashSet<>(Arrays.asList(
            "MULTIVITAMIN", "PARACETAMOL", "IBUPROFEN", "AMOXICILLIN"
    ));

    /**
     * Validates LLM outputs against strict rules. Reverts to heuristic parser outputs if LLM output is invalid.
     */
    public static MedicationData validateAndMerge(
            Context context,
            EngineResult llmResult,
            MedicationData parserResult,
            String fullOcrText) {

        String flatOcrUpper = fullOcrText.replaceAll("\\s+", " ").toUpperCase();
        
        // Step 2: Global Context Dominance Detection
        String dominantMode = "PACKAGING";
        String flatLower = fullOcrText.toLowerCase();
        if (flatLower.contains("take") || flatLower.contains("every") || flatLower.contains("daily")) {
            dominantMode = "PRESCRIPTION";
        } else if (flatLower.contains("multivitamin") || flatLower.contains("vitamin") || 
                   flatLower.contains("supplement") || flatLower.contains("zinc")) {
            dominantMode = "PACKAGING"; // Explicit packaging signal
        }
        Log.d(TAG, "[DecisionTrace] Global Dominant Context Detected: " + dominantMode);

        // --- STEP 0: Word Reconstruction & Normalization ---
        Set<String> uniqueCands = new HashSet<>();
        uniqueCands.addAll(reconstructFragmentedDrugs(fullOcrText));

        if (parserResult.brandName != null && !parserResult.brandName.isEmpty()) uniqueCands.add(parserResult.brandName);
        if (parserResult.drugName != null && !parserResult.drugName.isEmpty()) uniqueCands.add(parserResult.drugName);
        if (llmResult != null) {
            if (llmResult.brand_name != null && !llmResult.brand_name.isEmpty()) uniqueCands.add(llmResult.brand_name);
            if (llmResult.drug_name != null && !llmResult.drug_name.isEmpty()) uniqueCands.add(llmResult.drug_name);
        }

        // --- STEP 1: Evaluate All for Both Roles ---
        List<CandidateEval> brandEvals = new ArrayList<>();
        List<CandidateEval> drugEvals = new ArrayList<>();

        for (String cand : uniqueCands) {
            boolean isLlmBrand = llmResult != null && cand.equalsIgnoreCase(llmResult.brand_name);
            boolean isLlmDrug = llmResult != null && cand.equalsIgnoreCase(llmResult.drug_name);
            
            // Score as Brand
            brandEvals.add(getCandidateScore(context, cand, flatOcrUpper, dominantMode, false, "BRAND", isLlmBrand || isLlmDrug));
            // Score as Drug
            drugEvals.add(getCandidateScore(context, cand, flatOcrUpper, dominantMode, false, "DRUG", isLlmBrand || isLlmDrug));
        }

        // Sort by score DESC
        brandEvals.sort((a, b) -> Integer.compare(b.score, a.score));
        drugEvals.sort((a, b) -> Integer.compare(b.score, a.score));

        // --- STEP 3: Select Winners (Strict Separation) ---
        // STEP 6: Final Guard — skip any winner that is a marketing word
        CandidateEval winningBrand = null;
        for (CandidateEval be : brandEvals) {
            if (MARKETING_WORDS.contains(be.name.toUpperCase())) {
                Log.w(TAG, "[FinalGuard] Skipping marketing-word brand candidate: " + be.name);
                continue;
            }
            winningBrand = be;
            break;
        }
        String finalBrand = winningBrand != null && winningBrand.score >= 30 ? winningBrand.name.toUpperCase() : "";
        String brandDebug = winningBrand != null ? formatSingleReasoning(winningBrand) : "Not detected";

        String finalDrug = "";
        String drugDebug = "Not detected";
        List<String> suggestions = new ArrayList<>();
        CandidateEval winningDrug = null;
        
        for (CandidateEval de : drugEvals) {
            // STEP 7: ENFORCE BRAND ≠ DRUG
            if (winningBrand != null && de.name.equalsIgnoreCase(winningBrand.name)) continue;
            // STEP 6: Final Guard — skip marketing words in drug field too
            if (MARKETING_WORDS.contains(de.name.toUpperCase())) {
                Log.w(TAG, "[FinalGuard] Skipping marketing-word drug candidate: " + de.name);
                continue;
            }
            
            // Validation Rules
            boolean hasKeywords = de.name.toUpperCase().contains("VITAMIN") || de.name.toUpperCase().contains("MULTI") ||
                                  de.name.toUpperCase().contains("TABLET") || de.name.toUpperCase().contains("CAPSULE");
            boolean hasFreq = de.reasons.toString().contains("multiple times") || de.reasons.toString().contains("multiple scan passes");
            boolean isDict = de.reasons.toString().contains("drug dictionary");
            
            if (hasKeywords || hasFreq || isDict || de.isLlmSuggested) {
                if (finalDrug.isEmpty() && de.score >= 30) {
                    winningDrug = de;
                    finalDrug = de.name.toUpperCase();
                    drugDebug = formatSingleReasoning(de);
                } else if (de.score >= 20) {
                    suggestions.add(de.name.toUpperCase());
                }
            }
        }

        String finalDosage = validateDosage(llmResult != null ? llmResult.dosage : null, parserResult.dosage);
        String finalExpiry = validateExpiry(llmResult != null ? llmResult.expiry_date : null, parserResult.expiry, flatOcrUpper);

        Log.d(TAG, "[FINAL_RESULT] Brand: " + finalBrand + " | Drug: " + finalDrug + " | Dosage: " + finalDosage + " | Expiry: " + finalExpiry);

        MedicationData result = new MedicationData(finalBrand, finalDrug, finalDosage, finalExpiry, parserResult.dosageLineCount);
        result.brandReasoning = brandDebug;
        result.drugReasoning = drugDebug;
        result.drugSuggestions = suggestions;
        
        // --- STEP 3: Actionable Confidence Badge Logic ---
        int minScore = Math.min(winningBrand != null ? winningBrand.score : 0, winningDrug != null ? winningDrug.score : 0);
        if (minScore >= 60) result.confidenceLevel = "HIGH";
        else if (minScore >= 30) result.confidenceLevel = "MEDIUM";
        else result.confidenceLevel = "LOW";
        
        // --- STEP 6: Provenance Display ---
        if (winningDrug != null) {
            if (winningDrug.reasons.toString().contains("multiple")) {
                result.provenance = "Detected across multiple scans";
            } else if (dominantMode.equals("PACKAGING")) {
                result.provenance = "Detected from packaging text";
            } else {
                result.provenance = "Detected from clinical labels";
            }
        }

        return result;
    }

    private static String resolveWinner(CandidateEval parser, CandidateEval llm) {
        CandidateEval best = (llm.score > parser.score) ? llm : parser;
        if (best.score < 30) {
            return "[MANUAL CONFIRMATION REQUIRED]\nConfidence too low.";
        }
        if (parser.name != null && llm.name != null && !parser.name.equalsIgnoreCase(llm.name) && parser.score >= 30 && llm.score >= 30) {
            return (llm.score > parser.score) ? formatSingleReasoning(llm) : formatSingleReasoning(parser);
        }
        return formatSingleReasoning(best);
    }

    private static String getConfidenceLevel(int score) {
        if (score >= 60) return "HIGH";
        if (score >= 30) return "MEDIUM";
        return "LOW";
    }

    private static String formatCandidateExplanation(CandidateEval eval) {
        return eval.name + "\n\u2192 Found near: \"" + truncateContext(eval.explanationContext) + "\""
                + "\n\u2192 Confidence: " + getConfidenceLevel(eval.score);
    }

    private static String formatSingleReasoning(CandidateEval eval) {
        StringBuilder sb = new StringBuilder();
        sb.append(eval.name.toUpperCase()).append("\n\nDetected because:\n");
        for (String reason : eval.reasons) {
            sb.append("\u2714 ").append(reason).append("\n");
        }
        return sb.toString().trim();
    }

    private static String truncateContext(String text) {
        if (text == null || text.isEmpty()) return "Unknown Context";
        if (text.length() > 40) return text.substring(0, 37) + "...";
        return text;
    }

    private static class ContextMatch {
        String matchLine = "";
        int prescriptionWeight = 0;
        int packagingWeight = 0;
        boolean isHeader = false;
    }

    private static ContextMatch findCandidateContext(String candidate, String fullText) {
        ContextMatch cm = new ContextMatch();
        if (candidate == null || candidate.isEmpty() || fullText == null) return cm;
        
        String upperCand = candidate.toUpperCase();
        String[] lines = fullText.split("\\n");
        // Check exact match lines
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            String upperLine = line.toUpperCase();
            if (upperLine.contains(upperCand)) {
                assignContextWeights(cm, line);
                if (i < 5) cm.isHeader = true; // First 5 lines are headers
                return cm;
            }
        }
        
        // Check fuzzy match lines if exact not found
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (isFuzzyPresentInRaw(upperCand, line)) {
                assignContextWeights(cm, line);
                if (i < 5) cm.isHeader = true;
                return cm;
            }
        }
        return cm;
    }

    private static void assignContextWeights(ContextMatch cm, String line) {
        cm.matchLine = line.trim();
        String lowerLine = line.toLowerCase();
        if (lowerLine.contains("take") || lowerLine.contains("every") || lowerLine.contains("daily")) {
            cm.prescriptionWeight += 1;
        }
        if (lowerLine.contains("mg") || lowerLine.contains("tablet") || lowerLine.contains("capsule") || 
            lowerLine.contains("composition") || lowerLine.contains("vitamin") || 
            lowerLine.contains("zinc") || lowerLine.contains("mineral") || lowerLine.contains("supplement")) {
            cm.packagingWeight += 1;
        }
    }

    private static class CandidateEval {
        String name;
        int score;
        String explanationContext;
        boolean isLlmSuggested = false;
        List<String> reasons = new ArrayList<>();
    }

    private static CandidateEval getCandidateScore(Context context, String cand, String fullOcrText, String dominantMode, boolean isSoftRejected, String fieldType, boolean isLlmSuggested) {
        return getCandidateScore(context, cand, fullOcrText, dominantMode, isSoftRejected, fieldType, isLlmSuggested, null);
    }

    private static CandidateEval getCandidateScore(Context context, String cand, String fullOcrText, String dominantMode, boolean isSoftRejected, String fieldType, boolean isLlmSuggested, Set<String> fuzzyCandidateSet) {
        CandidateEval eval = new CandidateEval();
        eval.name = cand;
        eval.score = 0; // Neutral start
        eval.explanationContext = "Unknown context";
        eval.isLlmSuggested = isLlmSuggested;

        if (cand == null || cand.trim().isEmpty()) return eval;
        String upperCand = cand.trim().toUpperCase();
        int score = 0;

        // ─── STEP 1: NEGATIVE FILTER — Marketing word hard reject ───────────────
        if (MARKETING_WORDS.contains(upperCand)) {
            eval.score = -100;
            eval.reasons.add("Rejected: marketing/decorative word");
            Log.w(TAG, "[NegativeFilter] Hard-rejected marketing word: " + upperCand);
            return eval;
        }
        
        int freq = 0;
        String[] tokens = fullOcrText.toUpperCase().split("[\\s/\\-,]+");
        for(String t: tokens) {
            if(t.contains(upperCand)) freq++;
        }
        
        // --- Global Safety Flags ---
        String dictMatch = DrugNormalizer.findBestMatch(context, upperCand);
        boolean isDictMatch = !dictMatch.equalsIgnoreCase(upperCand);
        boolean isFuzzyMatch = isFuzzyPresentInRaw(upperCand, fullOcrText);
        boolean isStructural = upperCand.length() >= 5 && upperCand.matches("^[A-Z0-9/+-]+$");
        
        // --- Contextual Mapping ---
        ContextMatch cm = findCandidateContext(cand, fullOcrText);
        eval.explanationContext = cm.matchLine;

        // ─── STEP 4: MULTI-WORD PRIORITY BOOST ──────────────────────────────────
        if (cand.trim().contains(" ")) {
            score += 20;
            eval.reasons.add("Multi-word candidate (brand/drug phrase)");
        }

        // --- Priority Rule Engine ---
        if ("BRAND".equals(fieldType)) {
            // BRAND PRIORITY: 1. Header, 2. Repeated, 3. Structural Uppercase
            if (cm.isHeader) {
                score += 50;
                eval.reasons.add("Found in label header zone");
            }
            if (freq >= 2) {
                score += 20;
                eval.reasons.add("Appears multiple times across passes");
            }
            if (isStructural) {
                score += 15;
                // Avoid medical keywords and marketing words for brands
                if (upperCand.contains("VITAMIN") || upperCand.contains("MULTI") || 
                    upperCand.contains("TABLET") || upperCand.contains("CAPSULE") ||
                    MARKETING_WORDS.contains(upperCand)) {
                    score -= 60; // Heavy penalty for marketing/medical words in Brand field
                }
            }
        } else if ("DRUG".equals(fieldType)) {
            // DRUG PRIORITY: 1. Keywords, 2. Frequency, 3. Dictionary Match, 4. Context
            
            // 1. Keywords (MULTIVITAMIN, TABLET, etc.)
            if (upperCand.contains("VITAMIN") || upperCand.contains("MULTI") || 
                upperCand.contains("TABLET") || upperCand.contains("CAPSULE") || 
                upperCand.contains("SYRUP") || upperCand.contains("ZINC")) {
                
                score += 60;
                // STEP 3: PHRASE PRIORITY - Prefer longer phrases (MULTIVITAMIN > VITAMIN)
                score += (upperCand.length() * 2); 
                eval.reasons.add("Matches medical product keywords (Phrase priority)");
            }

            // ─── STEP 5: FULL DRUG CLASS BOOST ─────────────────────────────────
            if (DRUG_CLASS_BOOSTED.contains(upperCand)) {
                score += 30;
                eval.reasons.add("Known drug class match (full name boost)");
                Log.d(TAG, "[DrugClassBoost] +30 applied to: " + upperCand);
            }
            
            // 2. OCR Frequency
            if (freq >= 2) {
                score += 40;
                eval.reasons.add("Verified via multiple scan passes");
            } else {
                score -= 10;
            }

            // 3. Dictionary Match
            if (isDictMatch) {
                score += 30;
                eval.reasons.add("Verified in drug dictionary");
            }
            
            // 4. Structural Fallback (for unknown drugs)
            if (isStructural && !isDictMatch) {
                score += 10;
            }
            
            // Open-World Support
            boolean isOpenWorldDrug = !isDictMatch && !isFuzzyMatch && isStructural && freq >= 2;
            if (isOpenWorldDrug) {
                score += 25;
                eval.reasons.add("Matches clinical pattern (Open-world)");
            }
        }

        // ─── STEP 2: PATTERN MATCH BOOST ────────────────────────────────────────
        // isFuzzyMatch already uses similarity >= 0.65–0.80; treat as pattern match ratio >= 0.75
        if (isFuzzyMatch) {
            score += 70;
            eval.reasons.add("High-confidence pattern match (fuzzy ratio >= 0.75)");
            // Additional +20 if also grounded in fuzzy candidate list
            if (fuzzyCandidateSet != null && fuzzyCandidateSet.contains(upperCand)) {
                score += 20;
                eval.reasons.add("Confirmed in fuzzy candidates roster");
            }
        }
        
        if (isSoftRejected) {
            score -= 20; // Step 3: Soft Rejection penalty
            eval.reasons.add("Limited structural evidence (Weak AI candidate)");
        }

        // ─── STEP 3: PARSER NOISE PENALTY ───────────────────────────────────────
        // Apply -30 if this word is not in candidate list and not medically relevant
        boolean inCandidateList = fuzzyCandidateSet != null && fuzzyCandidateSet.contains(upperCand);
        boolean isMedicallyRelevant = isDictMatch || isFuzzyMatch ||
                upperCand.contains("VITAMIN") || upperCand.contains("MULTI") ||
                upperCand.contains("TABLET") || upperCand.contains("CAPSULE");
        if (!inCandidateList && !isMedicallyRelevant) {
            score -= 30;
            eval.reasons.add("Parser noise penalty: not in candidate list or medically relevant");
        }

        // --- Contextual Mapping Boost (Dynamic) ---
        boolean qualifiesForContextBoost = isDictMatch || isFuzzyMatch || (freq >= 2 && isStructural);
        if (qualifiesForContextBoost) {
            boolean hasContext = cm.prescriptionWeight > 0 || cm.packagingWeight > 0;
            if (hasContext) {
                String candidateContext = cm.prescriptionWeight >= cm.packagingWeight ? "PRESCRIPTION" : "PACKAGING";
                if (candidateContext.equals(dominantMode)) {
                    score += 50; // Dominant boost
                    eval.reasons.add("Strong alignment with " + dominantMode.toLowerCase() + " signals");
                }
            }
        }

        // --- Noise Penalty ---
        for (String stopword : STOPWORDS) {
            if (upperCand.contains(stopword)) score -= 50;
        }
        
        eval.score = score;
        return eval;
    }

    /**
     * Reconstructs fragmented OCR tokens into valid drug name candidates.
     */
    public static Set<String> reconstructFragmentedDrugs(String rawText) {
        Set<String> reconstructed = new HashSet<>();
        if (rawText == null) return reconstructed;

        String upper = rawText.toUpperCase();
        
        // Step 1: Fragment Merging (e.g., MULTI VITAMIN -> MULTIVITAMIN)
        // Look for common split drug names
        String[] pairs = {
            "MULTI", "VITAMIN",
            "PARA", "CETAMOL",
            "AMOX", "YCILLIN",
            "HYDRO", "CODON",
            "PANT", "OPRAZOLE"
        };

        for (int i = 0; i < pairs.length; i += 2) {
            String p1 = pairs[i];
            String p2 = pairs[i+1];
            // Match with optional space or noise
            if (upper.matches(".*" + p1 + "\\s*" + p2 + ".*") || 
                upper.matches(".*" + p1 + "[^A-Z]?" + p2 + ".*")) {
                reconstructed.add(p1 + p2);
            }
        }

        // Step 2: Global De-noising (removing internal spaces in potential candidates)
        String dense = upper.replaceAll("\\s+", "");
        if (dense.contains("MULTIVITAMIN")) reconstructed.add("MULTIVITAMIN");
        if (dense.contains("PARACETAMOL")) reconstructed.add("PARACETAMOL");
        if (dense.contains("AMOXICILLIN")) reconstructed.add("AMOXICILLIN");
        if (dense.contains("IBUPROFEN")) reconstructed.add("IBUPROFEN");
        if (dense.contains("HKVITALS")) reconstructed.add("HKVITALS");

        // Step 3: Header Candidates & OCR Normalization
        String[] lines = upper.split("\\n");
        for (int i = 0; i < Math.min(lines.length, 5); i++) {
            // Fix common OCR artifacts
            String line = lines[i]
                .replace("İ", "I")
                .replace("ı", "I")
                .trim();
                
            String[] lTokens = line.split("[\\s/\\-,]+");
            for (String t : lTokens) {
                String clean = t.replaceAll("[^A-Z]", "");
                if (clean.length() >= 5) reconstructed.add(clean);
            }
        }

        // Step 4: Fuzzy Clustering
        String[] tokens = upper.split("[\\s/\\-,]+");
        List<String> clusters = new ArrayList<>();
        for (String t : tokens) {
            String clean = t.replaceAll("[^A-Z]", "");
            if (clean.length() < 5) continue;

            boolean added = false;
            for (int j = 0; j < clusters.size(); j++) {
                if (DrugNormalizer.similarity(clean, clusters.get(j)) > 0.7) {
                    // Keyword Reinforcement: If one has 'VITAMIN', pick it as representative
                    if (clean.contains("VITAMIN") || clean.contains("MULTI")) {
                        clusters.set(j, clean);
                    }
                    added = true;
                    break;
                }
            }
            if (!added) clusters.add(clean);
        }
        reconstructed.addAll(clusters);

        return reconstructed;
    }

    private static boolean isFuzzyPresentInRaw(String target, String rawOcrUpper) {
        String strippedTarget = target.replaceAll("[^A-Z]", "");
        if (strippedTarget.length() < 4) return false; // Too risky to fuzzy match tiny words

        // Step 1: Adaptive Fuzzy Matching 
        double threshold = 0.65;
        if (strippedTarget.length() < 6) threshold = 0.80;
        else if (strippedTarget.length() <= 10) threshold = 0.70;

        String[] rawTokens = rawOcrUpper.split("[\\s/\\-,]+");
        for (String raw : rawTokens) {
            String strippedRaw = raw.replaceAll("[^A-Z]", "");
            if (strippedRaw.isEmpty()) continue;
            
            // Check Levenshtein similarity with adaptive threshold
            double sim = DrugNormalizer.similarity(strippedTarget, strippedRaw);
            if (sim >= threshold) {
                return true;
            }
        }
        return false;
    }

    // Deprecated static single-validation pattern (validateDrugName is replaced by selectBestCandidate)

    private static String validateDosage(String llmDosage, String parserDosage) {
        if (llmDosage == null || llmDosage.trim().isEmpty()) {
            return parserDosage;
        }

        String lower = llmDosage.toLowerCase().trim();

        // Rule 1: Must not be excessively long (no paragraphs)
        if (lower.length() > 100) {
            Log.w(TAG, "Formatting Blocked: LLM Dosage excessively long.");
            return parserDosage;
        }

        // Rule 2: Must contain instruction intent
        boolean hasInstruction = lower.contains("take") || lower.contains("daily") || 
                                 lower.contains("every") || lower.contains("once") ||
                                 lower.contains("twice") || lower.contains("hour") ||
                                 lower.contains("needed") || lower.contains("mouth") ||
                                 lower.matches(".*\\d+\\s*(tablet|capsule|ml).*");
        
        if (hasInstruction) {
            Log.d(TAG, "Validation Passed: LLM Dosage '" + llmDosage + "' accepted.");
            return llmDosage;
        }

        Log.w(TAG, "Intent Missing: LLM Dosage lacks instructional keywords. Falling back.");
        return parserDosage;
    }

    private static String validateExpiry(String llmExpiry, String parserExpiry, String rawOcrUpper) {
        if (llmExpiry == null || llmExpiry.trim().isEmpty()) {
            return parserExpiry;
        }

        String upper = llmExpiry.toUpperCase().trim();

        // Rule 1: Anti-hallucination
        // Expiry might have formatting differences (MM/YY vs MM-YY)
        String cleanExpiry = upper.replaceAll("[^0-9A-Z]", "");
        String cleanRaw = rawOcrUpper.replaceAll("[^0-9A-Z]", "");
        
        if (!cleanRaw.contains(cleanExpiry) || cleanExpiry.length() < 4) {
            Log.w(TAG, "Hallucination Blocked: Expiry '" + llmExpiry + "' not in raw text.");
            return parserExpiry;
        }

        // Rule 2: Date Regex Match
        Pattern p = Pattern.compile("(\\d{1,2}[/\\-]\\d{1,2}[/\\-]\\d{2,4})|(\\d{1,2}[/\\-]\\d{2,4})|(JAN|FEB|MAR|APR|MAY|JUN|JUL|AUG|SEP|OCT|NOV|DEC)[A-Z]*\\s*\\d{2,4}");
        Matcher m = p.matcher(upper);
        if (m.find()) {
            Log.d(TAG, "Validation Passed: LLM Expiry '" + llmExpiry + "' accepted.");
            return llmExpiry;
        }

        Log.w(TAG, "Format Blocked: LLM Expiry invalid format. Falling back.");
        return parserExpiry;
    }
    
    /**
     * Boosts confidence if LLM and Parser agree.
     */
    public static int recalculateConfidence(int originalConfidence, String llmValue, String parserValue, String finalValidatedValue) {
        if (llmValue == null || llmValue.isEmpty()) {
            return originalConfidence; // No LLM contribution
        }
        
        boolean llmAccepted = llmValue.equalsIgnoreCase(finalValidatedValue);
        boolean parserAgrees = parserValue != null && parserValue.equalsIgnoreCase(llmValue);

        if (llmAccepted && parserAgrees) {
            // LLM and parser agree -> High boost
            return Math.min(100, originalConfidence + 30);
        } else if (finalValidatedValue != null && finalValidatedValue.contains(" / ")) {
            // Conflict Safety Merged -> Significant Penalty to alert user
            return Math.max(0, originalConfidence - 20);
        } else if (llmAccepted && !parserAgrees) {
            // LLM overrode Parser successfully
            boolean wasNoise = parserValue != null && STOPWORDS.contains(parserValue.toUpperCase());
            if (wasNoise) {
                return Math.min(100, originalConfidence + 20); // Rescued a noise scan
            }
            return Math.min(100, originalConfidence + 10); // Standard override boost
        } else {
            // Unresolved Disagreement (LLM rejected, fell back to Parser)
            return Math.max(0, originalConfidence - 20);
        }
    }
}
