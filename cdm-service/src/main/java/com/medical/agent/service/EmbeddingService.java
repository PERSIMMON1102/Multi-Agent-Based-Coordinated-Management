package com.medical.agent.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Service
public class EmbeddingService {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingService.class);
    private static final MediaType JSON_TYPE = MediaType.get("application/json; charset=utf-8");

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final OkHttpClient httpClient;
    private volatile String lastError;

    @Value("${embedding.api.enabled:false}")
    private boolean enabled;

    @Value("${embedding.api.base-url:${llm.api.base-url:https://api.openai.com/v1}}")
    private String embeddingBaseUrl;

    @Value("${embedding.api.key:${llm.api.key:}}")
    private String embeddingApiKey;

    @Value("${embedding.api.model:text-embedding-3-small}")
    private String model;

    @Value("${embedding.dimension:1536}")
    private Integer dimension;

    @Value("${llm.api.base-url:https://api.openai.com/v1}")
    private String llmBaseUrl;

    @Value("${llm.api.key:}")
    private String llmApiKey;

    public EmbeddingService() {
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
    }

    public boolean isAvailable() {
        return enabled && getEffectiveApiKey() != null;
    }

    public String getModel() {
        return model;
    }

    public java.util.Map<String, Object> getStatus() {
        java.util.Map<String, Object> status = new java.util.LinkedHashMap<>();
        status.put("enabled", enabled);
        status.put("hasApiKey", getEffectiveApiKey() != null);
        status.put("available", isAvailable());
        status.put("baseUrl", getEffectiveBaseUrl());
        status.put("model", model);
        status.put("dimension", dimension);
        status.put("lastError", lastError);
        return status;
    }

    public Optional<List<Double>> embed(String text) {
        if (!isAvailable() || text == null || text.isBlank()) {
            return Optional.empty();
        }

        try {
            String effectiveBaseUrl = getEffectiveBaseUrl();
            String effectiveApiKey = getEffectiveApiKey();
            Map<String, Object> requestBody = buildRequestBody(text);
            String json = objectMapper.writeValueAsString(requestBody);
            String url = buildRequestUrl(effectiveBaseUrl);

            Request request = new Request.Builder()
                    .url(url)
                    .addHeader("Authorization", "Bearer " + effectiveApiKey)
                    .addHeader("Content-Type", "application/json")
                    .post(RequestBody.create(json, JSON_TYPE))
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    String body = response.body() != null ? response.body().string() : "no body";
                    lastError = "embedding request failed: status=" + response.code() + ", body=" + body;
                    log.warn("[EmbeddingService] embedding request failed: status={}, body={}", response.code(), body);
                    return Optional.empty();
                }

                String responseBody = response.body() != null ? response.body().string() : "";
                JsonNode embeddingNode = extractEmbeddingNode(objectMapper.readTree(responseBody));
                if (embeddingNode == null || !embeddingNode.isArray() || embeddingNode.isEmpty()) {
                    return Optional.empty();
                }

                List<Double> vector = new ArrayList<>(embeddingNode.size());
                for (JsonNode item : embeddingNode) {
                    vector.add(item.asDouble());
                }
                lastError = null;
                return Optional.of(vector);
            }
        } catch (IOException e) {
            lastError = "embedding request error: " + e.getMessage();
            log.warn("[EmbeddingService] embedding request error: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private String getEffectiveBaseUrl() {
        String resolved = firstNonBlank(embeddingBaseUrl, llmBaseUrl);
        return resolved != null ? resolved : "https://api.openai.com/v1";
    }

    private String getEffectiveApiKey() {
        return firstNonBlank(embeddingApiKey, llmApiKey);
    }

    private String firstNonBlank(String primary, String fallback) {
        if (primary != null && !primary.isBlank()) {
            return primary;
        }
        if (fallback != null && !fallback.isBlank()) {
            return fallback;
        }
        return null;
    }

    private boolean shouldSendDimensions() {
        if (model == null || model.isBlank()) {
            return false;
        }
        String normalized = model.trim().toLowerCase();
        return normalized.startsWith("text-embedding-v3")
                || normalized.startsWith("text-embedding-v4")
                || normalized.startsWith("text-embedding-3-small")
                || normalized.startsWith("text-embedding-3-large");
    }

    private Map<String, Object> buildRequestBody(String text) {
        if (isDashScopeMultimodalModel()) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", model);

            Map<String, Object> content = new LinkedHashMap<>();
            content.put("text", text);
            Map<String, Object> input = new LinkedHashMap<>();
            input.put("contents", List.of(content));
            body.put("input", input);

            if (dimension != null && dimension > 0) {
                Map<String, Object> parameters = new LinkedHashMap<>();
                parameters.put("dimension", dimension);
                body.put("parameters", parameters);
            }
            return body;
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("input", text);
        if (shouldSendDimensions() && dimension != null && dimension > 0) {
            body.put("dimensions", dimension);
        }
        return body;
    }

    private String buildRequestUrl(String baseUrl) {
        if (isDashScopeMultimodalModel()) {
            return normalizeDashScopeBase(baseUrl, "api/v1") + "/services/embeddings/multimodal-embedding/multimodal-embedding";
        }
        return baseUrl.endsWith("/") ? baseUrl + "embeddings" : baseUrl + "/embeddings";
    }

    private JsonNode extractEmbeddingNode(JsonNode root) {
        JsonNode dataEmbedding = root.path("data");
        if (dataEmbedding.isArray() && !dataEmbedding.isEmpty()) {
            JsonNode embedding = dataEmbedding.get(0).path("embedding");
            if (embedding.isArray() && !embedding.isEmpty()) {
                return embedding;
            }
        }

        JsonNode outputEmbeddings = root.path("output").path("embeddings");
        if (outputEmbeddings.isArray() && !outputEmbeddings.isEmpty()) {
            JsonNode first = outputEmbeddings.get(0);
            if (first.isArray() && !first.isEmpty()) {
                return first;
            }
            JsonNode embedding = first.path("embedding");
            if (embedding.isArray() && !embedding.isEmpty()) {
                return embedding;
            }
        }
        return null;
    }

    private boolean isDashScopeMultimodalModel() {
        if (model == null || model.isBlank()) {
            return false;
        }
        String normalized = model.trim().toLowerCase();
        return normalized.startsWith("qwen3-vl-embedding")
                || normalized.startsWith("multimodal-embedding-v1")
                || normalized.startsWith("tongyi-embedding-vision-plus");
    }

    private String normalizeDashScopeBase(String baseUrl, String targetPath) {
        String sanitized = baseUrl != null ? baseUrl.trim() : "";
        if (sanitized.isBlank()) {
            sanitized = "https://dashscope.aliyuncs.com/" + targetPath;
        }
        sanitized = sanitized.replaceAll("/+$", "");

        if (sanitized.contains("dashscope.aliyuncs.com")) {
            sanitized = sanitized
                    .replace("/compatible-mode/v1", "/" + targetPath)
                    .replace("/compatible-api/v1", "/" + targetPath)
                    .replace("/api/v1", "/" + targetPath);
        }

        if (!sanitized.endsWith("/" + targetPath)) {
            sanitized = sanitized + "/" + targetPath;
        }
        return sanitized.replaceAll("/+$", "");
    }
}
