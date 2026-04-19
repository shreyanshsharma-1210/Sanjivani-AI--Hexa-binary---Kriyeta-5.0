package com.emergency.patient.scheduling;

import android.util.Log;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class GroqScheduleInterpreter {

    private static final String TAG = "GroqScheduleInterpreter";
    private static final String GROQ_API_URL = "https://api.groq.com/openai/v1/chat/completions";
    private static final String GROQ_API_KEY = "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";
    private static final String MODEL = "meta-llama/llama-4-scout-17b-16e-instruct";

    public static ScheduleData parseWithFallback(String dosageText) {
        // First try deterministic rules
        ScheduleData structured = DosageParser.parse(dosageText);
        
        // If it looks complex (unclear frequency or quantity), use Groq
        if (isComplex(dosageText)) {
            ScheduleData aiResult = callGroq(dosageText);
            if (aiResult != null) return aiResult;
        }
        
        return structured;
    }

    private static boolean isComplex(String text) {
        if (text == null) return false;
        String t = text.toLowerCase();
        return t.contains("every") || t.contains("hours") || t.contains("needed") || t.contains("/") || t.contains("-");
    }

    private static ScheduleData callGroq(String dosageText) {
        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .build();

        String prompt = "Extract structured medication schedule from this dosage text: \"" + dosageText + "\"\n" +
                "Respond ONLY with a JSON object like this:\n" +
                "{\n" +
                "  \"frequency\": \"...\",\n" +
                "  \"times\": [\"HH:mm\", \"HH:mm\"],\n" +
                "  \"quantity\": \"...\",\n" +
                "  \"instructions\": \"...\"\n" +
                "}";

        try {
            JSONObject jsonBody = new JSONObject();
            jsonBody.put("model", MODEL);
            jsonBody.put("temperature", 0.0);
            jsonBody.put("response_format", new JSONObject().put("type", "json_object"));

            JSONArray messages = new JSONArray();
            messages.put(new JSONObject().put("role", "user").put("content", prompt));
            jsonBody.put("messages", messages);

            RequestBody body = RequestBody.create(jsonBody.toString(), MediaType.parse("application/json; charset=utf-8"));
            Request request = new Request.Builder()
                    .url(GROQ_API_URL)
                    .post(body)
                    .addHeader("Authorization", "Bearer " + GROQ_API_KEY)
                    .build();

            try (Response response = client.newCall(request).execute()) {
                if (response.isSuccessful() && response.body() != null) {
                    String content = new JSONObject(response.body().string())
                            .getJSONArray("choices").getJSONObject(0)
                            .getJSONObject("message").getString("content");
                    
                    JSONObject extracted = new JSONObject(content);
                    ScheduleData data = new ScheduleData();
                    data.frequency = extracted.optString("frequency");
                    data.quantity = extracted.optString("quantity");
                    data.instructions = extracted.optString("instructions");
                    
                    JSONArray timesArr = extracted.optJSONArray("times");
                    data.times = new ArrayList<>();
                    if (timesArr != null) {
                        for (int i = 0; i < timesArr.length(); i++) {
                            data.times.add(timesArr.getString(i));
                        }
                    }
                    return data;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Groq scheduling fallback failed", e);
        }
        return null;
    }
}
