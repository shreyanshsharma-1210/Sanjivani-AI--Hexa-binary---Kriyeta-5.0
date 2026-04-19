package com.emergency.patient.ocr;

import android.content.Context;
import android.util.Log;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DdiGraphManager {

    private static final String TAG = "DdiGraphManager";
    private static JSONObject graphData = null;

    private static void loadGraph(Context context) {
        synchronized (DdiGraphManager.class) {
            if (graphData != null) return;
            try {
                InputStream is = context.getAssets().open("ddi_graph.json");
                BufferedReader reader = new BufferedReader(new InputStreamReader(is));
                StringBuilder json = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    json.append(line);
                }
                reader.close();
                graphData = new JSONObject(json.toString());
                Log.d(TAG, "DDI Graph loaded successfully");
            } catch (Exception e) {
                Log.e(TAG, "Failed to load ddi_graph.json", e);
                graphData = new JSONObject();
            }
        }
    }

    public static List<Interaction> queryLocal(Context context, String rxcuiA, List<String> existingRxcuis, Map<String, String> rxcuiToName) {
        loadGraph(context);
        List<Interaction> results = new ArrayList<>();
        
        if (graphData == null || rxcuiA == null) return results;

        try {
            JSONArray edges = graphData.optJSONArray("edges");
            if (edges == null) return results;

            for (String rxcuiB : existingRxcuis) {
                if (rxcuiA.equals(rxcuiB)) continue;

                for (int i = 0; i < edges.length(); i++) {
                    JSONObject edge = edges.getJSONObject(i);
                    String from = edge.getString("from");
                    String to = edge.getString("to");

                    if ((from.equals(rxcuiA) && to.equals(rxcuiB)) || (from.equals(rxcuiB) && to.equals(rxcuiA))) {
                        String nameA = rxcuiToName.getOrDefault(rxcuiA, "Drug " + rxcuiA);
                        String nameB = rxcuiToName.getOrDefault(rxcuiB, "Drug " + rxcuiB);
                        
                        results.add(new Interaction(
                            nameA,
                            nameB,
                            edge.optString("severity", "MEDIUM"),
                            edge.optString("description", "Potential interaction detected"),
                            "Local Graph"
                        ));
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error querying DDI graph", e);
        }

        return results;
    }
}
