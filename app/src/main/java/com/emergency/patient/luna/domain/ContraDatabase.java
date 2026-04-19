package com.emergency.patient.luna.domain;

import com.emergency.patient.luna.model.Contradiction;
import com.emergency.patient.luna.model.ContraSeverity;
import com.emergency.patient.luna.model.ContraType;
import com.emergency.patient.luna.model.CyclePhase;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;

/**
 * Static knowledge base for drug–drug, drug–phase, and drug–nutrient contradictions.
 * All data is hardcoded — no network dependency — providing instant offline results.
 */
public class ContraDatabase {

    private static final List<Contradiction> ALL = Arrays.asList(

        // ═══ DRUG–DRUG ═══════════════════════════════════════════════════════

        new Contradiction(
            "ssri_hormonal",
            ContraType.DRUG_DRUG, ContraSeverity.CRITICAL,
            "sertraline", "hormonal_contraceptive",
            null, null,
            "SSRIs can reduce hormonal contraceptive efficacy and amplify cycle disruption.",
            "Sertraline inhibits CYP3A4, the enzyme responsible for metabolising synthetic " +
            "estrogens and progestins. Plasma hormone levels may fall 30–40%, reducing " +
            "contraceptive reliability. Combined serotonin + hormone fluctuations worsen PMDD.",
            Arrays.asList(
                "Reduced contraceptive reliability",
                "Cycle length shift of 3–7 days",
                "Worsens luteal-phase mood symptoms",
                "May elevate prolactin indirectly"
            ),
            "Discuss with prescriber. Consider non-hormonal contraception or confirm " +
            "overlap timing. Do not stop either medication without medical advice.",
            false
        ),

        new Contradiction(
            "ssri_thyroid",
            ContraType.DRUG_DRUG, ContraSeverity.MODERATE,
            "sertraline", "levothyroxine",
            null, null,
            "SSRIs can interfere with thyroid hormone absorption when taken close together.",
            "Sertraline may reduce absorption of levothyroxine by up to 25% if taken within " +
            "2 hours. Thyroid hormone directly regulates cycle regularity — even mild " +
            "hypothyroidism from reduced absorption can delay ovulation.",
            Arrays.asList(
                "Delayed ovulation",
                "Irregular cycles if thyroid levels affected",
                "Fatigue amplified"
            ),
            "Take levothyroxine at least 4 hours before or after sertraline. " +
            "Monitor TSH levels regularly.",
            false
        ),

        new Contradiction(
            "steroid_hormonal",
            ContraType.DRUG_DRUG, ContraSeverity.MODERATE,
            "prednisone", "hormonal_contraceptive",
            null, null,
            "Corticosteroids interact with hormonal contraceptives via shared metabolic pathways.",
            "Both prednisone and synthetic hormones compete for CYP3A4 metabolism. " +
            "This can raise effective steroid levels and reduce contraceptive reliability.",
            Arrays.asList(
                "Reduced contraceptive efficacy",
                "Fluid retention",
                "Cycle disruption"
            ),
            "Use backup contraception during steroid courses. Consult prescriber.",
            false
        ),

        // ═══ DRUG–PHASE ══════════════════════════════════════════════════════

        new Contradiction(
            "nsaid_luteal",
            ContraType.DRUG_PHASE, ContraSeverity.MODERATE,
            "ibuprofen", null,
            CyclePhase.LUTEAL, null,
            "NSAIDs during the luteal phase may inhibit prostaglandins needed for menstruation onset.",
            "Ibuprofen suppresses COX-1/COX-2 enzymes, reducing prostaglandin synthesis. " +
            "In the luteal phase, prostaglandins trigger uterine contractions that initiate " +
            "menstruation. Chronic use (3+ days) can delay or lighten periods.",
            Arrays.asList(
                "May delay period by 1–3 days",
                "Reduces ovulation marker visibility",
                "Can mask cramping that signals underlying issues"
            ),
            "Use sparingly (1–2 days max). Paracetamol is a safer alternative.",
            true
        ),

        new Contradiction(
            "nsaid_ovulation",
            ContraType.DRUG_PHASE, ContraSeverity.MODERATE,
            "ibuprofen", null,
            CyclePhase.OVULATION, null,
            "NSAID use during the ovulation window may inhibit or delay ovulation (LUF syndrome).",
            "High-dose NSAIDs around ovulation can prevent the LH-triggered follicle rupture, " +
            "causing Luteinised Unruptured Follicle (LUF) syndrome — the egg is never released.",
            Arrays.asList(
                "Ovulation may not occur",
                "Luteal phase deficiency risk",
                "Can cause missed period despite no pregnancy"
            ),
            "Avoid NSAIDs during the 3 days surrounding predicted ovulation.",
            true
        ),

        new Contradiction(
            "antipsychotic_all",
            ContraType.DRUG_PHASE, ContraSeverity.MODERATE,
            "risperidone", null,
            null, null,
            "Antipsychotics raise prolactin, which can suppress LH/FSH and halt ovulation.",
            "Dopamine-blocking antipsychotics increase prolactin secretion. Elevated prolactin " +
            "suppresses GnRH, reducing FSH and LH — the hormones needed to trigger ovulation. " +
            "This can cause oligomenorrhoea or amenorrhoea.",
            Arrays.asList(
                "Irregular or absent periods",
                "Anovulatory cycles",
                "Reduced fertility window accuracy"
            ),
            "Inform gynaecologist about antipsychotic use. Prolactin levels should be monitored.",
            false
        ),

        // ═══ DRUG–NUTRIENT ════════════════════════════════════════════════════

        new Contradiction(
            "metformin_b12_iron",
            ContraType.DRUG_NUTRIENT, ContraSeverity.INFORMATIONAL,
            "metformin", null,
            null, "Iron / B12",
            "Long-term Metformin use reduces B12 and iron absorption, relevant post-menstruation.",
            "Metformin reduces absorption of Vitamin B12 (up to 30%) by competing for " +
            "intrinsic-factor-mediated uptake in the ileum. Iron losses during menstruation " +
            "compound this risk, increasing mild anaemia likelihood.",
            Arrays.asList(
                "Increased anaemia risk post-period",
                "Fatigue and brain fog",
                "May worsen menstrual-phase energy dip"
            ),
            "Monitor B12 and iron levels annually. Increase iron-rich foods post-period. " +
            "Consider B12 supplementation.",
            true
        ),

        new Contradiction(
            "ssri_magnesium",
            ContraType.DRUG_NUTRIENT, ContraSeverity.INFORMATIONAL,
            "sertraline", null,
            null, "Magnesium",
            "SSRIs may deplete magnesium over time, which is critical for reducing PMS symptoms.",
            "Chronic SSRI use is associated with reduced intracellular magnesium levels. " +
            "Magnesium deficiency amplifies luteal-phase anxiety, cramps, and sleep disruption.",
            Arrays.asList(
                "Worsened PMS symptoms",
                "Increased cramping",
                "Sleep quality reduction in luteal phase"
            ),
            "Supplement with magnesium glycinate 200–400mg in the luteal phase. " +
            "Increase magnesium-rich foods: dark chocolate, spinach, almonds.",
            true
        )
    );

    // ─── Public query methods ─────────────────────────────────────────────────

    /** Returns all contradictions that involve any of the given drug names. */
    public static List<Contradiction> findForDrugs(List<String> drugNames) {
        List<String> lower = toLower(drugNames);
        List<Contradiction> result = new ArrayList<>();
        for (Contradiction c : ALL) {
            if (matchesDrug(c, lower)) result.add(c);
        }
        return result;
    }

    /** Returns DRUG_PHASE contradictions active in the given phase that involve user's drugs. */
    public static List<Contradiction> findForPhase(CyclePhase phase, List<String> drugNames) {
        List<String> lower = toLower(drugNames);
        List<Contradiction> result = new ArrayList<>();
        for (Contradiction c : ALL) {
            if (c.type == ContraType.DRUG_PHASE
                    && (c.phaseTarget == null || c.phaseTarget == phase)
                    && containsDrug(lower, c.drugA)) {
                result.add(c);
            }
        }
        return result;
    }

    /** Returns DRUG_NUTRIENT contradictions for the given drug list. */
    public static List<Contradiction> findNutrientConflicts(List<String> drugNames) {
        List<String> lower = toLower(drugNames);
        List<Contradiction> result = new ArrayList<>();
        for (Contradiction c : ALL) {
            if (c.type == ContraType.DRUG_NUTRIENT && containsDrug(lower, c.drugA)) {
                result.add(c);
            }
        }
        return result;
    }

    /** Full analysis — deduplicates and sorts CRITICAL → MODERATE → INFORMATIONAL. */
    public static List<Contradiction> analyzeAll(List<String> drugNames, CyclePhase phase) {
        List<Contradiction> combined = new ArrayList<>();
        combined.addAll(findForDrugs(drugNames));
        combined.addAll(findForPhase(phase, drugNames));
        combined.addAll(findNutrientConflicts(drugNames));

        // Deduplicate by id
        List<Contradiction> deduped = new ArrayList<>();
        List<String> seen = new ArrayList<>();
        for (Contradiction c : combined) {
            if (!seen.contains(c.id)) { seen.add(c.id); deduped.add(c); }
        }

        // Sort: CRITICAL first
        deduped.sort((a, b) -> a.severity.ordinal() - b.severity.ordinal());
        return deduped;
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private static List<String> toLower(List<String> list) {
        List<String> result = new ArrayList<>();
        for (String s : list) if (s != null) result.add(s.toLowerCase());
        return result;
    }

    private static boolean matchesDrug(Contradiction c, List<String> lower) {
        return containsDrug(lower, c.drugA) ||
               (c.drugB != null && containsDrug(lower, c.drugB));
    }

    private static boolean containsDrug(List<String> lower, String drugKey) {
        if (drugKey == null) return false;
        String dk = drugKey.toLowerCase();
        for (String d : lower) {
            if (d.contains(dk) || dk.contains(d)) return true;
        }
        return false;
    }
}
