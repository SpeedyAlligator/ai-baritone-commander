package com.aicommander.llm;

import com.aicommander.config.ModConfig;
import com.aicommander.util.ChatUtils;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Centralized HTTP client for all Ollama API operations.
 * Handles model listing, generation, and pulling with progress.
 */
public class OllamaClient {

    private static final Gson GSON = new Gson();

    private final HttpClient httpClient;
    private final ModConfig config;

    // Track active pull operations
    private final AtomicBoolean isPulling = new AtomicBoolean(false);
    private String currentlyPulling = null;

    public OllamaClient() {
        this.config = ModConfig.getInstance();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    /**
     * Check if Ollama is running and accessible.
     */
    public boolean isAvailable() {
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
     * Get list of installed models.
     */
    public List<String> getInstalledModels() {
        List<String> models = new ArrayList<>();

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(config.getOllamaUrl() + "/api/tags"))
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
                JsonArray modelsArray = json.getAsJsonArray("models");

                if (modelsArray != null) {
                    for (int i = 0; i < modelsArray.size(); i++) {
                        JsonObject model = modelsArray.get(i).getAsJsonObject();
                        String name = model.get("name").getAsString();
                        models.add(name);
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("[AICommander] Failed to get models: " + e.getMessage());
        }

        return models;
    }

    /**
     * Check if a specific model is installed.
     */
    public boolean isModelInstalled(String modelName) {
        List<String> installed = getInstalledModels();

        // Check exact match or base name match
        for (String model : installed) {
            if (model.equals(modelName) ||
                    model.startsWith(modelName + ":") ||
                    modelName.equals(model.split(":")[0])) {
                return true;
            }
        }

        return false;
    }

    /**
     * Generate a response from a model.
     *
     * @param model       Model name
     * @param prompt      The prompt
     * @param temperature Temperature (0.0-1.0)
     * @param timeoutSecs Timeout in seconds
     * @return The generated text
     */
    public String generate(String model, String prompt, float temperature, int timeoutSecs)
            throws Exception {
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("model", model);
        requestBody.addProperty("prompt", prompt);
        requestBody.addProperty("stream", false);

        JsonObject options = new JsonObject();
        options.addProperty("temperature", temperature);
        requestBody.add("options", options);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(config.getOllamaUrl() + "/api/generate"))
                .timeout(Duration.ofSeconds(timeoutSecs))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(requestBody)))
                .build();

        HttpResponse<String> response = httpClient.send(request,
                HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new Exception("Ollama returned status " + response.statusCode() +
                    ": " + response.body());
        }

        JsonObject ollamaResponse = JsonParser.parseString(response.body()).getAsJsonObject();
        return ollamaResponse.get("response").getAsString();
    }

    /**
     * Pull a model with progress updates.
     *
     * @param modelName       Model to pull
     * @param progressHandler Called with progress updates (0-100)
     * @return CompletableFuture that completes when pull is done
     */
    public CompletableFuture<Boolean> pullModel(String modelName, Consumer<PullProgress> progressHandler) {
        if (isPulling.get()) {
            ChatUtils.sendWarning("Already pulling model: " + currentlyPulling);
            return CompletableFuture.completedFuture(false);
        }

        return CompletableFuture.supplyAsync(() -> {
            isPulling.set(true);
            currentlyPulling = modelName;

            try {
                JsonObject requestBody = new JsonObject();
                requestBody.addProperty("name", modelName);
                requestBody.addProperty("stream", true);

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(config.getOllamaUrl() + "/api/pull"))
                        .timeout(Duration.ofMinutes(30)) // Long timeout for large models
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(requestBody)))
                        .build();

                // Use streaming response
                HttpResponse<java.io.InputStream> response = httpClient.send(request,
                        HttpResponse.BodyHandlers.ofInputStream());

                if (response.statusCode() != 200) {
                    throw new Exception("Pull failed with status " + response.statusCode());
                }

                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(response.body()));

                String line;
                int lastPercent = -1;
                long lastUpdate = System.currentTimeMillis();

                while ((line = reader.readLine()) != null) {
                    try {
                        JsonObject progress = JsonParser.parseString(line).getAsJsonObject();

                        String status = progress.has("status") ?
                                progress.get("status").getAsString() : "";

                        // Calculate percentage if available
                        int percent = -1;
                        if (progress.has("completed") && progress.has("total")) {
                            long completed = progress.get("completed").getAsLong();
                            long total = progress.get("total").getAsLong();
                            if (total > 0) {
                                percent = (int) ((completed * 100) / total);
                            }
                        }

                        // Rate-limit updates (every 2 seconds or 5% change)
                        long now = System.currentTimeMillis();
                        if (progressHandler != null &&
                                (now - lastUpdate > 2000 ||
                                        (percent >= 0 && percent - lastPercent >= 5))) {

                            progressHandler.accept(new PullProgress(
                                    modelName, status, percent,
                                    progress.has("completed") ? progress.get("completed").getAsLong() : 0,
                                    progress.has("total") ? progress.get("total").getAsLong() : 0
                            ));

                            lastUpdate = now;
                            if (percent >= 0) lastPercent = percent;
                        }

                        // Check for completion or error
                        if (status.equals("success")) {
                            if (progressHandler != null) {
                                progressHandler.accept(new PullProgress(
                                        modelName, "success", 100, 0, 0));
                            }
                            return true;
                        }

                        if (progress.has("error")) {
                            throw new Exception(progress.get("error").getAsString());
                        }

                    } catch (Exception e) {
                        if (e.getMessage() != null && e.getMessage().contains("error")) {
                            throw e;
                        }
                        // Ignore parse errors for incomplete lines
                    }
                }

                return true;

            } catch (Exception e) {
                System.err.println("[AICommander] Pull failed: " + e.getMessage());
                if (progressHandler != null) {
                    progressHandler.accept(new PullProgress(
                            modelName, "error: " + e.getMessage(), -1, 0, 0));
                }
                return false;
            } finally {
                isPulling.set(false);
                currentlyPulling = null;
            }
        });
    }

    /**
     * Check if currently pulling a model.
     */
    public boolean isPulling() {
        return isPulling.get();
    }

    /**
     * Get the name of the model currently being pulled.
     */
    public String getCurrentlyPulling() {
        return currentlyPulling;
    }

    /**
     * Progress information for model pulling.
     */
    public static class PullProgress {
        public final String modelName;
        public final String status;
        public final int percent; // -1 if unknown
        public final long completed;
        public final long total;

        public PullProgress(String modelName, String status, int percent,
                            long completed, long total) {
            this.modelName = modelName;
            this.status = status;
            this.percent = percent;
            this.completed = completed;
            this.total = total;
        }

        public String formatProgress() {
            if (percent >= 0) {
                return percent + "%";
            } else if (total > 0) {
                return formatBytes(completed) + " / " + formatBytes(total);
            } else {
                return status;
            }
        }

        private String formatBytes(long bytes) {
            if (bytes < 1024) return bytes + " B";
            if (bytes < 1024 * 1024) return (bytes / 1024) + " KB";
            if (bytes < 1024 * 1024 * 1024) return (bytes / (1024 * 1024)) + " MB";
            return String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024));
        }

        public boolean isComplete() {
            return "success".equals(status) || percent == 100;
        }

        public boolean isError() {
            return status != null && status.startsWith("error");
        }
    }
}
