# 🌸 Luna v2 — Full Kotlin Implementation Plan
## Includes: Contradiction Engine · Hormone Trends · Symptom Logger · Predictions · Health Score

---

## 📁 Updated Project Structure

```
feature/cycle/
├── ui/
│   ├── CycleFragment.kt
│   ├── CycleViewModel.kt
│   ├── tabs/
│   │   ├── OverviewTabFragment.kt
│   │   ├── TrendsTabFragment.kt
│   │   ├── MedicationsTabFragment.kt
│   │   └── NutritionTabFragment.kt
│   └── views/
│       ├── MoonRingView.kt
│       ├── CycleCalendarView.kt
│       ├── HormoneChartView.kt         ← NEW: canvas trend chart
│       ├── ContraEngineCardView.kt     ← NEW: contradiction list
│       └── SymptomLoggerView.kt        ← NEW: interactive logger
│
├── domain/
│   ├── InsightEngine.kt
│   ├── CycleAnalyzer.kt
│   ├── ContraEngine.kt                 ← NEW: core contradiction system
│   ├── ContraDatabase.kt               ← NEW: interaction knowledge base
│   ├── HealthScoreEngine.kt            ← NEW: composite 0–100 score
│   ├── PredictionEngine.kt             ← NEW: period/fertile/PMS forecasting
│   ├── SymptomCorrelator.kt            ← NEW: symptom ↔ phase ↔ drug mapping
│   ├── MedFlagEngine.kt
│   └── NutritionEngine.kt
│
├── data/
│   ├── model/
│   │   ├── CycleEntry.kt
│   │   ├── VitalsEntry.kt
│   │   ├── SymptomEntry.kt             ← NEW
│   │   ├── MedEntry.kt                 ← NEW (full model)
│   │   ├── Contradiction.kt            ← NEW
│   │   ├── InsightResult.kt
│   │   └── HealthSnapshot.kt           ← NEW: full daily snapshot
│   ├── repository/
│   │   ├── CycleRepository.kt
│   │   ├── VitalsRepository.kt
│   │   ├── SymptomRepository.kt        ← NEW
│   │   └── MedRepository.kt            ← NEW
│   └── db/
│       ├── CycleDao.kt
│       ├── VitalsDao.kt
│       ├── SymptomDao.kt               ← NEW
│       └── MedDao.kt                   ← NEW
└── res/
    ├── raw/
    │   └── contra_database.json        ← NEW: interaction knowledge base
    └── font/
        └── cormorant_garamond.ttf
```

---

## ═══════════════════════════════
## MODULE 1 — CONTRADICTION ENGINE
## ═══════════════════════════════

### 1.1 Contradiction Data Models

```kotlin
// Contradiction.kt
enum class ContraSeverity { CRITICAL, MODERATE, INFORMATIONAL }

enum class ContraType {
    DRUG_DRUG,           // e.g. SSRI + hormonal contraceptive
    DRUG_PHASE,          // e.g. NSAID during luteal phase
    DRUG_NUTRIENT,       // e.g. Metformin + iron/B12 absorption
    DRUG_VITALS          // e.g. beta-blocker masking HR elevation
}

data class Contradiction(
    val id: String,
    val type: ContraType,
    val severity: ContraSeverity,
    val drugA: String,                 // null if vs phase/nutrient
    val drugB: String?,                // null for DRUG_PHASE/DRUG_NUTRIENT
    val phaseTarget: CyclePhase?,      // set for DRUG_PHASE
    val nutrientTarget: String?,       // e.g. "Iron", "B12"
    val summary: String,               // one-line description
    val mechanism: String,             // full clinical explanation
    val effects: List<String>,         // bullet effects
    val recommendation: String,        // what to do
    val cycleSpecific: Boolean = false // is this phase-dependent?
)
```

---

### 1.2 Contradiction Knowledge Base

```kotlin
// ContraDatabase.kt
object ContraDatabase {

    val interactions: List<Contradiction> = listOf(

        // ══ DRUG–DRUG ══════════════════════════════════════════

        Contradiction(
            id = "ssri_hormonal",
            type = ContraType.DRUG_DRUG,
            severity = ContraSeverity.CRITICAL,
            drugA = "sertraline", drugB = "hormonal_contraceptive",
            phaseTarget = null, nutrientTarget = null,
            summary = "SSRIs can reduce hormonal contraceptive efficacy and amplify cycle disruption.",
            mechanism = "Sertraline inhibits CYP3A4, the enzyme responsible for metabolising synthetic " +
                        "estrogens and progestins. Plasma hormone levels may fall 30–40%, reducing " +
                        "contraceptive reliability. Combined serotonin + hormone fluctuations worsen PMDD.",
            effects = listOf(
                "Reduced contraceptive reliability",
                "Cycle length shift of 3–7 days",
                "Worsens luteal-phase mood symptoms",
                "May elevate prolactin indirectly"
            ),
            recommendation = "Discuss with prescriber. Consider non-hormonal contraception or confirm " +
                             "overlap timing. Do not stop either medication without medical advice.",
            cycleSpecific = false
        ),

        Contradiction(
            id = "ssri_thyroid",
            type = ContraType.DRUG_DRUG,
            severity = ContraSeverity.MODERATE,
            drugA = "sertraline", drugB = "levothyroxine",
            phaseTarget = null, nutrientTarget = null,
            summary = "SSRIs can interfere with thyroid hormone absorption when taken close together.",
            mechanism = "Sertraline may reduce absorption of levothyroxine by up to 25% if taken " +
                        "within 2 hours. Thyroid hormone directly regulates cycle regularity — " +
                        "even mild hypothyroidism from reduced absorption can delay ovulation.",
            effects = listOf(
                "Delayed ovulation",
                "Irregular cycles if thyroid levels affected",
                "Fatigue amplified"
            ),
            recommendation = "Take levothyroxine at least 4 hours before or after sertraline. " +
                             "Monitor TSH levels regularly.",
            cycleSpecific = false
        ),

        Contradiction(
            id = "steroid_hormonal",
            type = ContraType.DRUG_DRUG,
            severity = ContraSeverity.MODERATE,
            drugA = "prednisone", drugB = "hormonal_contraceptive",
            phaseTarget = null, nutrientTarget = null,
            summary = "Corticosteroids interact with hormonal contraceptives via shared metabolic pathways.",
            mechanism = "Both prednisone and synthetic hormones compete for CYP3A4 metabolism. " +
                        "This can raise effective steroid levels and reduce contraceptive reliability.",
            effects = listOf("Reduced contraceptive efficacy", "Fluid retention", "Cycle disruption"),
            recommendation = "Use backup contraception during steroid courses. Consult prescriber.",
            cycleSpecific = false
        ),

        // ══ DRUG–PHASE ═════════════════════════════════════════

        Contradiction(
            id = "nsaid_luteal",
            type = ContraType.DRUG_PHASE,
            severity = ContraSeverity.MODERATE,
            drugA = "ibuprofen", drugB = null,
            phaseTarget = CyclePhase.LUTEAL, nutrientTarget = null,
            summary = "NSAIDs during the luteal phase may inhibit prostaglandins needed for menstruation onset.",
            mechanism = "Ibuprofen suppresses COX-1/COX-2 enzymes, reducing prostaglandin synthesis. " +
                        "In the luteal phase, prostaglandins trigger uterine contractions that initiate " +
                        "menstruation. Chronic use (3+ days) can delay or lighten periods.",
            effects = listOf(
                "May delay period by 1–3 days",
                "Reduces ovulation marker visibility",
                "Can mask cramping that signals underlying issues"
            ),
            recommendation = "Use sparingly (1–2 days max). Paracetamol is a safer alternative.",
            cycleSpecific = true
        ),

        Contradiction(
            id = "nsaid_ovulation",
            type = ContraType.DRUG_PHASE,
            severity = ContraSeverity.MODERATE,
            drugA = "ibuprofen", drugB = null,
            phaseTarget = CyclePhase.OVULATION, nutrientTarget = null,
            summary = "NSAID use during the ovulation window may inhibit or delay ovulation (LUF syndrome).",
            mechanism = "High-dose NSAIDs around ovulation can prevent the LH-triggered follicle rupture, " +
                        "causing Luteinised Unruptured Follicle (LUF) syndrome — the egg is never released.",
            effects = listOf(
                "Ovulation may not occur",
                "Luteal phase deficiency risk",
                "Can cause missed period despite no pregnancy"
            ),
            recommendation = "Avoid NSAIDs during the 3 days surrounding predicted ovulation.",
            cycleSpecific = true
        ),

        Contradiction(
            id = "antipsychotic_all_phases",
            type = ContraType.DRUG_PHASE,
            severity = ContraSeverity.MODERATE,
            drugA = "risperidone", drugB = null,
            phaseTarget = null, nutrientTarget = null,
            summary = "Antipsychotics raise prolactin, which can suppress LH/FSH and halt ovulation.",
            mechanism = "Dopamine-blocking antipsychotics increase prolactin secretion. Elevated prolactin " +
                        "suppresses GnRH, reducing FSH and LH — the hormones needed to trigger ovulation. " +
                        "This can cause oligomenorrhoea (infrequent periods) or amenorrhoea (absent periods).",
            effects = listOf(
                "Irregular or absent periods",
                "Anovulatory cycles",
                "Reduced fertility window accuracy"
            ),
            recommendation = "Inform gynaecologist about antipsychotic use. Prolactin levels should " +
                             "be monitored periodically.",
            cycleSpecific = false
        ),

        // ══ DRUG–NUTRIENT ═══════════════════════════════════════

        Contradiction(
            id = "metformin_b12_iron",
            type = ContraType.DRUG_NUTRIENT,
            severity = ContraSeverity.INFORMATIONAL,
            drugA = "metformin", drugB = null,
            phaseTarget = null, nutrientTarget = "Iron / B12",
            summary = "Long-term Metformin use reduces B12 and iron absorption, relevant post-menstruation.",
            mechanism = "Metformin reduces absorption of Vitamin B12 (up to 30%) by competing for " +
                        "intrinsic-factor-mediated uptake in the ileum. Iron losses during menstruation " +
                        "compound this risk, increasing mild anaemia likelihood.",
            effects = listOf(
                "Increased anaemia risk post-period",
                "Fatigue and brain fog",
                "May worsen menstrual-phase energy dip"
            ),
            recommendation = "Monitor B12 and iron levels annually. Increase iron-rich foods post-period. " +
                             "Consider B12 supplementation.",
            cycleSpecific = true
        ),

        Contradiction(
            id = "ssri_magnesium",
            type = ContraType.DRUG_NUTRIENT,
            severity = ContraSeverity.INFORMATIONAL,
            drugA = "sertraline", drugB = null,
            phaseTarget = null, nutrientTarget = "Magnesium",
            summary = "SSRIs may deplete magnesium over time, which is critical for reducing PMS symptoms.",
            mechanism = "Chronic SSRI use is associated with reduced intracellular magnesium levels. " +
                        "Magnesium deficiency amplifies luteal-phase anxiety, cramps, and sleep disruption.",
            effects = listOf(
                "Worsened PMS symptoms",
                "Increased cramping",
                "Sleep quality reduction in luteal phase"
            ),
            recommendation = "Supplement with magnesium glycinate 200–400mg in the luteal phase. " +
                             "Increase magnesium-rich foods: dark chocolate, spinach, almonds.",
            cycleSpecific = true
        ),
    )

    // ── Query helpers ─────────────────────────────────────────

    fun findForDrugs(drugList: List<String>): List<Contradiction> {
        val lower = drugList.map { it.lowercase() }
        return interactions.filter { contra ->
            lower.any { d ->
                contra.drugA.lowercase().contains(d) ||
                contra.drugB?.lowercase()?.contains(d) == true
            }
        }
    }

    fun findForPhase(phase: CyclePhase, drugList: List<String>): List<Contradiction> {
        val lower = drugList.map { it.lowercase() }
        return interactions.filter { contra ->
            contra.type == ContraType.DRUG_PHASE &&
            (contra.phaseTarget == null || contra.phaseTarget == phase) &&
            lower.any { d -> contra.drugA.lowercase().contains(d) }
        }
    }

    fun findNutrientConflicts(drugList: List<String>): List<Contradiction> {
        val lower = drugList.map { it.lowercase() }
        return interactions.filter { contra ->
            contra.type == ContraType.DRUG_NUTRIENT &&
            lower.any { d -> contra.drugA.lowercase().contains(d) }
        }
    }
}
```

---

### 1.3 ContraEngine — Main Logic

```kotlin
// ContraEngine.kt
class ContraEngine @Inject constructor(
    private val medRepo: MedRepository
) {
    data class ContraResult(
        val allContradictions: List<Contradiction>,
        val criticalCount: Int,
        val moderateCount: Int,
        val infoCount: Int,
        val phaseSpecificAlerts: List<Contradiction>,
        val nutrientAlerts: List<Contradiction>,
        val hasCritical: Boolean
    )

    suspend fun analyze(currentPhase: CyclePhase): ContraResult {
        val activeMeds = medRepo.getActiveMedications()
        val drugNames  = activeMeds.map { it.genericName }

        val drugDrug    = ContraDatabase.findForDrugs(drugNames)
        val drugPhase   = ContraDatabase.findForPhase(currentPhase, drugNames)
        val drugNutrient = ContraDatabase.findNutrientConflicts(drugNames)

        // Deduplicate (drug-phase also appears in findForDrugs sometimes)
        val all = (drugDrug + drugPhase + drugNutrient)
            .distinctBy { it.id }
            .sortedByDescending { it.severity.ordinal.unaryMinus() }
            // CRITICAL first, then MODERATE, then INFORMATIONAL

        return ContraResult(
            allContradictions  = all,
            criticalCount      = all.count { it.severity == ContraSeverity.CRITICAL },
            moderateCount      = all.count { it.severity == ContraSeverity.MODERATE },
            infoCount          = all.count { it.severity == ContraSeverity.INFORMATIONAL },
            phaseSpecificAlerts = drugPhase,
            nutrientAlerts      = drugNutrient,
            hasCritical         = all.any { it.severity == ContraSeverity.CRITICAL }
        )
    }
}
```

---

### 1.4 ContraEngineCardView (UI component)

```kotlin
// ContraEngineCardView.kt
class ContraEngineCardView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : LinearLayout(context, attrs) {

    init { orientation = VERTICAL }

    fun bind(result: ContraEngine.ContraResult) {
        removeAllViews()
        // Header summary card
        addView(buildHeaderCard(result))
        // Each contradiction item
        result.allContradictions.forEach { contra ->
            addView(buildContraItem(contra))
        }
    }

    private fun buildHeaderCard(result: ContraEngine.ContraResult): View {
        // Inflate header_contra_card.xml:
        //  - Shows icon, "N Interactions Detected"
        //  - Severity badge row: "X Critical / Y Moderate / Z Info"
        //  - Phase-specific alert if any
        return LayoutInflater.from(context)
            .inflate(R.layout.header_contra_card, this, false).apply {
                findViewById<TextView>(R.id.tvContraCount).text =
                    "${result.allContradictions.size} Interactions Detected"
                // bind badge counts...
                // bind phase alert if result.phaseSpecificAlerts.isNotEmpty()
            }
    }

    private fun buildContraItem(contra: Contradiction): View {
        // Inflate item_contra.xml:
        //  - Left accent bar (red/orange/teal by severity)
        //  - Drug pills: "DrugA ✕ DrugB/Phase/Nutrient"
        //  - Severity badge
        //  - Summary text
        //  - Effects list (collapsed by default)
        //  - Expand/collapse on tap
        //  - Action buttons: "Share with Doctor" / "See Alternatives" / "More Info"
        return LayoutInflater.from(context)
            .inflate(R.layout.item_contra, this, false).apply {
                // Set left border color by severity
                val borderColor = when(contra.severity) {
                    ContraSeverity.CRITICAL     -> 0xFFE05555.toInt()
                    ContraSeverity.MODERATE     -> 0xFFE0935A.toInt()
                    ContraSeverity.INFORMATIONAL -> 0xFF5BBFB5.toInt()
                }
                // Set pill text, summary, effects...
                // Set expand toggle listener
                setOnClickListener {
                    val expanded = findViewWithTag<View>("expanded")
                    expanded.isVisible = !expanded.isVisible
                }
            }
    }
}
```

---

## ═══════════════════════════════
## MODULE 2 — HEALTH SCORE ENGINE
## ═══════════════════════════════

```kotlin
// HealthScoreEngine.kt
class HealthScoreEngine @Inject constructor() {

    data class HealthScore(
        val total: Int,           // 0–100
        val cycleScore: Int,      // 0–30
        val vitalsScore: Int,     // 0–25
        val symptomsScore: Int,   // 0–20
        val medScore: Int,        // 0–25 (inversely from contradictions)
        val label: String,
        val trend: Int            // +/- vs yesterday
    )

    fun calculate(
        insight: InsightResult,
        contraResult: ContraEngine.ContraResult,
        symptoms: List<SymptomEntry>,
        vitals: VitalsEntry?
    ): HealthScore {

        // ── Cycle score (0–30) ───────────────────────────────
        var cycleScore = 30
        if (insight.confidence == Confidence.MEDIUM) cycleScore -= 8
        if (insight.confidence == Confidence.HIGH)   cycleScore -= 15  // anomaly = HIGH
        if (insight.isEstimated) cycleScore -= 5

        // ── Vitals score (0–25) ──────────────────────────────
        var vitalsScore = 25
        vitals?.let {
            it.tempCelsius?.let { t ->
                if (kotlin.math.abs(t - 37.0f) > 0.8f) vitalsScore -= 6
            }
            it.heartRateBpm?.let { hr ->
                if (hr > 95 || hr < 50) vitalsScore -= 6
            }
            it.spO2Percent?.let { spo2 ->
                if (spo2 < 95) vitalsScore -= 8
            }
            it.hrv?.let { hrv ->
                if (hrv < 25) vitalsScore -= 5
            }
        }

        // ── Symptom score (0–20) — fewer severe = better ─────
        var symptomsScore = 20
        val severeSymptoms = symptoms.filter { it.intensity >= 7 }
        symptomsScore -= minOf(severeSymptoms.size * 4, 20)

        // ── Medication score (0–25, inverted from conflicts) ──
        var medScore = 25
        medScore -= contraResult.criticalCount * 12
        medScore -= contraResult.moderateCount * 5
        medScore -= contraResult.infoCount * 2
        medScore = medScore.coerceAtLeast(0)

        val total = (cycleScore + vitalsScore + symptomsScore + medScore).coerceIn(0, 100)
        val label = when {
            total >= 80 -> "Excellent"
            total >= 65 -> "Good"
            total >= 50 -> "Fair"
            total >= 35 -> "Needs Attention"
            else        -> "Poor — Review Recommended"
        }

        return HealthScore(total, cycleScore, vitalsScore, symptomsScore, medScore, label, trend = +2)
    }
}
```

---

## ═══════════════════════════════
## MODULE 3 — PREDICTION ENGINE
## ═══════════════════════════════

```kotlin
// PredictionEngine.kt
class PredictionEngine @Inject constructor() {

    data class Predictions(
        val nextPeriodDate: LocalDate,
        val nextPeriodDaysAway: Int,
        val fertileWindowStart: LocalDate,
        val fertileWindowEnd: LocalDate,
        val ovulationDate: LocalDate,
        val pmsWindowStart: LocalDate,
        val pmsWindowEnd: LocalDate,
        val confidenceLabel: String
    )

    fun predict(cycles: List<CycleEntry>, today: LocalDate = LocalDate.now()): Predictions {
        val avgLength = if (cycles.size >= 2) {
            cycles.takeLast(3)
                .zipWithNext { a, b -> ChronoUnit.DAYS.between(
                    Instant.ofEpochMilli(a.startDate).atZone(ZoneId.systemDefault()).toLocalDate(),
                    Instant.ofEpochMilli(b.startDate).atZone(ZoneId.systemDefault()).toLocalDate()
                ).toInt() }
                .average().toInt()
        } else 28

        val lastStart = Instant.ofEpochMilli(cycles.last().startDate)
            .atZone(ZoneId.systemDefault()).toLocalDate()

        val nextPeriod  = lastStart.plusDays(avgLength.toLong())
        val ovulation   = lastStart.plusDays((avgLength - 14).toLong())
        val fertileStart = ovulation.minusDays(4)
        val fertileEnd  = ovulation.plusDays(2)
        val pmsStart    = nextPeriod.minusDays(7)
        val pmsEnd      = nextPeriod.minusDays(1)

        val daysAway = ChronoUnit.DAYS.between(today, nextPeriod).toInt()

        return Predictions(
            nextPeriodDate    = nextPeriod,
            nextPeriodDaysAway = daysAway.coerceAtLeast(0),
            fertileWindowStart = fertileStart,
            fertileWindowEnd  = fertileEnd,
            ovulationDate     = ovulation,
            pmsWindowStart    = pmsStart,
            pmsWindowEnd      = pmsEnd,
            confidenceLabel   = if (cycles.size >= 3) "Based on your data" else "Estimated"
        )
    }
}
```

---

## ═══════════════════════════════
## MODULE 4 — SYMPTOM LOGGER
## ═══════════════════════════════

```kotlin
// SymptomEntry.kt
@Entity(tableName = "symptom_entries")
data class SymptomEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: Long,                    // epoch ms (day only, zeroed to midnight)
    val symptoms: List<String>,        // e.g. ["cramps", "fatigue", "bloating"]
    val intensity: Int,                // 1–10
    val notes: String = "",
    val cycleDay: Int,
    val phase: String                  // phase label at time of logging
)

// SymptomType.kt (enum for structured logging)
enum class SymptomType(val emoji: String, val label: String) {
    CRAMPS("😣", "Cramps"),
    HEADACHE("💢", "Headache"),
    FATIGUE("😔", "Fatigue"),
    BLOATING("🫀", "Bloating"),
    MOOD_LOW("😤", "Mood ↓"),
    HOT_FLASH("🌡", "Hot Flash"),
    INSOMNIA("😴", "Insomnia"),
    ENERGISED("✨", "Energised"),
    BREAST_PAIN("⚡", "Breast Pain"),
    NAUSEA("🤢", "Nausea"),
    ACNE("💊", "Acne"),
    BACK_PAIN("🦴", "Back Pain")
}
```

---

## ═══════════════════════════════
## MODULE 5 — HORMONE CHART VIEW
## ═══════════════════════════════

```kotlin
// HormoneChartView.kt — Canvas-based multi-line chart
class HormoneChartView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private val tempPoints = mutableListOf<Float>()
    private val hrPoints   = mutableListOf<Float>()
    private val hrvPoints  = mutableListOf<Float>()
    private var cycleLength = 28
    private var currentDay  = 1

    private val tempPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 4f
        color = 0xFFE8637A.toInt(); strokeCap = Paint.Cap.ROUND
        pathEffect = CornerPathEffect(16f)
    }
    private val hrPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 3f
        color = 0xFFD4A76A.toInt(); strokeCap = Paint.Cap.ROUND
        pathEffect = DashPathEffect(floatArrayOf(10f, 5f), 0f)
    }
    private val hrvPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 2.5f
        color = 0xFF5BBFB5.toInt(); strokeCap = Paint.Cap.ROUND
        alpha = 180
    }
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 1f; color = 0x0AFFFFFF
    }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        maskFilter = BlurMaskFilter(12f, BlurMaskFilter.Blur.NORMAL)
    }
    private val phaseShade = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    fun setData(temps: List<Float>, hrs: List<Float>, hrvs: List<Float>,
                cycleLen: Int, curDay: Int) {
        tempPoints.clear(); tempPoints.addAll(temps)
        hrPoints.clear();   hrPoints.addAll(hrs)
        hrvPoints.clear();  hrvPoints.addAll(hrvs)
        cycleLength = cycleLen; currentDay = curDay
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat(); val h = height.toFloat()
        val padL = 16f; val padR = 16f; val padT = 12f; val padB = 28f
        val chartW = w - padL - padR
        val chartH = h - padT - padB

        // Phase backgrounds
        drawPhaseShading(canvas, padL, padT, chartW, chartH)

        // Grid lines
        repeat(4) { i ->
            val y = padT + chartH * (i + 1) / 5f
            canvas.drawLine(padL, y, padL + chartW, y, gridPaint)
        }

        // Phase divider lines
        listOf(5, 13, 15).forEach { day ->
            val x = padL + (day.toFloat() / cycleLength) * chartW
            canvas.drawLine(x, padT, x, padT + chartH, gridPaint)
        }

        // Current day marker line
        val cx = padL + (currentDay.toFloat() / cycleLength) * chartW
        canvas.drawLine(cx, padT, cx, padT + chartH,
            Paint().apply { color = 0x33C487B8; strokeWidth = 2f; style = Paint.Style.STROKE })

        // Draw lines
        drawLine(canvas, tempPoints, tempPaint, padL, padT, chartW, chartH, 36.2f, 38f)
        drawLine(canvas, hrPoints,   hrPaint,   padL, padT, chartW, chartH, 55f,   105f)
        drawLine(canvas, hrvPoints,  hrvPaint,  padL, padT, chartW, chartH, 10f,   80f)

        // Current day dot
        if (currentDay <= tempPoints.size) {
            val y = normalise(tempPoints[currentDay - 1], 36.2f, 38f, padT, chartH)
            glowPaint.color = 0xFFC487B8.toInt()
            canvas.drawCircle(cx, y, 14f, glowPaint)
            canvas.drawCircle(cx, y, 7f, Paint().apply {
                color = 0xFFC487B8.toInt(); style = Paint.Style.FILL
            })
        }
    }

    private fun drawLine(canvas: Canvas, points: List<Float>, paint: Paint,
                         padL: Float, padT: Float, cw: Float, ch: Float,
                         minV: Float, maxV: Float) {
        if (points.isEmpty()) return
        val path = Path()
        points.forEachIndexed { i, v ->
            val x = padL + (i.toFloat() / (points.size - 1)) * cw
            val y = normalise(v, minV, maxV, padT, ch)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        canvas.drawPath(path, paint)
    }

    private fun normalise(v: Float, min: Float, max: Float, padT: Float, h: Float): Float {
        return padT + h - ((v - min) / (max - min)) * h
    }

    private fun drawPhaseShading(canvas: Canvas, padL: Float, padT: Float,
                                  cw: Float, ch: Float) {
        val phases = listOf(
            Triple(0, 5,  0x0DE8637A.toInt()),
            Triple(5, 13, 0x0DC487B8.toInt()),
            Triple(13,15, 0x14D4A76A.toInt()),
            Triple(15, cycleLength, 0x0D7C3D6E.toInt())
        )
        phases.forEach { (s, e, col) ->
            val x1 = padL + (s.toFloat() / cycleLength) * cw
            val x2 = padL + (e.toFloat() / cycleLength) * cw
            phaseShade.color = col
            canvas.drawRect(x1, padT, x2, padT + ch, phaseShade)
        }
    }
}
```

---

## ═══════════════════════════════
## MODULE 6 — UPDATED VIEW MODEL
## ═══════════════════════════════

```kotlin
@HiltViewModel
class CycleViewModel @Inject constructor(
    private val insightEngine:   InsightEngine,
    private val contraEngine:    ContraEngine,
    private val healthScoreEngine: HealthScoreEngine,
    private val predictionEngine: PredictionEngine,
    private val cycleRepo:       CycleRepository,
    private val vitalsRepo:      VitalsRepository,
    private val symptomRepo:     SymptomRepository,
    private val medRepo:         MedRepository
) : ViewModel() {

    data class CycleUiState(
        val loading: Boolean = true,
        val insight: InsightResult? = null,
        val contraResult: ContraEngine.ContraResult? = null,
        val healthScore: HealthScoreEngine.HealthScore? = null,
        val predictions: PredictionEngine.Predictions? = null,
        val todaySymptoms: List<SymptomEntry> = emptyList(),
        val vitals: VitalsEntry? = null,
        val meds: List<MedEntry> = emptyList(),
        val error: String? = null
    )

    private val _state = MutableStateFlow(CycleUiState())
    val state: StateFlow<CycleUiState> = _state.asStateFlow()

    init { loadAll() }

    fun loadAll() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true) }
            try {
                // Run heavy work in parallel
                val insightDeferred   = async { insightEngine.buildInsight() }
                val vitalsDeferred    = async { vitalsRepo.getLatest() }
                val symptomsDeferred  = async { symptomRepo.getToday() }
                val medsDeferred      = async { medRepo.getActiveMedications() }
                val cyclesDeferred    = async { cycleRepo.getRecentCycles(6) }

                val insight  = insightDeferred.await()
                val vitals   = vitalsDeferred.await()
                val symptoms = symptomsDeferred.await()
                val meds     = medsDeferred.await()
                val cycles   = cyclesDeferred.await()

                // Sequential (depends on insight)
                val contra = contraEngine.analyze(insight.phase)
                val score  = healthScoreEngine.calculate(insight, contra, symptoms, vitals)
                val preds  = predictionEngine.predict(cycles)

                _state.update { CycleUiState(
                    loading      = false,
                    insight      = insight,
                    contraResult = contra,
                    healthScore  = score,
                    predictions  = preds,
                    todaySymptoms = symptoms,
                    vitals       = vitals,
                    meds         = meds
                )}

            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = e.message) }
            }
        }
    }

    fun logSymptoms(selected: List<SymptomType>, intensity: Int) {
        viewModelScope.launch {
            val entry = SymptomEntry(
                date     = System.currentTimeMillis(),
                symptoms = selected.map { it.name },
                intensity = intensity,
                cycleDay = state.value.insight?.dayOfCycle ?: 1,
                phase    = state.value.insight?.phase?.label ?: "Unknown"
            )
            symptomRepo.insert(entry)
            loadAll()
        }
    }

    fun onPeriodStarted() {
        viewModelScope.launch {
            cycleRepo.insertCycle(CycleEntry(startDate = System.currentTimeMillis()))
            loadAll()
        }
    }
}
```

---

## ═══════════════════════════════
## MODULE 7 — FRAGMENT BINDING
## ═══════════════════════════════

```kotlin
@AndroidEntryPoint
class CycleFragment : Fragment(R.layout.fragment_cycle) {
    private val vm: CycleViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.state.collectLatest { state ->
                    if (state.loading) { showSkeleton(); return@collectLatest }
                    hideSkeleton()

                    state.insight?.let        { bindMoonRing(it) }
                    state.vitals?.let         { bindVitals(it, state.insight) }
                    state.healthScore?.let    { bindHealthScore(it) }
                    state.predictions?.let    { bindPredictions(it) }
                    state.contraResult?.let   { bindContraEngine(it, state.insight?.phase) }
                    state.meds.let            { bindMedTimeline(it) }
                    state.todaySymptoms.let   { bindSymptomLogger(it) }
                    state.insight?.let        { bindHormoneTrend(it) }
                }
            }
        }
        setupSymptomLogger()
        animateEntrance()
    }

    private fun bindContraEngine(result: ContraEngine.ContraResult, phase: CyclePhase?) {
        binding.contraEngineCard.bind(result)
        // Update bottom nav badge
        val badgeCount = result.criticalCount + result.moderateCount
        binding.bottomNav.getOrCreateBadge(R.id.nav_meds).apply {
            isVisible = badgeCount > 0
            number = badgeCount
        }
    }

    private fun setupSymptomLogger() {
        binding.btnLogSymptoms.setOnClickListener {
            val selected  = binding.symptomGrid.getSelectedSymptoms()
            val intensity = binding.intensitySlider.value.toInt()
            vm.logSymptoms(selected, intensity)
            showLoggedToast()
        }
    }

    private fun animateEntrance() {
        val sections = listOf(
            binding.moonSection, binding.vitalsSection, binding.chartSection,
            binding.contraSection, binding.predSection, binding.symptomSection,
            binding.calSection, binding.insightSection
        )
        sections.forEachIndexed { i, v ->
            v.alpha = 0f; v.translationY = 30f
            v.animate().alpha(1f).translationY(0f)
                .setStartDelay(i * 80L).setDuration(420L)
                .setInterpolator(DecelerateInterpolator(1.4f)).start()
        }
    }
}
```

---

## ═══════════════════════════════
## BUILD ORDER (Step-by-step)
## ═══════════════════════════════

```
Step 1  → Data models: CycleEntry, VitalsEntry, SymptomEntry, MedEntry, Contradiction
Step 2  → Room DB setup: all DAOs + migrations
Step 3  → Repository layer (all 4 repos)
Step 4  → ContraDatabase (knowledge base, no network needed)
Step 5  → ContraEngine (query + severity sorting)
Step 6  → InsightEngine (existing, update for new phase logic)
Step 7  → PredictionEngine (next period, fertile window, PMS)
Step 8  → HealthScoreEngine (composite 0–100)
Step 9  → SymptomCorrelator (optional: correlate symptoms to drug/phase)
Step 10 → MoonRingView (Canvas ring with phase segments)
Step 11 → HormoneChartView (Canvas multi-line chart)
Step 12 → ContraEngineCardView (expandable list)
Step 13 → SymptomLoggerView (grid + intensity slider)
Step 14 → CycleCalendarView (horizontal phase strip with symptom dots)
Step 15 → XML layouts (all cards, glass theme, skeuomorphic buttons)
Step 16 → CycleViewModel (parallel async loading of all engines)
Step 17 → CycleFragment (bind all sections + entrance animations)
Step 18 → Bottom nav badge for contradiction count
Step 19 → Safety disclaimer layer (always visible)
Step 20 → Test all contradiction paths: 0 meds, 1 med, drug-drug, drug-phase
```

---

## Safety Requirements (Non-negotiable)

```kotlin
object SafetyLayer {
    const val DISCLAIMER = "⚠ This is not a medical diagnosis. " +
        "Contradiction alerts are informational and based on published interaction data. " +
        "For persistent symptoms or concerns, consult a qualified healthcare professional."

    const val CONTRA_PREFIX = "Informational only — not a prescription review."

    // Always shown when severity = CRITICAL
    const val CRITICAL_FOOTER = "Please do not stop or change your medication without " +
        "consulting your prescriber first."
}
```
