package com.medical.agent.service;

import com.medical.agent.common.dto.RagEvidenceDTO;
import com.medical.agent.common.dto.VideoTranscriptChunkDTO;
import com.medical.agent.common.exception.BusinessException;
import com.medical.agent.common.util.DiseaseAliasUtil;
import com.medical.agent.model.entity.VideoResource;
import com.medical.agent.model.entity.VideoTranscriptChunk;
import com.medical.agent.model.repository.VideoResourceRepository;
import com.medical.agent.model.repository.VideoTranscriptChunkRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class VideoTranscriptService {

    private static final int MAX_CHUNK_LENGTH = 1000;

    private final VideoTranscriptChunkRepository transcriptChunkRepository;
    private final VideoResourceRepository videoResourceRepository;
    private final VectorStoreService vectorStoreService;
    private final EmbeddingService embeddingService;

    public VideoTranscriptService(VideoTranscriptChunkRepository transcriptChunkRepository,
                                  VideoResourceRepository videoResourceRepository,
                                  VectorStoreService vectorStoreService,
                                  EmbeddingService embeddingService) {
        this.transcriptChunkRepository = transcriptChunkRepository;
        this.videoResourceRepository = videoResourceRepository;
        this.vectorStoreService = vectorStoreService;
        this.embeddingService = embeddingService;
    }

    public Map<String, Object> getTranscript(Long videoId) {
        VideoResource video = videoResourceRepository.findById(videoId)
                .orElseThrow(() -> new BusinessException(404, "视频不存在"));
        List<VideoTranscriptChunk> chunks = transcriptChunkRepository.findByVideoIdOrderByChunkIndexAsc(videoId);
        StringBuilder content = new StringBuilder();
        for (VideoTranscriptChunk chunk : chunks) {
            if (!content.isEmpty()) {
                content.append("\n\n");
            }
            content.append(chunk.getContent());
        }
        return Map.of(
                "videoId", videoId,
                "hasTranscript", Boolean.TRUE.equals(video.getHasTranscript()),
                "content", content.toString(),
                "chunkCount", chunks.size(),
                "indexStatus", resolveIndexStatus(chunks.size(), (int) chunks.stream().filter(item -> "INDEXED".equals(item.getEmbeddingStatus())).count()),
                "chunks", chunks.stream().map(this::toDTO).toList()
        );
    }

    public Map<String, Object> reindexAll(boolean dryRun) {
        List<VideoResource> videos = videoResourceRepository.findAll();
        List<VideoResource> candidates = videos.stream()
                .filter(video -> Boolean.TRUE.equals(video.getHasTranscript()))
                .toList();

        int transcriptChunkCount = 0;
        List<Long> videoIds = new ArrayList<>(candidates.size());
        for (VideoResource video : candidates) {
            videoIds.add(video.getId());
        }
        if (!videoIds.isEmpty()) {
            transcriptChunkCount = transcriptChunkRepository.findByVideoIdInOrderByVideoIdAscChunkIndexAsc(videoIds).size();
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("message", dryRun ? "视频讲稿批量重建预检完成" : "视频讲稿批量重建已执行");
        result.put("dryRun", dryRun);
        result.put("candidateVideoCount", candidates.size());
        result.put("candidateChunkCount", transcriptChunkCount);
        result.put("embeddingModel", embeddingService.getModel());
        result.put("vectorEnabled", vectorStoreService.isAvailable());

        if (dryRun) {
            return result;
        }

        int reindexedVideoCount = 0;
        int indexedChunkCount = 0;
        List<Long> skippedVideoIds = new ArrayList<>();
        List<Long> failedVideoIds = new ArrayList<>();

        for (VideoResource video : candidates) {
            List<VideoTranscriptChunk> existing = transcriptChunkRepository.findByVideoIdOrderByChunkIndexAsc(video.getId());
            if (existing.isEmpty()) {
                skippedVideoIds.add(video.getId());
                continue;
            }

            String content = joinTranscript(existing);
            String sourceType = existing.get(0).getSourceType();
            try {
                Map<String, Object> reindexResult = saveTranscript(video.getId(), content, sourceType);
                reindexedVideoCount++;
                indexedChunkCount += readInt(reindexResult.get("indexedChunkCount"));
            } catch (Exception ex) {
                failedVideoIds.add(video.getId());
            }
        }

        result.put("reindexedVideoCount", reindexedVideoCount);
        result.put("indexedChunkCount", indexedChunkCount);
        result.put("skippedVideoIds", skippedVideoIds);
        result.put("failedVideoIds", failedVideoIds);
        result.put("indexStatus", failedVideoIds.isEmpty() ? "SUCCESS" : (reindexedVideoCount > 0 ? "PARTIAL" : "FAILED"));
        return result;
    }

    @Transactional
    public Map<String, Object> saveTranscript(Long videoId, String content, String sourceType) {
        VideoResource video = videoResourceRepository.findById(videoId)
                .orElseThrow(() -> new BusinessException(404, "视频不存在"));

        List<VideoTranscriptChunk> existing = transcriptChunkRepository.findByVideoIdOrderByChunkIndexAsc(videoId);
        for (VideoTranscriptChunk chunk : existing) {
            vectorStoreService.deleteVideoTranscriptPoint(chunk.getVectorPointId());
        }
        transcriptChunkRepository.deleteByVideoId(videoId);

        String normalizedContent = normalizeTranscript(content);
        if (normalizedContent == null || normalizedContent.isBlank()) {
            video.setHasTranscript(false);
            videoResourceRepository.save(video);
            return Map.of(
                    "videoId", videoId,
                    "hasTranscript", false,
                    "chunkCount", 0,
                    "indexedChunkCount", 0
            );
        }

        List<String> pieces = splitLongText(normalizedContent, MAX_CHUNK_LENGTH);
        List<VideoTranscriptChunk> chunks = new ArrayList<>();
        for (int i = 0; i < pieces.size(); i++) {
            chunks.add(VideoTranscriptChunk.builder()
                    .videoId(videoId)
                    .chunkIndex(i)
                    .content(pieces.get(i))
                    .sourceType(normalizeSourceType(sourceType))
                    .embeddingStatus(vectorStoreService.isAvailable() ? "PENDING" : "DISABLED")
                    .build());
        }
        chunks = transcriptChunkRepository.saveAll(chunks);

        int indexed = 0;
        for (VideoTranscriptChunk chunk : chunks) {
            String pointId = vectorStoreService.upsertVideoTranscriptChunk(chunk, video);
            if (pointId != null) {
                chunk.setVectorPointId(pointId);
                chunk.setEmbeddingModel(embeddingService.getModel());
                chunk.setEmbeddingStatus("INDEXED");
                chunk.setIndexedAt(LocalDateTime.now());
                indexed++;
            } else if (vectorStoreService.isAvailable()) {
                chunk.setEmbeddingStatus("FAILED");
            } else {
                chunk.setEmbeddingStatus("DISABLED");
            }
        }
        transcriptChunkRepository.saveAll(chunks);

        video.setHasTranscript(true);
        videoResourceRepository.save(video);

        return Map.of(
                "videoId", videoId,
                "hasTranscript", true,
                "chunkCount", chunks.size(),
                "indexedChunkCount", indexed,
                "indexStatus", resolveIndexStatus(chunks.size(), indexed),
                "chunks", chunks.stream().map(this::toDTO).toList()
        );
    }

    @Transactional
    public void deleteTranscript(Long videoId) {
        List<VideoTranscriptChunk> existing = transcriptChunkRepository.findByVideoIdOrderByChunkIndexAsc(videoId);
        for (VideoTranscriptChunk chunk : existing) {
            vectorStoreService.deleteVideoTranscriptPoint(chunk.getVectorPointId());
        }
        transcriptChunkRepository.deleteByVideoId(videoId);
        videoResourceRepository.findById(videoId).ifPresent(video -> {
            video.setHasTranscript(false);
            videoResourceRepository.save(video);
        });
    }

    public Map<Long, Double> scoreVideos(String queryText, Collection<String> diseaseKeys, Collection<Long> candidateVideoIds) {
        if (candidateVideoIds == null || candidateVideoIds.isEmpty() || queryText == null || queryText.isBlank()) {
            return Map.of();
        }
        Set<Long> candidateSet = new LinkedHashSet<>(candidateVideoIds);
        LinkedHashMap<Long, Double> scores = new LinkedHashMap<>();

        List<RagEvidenceDTO> vectorMatches = vectorStoreService.searchVideoTranscripts(
                queryText,
                diseaseKeys,
                Math.max(24, candidateSet.size() * 4)
        );
        for (RagEvidenceDTO match : vectorMatches) {
            if (match.getVideoId() == null || !candidateSet.contains(match.getVideoId())) {
                continue;
            }
            scores.merge(match.getVideoId(), match.getScore() != null ? match.getScore() : 0.0, Double::sum);
        }
        if (!scores.isEmpty()) {
            return scores;
        }
        return lexicalFallbackScores(queryText, candidateSet, diseaseKeys);
    }

    private Map<Long, Double> lexicalFallbackScores(String queryText, Set<Long> candidateVideoIds, Collection<String> diseaseKeys) {
        List<VideoTranscriptChunk> chunks = transcriptChunkRepository.findByVideoIdInOrderByVideoIdAscChunkIndexAsc(candidateVideoIds);
        if (chunks.isEmpty()) {
            return Map.of();
        }

        List<String> keywords = extractKeywords(queryText, diseaseKeys);
        LinkedHashMap<Long, Double> scores = new LinkedHashMap<>();
        for (VideoTranscriptChunk chunk : chunks) {
            String content = chunk.getContent() != null ? chunk.getContent() : "";
            double score = 0;
            for (String keyword : keywords) {
                if (keyword != null && !keyword.isBlank() && content.contains(keyword)) {
                    score += 1.0;
                }
            }
            if (score > 0) {
                scores.merge(chunk.getVideoId(), score, Double::sum);
            }
        }
        return scores;
    }

    private List<String> extractKeywords(String queryText, Collection<String> diseaseKeys) {
        LinkedHashSet<String> keywords = new LinkedHashSet<>();
        for (String diseaseKey : DiseaseAliasUtil.normalizeKeys(diseaseKeys)) {
            keywords.add(DiseaseAliasUtil.displayName(diseaseKey));
            keywords.add(DiseaseAliasUtil.knowledgeLabel(diseaseKey));
        }
        String[] parts = queryText.split("[\\n，。；、,:： ]+");
        for (String part : parts) {
            String trimmed = part.trim();
            if (trimmed.length() >= 2 && trimmed.length() <= 12) {
                keywords.add(trimmed);
            }
            if (keywords.size() >= 16) {
                break;
            }
        }
        return new ArrayList<>(keywords);
    }

    private String joinTranscript(List<VideoTranscriptChunk> chunks) {
        StringBuilder builder = new StringBuilder();
        for (VideoTranscriptChunk chunk : chunks) {
            if (chunk == null || chunk.getContent() == null || chunk.getContent().isBlank()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append("\n\n");
            }
            builder.append(chunk.getContent().trim());
        }
        return builder.toString();
    }

    private int readInt(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null) {
            return 0;
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private String normalizeTranscript(String content) {
        if (content == null) {
            return null;
        }
        return content.replace("\uFEFF", "")
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .trim();
    }

    private String normalizeSourceType(String sourceType) {
        if (sourceType == null || sourceType.isBlank()) {
            return "MANUAL";
        }
        return sourceType.trim().toUpperCase();
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

    private VideoTranscriptChunkDTO toDTO(VideoTranscriptChunk chunk) {
        return VideoTranscriptChunkDTO.builder()
                .id(chunk.getId())
                .videoId(chunk.getVideoId())
                .chunkIndex(chunk.getChunkIndex())
                .content(chunk.getContent())
                .sourceType(chunk.getSourceType())
                .embeddingStatus(chunk.getEmbeddingStatus())
                .createdAt(chunk.getCreatedAt() != null ? chunk.getCreatedAt().toString() : null)
                .build();
    }
}
