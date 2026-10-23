package com.medical.agent.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.medical.agent.common.dto.RagEvidenceDTO;
import com.medical.agent.common.util.DiseaseAliasUtil;
import com.medical.agent.model.entity.KnowledgeChunk;
import com.medical.agent.model.entity.VideoResource;
import com.medical.agent.model.entity.VideoTranscriptChunk;
import com.medical.agent.service.cache.CacheNames;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Service
public class VectorStoreService {

    private static final Logger log = LoggerFactory.getLogger(VectorStoreService.class);
    private static final MediaType JSON_TYPE = MediaType.get("application/json; charset=utf-8");
    private static final String SOURCE_KNOWLEDGE = "KNOWLEDGE";
    private static final String SOURCE_VIDEO_TRANSCRIPT = "VIDEO_TRANSCRIPT";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final OkHttpClient httpClient;
    private final EmbeddingService embeddingService;
    private final RerankService rerankService;
    private final Set<String> ensuredCollections = ConcurrentHashMap.newKeySet();
    private volatile String lastError;

    @Value("${vector.enabled:false}")
    private boolean enabled;

    @Value("${vector.qdrant.url:http://localhost:6333}")
    private String qdrantUrl;

    @Value("${vector.qdrant.api-key:}")
    private String apiKey;

    @Value("${vector.qdrant.knowledge-collection:knowledge_chunks}")
    private String knowledgeCollection;

    @Value("${vector.qdrant.video-collection:video_transcripts}")
    private String videoCollection;

    @Value("${embedding.dimension:1536}")
    private int embeddingDimension;

    public VectorStoreService(EmbeddingService embeddingService,
                              RerankService rerankService) {
        this.embeddingService = embeddingService;
        this.rerankService = rerankService;
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
    }

    public boolean isAvailable() {
        return enabled && embeddingService.isAvailable() && qdrantUrl != null && !qdrantUrl.isBlank();
    }

    @Cacheable(cacheNames = CacheNames.VECTOR_STATUS, key = "'default'", sync = true)
    public Map<String, Object> getStatus() {
        Map<String, Object> status = new LinkedHashMap<>();
        Map<String, Object> embeddingStatus = embeddingService.getStatus();
        boolean qdrantReachable = probeQdrant();
        List<String> collections = listCollections();
        status.put("enabled", enabled);
        status.put("available", isAvailable());
        status.put("qdrantUrl", qdrantUrl);
        status.put("embedding", embeddingStatus);
        status.put("rerank", rerankService.getStatus());
        status.put("qdrantReachable", qdrantReachable);
        status.put("collections", collections);
        status.put("lastError", lastError);
        status.put("diagnosis", buildDiagnosis(embeddingStatus, qdrantReachable));
        status.put("recommendation", buildRecommendation(embeddingStatus, qdrantReachable, collections));
        return status;
    }

    public String upsertKnowledgeChunk(KnowledgeChunk chunk) {
        if (!isAvailable() || chunk == null || chunk.getId() == null || chunk.getContent() == null || chunk.getContent().isBlank()) {
            return null;
        }
        return embeddingService.embed(chunk.getContent())
                .map(vector -> {
                    String pointId = stablePointId("knowledge", chunk.getId());
                    Map<String, Object> payload = new LinkedHashMap<>();
                    payload.put("sourceType", SOURCE_KNOWLEDGE);
                    payload.put("refId", chunk.getId());
                    payload.put("diseaseKey", DiseaseAliasUtil.normalizeKey(chunk.getDisease()));
                    payload.put("title", chunk.getTitle());
                    payload.put("section", chunk.getSection());
                    payload.put("source", chunk.getSource());
                    payload.put("contentTags", chunk.getContentTags());
                    payload.put("publishedAt", chunk.getPublishedAt() != null ? chunk.getPublishedAt().toString() : null);
                    payload.put("contentSnippet", toSnippet(chunk.getContent(), 240));
                    return upsertPoint(knowledgeCollection, pointId, vector, payload) ? pointId : null;
                })
                .orElse(null);
    }

    public String upsertVideoTranscriptChunk(VideoTranscriptChunk chunk, VideoResource video) {
        if (!isAvailable() || chunk == null || chunk.getId() == null || video == null || chunk.getContent() == null || chunk.getContent().isBlank()) {
            return null;
        }
        return embeddingService.embed(chunk.getContent())
                .map(vector -> {
                    String pointId = stablePointId("video-transcript", chunk.getId());
                    Map<String, Object> payload = new LinkedHashMap<>();
                    payload.put("sourceType", SOURCE_VIDEO_TRANSCRIPT);
                    payload.put("refId", chunk.getId());
                    payload.put("videoId", video.getId());
                    payload.put("diseaseKey", DiseaseAliasUtil.normalizeKey(video.getDiseaseType()));
                    payload.put("title", video.getTitle());
                    payload.put("section", "讲稿片段-" + chunk.getChunkIndex());
                    payload.put("source", chunk.getSourceType());
                    payload.put("contentTags", video.getContentTags());
                    payload.put("publishedAt", video.getCreatedAt() != null ? video.getCreatedAt().toLocalDate().toString() : LocalDate.now().toString());
                    payload.put("contentSnippet", toSnippet(chunk.getContent(), 240));
                    return upsertPoint(videoCollection, pointId, vector, payload) ? pointId : null;
                })
                .orElse(null);
    }

    public void deleteKnowledgePoint(String pointId) {
        deletePoint(knowledgeCollection, pointId);
    }

    public void deleteVideoTranscriptPoint(String pointId) {
        deletePoint(videoCollection, pointId);
    }

    public List<RagEvidenceDTO> searchKnowledge(String queryText, Collection<String> diseaseKeys, int limit) {
        return search(knowledgeCollection, queryText, diseaseKeys, limit, null);
    }

    public List<RagEvidenceDTO> searchVideoTranscripts(String queryText, Collection<String> diseaseKeys, int limit) {
        return search(videoCollection, queryText, diseaseKeys, limit, SOURCE_VIDEO_TRANSCRIPT);
    }

    private List<RagEvidenceDTO> search(String collection,
                                        String queryText,
                                        Collection<String> diseaseKeys,
                                        int limit,
                                        String expectedSourceType) {
        if (!isAvailable() || queryText == null || queryText.isBlank()) {
            return List.of();
        }
        return embeddingService.embed(queryText)
                .map(vector -> doSearch(collection, vector, diseaseKeys, limit, expectedSourceType))
                .orElse(List.of());
    }

    private List<RagEvidenceDTO> doSearch(String collection,
                                          List<Double> vector,
                                          Collection<String> diseaseKeys,
                                          int limit,
                                          String expectedSourceType) {
        ensureCollection(collection);
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("vector", vector);
            body.put("limit", Math.max(1, Math.min(limit, 40)));
            body.put("with_payload", true);
            Map<String, Object> filter = buildDiseaseFilter(diseaseKeys);
            if (!filter.isEmpty()) {
                body.put("filter", filter);
            }

            Request request = new Request.Builder()
                    .url(normalizeBaseUrl() + "/collections/" + collection + "/points/search")
                    .headers(baseHeaders())
                    .post(RequestBody.create(objectMapper.writeValueAsString(body), JSON_TYPE))
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    String bodyText = response.body() != null ? response.body().string() : "no body";
                    lastError = "search failed: collection=" + collection + ", status=" + response.code();
                    log.warn("[VectorStoreService] search failed: collection={}, status={}, body={}", collection, response.code(), bodyText);
                    return List.of();
                }

                JsonNode root = objectMapper.readTree(response.body() != null ? response.body().string() : "");
                JsonNode result = root.path("result");
                if (!result.isArray()) {
                    return List.of();
                }

                List<RagEvidenceDTO> matches = new ArrayList<>();
                for (JsonNode item : result) {
                    JsonNode payload = item.path("payload");
                    String sourceType = payload.path("sourceType").asText(null);
                    if (expectedSourceType != null && sourceType != null && !expectedSourceType.equalsIgnoreCase(sourceType)) {
                        continue;
                    }
                    matches.add(RagEvidenceDTO.builder()
                            .sourceType(sourceType)
                            .refId(readLong(payload.path("refId")))
                            .videoId(readLong(payload.path("videoId")))
                            .diseaseKey(payload.path("diseaseKey").asText(null))
                            .title(payload.path("title").asText(null))
                            .section(payload.path("section").asText(null))
                            .source(payload.path("source").asText(null))
                            .publishedAt(payload.path("publishedAt").asText(null))
                            .contentSnippet(payload.path("contentSnippet").asText(null))
                            .score(item.path("score").isNumber() ? item.path("score").asDouble() : null)
                            .build());
                }
                lastError = null;
                return matches;
            }
        } catch (IOException e) {
            lastError = "search error: " + e.getMessage();
            log.warn("[VectorStoreService] search error: {}", e.getMessage());
            return List.of();
        }
    }

    private boolean upsertPoint(String collection, String pointId, List<Double> vector, Map<String, Object> payload) {
        if (!isAvailable()) {
            return false;
        }
        ensureCollection(collection);
        try {
            Map<String, Object> point = new LinkedHashMap<>();
            point.put("id", pointId);
            point.put("vector", vector);
            point.put("payload", payload);
            Map<String, Object> body = Map.of("points", List.of(point));

            Request request = new Request.Builder()
                    .url(normalizeBaseUrl() + "/collections/" + collection + "/points?wait=true")
                    .headers(baseHeaders())
                    .put(RequestBody.create(objectMapper.writeValueAsString(body), JSON_TYPE))
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    String bodyText = response.body() != null ? response.body().string() : "no body";
                    lastError = "upsert failed: collection=" + collection + ", status=" + response.code();
                    log.warn("[VectorStoreService] upsert failed: collection={}, status={}, body={}", collection, response.code(), bodyText);
                    return false;
                } else {
                    lastError = null;
                    return true;
                }
            }
        } catch (IOException e) {
            lastError = "upsert error: " + e.getMessage();
            log.warn("[VectorStoreService] upsert error: {}", e.getMessage());
            return false;
        }
    }

    private void deletePoint(String collection, String pointId) {
        if (!isAvailable() || pointId == null || pointId.isBlank()) {
            return;
        }
        try {
            Map<String, Object> body = Map.of("points", List.of(pointId));
            Request request = new Request.Builder()
                    .url(normalizeBaseUrl() + "/collections/" + collection + "/points/delete?wait=true")
                    .headers(baseHeaders())
                    .post(RequestBody.create(objectMapper.writeValueAsString(body), JSON_TYPE))
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    String bodyText = response.body() != null ? response.body().string() : "no body";
                    lastError = "delete point failed: collection=" + collection + ", status=" + response.code();
                    log.warn("[VectorStoreService] delete point failed: collection={}, status={}, body={}", collection, response.code(), bodyText);
                } else {
                    lastError = null;
                }
            }
        } catch (IOException e) {
            lastError = "delete point error: " + e.getMessage();
            log.warn("[VectorStoreService] delete point error: {}", e.getMessage());
        }
    }

    private Map<String, Object> buildDiseaseFilter(Collection<String> diseaseKeys) {
        List<String> normalized = DiseaseAliasUtil.normalizeKeys(diseaseKeys);
        if (normalized.isEmpty()) {
            return Map.of();
        }
        LinkedHashSet<String> filterValues = new LinkedHashSet<>(normalized);
        filterValues.add(DiseaseAliasUtil.GENERAL);

        List<Map<String, Object>> should = new ArrayList<>();
        for (String diseaseKey : filterValues) {
            should.add(Map.of("key", "diseaseKey", "match", Map.of("value", diseaseKey)));
        }
        return Map.of("should", should);
    }

    private String stablePointId(String prefix, Long refId) {
        return UUID.nameUUIDFromBytes((prefix + "-" + refId).getBytes()).toString();
    }

    private void ensureCollection(String collection) {
        if (!isAvailable() || ensuredCollections.contains(collection)) {
            return;
        }
        synchronized (ensuredCollections) {
            if (ensuredCollections.contains(collection)) {
                return;
            }

            Request probe = new Request.Builder()
                    .url(normalizeBaseUrl() + "/collections/" + collection)
                    .headers(baseHeaders())
                    .get()
                    .build();

            try (Response response = httpClient.newCall(probe).execute()) {
                if (response.isSuccessful()) {
                    ensuredCollections.add(collection);
                    return;
                }
            } catch (IOException ignored) {
            }

            try {
                Map<String, Object> body = Map.of("vectors", Map.of("size", embeddingDimension, "distance", "Cosine"));
                Request create = new Request.Builder()
                        .url(normalizeBaseUrl() + "/collections/" + collection)
                        .headers(baseHeaders())
                        .put(RequestBody.create(objectMapper.writeValueAsString(body), JSON_TYPE))
                        .build();
                try (Response response = httpClient.newCall(create).execute()) {
                    if (response.isSuccessful()) {
                        ensuredCollections.add(collection);
                        lastError = null;
                    } else {
                        String bodyText = response.body() != null ? response.body().string() : "no body";
                        lastError = "create collection failed: collection=" + collection + ", status=" + response.code();
                        log.warn("[VectorStoreService] create collection failed: collection={}, status={}, body={}", collection, response.code(), bodyText);
                    }
                }
            } catch (IOException e) {
                lastError = "create collection error: " + e.getMessage();
                log.warn("[VectorStoreService] create collection error: {}", e.getMessage());
            }
        }
    }

    private boolean probeQdrant() {
        if (qdrantUrl == null || qdrantUrl.isBlank()) {
            return false;
        }
        Request request = new Request.Builder()
                .url(normalizeBaseUrl() + "/collections")
                .headers(baseHeaders())
                .get()
                .build();
        try (Response response = httpClient.newCall(request).execute()) {
            return response.isSuccessful();
        } catch (IOException e) {
            return false;
        }
    }

    private List<String> listCollections() {
        if (qdrantUrl == null || qdrantUrl.isBlank()) {
            return List.of();
        }
        Request request = new Request.Builder()
                .url(normalizeBaseUrl() + "/collections")
                .headers(baseHeaders())
                .get()
                .build();
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                return List.of();
            }
            JsonNode root = objectMapper.readTree(response.body() != null ? response.body().string() : "");
            JsonNode collections = root.path("result").path("collections");
            if (!collections.isArray()) {
                return List.of();
            }
            List<String> result = new ArrayList<>();
            for (JsonNode item : collections) {
                String name = item.path("name").asText(null);
                if (name != null && !name.isBlank()) {
                    result.add(name);
                }
            }
            return result;
        } catch (IOException e) {
            return List.of();
        }
    }

    private String buildDiagnosis(Map<String, Object> embeddingStatus, boolean qdrantReachable) {
        boolean embeddingEnabled = Boolean.TRUE.equals(embeddingStatus.get("enabled"));
        boolean hasApiKey = Boolean.TRUE.equals(embeddingStatus.get("hasApiKey"));
        String embeddingError = embeddingStatus.get("lastError") != null ? embeddingStatus.get("lastError").toString() : null;

        if (!enabled) {
            return "向量检索未启用";
        }
        if (!embeddingEnabled) {
            return "Embedding 服务未启用";
        }
        if (!hasApiKey) {
            return "Embedding API 密钥未配置";
        }
        if (!qdrantReachable) {
            return "Qdrant 不可连接";
        }
        if (embeddingError != null && embeddingError.contains("ModelNotAllowed")) {
            return "当前 embedding 模型无访问权限";
        }
        if (embeddingError != null && !embeddingError.isBlank()) {
            return "Embedding 调用失败";
        }
        return "向量服务可用";
    }

    private String buildRecommendation(Map<String, Object> embeddingStatus,
                                       boolean qdrantReachable,
                                       List<String> collections) {
        boolean embeddingEnabled = Boolean.TRUE.equals(embeddingStatus.get("enabled"));
        boolean hasApiKey = Boolean.TRUE.equals(embeddingStatus.get("hasApiKey"));
        String embeddingError = embeddingStatus.get("lastError") != null ? embeddingStatus.get("lastError").toString() : null;

        if (!enabled) {
            return "将 VECTOR_ENABLED 设为 true 后重启后端容器。";
        }
        if (!embeddingEnabled) {
            return "将 EMBEDDING_API_ENABLED 设为 true 后重启后端容器。";
        }
        if (!hasApiKey) {
            return "补充 EMBEDDING_API_KEY 或让系统回退使用可用的 LLM_API_KEY。";
        }
        if (!qdrantReachable) {
            return "检查 Qdrant 容器状态和 QDRANT_URL 配置。";
        }
        if (embeddingError != null && embeddingError.contains("ModelNotAllowed")) {
            return "为当前 key 开通 embedding 模型权限，或改用网关允许访问的 embedding 模型/密钥。";
        }
        if (embeddingError != null && !embeddingError.isBlank()) {
            return "先解决最近一次 embedding 调用错误，再重新索引知识库或重新保存视频讲稿。";
        }
        if (collections == null || collections.isEmpty()) {
            return "向量服务正常，下一步可上传知识文档或保存视频讲稿以创建 collection。";
        }
        return "向量服务正常，可继续上传资料并按需执行重新索引。";
    }

    private okhttp3.Headers baseHeaders() {
        okhttp3.Headers.Builder builder = new okhttp3.Headers.Builder()
                .add("Content-Type", "application/json");
        if (apiKey != null && !apiKey.isBlank()) {
            builder.add("api-key", apiKey);
        }
        return builder.build();
    }

    private String normalizeBaseUrl() {
        return qdrantUrl.endsWith("/") ? qdrantUrl.substring(0, qdrantUrl.length() - 1) : qdrantUrl;
    }

    private Long readLong(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        if (node.isIntegralNumber()) {
            return node.asLong();
        }
        try {
            return Long.valueOf(node.asText());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String toSnippet(String text, int maxLength) {
        if (text == null) {
            return null;
        }
        String normalized = text.replaceAll("\\s+", " ").trim();
        return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength) + "...";
    }
}
