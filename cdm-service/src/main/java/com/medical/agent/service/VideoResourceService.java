package com.medical.agent.service;

import com.medical.agent.common.dto.VideoResourceDTO;
import com.medical.agent.common.enums.DiseaseType;
import com.medical.agent.common.enums.RiskLevel;
import com.medical.agent.common.exception.BusinessException;
import com.medical.agent.common.util.DiseaseAliasUtil;
import com.medical.agent.model.entity.HealthRecord;
import com.medical.agent.model.entity.HealthReportVideoSnapshot;
import com.medical.agent.model.entity.RiskAssessment;
import com.medical.agent.model.entity.VideoResource;
import com.medical.agent.model.entity.VideoWatchSession;
import com.medical.agent.model.repository.HealthRecordRepository;
import com.medical.agent.model.repository.HealthReportVideoSnapshotRepository;
import com.medical.agent.model.repository.RiskAssessmentRepository;
import com.medical.agent.model.repository.VideoResourceRepository;
import com.medical.agent.model.repository.VideoWatchSessionRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class VideoResourceService {

    public static final String DISEASE_GENERAL = DiseaseAliasUtil.GENERAL;
    private static final int DEFAULT_RECOMMEND_LIMIT = 4;

    private final VideoResourceRepository videoResourceRepository;
    private final RiskAssessmentRepository riskAssessmentRepository;
    private final HealthRecordRepository healthRecordRepository;
    private final HealthReportVideoSnapshotRepository healthReportVideoSnapshotRepository;
    private final VideoTranscriptService videoTranscriptService;
    private final KnowledgeGraphService knowledgeGraphService;
    private final VideoWatchSessionRepository videoWatchSessionRepository;

    public VideoResourceService(VideoResourceRepository videoResourceRepository,
                                RiskAssessmentRepository riskAssessmentRepository,
                                HealthRecordRepository healthRecordRepository,
                                HealthReportVideoSnapshotRepository healthReportVideoSnapshotRepository,
                                VideoTranscriptService videoTranscriptService,
                                KnowledgeGraphService knowledgeGraphService,
                                VideoWatchSessionRepository videoWatchSessionRepository) {
        this.videoResourceRepository = videoResourceRepository;
        this.riskAssessmentRepository = riskAssessmentRepository;
        this.healthRecordRepository = healthRecordRepository;
        this.healthReportVideoSnapshotRepository = healthReportVideoSnapshotRepository;
        this.videoTranscriptService = videoTranscriptService;
        this.knowledgeGraphService = knowledgeGraphService;
        this.videoWatchSessionRepository = videoWatchSessionRepository;
    }

    public List<VideoResourceDTO> listAdmin(String keyword, Boolean active, String diseaseType, String category) {
        return sortedVideos().stream()
                .filter(item -> active == null || Objects.equals(Boolean.TRUE.equals(item.getActive()), active))
                .filter(item -> matchesKeyword(item, keyword))
                .filter(item -> matchesDisease(item, diseaseType))
                .filter(item -> matchesCategory(item, category))
                .map(item -> toDTO(item, null))
                .toList();
    }

    public List<VideoResourceDTO> listPublished(String diseaseType, String category, Integer limit) {
        int finalLimit = normalizeLimit(limit, 0);
        return sortedVideos().stream()
                .filter(item -> Boolean.TRUE.equals(item.getActive()))
                .filter(item -> matchesDisease(item, diseaseType))
                .filter(item -> matchesCategory(item, category))
                .limit(finalLimit > 0 ? finalLimit : Long.MAX_VALUE)
                .map(item -> toDTO(item, null))
                .toList();
    }

    public List<VideoResourceDTO> getRecommendedVideos(Long patientId, Integer limit) {
        int finalLimit = normalizeLimit(limit, DEFAULT_RECOMMEND_LIMIT);
        List<VideoResource> activeVideos = sortedVideos().stream()
                .filter(item -> Boolean.TRUE.equals(item.getActive()))
                .toList();
        if (activeVideos.isEmpty()) {
            return List.of();
        }

        List<RiskAssessment> assessments = riskAssessmentRepository.findByPatientIdOrderByCreatedAtDesc(patientId);
        Optional<HealthRecord> latestRecord = healthRecordRepository.findFirstByPatientIdOrderByRecordDateDesc(patientId);
        Map<String, RiskAssessment> latestByDisease = latestAssessmentsByDisease(assessments);
        List<String> diseaseKeys = new ArrayList<>(latestByDisease.keySet());

        List<VideoResource> candidates = diseaseKeys.isEmpty()
                ? activeVideos
                : activeVideos.stream()
                .filter(item -> {
                    String diseaseKey = normalizedDiseaseType(item.getDiseaseType());
                    return diseaseKeys.contains(diseaseKey) || DISEASE_GENERAL.equalsIgnoreCase(diseaseKey);
                })
                .toList();
        if (candidates.isEmpty()) {
            candidates = activeVideos;
        }

        String queryText = buildRecommendationQuery(latestByDisease.values().stream().toList(), latestRecord.orElse(null));
        Map<Long, Double> semanticScores = videoTranscriptService.scoreVideos(
                queryText,
                diseaseKeys,
                candidates.stream().map(VideoResource::getId).toList()
        );
        Map<Long, Double> behaviorScores = buildBehaviorScores(patientId, candidates);
        Map<Long, KnowledgeGraphService.RecommendationScore> graphScores = knowledgeGraphService.scoreVideosForPatient(
                candidates.stream().map(VideoResource::getId).toList(),
                assessments,
                latestRecord.orElse(null)
        );

        return candidates.stream()
                .sorted(Comparator
                        .comparingDouble((VideoResource item) -> -recommendationScore(
                                semanticScores.getOrDefault(item.getId(), 0d),
                                graphScores.get(item.getId()),
                                behaviorScores.getOrDefault(item.getId(), 0d),
                                tagScore(item, latestByDisease.keySet(), latestRecord.orElse(null))
                        ))
                        .thenComparingDouble((VideoResource item) -> -graphScore(graphScores.get(item.getId())))
                        .thenComparingDouble((VideoResource item) -> -behaviorScores.getOrDefault(item.getId(), 0d))
                        .thenComparingDouble((VideoResource item) -> -semanticScores.getOrDefault(item.getId(), 0d))
                        .thenComparingInt(item -> diseasePriority(normalizedDiseaseType(item.getDiseaseType()), diseaseKeys))
                        .thenComparing(VideoResource::getSortOrder, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(VideoResource::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(finalLimit)
                .map(item -> toDTO(item, buildRecommendReason(
                        item,
                        latestByDisease,
                        semanticScores.getOrDefault(item.getId(), 0d),
                        graphScores.get(item.getId()),
                        behaviorScores.getOrDefault(item.getId(), 0d),
                        tagScore(item, latestByDisease.keySet(), latestRecord.orElse(null))
                )))
                .toList();
    }

    public VideoResourceDTO getById(Long id) {
        return videoResourceRepository.findById(id)
                .map(item -> toDTO(item, null))
                .orElseThrow(() -> new BusinessException(404, "视频不存在"));
    }

    public Map<String, Object> getRecommendationFeedbackSummary() {
        List<VideoResource> videos = sortedVideos();
        List<VideoWatchSession> sessions = videoWatchSessionRepository.findAll();

        Map<Long, List<VideoWatchSession>> sessionMap = sessions.stream()
                .filter(session -> session.getVideoId() != null)
                .collect(Collectors.groupingBy(VideoWatchSession::getVideoId, LinkedHashMap::new, Collectors.toList()));

        List<Map<String, Object>> topVideos = new ArrayList<>();
        long watchedVideoCount = 0L;
        long completedSessionCount = 0L;
        double progressSum = 0D;

        for (VideoResource video : videos) {
            List<VideoWatchSession> related = sessionMap.getOrDefault(video.getId(), List.of());
            if (!related.isEmpty()) {
                watchedVideoCount++;
            }

            long watchCount = related.size();
            long uniquePatientCount = related.stream()
                    .map(VideoWatchSession::getPatientId)
                    .filter(Objects::nonNull)
                    .distinct()
                    .count();
            long completionCount = related.stream()
                    .filter(session -> Boolean.TRUE.equals(session.getCompleted()))
                    .count();
            completedSessionCount += completionCount;

            double avgProgress = related.stream()
                    .map(VideoWatchSession::getProgressPercent)
                    .filter(Objects::nonNull)
                    .mapToDouble(Double::doubleValue)
                    .average()
                    .orElse(0D);
            progressSum += avgProgress;

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("videoId", video.getId());
            item.put("title", video.getTitle());
            item.put("diseaseType", normalizedDiseaseType(video.getDiseaseType()));
            item.put("category", video.getCategory());
            item.put("active", Boolean.TRUE.equals(video.getActive()));
            item.put("watchCount", watchCount);
            item.put("uniquePatientCount", uniquePatientCount);
            item.put("completionCount", completionCount);
            item.put("completionRate", watchCount == 0 ? 0D : round2((completionCount * 100D) / watchCount));
            item.put("avgProgress", round2(avgProgress));
            item.put("recommendationHint", feedbackHint(video, watchCount, completionCount, avgProgress));
            topVideos.add(item);
        }

        topVideos.sort(Comparator
                .comparingDouble((Map<String, Object> item) -> -toDouble(item.get("completionRate")))
                .thenComparingDouble(item -> -toDouble(item.get("avgProgress")))
                .thenComparingLong(item -> -toLongValue(item.get("watchCount"))));

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("totalVideos", videos.size());
        summary.put("watchedVideos", watchedVideoCount);
        summary.put("totalWatchSessions", sessions.size());
        summary.put("completedSessions", completedSessionCount);
        summary.put("avgProgress", videos.isEmpty() ? 0D : round2(progressSum / videos.size()));
        summary.put("topVideos", topVideos.stream().limit(8).toList());
        return summary;
    }

    @Transactional
    public VideoResourceDTO create(Map<String, Object> body) {
        VideoResource entity = new VideoResource();
        applyBody(entity, body, true);
        return toDTO(videoResourceRepository.save(entity), null);
    }

    @Transactional
    public VideoResourceDTO update(Long id, Map<String, Object> body) {
        VideoResource entity = videoResourceRepository.findById(id)
                .orElseThrow(() -> new BusinessException(404, "视频不存在"));
        applyBody(entity, body, false);
        return toDTO(videoResourceRepository.save(entity), null);
    }

    @Transactional
    public Map<String, Object> delete(Long id) {
        VideoResource entity = videoResourceRepository.findById(id)
                .orElseThrow(() -> new BusinessException(404, "视频不存在"));
        videoResourceRepository.delete(entity);
        return Map.of("message", "视频已删除", "id", id);
    }

    public boolean canRecycleVideoAsset(String objectKey) {
        String normalized = normalizedText(objectKey);
        if (normalized == null) {
            return false;
        }
        return videoResourceRepository.countByObjectKey(normalized) == 0
                && healthReportVideoSnapshotRepository.countByObjectKey(normalized) == 0;
    }

    public boolean canRecycleCoverAsset(String objectKey) {
        String normalized = normalizedText(objectKey);
        if (normalized == null) {
            return false;
        }
        return videoResourceRepository.countByCoverObjectKey(normalized) == 0
                && healthReportVideoSnapshotRepository.countByCoverObjectKey(normalized) == 0;
    }

    public HealthReportVideoSnapshot toSnapshotEntity(Long reportId, VideoResourceDTO video, int sortOrder) {
        return HealthReportVideoSnapshot.builder()
                .reportId(reportId)
                .videoId(video.getId())
                .title(video.getTitle())
                .description(video.getDescription())
                .diseaseType(video.getDiseaseType())
                .category(video.getCategory())
                .coverUrl(video.getCoverUrl())
                .videoUrl(video.getVideoUrl())
                .objectKey(video.getObjectKey())
                .coverObjectKey(video.getCoverObjectKey())
                .durationSeconds(video.getDurationSeconds())
                .recommendReason(video.getRecommendReason())
                .sortOrder(sortOrder)
                .build();
    }

    public VideoResourceDTO toDTO(HealthReportVideoSnapshot snapshot) {
        return VideoResourceDTO.builder()
                .id(snapshot.getVideoId())
                .title(snapshot.getTitle())
                .description(snapshot.getDescription())
                .diseaseType(normalizedDiseaseType(snapshot.getDiseaseType()))
                .category(snapshot.getCategory())
                .tags(List.of())
                .coverUrl(snapshot.getCoverUrl())
                .videoUrl(snapshot.getVideoUrl())
                .objectKey(snapshot.getObjectKey())
                .coverObjectKey(snapshot.getCoverObjectKey())
                .durationSeconds(snapshot.getDurationSeconds())
                .sortOrder(snapshot.getSortOrder())
                .recommendReason(snapshot.getRecommendReason())
                .active(true)
                .hasTranscript(false)
                .createdAt(snapshot.getCreatedAt() != null ? snapshot.getCreatedAt().toString() : null)
                .updatedAt(snapshot.getCreatedAt() != null ? snapshot.getCreatedAt().toString() : null)
                .build();
    }

    private List<VideoResource> sortedVideos() {
        return videoResourceRepository.findAll(Sort.by(
                Sort.Order.desc("active"),
                Sort.Order.desc("hasTranscript"),
                Sort.Order.asc("sortOrder"),
                Sort.Order.desc("createdAt")
        ));
    }

    private void applyBody(VideoResource entity, Map<String, Object> body, boolean createMode) {
        String title = normalizedText(body.get("title"));
        String videoUrl = normalizedText(body.get("videoUrl"));
        if (createMode && (title == null || title.isBlank())) {
            throw new BusinessException(400, "视频标题不能为空");
        }
        if (createMode && (videoUrl == null || videoUrl.isBlank())) {
            throw new BusinessException(400, "视频地址不能为空");
        }

        if (title != null) {
            entity.setTitle(title);
        }
        if (body.containsKey("description")) {
            entity.setDescription(normalizedText(body.get("description")));
        }
        if (videoUrl != null) {
            entity.setVideoUrl(videoUrl);
        }
        if (body.containsKey("coverUrl")) {
            entity.setCoverUrl(normalizedText(body.get("coverUrl")));
        }
        if (body.containsKey("objectKey")) {
            entity.setObjectKey(normalizedText(body.get("objectKey")));
        }
        if (body.containsKey("coverObjectKey")) {
            entity.setCoverObjectKey(normalizedText(body.get("coverObjectKey")));
        }
        if (body.containsKey("category")) {
            entity.setCategory(normalizedText(body.get("category")));
        }
        if (body.containsKey("tags")) {
            entity.setContentTags(normalizeTags(body.get("tags")));
        }
        if (body.containsKey("diseaseType")) {
            entity.setDiseaseType(normalizeDiseaseValue(normalizedText(body.get("diseaseType"))));
        }
        if (body.containsKey("durationSeconds")) {
            entity.setDurationSeconds(toLong(body.get("durationSeconds")));
        }
        if (body.containsKey("sortOrder")) {
            entity.setSortOrder(toInteger(body.get("sortOrder"), 0));
        }
        if (body.containsKey("active")) {
            entity.setActive(toBoolean(body.get("active"), true));
        }
        entity.setSourceType(inferSourceType(body, entity));

        if (entity.getTitle() == null || entity.getTitle().isBlank()) {
            throw new BusinessException(400, "视频标题不能为空");
        }
        if (entity.getVideoUrl() == null || entity.getVideoUrl().isBlank()) {
            throw new BusinessException(400, "视频地址不能为空");
        }
    }

    private Map<String, RiskAssessment> latestAssessmentsByDisease(List<RiskAssessment> assessments) {
        Map<String, RiskAssessment> latestByDisease = new LinkedHashMap<>();
        for (RiskAssessment assessment : assessments) {
            String diseaseKey = assessment.getDiseaseType() != null
                    ? DiseaseAliasUtil.normalizeKey(assessment.getDiseaseType().name())
                    : null;
            if (diseaseKey != null && !latestByDisease.containsKey(diseaseKey)) {
                latestByDisease.put(diseaseKey, assessment);
            }
        }
        return latestByDisease;
    }

    private String buildRecommendationQuery(List<RiskAssessment> assessments, HealthRecord latestRecord) {
        StringBuilder query = new StringBuilder("推荐患者教育视频：\n");
        assessments.stream()
                .sorted(Comparator
                        .comparingInt((RiskAssessment item) -> riskWeight(item.getRiskLevel())).reversed()
                        .thenComparing(item -> item.getScore() != null ? item.getScore() : 0d, Comparator.reverseOrder()))
                .limit(4)
                .forEach(item -> {
                    if (item.getDiseaseType() == null) {
                        return;
                    }
                    query.append("- ").append(item.getDiseaseType().getDisplayName());
                    if (item.getRiskLevel() != null) {
                        query.append(" / ").append(item.getRiskLevel().getDisplayName());
                    }
                    if (item.getAssessmentDetail() != null && !item.getAssessmentDetail().isBlank()) {
                        query.append(" / ").append(item.getAssessmentDetail());
                    }
                    query.append('\n');
                });
        if (latestRecord != null) {
            appendMetric(query, "收缩压", latestRecord.getSystolicBp(), "mmHg");
            appendMetric(query, "舒张压", latestRecord.getDiastolicBp(), "mmHg");
            appendMetric(query, "空腹血糖", latestRecord.getFastingGlucose(), "mmol/L");
            appendMetric(query, "BMI", latestRecord.getBmi(), "");
            appendMetric(query, "每周运动次数", latestRecord.getExerciseFrequency(), "次");
            appendMetric(query, "盐摄入评分", latestRecord.getSaltIntake(), "");
            appendMetric(query, "漏服率", latestRecord.getMedicationMissRate(), "%");
        }
        return query.toString();
    }

    private void appendMetric(StringBuilder query, String label, Object value, String unit) {
        if (value == null) {
            return;
        }
        query.append("- ").append(label).append("：").append(value);
        if (unit != null && !unit.isBlank()) {
            query.append(' ').append(unit);
        }
        query.append('\n');
    }

    private int diseasePriority(String diseaseKey, List<String> orderedDiseaseKeys) {
        int index = orderedDiseaseKeys.indexOf(diseaseKey);
        if (index >= 0) {
            return index;
        }
        if (DISEASE_GENERAL.equalsIgnoreCase(diseaseKey)) {
            return 100;
        }
        return 200;
    }

    private String inferSourceType(Map<String, Object> body, VideoResource entity) {
        String explicit = normalizedText(body.get("sourceType"));
        if (explicit != null && !explicit.isBlank()) {
            return explicit.toUpperCase(Locale.ROOT);
        }
        if (entity.getObjectKey() != null && !entity.getObjectKey().isBlank()) {
            return "UPLOAD";
        }
        if (entity.getVideoUrl() != null && entity.getVideoUrl().startsWith("http")) {
            return "REMOTE";
        }
        return "LOCAL";
    }

    private String buildRecommendReason(VideoResource item,
                                        Map<String, RiskAssessment> latestByDisease,
                                        double semanticScore,
                                        KnowledgeGraphService.RecommendationScore graphScore,
                                        double behaviorScore,
                                        double tagScore) {
        String diseaseKey = normalizedDiseaseType(item.getDiseaseType());
        RiskAssessment assessment = latestByDisease.get(diseaseKey);
        String graphReason = graphScore != null ? graphScore.getReason() : null;
        if (assessment != null) {
            String base = buildReason(assessment);
            if (semanticScore > 0 && Boolean.TRUE.equals(item.getHasTranscript()) && graphReason != null && !graphReason.isBlank()) {
                return appendBehaviorReason(base + "，" + graphReason + "，并结合视频讲稿语义匹配", behaviorScore, tagScore);
            }
            if (graphReason != null && !graphReason.isBlank()) {
                return appendBehaviorReason(base + "，" + graphReason, behaviorScore, tagScore);
            }
            if (semanticScore > 0 && Boolean.TRUE.equals(item.getHasTranscript())) {
                return appendBehaviorReason(base + "，并结合视频讲稿语义匹配", behaviorScore, tagScore);
            }
            return appendBehaviorReason(base, behaviorScore, tagScore);
        }
        if (graphReason != null && !graphReason.isBlank()) {
            if (semanticScore > 0 && Boolean.TRUE.equals(item.getHasTranscript())) {
                return appendBehaviorReason(graphReason + "，并结合视频讲稿语义匹配", behaviorScore, tagScore);
            }
            return appendBehaviorReason(graphReason, behaviorScore, tagScore);
        }
        if (semanticScore > 0 && Boolean.TRUE.equals(item.getHasTranscript())) {
            return appendBehaviorReason("结合患者当前情况和视频讲稿内容推荐", behaviorScore, tagScore);
        }
        if (DISEASE_GENERAL.equalsIgnoreCase(diseaseKey)) {
            return appendBehaviorReason("通用慢病管理视频", behaviorScore, tagScore);
        }
        return appendBehaviorReason("补充推荐", behaviorScore, tagScore);
    }

    private double recommendationScore(double semanticScore,
                                       KnowledgeGraphService.RecommendationScore graphScore,
                                       double behaviorScore,
                                       double tagScore) {
        return graphScore(graphScore) * 1.25d + semanticScore + behaviorScore * 0.55d + tagScore * 0.35d;
    }

    private double graphScore(KnowledgeGraphService.RecommendationScore graphScore) {
        return graphScore == null ? 0d : graphScore.getScore();
    }

    private String buildReason(RiskAssessment assessment) {
        DiseaseType diseaseType = assessment.getDiseaseType();
        if (diseaseType == null) {
            return "根据最近风险评估推荐";
        }
        return "根据" + diseaseType.getDisplayName() +
                (assessment.getRiskLevel() != null ? "的" + assessment.getRiskLevel().getDisplayName() : "") +
                "评估结果推荐";
    }

    private Map<Long, Double> buildBehaviorScores(Long patientId, List<VideoResource> candidates) {
        if (patientId == null || candidates == null || candidates.isEmpty()) {
            return Map.of();
        }
        Map<Long, Double> scores = new LinkedHashMap<>();
        for (VideoResource candidate : candidates) {
            VideoWatchSession latest = videoWatchSessionRepository
                    .findTopByPatientIdAndVideoIdOrderByLastActiveAtDesc(patientId, candidate.getId())
                    .orElse(null);
            if (latest == null) {
                continue;
            }
            double progress = latest.getProgressPercent() == null ? 0D : latest.getProgressPercent();
            double score = 0D;
            if (Boolean.TRUE.equals(latest.getCompleted())) {
                score += 0.8d;
            } else if (progress >= 60D) {
                score += 0.45d;
            } else if (progress <= 10D) {
                score -= 0.2d;
            }
            scores.put(candidate.getId(), score);
        }
        return scores;
    }

    private double tagScore(VideoResource item, java.util.Set<String> diseaseKeys, HealthRecord latestRecord) {
        List<String> tags = splitStoredTags(item.getContentTags());
        if (tags.isEmpty()) {
            return 0D;
        }
        double score = 0D;
        for (String diseaseKey : diseaseKeys) {
            String display = DiseaseAliasUtil.displayName(diseaseKey).toLowerCase(Locale.ROOT);
            if (tags.stream().anyMatch(tag -> tag.toLowerCase(Locale.ROOT).contains(display))) {
                score += 0.4d;
            }
        }
        if (latestRecord != null) {
            if (latestRecord.getSaltIntake() != null && latestRecord.getSaltIntake() >= 3D
                    && tags.stream().anyMatch(tag -> tag.contains("饮食") || tag.contains("低盐"))) {
                score += 0.25d;
            }
            if (latestRecord.getMedicationMissRate() != null && latestRecord.getMedicationMissRate() > 0D
                    && tags.stream().anyMatch(tag -> tag.contains("用药") || tag.contains("依从"))) {
                score += 0.25d;
            }
            if (latestRecord.getExerciseFrequency() != null && latestRecord.getExerciseFrequency() <= 1
                    && tags.stream().anyMatch(tag -> tag.contains("运动") || tag.contains("活动"))) {
                score += 0.2d;
            }
        }
        return score;
    }

    private String appendBehaviorReason(String base, double behaviorScore, double tagScore) {
        if (behaviorScore >= 0.75d) {
            return base + "，且该患者对同类内容完成度较高";
        }
        if (tagScore >= 0.45d) {
            return base + "，并与患者当前宣教主题高度匹配";
        }
        return base;
    }

    private String feedbackHint(VideoResource video, long watchCount, long completionCount, double avgProgress) {
        if (watchCount == 0) {
            return "当前暂无观看反馈，可优先补充讲稿和更细标签。";
        }
        if (completionCount > 0 && avgProgress >= 75D) {
            return "完成度较高，可作为高优先级宣教内容继续推荐。";
        }
        if (avgProgress >= 45D) {
            return "观看意愿中等，建议优化标题、封面或推荐场景描述。";
        }
        if (!Boolean.TRUE.equals(video.getHasTranscript())) {
            return "观看进度偏低，建议补全讲稿并强化主题标签。";
        }
        return "观看进度偏低，建议重新校准推荐病种、分类和主题标签。";
    }

    private double round2(double value) {
        return Math.round(value * 100D) / 100D;
    }

    private double toDouble(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value == null) {
            return 0D;
        }
        try {
            return Double.parseDouble(value.toString());
        } catch (NumberFormatException ignored) {
            return 0D;
        }
    }

    private long toLongValue(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value == null) {
            return 0L;
        }
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException ignored) {
            return 0L;
        }
    }

    private boolean matchesKeyword(VideoResource item, String keyword) {
        String normalized = normalizedText(keyword);
        if (normalized == null || normalized.isBlank()) {
            return true;
        }
        String lower = normalized.toLowerCase(Locale.ROOT);
        return containsIgnoreCase(item.getTitle(), lower)
                || containsIgnoreCase(item.getDescription(), lower)
                || containsIgnoreCase(item.getCategory(), lower)
                || containsIgnoreCase(item.getContentTags(), lower);
    }

    private boolean matchesDisease(VideoResource item, String diseaseType) {
        String raw = normalizedText(diseaseType);
        if (raw == null || raw.isBlank()) {
            return true;
        }
        String normalized = normalizeDiseaseValue(raw);
        return normalized.equalsIgnoreCase(normalizedDiseaseType(item.getDiseaseType()));
    }

    private boolean matchesCategory(VideoResource item, String category) {
        String normalized = normalizedText(category);
        if (normalized == null || normalized.isBlank()) {
            return true;
        }
        return containsIgnoreCase(item.getCategory(), normalized.toLowerCase(Locale.ROOT));
    }

    private boolean containsIgnoreCase(String source, String keywordLower) {
        return source != null && source.toLowerCase(Locale.ROOT).contains(keywordLower);
    }

    private String normalizeDiseaseValue(String diseaseType) {
        return DiseaseAliasUtil.normalizeKey(diseaseType);
    }

    private String normalizedDiseaseType(String diseaseType) {
        return DiseaseAliasUtil.normalizeKey(diseaseType);
    }

    private String normalizeTags(Object raw) {
        if (raw == null) {
            return null;
        }
        List<String> values = new ArrayList<>();
        if (raw instanceof Iterable<?> iterable) {
            for (Object item : iterable) {
                String tag = normalizedText(item);
                if (tag != null) {
                    values.add(tag);
                }
            }
        } else {
            String text = raw.toString();
            for (String part : text.split("[,，;；\\n]+")) {
                String tag = normalizedText(part);
                if (tag != null) {
                    values.add(tag);
                }
            }
        }
        LinkedHashSet<String> tags = new LinkedHashSet<>();
        for (String value : values) {
            String normalized = value.length() > 24 ? value.substring(0, 24) : value;
            tags.add(normalized);
            if (tags.size() >= 12) {
                break;
            }
        }
        return tags.isEmpty() ? null : String.join(", ", tags);
    }

    private List<String> splitStoredTags(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        List<String> tags = new ArrayList<>();
        for (String part : raw.split("[,，;；\\n]+")) {
            String normalized = normalizedText(part);
            if (normalized != null && !normalized.isBlank()) {
                tags.add(normalized);
            }
        }
        return tags;
    }

    private List<String> toTagList(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        List<String> tags = new ArrayList<>();
        for (String part : raw.split("[,，;；\\n]+")) {
            String tag = normalizedText(part);
            if (tag != null) {
                tags.add(tag);
            }
        }
        return tags;
    }

    private int normalizeLimit(Integer limit, int defaultValue) {
        if (limit == null) {
            return defaultValue;
        }
        return Math.max(1, Math.min(limit, 20));
    }

    private int riskWeight(RiskLevel riskLevel) {
        if (riskLevel == null) {
            return 0;
        }
        return switch (riskLevel) {
            case LOW -> 1;
            case MEDIUM -> 2;
            case HIGH -> 3;
            case CRITICAL -> 4;
        };
    }

    private String normalizedText(Object value) {
        if (value == null) {
            return null;
        }
        String text = value.toString().trim();
        return text.isEmpty() ? null : text;
    }

    private Integer toInteger(Object value, int defaultValue) {
        if (value == null || value.toString().isBlank()) {
            return defaultValue;
        }
        try {
            return Integer.valueOf(value.toString().trim());
        } catch (NumberFormatException e) {
            throw new BusinessException(400, "排序值格式错误");
        }
    }

    private Long toLong(Object value) {
        if (value == null || value.toString().isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(value.toString().trim());
        } catch (NumberFormatException e) {
            throw new BusinessException(400, "视频时长格式错误");
        }
    }

    private Boolean toBoolean(Object value, boolean defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Boolean bool) {
            return bool;
        }
        String text = value.toString().trim();
        if (text.isEmpty()) {
            return defaultValue;
        }
        return "true".equalsIgnoreCase(text) || "1".equals(text) || "yes".equalsIgnoreCase(text);
    }

    private VideoResourceDTO toDTO(VideoResource entity, String recommendReason) {
        return VideoResourceDTO.builder()
                .id(entity.getId())
                .title(entity.getTitle())
                .description(entity.getDescription())
                .diseaseType(normalizedDiseaseType(entity.getDiseaseType()))
                .category(entity.getCategory())
                .tags(toTagList(entity.getContentTags()))
                .coverUrl(entity.getCoverUrl())
                .videoUrl(entity.getVideoUrl())
                .objectKey(entity.getObjectKey())
                .coverObjectKey(entity.getCoverObjectKey())
                .sourceType(entity.getSourceType())
                .durationSeconds(entity.getDurationSeconds())
                .sortOrder(entity.getSortOrder())
                .active(Boolean.TRUE.equals(entity.getActive()))
                .hasTranscript(Boolean.TRUE.equals(entity.getHasTranscript()))
                .recommendReason(recommendReason)
                .createdAt(entity.getCreatedAt() != null ? entity.getCreatedAt().toString() : null)
                .updatedAt(entity.getUpdatedAt() != null ? entity.getUpdatedAt().toString() : null)
                .build();
    }
}
