package com.emergency.patient.ocr;

/**
 * NormalizationResult — Represents the outcome of checking a raw drug name
 * against the local normalization dictionary.
 */
public class NormalizationResult {
    public final String name;
    public final boolean isMatched;

    public NormalizationResult(String name, boolean isMatched) {
        this.name = name;
        this.isMatched = isMatched;
    }
}
