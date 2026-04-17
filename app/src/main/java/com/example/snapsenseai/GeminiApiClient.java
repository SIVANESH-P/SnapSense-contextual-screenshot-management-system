package com.example.snapsenseai;

import android.util.Log;
import org.json.JSONArray;
import org.json.JSONObject;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class GeminiApiClient {
    private static final String API_KEY = "AIzaSyBz2xOuNpqHcU2-OA3_6g9C0ODqvN9OodA";
    // 1. Updated Model ID for the Lite version
    private static final String MODEL = "gemini-2.5-flash-lite";

    // 2. The URL will automatically use the new 'lite' string
    private static final String URL = "https://generativelanguage.googleapis.com/v1beta/models/" + MODEL + ":generateContent?key=" + API_KEY;

    public interface GeminiCallback {
        // Updated: Now includes date and time for Phase 6 Calendar integration
        void onSuccess(String category, int ttlHours, String suggestedName, String eventDate, String eventTime);
        void onFailure(Exception e);
    }

    public void classifyContent(String redactedText, GeminiCallback callback) {
        new Thread(() -> {
            try {
                OkHttpClient client = new OkHttpClient.Builder()
                        .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                        .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                        .build();

                // Phase 6 Updated Prompt: Specifically asking for ISO Date and Time
                String prompt = "Analyze this redacted text: '" + redactedText + "'. " +
                        "1. Classify: Finance, Study, Social, Travel, or OTP. " +
                        "2. TTL: If temporary, provide hours. " +
                        "3. Naming: Suggest a file name. " +
                        "4. Calendar: If you see a movie/event date, extract it as YYYY-MM-DD and HH:mm. " +
                        "Return ONLY JSON: {\"category\": \"string\", \"ttl\": int, \"name\": \"string\", \"date\": \"YYYY-MM-DD\", \"time\": \"HH:mm\"}. " +
                        "If no date found, return null for date and time fields.";

                JSONObject jsonBody = new JSONObject();
                JSONObject part = new JSONObject().put("text", prompt);
                JSONArray parts = new JSONArray().put(part);
                JSONObject content = new JSONObject().put("parts", parts);
                JSONArray contents = new JSONArray().put(content);
                jsonBody.put("contents", contents);

                RequestBody body = RequestBody.create(jsonBody.toString(), MediaType.parse("application/json"));
                Request request = new Request.Builder().url(URL).post(body).build();

                try (Response response = client.newCall(request).execute()) {
                    if (response.isSuccessful() && response.body() != null) {
                        String result = response.body().string();
                        parseGeminiResponse(result, callback);
                    } else {
                        callback.onFailure(new Exception("HTTP " + response.code()));
                    }
                }
            } catch (Exception e) {
                callback.onFailure(e);
            }
        }).start();
    }

    private void parseGeminiResponse(String responseBody, GeminiCallback callback) {
        try {
            JSONObject json = new JSONObject(responseBody);
            String textResponse = json.getJSONArray("candidates")
                    .getJSONObject(0).getJSONObject("content")
                    .getJSONArray("parts").getJSONObject(0).getString("text");

            // Clean JSON markdown if present
            String cleanedJson = textResponse.replaceAll("```json", "").replaceAll("```", "").trim();
            JSONObject result = new JSONObject(cleanedJson);

            // Phase 6: Extracting the new fields
            String category = result.optString("category", "Miscellaneous");
            int ttl = result.optInt("ttl", 24);
            String name = result.optString("name", "Screenshot_" + System.currentTimeMillis());
            String date = result.optString("date", null);
            String time = result.optString("time", null);

            callback.onSuccess(category, ttl, name, date, time);
        } catch (Exception e) {
            Log.e("SnapSense", "Parsing Error: " + e.getMessage());
            callback.onFailure(e);
        }
    }
}