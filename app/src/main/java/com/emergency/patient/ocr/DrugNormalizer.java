package com.emergency.patient.ocr;

import android.content.Context;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * DrugNormalizer — Comprehensive OCR correction and generic normalization engine.
 * Handles systematic pattern errors, merged tokens, and junk filtering.
 */
public class DrugNormalizer {

    private static final String TAG = "DrugNormalizer";
    private static Map<String, List<String>> drugMap = null;
    private static Map<String, String> ocrOverrides = null;
    private static List<PatternKnowledge> patternKnowledgeList = null;

    public static class PatternKnowledge {
        public String id;
        public List<String> keywords;
        public String brandName;
        public String drugName;
        public String dosageHint;
        public int confidenceBoost;
        public double matchRatio;

        public PatternKnowledge(String id, List<String> keywords, String brandName, String drugName, String dosageHint, int confidenceBoost) {
            this.id = id;
            this.keywords = keywords;
            this.brandName = brandName;
            this.drugName = drugName;
            this.dosageHint = dosageHint;
            this.confidenceBoost = confidenceBoost;
            this.matchRatio = 0.0;
        }
    }

    public static class Candidate {
        public String name;
        public double confidence;
        public String reason;

        public Candidate(String name, double confidence, String reason) {
            this.name = name;
            this.confidence = confidence;
            this.reason = reason;
        }
    }

    /**
     * Main normalization pipeline.
     */
    public static NormalizationResult normalize(Context context, String input) {
        if (input == null || input.trim().isEmpty()) {
            return new NormalizationResult("", false);
        }

        // STEP 1: Generic OCR Clean-up and intelligent token isolation
        String cleaned = normalizeDrugName(input);
        
        // STEP 2: Map to Local Dictionary (Exact or Alias Match)
        loadDrugMap(context);
        
        String candidate = cleaned.toLowerCase();
        
        if (drugMap != null && !drugMap.isEmpty()) {
            for (String canonical : drugMap.keySet()) {
                // Check if candidate is exactly canonical or listed as an alias
                if (candidate.equals(canonical)) {
                    return new NormalizationResult(canonical.substring(0, 1).toUpperCase() + canonical.substring(1), true);
                }
                
                List<String> aliases = drugMap.get(canonical);
                if (aliases != null && aliases.contains(candidate)) {
                    return new NormalizationResult(canonical.substring(0, 1).toUpperCase() + canonical.substring(1), true);
                }
            }
        }

        // Fallback: return the cleaned capitalized name
        String finalName = cleaned.length() > 1 ? cleaned.substring(0, 1).toUpperCase() + cleaned.substring(1).toLowerCase() : cleaned;
        return new NormalizationResult(finalName, false);
    }

    /**
     * Step 1: Candidate Ranking (Pre-engine)
     */
    public static List<PatternKnowledge> detectMatchedPatterns(Context context, String normalizedText) {
        List<PatternKnowledge> matched = new ArrayList<>();
        if (normalizedText == null || normalizedText.isEmpty()) return matched;

        loadPatternKnowledge(context);
        if (patternKnowledgeList == null) return matched;

        String upper = normalizedText.toUpperCase();

        for (PatternKnowledge p : patternKnowledgeList) {
            int matchCount = 0;
            for (String kw : p.keywords) {
                // Fuzzy keyword matching for resilience
                if (upper.contains(kw)) {
                    matchCount++;
                } else {
                    // Check if any word in raw text is similar to keyword
                    for (String word : upper.split("\\s+")) {
                        if (word.length() >= 4 && similarity(word, kw) >= 0.8) {
                            matchCount++;
                            break;
                        }
                    }
                }
            }

            // Trigger if at least 2 keywords match or 50% of the keywords
            double matchRatio = (double) matchCount / p.keywords.size();
            p.matchRatio = matchRatio;
            if (matchCount >= 2 || (p.keywords.size() < 2 && matchCount >= 1)) {
                matched.add(p);
                Log.d(TAG, "[PATTERN_MATCH] Detected: " + p.id + " | Match Ratio: " + String.format("%.2f", matchRatio));
            }
        }
        return matched;
    }

    public static List<Candidate> getRankedCandidates(Context context, String rawText) {
        List<Candidate> results = new ArrayList<>();
        if (rawText == null || rawText.isEmpty()) return results;

        loadDrugMap(context);
        loadOverrides(context);

        String upper = rawText.toUpperCase();
        String[] tokens = upper.split("[\\s/\\-,]+");

        // 1. Check Overrides (Highest Priority)
        if (ocrOverrides != null) {
            for (String key : ocrOverrides.keySet()) {
                if (upper.contains(key)) {
                    results.add(new Candidate(ocrOverrides.get(key), 0.95, "override_match"));
                }
            }
        }

        // 2. Token Analysis
        Map<String, Integer> freqMap = new HashMap<>();
        for (String t : tokens) {
            String clean = t.replaceAll("[^A-Z]", "");
            if (clean.length() < 3 || isJunk(clean)) continue;
            freqMap.put(clean, freqMap.getOrDefault(clean, 0) + 1);
        }

        for (String drug : freqMap.keySet()) {
            double score = 0.0;
            String reason = "";

            // Frequency signal
            int freq = freqMap.get(drug);
            score += (freq * 0.1); 

            // Dictionary signal
            String bestMatch = findBestMatch(context, drug);
            if (!bestMatch.equalsIgnoreCase(drug)) {
                score += 0.5;
                reason = "dictionary_match";
                results.add(new Candidate(bestMatch, Math.min(0.9, score), reason));
            } else if (drug.length() >= 6) {
                score += 0.3;
                reason = "structural_token";
                results.add(new Candidate(drug, Math.min(0.8, score), reason));
            }
        }

        // Sort by confidence DESC
        results.sort((a, b) -> Double.compare(b.confidence, a.confidence));
        
        // Return top 10 unique
        List<Candidate> filtered = new ArrayList<>();
        Set<String> seen = new java.util.HashSet<>();
        for (Candidate c : results) {
            if (!seen.contains(c.name.toUpperCase())) {
                filtered.add(c);
                seen.add(c.name.toUpperCase());
                if (filtered.size() >= 10) break;
            }
        }
        return filtered;
    }

    // ─── Generic Normalization Pipeline ──────────────────────────────────────

    public static String normalizeDrugName(String raw) {
        if (raw == null || raw.trim().isEmpty()) return "";

        String step1 = normalizeCharacters(raw);
        String step2 = normalizeMedicalPatterns(step1);

        List<String> tokens = tokenize(step2);

        List<String> filtered = new ArrayList<>();
        for (String t : tokens) {
            if (!isJunk(t)) {
                filtered.add(t);
            }
        }

        if (filtered.isEmpty() && !tokens.isEmpty()) {
            // If all tokens filtered out but they weren't empty, pick the best from original
            return selectBestToken(tokens);
        }

        String best = selectBestToken(filtered);
        return cleanFinal(best);
    }

    private static String normalizeCharacters(String input) {
        String name = input.toUpperCase();
        // Fix common OCR substitutions
        name = name.replace('0', 'O');
        name = name.replace('1', 'I');
        name = name.replace('5', 'S');
        return name;
    }

    private static String normalizeMedicalPatterns(String name) {
        // Fix slash-related merges (e.g. HYDROCODONEIAPAP -> HYDROCODONE/APAP)
        name = name.replace("IAPAP", "/APAP");
        name = name.replace("APAP", "/APAP");

        // Fix missing hyphens in dosage ranges (e.g. 12 -> 1-2)
        // Only if it looks like a range (small numbers) or instruction context
        name = name.replaceAll("(\\d)(\\d)", "$1-$2");

        return name;
    }

    private static List<String> tokenize(String name) {
        // Split on multiple separators
        String[] raw = name.split("[/\\s\\-_,]");
        List<String> tokens = new ArrayList<>();

        for (String token : raw) {
            token = token.trim();
            if (token.length() >= 3) {
                tokens.add(token);
            }
        }
        return tokens;
    }

    private static boolean isJunk(String token) {
        String t = token.toLowerCase();
        return t.contains("take") ||
               t.contains("daily") ||
               t.contains("every") ||
               t.contains("tablet") ||
               t.contains("capsule") ||
               t.contains("qty") ||
               t.contains("pharmacy") ||
               t.contains("pain") ||
               t.contains("refill") ||
               t.matches("\\d+"); // pure numbers
    }

    private static int scoreToken(String token) {
        int score = 0;
        // Strong signals
        if (token.matches("[A-Z]{5,}")) score += 5; // long uppercase word
        if (token.matches(".*\\d+MG.*")) score += 5;
        if (token.matches(".*\\d+.*")) score += 2;

        // Weak signals
        if (token.length() > 6) score += 2;

        // Penalize junk
        if (isJunk(token)) score -= 5;
        return score;
    }

    private static String selectBestToken(List<String> tokens) {
        String best = "";
        int bestScore = Integer.MIN_VALUE;

        for (String token : tokens) {
            int score = scoreToken(token);
            if (score > bestScore) {
                bestScore = score;
                best = token;
            }
        }
        return best;
    }

    private static String cleanFinal(String token) {
        if (token == null) return "";
        // Keep ONLY characters (remove numbers/junk at the very end if requested, 
        // but often drug names have numbers like Dolo 650)
        // User asked for: token.replaceAll("[^A-Z]", "");
        String cleaned = token.toUpperCase().replaceAll("[^A-Z]", "");

        // Remove common suffixes
        cleaned = cleaned.replace("TABLETS", "");
        cleaned = cleaned.replace("CAPSULES", "");
        cleaned = cleaned.replace("MG", "");
        cleaned = cleaned.replace("ML", "");

        return cleaned.trim();
    }

    // ─── Dictionary Loading ───────────────────────────────────────────────────

    private static void loadDrugMap(Context context) {
        synchronized (DrugNormalizer.class) {
            if (drugMap != null) return;
            try {
                InputStream is = context.getAssets().open("drug_dictionary.json");
                BufferedReader reader = new BufferedReader(new InputStreamReader(is));
                StringBuilder json = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    json.append(line);
                }
                JSONObject obj = new JSONObject(json.toString());
                drugMap = new HashMap<>();
                Iterator<String> keys = obj.keys();
                while (keys.hasNext()) {
                    String key = keys.next();
                    JSONArray arr = obj.getJSONArray(key);
                    List<String> list = new ArrayList<>();
                    for (int i = 0; i < arr.length(); i++) {
                        list.add(arr.getString(i).toLowerCase());
                    }
                    drugMap.put(key.toLowerCase(), list);
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to load drug_dictionary.json", e);
                drugMap = new HashMap<>();
            }
        }
    }

    private static void loadOverrides(Context context) {
        synchronized (DrugNormalizer.class) {
            if (ocrOverrides != null) return;
            try {
                InputStream is = context.getAssets().open("ocr_overrides.json");
                BufferedReader reader = new BufferedReader(new InputStreamReader(is));
                StringBuilder json = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    json.append(line);
                }
                JSONObject obj = new JSONObject(json.toString());
                ocrOverrides = new HashMap<>();
                Iterator<String> keys = obj.keys();
                while (keys.hasNext()) {
                    String key = keys.next();
                    ocrOverrides.put(key.toUpperCase(), obj.getString(key));
                }
            } catch (Exception e) {
                Log.v(TAG, "No ocr_overrides.json found in assets");
                ocrOverrides = new HashMap<>();
            }
        }
    }

    private static void loadPatternKnowledge(Context context) {
        synchronized (DrugNormalizer.class) {
            if (patternKnowledgeList != null) return;
            try {
                InputStream is = context.getAssets().open("pattern_knowledge.json");
                BufferedReader reader = new BufferedReader(new InputStreamReader(is));
                StringBuilder json = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    json.append(line);
                }
                JSONArray arr = new JSONArray(json.toString());
                patternKnowledgeList = new ArrayList<>();
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject obj = arr.getJSONObject(i);
                    JSONArray kwArr = obj.getJSONArray("match_keywords");
                    List<String> keywords = new ArrayList<>();
                    for (int j = 0; j < kwArr.length(); j++) {
                        keywords.add(kwArr.getString(j).toUpperCase());
                    }
                    patternKnowledgeList.add(new PatternKnowledge(
                        obj.getString("id"),
                        keywords,
                        obj.getString("brand_name"),
                        obj.getString("drug_name"),
                        obj.getString("dosage_hint"),
                        obj.getInt("confidence_boost")
                    ));
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to load pattern_knowledge.json", e);
                patternKnowledgeList = new ArrayList<>();
            }
        }
    }

    // Existing Confidence/Cleanup helpers (maintained for API usage)
    
    public static boolean isLowConfidence(String name) {
        if (name == null || name.trim().isEmpty()) return true;
        name = name.trim();
        if (name.matches("^[A-Z]{10,}$")) return true;
        if (name.matches(".*[0-9].*") && !name.matches(".*\\s\\d+.*")) return true;
        if (name.length() < 3) return true;
        return false;
    }

    public static String cleanDrugName(String name) {
        if (name == null) return "";
        return normalizeDrugName(name); // Use the new generic pipeline for cleaning
    }

    // ─── Dictionary String Reconstruction (Fuzzy matching) ────────────────────

    public static String findBestMatch(Context context, String word) {
        if (word == null || word.length() < 3) return word;

        loadDrugMap(context);
        
        if (drugMap == null || drugMap.isEmpty()) return word;

        String lowerWord = word.toLowerCase();
        String bestMatch = word;
        double bestScore = 0.0;

        for (String canonical : drugMap.keySet()) {
            double sim = similarity(lowerWord, canonical);
            if (sim > bestScore) {
                bestScore = sim;
                bestMatch = canonical;
            }
            // Check aliases too
            List<String> aliases = drugMap.get(canonical);
            if (aliases != null) {
                for (String alias : aliases) {
                    double aliasSim = similarity(lowerWord, alias);
                    if (aliasSim > bestScore) {
                        bestScore = aliasSim;
                        bestMatch = canonical; // if matched alias, return the canonical parent
                    }
                }
            }
        }

        // If high match threshold met, reconstruct the word
        if (bestScore >= 0.75) {
            String result = bestMatch.substring(0, 1).toUpperCase() + bestMatch.substring(1).toLowerCase();
            Log.d(TAG, "Fuzzy Match Found: '" + word + "' -> '" + result + "' (Score: " + String.format("%.2f", bestScore) + ")");
            return result;
        }

        return word;
    }

    public static double similarity(String s1, String s2) {
        if (s1.equals(s2)) return 1.0;
        int maxLen = Math.max(s1.length(), s2.length());
        if (maxLen == 0) return 1.0;
        return 1.0 - ((double) levenshtein(s1, s2) / maxLen);
    }

    public static int levenshtein(String s1, String s2) {
        int[] costs = new int[s2.length() + 1];
        for (int j = 0; j < costs.length; j++)
            costs[j] = j;
        for (int i = 1; i <= s1.length(); i++) {
            costs[0] = i;
            int nw = i - 1;
            for (int j = 1; j <= s2.length(); j++) {
                int cj = Math.min(1 + Math.min(costs[j], costs[j - 1]), s1.charAt(i - 1) == s2.charAt(j - 1) ? nw : nw + 1);
                nw = costs[j];
                costs[j] = cj;
            }
        }
        return costs[s2.length()];
    }
}
