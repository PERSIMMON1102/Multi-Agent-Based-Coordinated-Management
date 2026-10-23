package com.medical.agent.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.medical.agent.common.util.DiseaseAliasUtil;
import com.medical.agent.model.entity.HealthRecord;
import com.medical.agent.model.entity.KnowledgeChunk;
import com.medical.agent.model.entity.KnowledgeGraphEdge;
import com.medical.agent.model.entity.KnowledgeGraphNode;
import com.medical.agent.model.entity.RiskAssessment;
import com.medical.agent.model.entity.VideoResource;
import com.medical.agent.model.repository.KnowledgeChunkRepository;
import com.medical.agent.model.repository.KnowledgeGraphEdgeRepository;
import com.medical.agent.model.repository.KnowledgeGraphNodeRepository;
import com.medical.agent.model.repository.VideoResourceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class KnowledgeGraphService {

    public static final String NODE_DISEASE = "DISEASE";
    public static final String NODE_VIDEO = "VIDEO";
    public static final String NODE_KNOWLEDGE_DOC = "KNOWLEDGE_DOC";
    public static final String NODE_TOPIC = "TOPIC";

    public static final String REL_ABOUT_DISEASE = "ABOUT_DISEASE";
    public static final String REL_HAS_TOPIC = "HAS_TOPIC";
    public static final String REL_SUPPORTED_BY_DOC = "SUPPORTED_BY_DOC";

    private static final String TOPIC_DIET = "DIET";
    private static final String TOPIC_EXERCISE = "EXERCISE";
    private static final String TOPIC_LIFESTYLE = "LIFESTYLE";
    private static final String TOPIC_MEDICATION = "MEDICATION";
    private static final String TOPIC_MONITORING = "MONITORING";
    private static final String TOPIC_FOLLOW_UP = "FOLLOW_UP";
    private static final String TOPIC_WARNING = "WARNING";

    private final KnowledgeGraphNodeRepository nodeRepository;
    private final KnowledgeGraphEdgeRepository edgeRepository;
    private final VideoResourceRepository videoResourceRepository;
    private final KnowledgeChunkRepository knowledgeChunkRepository;
    private final ObjectMapper objectMapper;

    public KnowledgeGraphService(KnowledgeGraphNodeRepository nodeRepository,
                                 KnowledgeGraphEdgeRepository edgeRepository,
                                 VideoResourceRepository videoResourceRepository,
                                 KnowledgeChunkRepository knowledgeChunkRepository,
                                 ObjectMapper objectMapper) {
        this.nodeRepository = nodeRepository;
        this.edgeRepository = edgeRepository;
        this.videoResourceRepository = videoResourceRepository;
        this.knowledgeChunkRepository = knowledgeChunkRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public Map<String, Object> rebuildGraph() {
        edgeRepository.deleteAllInBatch();
        nodeRepository.deleteAllInBatch();

        GraphBuilder builder = new GraphBuilder();
        List<VideoResource> videos = videoResourceRepository.findAll();
        List<KnowledgeChunk> chunks = knowledgeChunkRepository.findAll();

        for (VideoResource video : videos) {
            registerVideo(builder, video);
        }
        registerKnowledgeDocs(builder, chunks);
        linkVideoAndDocs(builder);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("message", "知识图谱重建完成");
        result.put("nodeCount", nodeRepository.count());
        result.put("edgeCount", edgeRepository.count());
        result.put("videos", videos.size());
        result.put("knowledgeChunks", chunks.size());
        result.put("diseaseNodes", nodeRepository.countByNodeType(NODE_DISEASE));
        result.put("videoNodes", nodeRepository.countByNodeType(NODE_VIDEO));
        result.put("knowledgeDocNodes", nodeRepository.countByNodeType(NODE_KNOWLEDGE_DOC));
        result.put("topicNodes", nodeRepository.countByNodeType(NODE_TOPIC));
        return result;
    }

    public Map<String, Object> getStatus() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("available", true);
        result.put("nodeCount", nodeRepository.count());
        result.put("edgeCount", edgeRepository.count());
        result.put("nodeTypes", Map.of(
                NODE_DISEASE, nodeRepository.countByNodeType(NODE_DISEASE),
                NODE_VIDEO, nodeRepository.countByNodeType(NODE_VIDEO),
                NODE_KNOWLEDGE_DOC, nodeRepository.countByNodeType(NODE_KNOWLEDGE_DOC),
                NODE_TOPIC, nodeRepository.countByNodeType(NODE_TOPIC)
        ));
        result.put("relationTypes", Map.of(
                REL_ABOUT_DISEASE, edgeRepository.countByRelationType(REL_ABOUT_DISEASE),
                REL_HAS_TOPIC, edgeRepository.countByRelationType(REL_HAS_TOPIC),
                REL_SUPPORTED_BY_DOC, edgeRepository.countByRelationType(REL_SUPPORTED_BY_DOC)
        ));
        result.put("built", nodeRepository.count() > 0 && edgeRepository.count() > 0);
        return result;
    }

    public Map<String, Object> getVisualization(String nodeType, String keyword, Integer limit) {
        int finalLimit = normalizeVisualizationLimit(limit);
        String normalizedType = safeText(nodeType).toUpperCase(Locale.ROOT);
        String normalizedKeyword = safeText(keyword).toLowerCase(Locale.ROOT);

        List<KnowledgeGraphNode> allNodes = nodeRepository.findAll();
        List<KnowledgeGraphEdge> allEdges = edgeRepository.findAll();

        List<KnowledgeGraphNode> seedNodes = allNodes.stream()
                .filter(node -> normalizedType.isBlank() || normalizedType.equalsIgnoreCase(node.getNodeType()))
                .filter(node -> normalizedKeyword.isBlank() || matchesKeyword(node, normalizedKeyword))
                .sorted(Comparator
                        .comparing(KnowledgeGraphNode::getNodeType, Comparator.nullsLast(String::compareTo))
                        .thenComparing(KnowledgeGraphNode::getDisplayName, Comparator.nullsLast(String::compareTo)))
                .limit(normalizedKeyword.isBlank() && normalizedType.isBlank() ? finalLimit : Math.max(12, finalLimit / 2))
                .toList();

        if (seedNodes.isEmpty() && !allNodes.isEmpty()) {
            seedNodes = allNodes.stream()
                    .sorted(Comparator
                            .comparing(KnowledgeGraphNode::getNodeType, Comparator.nullsLast(String::compareTo))
                            .thenComparing(KnowledgeGraphNode::getDisplayName, Comparator.nullsLast(String::compareTo)))
                    .limit(finalLimit)
                    .toList();
        }

        LinkedHashMap<Long, KnowledgeGraphNode> selectedNodes = new LinkedHashMap<>();
        for (KnowledgeGraphNode node : seedNodes) {
            selectedNodes.put(node.getId(), node);
        }

        if (selectedNodes.size() < finalLimit) {
            for (KnowledgeGraphEdge edge : allEdges) {
                if (selectedNodes.size() >= finalLimit) {
                    break;
                }
                boolean touchesSeed = selectedNodes.containsKey(edge.getSourceNodeId()) || selectedNodes.containsKey(edge.getTargetNodeId());
                if (!touchesSeed) {
                    continue;
                }
                findNode(allNodes, edge.getSourceNodeId()).ifPresent(node -> {
                    if (selectedNodes.size() < finalLimit) {
                        selectedNodes.putIfAbsent(node.getId(), node);
                    }
                });
                findNode(allNodes, edge.getTargetNodeId()).ifPresent(node -> {
                    if (selectedNodes.size() < finalLimit) {
                        selectedNodes.putIfAbsent(node.getId(), node);
                    }
                });
            }
        }

        Set<Long> selectedNodeIds = selectedNodes.keySet();
        List<KnowledgeGraphEdge> selectedEdges = allEdges.stream()
                .filter(edge -> selectedNodeIds.contains(edge.getSourceNodeId()) && selectedNodeIds.contains(edge.getTargetNodeId()))
                .limit(Math.max(120, finalLimit * 4L))
                .toList();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", getStatus());
        result.put("filters", Map.of(
                "nodeType", normalizedType,
                "keyword", normalizedKeyword,
                "limit", finalLimit
        ));
        result.put("categories", selectedNodes.values().stream()
                .map(KnowledgeGraphNode::getNodeType)
                .filter(Objects::nonNull)
                .distinct()
                .map(type -> Map.of(
                        "name", type,
                        "label", nodeTypeLabel(type),
                        "color", nodeTypeColor(type)
                ))
                .toList());
        result.put("nodes", selectedNodes.values().stream()
                .map(node -> Map.of(
                        "id", node.getId(),
                        "nodeType", safeText(node.getNodeType()),
                        "nodeTypeLabel", nodeTypeLabel(node.getNodeType()),
                        "nodeKey", safeText(node.getNodeKey()),
                        "name", safeDisplayName(node.getDisplayName(), safeText(node.getNodeKey())),
                        "displayName", safeDisplayName(node.getDisplayName(), safeText(node.getNodeKey())),
                        "sourceType", safeText(node.getSourceType()),
                        "sourceRefId", safeText(node.getSourceRefId()),
                        "symbolSize", nodeSymbolSize(node.getNodeType()),
                        "itemStyle", Map.of("color", nodeTypeColor(node.getNodeType()))
                ))
                .toList());
        result.put("edges", selectedEdges.stream()
                .map(edge -> Map.of(
                        "id", edge.getId(),
                        "source", edge.getSourceNodeId(),
                        "target", edge.getTargetNodeId(),
                        "relationType", safeText(edge.getRelationType()),
                        "relationLabel", relationLabel(edge.getRelationType()),
                        "weight", edge.getWeight(),
                        "evidenceText", safeText(edge.getEvidenceText())
                ))
                .toList());
        return result;
    }

    public Map<Long, RecommendationScore> scoreVideosForPatient(Collection<Long> candidateVideoIds,
                                                                Collection<RiskAssessment> assessments,
                                                                HealthRecord latestRecord) {
        if (candidateVideoIds == null || candidateVideoIds.isEmpty()) {
            return Map.of();
        }

        List<KnowledgeGraphNode> videoNodes = nodeRepository.findByNodeTypeAndSourceRefIdIn(
                NODE_VIDEO,
                candidateVideoIds.stream().map(String::valueOf).toList()
        );
        if (videoNodes.isEmpty()) {
            return Map.of();
        }

        Map<Long, KnowledgeGraphNode> videoNodeByVideoId = new LinkedHashMap<>();
        for (KnowledgeGraphNode node : videoNodes) {
            try {
                videoNodeByVideoId.put(Long.valueOf(node.getSourceRefId()), node);
            } catch (NumberFormatException ignored) {
                // ignore malformed source ref
            }
        }
        if (videoNodeByVideoId.isEmpty()) {
            return Map.of();
        }

        List<String> diseaseKeys = latestAssessmentsByDisease(assessments).keySet().stream().toList();
        Set<String> preferredTopics = inferPreferredTopics(assessments, latestRecord);
        Set<Long> videoNodeIds = videoNodeByVideoId.values().stream().map(KnowledgeGraphNode::getId).collect(Collectors.toSet());

        Map<Long, Set<Long>> diseaseTargetsByVideoNode = groupEdgeTargets(edgeRepository.findByRelationTypeAndSourceNodeIdIn(
                REL_ABOUT_DISEASE, videoNodeIds
        ));
        Map<Long, Set<Long>> topicTargetsByVideoNode = groupEdgeTargets(edgeRepository.findByRelationTypeAndSourceNodeIdIn(
                REL_HAS_TOPIC, videoNodeIds
        ));
        Map<Long, List<KnowledgeGraphEdge>> supportEdgesByVideoNode = edgeRepository.findByRelationTypeAndSourceNodeIdIn(
                REL_SUPPORTED_BY_DOC, videoNodeIds
        ).stream().collect(Collectors.groupingBy(KnowledgeGraphEdge::getSourceNodeId));

        Map<String, Long> diseaseNodeIds = nodeRepository.findByNodeTypeAndNodeKeyIn(NODE_DISEASE, diseaseKeys).stream()
                .collect(Collectors.toMap(KnowledgeGraphNode::getNodeKey, KnowledgeGraphNode::getId, (a, b) -> a, LinkedHashMap::new));
        Map<String, Long> topicNodeIds = nodeRepository.findByNodeTypeAndNodeKeyIn(NODE_TOPIC, preferredTopics).stream()
                .collect(Collectors.toMap(KnowledgeGraphNode::getNodeKey, KnowledgeGraphNode::getId, (a, b) -> a, LinkedHashMap::new));

        Map<Long, RecommendationScore> scores = new LinkedHashMap<>();
        for (Map.Entry<Long, KnowledgeGraphNode> entry : videoNodeByVideoId.entrySet()) {
            Long videoId = entry.getKey();
            KnowledgeGraphNode node = entry.getValue();
            Set<Long> diseaseTargets = diseaseTargetsByVideoNode.getOrDefault(node.getId(), Set.of());
            Set<Long> topicTargets = topicTargetsByVideoNode.getOrDefault(node.getId(), Set.of());
            List<KnowledgeGraphEdge> supportEdges = supportEdgesByVideoNode.getOrDefault(node.getId(), List.of());

            int matchedDiseases = 0;
            boolean generalOnly = false;
            for (String diseaseKey : diseaseKeys) {
                Long diseaseNodeId = diseaseNodeIds.get(diseaseKey);
                if (diseaseNodeId != null && diseaseTargets.contains(diseaseNodeId)) {
                    matchedDiseases++;
                }
            }
            Long generalNodeId = diseaseNodeIds.get(DiseaseAliasUtil.GENERAL);
            if (matchedDiseases == 0 && generalNodeId != null && diseaseTargets.contains(generalNodeId)) {
                generalOnly = true;
            }

            int matchedTopics = 0;
            List<String> matchedTopicKeys = new ArrayList<>();
            for (Map.Entry<String, Long> topicEntry : topicNodeIds.entrySet()) {
                if (topicTargets.contains(topicEntry.getValue())) {
                    matchedTopics++;
                    matchedTopicKeys.add(topicEntry.getKey());
                }
            }

            int supportDocs = (int) supportEdges.stream()
                    .map(KnowledgeGraphEdge::getTargetNodeId)
                    .distinct()
                    .count();

            double score = matchedDiseases * 2.5d
                    + (generalOnly ? 0.6d : 0d)
                    + matchedTopics * 1.4d
                    + Math.min(1.5d, supportDocs * 0.35d);

            if (score <= 0) {
                continue;
            }

            String reason = buildGraphReason(matchedDiseases, generalOnly, matchedTopicKeys, supportDocs);
            scores.put(videoId, new RecommendationScore(score, reason, matchedDiseases, matchedTopics, supportDocs));
        }
        return scores;
    }

    public ChatGraphSupport buildChatSupport(String userQuestion,
                                             Collection<RiskAssessment> assessments,
                                             HealthRecord latestRecord,
                                             int limit) {
        if (nodeRepository.count() == 0 || edgeRepository.count() == 0) {
            return ChatGraphSupport.empty();
        }

        int finalLimit = Math.max(1, Math.min(limit, 5));
        LinkedHashSet<String> diseaseKeys = new LinkedHashSet<>(latestAssessmentsByDisease(assessments).keySet());
        if (diseaseKeys.isEmpty()) {
            diseaseKeys.add(DiseaseAliasUtil.GENERAL);
        }

        LinkedHashSet<String> topicKeys = new LinkedHashSet<>(inferPreferredTopics(assessments, latestRecord));
        topicKeys.addAll(inferTopics(collectTexts(userQuestion)));

        List<KnowledgeGraphNode> diseaseNodes = nodeRepository.findByNodeTypeAndNodeKeyIn(NODE_DISEASE, diseaseKeys);
        if (diseaseNodes.isEmpty()) {
            return ChatGraphSupport.empty();
        }

        Map<Long, String> diseaseLabelsByNodeId = diseaseNodes.stream()
                .collect(Collectors.toMap(
                        KnowledgeGraphNode::getId,
                        KnowledgeGraphNode::getDisplayName,
                        (left, right) -> left,
                        LinkedHashMap::new
                ));

        Set<Long> diseaseNodeIds = diseaseLabelsByNodeId.keySet();
        List<KnowledgeGraphEdge> diseaseEdges = edgeRepository.findByRelationTypeAndTargetNodeIdIn(
                REL_ABOUT_DISEASE,
                diseaseNodeIds
        );

        Map<Long, Set<String>> diseaseLabelsByDocNodeId = new LinkedHashMap<>();
        for (KnowledgeGraphEdge edge : diseaseEdges) {
            KnowledgeGraphNode sourceNode = nodeRepository.findById(edge.getSourceNodeId()).orElse(null);
            if (sourceNode == null || !NODE_KNOWLEDGE_DOC.equals(sourceNode.getNodeType())) {
                continue;
            }
            String label = diseaseLabelsByNodeId.get(edge.getTargetNodeId());
            if (label == null || label.isBlank()) {
                continue;
            }
            diseaseLabelsByDocNodeId.computeIfAbsent(sourceNode.getId(), ignored -> new LinkedHashSet<>()).add(label);
        }

        if (diseaseLabelsByDocNodeId.isEmpty()) {
            return ChatGraphSupport.empty();
        }

        Map<Long, Set<String>> topicLabelsByDocNodeId = new LinkedHashMap<>();
        if (!topicKeys.isEmpty()) {
            Map<Long, String> topicLabelsByNodeId = nodeRepository.findByNodeTypeAndNodeKeyIn(NODE_TOPIC, topicKeys).stream()
                    .collect(Collectors.toMap(
                            KnowledgeGraphNode::getId,
                            KnowledgeGraphNode::getDisplayName,
                            (left, right) -> left,
                            LinkedHashMap::new
                    ));
            if (!topicLabelsByNodeId.isEmpty()) {
                List<KnowledgeGraphEdge> topicEdges = edgeRepository.findByRelationTypeAndTargetNodeIdIn(
                        REL_HAS_TOPIC,
                        topicLabelsByNodeId.keySet()
                );
                for (KnowledgeGraphEdge edge : topicEdges) {
                    if (!diseaseLabelsByDocNodeId.containsKey(edge.getSourceNodeId())) {
                        continue;
                    }
                    String topicLabel = topicLabelsByNodeId.get(edge.getTargetNodeId());
                    if (topicLabel == null || topicLabel.isBlank()) {
                        continue;
                    }
                    topicLabelsByDocNodeId.computeIfAbsent(edge.getSourceNodeId(), ignored -> new LinkedHashSet<>()).add(topicLabel);
                }
            }
        }

        List<KnowledgeGraphNode> candidateDocNodes = nodeRepository.findAllById(diseaseLabelsByDocNodeId.keySet()).stream()
                .filter(node -> NODE_KNOWLEDGE_DOC.equals(node.getNodeType()))
                .toList();
        if (candidateDocNodes.isEmpty()) {
            return ChatGraphSupport.empty();
        }

        List<KnowledgeGraphNode> prioritizedDocNodes = candidateDocNodes.stream()
                .sorted(Comparator
                        .comparingInt((KnowledgeGraphNode node) -> -topicLabelsByDocNodeId.getOrDefault(node.getId(), Set.of()).size())
                        .thenComparingInt(node -> -diseaseLabelsByDocNodeId.getOrDefault(node.getId(), Set.of()).size())
                        .thenComparing(KnowledgeGraphNode::getDisplayName, Comparator.nullsLast(String::compareTo)))
                .toList();

        List<ChatGraphSupportItem> items = new ArrayList<>();
        for (KnowledgeGraphNode docNode : prioritizedDocNodes) {
            Set<String> matchedDiseases = diseaseLabelsByDocNodeId.getOrDefault(docNode.getId(), Set.of());
            Set<String> matchedTopics = topicLabelsByDocNodeId.getOrDefault(docNode.getId(), Set.of());
            if (!topicKeys.isEmpty() && !topicLabelsByDocNodeId.isEmpty() && matchedTopics.isEmpty()) {
                continue;
            }
            items.add(toChatSupportItem(docNode, matchedDiseases, matchedTopics));
            if (items.size() >= finalLimit) {
                break;
            }
        }

        if (items.isEmpty()) {
            for (KnowledgeGraphNode docNode : prioritizedDocNodes.stream().limit(finalLimit).toList()) {
                items.add(toChatSupportItem(
                        docNode,
                        diseaseLabelsByDocNodeId.getOrDefault(docNode.getId(), Set.of()),
                        topicLabelsByDocNodeId.getOrDefault(docNode.getId(), Set.of())
                ));
            }
        }

        if (items.isEmpty()) {
            return ChatGraphSupport.empty();
        }

        LinkedHashSet<String> matchedDiseaseLabels = new LinkedHashSet<>();
        LinkedHashSet<String> matchedTopicLabels = new LinkedHashSet<>();
        items.forEach(item -> {
            matchedDiseaseLabels.addAll(item.matchedDiseases());
            matchedTopicLabels.addAll(item.matchedTopics());
        });

        StringBuilder summary = new StringBuilder();
        if (!matchedDiseaseLabels.isEmpty()) {
            summary.append("- 命中病种：").append(String.join("、", matchedDiseaseLabels)).append('\n');
        }
        if (!matchedTopicLabels.isEmpty()) {
            summary.append("- 命中主题：").append(String.join("、", matchedTopicLabels)).append('\n');
        }
        summary.append("- 图谱关联文档：\n");
        for (ChatGraphSupportItem item : items) {
            summary.append("  - ").append(item.title());
            if (!item.matchedDiseases().isEmpty()) {
                summary.append(" | 病种：").append(String.join("、", item.matchedDiseases()));
            }
            if (!item.matchedTopics().isEmpty()) {
                summary.append(" | 主题：").append(String.join("、", item.matchedTopics()));
            }
            if (item.source() != null && !item.source().isBlank()) {
                summary.append(" | 来源：").append(item.source());
            }
            summary.append('\n');
        }

        return new ChatGraphSupport(summary.toString().trim(), items);
    }

    private String buildGraphReason(int matchedDiseases,
                                    boolean generalOnly,
                                    List<String> matchedTopicKeys,
                                    int supportDocs) {
        List<String> parts = new ArrayList<>();
        if (matchedDiseases > 0) {
            parts.add("匹配患者当前病种");
        } else if (generalOnly) {
            parts.add("匹配通用慢病主题");
        }
        if (!matchedTopicKeys.isEmpty()) {
            parts.add("命中" + matchedTopicKeys.stream()
                    .map(this::topicLabel)
                    .distinct()
                    .collect(Collectors.joining("、")) + "主题");
        }
        if (supportDocs > 0) {
            parts.add("有" + supportDocs + "条知识文档关联支撑");
        }
        return parts.isEmpty() ? "知识图谱补充推荐" : String.join("，", parts);
    }

    private ChatGraphSupportItem toChatSupportItem(KnowledgeGraphNode docNode,
                                                   Set<String> matchedDiseases,
                                                   Set<String> matchedTopics) {
        Map<String, Object> attrs = parseAttributes(docNode.getAttributesJson());
        String title = attrs.getOrDefault("title", safeDisplayName(docNode.getDisplayName(), "知识文档")).toString();
        String source = attrs.getOrDefault("source", "").toString();
        String publishedAt = attrs.getOrDefault("publishedAt", "").toString();
        return new ChatGraphSupportItem(
                docNode.getId(),
                "KNOWLEDGE",
                parseLongOrNull(docNode.getSourceRefId()),
                title,
                source,
                publishedAt,
                new ArrayList<>(matchedDiseases),
                new ArrayList<>(matchedTopics)
        );
    }

    private void registerVideo(GraphBuilder builder, VideoResource video) {
        String diseaseKey = DiseaseAliasUtil.normalizeKey(video.getDiseaseType());
        KnowledgeGraphNode videoNode = builder.node(
                NODE_VIDEO,
                String.valueOf(video.getId()),
                safeDisplayName(video.getTitle(), "视频#" + video.getId()),
                "VIDEO_RESOURCE",
                String.valueOf(video.getId()),
                Map.of(
                        "category", safeText(video.getCategory()),
                        "disease", diseaseKey,
                        "hasTranscript", Boolean.TRUE.equals(video.getHasTranscript()),
                        "tags", parseTags(video.getContentTags())
                )
        );
        KnowledgeGraphNode diseaseNode = builder.node(
                NODE_DISEASE,
                diseaseKey,
                DiseaseAliasUtil.displayName(diseaseKey),
                "NORMALIZED",
                diseaseKey,
                Map.of("label", DiseaseAliasUtil.knowledgeLabel(diseaseKey))
        );
        builder.edge(REL_ABOUT_DISEASE, videoNode, diseaseNode, 1.0d, "VIDEO_RESOURCE", String.valueOf(video.getId()), video.getTitle());

        for (String topicKey : inferTopics(
                collectTexts(video.getCategory(), video.getTitle(), video.getDescription(), video.getContentTags())
        )) {
            KnowledgeGraphNode topicNode = builder.node(NODE_TOPIC, topicKey, topicLabel(topicKey), "NORMALIZED", topicKey, Map.of());
            builder.edge(REL_HAS_TOPIC, videoNode, topicNode, 1.0d, "VIDEO_RESOURCE", String.valueOf(video.getId()), safeText(video.getCategory()));
        }
    }

    private void registerKnowledgeDocs(GraphBuilder builder, List<KnowledgeChunk> chunks) {
        Map<String, List<KnowledgeChunk>> grouped = chunks.stream()
                .collect(Collectors.groupingBy(this::knowledgeDocKey, LinkedHashMap::new, Collectors.toList()));

        for (Map.Entry<String, List<KnowledgeChunk>> entry : grouped.entrySet()) {
            List<KnowledgeChunk> docs = entry.getValue();
            if (docs.isEmpty()) {
                continue;
            }
            KnowledgeChunk first = docs.get(0);
            String diseaseKey = DiseaseAliasUtil.normalizeKey(first.getDisease());
            KnowledgeGraphNode docNode = builder.node(
                    NODE_KNOWLEDGE_DOC,
                    entry.getKey(),
                    safeDisplayName(first.getTitle(), first.getSource()),
                    "KNOWLEDGE_CHUNK",
                    String.valueOf(first.getId()),
                    Map.of(
                            "title", safeText(first.getTitle()),
                            "source", safeText(first.getSource()),
                            "version", safeText(first.getVersionTag()),
                            "publishedAt", first.getPublishedAt() != null ? first.getPublishedAt().toString() : "",
                            "chunkCount", docs.size()
                    )
            );
            KnowledgeGraphNode diseaseNode = builder.node(
                    NODE_DISEASE,
                    diseaseKey,
                    DiseaseAliasUtil.displayName(diseaseKey),
                    "NORMALIZED",
                    diseaseKey,
                    Map.of("label", DiseaseAliasUtil.knowledgeLabel(diseaseKey))
            );
            builder.edge(REL_ABOUT_DISEASE, docNode, diseaseNode, 1.0d, "KNOWLEDGE_DOC", entry.getKey(), safeText(first.getSection()));

            Set<String> topics = new LinkedHashSet<>();
            for (KnowledgeChunk chunk : docs) {
                topics.addAll(inferTopics(collectTexts(chunk.getSection(), chunk.getTitle(), chunk.getContentTags(), chunk.getContent())));
            }
            for (String topicKey : topics) {
                KnowledgeGraphNode topicNode = builder.node(NODE_TOPIC, topicKey, topicLabel(topicKey), "NORMALIZED", topicKey, Map.of());
                builder.edge(REL_HAS_TOPIC, docNode, topicNode, 1.0d, "KNOWLEDGE_DOC", entry.getKey(), safeText(first.getSection()));
            }
        }
    }

    private void linkVideoAndDocs(GraphBuilder builder) {
        List<KnowledgeGraphNode> videos = builder.nodesByType(NODE_VIDEO);
        List<KnowledgeGraphNode> docs = builder.nodesByType(NODE_KNOWLEDGE_DOC);
        if (videos.isEmpty() || docs.isEmpty()) {
            return;
        }

        Map<Long, Set<Long>> videoDiseaseTargets = builder.targetIdsByRelationAndSource(REL_ABOUT_DISEASE, videos);
        Map<Long, Set<Long>> videoTopicTargets = builder.targetIdsByRelationAndSource(REL_HAS_TOPIC, videos);
        Map<Long, Set<Long>> docDiseaseTargets = builder.targetIdsByRelationAndSource(REL_ABOUT_DISEASE, docs);
        Map<Long, Set<Long>> docTopicTargets = builder.targetIdsByRelationAndSource(REL_HAS_TOPIC, docs);

        for (KnowledgeGraphNode video : videos) {
            Set<Long> videoDiseases = videoDiseaseTargets.getOrDefault(video.getId(), Set.of());
            Set<Long> videoTopics = videoTopicTargets.getOrDefault(video.getId(), Set.of());
            for (KnowledgeGraphNode doc : docs) {
                Set<Long> docDiseases = docDiseaseTargets.getOrDefault(doc.getId(), Set.of());
                Set<Long> docTopics = docTopicTargets.getOrDefault(doc.getId(), Set.of());
                boolean diseaseMatched = !intersection(videoDiseases, docDiseases).isEmpty();
                boolean topicMatched = !intersection(videoTopics, docTopics).isEmpty();
                if (!diseaseMatched && !topicMatched) {
                    continue;
                }
                double weight = (diseaseMatched ? 1.0d : 0d) + (topicMatched ? 0.8d : 0d);
                builder.edge(
                        REL_SUPPORTED_BY_DOC,
                        video,
                        doc,
                        weight,
                        "DERIVED",
                        video.getSourceRefId(),
                        diseaseMatched && topicMatched ? "病种与主题双匹配" : (diseaseMatched ? "病种匹配" : "主题匹配")
                );
            }
        }
    }

    private Map<Long, Set<Long>> groupEdgeTargets(List<KnowledgeGraphEdge> edges) {
        Map<Long, Set<Long>> result = new LinkedHashMap<>();
        for (KnowledgeGraphEdge edge : edges) {
            result.computeIfAbsent(edge.getSourceNodeId(), key -> new LinkedHashSet<>()).add(edge.getTargetNodeId());
        }
        return result;
    }

    private Map<String, RiskAssessment> latestAssessmentsByDisease(Collection<RiskAssessment> assessments) {
        Map<String, RiskAssessment> latestByDisease = new LinkedHashMap<>();
        if (assessments == null) {
            return latestByDisease;
        }
        assessments.stream()
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(RiskAssessment::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .forEach(assessment -> {
                    String diseaseKey = assessment.getDiseaseType() == null
                            ? DiseaseAliasUtil.GENERAL
                            : DiseaseAliasUtil.normalizeKey(assessment.getDiseaseType().name());
                    latestByDisease.putIfAbsent(diseaseKey, assessment);
                });
        return latestByDisease;
    }

    private Set<String> inferPreferredTopics(Collection<RiskAssessment> assessments, HealthRecord latestRecord) {
        LinkedHashSet<String> topics = new LinkedHashSet<>();
        if (assessments != null) {
            for (RiskAssessment assessment : assessments) {
                if (assessment == null || assessment.getRiskLevel() == null) {
                    continue;
                }
                switch (assessment.getRiskLevel()) {
                    case HIGH, CRITICAL -> {
                        topics.add(TOPIC_FOLLOW_UP);
                        topics.add(TOPIC_WARNING);
                        topics.add(TOPIC_MONITORING);
                    }
                    case MEDIUM -> topics.add(TOPIC_MONITORING);
                    default -> {
                    }
                }
            }
        }
        if (latestRecord != null) {
            if (isPositive(latestRecord.getMedicationMissRate(), 20)) {
                topics.add(TOPIC_MEDICATION);
            }
            if (isPositive(latestRecord.getSaltIntake(), 6)
                    || isPositive(latestRecord.getSystolicBp(), 140)
                    || isPositive(latestRecord.getDiastolicBp(), 90)
                    || isPositive(latestRecord.getFastingGlucose(), 7)
                    || isPositive(latestRecord.getHba1c(), 7)) {
                topics.add(TOPIC_DIET);
                topics.add(TOPIC_MONITORING);
            }
            if (isPositive(latestRecord.getBmi(), 24)
                    || isLow(latestRecord.getExerciseFrequency(), 3)) {
                topics.add(TOPIC_EXERCISE);
                topics.add(TOPIC_LIFESTYLE);
            }
        }
        if (topics.isEmpty()) {
            topics.add(TOPIC_LIFESTYLE);
            topics.add(TOPIC_MONITORING);
        }
        return topics;
    }

    private boolean isPositive(Number value, double threshold) {
        return value != null && value.doubleValue() >= threshold;
    }

    private boolean isLow(Number value, double threshold) {
        return value == null || value.doubleValue() < threshold;
    }

    private List<String> inferTopics(Collection<String> textSources) {
        LinkedHashSet<String> topics = new LinkedHashSet<>();
        String merged = textSources == null ? "" : textSources.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.joining("\n"))
                .toLowerCase(Locale.ROOT);
        if (merged.isBlank()) {
            return List.of();
        }
        if (containsAny(merged, "饮食", "膳食", "食谱", "低盐", "低糖", "低脂", "营养", "食物", "水果", "主食")) {
            topics.add(TOPIC_DIET);
        }
        if (containsAny(merged, "运动", "锻炼", "步行", "有氧", "力量训练", "活动量", "康复")) {
            topics.add(TOPIC_EXERCISE);
        }
        if (containsAny(merged, "作息", "睡眠", "情绪", "心理", "减压", "戒烟", "限酒", "生活方式")) {
            topics.add(TOPIC_LIFESTYLE);
        }
        if (containsAny(merged, "用药", "服药", "药物", "漏服", "依从性", "降压药", "降糖药")) {
            topics.add(TOPIC_MEDICATION);
        }
        if (containsAny(merged, "监测", "血压", "血糖", "复测", "记录", "指标", "随测", "复查")) {
            topics.add(TOPIC_MONITORING);
        }
        if (containsAny(merged, "随访", "复诊", "门诊", "管理计划", "追踪")) {
            topics.add(TOPIC_FOLLOW_UP);
        }
        if (containsAny(merged, "警示", "危险", "预警", "立即就医", "急诊", "胸痛", "呼吸困难", "头晕")) {
            topics.add(TOPIC_WARNING);
        }
        return new ArrayList<>(topics);
    }

    private boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private String knowledgeDocKey(KnowledgeChunk chunk) {
        String raw = String.join("|",
                DiseaseAliasUtil.normalizeKey(chunk.getDisease()),
                safeText(chunk.getTitle()),
                safeText(chunk.getSource()),
                safeText(chunk.getVersionTag()),
                Optional.ofNullable(chunk.getPublishedAt()).map(LocalDate::toString).orElse("")
        );
        return "DOC-" + UUID.nameUUIDFromBytes(raw.getBytes(StandardCharsets.UTF_8));
    }

    private String topicLabel(String topicKey) {
        return switch (topicKey) {
            case TOPIC_DIET -> "饮食管理";
            case TOPIC_EXERCISE -> "运动康复";
            case TOPIC_LIFESTYLE -> "生活方式";
            case TOPIC_MEDICATION -> "用药依从";
            case TOPIC_MONITORING -> "指标监测";
            case TOPIC_FOLLOW_UP -> "随访复诊";
            case TOPIC_WARNING -> "风险警示";
            default -> topicKey;
        };
    }

    private String safeDisplayName(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private String safeText(String value) {
        return value == null ? "" : value.trim();
    }

    private String normalizeNodeKey(String nodeType, String raw) {
        String value = safeText(raw);
        if (value.length() <= 140) {
            return value;
        }
        return nodeType + "-" + UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8));
    }

    private String normalizeRefId(String raw) {
        String value = safeText(raw);
        if (value.length() <= 110) {
            return value;
        }
        return "REF-" + UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8));
    }

    private Long parseLongOrNull(String raw) {
        String value = safeText(raw);
        if (value.isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private int normalizeVisualizationLimit(Integer limit) {
        if (limit == null) {
            return 80;
        }
        return Math.max(20, Math.min(180, limit));
    }

    private boolean matchesKeyword(KnowledgeGraphNode node, String keyword) {
        return safeText(node.getDisplayName()).toLowerCase(Locale.ROOT).contains(keyword)
                || safeText(node.getNodeKey()).toLowerCase(Locale.ROOT).contains(keyword)
                || safeText(node.getSourceType()).toLowerCase(Locale.ROOT).contains(keyword);
    }

    private Optional<KnowledgeGraphNode> findNode(List<KnowledgeGraphNode> nodes, Long id) {
        if (id == null) {
            return Optional.empty();
        }
        return nodes.stream().filter(node -> id.equals(node.getId())).findFirst();
    }

    private String nodeTypeLabel(String nodeType) {
        return switch (safeText(nodeType).toUpperCase(Locale.ROOT)) {
            case NODE_DISEASE -> "疾病";
            case NODE_VIDEO -> "视频";
            case NODE_KNOWLEDGE_DOC -> "知识文档";
            case NODE_TOPIC -> "主题";
            default -> safeText(nodeType);
        };
    }

    private String relationLabel(String relationType) {
        return switch (safeText(relationType).toUpperCase(Locale.ROOT)) {
            case REL_ABOUT_DISEASE -> "关联病种";
            case REL_HAS_TOPIC -> "包含主题";
            case REL_SUPPORTED_BY_DOC -> "文档支撑";
            default -> safeText(relationType);
        };
    }

    private String nodeTypeColor(String nodeType) {
        return switch (safeText(nodeType).toUpperCase(Locale.ROOT)) {
            case NODE_DISEASE -> "#ef4444";
            case NODE_VIDEO -> "#0ea5e9";
            case NODE_KNOWLEDGE_DOC -> "#22c55e";
            case NODE_TOPIC -> "#f59e0b";
            default -> "#64748b";
        };
    }

    private int nodeSymbolSize(String nodeType) {
        return switch (safeText(nodeType).toUpperCase(Locale.ROOT)) {
            case NODE_DISEASE -> 56;
            case NODE_VIDEO -> 46;
            case NODE_KNOWLEDGE_DOC -> 38;
            case NODE_TOPIC -> 34;
            default -> 30;
        };
    }

    private List<String> collectTexts(String... values) {
        List<String> result = new ArrayList<>();
        if (values == null) {
            return result;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                result.add(value);
            }
        }
        return result;
    }

    private List<String> parseTags(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        LinkedHashSet<String> tags = new LinkedHashSet<>();
        for (String part : raw.split("[,，;；\\n]+")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                tags.add(trimmed);
            }
        }
        return new ArrayList<>(tags);
    }

    private Map<String, Object> parseAttributes(String raw) {
        if (raw == null || raw.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(raw, new TypeReference<Map<String, Object>>() {
            });
        } catch (Exception e) {
            return Map.of();
        }
    }

    private Set<Long> intersection(Set<Long> left, Set<Long> right) {
        if (left == null || right == null || left.isEmpty() || right.isEmpty()) {
            return Set.of();
        }
        LinkedHashSet<Long> result = new LinkedHashSet<>(left);
        result.retainAll(right);
        return result;
    }

    private String toJson(Map<String, Object> value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }

    public static class RecommendationScore {
        private final double score;
        private final String reason;
        private final int matchedDiseases;
        private final int matchedTopics;
        private final int supportDocs;

        public RecommendationScore(double score, String reason, int matchedDiseases, int matchedTopics, int supportDocs) {
            this.score = score;
            this.reason = reason;
            this.matchedDiseases = matchedDiseases;
            this.matchedTopics = matchedTopics;
            this.supportDocs = supportDocs;
        }

        public double getScore() {
            return score;
        }

        public String getReason() {
            return reason;
        }

        public int getMatchedDiseases() {
            return matchedDiseases;
        }

        public int getMatchedTopics() {
            return matchedTopics;
        }

        public int getSupportDocs() {
            return supportDocs;
        }
    }

    public record ChatGraphSupport(String summary, List<ChatGraphSupportItem> items) {
        public static ChatGraphSupport empty() {
            return new ChatGraphSupport("", List.of());
        }

        public boolean isEmpty() {
            return summary == null || summary.isBlank() || items == null || items.isEmpty();
        }
    }

    public record ChatGraphSupportItem(Long nodeId,
                                       String sourceType,
                                       Long refId,
                                       String title,
                                       String source,
                                       String publishedAt,
                                       List<String> matchedDiseases,
                                       List<String> matchedTopics) {
    }

    private class GraphBuilder {
        private final Map<String, KnowledgeGraphNode> nodesByKey = new LinkedHashMap<>();
        private final Map<String, KnowledgeGraphEdge> edgesByKey = new LinkedHashMap<>();

        KnowledgeGraphNode node(String nodeType,
                                String nodeKey,
                                String displayName,
                                String sourceType,
                                String sourceRefId,
                                Map<String, Object> attributes) {
            String normalizedNodeKey = normalizeNodeKey(nodeType, nodeKey);
            String normalizedSourceRefId = normalizeRefId(sourceRefId);
            String key = nodeType + "|" + normalizedNodeKey;
            return nodesByKey.computeIfAbsent(key, ignored -> nodeRepository.save(KnowledgeGraphNode.builder()
                    .nodeType(nodeType)
                    .nodeKey(normalizedNodeKey)
                    .displayName(displayName)
                    .sourceType(sourceType)
                    .sourceRefId(normalizedSourceRefId)
                    .attributesJson(toJson(attributes == null ? Map.of() : attributes))
                    .active(true)
                    .build()));
        }

        void edge(String relationType,
                  KnowledgeGraphNode sourceNode,
                  KnowledgeGraphNode targetNode,
                  double weight,
                  String sourceType,
                  String sourceRefId,
                  String evidence) {
            if (sourceNode == null || targetNode == null) {
                return;
            }
            String key = relationType + "|" + sourceNode.getId() + "|" + targetNode.getId();
            if (edgesByKey.containsKey(key)) {
                return;
            }
            edgesByKey.put(key, edgeRepository.save(KnowledgeGraphEdge.builder()
                    .relationType(relationType)
                    .sourceNodeId(sourceNode.getId())
                    .targetNodeId(targetNode.getId())
                    .weight(weight)
                    .sourceType(sourceType)
                    .sourceRefId(sourceRefId)
                    .evidenceText(evidence)
                    .build()));
        }

        List<KnowledgeGraphNode> nodesByType(String nodeType) {
            return nodesByKey.values().stream()
                    .filter(node -> nodeType.equals(node.getNodeType()))
                    .toList();
        }

        Map<Long, Set<Long>> targetIdsByRelationAndSource(String relationType, List<KnowledgeGraphNode> sources) {
            Set<Long> sourceIds = sources.stream().map(KnowledgeGraphNode::getId).collect(Collectors.toSet());
            return groupEdgeTargets(edgesByKey.values().stream()
                    .filter(edge -> relationType.equals(edge.getRelationType()) && sourceIds.contains(edge.getSourceNodeId()))
                    .toList());
        }
    }
}
