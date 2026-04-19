package com.emergency.patient.ocr;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;

/**
 * RxNormApiService — standardizes drug names using the NIH RxNav API.
 * This is used as a fallback for the local drug dictionary.
 */
public class RxNormApiService {

    public interface Callback {
        void onResult(String correctedName);
    }

    /**
     * Attempts to verify or correct a drug name using the RxNorm API.
     * 
     * @param input    The raw drug name candidate.
     * @param callback Result callback (returns null if verification fails or offline).
     */
    public static void fetchDrugName(String input, Callback callback) {
        if (input == null || input.trim().isEmpty()) {
            callback.onResult(null);
            return;
        }

        new Thread(() -> {
            try {
                // REST API to get RxCUI for a drug name
                String urlStr = "https://rxnav.nlm.nih.gov/REST/rxcui.json?name="
                        + URLEncoder.encode(input.trim(), "UTF-8");

                android.util.Log.d("API_CALL", "Request URL: " + urlStr);

                URL url = new URL(urlStr);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();

                conn.setConnectTimeout(3000); // 3s timeout
                conn.setReadTimeout(3000);

                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(conn.getInputStream())
                );

                StringBuilder response = new StringBuilder();
                String line;

                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }

                reader.close();

                android.util.Log.d("API_RESPONSE", response.toString());

                JSONObject json = new JSONObject(response.toString());

                // RxNorm response structure: idGroup -> rxnormId (array)
                JSONObject idGroup = json.optJSONObject("idGroup");
                if (idGroup != null) {
                    JSONArray ids = idGroup.optJSONArray("rxnormId");
                    if (ids != null && ids.length() > 0) {
                        // Success: The name is a valid drug recognizable by RxNorm
                        callback.onResult(input);
                        return;
                    }
                }
                
                callback.onResult(null);

            } catch (Exception e) {
                // Graceful failure for offline/timeout
                callback.onResult(null);
            }
        }).start();
    }
}
