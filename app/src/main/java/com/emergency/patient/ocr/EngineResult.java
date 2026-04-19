package com.emergency.patient.ocr;

public class EngineResult {
    public String brand_name;
    public String drug_name;
    public String dosage;
    public String expiry_date;
    public int confidence;
    public String source; // "parser", "nano", "groq"
    public boolean isGrounded = false;

    public EngineResult(String brandName, String drugName, String dosage, String expiryDate, int confidence, String source) {
        this.brand_name = brandName != null ? brandName.trim() : "";
        this.drug_name = drugName != null ? drugName.trim() : "";
        this.dosage = dosage != null ? dosage.trim() : "";
        this.expiry_date = expiryDate != null ? expiryDate.trim() : "";
        this.confidence = confidence;
        this.source = source;
    }
}
