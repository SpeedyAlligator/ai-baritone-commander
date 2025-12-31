package com.aicommander.llm;

import com.aicommander.config.ModConfig;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * HTTP client for communicating with Ollama API.
 */
public class LLMClient {
    private static final Gson GSON = new Gson();
    private static final Pattern JSON_PATTERN = Pattern.compile("\\{[\\s\\S]*\\}");
    
    private final HttpClient httpClient;
    private final ModConfig config;
    
    public LLMClient() {
        this.config = ModConfig.getInstance();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }
    
    /**
     * Check if Ollama is running and accessible.
     */
    public boolean isOllamaAvailable() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(config.getOllamaUrl() + "/api/tags"))
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();
            
            HttpResponse<String> response = httpClient.send(request, 
                    HttpResponse.BodyHandlers.ofString());
            
            return response.statusCode() == 200;
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * Send a prompt to Ollama and get a response.
     */
    public CompletableFuture<LLMResponse> sendPrompt(String prompt) {
        return CompletableFuture.supplyAsync(() -> {
            int retries = 0;
            Exception lastException = null;
            
            while (retries <= config.ollamaMaxRetries) {
                try {
                    return sendPromptInternal(prompt);
                } catch (Exception e) {
                    lastException = e;
                    retries++;
                    if (retries <= config.ollamaMaxRetries) {
                        try {
                            Thread.sleep(1000 * retries); // Exponential backoff
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }
                }
            }
            
            throw new RuntimeException("Failed after " + config.ollamaMaxRetries + 
                    " retries: " + (lastException != null ? lastException.getMessage() : "Unknown error"));
        });
    }
    
    private LLMResponse sendPromptInternal(String prompt) throws IOException, InterruptedException {
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("model", config.ollamaModel);
        requestBody.addProperty("prompt", prompt);
        requestBody.addProperty("stream", false);
        
        // Request JSON format
        JsonObject options = new JsonObject();
        options.addProperty("temperature", 0.3); // Lower temperature for more deterministic output
        requestBody.add("options", options);
        
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(config.getOllamaUrl() + "/api/generate"))
                .timeout(Duration.ofSeconds(config.ollamaTimeoutSeconds))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(requestBody)))
                .build();
        
        HttpResponse<String> response = httpClient.send(request, 
                HttpResponse.BodyHandlers.ofString());
        
        if (response.statusCode() != 200) {
            throw new IOException("Ollama returned status " + response.statusCode() + 
                    ": " + response.body());
        }
        
        // Parse Ollama response
        JsonObject ollamaResponse = JsonParser.parseString(response.body()).getAsJsonObject();
        String llmOutput = ollamaResponse.get("response").getAsString();
        
        // Extract JSON from the response (LLM might include extra text)
        String jsonStr = extractJson(llmOutput);
        
        if (jsonStr == null) {
            throw new IOException("No valid JSON found in LLM response: " + llmOutput);
        }
        
        try {
            LLMResponse llmResponse = GSON.fromJson(jsonStr, LLMResponse.class);
            if (llmResponse == null || !llmResponse.isValid()) {
                throw new IOException("Invalid LLM response structure");
            }
            return llmResponse;
        } catch (JsonSyntaxException e) {
            throw new IOException("Failed to parse LLM response JSON: " + e.getMessage());
        }
    }
    
    /**
     * Extract JSON object from a string that might contain extra text.
     */
    private String extractJson(String text) {
        // Try to find JSON object in the text
        Matcher matcher = JSON_PATTERN.matcher(text);
        
        // Find the largest valid JSON object
        String bestMatch = null;
        int bestLength = 0;
        
        while (matcher.find()) {
            String candidate = matcher.group();
            try {
                // Verify it's valid JSON
                JsonParser.parseString(candidate);
                if (candidate.length() > bestLength) {
                    bestMatch = candidate;
                    bestLength = candidate.length();
                }
            } catch (JsonSyntaxException e) {
                // Not valid JSON, try to balance braces
                String balanced = balanceBraces(text.substring(matcher.start()));
                if (balanced != null) {
                    try {
                        JsonParser.parseString(balanced);
                        if (balanced.length() > bestLength) {
                            bestMatch = balanced;
                            bestLength = balanced.length();
                        }
                    } catch (JsonSyntaxException e2) {
                        // Still not valid
                    }
                }
            }
        }
        
        return bestMatch;
    }
    
    /**
     * Try to balance braces in a JSON string.
     */
    private String balanceBraces(String text) {
        int braceCount = 0;
        int startIndex = text.indexOf('{');
        
        if (startIndex == -1) return null;
        
        for (int i = startIndex; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '{') braceCount++;
            else if (c == '}') braceCount--;
            
            if (braceCount == 0) {
                return text.substring(startIndex, i + 1);
            }
        }
        
        return null;
    }
    
    /**
     * Get the model name being used.
     */
    public String getModelName() {
        return config.ollamaModel;
    }
}
