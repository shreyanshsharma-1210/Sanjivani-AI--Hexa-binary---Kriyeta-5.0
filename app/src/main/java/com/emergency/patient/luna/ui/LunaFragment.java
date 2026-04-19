package com.emergency.patient.luna.ui;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.format.DateFormat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.emergency.patient.R;
import com.emergency.patient.db.AppDatabaseProvider;
import com.emergency.patient.db.MedicationEntity;
import com.emergency.patient.db.PatientEntity;
import com.emergency.patient.luna.db.CycleDao;
import com.emergency.patient.luna.db.SymptomDao;
import com.emergency.patient.luna.domain.ContraDatabase;
import com.emergency.patient.luna.domain.HealthScoreEngine;
import com.emergency.patient.luna.domain.PredictionEngine;
import com.emergency.patient.luna.model.Contradiction;
import com.emergency.patient.luna.model.ContraSeverity;
import com.emergency.patient.luna.model.CycleEntry;
import com.emergency.patient.luna.model.SymptomEntry;
import com.emergency.patient.security.TokenManager;
import com.emergency.patient.network.GroqApiService;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

import org.json.JSONArray;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Luna v2 — Women's Cycle Health Intelligence Screen.
 *
 * Access gate: visible only when patient.gender == "Female".
 * Sections: Header · Tabs · Moon Ring · Vitals · Hormone Chart ·
 *           Contradiction Engine · Medication Timeline ·
 *           Predictions · Symptom Logger · AI Insight · Disclaimer
 */
public class LunaFragment extends Fragment {

    // ─── Views ────────────────────────────────────────────────────────────────
    private View lunaHeader, lunaTabBar, lunaMoonSection, lunaVitalsSection;
    private View lunaChartSection, lunaContraSection, lunaMedSection;
    private View lunaPredSection, lunaSymptomSection, lunaInsightSection;

    private TextView tvLunaDate, tvCycleDay, tvCyclePhase, tvCycleLength;
    private TextView tvNextPeriod, tvNextPeriodDate, tvFertileWindow, tvLunaHealthScore;
    private ProgressBar progressNextPeriod;

    // Vitals (display only — no sensor input yet)
    private TextView tvLunaTemp, tvLunaHR, tvLunaSpO2, tvLunaHRV, tvLunaStress;

    // Chart
    private HormoneChartView hormoneChartView;

    // Moon ring
    private MoonRingView moonRingView;

    // Contradiction engine
    private TextView tvContraCount, tvContraSubtitle, tvCritBadge, tvModBadge, tvInfoBadge;
    private View contraBadgeRow;
    private ContraEngineCardView contraEngineCardView;

    // Medication container
    private LinearLayout lunaMedContainer;
    private TextView tvLunaMedEmpty;

    // Predictions
    private TextView tvPredNextDays, tvPredNextDate;
    private TextView tvPredFertileDays, tvPredFertileDate;
    private TextView tvPredPmsDays, tvPredPmsDate;
    private TextView tvPredAvgLength, tvPredConfidence;

    // Symptoms
    private SymptomLoggerView symptomLoggerView;
    private Button btnLogSymptoms;

    // AI Insight
    private TextView tvLunaInsight, tvInsightConf;

    // Period button
    private Button btnPeriodStarted;

    // ─── State ────────────────────────────────────────────────────────────────
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private List<MedicationEntity> medications = new ArrayList<>();
    private List<CycleEntry> cycleEntries = new ArrayList<>();
    private List<Contradiction> activeContradictions = new ArrayList<>();
    private PredictionEngine.Predictions predictions;
    
    private GroqApiService groqService;
    private static final String GROQ_API_KEY = "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";

    // ─── Lifecycle ────────────────────────────────────────────────────────────

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_luna, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        // ── Gender Gate (second lock) ─────────────────────────────────────────
        enforceGenderGate();
        
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl("https://api.groq.com/openai/v1/")
                .addConverterFactory(GsonConverterFactory.create())
                .build();
        groqService = retrofit.create(GroqApiService.class);
        
        bindViews(view);
        setCurrentDate();
        setupClickListeners();
        startEntranceAnimations();
        loadData();
    }

    @Override
    public void onResume() {
        super.onResume();
        loadData();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        mainHandler.removeCallbacksAndMessages(null);
    }

    // ─── Gender Gate ──────────────────────────────────────────────────────────

    private void enforceGenderGate() {
        // Run check on background thread so we don't block UI
        new Thread(() -> {
            try {
                String uuid = TokenManager.getUUID(requireContext());
                PatientEntity patient = AppDatabaseProvider.getInstance(requireContext())
                        .patientDao().getPatient(uuid);
                boolean isFemal = patient != null && "Female".equalsIgnoreCase(patient.gender);
                if (!isFemal) {
                    mainHandler.post(() -> {
                        Toast.makeText(requireContext(),
                                "Luna is available for female profiles only.",
                                Toast.LENGTH_SHORT).show();
                        if (getParentFragmentManager().getBackStackEntryCount() > 0) {
                            getParentFragmentManager().popBackStack();
                        }
                    });
                }
            } catch (Exception ignored) {}
        }).start();
    }

    // ─── View binding ─────────────────────────────────────────────────────────

    private void bindViews(View v) {
        lunaHeader        = v.findViewById(R.id.lunaHeader);
        lunaTabBar        = v.findViewById(R.id.lunaTabBar);
        lunaMoonSection   = v.findViewById(R.id.lunaMoonSection);
        lunaVitalsSection = v.findViewById(R.id.lunaVitalsSection);
        lunaChartSection  = v.findViewById(R.id.lunaChartSection);
        lunaContraSection = v.findViewById(R.id.lunaContraSection);
        lunaMedSection    = v.findViewById(R.id.lunaMedSection);
        lunaPredSection   = v.findViewById(R.id.lunaPredSection);
        lunaSymptomSection = v.findViewById(R.id.lunaSymptomSection);
        lunaInsightSection = v.findViewById(R.id.lunaInsightSection);

        tvLunaDate        = v.findViewById(R.id.tvLunaDate);
        tvCycleDay        = v.findViewById(R.id.tvCycleDay);
        tvCyclePhase      = v.findViewById(R.id.tvCyclePhase);
        tvCycleLength     = v.findViewById(R.id.tvCycleLength);
        tvNextPeriod      = v.findViewById(R.id.tvNextPeriod);
        tvNextPeriodDate  = v.findViewById(R.id.tvNextPeriodDate);
        tvFertileWindow   = v.findViewById(R.id.tvFertileWindow);
        tvLunaHealthScore = v.findViewById(R.id.tvLunaHealthScore);
        progressNextPeriod = v.findViewById(R.id.progressNextPeriod);

        tvLunaTemp  = v.findViewById(R.id.tvLunaTemp);
        tvLunaHR    = v.findViewById(R.id.tvLunaHR);
        tvLunaSpO2  = v.findViewById(R.id.tvLunaSpO2);
        tvLunaHRV   = v.findViewById(R.id.tvLunaHRV);
        tvLunaStress = v.findViewById(R.id.tvLunaStress);

        hormoneChartView = v.findViewById(R.id.hormoneChartView);
        moonRingView     = v.findViewById(R.id.moonRingView);

        tvContraCount    = v.findViewById(R.id.tvContraCount);
        tvContraSubtitle = v.findViewById(R.id.tvContraSubtitle);
        tvCritBadge      = v.findViewById(R.id.tvCritBadge);
        tvModBadge       = v.findViewById(R.id.tvModBadge);
        tvInfoBadge      = v.findViewById(R.id.tvInfoBadge);
        contraBadgeRow   = v.findViewById(R.id.contraBadgeRow);
        contraEngineCardView = v.findViewById(R.id.contraEngineCardView);

        lunaMedContainer = v.findViewById(R.id.lunaMedContainer);
        tvLunaMedEmpty   = v.findViewById(R.id.tvLunaMedEmpty);

        tvPredNextDays   = v.findViewById(R.id.tvPredNextDays);
        tvPredNextDate   = v.findViewById(R.id.tvPredNextDate);
        tvPredFertileDays = v.findViewById(R.id.tvPredFertileDays);
        tvPredFertileDate = v.findViewById(R.id.tvPredFertileDate);
        tvPredPmsDays    = v.findViewById(R.id.tvPredPmsDays);
        tvPredPmsDate    = v.findViewById(R.id.tvPredPmsDate);
        tvPredAvgLength  = v.findViewById(R.id.tvPredAvgLength);
        tvPredConfidence = v.findViewById(R.id.tvPredConfidence);

        symptomLoggerView = v.findViewById(R.id.symptomLoggerView);
        btnLogSymptoms   = v.findViewById(R.id.btnLogSymptoms);
        tvLunaInsight    = v.findViewById(R.id.tvLunaInsight);
        tvInsightConf    = v.findViewById(R.id.tvInsightConf);
        btnPeriodStarted = v.findViewById(R.id.btnPeriodStarted);
    }

    // ─── Setup ────────────────────────────────────────────────────────────────

    private void setCurrentDate() {
        if (tvLunaDate == null) return;
        String day = new java.text.SimpleDateFormat("EEEE, dd MMMM", Locale.getDefault())
                .format(new Date());
        tvLunaDate.setText(day);
    }

    private void setupClickListeners() {
        if (btnPeriodStarted != null) {
            btnPeriodStarted.setOnClickListener(v -> logPeriodStart());
        }
        if (btnLogSymptoms != null) {
            btnLogSymptoms.setOnClickListener(v -> logTodaySymptoms());
        }
    }

    // ─── Data loading ─────────────────────────────────────────────────────────

    private void loadData() {
        new Thread(() -> {
            if (getActivity() == null) return;
            try {
                // 1. Medications from existing DAO
                List<MedicationEntity> meds = AppDatabaseProvider.getInstance(requireContext())
                        .medicationDao().getAllMedications();
                if (meds == null || meds.isEmpty()) {
                    MedicationEntity mock1 = new MedicationEntity(); mock1.name = "Ibuprofen"; mock1.dosage = "400mg";
                    MedicationEntity mock2 = new MedicationEntity(); mock2.name = "Sertraline"; mock2.dosage = "50mg";
                    AppDatabaseProvider.getInstance(requireContext()).medicationDao().insertMedication(mock1);
                    AppDatabaseProvider.getInstance(requireContext()).medicationDao().insertMedication(mock2);
                    meds = AppDatabaseProvider.getInstance(requireContext()).medicationDao().getAllMedications();
                }
                medications = meds != null ? meds : new ArrayList<>();

                // 2. Cycle entries
                List<CycleEntry> cycles = AppDatabaseProvider.getInstance(requireContext())
                        .cycleDao().getRecent(6);
                if (cycles == null || cycles.isEmpty()) {
                    CycleEntry mockCycle = new CycleEntry(System.currentTimeMillis() - 17L * 24 * 3600 * 1000, 0);
                    AppDatabaseProvider.getInstance(requireContext()).cycleDao().insert(mockCycle);
                    cycles = AppDatabaseProvider.getInstance(requireContext()).cycleDao().getRecent(6);
                }
                cycleEntries = cycles != null ? cycles : new ArrayList<>();

                // 3. Compute predictions
                predictions = PredictionEngine.predict(cycleEntries);

                // 4. Drug names for ContradictionEngine
                List<String> drugNames = new ArrayList<>();
                for (MedicationEntity m : medications) {
                    if (m.name != null && !m.name.trim().isEmpty()) drugNames.add(m.name);
                }

                // 5. Run contradiction analysis
                activeContradictions = ContraDatabase.analyzeAll(drugNames, predictions.currentPhase);

                // 6. Health score
                int critCount = 0, modCount = 0, infoCount = 0;
                for (Contradiction c : activeContradictions) {
                    if (c.severity == ContraSeverity.CRITICAL) critCount++;
                    else if (c.severity == ContraSeverity.MODERATE) modCount++;
                    else infoCount++;
                }
                boolean cycleRegular = cycleEntries.size() >= 2;
                HealthScoreEngine.HealthScore score = HealthScoreEngine.calculate(
                        cycleRegular, cycleEntries.size() < 3, 0, activeContradictions);

                final int finalCrit = critCount, finalMod = modCount, finalInfo = infoCount;

                mainHandler.post(() -> {
                    if (!isAdded()) return;
                    bindMoonRing();
                    bindVitals();
                    bindChart();
                    bindContraEngine(finalCrit, finalMod, finalInfo);
                    bindMedTimeline();
                    bindPredictions();
                    bindHealthScore(score.total, score.label);
                    bindInsight();
                });

            } catch (Exception e) {
                mainHandler.post(() -> {
                    if (!isAdded()) return;
                    Toast.makeText(requireContext(), "Failed to load Luna data", Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }

    // ─── UI binding ───────────────────────────────────────────────────────────

    private void bindMoonRing() {
        if (predictions == null) return;
        if (moonRingView != null)
            moonRingView.setCycleData(predictions.currentDay, predictions.cycleLength);
        if (tvCycleDay != null)
            tvCycleDay.setText(String.valueOf(predictions.currentDay));
        if (tvCyclePhase != null)
            tvCyclePhase.setText(predictions.currentPhase.label);
        if (tvCycleLength != null)
            tvCycleLength.setText(predictions.cycleLength + " days");
    }

    private void bindVitals() {
        // Vitals shown as "—" since no input mechanism exists yet
        safeSet(tvLunaTemp,   "—°C");
        safeSet(tvLunaHR,     "—");
        safeSet(tvLunaSpO2,   "—%");
        safeSet(tvLunaHRV,    "— ms");
        safeSet(tvLunaStress, "—");
    }

    private void bindChart() {
        if (hormoneChartView == null || predictions == null) return;
        // Generate illustrative placeholder points based on cycle phase
        int len = predictions.cycleLength;
        List<Float> temps = generateTempCurve(len);
        List<Float> hrs   = generateHrCurve(len);
        List<Float> hrvs  = generateHrvCurve(len);
        hormoneChartView.setData(temps, hrs, hrvs, len, predictions.currentDay);
    }

    private void bindContraEngine(int crit, int mod, int info) {
        if (tvContraCount == null) return;
        int total = activeContradictions.size();
        tvContraCount.setText(total + (total == 1 ? " Interaction Detected" : " Interactions Detected"));
        if (tvContraSubtitle != null) {
            tvContraSubtitle.setText(total == 0
                    ? "No conflicts with current medications"
                    : "Drug–drug · Drug–cycle · Drug–nutrient conflicts");
        }
        if (contraBadgeRow != null) contraBadgeRow.setVisibility(total > 0 ? View.VISIBLE : View.GONE);
        safeSet(tvCritBadge, crit + " Critical");
        safeSet(tvModBadge, mod + " Moderate");
        safeSet(tvInfoBadge, info + " Info");
        if (contraEngineCardView != null) contraEngineCardView.bind(activeContradictions);
    }

    private void bindMedTimeline() {
        if (lunaMedContainer == null) return;
        // Remove old dynamic items (keep first child which is tvLunaMedEmpty)
        if (lunaMedContainer.getChildCount() > 1) {
            lunaMedContainer.removeViews(1, lunaMedContainer.getChildCount() - 1);
        }
        if (medications.isEmpty()) {
            if (tvLunaMedEmpty != null) tvLunaMedEmpty.setVisibility(View.VISIBLE);
            return;
        }
        if (tvLunaMedEmpty != null) tvLunaMedEmpty.setVisibility(View.GONE);

        for (MedicationEntity med : medications) {
            View item = buildMedRow(med);
            lunaMedContainer.addView(item);
        }
    }

    private View buildMedRow(MedicationEntity med) {
        LinearLayout row = new LinearLayout(requireContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(dp(14), dp(14), dp(14), dp(14));

        // Icon
        TextView icon = new TextView(requireContext());
        icon.setText("💊");
        icon.setTextSize(20f);
        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(dp(40), dp(40));
        ip.setMarginEnd(dp(12));
        icon.setLayoutParams(ip);
        icon.setGravity(android.view.Gravity.CENTER);
        row.addView(icon);

        // Info
        LinearLayout info = new LinearLayout(requireContext());
        info.setOrientation(LinearLayout.VERTICAL);
        info.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView name = new TextView(requireContext());
        name.setText(med.name != null ? med.name : "Unknown");
        name.setTextColor(0xFFFFFFFF);
        name.setTextSize(13f);
        info.addView(name);

        if (med.dosage != null && !med.dosage.isEmpty()) {
            TextView dose = new TextView(requireContext());
            dose.setText(med.dosage);
            dose.setTextColor(0x66FFFFFF);
            dose.setTextSize(11f);
            info.addView(dose);
        }
        row.addView(info);

        // Divider
        View divider = new View(requireContext());
        divider.setBackgroundColor(0x0DFFFFFF);
        LinearLayout wrapper = new LinearLayout(requireContext());
        wrapper.setOrientation(LinearLayout.VERTICAL);
        wrapper.addView(row);
        LinearLayout.LayoutParams dp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 1);
        divider.setLayoutParams(dp);
        wrapper.addView(divider);
        return wrapper;
    }

    private void bindPredictions() {
        if (predictions == null) return;
        safeSet(tvPredNextDays, String.valueOf(predictions.daysToNextPeriod));
        safeSet(tvPredNextDate, predictions.nextPeriodLabel);
        safeSet(tvPredFertileDays, String.valueOf(predictions.daysToFertileStart));
        safeSet(tvPredFertileDate, predictions.fertileWindowLabel);
        // PMS: next period - 7 days
        int pmsDays = Math.max(0, predictions.daysToNextPeriod - 7);
        safeSet(tvPredPmsDays, String.valueOf(pmsDays));
        safeSet(tvPredPmsDate, predictions.pmsWindowLabel);
        safeSet(tvPredAvgLength, String.valueOf(predictions.cycleLength));
        safeSet(tvPredConfidence, predictions.confidenceLabel);

        // Next period progress bar
        if (progressNextPeriod != null) {
            int progress = predictions.cycleLength > 0
                    ? (int) (((float) predictions.currentDay / predictions.cycleLength) * 100)
                    : 50;
            progressNextPeriod.setProgress(Math.min(100, progress));
        }
        if (tvNextPeriod != null)
            tvNextPeriod.setText(predictions.daysToNextPeriod + " days");
        if (tvNextPeriodDate != null)
            tvNextPeriodDate.setText(predictions.nextPeriodLabel + " (est.)");
        if (tvFertileWindow != null)
            tvFertileWindow.setText(predictions.fertileWindowLabel);
    }

    private void bindHealthScore(int score, String label) {
        safeSet(tvLunaHealthScore, String.valueOf(score));
    }

    private void bindInsight() {
        if (tvLunaInsight == null) return;
        if (cycleEntries.isEmpty()) {
            tvLunaInsight.setText("Tap '🩸 Period Started Today' to log your first cycle. " +
                    "Luna will then personalise insights based on your medications and health data.");
            if (tvInsightConf != null) tvInsightConf.setText("● Awaiting first entry");
            return;
        }
        
        if (medications.isEmpty()) {
            String phaseLabel = predictions != null ? predictions.currentPhase.label : "current";
            StringBuilder sb = new StringBuilder();
            sb.append("You are on day ").append(predictions != null ? predictions.currentDay : 1)
              .append(" of your cycle — ").append(phaseLabel).append(" phase. ");
            sb.append("No medication conflicts detected with your current phase. Keep tracking to refine predictions.");
            tvLunaInsight.setText(sb.toString());
            if (tvInsightConf != null)
                tvInsightConf.setText("● " + (predictions != null ? predictions.confidenceLabel : "Estimated"));
        } else {
            fetchGroqAIInsight();
        }
    }

    private void fetchGroqAIInsight() {
        if (tvLunaInsight != null) tvLunaInsight.setText("Generating AI Insight...");
        
        List<String> drugNames = new ArrayList<>();
        for (MedicationEntity m : medications) {
            if (m.name != null && !m.name.trim().isEmpty()) drugNames.add(m.name);
        }
        
        String phaseLabel = predictions != null ? predictions.currentPhase.label : "Luteal";
        StringBuilder drugs = new StringBuilder();
        for (String d : drugNames) drugs.append(d).append(", ");

        String systemPrompt = "You are Luna, an empathetic women's health AI assistant. " +
                "Given the user's current menstrual phase and medications, provide a brief, " +
                "2-3 sentence clinical insight on how these drugs might affect their cycle, symptoms, or what they should look out for. " +
                "Focus ONLY on their menstrual cycle and the drugs specifically. Be concise, direct, and empathetic. Limit your output to pure text, no markdown.";
        String userPrompt = "Current cycle phase: " + phaseLabel + ". Active Medications: " + drugs.toString();

        List<GroqApiService.GroqRequest.Message> msgList = new ArrayList<>();
        msgList.add(new GroqApiService.GroqRequest.Message("system", systemPrompt));
        msgList.add(new GroqApiService.GroqRequest.Message("user", userPrompt));

        GroqApiService.GroqRequest request = new GroqApiService.GroqRequest("llama-3.3-70b-versatile", msgList);

        groqService.getChatCompletion("Bearer " + GROQ_API_KEY, request).enqueue(new Callback<GroqApiService.GroqResponse>() {
            @Override
            public void onResponse(Call<GroqApiService.GroqResponse> call, Response<GroqApiService.GroqResponse> response) {
                if (!isAdded() || tvLunaInsight == null) return;
                mainHandler.post(() -> {
                    if (response.isSuccessful() && response.body() != null) {
                        String answer = response.body().choices.get(0).message.content;
                        tvLunaInsight.setText(answer);
                        if (tvInsightConf != null) tvInsightConf.setText("● AI Generated Insight");
                    } else {
                        tvLunaInsight.setText("AI Insight unavailable. Ensure network is connected.");
                    }
                });
            }

            @Override
            public void onFailure(Call<GroqApiService.GroqResponse> call, Throwable t) {
                if (!isAdded() || tvLunaInsight == null) return;
                mainHandler.post(() -> tvLunaInsight.setText("AI Insight unavailable. Check your network."));
            }
        });
    }

    // ─── Actions ──────────────────────────────────────────────────────────────

    private void logPeriodStart() {
        new Thread(() -> {
            CycleEntry entry = new CycleEntry(System.currentTimeMillis(), 0);
            AppDatabaseProvider.getInstance(requireContext()).cycleDao().insert(entry);
            mainHandler.post(() -> {
                if (!isAdded()) return;
                Toast.makeText(requireContext(), "🩸 Period logged! Predictions updated.", Toast.LENGTH_SHORT).show();
                loadData();
            });
        }).start();
    }

    private void logTodaySymptoms() {
        if (symptomLoggerView == null) return;
        List<String> selected = symptomLoggerView.getSelectedSymptoms();
        int intensity = symptomLoggerView.getIntensity();
        if (selected.isEmpty()) {
            Toast.makeText(requireContext(), "Select at least one symptom.", Toast.LENGTH_SHORT).show();
            return;
        }
        new Thread(() -> {
            SymptomEntry entry = new SymptomEntry();
            entry.date = System.currentTimeMillis();
            JSONArray arr = new JSONArray();
            for (String s : selected) arr.put(s);
            entry.symptomsJson = arr.toString();
            entry.intensity = intensity;
            entry.cycleDay = predictions != null ? predictions.currentDay : 1;
            entry.phase = predictions != null ? predictions.currentPhase.label : "Unknown";
            AppDatabaseProvider.getInstance(requireContext()).symptomDao().insert(entry);
            mainHandler.post(() -> {
                if (!isAdded()) return;
                Toast.makeText(requireContext(), "✅ Symptoms logged for today.", Toast.LENGTH_SHORT).show();
                symptomLoggerView.clearSelections();
            });
        }).start();
    }

    // ─── Entrance animations ──────────────────────────────────────────────────

    private void startEntranceAnimations() {
        View[] sections = {
            lunaHeader, lunaTabBar, lunaMoonSection, lunaVitalsSection,
            lunaChartSection, lunaContraSection, lunaMedSection,
            lunaPredSection, lunaSymptomSection, lunaInsightSection
        };
        for (int i = 0; i < sections.length; i++) {
            final View v = sections[i];
            if (v == null) continue;
            v.setAlpha(0f);
            v.setTranslationY(30f);
            v.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(420)
                .setStartDelay(i * 80L)
                .setInterpolator(new DecelerateInterpolator(1.4f))
                .start();
        }
    }

    // ─── Chart curve generators (illustrative) ────────────────────────────────

    /** Generates a typical biphasic temperature curve for one cycle. */
    private List<Float> generateTempCurve(int len) {
        List<Float> pts = new ArrayList<>();
        int ovDay = len - 14;
        for (int d = 1; d <= len; d++) {
            float base = d < ovDay ? 36.5f : 36.9f;
            float noise = (float) ((Math.random() - 0.5) * 0.15);
            pts.add(base + noise);
        }
        return pts;
    }

    private List<Float> generateHrCurve(int len) {
        List<Float> pts = new ArrayList<>();
        for (int d = 1; d <= len; d++) {
            float hr = 68 + (float) (Math.sin(d / (float) len * Math.PI) * 12)
                    + (float) ((Math.random() - 0.5) * 6);
            pts.add(hr);
        }
        return pts;
    }

    private List<Float> generateHrvCurve(int len) {
        List<Float> pts = new ArrayList<>();
        int ovDay = len - 14;
        for (int d = 1; d <= len; d++) {
            float hrv = d <= 5 ? 35f : d <= ovDay ? 50f : 38f;
            hrv += (float) ((Math.random() - 0.5) * 10);
            pts.add(hrv);
        }
        return pts;
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private void safeSet(TextView tv, String text) {
        if (tv != null) tv.setText(text);
    }

    private int dp(int dp) {
        if (getContext() == null) return dp;
        return (int) android.util.TypedValue.applyDimension(
                android.util.TypedValue.COMPLEX_UNIT_DIP, dp,
                getContext().getResources().getDisplayMetrics());
    }
}
