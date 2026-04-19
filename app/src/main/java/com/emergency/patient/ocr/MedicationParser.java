package com.emergency.patient.ocr;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * MedicationParser — Zone-based extraction engine.
 * Classifies OCR lines into functional medical zones before parsing
 * to eliminate noise from pharmacy metadata, addresses, and warnings.
 */
public class MedicationParser {

    private static class Zones {
        List<String> drugLines = new ArrayList<>();
        List<String> dosageLines = new ArrayList<>();
        List<String> expiryLines = new ArrayList<>();
        List<String> headerLines = new ArrayList<>();
    }

    public static MedicationData parse(String rawText, java.util.Set<String> fuzzyCandidates) {
        if (rawText == null || rawText.trim().isEmpty()) {
            MedicationData empty = new MedicationData("", "", "", "");
            if (fuzzyCandidates != null) empty.fuzzyCandidates.addAll(fuzzyCandidates);
            return empty;
        }

        List<String> lines = Arrays.asList(rawText.split("\\n"));
        Zones zones = classifyZones(lines);

        String brand = extractBrandName(zones, rawText);
        String drug = extractDrugName(zones, rawText, fuzzyCandidates);
        
        // Final Fallback for Drug Name
        if (drug.isEmpty() && fuzzyCandidates != null) {
            for (String cand : fuzzyCandidates) {
                if (rawText.toUpperCase().contains(cand) && !cand.equals(brand)) {
                    drug = cand; // Pick first grounded candidate found in raw text
                    break;
                }
            }
        }
        
        if (drug.isEmpty()) {
            String[] allWords = rawText.toUpperCase().split("\\s+");
            for (String w : allWords) {
                String clean = w.replaceAll("[^A-Z]", "");
                // Step 3/6 guard: never pick a marketing/decorative word
                if (clean.length() >= 5 && !STOPWORDS.contains(clean) && !MARKETING_WORDS.contains(clean) && !clean.equals(brand)) {
                    if (clean.length() > drug.length()) drug = clean;
                }
            }
        }

        DosageResult dosageResult = extractDosage(zones);

        MedicationData data = new MedicationData(
            brand,
            drug,
            dosageResult.text,
            extractExpiry(zones),
            dosageResult.lineCount
        );
        if (fuzzyCandidates != null) data.fuzzyCandidates.addAll(fuzzyCandidates);
        return data;
    }

    // ─── STEP 1: ZONE CLASSIFICATION ──────────────────────────────────────────

    private static Zones classifyZones(List<String> lines) {
        Zones z = new Zones();

        int lineIdx = 0;
        for (String line : lines) {
            String upper = line.toUpperCase().trim();
            if (upper.length() < 3) continue;

            // Track early lines as headers for brand detection
            if (lineIdx < 5) {
                z.headerLines.add(line);
            }
            lineIdx++;

            // Filter out obvious pharmacy noise early
            if (upper.contains("REFILL") || upper.contains("QTY") || upper.contains("RX:")) continue;

            // EXPIRY ZONE
            if (upper.contains("EXP") || upper.contains("DATE") || upper.contains("USE BEFORE")) {
                z.expiryLines.add(line);
                continue;
            }

            // DOSAGE ZONE 
            if (upper.contains("TAKE") || upper.contains("DAILY") || upper.contains("EVERY") || upper.contains("ONCE")) {
                z.dosageLines.add(line);
            }

            // DRUG ZONE (RELAXED: Include strength-less lines with readable words)
            if (upper.contains("MG") || upper.contains("ML") || 
                upper.contains("TABLET") || upper.contains("CAPSULE") || 
                upper.contains("USP") || upper.contains("SOLUTION") ||
                upper.contains("VITAMIN") || upper.contains("MULTI") ||
                upper.matches(".*[A-Z]{5,}.*")) {
                z.drugLines.add(line);
            }
        }
        return z;
    }

    // ─── STEP 2: TARGETED EXTRACTION ──────────────────────────────────────────

    private static final Set<String> STOPWORDS = new HashSet<>(Arrays.asList(
        "ORAL", "SUSPENSION", "TABLET", "TABLETS", "CAPSULE", "CAPSULES",
        "SYRUP", "SOLUTION", "INJECTION", "DOSE", "DOSAGE", "FILM", "COATED",
        "ML", "MG", "USP", "TAKE", "PRESCRIPTION", "INFORMATION", "QTY", "REFILL",
        "WARNING", "MANUFACTURED", "STORE", "KEEP", "USE", "NOT", "BROKEN", "BLISTER",
        "DO", "IF", "THE", "AND", "OR", "MORE", "THAN", "MAXIMUM", "DAILY", "IP",
        // Marketing / decorative words — must never be selected as a drug name
        "RESEARCH", "INNOVATIONS", "ENERGY", "IMMUNITY", "DRIVEN",
        "POWER", "ADVANCE", "PLUS", "FORTE"
    ));

    // Step 3/6 guard: explicit marketing word set used in final fallback sweep
    private static final Set<String> MARKETING_WORDS = new HashSet<>(Arrays.asList(
        "RESEARCH", "INNOVATIONS", "ENERGY", "IMMUNITY", "DRIVEN",
        "POWER", "ADVANCE", "PLUS", "FORTE"
    ));

    private static String extractBrandName(Zones z, String rawText) {
        // Brand logic: Top-pass extraction (headers)
        String best = "";
        int bestScore = -999;

        for (String line : z.headerLines) {
            String l = line.toUpperCase();
            String[] words = l.split("\\s+");
            for (String word : words) {
                String clean = word.replaceAll("[^A-Z]", "").trim();
                if (clean.length() < 5 || clean.length() > 15) continue;

                int score = 10; // Base for being in header

                // Penalty for drug words
                if (STOPWORDS.contains(clean) || clean.contains("VITAMIN") || clean.contains("MULTI")) {
                    score -= 50;
                }

                if (score > bestScore) {
                    bestScore = score;
                    best = clean;
                }
            }
        }
        return bestScore > 0 ? best : "";
    }

    private static String extractDrugName(Zones z, String rawText, java.util.Set<String> fuzzyCandidates) {
        if (isPrescription(rawText)) {
            android.util.Log.d("OCR_MODE", "Detected Mode: PRESCRIPTION");
            return extractFromPrescription(z, fuzzyCandidates);
        } else {
            android.util.Log.d("OCR_MODE", "Detected Mode: PACKAGING");
            return extractFromPackaging(z, fuzzyCandidates);
        }
    }

    private static boolean isPrescription(String text) {
        String upper = text.toUpperCase();
        return upper.contains("TAKE") || upper.contains("EVERY") || upper.contains("QTY");
    }

    private static String extractFromPrescription(Zones z, java.util.Set<String> fuzzyCandidates) {
        // First try grounded candidates in drug lines (highest confidence)
        if (fuzzyCandidates != null) {
            for (String line : z.drugLines) {
                String l = line.toUpperCase();
                for (String cand : fuzzyCandidates) {
                    if (l.contains(cand)) return cand;
                }
            }
        }

        // Scan bottom-to-top: Drug names are usually below pharmacy headers
        for (int i = z.drugLines.size() - 1; i >= 0; i--) {
            String line = z.drugLines.get(i);
            String l = line.toUpperCase()
                    .replace("IAPAP", "/APAP")
                    .replace("1APAP", "/APAP")
                    .trim();

            // Skip obvious noise
            if (l.contains("PHARMACY") || l.contains("PHARNACY") || l.contains("RX:") || l.contains("PHONE")) continue;

            // Pattern 1: Combination drugs (Most unique signature)
            if (l.contains("/")) {
                String firstWord = l.split("\\s+")[0];
                if (firstWord.contains("/")) {
                    return firstWord.split("/")[0];
                }
            }

            // Pattern 2: Strong drug identifiers (Length 6-15)
            String[] words = l.split("\\s+");
            for (String word : words) {
                if (word.length() >= 6 && word.length() <= 20) {
                    if (word.contains("PHARM")) continue;
                    if (STOPWORDS.contains(word)) continue;
                    return word;
                }
            }
        }
        return "";
    }

    private static String extractFromPackaging(Zones z, java.util.Set<String> fuzzyCandidates) {
        // Grounding priority
        if (fuzzyCandidates != null) {
            for (String line : z.drugLines) {
                String l = line.toUpperCase();
                for (String cand : fuzzyCandidates) {
                    if (l.contains(cand)) return cand;
                }
            }
            // Even if not in direct drug lines, if it's the only strong candidate, use it
            for (String cand : fuzzyCandidates) {
               if (cand.length() >= 6) return cand;
            }
        }

        String best = "";
        int bestScore = -999;

        for (String line : z.drugLines) {
            String l = line.toUpperCase();
            String[] words = l.split("\\s+");

            for (String word : words) {
                String clean = word.replaceAll("[^A-Z]", "").trim();
                if (clean.length() < 3) continue;

                int score = 0;

                // Typical drug name length boost
                if (clean.length() >= 5 && clean.length() <= 12) score += 5;

                // Context indicators
                if (l.contains("MG")) score += 5;
                if (l.contains("TABLET") || l.contains("CAPSULE")) score += 5;

                // Penalties
                if (STOPWORDS.contains(clean)) score -= 10;
                if (isGarbageWord(clean)) score -= 20;
                
                // Penalize common generic packaging words
                if (clean.equals("TABLET") || clean.equals("CAPSULE") || clean.equals("USE")) score -= 15;

                if (score > bestScore) {
                    bestScore = score;
                    best = clean;
                }
            }
        }
        return bestScore > -20 ? best : "";
    }

    private static boolean isGarbageWord(String word) {
        // Likely a merged sentence if too long
        if (word.length() > 15) return true;
        
        // Characteristic of merged OCR sentences (common words lack spacing)
        if (word.matches(".*(USE|NOT|IF|THE|DO|AND|LIMIT|DAILY).*") && word.length() > 8) {
            return true;
        }
        return false;
    }

    private static List<String> splitDrugTokens(String word) {
        List<String> result = new ArrayList<>();
        String w = word.toUpperCase();

        // Case 1: Slash separated (e.g. HYDROCODONE/APAP)
        if (w.contains("/")) {
            result.addAll(Arrays.asList(w.split("/")));
            return result;
        }

        // Case 2: Common merged OCR patterns for APAP
        // (If it doesn't contain a slash, OCR often reads it as one word: HYDROCODONEIAPAP)
        if (w.contains("APAP") && w.length() > 4) {
            result.add(w.substring(0, w.indexOf("APAP")));
            result.add("APAP");
            return result;
        }

        result.add(word);
        return result;
    }

    private static DosageResult extractDosage(Zones z) {
        if (z.dosageLines.isEmpty()) return new DosageResult("", 0);

        List<String> cleanedLines = new ArrayList<>();
        for (String line : z.dosageLines) {
            String cleaned = line.toLowerCase()
                .replace("bymn", "by mouth")
                .replace("byn", "by mouth")
                .replace("by!", "by mouth")
                .replace("oy", "by")
                .replace("ask", "as needed")
                .replace("asn", "as needed")
                .replace("fabiet", "tablet")
                .replace("tabiet", "tablet")
                .replace("na", "mg")
                .replace("i-", "1-")
                .replace("3o", "30")
                .trim();
            cleanedLines.add(cleaned);
        }

        // STEP 2: Greediest Capture
        StringBuilder fullBlock = new StringBuilder();
        int lineCount = 0;
        boolean capturing = false;

        for (String l : cleanedLines) {
            if (!capturing && (l.contains("take") || l.contains("use") || l.contains("apply"))) {
                capturing = true;
            }
            if (capturing) {
                if (l.contains("qty") || l.contains("rx:") || l.contains("refill")) break;
                fullBlock.append(l).append(" ");
                lineCount++;
            }
        }

        String combined = fullBlock.toString().replaceAll("\\s+", " ").trim();
        if (combined.isEmpty()) return new DosageResult("", 0);

        // Standardize output
        String finalDosage = combined;
        // Capitalize first letter
        if (finalDosage.length() > 0) {
            finalDosage = Character.toUpperCase(finalDosage.charAt(0)) + finalDosage.substring(1);
        }

        return new DosageResult(finalDosage, lineCount);
    }

    private static class DosageResult {
        String text;
        int lineCount;
        DosageResult(String t, int c) { text = t; lineCount = c; }
    }

    private static String extractExpiry(Zones z) {
        // Common pharmaceutical expiry patterns (MM/DD/YY, MM/YYYY, etc.)
        Pattern p = Pattern.compile("(\\d{1,2}[/\\-]\\d{1,2}[/\\-]\\d{2,4})|(\\d{1,2}[/\\-]\\d{2,4})");

        for (String line : z.expiryLines) {
            Matcher m = p.matcher(line);
            if (m.find()) {
                return m.group();
            }
        }
        
        // Fallback: If no date found in expiry lines, scan them for month names
        Pattern monthPattern = Pattern.compile("(JAN|FEB|MAR|APR|MAY|JUN|JUL|AUG|SEP|OCT|NOV|DEC)[A-Z]*\\s*\\d{2,4}", Pattern.CASE_INSENSITIVE);
        for (String line : z.expiryLines) {
            Matcher m = monthPattern.matcher(line);
            if (m.find()) return m.group();
        }

        return "";
    }
}
