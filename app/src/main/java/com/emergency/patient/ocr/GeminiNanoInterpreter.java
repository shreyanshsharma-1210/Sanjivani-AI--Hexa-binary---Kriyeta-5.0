package com.emergency.patient.ocr;

import android.content.Context;
import android.util.Log;

import com.google.ai.edge.aicore.GenerativeModel;
import com.google.ai.edge.aicore.GenerationConfig;
import com.google.ai.edge.aicore.DownloadConfig;
import com.google.ai.edge.aicore.Content;
import com.google.ai.edge.aicore.java.GenerativeModelFutures;
import com.google.ai.edge.aicore.GenerateContentResponse;

import org.json.JSONObject;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * GeminiNanoInterpreter
 *
 * Interfaces with the on-device Large Language Model (Gemini Nano) to extract
 * structured fields from raw OCR text. Uses strict prompting to minimize hallucination.
 * Completely safe-to-fail (returns null if the device lacks AICore support or times out).
 */
public class GeminiNanoInterpreter {

    private static final String TAG = "GeminiNanoInterpreter";
    private static final ExecutorService executor = Executors.newSingleThreadExecutor();



    /**
     * Executes the on-device inference with a strict timeout so the UI doesn't hang.
     */
    public static EngineResult extractWithTimeout(Context context, String rawOcrText, java.util.Set<String> fuzzyCandidates, int timeoutSeconds) {
        // Step: Explicitly check if model is ready/linked
        if (!isModelReady(context)) {
            Log.w(TAG, "[GEMINI_UNAVAILABLE_DEVICE] On-device AI Core not available on this device.");
            return null;
        }

        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<EngineResult> future = executor.submit(new Callable<EngineResult>() {
            @Override
            public EngineResult call() throws Exception {
                return executeInference(context, rawOcrText, fuzzyCandidates);
            }
        });

        long startTime = System.currentTimeMillis();
        try {
            EngineResult result = future.get(timeoutSeconds, TimeUnit.SECONDS);
            long duration = System.currentTimeMillis() - startTime;
            Log.d(TAG, "[GEMINI_TIME] Execution took: " + duration + " ms");
            Log.d(TAG, "[GEMINI_DONE]");
            return result;
        } catch (TimeoutException e) {
            Log.e(TAG, "[GEMINI_ERROR] Gemini Nano inference timed out after " + timeoutSeconds + " seconds.");
            future.cancel(true);
            return null;
        } catch (Exception e) {
            Log.e(TAG, "[GEMINI_ERROR] Exception in Gemini Nano extraction: " + e.getMessage(), e);
            return null;
        }
    }

    /**
     * The core inference engine. Prompts the on-device model and parses the JSON response.
     */
    private static EngineResult executeInference(Context context, String rawOcrText, java.util.Set<String> fuzzyCandidates) {
        // 1. Clean the text slightly to save token space
        String cleanedText = rawOcrText.replaceAll("\\n+", " ").replaceAll("\\s+", " ").trim();

        // 2. Strict Non-Creative Prompt
        String prompt = buildPrompt(cleanedText, fuzzyCandidates);

        // Log Input (Step 1)
        Log.d(TAG, "[GEMINI_INPUT]");
        Log.d(TAG, "Text: " + cleanedText);
        Log.d(TAG, "Candidates: " + (fuzzyCandidates != null ? fuzzyCandidates.toString() : "[]"));

        try {
            // Run the actual on-device inference via AICore
            String jsonString = performOnDeviceInference(context, prompt);
            
            // Log Raw Output (Step 2)
            Log.d(TAG, "[GEMINI_RAW_RESPONSE]");
            Log.d(TAG, jsonString != null ? jsonString : "NULL");

            if (jsonString == null || jsonString.isEmpty()) {
                return null;
            }

            // 3. Extract purely JSON content (in case the model wraps it in markdown)
            jsonString = sanitizeJsonResponse(jsonString);

            // 4. Safe JSON parsing
            JSONObject json = new JSONObject(jsonString);
            
            String brand = json.optString("brand_name", null);
            String drug  = json.optString("drug_name", null);
            String dosage = json.optString("dosage", null);
            String expiry = json.optString("expiry_date", null);
            
            if ("null".equalsIgnoreCase(brand)) brand = null;
            if ("null".equalsIgnoreCase(drug)) drug = null;
            if ("null".equalsIgnoreCase(dosage)) dosage = null;
            if ("null".equalsIgnoreCase(expiry)) expiry = null;

            // Log Parsed Output (Step 3)
            Log.d(TAG, "[GEMINI_PARSED]");
            Log.d(TAG, "brand=" + (brand != null ? brand : "null"));
            Log.d(TAG, "drug=" + (drug != null ? drug : "null"));
            Log.d(TAG, "dosage=" + (dosage != null ? dosage : "null"));
            Log.d(TAG, "expiry=" + (expiry != null ? expiry : "null"));

            EngineResult result = new EngineResult(brand, drug, dosage, expiry, 80, "nano");
            
            // Check grounding
            if (fuzzyCandidates != null && drug != null) {
                for (String cand : fuzzyCandidates) {
                    if (drug.toUpperCase().contains(cand.toUpperCase())) {
                        result.isGrounded = true;
                        break;
                    }
                }
            }
            
            return result;

        } catch (Exception e) {
            Log.e(TAG, "[GEMINI_ERROR] Failed to parse LLM Response as JSON: " + e.getMessage(), e);
            return null; // Fail safely
        }
    }

    private static String buildPrompt(String targetText, java.util.Set<String> fuzzyCandidates) {
        StringBuilder candidatesStr = new StringBuilder();
        if (fuzzyCandidates != null && !fuzzyCandidates.isEmpty()) {
            candidatesStr.append("Candidate Words:\n[");
            for (String s : fuzzyCandidates) candidatesStr.append(s).append(", ");
            if (candidatesStr.length() > 2) candidatesStr.setLength(candidatesStr.length() - 2);
            candidatesStr.append("]\n\n");
            candidatesStr.append("ONLY choose from candidate words if possible. DO NOT hallucinate.\n\n");
        }

        return "Extract the following fields from the given OCR text:\n\n" +
                "- brand_name\n" +
                "- drug_name\n" +
                "- dosage\n" +
                "- expiry_date\n\n" +
                "Rules:\n" +
                "- ONLY choose from candidate words if possible\n" +
                "- DO NOT hallucinate\n" +
                "- If not found → return \"NOT_DECLARED\"\n\n" +
                "OCR Text:\n<<< " + targetText + " >>>\n\n" +
                candidatesStr.toString() +
                "Return ONLY JSON:\n" +
                "{\n" +
                "  \"brand_name\": \"...\",\n" +
                "  \"drug_name\": \"...\",\n" +
                "  \"dosage\": \"...\",\n" +
                "  \"expiry_date\": \"...\"\n" +
                "}";
    }

    /**
     * Mock/Placeholder for native AICore integration.
     * To activate natively, you would inject Google AI Edge SDK.
     */
    private static boolean isModelReady(Context context) {
        try {
            // Check if the system service package is installed
            context.getPackageManager().getPackageInfo("com.google.android.aicore", 0);
            return true;
        } catch (Exception e) {
            // Fallback: the library might provide a check in newer versions, 
            // but for now we check for the system app existence which is the source of Gemini Nano.
            return false;
        }
    }

    /**
     * Executes real on-device inference using AICore session.
     */
    private static String performOnDeviceInference(Context context, String prompt) {
        try {
            // 1. Configure generation parameters using the Builder (non-fluent API in some versions)
            GenerationConfig.Builder configBuilder = new GenerationConfig.Builder();
            configBuilder.setTemperature(0.0f);
            GenerationConfig config = configBuilder.build();

            // 2. Configure download/loading
            DownloadConfig downloadConfig = new DownloadConfig();

            // 3. Create the GenerativeModel instance using the direct constructor
            GenerativeModel model = new GenerativeModel(config, downloadConfig);

            // 4. Wrap for Java compatibility (ListenableFutures)
            GenerativeModelFutures modelFutures = GenerativeModelFutures.from(model);

            // 5. Structure the prompt into a Content object
            Content content = new Content.Builder()
                    .addText(prompt)
                    .build();

            // 6. Execute inference
            Log.d(TAG, "Gemini Nano session established. Running inference...");
            
            // generateContent returns a ListenableFuture<GenerateContentResponse>
            GenerateContentResponse response = modelFutures.generateContent(content).get();
            
            return response != null ? response.getText() : null;

        } catch (Exception e) {
            Log.e(TAG, "[GEMINI_ERROR] AICore session or inference failed: " + e.getMessage(), e);
            return null; // Fallback to other engines
        }
    }

    private static String sanitizeJsonResponse(String rawResponse) {
        // Sometimes LLMs return ```json { ... } ```
        int start = rawResponse.indexOf('{');
        int end = rawResponse.lastIndexOf('}');
        if (start != -1 && end != -1 && start < end) {
            return rawResponse.substring(start, end + 1);
        }
        return rawResponse;
    }
}
