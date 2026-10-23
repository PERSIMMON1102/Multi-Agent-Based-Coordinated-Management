package com.medical.agent.service;

import com.medical.agent.common.exception.BusinessException;
import com.medical.agent.model.entity.KnowledgeChunk;
import com.medical.agent.model.entity.VideoResource;
import com.medical.agent.model.entity.VideoTranscriptChunk;
import com.medical.agent.model.repository.KnowledgeChunkRepository;
import com.medical.agent.model.repository.VideoResourceRepository;
import com.medical.agent.model.repository.VideoTranscriptChunkRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

@Service
public class ReferenceDetailService {

    private static final String SOURCE_KNOWLEDGE = "KNOWLEDGE";
    private static final String SOURCE_VIDEO_TRANSCRIPT = "VIDEO_TRANSCRIPT";

    private final KnowledgeChunkRepository knowledgeChunkRepository;
    private final VideoTranscriptChunkRepository transcriptChunkRepository;
    private final VideoResourceRepository videoResourceRepository;

    public ReferenceDetailService(KnowledgeChunkRepository knowledgeChunkRepository,
                                  VideoTranscriptChunkRepository transcriptChunkRepository,
                                  VideoResourceRepository videoResourceRepository) {
        this.knowledgeChunkRepository = knowledgeChunkRepository;
        this.transcriptChunkRepository = transcriptChunkRepository;
        this.videoResourceRepository = videoResourceRepository;
    }

    public Map<String, Object> getDetail(String sourceType, Long refId, Long videoId) {
        String normalizedType = normalizeSourceType(sourceType);
        return switch (normalizedType) {
            case SOURCE_KNOWLEDGE -> getKnowledgeDetail(refId);
            case SOURCE_VIDEO_TRANSCRIPT -> getVideoTranscriptDetail(refId, videoId);
            default -> throw new BusinessException(400, "暂不支持查看该引用类型详情: " + normalizedType);
        };
    }

    private Map<String, Object> getKnowledgeDetail(Long refId) {
        if (refId == null) {
            throw new BusinessException(400, "知识库引用缺少 refId");
        }
        KnowledgeChunk chunk = knowledgeChunkRepository.findById(refId)
                .orElseThrow(() -> new BusinessException(404, "知识库片段不存在: " + refId));

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("sourceType", SOURCE_KNOWLEDGE);
        detail.put("sourceLabel", "知识库");
        detail.put("refId", chunk.getId());
        detail.put("videoId", null);
        detail.put("title", defaultIfBlank(chunk.getTitle(), "知识片段"));
        detail.put("subtitle", chunk.getDisease());
        detail.put("section", chunk.getSection());
        detail.put("source", chunk.getSource());
        detail.put("publishedAt", chunk.getPublishedAt() != null ? chunk.getPublishedAt().toString() : null);
        detail.put("content", chunk.getContent());
        detail.put("disease", chunk.getDisease());
        detail.put("versionTag", chunk.getVersionTag());
        detail.put("contentTags", toTagList(chunk.getContentTags()));
        detail.put("createdAt", chunk.getCreatedAt() != null ? chunk.getCreatedAt().toString() : null);
        detail.put("navigation", buildKnowledgeNavigation(chunk));
        return detail;
    }

    private Map<String, Object> getVideoTranscriptDetail(Long refId, Long videoId) {
        if (refId == null) {
            throw new BusinessException(400, "视频讲稿引用缺少 refId");
        }
        VideoTranscriptChunk chunk = transcriptChunkRepository.findById(refId)
                .orElseThrow(() -> new BusinessException(404, "视频讲稿片段不存在: " + refId));

        Long resolvedVideoId = videoId != null ? videoId : chunk.getVideoId();
        VideoResource video = resolvedVideoId == null ? null : videoResourceRepository.findById(resolvedVideoId).orElse(null);

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("sourceType", SOURCE_VIDEO_TRANSCRIPT);
        detail.put("sourceLabel", "视频讲稿");
        detail.put("refId", chunk.getId());
        detail.put("videoId", resolvedVideoId);
        detail.put("title", video != null ? defaultIfBlank(video.getTitle(), "视频讲稿片段") : "视频讲稿片段");
        detail.put("subtitle", video != null ? video.getDiseaseType() : null);
        detail.put("section", "讲稿片段 #" + (chunk.getChunkIndex() + 1));
        detail.put("source", video != null ? video.getCategory() : null);
        detail.put("publishedAt", null);
        detail.put("content", chunk.getContent());
        detail.put("disease", video != null ? video.getDiseaseType() : null);
        detail.put("contentTags", video != null ? toTagList(video.getContentTags()) : List.of());
        detail.put("videoTitle", video != null ? video.getTitle() : null);
        detail.put("videoUrl", video != null ? video.getVideoUrl() : null);
        detail.put("coverUrl", video != null ? video.getCoverUrl() : null);
        detail.put("chunkIndex", chunk.getChunkIndex());
        detail.put("transcriptSourceType", chunk.getSourceType());
        detail.put("createdAt", chunk.getCreatedAt() != null ? chunk.getCreatedAt().toString() : null);
        detail.put("navigation", buildVideoNavigation(chunk, video));
        return detail;
    }

    private Map<String, Object> buildKnowledgeNavigation(KnowledgeChunk current) {
        List<KnowledgeChunk> siblings = knowledgeChunkRepository.findByDiseaseAndTitleAndSourceAndVersionTagAndPublishedAtOrderByIdAsc(
                current.getDisease(),
                current.getTitle(),
                current.getSource(),
                current.getVersionTag(),
                current.getPublishedAt()
        );
        return buildSiblingNavigation(
                siblings,
                current.getId(),
                item -> toNavigationItem(
                        SOURCE_KNOWLEDGE,
                        item.getId(),
                        null,
                        defaultIfBlank(item.getSection(), item.getTitle()),
                        item.getContent()
                )
        );
    }

    private Map<String, Object> buildVideoNavigation(VideoTranscriptChunk current, VideoResource video) {
        List<VideoTranscriptChunk> siblings = transcriptChunkRepository.findByVideoIdOrderByChunkIndexAsc(current.getVideoId());
        return buildSiblingNavigation(
                siblings,
                current.getId(),
                item -> toNavigationItem(
                        SOURCE_VIDEO_TRANSCRIPT,
                        item.getId(),
                        current.getVideoId(),
                        "讲稿片段 #" + (item.getChunkIndex() + 1),
                        item.getContent()
                )
        );
    }

    private <T> Map<String, Object> buildSiblingNavigation(List<T> siblings,
                                                           Long currentId,
                                                           java.util.function.Function<T, Map<String, Object>> mapper) {
        Map<String, Object> navigation = new LinkedHashMap<>();
        navigation.put("previous", null);
        navigation.put("next", null);
        if (siblings == null || siblings.isEmpty() || currentId == null) {
            return navigation;
        }
        for (int i = 0; i < siblings.size(); i++) {
            T item = siblings.get(i);
            Long itemId = extractId(item);
            if (!currentId.equals(itemId)) {
                continue;
            }
            if (i > 0) {
                navigation.put("previous", mapper.apply(siblings.get(i - 1)));
            }
            if (i + 1 < siblings.size()) {
                navigation.put("next", mapper.apply(siblings.get(i + 1)));
            }
            return navigation;
        }
        return navigation;
    }

    private Long extractId(Object item) {
        if (item instanceof KnowledgeChunk chunk) {
            return chunk.getId();
        }
        if (item instanceof VideoTranscriptChunk chunk) {
            return chunk.getId();
        }
        return null;
    }

    private Map<String, Object> toNavigationItem(String sourceType,
                                                 Long refId,
                                                 Long videoId,
                                                 String label,
                                                 String content) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("sourceType", sourceType);
        item.put("refId", refId);
        item.put("videoId", videoId);
        item.put("label", label);
        item.put("snippet", toSnippet(content));
        return item;
    }

    private String toSnippet(String content) {
        if (content == null || content.isBlank()) {
            return "";
        }
        String normalized = content.replace('\n', ' ').trim();
        return normalized.length() <= 72 ? normalized : normalized.substring(0, 72) + "...";
    }

    private List<String> toTagList(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        return java.util.Arrays.stream(raw.split("[,，;；\\n]+"))
                .map(item -> item == null ? null : item.trim())
                .filter(item -> item != null && !item.isBlank())
                .toList();
    }

    private String normalizeSourceType(String sourceType) {
        if (sourceType == null || sourceType.isBlank()) {
            throw new BusinessException(400, "引用类型不能为空");
        }
        return sourceType.trim().toUpperCase(Locale.ROOT);
    }

    private String defaultIfBlank(String preferred, String fallback) {
        if (preferred != null && !preferred.isBlank()) {
            return preferred;
        }
        return fallback;
    }
}
