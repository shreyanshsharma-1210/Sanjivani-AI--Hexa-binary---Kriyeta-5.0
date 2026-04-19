package com.emergency.patient.luna.model;

import java.util.List;

/** Represents one drug/phase/nutrient contradiction from the knowledge base. */
public class Contradiction {
    public final String id;
    public final ContraType type;
    public final ContraSeverity severity;
    public final String drugA;
    public final String drugB;          // null for DRUG_PHASE / DRUG_NUTRIENT
    public final CyclePhase phaseTarget; // null unless DRUG_PHASE
    public final String nutrientTarget;  // null unless DRUG_NUTRIENT
    public final String summary;
    public final String mechanism;
    public final List<String> effects;
    public final String recommendation;
    public final boolean cycleSpecific;

    public Contradiction(String id, ContraType type, ContraSeverity severity,
                         String drugA, String drugB, CyclePhase phaseTarget,
                         String nutrientTarget, String summary, String mechanism,
                         List<String> effects, String recommendation, boolean cycleSpecific) {
        this.id = id;
        this.type = type;
        this.severity = severity;
        this.drugA = drugA;
        this.drugB = drugB;
        this.phaseTarget = phaseTarget;
        this.nutrientTarget = nutrientTarget;
        this.summary = summary;
        this.mechanism = mechanism;
        this.effects = effects;
        this.recommendation = recommendation;
        this.cycleSpecific = cycleSpecific;
    }
}
