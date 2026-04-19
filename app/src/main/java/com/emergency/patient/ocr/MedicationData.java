package com.emergency.patient.ocr;

/**
 * MedicationData — Plain data holder produced by MedicationParser.
 *
 * All fields are raw strings exactly as extracted/inferred from the OCR text.
 * The UI layer (MedicationOcrActivity) displays and allows editing of each
 * field before the patient approves and the record is written to Room.
 */
public class MedicationData {
 
    /** Manufacturer / Marketing brand, e.g. "HKVitals", "Advil". Empty string if not found. */
    public final String brandName;

    /** Actual medicine product / type, e.g. "Paracetamol", "Multivitamin". Empty string if not found. */
    public final String drugName;

    /** Dosage string, e.g. "500 mg", "250 mg/5 ml". Empty string if not found. */
    public final String dosage;

    /** Expiry string, e.g. "06/27", "15/06/2027", "EXP: 2027-06". Empty string if not found. */
    public final String expiry;

    /** Number of lines identified as part of the dosage instruction. */
    public final int dosageLineCount;

    /** Overall confidence level: HIGH, MEDIUM, LOW. */
    public String confidenceLevel = "MEDIUM";

    /** Provenance hint, e.g. "Detected across 2 scans". */
    public String provenance = "";

    /** Alternative candidates for drug name. */
    public java.util.List<String> drugSuggestions = new java.util.ArrayList<>();

    /** Reasoning / Debug info for brand selection. */
    public String brandReasoning;

    /** Reasoning / Debug info for drug selection. */
    public String drugReasoning;

    /** Grounded candidate list from fuzzy normalization. */
    public java.util.Set<String> fuzzyCandidates = new java.util.HashSet<>();

    /** Normalized version of raw text. */
    public String normalizedText = "";

    public MedicationData(String brandName, String drugName, String dosage, String expiry) {
        this(brandName, drugName, dosage, expiry, 0);
    }

    public MedicationData(String brandName, String drugName, String dosage, String expiry, int dosageLineCount) {
        this.brandName = brandName != null ? brandName.trim() : "";
        this.drugName  = drugName  != null ? drugName.trim()  : "";
        this.dosage = dosage != null ? dosage.trim() : "";
        this.expiry = expiry != null ? expiry.trim() : "";
        this.dosageLineCount = dosageLineCount;
        this.brandReasoning = "";
        this.drugReasoning = "";
    }

    /** Returns true if at least one field was successfully extracted. */
    public boolean hasAnyData() {
        return !brandName.isEmpty() || !drugName.isEmpty() || !dosage.isEmpty() || !expiry.isEmpty();
    }

    @Override
    public String toString() {
        return "MedicationData{brand='" + brandName + "', drug='" + drugName + "', dosage='" + dosage + "', expiry='" + expiry + "'}";
    }
}
