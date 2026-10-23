package com.medical.agent.service;

import com.medical.agent.common.dto.RagEvidenceDTO;
import com.medical.agent.common.dto.RagQueryContextDTO;
import com.medical.agent.service.reference.agent.ReferenceSelectionSupervisorService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class ReferenceCurationService {

    private static final Set<String> HIDDEN_SOURCE_HINTS = Set.of(
            "chinese-medical-dialogue-data",
            "vector-smoke-test",
            "admin-live-index-test",
            "smoke",
            "dialogue_import_smoke",
            "dialogue_upload_smoke"
    );

    private static final Map<String, List<String>> TOPIC_KEYWORDS = new LinkedHashMap<>();

    static {
        TOPIC_KEYWORDS.put("diet", List.of("饮食", "食物", "主食", "水果", "蔬菜", "早餐", "午餐", "晚餐", "加餐", "盐", "控盐", "糖", "脂肪"));
        TOPIC_KEYWORDS.put("exercise", List.of("运动", "锻炼", "步行", "走路", "跑步", "有氧", "力量", "散步", "强度", "每周"));
        TOPIC_KEYWORDS.put("lifestyle", List.of("作息", "睡眠", "生活方式", "情绪", "压力", "戒烟", "饮酒", "监测", "记录", "用药提醒"));
        TOPIC_KEYWORDS.put("patient_report", List.of("风险", "建议", "随访", "生活方式", "用药", "警示", "血压", "血糖", "体重"));
        TOPIC_KEYWORDS.put("doctor_report", List.of("风险分层", "证据", "干预", "随访", "优先级", "临床", "评估", "指标", "管理"));
    }

    private final ReferenceSelectionSupervisorService referenceSelectionSupervisorService;

    public ReferenceCurationService(ReferenceSelectionSupervisorService referenceSelectionSupervisorService) {
        this.referenceSelectionSupervisorService = referenceSelectionSupervisorService;
    }

    public List<RagEvidenceDTO> curateEducationPromptReferences(RagQueryContextDTO context,
                                                                String topicKey,
                                                                List<RagEvidenceDTO> candidates,
                                                                int limit) {
        return curateByRules(candidates, context, List.of(topicKey), null, limit, false);
    }

    public List<RagEvidenceDTO> curateEducationDisplayReferences(RagQueryContextDTO context,
                                                                 String topicKey,
                                                                 String sectionContent,
                                                                 List<RagEvidenceDTO> candidates,
                                                                 int limit) {
        return curateByRules(candidates, context, List.of(topicKey), sectionContent, limit, true);
    }

    public List<RagEvidenceDTO> curateReportPromptReferences(RagQueryContextDTO context,
                                                             String topicKey,
                                                             List<RagEvidenceDTO> candidates,
                                                             int limit) {
        return curateByRules(candidates, context, List.of(topicKey), null, limit, false);
    }

    public List<RagEvidenceDTO> curateReportDisplayReferences(RagQueryContextDTO context,
                                                              String topicKey,
                                                              String reportContent,
                                                              List<RagEvidenceDTO> candidates,
                                                              int limit,
                                                              boolean useJudge) {
        List<RagEvidenceDTO> filtered = curateByRules(candidates, context, List.of(topicKey), reportContent, Math.max(limit + 1, limit), true);
        if (filtered.isEmpty()) {
            return List.of();
        }
        if (useJudge) {
            List<RagEvidenceDTO> judged = referenceSelectionSupervisorService.judgeContentDisplayReferences(
                    topicKey,
                    reportContent,
                    filtered,
                    limit
            );
            if (judged != null && !judged.isEmpty()) {
                return judged.stream().limit(limit).toList();
            }
        }
        return filtered.stream().limit(limit).toList();
    }

    public List<RagEvidenceDTO> mergeDistinct(int limit, List<RagEvidenceDTO>... groups) {
        LinkedHashMap<String, RagEvidenceDTO> merged = new LinkedHashMap<>();
        for (List<RagEvidenceDTO> group : groups) {
            if (group == null) {
                continue;
            }
            for (RagEvidenceDTO item : group) {
                String key = buildDocumentKey(item);
                RagEvidenceDTO existing = merged.get(key);
                if (existing == null || referenceScore(item) > referenceScore(existing)) {
                    merged.put(key, item);
                }
            }
        }
        return merged.values().stream()
                .sorted(Comparator.comparingDouble(this::referenceScore).reversed())
                .limit(limit)
                .toList();
    }

    private List<RagEvidenceDTO> curateByRules(List<RagEvidenceDTO> candidates,
                                               RagQueryContextDTO context,
                                               List<String> topicKeys,
                                               String generatedContent,
                                               int limit,
                                               boolean requireContentAlignment) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }

        Set<String> diseaseKeys = normalizeDiseaseKeys(context);
        boolean hasSpecificDisease = diseaseKeys.stream().anyMatch(key -> !"general".equals(key));
        Map<String, RagEvidenceDTO> deduped = new LinkedHashMap<>();
        List<RagEvidenceDTO> sorted = candidates.stream()
                .sorted(Comparator.comparingDouble(this::referenceScore).reversed())
                .toList();

        for (RagEvidenceDTO item : sorted) {
            if (!isDisplayableSource(item)) {
                continue;
            }
            if (hasSpecificDisease && isConflictingDisease(item, diseaseKeys)) {
                continue;
            }
            if (!topicKeys.isEmpty() && !isTopicAligned(item, topicKeys, requireContentAlignment)) {
                continue;
            }
            if (requireContentAlignment && !isContentAligned(item, generatedContent, topicKeys)) {
                continue;
            }

            String key = buildDocumentKey(item);
            RagEvidenceDTO existing = deduped.get(key);
            if (existing == null || comparePriority(item, existing, diseaseKeys) < 0) {
                deduped.put(key, item);
            }
        }

        List<RagEvidenceDTO> curated = deduped.values().stream()
                .sorted((left, right) -> comparePriority(left, right, diseaseKeys))
                .limit(limit)
                .toList();
        if (!curated.isEmpty()) {
            return curated;
        }
        return sorted.stream()
                .filter(this::isDisplayableSource)
                .limit(Math.min(limit, sorted.size()))
                .toList();
    }

    private boolean isDisplayableSource(RagEvidenceDTO item) {
        String searchable = joinText(item.getSource(), item.getTitle(), item.getSection());
        for (String hint : HIDDEN_SOURCE_HINTS) {
            if (searchable.contains(hint.toLowerCase(Locale.ROOT))) {
                return false;
            }
        }
        return true;
    }

    private boolean isConflictingDisease(RagEvidenceDTO item, Set<String> diseaseKeys) {
        if (item == null || item.getDiseaseKey() == null || diseaseKeys.isEmpty()) {
            return false;
        }
        String diseaseKey = item.getDiseaseKey().trim().toLowerCase(Locale.ROOT);
        return !"general".equals(diseaseKey) && !diseaseKeys.contains(diseaseKey);
    }

    private boolean isTopicAligned(RagEvidenceDTO item, List<String> topicKeys, boolean strict) {
        if (topicKeys == null || topicKeys.isEmpty()) {
            return true;
        }
        String strongText = joinText(item.getTitle(), item.getSection(), item.getSource());
        String weakText = joinText(item.getContentSnippet());
        int strongHits = countTopicHits(strongText, topicKeys);
        int weakHits = countTopicHits(weakText, topicKeys);
        if (strongHits > 0) {
            return true;
        }
        return !strict && weakHits >= 2;
    }

    private boolean isContentAligned(RagEvidenceDTO item, String generatedContent, List<String> topicKeys) {
        if (generatedContent == null || generatedContent.isBlank()) {
            return true;
        }
        String contentText = generatedContent.toLowerCase(Locale.ROOT);
        String titleSection = joinText(item.getTitle(), item.getSection());
        if (!titleSection.isBlank()) {
            for (String token : titleSection.split("\\s+")) {
                String word = token.trim();
                if (word.length() >= 4 && contentText.contains(word)) {
                    return true;
                }
            }
        }
        return countTopicHits(contentText, topicKeys) > 0 && isTopicAligned(item, topicKeys, true);
    }

    private int countTopicHits(String text, List<String> topicKeys) {
        if (text == null || text.isBlank() || topicKeys == null || topicKeys.isEmpty()) {
            return 0;
        }
        int hits = 0;
        for (String topicKey : topicKeys) {
            List<String> keywords = TOPIC_KEYWORDS.getOrDefault(topicKey, List.of());
            if (keywords.stream().anyMatch(keyword -> text.contains(keyword.toLowerCase(Locale.ROOT)))) {
                hits++;
            }
        }
        return hits;
    }

    private Set<String> normalizeDiseaseKeys(RagQueryContextDTO context) {
        LinkedHashSet<String> keys = new LinkedHashSet<>();
        if (context == null || context.getDiseaseKeys() == null) {
            return keys;
        }
        for (String key : context.getDiseaseKeys()) {
            if (key != null && !key.isBlank()) {
                keys.add(key.trim().toLowerCase(Locale.ROOT));
            }
        }
        return keys;
    }

    private int comparePriority(RagEvidenceDTO left, RagEvidenceDTO right, Set<String> diseaseKeys) {
        double leftWeight = referenceScore(left) + diseaseWeight(left, diseaseKeys);
        double rightWeight = referenceScore(right) + diseaseWeight(right, diseaseKeys);
        return Double.compare(rightWeight, leftWeight);
    }

    private double diseaseWeight(RagEvidenceDTO item, Set<String> diseaseKeys) {
        if (item == null || item.getDiseaseKey() == null) {
            return 0D;
        }
        String key = item.getDiseaseKey().trim().toLowerCase(Locale.ROOT);
        if (diseaseKeys.contains(key) && !"general".equals(key)) {
            return 0.4D;
        }
        if ("general".equals(key)) {
            return -0.2D;
        }
        return -0.4D;
    }

    private double referenceScore(RagEvidenceDTO item) {
        if (item == null || item.getScore() == null) {
            return 0D;
        }
        return item.getScore();
    }

    private String buildDocumentKey(RagEvidenceDTO item) {
        return joinText(item.getSourceType(), item.getTitle(), item.getSource(), item.getPublishedAt());
    }

    private String joinText(String... parts) {
        return Arrays.stream(parts)
                .filter(part -> part != null && !part.isBlank())
                .map(part -> part.toLowerCase(Locale.ROOT))
                .collect(Collectors.joining(" "));
    }

    private List<RagEvidenceDTO> mergeDistinct(int limit, Collection<RagEvidenceDTO> items) {
        LinkedHashMap<String, RagEvidenceDTO> merged = new LinkedHashMap<>();
        for (RagEvidenceDTO item : items) {
            if (item == null) {
                continue;
            }
            String key = buildDocumentKey(item);
            RagEvidenceDTO existing = merged.get(key);
            if (existing == null || referenceScore(item) > referenceScore(existing)) {
                merged.put(key, item);
            }
        }
        return merged.values().stream()
                .sorted(Comparator.comparingDouble(this::referenceScore).reversed())
                .limit(limit)
                .toList();
    }
}
