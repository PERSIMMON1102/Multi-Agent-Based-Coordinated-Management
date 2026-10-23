package com.medical.agent.service;

import com.medical.agent.common.dto.RagEvidenceDTO;
import com.medical.agent.common.dto.RagQueryContextDTO;
import com.medical.agent.common.enums.DiseaseType;
import com.medical.agent.common.util.DiseaseAliasUtil;
import com.medical.agent.model.entity.FollowUpPlan;
import com.medical.agent.model.entity.HealthRecord;
import com.medical.agent.model.entity.KnowledgeChunk;
import com.medical.agent.model.entity.Patient;
import com.medical.agent.model.entity.RiskAssessment;
import com.medical.agent.model.repository.KnowledgeChunkRepository;
import com.medical.agent.service.cache.CacheNames;
import com.medical.agent.service.cache.AiCacheKeySupport;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.Period;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
public class RagRetrievalService {

    private static final Set<String> AUTHORITATIVE_SOURCE_HINTS = Set.of(
            "管理员批量导入", "管理员上传", "指南", "共识", "慢病", "居家管理", "健康教育"
    );

    private static final Set<String> OFF_TOPIC_SPECIALTY_TERMS = Set.of(
            "男科", "阳痿", "早泄", "勃起", "阴茎", "包皮", "前列腺",
            "妇科", "月经", "白带", "妊娠", "产科",
            "皮肤科", "湿疹", "痤疮", "荨麻疹",
            "眼科", "近视", "白内障",
            "耳鼻喉", "鼻炎", "中耳炎",
            "口腔", "牙龈", "牙周", "龋齿"
    );

    private final KnowledgeChunkRepository knowledgeChunkRepository;
    private final VectorStoreService vectorStoreService;
    private final RerankService rerankService;

    public RagRetrievalService(KnowledgeChunkRepository knowledgeChunkRepository,
                               VectorStoreService vectorStoreService,
                               RerankService rerankService) {
        this.knowledgeChunkRepository = knowledgeChunkRepository;
        this.vectorStoreService = vectorStoreService;
        this.rerankService = rerankService;
    }

    public RagQueryContextDTO buildContext(String scenario,
                                           String focus,
                                           Patient patient,
                                           List<RiskAssessment> assessments,
                                           List<FollowUpPlan> plans,
                                           HealthRecord latestRecord,
                                           String medicalHistorySummary) {
        List<String> diseaseKeys = extractDiseaseKeys(assessments, plans);
        StringBuilder query = new StringBuilder();
        query.append("场景：").append(scenario).append('\n');
        if (focus != null && !focus.isBlank()) {
            query.append("关注点：").append(focus).append('\n');
        }
        if (patient != null) {
            query.append("患者：").append(patient.getName() != null ? patient.getName() : "未命名患者");
            if (patient.getGender() != null) {
                query.append("，性别：").append(patient.getGender());
            }
            if (patient.getBirthDate() != null) {
                query.append("，年龄：").append(Period.between(patient.getBirthDate(), LocalDate.now()).getYears()).append("岁");
            }
            query.append('\n');
        }
        if (!diseaseKeys.isEmpty()) {
            query.append("相关病种：");
            for (String diseaseKey : diseaseKeys) {
                query.append(DiseaseAliasUtil.displayName(diseaseKey)).append(' ');
            }
            query.append('\n');
        }
        appendAssessmentSummary(query, assessments);
        appendPlanSummary(query, plans);
        appendRecordSummary(query, latestRecord);
        appendMedicalHistorySummary(query, medicalHistorySummary);

        return RagQueryContextDTO.builder()
                .patientId(patient != null ? patient.getId() : null)
                .scenario(scenario)
                .focus(focus)
                .queryText(query.toString())
                .diseaseKeys(diseaseKeys)
                .topicTags(inferTopicTags(focus, query.toString(), medicalHistorySummary, latestRecord))
                .audience(inferAudience(scenario))
                .structuredContext(buildStructuredContext(patient, latestRecord, medicalHistorySummary, diseaseKeys))
                .build();
    }

    public RagQueryContextDTO buildContext(String scenario,
                                           String focus,
                                           Patient patient,
                                           List<RiskAssessment> assessments,
                                           List<FollowUpPlan> plans,
                                           HealthRecord latestRecord) {
        return buildContext(scenario, focus, patient, assessments, plans, latestRecord, null);
    }

    @Cacheable(cacheNames = CacheNames.CHAT_EVIDENCE_RETRIEVAL,
            key = "@aiCacheKeyFactory.evidence(#context, #limit, @aiChatCacheVersionService.evidenceVersion(#context))",
            sync = true)
    public List<RagEvidenceDTO> retrieveKnowledge(RagQueryContextDTO context, int limit) {
        if (context == null) {
            return List.of();
        }

        int finalLimit = Math.max(1, Math.min(limit, 8));
        LinkedHashMap<String, RagEvidenceDTO> merged = new LinkedHashMap<>();

        for (String query : buildVectorQueries(context)) {
            List<RagEvidenceDTO> vectorMatches = vectorStoreService.searchKnowledge(
                    query,
                    context.getDiseaseKeys(),
                    Math.max(finalLimit * 4, 12)
            );
            for (RagEvidenceDTO match : vectorMatches) {
                mergeEvidence(merged, rerank(match, context));
            }
        }

        List<RagEvidenceDTO> lexicalMatches = fallbackSearch(context, finalLimit * 2);
        for (RagEvidenceDTO match : lexicalMatches) {
            mergeEvidence(merged, rerank(match, context));
        }
        List<RagEvidenceDTO> heuristicRanked = rankAndTrim(merged.values(), finalLimit * 4);
        List<RagEvidenceDTO> modelReranked = applyModelRerank(heuristicRanked, context, finalLimit * 3);
        return trimLowSignal(modelReranked, finalLimit);
    }

    public String evidenceCacheKey(RagQueryContextDTO context, int limit, String version) {
        return AiCacheKeySupport.evidence(context, limit, version);
    }

    public String renderEvidenceBlock(List<RagEvidenceDTO> references) {
        if (references == null || references.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("【可参考的专业资料】\n");
        int index = 1;
        for (RagEvidenceDTO item : references) {
            sb.append(index++).append(". ");
            sb.append(item.getTitle() != null && !item.getTitle().isBlank() ? item.getTitle() : "知识片段");
            if (item.getSection() != null && !item.getSection().isBlank()) {
                sb.append(" / ").append(item.getSection());
            }
            if (item.getSource() != null && !item.getSource().isBlank()) {
                sb.append(" / 来源：").append(item.getSource());
            }
            if (item.getPublishedAt() != null && !item.getPublishedAt().isBlank()) {
                sb.append(" / 发布：").append(item.getPublishedAt());
            }
            if (item.getContentSnippet() != null && !item.getContentSnippet().isBlank()) {
                sb.append("\n   摘要：").append(item.getContentSnippet());
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    private List<RagEvidenceDTO> fallbackSearch(RagQueryContextDTO context, int limit) {
        LinkedHashMap<Long, RagEvidenceDTO> merged = new LinkedHashMap<>();
        List<String> diseaseKeys = new ArrayList<>(DiseaseAliasUtil.normalizeKeys(context.getDiseaseKeys()));
        if (!diseaseKeys.contains(DiseaseAliasUtil.GENERAL)) {
            diseaseKeys.add(DiseaseAliasUtil.GENERAL);
        }

        List<String> keywords = extractFallbackKeywords(context);
        if (keywords.isEmpty()) {
            keywords = List.of(normalizeFocusKeyword(context.getFocus()));
        }

        for (String keyword : keywords) {
            for (String diseaseKey : diseaseKeys) {
                String diseaseLabel = DiseaseAliasUtil.knowledgeLabel(diseaseKey);
                List<KnowledgeChunk> chunks = knowledgeChunkRepository.search(
                        diseaseLabel,
                        keyword,
                        PageRequest.of(0, Math.max(limit, 6), Sort.by(Sort.Direction.DESC, "publishedAt", "createdAt", "id"))
                ).getContent();
                for (KnowledgeChunk chunk : chunks) {
                    RagEvidenceDTO candidate = toEvidence(chunk, lexicalScore(chunk, keyword));
                    RagEvidenceDTO existing = merged.get(chunk.getId());
                    if (existing == null || scoreOf(candidate) > scoreOf(existing)) {
                        merged.put(chunk.getId(), candidate);
                    }
                    if (merged.size() >= limit) {
                        return rankAndTrim(merged.values(), limit);
                    }
                }
            }
        }
        return rankAndTrim(merged.values(), limit);
    }

    private List<String> extractDiseaseKeys(List<RiskAssessment> assessments, List<FollowUpPlan> plans) {
        LinkedHashSet<String> keys = new LinkedHashSet<>();
        if (assessments != null) {
            for (RiskAssessment assessment : assessments) {
                DiseaseType diseaseType = assessment.getDiseaseType();
                if (diseaseType != null) {
                    keys.add(DiseaseAliasUtil.normalizeKey(diseaseType.name()));
                }
            }
        }
        if (plans != null) {
            for (FollowUpPlan plan : plans) {
                if (plan.getDiseaseType() != null) {
                    keys.add(DiseaseAliasUtil.normalizeKey(plan.getDiseaseType().name()));
                }
            }
        }
        return new ArrayList<>(keys);
    }

    private void appendAssessmentSummary(StringBuilder query, List<RiskAssessment> assessments) {
        if (assessments == null || assessments.isEmpty()) {
            return;
        }
        query.append("风险评估：\n");
        assessments.stream().limit(4).forEach(item -> {
            if (item.getDiseaseType() == null) {
                return;
            }
            query.append("- ").append(item.getDiseaseType().getDisplayName());
            if (item.getRiskLevel() != null) {
                query.append("：").append(item.getRiskLevel().getDisplayName());
            }
            if (item.getScore() != null) {
                query.append("（评分 ").append(String.format("%.1f", item.getScore())).append("）");
            }
            if (item.getAssessmentDetail() != null && !item.getAssessmentDetail().isBlank()) {
                query.append("，").append(item.getAssessmentDetail());
            }
            query.append('\n');
        });
    }

    private void appendPlanSummary(StringBuilder query, List<FollowUpPlan> plans) {
        if (plans == null || plans.isEmpty()) {
            return;
        }
        query.append("随访计划：\n");
        plans.stream().limit(2).forEach(plan -> {
            if (plan.getDiseaseType() != null) {
                query.append("- ").append(plan.getDiseaseType().getDisplayName());
            }
            if (plan.getFrequency() != null) {
                query.append(" / ").append(plan.getFrequency().getDisplayName());
            }
            if (plan.getNextFollowDate() != null) {
                query.append(" / 下次随访 ").append(plan.getNextFollowDate());
            }
            query.append('\n');
        });
    }

    private void appendRecordSummary(StringBuilder query, HealthRecord latestRecord) {
        if (latestRecord == null) {
            return;
        }
        query.append("最新健康指标：\n");
        appendMetric(query, "收缩压", latestRecord.getSystolicBp(), "mmHg");
        appendMetric(query, "舒张压", latestRecord.getDiastolicBp(), "mmHg");
        appendMetric(query, "BMI", latestRecord.getBmi(), "");
        appendMetric(query, "空腹血糖", latestRecord.getFastingGlucose(), "mmol/L");
        appendMetric(query, "HbA1c", latestRecord.getHba1c(), "%");
        appendMetric(query, "每周运动次数", latestRecord.getExerciseFrequency(), "次");
        appendMetric(query, "盐摄入评分", latestRecord.getSaltIntake(), "");
        appendMetric(query, "漏服率", latestRecord.getMedicationMissRate(), "%");
    }

    private void appendMedicalHistorySummary(StringBuilder query, String medicalHistorySummary) {
        if (medicalHistorySummary == null || medicalHistorySummary.isBlank()) {
            return;
        }
        query.append("病史档案：\n");
        for (String line : medicalHistorySummary.split("\\n")) {
            String normalized = line == null ? "" : line.trim();
            if (!normalized.isBlank()) {
                query.append("- ").append(normalized).append('\n');
            }
        }
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

    private String normalizeFocusKeyword(String focus) {
        if (focus == null || focus.isBlank()) {
            return null;
        }
        if (focus.contains("饮食")) {
            return "饮食";
        }
        if (focus.contains("运动")) {
            return "运动";
        }
        if (focus.contains("作息") || focus.contains("生活方式")) {
            return "生活";
        }
        if (focus.contains("报告")) {
            return null;
        }
        return focus.trim();
    }

    private List<String> buildVectorQueries(RagQueryContextDTO context) {
        LinkedHashSet<String> queries = new LinkedHashSet<>();
        addQueryCandidate(queries, context.getFocus());
        addQueryCandidate(queries, normalizeFocusKeyword(context.getFocus()));
        addQueryCandidate(queries, compressQuery(context.getFocus()));
        for (String topicQuery : buildTopicQueries(context)) {
            addQueryCandidate(queries, topicQuery);
        }
        addQueryCandidate(queries, compressQuery(context.getQueryText()));
        addQueryCandidate(queries, context.getQueryText());
        return new ArrayList<>(queries);
    }

    private void addQueryCandidate(LinkedHashSet<String> queries, String candidate) {
        if (candidate == null) {
            return;
        }
        String normalized = candidate.replaceAll("\\s+", " ").trim();
        if (normalized.isBlank()) {
            return;
        }
        if (normalized.length() > 120) {
            normalized = normalized.substring(0, 120);
        }
        queries.add(normalized);
    }

    private String compressQuery(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String normalized = text.replaceAll("[\\r\\n]+", " ")
                .replaceAll("场景：", " ")
                .replaceAll("关注点：", " ")
                .replaceAll("患者：", " ")
                .replaceAll("相关病种：", " ")
                .replaceAll("风险评估：", " ")
                .replaceAll("随访计划：", " ")
                .replaceAll("最新健康指标：", " ")
                .replaceAll("\\s+", " ")
                .trim();
        return normalized.length() > 120 ? normalized.substring(0, 120) : normalized;
    }

    private List<String> extractFallbackKeywords(RagQueryContextDTO context) {
        LinkedHashSet<String> keywords = new LinkedHashSet<>();
        for (String diseaseKey : DiseaseAliasUtil.normalizeKeys(context.getDiseaseKeys())) {
            keywords.add(DiseaseAliasUtil.displayName(diseaseKey));
            keywords.add(DiseaseAliasUtil.knowledgeLabel(diseaseKey));
        }
        if (context.getTopicTags() != null) {
            context.getTopicTags().stream()
                    .map(this::topicKeyword)
                    .filter(Objects::nonNull)
                    .forEach(keywords::add);
        }

        String text = context.getFocus();
        if (text == null || text.isBlank()) {
            text = context.getQueryText();
        }
        if (text == null || text.isBlank()) {
            return new ArrayList<>(keywords);
        }

        String normalized = text.replaceAll("[\\r\\n]+", " ")
                .replaceAll("[，。；、,:：？?！!（）()“”\"'`\\[\\]{}]+", " ")
                .replaceAll("\\s+", " ")
                .trim();

        for (String token : normalized.split(" ")) {
            String word = token.trim();
            if (word.length() < 2) {
                continue;
            }
            if (word.length() > 16) {
                continue;
            }
            keywords.add(word);
            if (word.contains("高血压")) keywords.add("血压");
            if (word.contains("糖尿病")) keywords.add("血糖");
            if (word.contains("服药")) keywords.add("规律服药");
            if (word.contains("记录")) keywords.add("记录");
            if (word.contains("监测")) keywords.add("监测");
        }

        return new ArrayList<>(keywords);
    }

    private double lexicalScore(KnowledgeChunk chunk, String keyword) {
        double score = 0.2d;
        if (keyword == null || keyword.isBlank()) {
            return score;
        }
        String candidate = keyword.toLowerCase(Locale.ROOT);
        if (containsIgnoreCase(chunk.getTitle(), candidate)) score += 0.3d;
        if (containsIgnoreCase(chunk.getSection(), candidate)) score += 0.2d;
        if (containsIgnoreCase(chunk.getContent(), candidate)) score += 0.4d;
        if (containsIgnoreCase(chunk.getSource(), candidate)) score += 0.1d;
        return score;
    }

    private RagEvidenceDTO rerank(RagEvidenceDTO item, RagQueryContextDTO context) {
        if (item == null) {
            return null;
        }

        double score = scoreOf(item);
        Set<String> diseaseKeys = new LinkedHashSet<>(DiseaseAliasUtil.normalizeKeys(context.getDiseaseKeys()));
        List<String> keywords = extractFallbackKeywords(context);
        String searchable = joinText(item.getTitle(), item.getSection(), item.getSource(), item.getContentSnippet());

        if (!diseaseKeys.isEmpty()) {
            String itemDiseaseKey = DiseaseAliasUtil.normalizeKey(item.getDiseaseKey());
            if (diseaseKeys.contains(itemDiseaseKey) && !DiseaseAliasUtil.GENERAL.equals(itemDiseaseKey)) {
                score += 0.45d;
            } else if (DiseaseAliasUtil.GENERAL.equals(itemDiseaseKey)) {
                score += 0.1d;
            }
        }

        int keywordHits = 0;
        for (String keyword : keywords) {
            if (keyword == null || keyword.isBlank()) {
                continue;
            }
            if (searchable.contains(keyword.toLowerCase(Locale.ROOT))) {
                keywordHits++;
            }
        }
        score += Math.min(keywordHits, 3) * 0.12d;

        if (matchesTopic(item, context)) {
            score += 0.28d;
        }
        if (matchesAudience(item, context)) {
            score += 0.1d;
        }

        if (containsAny(searchable, AUTHORITATIVE_SOURCE_HINTS)) {
            score += 0.35d;
        }
        if (isDatasetSource(item)) {
            score -= 0.2d;
        }
        if (looksOffTopic(item, context)) {
            score -= 0.8d;
        }
        if (isWeakTitleSectionMatch(item, keywords)) {
            score -= 0.18d;
        }

        return RagEvidenceDTO.builder()
                .sourceType(item.getSourceType())
                .refId(item.getRefId())
                .videoId(item.getVideoId())
                .diseaseKey(item.getDiseaseKey())
                .title(item.getTitle())
                .section(item.getSection())
                .source(item.getSource())
                .publishedAt(item.getPublishedAt())
                .contentSnippet(item.getContentSnippet())
                .score(score)
                .build();
    }

    private boolean looksOffTopic(RagEvidenceDTO item, RagQueryContextDTO context) {
        String searchable = joinText(item.getTitle(), item.getSection(), item.getSource(), item.getContentSnippet());
        if (!containsAny(searchable, OFF_TOPIC_SPECIALTY_TERMS)) {
            return false;
        }

        List<String> keywords = extractFallbackKeywords(context);
        for (String keyword : keywords) {
            if (keyword != null && !keyword.isBlank() && containsAny(keyword.toLowerCase(Locale.ROOT), OFF_TOPIC_SPECIALTY_TERMS)) {
                return false;
            }
        }
        return true;
    }

    private boolean isDatasetSource(RagEvidenceDTO item) {
        return joinText(item.getSource(), item.getTitle()).contains("chinese-medical-dialogue-data");
    }

    private boolean isWeakTitleSectionMatch(RagEvidenceDTO item, List<String> keywords) {
        if (!isDatasetSource(item)) {
            return false;
        }
        String titleSection = joinText(item.getTitle(), item.getSection());
        for (String keyword : keywords) {
            if (keyword == null || keyword.isBlank()) {
                continue;
            }
            if (titleSection.contains(keyword.toLowerCase(Locale.ROOT))) {
                return false;
            }
        }
        return true;
    }

    private boolean matchesTopic(RagEvidenceDTO item, RagQueryContextDTO context) {
        if (item == null || context == null || context.getTopicTags() == null || context.getTopicTags().isEmpty()) {
            return false;
        }
        String searchable = joinText(item.getTitle(), item.getSection(), item.getSource(), item.getContentSnippet());
        for (String topicTag : context.getTopicTags()) {
            String keyword = topicKeyword(topicTag);
            if (keyword != null && searchable.contains(keyword.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private boolean matchesAudience(RagEvidenceDTO item, RagQueryContextDTO context) {
        if (item == null || context == null || context.getAudience() == null || context.getAudience().isBlank()) {
            return false;
        }
        String searchable = joinText(item.getTitle(), item.getSection(), item.getSource(), item.getContentSnippet());
        if ("doctor".equalsIgnoreCase(context.getAudience())) {
            return searchable.contains("指南") || searchable.contains("共识") || searchable.contains("评估");
        }
        if ("patient".equalsIgnoreCase(context.getAudience())) {
            return searchable.contains("教育") || searchable.contains("居家") || searchable.contains("饮食") || searchable.contains("运动");
        }
        return false;
    }

    private void mergeEvidence(Map<String, RagEvidenceDTO> merged, RagEvidenceDTO candidate) {
        if (candidate == null) {
            return;
        }
        String key = buildEvidenceKey(candidate);
        RagEvidenceDTO existing = merged.get(key);
        if (existing == null || scoreOf(candidate) > scoreOf(existing)) {
            merged.put(key, candidate);
        }
    }

    private double scoreOf(RagEvidenceDTO item) {
        return item != null && item.getScore() != null ? item.getScore() : 0D;
    }

    private List<RagEvidenceDTO> trimLowSignal(List<RagEvidenceDTO> ranked, int limit) {
        if (ranked == null || ranked.isEmpty()) {
            return List.of();
        }

        double best = scoreOf(ranked.get(0));
        double floor = best >= 1.0d ? best - 0.35d : Math.max(best * 0.72d, 0.45d);

        List<RagEvidenceDTO> filtered = ranked.stream()
                .filter(item -> scoreOf(item) >= floor)
                .limit(limit)
                .toList();

        if (!filtered.isEmpty()) {
            return filtered;
        }
        return ranked.stream().limit(limit).toList();
    }

    private List<RagEvidenceDTO> applyModelRerank(List<RagEvidenceDTO> ranked,
                                                  RagQueryContextDTO context,
                                                  int limit) {
        if (ranked == null || ranked.isEmpty()) {
            return List.of();
        }

        List<RagEvidenceDTO> candidates = ranked.stream()
                .limit(Math.max(limit, 12))
                .toList();
        Map<Integer, Double> modelScores = rerankService.rerank(
                buildRerankQuery(context),
                candidates.stream().map(this::toRerankDocument).toList()
        );
        if (modelScores.isEmpty()) {
            return candidates.stream().limit(limit).toList();
        }

        List<RagEvidenceDTO> reranked = new ArrayList<>(candidates.size());
        for (int i = 0; i < candidates.size(); i++) {
            RagEvidenceDTO item = candidates.get(i);
            double modelScore = Math.max(0D, modelScores.getOrDefault(i, 0D));
            reranked.add(RagEvidenceDTO.builder()
                    .sourceType(item.getSourceType())
                    .refId(item.getRefId())
                    .videoId(item.getVideoId())
                    .diseaseKey(item.getDiseaseKey())
                    .title(item.getTitle())
                    .section(item.getSection())
                    .source(item.getSource())
                    .publishedAt(item.getPublishedAt())
                    .contentSnippet(item.getContentSnippet())
                    .score(scoreOf(item) + modelScore * 1.1d)
                    .build());
        }
        return rankAndTrim(reranked, limit);
    }

    private String buildRerankQuery(RagQueryContextDTO context) {
        if (context == null) {
            return "";
        }
        if (context.getFocus() != null && !context.getFocus().isBlank()) {
            return context.getFocus().trim();
        }
        return context.getQueryText() != null ? context.getQueryText().trim() : "";
    }

    private String toRerankDocument(RagEvidenceDTO item) {
        if (item == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        appendPart(builder, item.getTitle());
        appendPart(builder, item.getSection());
        appendPart(builder, item.getSource());
        appendPart(builder, item.getDiseaseKey());
        appendPart(builder, item.getContentSnippet());
        return builder.toString();
    }

    private void appendPart(StringBuilder builder, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        if (builder.length() > 0) {
            builder.append('\n');
        }
        builder.append(value.trim());
    }

    private boolean containsAny(String text, Collection<String> needles) {
        if (text == null || text.isBlank() || needles == null || needles.isEmpty()) {
            return false;
        }
        for (String needle : needles) {
            if (needle != null && !needle.isBlank() && text.contains(needle.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private String joinText(String... parts) {
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (part != null && !part.isBlank()) {
                if (builder.length() > 0) {
                    builder.append(' ');
                }
                builder.append(part.toLowerCase(Locale.ROOT));
            }
        }
        return builder.toString();
    }

    private boolean containsIgnoreCase(String text, String keyword) {
        return text != null && keyword != null && text.toLowerCase(Locale.ROOT).contains(keyword);
    }

    private List<RagEvidenceDTO> rankAndTrim(Collection<RagEvidenceDTO> values, int limit) {
        return values.stream()
                .sorted(Comparator.comparingDouble((RagEvidenceDTO item) ->
                        item.getScore() != null ? item.getScore() : 0D).reversed())
                .limit(limit)
                .toList();
    }

    private List<RagEvidenceDTO> deduplicate(Collection<RagEvidenceDTO> values, int limit) {
        LinkedHashMap<String, RagEvidenceDTO> dedup = new LinkedHashMap<>();
        if (values != null) {
            for (RagEvidenceDTO item : values) {
                String key = buildEvidenceKey(item);
                dedup.putIfAbsent(key, item);
                if (dedup.size() >= limit) {
                    break;
                }
            }
        }
        return new ArrayList<>(dedup.values());
    }

    private String buildEvidenceKey(RagEvidenceDTO item) {
        return item.getSourceType() + "-" + item.getRefId() + "-" + item.getVideoId();
    }

    private RagEvidenceDTO toEvidence(KnowledgeChunk chunk, double score) {
        return RagEvidenceDTO.builder()
                .sourceType("KNOWLEDGE")
                .refId(chunk.getId())
                .diseaseKey(DiseaseAliasUtil.normalizeKey(chunk.getDisease()))
                .title(chunk.getTitle())
                .section(chunk.getSection())
                .source(chunk.getSource())
                .publishedAt(chunk.getPublishedAt() != null ? chunk.getPublishedAt().toString() : null)
                .contentSnippet(toSnippet(chunk.getContent()))
                .score(score)
                .build();
    }

    private String toSnippet(String content) {
        if (content == null) {
            return null;
        }
        String normalized = content.replaceAll("\\s+", " ").trim();
        return normalized.length() <= 220 ? normalized : normalized.substring(0, 220) + "...";
    }

    private List<String> inferTopicTags(String focus,
                                        String queryText,
                                        String medicalHistorySummary,
                                        HealthRecord latestRecord) {
        LinkedHashSet<String> topics = new LinkedHashSet<>();
        String text = joinText(focus, queryText, medicalHistorySummary);
        if (containsAny(text, List.of("饮食", "主食", "盐", "控盐", "血糖饮食", "低糖"))) {
            topics.add("diet");
        }
        if (containsAny(text, List.of("运动", "锻炼", "步行", "走路", "活动"))) {
            topics.add("exercise");
        }
        if (containsAny(text, List.of("药", "服药", "用药", "漏服", "胰岛素"))) {
            topics.add("medication");
        }
        if (containsAny(text, List.of("血压", "血糖", "糖化", "指标", "化验", "bmi"))) {
            topics.add("metrics");
        }
        if (containsAny(text, List.of("复查", "复诊", "随访", "下次"))) {
            topics.add("followup");
        }
        if (containsAny(text, List.of("胸痛", "气短", "头晕", "危险", "急诊", "就医"))) {
            topics.add("risk");
            topics.add("safety");
        }
        if (latestRecord != null && latestRecord.getMedicationMissRate() != null && latestRecord.getMedicationMissRate() > 0D) {
            topics.add("medication");
        }
        if (topics.isEmpty()) {
            topics.add("general");
        }
        return new ArrayList<>(topics);
    }

    private String inferAudience(String scenario) {
        if (scenario == null || scenario.isBlank()) {
            return "general";
        }
        if (scenario.contains("宣教")) {
            return "patient";
        }
        if (scenario.contains("医生") || scenario.contains("临床")) {
            return "doctor";
        }
        return "general";
    }

    private Map<String, Object> buildStructuredContext(Patient patient,
                                                       HealthRecord latestRecord,
                                                       String medicalHistorySummary,
                                                       List<String> diseaseKeys) {
        Map<String, Object> structured = new LinkedHashMap<>();
        structured.put("patientId", patient == null ? null : patient.getId());
        structured.put("gender", patient == null ? null : patient.getGender());
        structured.put("diseaseKeys", diseaseKeys);
        structured.put("medicalHistorySummary", medicalHistorySummary);
        if (latestRecord != null) {
            structured.put("systolicBp", latestRecord.getSystolicBp());
            structured.put("diastolicBp", latestRecord.getDiastolicBp());
            structured.put("fastingGlucose", latestRecord.getFastingGlucose());
            structured.put("hba1c", latestRecord.getHba1c());
            structured.put("bmi", latestRecord.getBmi());
            structured.put("exerciseFrequency", latestRecord.getExerciseFrequency());
            structured.put("medicationMissRate", latestRecord.getMedicationMissRate());
        }
        return structured;
    }

    private List<String> buildTopicQueries(RagQueryContextDTO context) {
        if (context == null || context.getTopicTags() == null || context.getTopicTags().isEmpty()) {
            return List.of();
        }
        List<String> queries = new ArrayList<>();
        for (String topicTag : context.getTopicTags()) {
            String keyword = topicKeyword(topicTag);
            if (keyword != null) {
                String diseasePart = context.getDiseaseKeys() == null || context.getDiseaseKeys().isEmpty()
                        ? ""
                        : DiseaseAliasUtil.displayName(context.getDiseaseKeys().get(0)) + " ";
                queries.add((diseasePart + keyword + " 指南 建议").trim());
            }
        }
        return queries;
    }

    private String topicKeyword(String topicTag) {
        if (topicTag == null || topicTag.isBlank()) {
            return null;
        }
        return switch (topicTag) {
            case "diet" -> "饮食";
            case "exercise" -> "运动";
            case "medication" -> "用药";
            case "metrics" -> "指标";
            case "monitoring" -> "监测";
            case "risk" -> "风险";
            case "followup" -> "随访";
            case "lifestyle" -> "生活方式";
            case "safety" -> "禁忌";
            default -> null;
        };
    }
}
