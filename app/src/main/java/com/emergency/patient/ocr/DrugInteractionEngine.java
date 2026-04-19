package com.emergency.patient.ocr;

import android.content.Context;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

import java.util.Collections;
import java.util.Map;

public class DrugInteractionEngine {

    public interface DdiCallback {
        void onResult(List<Interaction> results);
    }

    /**
     * Comprehensive DDI check: Local Graph -> RxNav API.
     */
    public static void checkHybrid(Context context, String newDrug, List<String> existingDrugs, DdiCallback callback) {
        List<String> allDrugs = new ArrayList<>();
        allDrugs.add(newDrug);
        allDrugs.addAll(existingDrugs);

        // Standardize: Resolve RxCUIs for all drugs first
        DrugInteractionApiService.resolveRxCuis(allDrugs, (nameToRxcui) -> {
            String newRxcui = nameToRxcui.get(newDrug);
            if (newRxcui == null) {
                // Cannot find RxCUI for the new drug, fallback or skip
                callback.onResult(new ArrayList<>());
                return;
            }

            Map<String, String> rxcuiToName = new java.util.HashMap<>();
            List<String> existingRxcuis = new ArrayList<>();
            for (Map.Entry<String, String> entry : nameToRxcui.entrySet()) {
                rxcuiToName.put(entry.getValue(), entry.getKey());
                if (!entry.getKey().equalsIgnoreCase(newDrug)) {
                    existingRxcuis.add(entry.getValue());
                }
            }

            // 1. Check Local Graph (High performance, offline)
            List<Interaction> localResults = DdiGraphManager.queryLocal(context, newRxcui, existingRxcuis, rxcuiToName);
            
            if (!localResults.isEmpty()) {
                // If local found something, we prioritize or return immediately depending on policy
                // User requirement: "display severity-based popup when contradictions are found"
                callback.onResult(localResults);
            } else {
                // 2. Fallback to Online Check (Comprehensive)
                DrugInteractionApiService.checkInteractionsOnline(context, newDrug, existingDrugs, nameToRxcui, results -> callback.onResult(results));
            }
        });
    }

    public static void checkAllInteractions(Context context, List<String> allDrugs, DdiCallback callback) {
        if (allDrugs == null || allDrugs.size() < 2) {
            callback.onResult(new ArrayList<>());
            return;
        }

        DrugInteractionApiService.resolveRxCuis(allDrugs, (nameToRxcui) -> {
            Map<String, String> rxcuiToName = new java.util.HashMap<>();
            List<String> rxcuis = new ArrayList<>();
            for (Map.Entry<String, String> entry : nameToRxcui.entrySet()) {
                rxcuiToName.put(entry.getValue(), entry.getKey());
                rxcuis.add(entry.getValue());
            }

            // 1. Check Local Graph for all pairs
            List<Interaction> localResults = new ArrayList<>();
            for (int i = 0; i < rxcuis.size(); i++) {
                String rxcuiA = rxcuis.get(i);
                List<String> others = new ArrayList<>();
                for (int j = i + 1; j < rxcuis.size(); j++) {
                    others.add(rxcuis.get(j));
                }
                if (!others.isEmpty()) {
                    List<Interaction> subLocal = DdiGraphManager.queryLocal(context, rxcuiA, others, rxcuiToName);
                    for (Interaction inter : subLocal) {
                        boolean exists = false;
                        for (Interaction lInter : localResults) {
                            if ((lInter.drugA.equals(inter.drugA) && lInter.drugB.equals(inter.drugB)) || 
                                (lInter.drugA.equals(inter.drugB) && lInter.drugB.equals(inter.drugA))) {
                                exists = true; break;
                            }
                        }
                        if (!exists) localResults.add(inter);
                    }
                }
            }
            
            if (!localResults.isEmpty()) {
                callback.onResult(localResults);
            } else {
                // 2. Fallback to Online Check for all drugs
                DrugInteractionApiService.checkAllInteractionsOnline(context, allDrugs, nameToRxcui, results -> callback.onResult(results));
            }
        });
    }
}
