package com.emergency.patient.ocr;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Set;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class GroqInterpreter {

    private static final String TAG = "GroqInterpreter";
    private static final String GROQ_API_URL = "https://api.groq.com/openai/v1/chat/completions";
    private static final String GROQ_API_KEY = "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";
    private static final String MODEL = "meta-llama/llama-4-scout-17b-16e-instruct";

    /**
     * Calls the Groq API synchronously and returns a structured EngineResult.
     *
     * @param rawOcrText      Raw concatenated OCR text from all image passes.
     * @param fuzzyCandidates Grounded candidate drug names to constrain the model.
     * @param timeoutSeconds  (unused — internal timeouts are now fixed per spec)
     * @return EngineResult on success, null on any failure (safe — pipeline continues).
     */
    public static EngineResult extractWithTimeout(String rawOcrText, Set<String> fuzzyCandidates, int timeoutSeconds) {

        // Step 4: Fixed timeouts per spec (>= 1500ms connect, >= 2000ms read)
        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(2000, TimeUnit.MILLISECONDS)
                .readTimeout(5000, TimeUnit.MILLISECONDS)
                .writeTimeout(3000, TimeUnit.MILLISECONDS)
                .build();

        // Build fuzzy candidate constraint block
        StringBuilder candidatesBuilder = new StringBuilder();
        if (fuzzyCandidates != null && !fuzzyCandidates.isEmpty()) {
            candidatesBuilder.append("\nCandidate words detected:\n[");
            for (String s : fuzzyCandidates) candidatesBuilder.append(s).append(", ");
            if (candidatesBuilder.length() > 2) candidatesBuilder.setLength(candidatesBuilder.length() - 2);
            candidatesBuilder.append("]\n\n");
            candidatesBuilder.append("You MUST choose the drug_name from this list or close variants. Do NOT invent new words.\n");
        }

        String prompt = "Extract medication details from this noisy OCR text: " + rawOcrText + "\n" +
                candidatesBuilder.toString() +
                "Respond ONLY with a JSON object exactly like this, nothing else:\n" +
                "{\n" +
                "  \"brand_name\": \"...\",\n" +
                "  \"drug_name\": \"...\",\n" +
                "  \"dosage\": \"...\",\n" +
                "  \"expiry_date\": \"...\"\n" +
                "}";

        // Build JSON request body
        JSONObject jsonBody;
        try {
            jsonBody = new JSONObject();
            jsonBody.put("model", MODEL);
            jsonBody.put("temperature", 0.0);
            jsonBody.put("response_format", new JSONObject().put("type", "json_object"));

            JSONArray messages = new JSONArray();
            JSONObject msg = new JSONObject();
            msg.put("role", "user");
            msg.put("content", prompt);
            messages.put(msg);
            jsonBody.put("messages", messages);
        } catch (Exception e) {
            Log.e(TAG, "[GROQ_ERROR] Failed to build request JSON: " + e.getMessage());
            return null; // Step 6: Failsafe — never crash
        }

        RequestBody body = RequestBody.create(
                jsonBody.toString(),
                MediaType.parse("application/json; charset=utf-8"));

        // Step 3: Correct headers (Authorization + Content-Type)
        Request request = new Request.Builder()
                .url(GROQ_API_URL)
                .post(body)
                .addHeader("Authorization", "Bearer " + GROQ_API_KEY)
                .addHeader("Content-Type", "application/json")
                .build();

        // Step 5: Logging
        Log.d(TAG, "[GROQ_CALL] Sending request to Groq. Model: " + MODEL
                + " | Candidates: " + (fuzzyCandidates != null ? fuzzyCandidates.size() : 0));

        try (Response response = client.newCall(request).execute()) {

            if (!response.isSuccessful() || response.body() == null) {
                Log.e(TAG, "[GROQ_ERROR] HTTP " + response.code() + " — " + response.message());
                return null; // Step 6: Failsafe
            }

            String responseBody = response.body().string();
            Log.d(TAG, "[GROQ_RESPONSE] Raw: " + responseBody);

            // Parse response
            JSONObject root     = new JSONObject(responseBody);
            String content      = root.getJSONArray("choices")
                                      .getJSONObject(0)
                                      .getJSONObject("message")
                                      .getString("content");

            JSONObject extracted = new JSONObject(content);
            String brand  = nullIfEmpty(extracted.optString("brand_name", null));
            String drug   = nullIfEmpty(extracted.optString("drug_name", null));
            String dosage = nullIfEmpty(extracted.optString("dosage", null));
            String expiry = nullIfEmpty(extracted.optString("expiry_date", null));

            Log.d(TAG, "[GROQ_RESPONSE] Parsed → brand=" + brand
                    + " | drug=" + drug + " | dosage=" + dosage + " | expiry=" + expiry);

            // Step 7: Build result for scoring
            EngineResult res = new EngineResult(brand, drug, dosage, expiry, 80, "groq");

            // Grounding check — boosts score if drug is in known candidates
            if (fuzzyCandidates != null && drug != null) {
                for (String cand : fuzzyCandidates) {
                    if (drug.toUpperCase().contains(cand.toUpperCase())) {
                        res.isGrounded = true;
                        break;
                    }
                }
            }

            return res;

        } catch (Exception e) {
            // Step 6: Failsafe — log and return null cleanly
            Log.e(TAG, "[GROQ_ERROR] Exception during call or parsing: " + e.getMessage(), e);
            return null;
        }
    }

    public interface PointersCallback {
        void onResult(String resultText);
    }

    public static void getMedicationPointers(String drugName, PointersCallback callback) {
        new Thread(() -> {
            OkHttpClient client = new OkHttpClient.Builder()
                    .connectTimeout(3000, TimeUnit.MILLISECONDS)
                    .readTimeout(8000, TimeUnit.MILLISECONDS)
                    .writeTimeout(3000, TimeUnit.MILLISECONDS)
                    .build();

            String prompt = "Why should a patient take " + drugName + "? Please provide 3 short bullet pointers explaining its medical purpose. First provide the pointers in English, then provide the exact 3 pointers translated to Hindi. Respond directly with the pointers, no conversational filler or intro.";

            try {
                JSONObject jsonBody = new JSONObject();
                jsonBody.put("model", MODEL);
                jsonBody.put("temperature", 0.3); // Slight creativity for text gen

                JSONArray messages = new JSONArray();
                JSONObject msg = new JSONObject();
                msg.put("role", "user");
                msg.put("content", prompt);
                messages.put(msg);
                jsonBody.put("messages", messages);

                RequestBody body = RequestBody.create(
                        jsonBody.toString(),
                        MediaType.parse("application/json; charset=utf-8"));

                Request request = new Request.Builder()
                        .url(GROQ_API_URL)
                        .post(body)
                        .addHeader("Authorization", "Bearer " + GROQ_API_KEY)
                        .addHeader("Content-Type", "application/json")
                        .build();

                try (Response response = client.newCall(request).execute()) {
                    if (!response.isSuccessful() || response.body() == null) {
                        callback.onResult(null);
                        return;
                    }

                    String responseBody = response.body().string();
                    JSONObject root = new JSONObject(responseBody);
                    String content = root.getJSONArray("choices")
                            .getJSONObject(0)
                            .getJSONObject("message")
                            .getString("content");

                    callback.onResult(content);
                }
            } catch (Exception e) {
                Log.e(TAG, "[GROQ_ERROR] Failed to fetch pointers: " + e.getMessage());
                callback.onResult(null);
            }
        }).start();
    }

    public static void getDdiPointers(String interactionsText, PointersCallback callback) {
        new Thread(() -> {
            OkHttpClient client = new OkHttpClient.Builder()
                    .connectTimeout(3000, TimeUnit.MILLISECONDS)
                    .readTimeout(8000, TimeUnit.MILLISECONDS)
                    .writeTimeout(3000, TimeUnit.MILLISECONDS)
                    .build();

            String prompt = "Explain the following drug interactions in simple, patient-friendly terms:\n" + interactionsText + "\n\nPlease provide 3 short bullet pointers explaining the risks and what the patient should do. First provide the pointers in English, then provide the exact translation in Hindi. Respond directly with the pointers, no conversational filler or intro.";

            try {
                JSONObject jsonBody = new JSONObject();
                jsonBody.put("model", MODEL);
                jsonBody.put("temperature", 0.3);

                JSONArray messages = new JSONArray();
                JSONObject msg = new JSONObject();
                msg.put("role", "user");
                msg.put("content", prompt);
                messages.put(msg);
                jsonBody.put("messages", messages);

                RequestBody body = RequestBody.create(
                        jsonBody.toString(),
                        MediaType.parse("application/json; charset=utf-8"));

                Request request = new Request.Builder()
                        .url(GROQ_API_URL)
                        .post(body)
                        .addHeader("Authorization", "Bearer " + GROQ_API_KEY)
                        .addHeader("Content-Type", "application/json")
                        .build();

                try (Response response = client.newCall(request).execute()) {
                    if (!response.isSuccessful() || response.body() == null) {
                        callback.onResult(null);
                        return;
                    }

                    String responseBody = response.body().string();
                    JSONObject root = new JSONObject(responseBody);
                    String content = root.getJSONArray("choices")
                            .getJSONObject(0)
                            .getJSONObject("message")
                            .getString("content");

                    callback.onResult(content);
                }
            } catch (Exception e) {
                Log.e(TAG, "[GROQ_ERROR] Failed to fetch DDI pointers: " + e.getMessage());
                callback.onResult(null);
            }
        }).start();
    }

    /**
     * Sanitizes string fields from the model — treats "null" strings and empty as null.
     */
    private static String nullIfEmpty(String val) {
        if (val == null) return null;
        String trimmed = val.trim();
        if (trimmed.isEmpty() || trimmed.equalsIgnoreCase("null") || trimmed.equals("...")) return null;
        return trimmed;
    }
}
