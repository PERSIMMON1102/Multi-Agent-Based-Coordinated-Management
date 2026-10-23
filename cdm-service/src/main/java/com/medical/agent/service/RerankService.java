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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
public class RerankService {

    private static final Logger log = LoggerFactory.getLogger(RerankService.class);
    private static final MediaType JSON_TYPE = MediaType.get("application/json; charset=utf-8");

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final OkHttpClient httpClient;
    private volatile String lastError;

    @Value("${rerank.api.enabled:false}")
    private boolean enabled;

    @Value("${rerank.api.base-url:${embedding.api.base-url:${llm.api.base-url:https://dashscope.aliyuncs.com/compatible-api/v1}}}")
    private String rerankBaseUrl;

    @Value("${rerank.api.key:${embedding.api.key:${llm.api.key:}}}")
    private String rerankApiKey;

    @Value("${embedding.api.base-url:}")
    private String embeddingBaseUrl;

    @Value("${embedding.api.key:}")
    private String embeddingApiKey;

    @Value("${llm.api.base-url:https://dashscope.aliyuncs.com/compatible-api/v1}")
    private String llmBaseUrl;

    @Value("${llm.api.key:}")
    private String llmApiKey;

    @Value("${rerank.api.model:qwen3-rerank}")
    private String model;

    public RerankService() {
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
    }

    public boolean isAvailable() {
        return enabled && getEffectiveApiKey() != null;
    }

    public Map<String, Object> getStatus() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("enabled", enabled);
        status.put("available", isAvailable());
        status.put("hasApiKey", getEffectiveApiKey() != null);
        status.put("baseUrl", normalizeBaseUrl());
        status.put("model", model);
        status.put("lastError", lastError);
        return status;
    }

    public Map<Integer, Double> rerank(String query, List<String> documents) {
        if (!isAvailable() || query == null || query.isBlank() || documents == null || documents.isEmpty()) {
            return Map.of();
        }

        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", model);
            body.put("query", query);
            body.put("documents", documents);
            body.put("top_n", documents.size());
            body.put("return_documents", false);

            Request request = new Request.Builder()
                    .url(normalizeBaseUrl() + "/reranks")
                    .addHeader("Authorization", "Bearer " + getEffectiveApiKey())
                    .addHeader("Content-Type", "application/json")
                    .post(RequestBody.create(objectMapper.writeValueAsString(body), JSON_TYPE))
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    String bodyText = response.body() != null ? response.body().string() : "no body";
                    lastError = "rerank request failed: status=" + response.code() + ", body=" + bodyText;
                    log.warn("[RerankService] rerank request failed: status={}, body={}", response.code(), bodyText);
                    return Map.of();
                }

                String responseBody = response.body() != null ? response.body().string() : "";
                JsonNode results = extractResults(objectMapper.readTree(responseBody));
                if (results == null || !results.isArray()) {
                    return Map.of();
                }

                Map<Integer, Double> scores = new LinkedHashMap<>();
                for (JsonNode item : results) {
                    int index = item.path("index").asInt(-1);
                    if (index < 0) {
                        continue;
                    }
                    double score = item.hasNonNull("relevance_score")
                            ? item.path("relevance_score").asDouble()
                            : item.path("score").asDouble();
                    scores.put(index, score);
                }
                lastError = null;
                return scores;
            }
        } catch (IOException e) {
            lastError = "rerank request error: " + e.getMessage();
            log.warn("[RerankService] rerank request error: {}", e.getMessage());
            return Map.of();
        }
    }

    private JsonNode extractResults(JsonNode root) {
        JsonNode outputResults = root.path("output").path("results");
        if (outputResults.isArray()) {
            return outputResults;
        }
        JsonNode results = root.path("results");
        if (results.isArray()) {
            return results;
        }
        return null;
    }

    private String normalizeBaseUrl() {
        String sanitized = firstNonBlank(
                rerankBaseUrl,
                embeddingBaseUrl,
                llmBaseUrl,
                System.getenv("RERANK_API_BASE_URL"),
                System.getenv("EMBEDDING_API_BASE_URL"),
                System.getenv("LLM_API_BASE_URL")
        );
        if (sanitized == null || sanitized.isBlank()) {
            sanitized = "https://dashscope.aliyuncs.com/compatible-api/v1";
        }
        sanitized = sanitized.replaceAll("/+$", "");

        if (sanitized.contains("dashscope.aliyuncs.com")) {
            if (sanitized.endsWith("/compatible-mode/v1")) {
                sanitized = sanitized.substring(0, sanitized.length() - "/compatible-mode/v1".length()) + "/compatible-api/v1";
            } else if (sanitized.endsWith("/api/v1")) {
                sanitized = sanitized.substring(0, sanitized.length() - "/api/v1".length()) + "/compatible-api/v1";
            }
        }

        if (!sanitized.endsWith("/compatible-api/v1")) {
            sanitized = sanitized + "/compatible-api/v1";
        }
        return sanitized.replaceAll("/+$", "");
    }

    private String getEffectiveApiKey() {
        return firstNonBlank(
                rerankApiKey,
                embeddingApiKey,
                llmApiKey,
                System.getenv("RERANK_API_KEY"),
                System.getenv("EMBEDDING_API_KEY"),
                System.getenv("LLM_API_KEY")
        );
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }
}
