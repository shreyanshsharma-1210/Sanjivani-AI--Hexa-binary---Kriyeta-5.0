package com.emergency.patient.ocr;

import android.util.Log;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class DrugInteractionApiService {
    
    private static final Map<String, String> rxcuiCache = new ConcurrentHashMap<>();
    private static final Map<String, List<Interaction>> ddiCache = new ConcurrentHashMap<>();

    public interface InteractionCallback {
        void onResult(List<Interaction> result);
    }

    public interface RxcuiCallback {
        void onResult(Map<String, String> nameToRxcui);
    }

    public static void resolveRxCuis(List<String> drugNames, RxcuiCallback callback) {
        new Thread(() -> {
            Map<String, String> results = new HashMap<>();
            for (String name : drugNames) {
                String rxcui = getRxCui(name);
                if (rxcui != null) {
                    results.put(name, rxcui);
                }
            }
            callback.onResult(results);
        }).start();
    }

    public static void checkInteractionsOnline(android.content.Context context, String newDrug, List<String> existingDrugs, Map<String, String> nameToRxcui, InteractionCallback callback) {
        String cacheKey = newDrug + ":" + new java.util.TreeSet<>(existingDrugs).toString();
        if (ddiCache.containsKey(cacheKey)) {
            callback.onResult(ddiCache.get(cacheKey));
            return;
        }

        new Thread(() -> {
            List<Interaction> interactions = new ArrayList<>();
            try {
                Map<String, String> rxcuiToName = new HashMap<>();
                List<String> rxcuis = new ArrayList<>();

                for (Map.Entry<String, String> entry : nameToRxcui.entrySet()) {
                    rxcuiToName.put(entry.getValue(), entry.getKey());
                    rxcuis.add(entry.getValue());
                }

                if (rxcuis.size() >= 2) {
                    String rxcuiList = android.text.TextUtils.join("+", rxcuis);
                    String urlStr = "https://rxnav.nlm.nih.gov/REST/interaction/list.json?rxcuis=" + rxcuiList;
                    
                    HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
                    conn.setRequestMethod("GET");
                    conn.setConnectTimeout(3000);
                    conn.setReadTimeout(3000);

                    if (conn.getResponseCode() == 200) {
                        BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                        StringBuilder response = new StringBuilder();
                        String line;
                        while ((line = reader.readLine()) != null) response.append(line);
                        reader.close();

                        JSONObject root = new JSONObject(response.toString());
                        if (root.has("fullInteractionTypeGroup")) {
                            JSONArray groups = root.getJSONArray("fullInteractionTypeGroup");
                            for (int i = 0; i < groups.length(); i++) {
                                JSONArray fullTypes = groups.getJSONObject(i).getJSONArray("fullInteractionType");
                                for (int j = 0; j < fullTypes.length(); j++) {
                                    JSONObject type = fullTypes.getJSONObject(j);
                                    JSONArray concept = type.getJSONArray("minConcept");
                                    String rxcui1 = concept.getJSONObject(0).getString("rxcui");
                                    String rxcui2 = concept.getJSONObject(1).getString("rxcui");
                                    
                                    String name1 = rxcuiToName.get(rxcui1);
                                    String name2 = rxcuiToName.get(rxcui2);
                                    
                                    if (name1 != null && name2 != null) {
                                        if (name1.equalsIgnoreCase(newDrug) || name2.equalsIgnoreCase(newDrug)) {
                                            String desc = type.getJSONArray("interactionPair").getJSONObject(0).getString("description");
                                            String severity = type.getJSONArray("interactionPair").getJSONObject(0).optString("severity", "MEDIUM");
                                            
                                            severity = mapSeverity(severity);
                                            
                                            String normA = DrugNormalizer.normalize(context, name1).name;
                                            String normB = DrugNormalizer.normalize(context, name2).name;
                                            interactions.add(new Interaction(normA, normB, severity, desc, "RxNav"));
                                        }
                                    }
                                }
                            }
                        }
                    }
                    conn.disconnect();
                }
            } catch (Exception e) {
                Log.e("DrugApi", "API check failed", e);
            }
            ddiCache.put(cacheKey, interactions);
            callback.onResult(interactions);
        }).start();
    }

    public static void checkAllInteractionsOnline(android.content.Context context, List<String> allDrugs, Map<String, String> nameToRxcui, InteractionCallback callback) {
        String cacheKey = "ALL:" + new java.util.TreeSet<>(allDrugs).toString();
        if (ddiCache.containsKey(cacheKey)) {
            callback.onResult(ddiCache.get(cacheKey));
            return;
        }

        new Thread(() -> {
            List<Interaction> interactions = new ArrayList<>();
            try {
                Map<String, String> rxcuiToName = new HashMap<>();
                List<String> rxcuis = new ArrayList<>();

                for (Map.Entry<String, String> entry : nameToRxcui.entrySet()) {
                    rxcuiToName.put(entry.getValue(), entry.getKey());
                    rxcuis.add(entry.getValue());
                }

                if (rxcuis.size() >= 2) {
                    String rxcuiList = android.text.TextUtils.join("+", rxcuis);
                    String urlStr = "https://rxnav.nlm.nih.gov/REST/interaction/list.json?rxcuis=" + rxcuiList;
                    
                    HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
                    conn.setRequestMethod("GET");
                    conn.setConnectTimeout(3000);
                    conn.setReadTimeout(3000);

                    if (conn.getResponseCode() == 200) {
                        BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                        StringBuilder response = new StringBuilder();
                        String line;
                        while ((line = reader.readLine()) != null) response.append(line);
                        reader.close();

                        JSONObject root = new JSONObject(response.toString());
                        if (root.has("fullInteractionTypeGroup")) {
                            JSONArray groups = root.getJSONArray("fullInteractionTypeGroup");
                            for (int i = 0; i < groups.length(); i++) {
                                JSONArray fullTypes = groups.getJSONObject(i).getJSONArray("fullInteractionType");
                                for (int j = 0; j < fullTypes.length(); j++) {
                                    JSONObject type = fullTypes.getJSONObject(j);
                                    JSONArray concept = type.getJSONArray("minConcept");
                                    String rxcui1 = concept.getJSONObject(0).getString("rxcui");
                                    String rxcui2 = concept.getJSONObject(1).getString("rxcui");
                                    
                                    String name1 = rxcuiToName.get(rxcui1);
                                    String name2 = rxcuiToName.get(rxcui2);
                                    
                                    if (name1 != null && name2 != null) {
                                        String desc = type.getJSONArray("interactionPair").getJSONObject(0).getString("description");
                                        String severity = type.getJSONArray("interactionPair").getJSONObject(0).optString("severity", "MEDIUM");
                                        
                                        severity = mapSeverity(severity);
                                        
                                        String normA = DrugNormalizer.normalize(context, name1).name;
                                        String normB = DrugNormalizer.normalize(context, name2).name;
                                        boolean exists = false;
                                        for(Interaction in : interactions) {
                                            if((in.drugA.equals(normA) && in.drugB.equals(normB)) || (in.drugA.equals(normB) && in.drugB.equals(normA))) {
                                                exists = true; break;
                                            }
                                        }
                                        if(!exists) {
                                            interactions.add(new Interaction(normA, normB, severity, desc, "RxNav"));
                                        }
                                    }
                                }
                            }
                        }
                    }
                    conn.disconnect();
                }
            } catch (Exception e) {
                Log.e("DrugApi", "API checkAll failed", e);
            }
            ddiCache.put(cacheKey, interactions);
            callback.onResult(interactions);
        }).start();
    }

    private static String mapSeverity(String input) {
        if (input == null || input.isEmpty() || input.equalsIgnoreCase("N/A")) return "UNKNOWN";
        input = input.toUpperCase();
        if (input.equals("HIGH") || input.equals("SEVERE")) return "HIGH";
        if (input.equals("MODERATE") || input.equals("MEDIUM")) return "MODERATE";
        if (input.equals("LOW") || input.equals("MINOR")) return "LOW";
        return "MODERATE";
    }

    public static String getRxCui(String drugName) {
        if (drugName == null || drugName.trim().isEmpty()) return null;
        
        String cleanName = drugName.trim().toUpperCase();
        if (rxcuiCache.containsKey(cleanName)) return rxcuiCache.get(cleanName);

        try {
            String urlStr = "https://rxnav.nlm.nih.gov/REST/rxcui.json?name=" + java.net.URLEncoder.encode(drugName, "UTF-8");
            HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(3000);
            conn.setReadTimeout(3000);

            if (conn.getResponseCode() == 200) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) response.append(line);
                reader.close();
                
                JSONObject root = new JSONObject(response.toString());
                if (root.has("idGroup")) {
                    JSONObject idGroup = root.getJSONObject("idGroup");
                    if (idGroup.has("rxnormId")) {
                        String rxcui = idGroup.getJSONArray("rxnormId").getString(0);
                        rxcuiCache.put(cleanName, rxcui);
                        return rxcui;
                    }
                }
            }
            conn.disconnect();
        } catch (Exception e) {
            Log.e("DrugApi", "Error getting RXCUI for " + drugName, e);
        }
        return null;
    }
}
