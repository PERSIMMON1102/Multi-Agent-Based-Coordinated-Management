package com.medical.agent.service;

import com.medical.agent.common.util.DiseaseAliasUtil;
import com.medical.agent.common.dto.RagEvidenceDTO;
import com.medical.agent.common.dto.RagQueryContextDTO;
import com.medical.agent.common.exception.BusinessException;
import com.medical.agent.model.entity.KnowledgeChunk;
import com.medical.agent.model.repository.KnowledgeChunkRepository;
import com.medical.agent.service.cache.CacheNames;
import com.medical.agent.service.security.UploadSecurityService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

@Service
public class KnowledgeService {

    private static final int MAX_CHUNK_LENGTH = 1200;

    private final KnowledgeChunkRepository knowledgeChunkRepository;
    private final VectorStoreService vectorStoreService;
    private final EmbeddingService embeddingService;
    private final UploadSecurityService uploadSecurityService;
    private final RagRetrievalService ragRetrievalService;

    @Autowired
    public KnowledgeService(KnowledgeChunkRepository knowledgeChunkRepository,
                            VectorStoreService vectorStoreService,
                            EmbeddingService embeddingService,
                            UploadSecurityService uploadSecurityService,
                            RagRetrievalService ragRetrievalService) {
        this.knowledgeChunkRepository = knowledgeChunkRepository;
        this.vectorStoreService = vectorStoreService;
        this.embeddingService = embeddingService;
        this.uploadSecurityService = uploadSecurityService;
        this.ragRetrievalService = ragRetrievalService;
    }

    public KnowledgeService(KnowledgeChunkRepository knowledgeChunkRepository,
                            VectorStoreService vectorStoreService,
                            EmbeddingService embeddingService) {
        this(knowledgeChunkRepository, vectorStoreService, embeddingService, new UploadSecurityService(), null);
    }

    @Transactional
    @CacheEvict(cacheNames = CacheNames.VECTOR_STATUS, allEntries = true)
    public Map<String, Object> upload(MultipartFile file,
                                      String disease,
                                      String title,
                                      String source,
                                      String publishedAt,
                                      String versionTag,
                                      String contentTags) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(400, "上传文件不能为空");
        }
        uploadSecurityService.validateKnowledgeTextFile(file);

        String diseaseValue = safeText(disease);
        if (diseaseValue == null || diseaseValue.isBlank()) {
            throw new BusinessException(400, "病种不能为空");
        }

        String finalTitle = defaultIfBlank(title, stripExtension(file.getOriginalFilename()));
        String finalSource = defaultIfBlank(source, file.getOriginalFilename());
        LocalDate publishedDate = parseDate(publishedAt);
        String normalizedVersionTag = safeText(versionTag);

        List<KnowledgeChunk> existingChunks = knowledgeChunkRepository
                .findByDiseaseAndTitleAndSourceAndVersionTagAndPublishedAtOrderByIdAsc(
                        diseaseValue,
                        finalTitle,
                        finalSource,
                        normalizedVersionTag,
                        publishedDate
                );
        if (!existingChunks.isEmpty()) {
            return buildDuplicateUploadResult(file, diseaseValue, finalTitle, finalSource, publishedDate, normalizedVersionTag, existingChunks);
        }

        String rawText = readUtf8Text(file);
        if (rawText.isBlank()) {
            throw new BusinessException(400, "文件内容为空或无法解析为文本");
        }

        String normalizedTags = normalizeTags(contentTags);
        List<KnowledgeChunk> chunks = buildChunks(rawText, diseaseValue, finalTitle, finalSource, publishedDate, normalizedVersionTag, normalizedTags);
        chunks = knowledgeChunkRepository.saveAll(chunks);
        int indexedChunkCount = indexChunks(chunks);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("message", "知识库上传成功");
        result.put("fileName", file.getOriginalFilename());
        result.put("title", finalTitle);
        result.put("disease", diseaseValue);
        result.put("source", finalSource);
        result.put("publishedAt", publishedDate != null ? publishedDate.toString() : null);
        result.put("versionTag", safeText(versionTag));
        result.put("contentTags", toTagList(normalizedTags));
        result.put("chunkCount", chunks.size());
        result.put("indexStatus", resolveIndexStatus(chunks.size(), indexedChunkCount));
        result.put("indexedChunkCount", indexedChunkCount);
        return result;
    }

    public List<Map<String, Object>> search(String disease, String keyword, Integer limit) {
        int pageSize = limit == null ? 20 : Math.max(1, Math.min(limit, 50));
        List<KnowledgeChunk> chunks = knowledgeChunkRepository.search(
                        normalizeDiseaseForSearch(safeText(disease)),
                        safeText(keyword),
                        PageRequest.of(0, pageSize, Sort.by(Sort.Direction.DESC, "publishedAt", "createdAt", "id")))
                .getContent();

        List<Map<String, Object>> result = new ArrayList<>();
        for (KnowledgeChunk chunk : chunks) {
            result.add(toMap(chunk));
        }
        return result;
    }

    @Transactional
    @CacheEvict(cacheNames = CacheNames.VECTOR_STATUS, allEntries = true)
    public Map<String, Object> reindexAll() {
        List<KnowledgeChunk> chunks = knowledgeChunkRepository.findAll();
        int indexed = indexChunks(chunks);
        return Map.of(
                "message", "知识库索引任务已执行",
                "chunkCount", chunks.size(),
                "indexedChunkCount", indexed,
                "indexStatus", resolveIndexStatus(chunks.size(), indexed)
        );
    }

    public Map<String, Object> evaluateRagRegression(List<Map<String, Object>> cases) {
        if (ragRetrievalService == null) {
            throw new BusinessException(500, "RAG 检索服务未就绪");
        }
        if (cases == null || cases.isEmpty()) {
            throw new BusinessException(400, "评估样本不能为空");
        }

        List<Map<String, Object>> results = new ArrayList<>();
        int passCount = 0;
        int totalHits = 0;

        for (int index = 0; index < cases.size(); index++) {
            Map<String, Object> item = cases.get(index);
            String scenario = text(item.get("scenario"));
            String focus = text(item.get("focus"));
            List<String> diseaseKeys = normalizeList(item.get("diseaseKeys"));
            List<String> topicTags = normalizeList(item.get("topicTags"));
            List<String> expectedTerms = normalizeList(item.get("expectedTerms"));
            int limit = clampLimit(item.get("limit"));

            RagQueryContextDTO context = RagQueryContextDTO.builder()
                    .scenario(defaultText(scenario, "回归评估"))
                    .focus(focus)
                    .queryText(buildEvalQuery(defaultText(scenario, "回归评估"), focus, diseaseKeys, topicTags))
                    .diseaseKeys(diseaseKeys)
                    .topicTags(topicTags.isEmpty() ? List.of("general") : topicTags)
                    .audience(text(item.get("audience")) == null ? "doctor" : text(item.get("audience")))
                    .structuredContext(Map.of(
                            "expectedTerms", expectedTerms,
                            "caseIndex", index + 1
                    ))
                    .build();

            List<RagEvidenceDTO> evidences = ragRetrievalService.retrieveKnowledge(context, limit);
            int hitCount = countHits(evidences, expectedTerms);
            totalHits += hitCount;
            boolean passed = expectedTerms.isEmpty() || hitCount > 0;
            if (passed) {
                passCount++;
            }

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("caseId", defaultText(text(item.get("caseId")), "case-" + (index + 1)));
            row.put("scenario", defaultText(scenario, "回归评估"));
            row.put("focus", focus);
            row.put("diseaseKeys", diseaseKeys);
            row.put("topicTags", topicTags);
            row.put("expectedTerms", expectedTerms);
            row.put("hitCount", hitCount);
            row.put("passed", passed);
            row.put("topReferences", summarizeEvidence(evidences));
            results.add(row);
        }

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("caseCount", cases.size());
        summary.put("passedCount", passCount);
        summary.put("passRate", round2(cases.isEmpty() ? 0D : (passCount * 100D) / cases.size()));
        summary.put("avgHitCount", round2(cases.isEmpty() ? 0D : (totalHits * 1D) / cases.size()));
        summary.put("results", results);
        return summary;
    }

    private String readUtf8Text(MultipartFile file) {
        try {
            return new String(file.getBytes(), StandardCharsets.UTF_8)
                    .replace("\uFEFF", "")
                    .replace("\r\n", "\n")
                    .replace('\r', '\n')
                    .trim();
        } catch (IOException e) {
            throw new BusinessException(500, "读取文件失败: " + e.getMessage());
        }
    }

    private LocalDate parseDate(String publishedAt) {
        if (publishedAt == null || publishedAt.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(publishedAt.trim());
        } catch (DateTimeParseException e) {
            throw new BusinessException(400, "发布日期格式错误，应为 YYYY-MM-DD");
        }
    }

    private List<KnowledgeChunk> buildChunks(String rawText,
                                             String disease,
                                              String title,
                                             String source,
                                             LocalDate publishedAt,
                                             String versionTag,
                                             String contentTags) {
        List<KnowledgeChunk> chunks = new ArrayList<>();
        List<String> blocks = new ArrayList<>();

        for (String block : rawText.split("\\n\\s*\\n")) {
            String trimmed = block.trim();
            if (!trimmed.isEmpty()) {
                blocks.add(trimmed);
            }
        }

        if (blocks.isEmpty()) {
            blocks.add(rawText);
        }

        String currentSection = defaultIfBlank(title, "正文");
        int sectionIndex = 1;
        for (String block : blocks) {
            String content = block;
            String[] lines = block.split("\\n");
            String firstLine = lines.length > 0 ? lines[0].trim() : "";
            if (isHeading(firstLine)) {
                currentSection = cleanHeading(firstLine);
                content = String.join("\n", Arrays.copyOfRange(lines, 1, lines.length)).trim();
            }

            if (content.isBlank()) {
                continue;
            }

            List<String> pieces = splitLongText(content, MAX_CHUNK_LENGTH);
            for (String piece : pieces) {
                chunks.add(KnowledgeChunk.builder()
                        .disease(disease)
                        .title(title)
                        .section(defaultIfBlank(currentSection, "正文-" + sectionIndex))
                        .content(piece)
                        .source(source)
                        .publishedAt(publishedAt)
                        .versionTag(versionTag)
                        .contentTags(contentTags)
                        .build());
            }
            sectionIndex++;
        }

        if (chunks.isEmpty()) {
            throw new BusinessException(400, "未提取到可保存的知识内容");
        }
        return chunks;
    }

    private List<String> splitLongText(String text, int maxLength) {
        List<String> pieces = new ArrayList<>();
        String remaining = text.trim();
        while (remaining.length() > maxLength) {
            int splitAt = remaining.lastIndexOf('\n', maxLength);
            if (splitAt < maxLength / 2) {
                splitAt = remaining.lastIndexOf('。', maxLength);
            }
            if (splitAt < maxLength / 2) {
                splitAt = maxLength;
            }
            String piece = remaining.substring(0, splitAt).trim();
            if (!piece.isEmpty()) {
                pieces.add(piece);
            }
            remaining = remaining.substring(splitAt).trim();
        }
        if (!remaining.isEmpty()) {
            pieces.add(remaining);
        }
        return pieces;
    }

    private boolean isHeading(String line) {
        if (line == null || line.isBlank()) {
            return false;
        }
        String normalized = line.trim();
        return normalized.length() <= 40 &&
                (normalized.startsWith("#") ||
                 normalized.startsWith("第") ||
                 normalized.endsWith("：") ||
                 normalized.endsWith(":"));
    }

    private String cleanHeading(String line) {
        return line.replaceFirst("^#+\\s*", "").trim();
    }

    private Map<String, Object> toMap(KnowledgeChunk chunk) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", chunk.getId());
        map.put("disease", chunk.getDisease());
        map.put("title", chunk.getTitle());
        map.put("section", chunk.getSection());
        map.put("content", chunk.getContent());
        map.put("source", chunk.getSource());
        map.put("publishedAt", chunk.getPublishedAt() != null ? chunk.getPublishedAt().toString() : null);
        map.put("versionTag", chunk.getVersionTag());
        map.put("contentTags", toTagList(chunk.getContentTags()));
        map.put("embeddingStatus", chunk.getEmbeddingStatus());
        map.put("embeddingModel", chunk.getEmbeddingModel());
        map.put("indexedAt", chunk.getIndexedAt() != null ? chunk.getIndexedAt().toString() : null);
        map.put("createdAt", chunk.getCreatedAt() != null ? chunk.getCreatedAt().toString() : null);
        return map;
    }

    private int indexChunks(List<KnowledgeChunk> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return 0;
        }
        int indexed = 0;
        for (KnowledgeChunk chunk : chunks) {
            String pointId = vectorStoreService.upsertKnowledgeChunk(chunk);
            if (pointId != null) {
                chunk.setVectorPointId(pointId);
                chunk.setEmbeddingModel(embeddingService.getModel());
                chunk.setEmbeddingStatus("INDEXED");
                chunk.setIndexedAt(java.time.LocalDateTime.now());
                indexed++;
            } else if (vectorStoreService.isAvailable()) {
                chunk.setEmbeddingStatus("FAILED");
            } else {
                chunk.setEmbeddingStatus("DISABLED");
            }
        }
        knowledgeChunkRepository.saveAll(chunks);
        return indexed;
    }

    private Map<String, Object> buildDuplicateUploadResult(MultipartFile file,
                                                           String disease,
                                                           String title,
                                                           String source,
                                                           LocalDate publishedAt,
                                                           String versionTag,
                                                           List<KnowledgeChunk> existingChunks) {
        int indexedChunkCount = (int) existingChunks.stream()
                .filter(chunk -> "INDEXED".equalsIgnoreCase(chunk.getEmbeddingStatus()))
                .count();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("message", "知识文档已存在，跳过重复导入");
        result.put("skipped", true);
        result.put("fileName", file.getOriginalFilename());
        result.put("title", title);
        result.put("disease", disease);
        result.put("source", source);
        result.put("publishedAt", publishedAt != null ? publishedAt.toString() : null);
        result.put("versionTag", versionTag);
        result.put("contentTags", toTagList(existingChunks.get(0).getContentTags()));
        result.put("chunkCount", existingChunks.size());
        result.put("indexedChunkCount", indexedChunkCount);
        result.put("indexStatus", "SKIPPED");
        result.put("existingIndexStatus", resolveExistingIndexStatus(existingChunks, indexedChunkCount));
        return result;
    }

    private String resolveIndexStatus(int chunkCount, int indexedCount) {
        if (chunkCount <= 0) {
            return "EMPTY";
        }
        if (!vectorStoreService.isAvailable()) {
            return "DISABLED";
        }
        if (indexedCount <= 0) {
            return "FAILED";
        }
        if (indexedCount < chunkCount) {
            return "PARTIAL";
        }
        return "INDEXED";
    }

    private String resolveExistingIndexStatus(List<KnowledgeChunk> chunks, int indexedCount) {
        if (chunks == null || chunks.isEmpty()) {
            return "EMPTY";
        }
        if (indexedCount >= chunks.size()) {
            return "INDEXED";
        }
        if (indexedCount > 0) {
            return "PARTIAL";
        }
        if (chunks.stream().anyMatch(chunk -> "FAILED".equalsIgnoreCase(chunk.getEmbeddingStatus()))) {
            return "FAILED";
        }
        if (chunks.stream().anyMatch(chunk -> "DISABLED".equalsIgnoreCase(chunk.getEmbeddingStatus()))) {
            return "DISABLED";
        }
        return "PENDING";
    }

    private String safeText(String value) {
        return value == null ? null : value.trim();
    }

    private String normalizeDiseaseForSearch(String disease) {
        if (disease == null || disease.isBlank()) {
            return null;
        }
        return DiseaseAliasUtil.knowledgeLabel(disease);
    }

    private String defaultIfBlank(String preferred, String fallback) {
        if (preferred != null && !preferred.isBlank()) {
            return preferred.trim();
        }
        if (fallback != null && !fallback.isBlank()) {
            return fallback.trim();
        }
        return null;
    }

    private String stripExtension(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return "未命名文档";
        }
        int idx = fileName.lastIndexOf('.');
        return idx > 0 ? fileName.substring(0, idx) : fileName;
    }

    private String normalizeTags(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        LinkedHashSet<String> tags = new LinkedHashSet<>();
        for (String part : raw.split("[,，;；\\n]+")) {
            String tag = part == null ? null : part.trim();
            if (tag == null || tag.isBlank()) {
                continue;
            }
            String normalized = tag.length() > 24 ? tag.substring(0, 24) : tag;
            tags.add(normalized);
            if (tags.size() >= 12) {
                break;
            }
        }
        return tags.isEmpty() ? null : String.join(", ", tags);
    }

    private List<String> toTagList(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        List<String> tags = new ArrayList<>();
        for (String part : raw.split("[,，;；\\n]+")) {
            String tag = part == null ? null : part.trim();
            if (tag == null || tag.isBlank()) {
                continue;
            }
            tags.add(tag);
        }
        return tags;
    }

    private int countHits(List<RagEvidenceDTO> evidences, List<String> expectedTerms) {
        if (evidences == null || evidences.isEmpty() || expectedTerms == null || expectedTerms.isEmpty()) {
            return 0;
        }
        int hits = 0;
        for (String term : expectedTerms) {
            if (term == null || term.isBlank()) {
                continue;
            }
            String needle = term.toLowerCase(Locale.ROOT);
            boolean matched = evidences.stream().anyMatch(evidence -> {
                String searchable = joinText(
                        evidence.getTitle(),
                        evidence.getSection(),
                        evidence.getSource(),
                        evidence.getContentSnippet()
                );
                return searchable.contains(needle);
            });
            if (matched) {
                hits++;
            }
        }
        return hits;
    }

    private List<Map<String, Object>> summarizeEvidence(List<RagEvidenceDTO> evidences) {
        if (evidences == null || evidences.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> refs = new ArrayList<>();
        for (RagEvidenceDTO evidence : evidences.stream().limit(3).toList()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("title", evidence.getTitle());
            item.put("section", evidence.getSection());
            item.put("source", evidence.getSource());
            item.put("score", evidence.getScore());
            item.put("snippet", evidence.getContentSnippet());
            refs.add(item);
        }
        return refs;
    }

    private String buildEvalQuery(String scenario, String focus, List<String> diseaseKeys, List<String> topicTags) {
        StringBuilder builder = new StringBuilder();
        builder.append("场景：").append(scenario);
        if (focus != null && !focus.isBlank()) {
            builder.append("\n关注点：").append(focus.trim());
        }
        if (diseaseKeys != null && !diseaseKeys.isEmpty()) {
            builder.append("\n病种：").append(String.join("、", diseaseKeys));
        }
        if (topicTags != null && !topicTags.isEmpty()) {
            builder.append("\n主题：").append(String.join("、", topicTags));
        }
        return builder.toString();
    }

    private List<String> normalizeList(Object raw) {
        if (raw == null) {
            return List.of();
        }
        LinkedHashSet<String> values = new LinkedHashSet<>();
        if (raw instanceof Iterable<?> iterable) {
            for (Object item : iterable) {
                String value = text(item);
                if (value != null && !value.isBlank()) {
                    values.add(normalizeListValue(value));
                }
            }
        } else {
            String value = text(raw);
            if (value != null && !value.isBlank()) {
                for (String part : value.split("[,，;；\\n]+")) {
                    String normalized = text(part);
                    if (normalized != null && !normalized.isBlank()) {
                        values.add(normalizeListValue(normalized));
                    }
                }
            }
        }
        return new ArrayList<>(values);
    }

    private String normalizeListValue(String value) {
        String normalized = value.trim();
        String diseaseKey = DiseaseAliasUtil.normalizeKey(normalized);
        if (!Objects.equals(diseaseKey, DiseaseAliasUtil.GENERAL) || normalized.equalsIgnoreCase("GENERAL") || normalized.contains("通用")) {
            return diseaseKey;
        }
        return normalized;
    }

    private int clampLimit(Object raw) {
        String value = text(raw);
        if (value == null) {
            return 5;
        }
        try {
            return Math.max(1, Math.min(Integer.parseInt(value), 8));
        } catch (NumberFormatException ignored) {
            return 5;
        }
    }

    private String joinText(String... values) {
        StringBuilder builder = new StringBuilder();
        for (String value : values) {
            if (value == null || value.isBlank()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(value.toLowerCase(Locale.ROOT));
        }
        return builder.toString();
    }

    private String text(Object value) {
        if (value == null) {
            return null;
        }
        String result = value.toString().trim();
        return result.isBlank() ? null : result;
    }

    private String defaultText(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private double round2(double value) {
        return Math.round(value * 100D) / 100D;
    }
}
