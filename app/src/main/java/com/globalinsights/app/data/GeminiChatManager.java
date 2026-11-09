package com.globalinsights.app.data;

import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;

import com.globalinsights.app.BuildConfig;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class GeminiChatManager {

    private static final String TAG = "GeminiChatManager";

    public static final String PRIMARY_MODEL_ID = "models/gemini-3.8-flash";
    private static final String[] FALLBACK_MODELS = new String[]{
            "models/gemini-3.8-flash",
            "models/gemini-3.7-flash",
            "models/gemini-3.6-flash"
    };

    private static final String ENDPOINT_URL =
            "https://generativelanguage.googleapis.com/v1beta/interactions?key=";

    private static final String SYSTEM_PREFIX =
            "System Instructions: You are Global Insights AI, an assistant specialized in country statistics, population, GDP and economic comparisons. " +
            "Give clear, accurate, beginner-friendly answers. Never invent statistics. " +
            "When actual World Bank data is available in the app, use it for factual statistics.\n\n";

    public interface GeminiCallback {
        void onSuccess(String responseText, String activeModelId);
        void onError(String errorMessage);
    }

    public interface SummaryCallback {
        void onSuccess(String summaryText);
        void onError(String errorMessage);
    }

    public static String formatModelDisplayName(String modelId) {
        if (modelId == null) return "Gemini AI";
        String clean = modelId.replace("models/", "").trim();
        if (clean.equalsIgnoreCase("gemini-3.8-flash")) return "Gemini 3.8 Flash";
        if (clean.equalsIgnoreCase("gemini-3.7-flash")) return "Gemini 3.7 Flash";
        if (clean.equalsIgnoreCase("gemini-3.6-flash")) return "Gemini 3.6 Flash";
        if (clean.startsWith("gemini-")) {
            String version = clean.substring(7);
            return "Gemini " + version.replace("-", " ").toUpperCase(Locale.US);
        }
        return "Gemini AI";
    }

    public static class ChatTurn {
        public final String role; // "user" or "model"
        public final String text;

        public ChatTurn(String role, String text) {
            this.role = role;
            this.text = text;
        }
    }

    private final List<ChatTurn> conversationHistory = new ArrayList<>();
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public void clearHistory() {
        conversationHistory.clear();
    }

    public void sendMessage(String userMessage, String countryContext, GeminiCallback callback) {
        String apiKey = BuildConfig.GEMINI_API_KEY;

        if (TextUtils.isEmpty(apiKey) || apiKey.contains("YOUR_GEMINI_API_KEY_HERE")) {
            mainHandler.post(() -> callback.onError("Gemini API key is not configured in local.properties."));
            return;
        }

        if (TextUtils.isEmpty(userMessage) || userMessage.trim().isEmpty()) {
            mainHandler.post(() -> callback.onError("Message cannot be empty."));
            return;
        }

        // Build current prompt message
        StringBuilder formattedMessage = new StringBuilder();
        if (conversationHistory.isEmpty()) {
            formattedMessage.append(SYSTEM_PREFIX);
        }
        formattedMessage.append(userMessage.trim());

        if (!TextUtils.isEmpty(countryContext)) {
            formattedMessage.append("\n\n[App World Bank Context: ").append(countryContext).append("]");
        }

        final String currentPromptText = formattedMessage.toString();

        executorService.execute(() -> {
            int maxAttempts = FALLBACK_MODELS.length; // 3 models: 3.8-flash, 3.7-flash, 3.6-flash
            long[] defaultBackoffDelaysMs = new long[]{2000L, 4000L}; // Default backoff delays

            boolean success = false;
            boolean allQuotaExceeded = true;
            String lastUserError = "Gemini is temporarily busy. Please try again in a few minutes.";

            for (int attempt = 0; attempt < maxAttempts; attempt++) {
                String currentModel = FALLBACK_MODELS[attempt];

                HttpURLConnection conn = null;
                try {
                    Log.d(TAG, "=== GEMINI INTERACTIONS API EXECUTION (Attempt " + (attempt + 1) + " of " + maxAttempts + ") ===");
                    Log.d(TAG, "Active Model: " + currentModel);

                    // Compile conversation history and current prompt for the Interactions API 'input' string
                    StringBuilder inputBuilder = new StringBuilder();
                    for (ChatTurn turn : conversationHistory) {
                        inputBuilder.append(turn.role.equals("user") ? "User:\n" : "Assistant:\n")
                                    .append(turn.text).append("\n\n");
                    }
                    inputBuilder.append("User:\n").append(currentPromptText);

                    // Build Interactions API payload
                    JSONObject rootObj = new JSONObject();
                    rootObj.put("model", currentModel);
                    rootObj.put("input", inputBuilder.toString().trim());

                    JSONObject genConfigObj = new JSONObject();
                    genConfigObj.put("max_output_tokens", 2048);
                    genConfigObj.put("thinking_level", "medium");
                    rootObj.put("generation_config", genConfigObj);

                    // Connect and send POST request
                    String requestUrl = ENDPOINT_URL + apiKey;
                    URL url = new URL(requestUrl);
                    conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("POST");
                    conn.setRequestProperty("Content-Type", "application/json");
                    conn.setConnectTimeout(20000); // 20s connection timeout
                    conn.setReadTimeout(60000);    // 60s read timeout
                    conn.setDoOutput(true);

                    long startTime = System.currentTimeMillis();

                    byte[] inputBytes = rootObj.toString().getBytes(StandardCharsets.UTF_8);
                    try (OutputStream os = conn.getOutputStream()) {
                        os.write(inputBytes, 0, inputBytes.length);
                    }

                    int responseCode = conn.getResponseCode();
                    long duration = System.currentTimeMillis() - startTime;

                    Log.d(TAG, "HTTP Status Code: " + responseCode + " for model " + currentModel + " (" + duration + " ms)");

                    String rawResponse = "";
                    InputStream is = (responseCode == HttpURLConnection.HTTP_OK) ? conn.getInputStream() : conn.getErrorStream();
                    if (is != null) {
                        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                            StringBuilder responseBuilder = new StringBuilder();
                            String line;
                            while ((line = reader.readLine()) != null) {
                                responseBuilder.append(line);
                            }
                            rawResponse = responseBuilder.toString();
                        }
                    }

                    if (responseCode == HttpURLConnection.HTTP_OK) {
                        String parsedText = parseInteractionsApiResponse(rawResponse);

                        if (!parsedText.isEmpty()) {
                            Log.d(TAG, "SUCCESS! Received response from model: " + currentModel);
                            conversationHistory.add(new ChatTurn("user", currentPromptText));
                            conversationHistory.add(new ChatTurn("model", parsedText));

                            final String finalReply = parsedText;
                            final String successfulModel = currentModel;
                            mainHandler.post(() -> callback.onSuccess(finalReply, successfulModel));
                            success = true;
                            break;
                        } else {
                            allQuotaExceeded = false;
                            lastUserError = "Gemini AI returned an empty response. Please rephrase.";
                            break;
                        }
                    } else if (responseCode == 429) {
                        Log.w(TAG, "Model " + currentModel + " returned HTTP 429 (Rate Limit Exceeded). Attempting next fallback model...");
                        lastUserError = "Gemini free-tier request limit reached.";
                    } else if (responseCode == 503 || responseCode == 500 || responseCode == 502 || responseCode == 504) {
                        allQuotaExceeded = false;
                        Log.w(TAG, "Model " + currentModel + " returned HTTP " + responseCode + " (Server Busy). Switching to next fallback model...");
                        lastUserError = "Gemini is temporarily busy.";
                        if (attempt < maxAttempts - 1) {
                            try {
                                Thread.sleep(defaultBackoffDelaysMs[attempt]);
                            } catch (InterruptedException ignored) {}
                        }
                    } else {
                        allQuotaExceeded = false;
                        String apiErrorMessage = "HTTP " + responseCode + " Error";
                        try {
                            JSONObject errJson = new JSONObject(rawResponse);
                            JSONObject errObj = errJson.optJSONObject("error");
                            if (errObj != null) {
                                apiErrorMessage = errObj.optString("message", apiErrorMessage);
                            }
                        } catch (Exception ignored) {}

                        Log.e(TAG, "Non-fallback Gemini API Error (" + responseCode + "): " + apiErrorMessage);
                        lastUserError = "Gemini API Error (" + responseCode + "): " + apiErrorMessage;
                        break;
                    }

                } catch (SocketTimeoutException e) {
                    allQuotaExceeded = false;
                    Log.e(TAG, "SocketTimeoutException on model " + currentModel, e);
                    lastUserError = "Request timed out while waiting for Gemini AI response.";
                } catch (Exception e) {
                    allQuotaExceeded = false;
                    Log.e(TAG, "Network exception during Gemini API request on model " + currentModel, e);
                    lastUserError = "Network Exception: " + e.getLocalizedMessage();
                    break;
                } finally {
                    if (conn != null) {
                        conn.disconnect();
                    }
                }
            }

            if (!success) {
                if (allQuotaExceeded) {
                    lastUserError = "Gemini free-tier request limit reached.";
                }
                final String finalError = lastUserError;
                mainHandler.post(() -> callback.onError(finalError));
            }
        });
    }

    public void generateNoteSummary(String title, String content, SummaryCallback callback) {
        String apiKey = BuildConfig.GEMINI_API_KEY;

        if (TextUtils.isEmpty(apiKey) || apiKey.contains("YOUR_GEMINI_API_KEY_HERE")) {
            mainHandler.post(() -> callback.onError("Gemini API key is not configured in local.properties."));
            return;
        }

        if (TextUtils.isEmpty(content) || content.trim().isEmpty()) {
            mainHandler.post(() -> callback.onError("Note content cannot be empty."));
            return;
        }

        String cleanTitle = (title != null) ? title.trim() : "";
        String cleanContent = content.trim();

        StringBuilder promptBuilder = new StringBuilder();
        promptBuilder.append("Summarize the following note in 2 to 3 clear, complete sentences. Do not include intros, meta-commentary, or markdown headers.\n\n");
        if (!cleanTitle.isEmpty()) {
            promptBuilder.append("Note Title: ").append(cleanTitle).append("\n");
        }
        promptBuilder.append("Note Content:\n").append(cleanContent);

        final String promptText = promptBuilder.toString();

        executorService.execute(() -> {
            int maxAttempts = FALLBACK_MODELS.length;
            long[] defaultBackoffDelaysMs = new long[]{2000L, 4000L};

            boolean success = false;
            String lastUserError = "AI Summary generation failed.";

            for (int attempt = 0; attempt < maxAttempts; attempt++) {
                String currentModel = FALLBACK_MODELS[attempt];

                if (attempt > 0) {
                    try {
                        Thread.sleep(defaultBackoffDelaysMs[attempt - 1]);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }

                HttpURLConnection conn = null;
                try {
                    JSONObject rootObj = new JSONObject();
                    rootObj.put("model", currentModel);
                    rootObj.put("input", promptText);

                    JSONObject genConfigObj = new JSONObject();
                    genConfigObj.put("max_output_tokens", 2048);
                    rootObj.put("generation_config", genConfigObj);

                    String requestUrl = ENDPOINT_URL + apiKey;
                    URL url = new URL(requestUrl);
                    conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("POST");
                    conn.setRequestProperty("Content-Type", "application/json");
                    conn.setConnectTimeout(15000);
                    conn.setReadTimeout(30000);
                    conn.setDoOutput(true);

                    byte[] inputBytes = rootObj.toString().getBytes(StandardCharsets.UTF_8);
                    try (OutputStream os = conn.getOutputStream()) {
                        os.write(inputBytes, 0, inputBytes.length);
                    }

                    int responseCode = conn.getResponseCode();
                    String rawResponse = "";
                    InputStream is = (responseCode == HttpURLConnection.HTTP_OK) ? conn.getInputStream() : conn.getErrorStream();
                    if (is != null) {
                        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                            StringBuilder responseBuilder = new StringBuilder();
                            String line;
                            while ((line = reader.readLine()) != null) {
                                responseBuilder.append(line);
                            }
                            rawResponse = responseBuilder.toString();
                        }
                    }

                    if (responseCode == HttpURLConnection.HTTP_OK) {
                        String parsedText = parseInteractionsApiResponse(rawResponse);
                        if (!parsedText.isEmpty() && !parsedText.contains("Files Modified") && !parsedText.contains("Root Cause")) {
                            final String summary = parsedText;
                            mainHandler.post(() -> callback.onSuccess(summary));
                            success = true;
                            break;
                        }
                    } else if (responseCode == 429) {
                        lastUserError = "AI rate limit reached.";
                    } else {
                        lastUserError = "AI service temporarily unavailable (" + responseCode + ")";
                    }

                } catch (Exception e) {
                    Log.e(TAG, "Error generating AI note summary on model " + currentModel, e);
                    lastUserError = "AI Summary generation failed.";
                } finally {
                    if (conn != null) conn.disconnect();
                }
            }

            if (!success) {
                final String err = lastUserError;
                mainHandler.post(() -> callback.onError(err));
            }
        });
    }

    private String parseInteractionsApiResponse(String rawResponse) {
        if (rawResponse == null || rawResponse.trim().isEmpty()) {
            return "";
        }

        try {
            JSONObject root = new JSONObject(rawResponse);
            StringBuilder extractedText = new StringBuilder();

            JSONArray steps = root.optJSONArray("steps");
            if (steps != null && steps.length() > 0) {
                for (int s = 0; s < steps.length(); s++) {
                    JSONObject step = steps.optJSONObject(s);
                    if (step == null) continue;

                    String type = step.optString("type", "");
                    // Skip internal model thinking/reasoning steps!
                    if ("thinking".equalsIgnoreCase(type) || "thought".equalsIgnoreCase(type) || "reasoning".equalsIgnoreCase(type)) {
                        continue;
                    }

                    // Process model_output or content steps
                    if ("model_output".equalsIgnoreCase(type) || step.has("content")) {
                        JSONArray contentArray = step.optJSONArray("content");
                        if (contentArray != null) {
                            StringBuilder stepText = new StringBuilder();
                            for (int c = 0; c < contentArray.length(); c++) {
                                JSONObject contentItem = contentArray.optJSONObject(c);
                                if (contentItem == null) continue;

                                String contentType = contentItem.optString("type", "");
                                if ("thinking".equalsIgnoreCase(contentType) || "thought".equalsIgnoreCase(contentType) || "reasoning".equalsIgnoreCase(contentType)) {
                                    continue;
                                }

                                if ("text".equalsIgnoreCase(contentType) || contentItem.has("text")) {
                                    String textBlock = contentItem.optString("text", "");
                                    stepText.append(textBlock);
                                }
                            }
                            String trimmedStep = stepText.toString().trim();
                            if (!trimmedStep.isEmpty()) {
                                if (extractedText.length() > 0) {
                                    extractedText.append("\n\n");
                                }
                                extractedText.append(trimmedStep);
                            }
                        }
                    }
                }
            }

            String finalResult = extractedText.toString().trim();
            if (!finalResult.isEmpty()) {
                return finalResult;
            }

            // Fallback 1: Root level "output_text"
            String outputText = root.optString("output_text", "").trim();
            if (!outputText.isEmpty()) {
                return outputText;
            }

            // Fallback 2: Standard "candidates" array fallback ("candidates" -> "content" -> "parts" -> "text")
            JSONArray candidates = root.optJSONArray("candidates");
            if (candidates != null && candidates.length() > 0) {
                StringBuilder sb = new StringBuilder();
                for (int c = 0; c < candidates.length(); c++) {
                    JSONObject candidate = candidates.optJSONObject(c);
                    if (candidate != null) {
                        JSONObject content = candidate.optJSONObject("content");
                        if (content != null) {
                            JSONArray parts = content.optJSONArray("parts");
                            if (parts != null) {
                                for (int p = 0; p < parts.length(); p++) {
                                    JSONObject part = parts.optJSONObject(p);
                                    if (part != null && part.has("text")) {
                                        sb.append(part.optString("text"));
                                    }
                                }
                            }
                        }
                    }
                }
                String result = sb.toString().trim();
                if (!result.isEmpty()) {
                    return result;
                }
            }

        } catch (Exception e) {
            Log.e(TAG, "Error parsing response JSON in parseInteractionsApiResponse", e);
        }

        return "";
    }

    public void shutdown() {
        executorService.shutdown();
    }
}