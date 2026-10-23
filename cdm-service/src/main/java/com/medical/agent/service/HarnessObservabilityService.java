package com.medical.agent.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.medical.agent.model.entity.AiChatMessage;
import com.medical.agent.model.entity.AiChatSession;
import com.medical.agent.model.mongo.AgentMessageLog;
import com.medical.agent.model.mongo.RuleExecutionLog;
import com.medical.agent.model.mongo.repository.AgentMessageLogRepository;
import com.medical.agent.model.mongo.repository.RuleExecutionLogRepository;
import com.medical.agent.model.repository.AiChatMessageRepository;
import com.medical.agent.model.repository.AiChatSessionRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
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
import java.util.stream.Collectors;

@Service
public class HarnessObservabilityService {

    private static final int CHAT_MAX_STEPS = 5;
    private static final int FOLLOW_UP_MAX_SPECIALISTS = 4;
    private static final int REPORT_MAX_SPECIALISTS = 5;
    private static final int EDUCATION_MAX_SPECIALISTS = 6;
    private static final int REFERENCE_MAX_SPECIALISTS = 3;

    private static final int DEFAULT_DAYS = 30;
    private static final int DEFAULT_ROW_LIMIT = 12;

    private final AiChatSessionRepository sessionRepository;
    private final AiChatMessageRepository messageRepository;
    private final AgentMessageLogRepository agentMessageLogRepository;
    private final RuleExecutionLogRepository ruleExecutionLogRepository;
    private final ObjectMapper objectMapper;
    private final com.medical.agent.service.security.PrivacyLogSanitizer sanitizer;

    public HarnessObservabilityService(AiChatSessionRepository sessionRepository,
                                       AiChatMessageRepository messageRepository,
                                       AgentMessageLogRepository agentMessageLogRepository,
                                       RuleExecutionLogRepository ruleExecutionLogRepository,
                                       ObjectMapper objectMapper) {
        this.sessionRepository = sessionRepository;
        this.messageRepository = messageRepository;
        this.agentMessageLogRepository = agentMessageLogRepository;
        this.ruleExecutionLogRepository = ruleExecutionLogRepository;
        this.objectMapper = objectMapper;
        this.sanitizer = new com.medical.agent.service.security.PrivacyLogSanitizer();
    }

    public Map<String, Object> getOverview(Integer days,
                                           Integer traceLimit,
                                           Integer ruleLimit,
                                           Integer messageLimit) {
        int recentDays = normalize(days, DEFAULT_DAYS, 1, 180);
        int normalizedTraceLimit = normalize(traceLimit, DEFAULT_ROW_LIMIT, 4, 30);
        int normalizedRuleLimit = normalize(ruleLimit, DEFAULT_ROW_LIMIT, 4, 30);
        int normalizedMessageLimit = normalize(messageLimit, DEFAULT_ROW_LIMIT, 4, 30);
        LocalDateTime cutoff = LocalDateTime.now().minusDays(recentDays);

        List<AiChatSession> recentSessions = sessionRepository.findAll().stream()
                .filter(session -> latestSessionTime(session) == null || !latestSessionTime(session).isBefore(cutoff))
                .sorted(Comparator.comparing(this::latestSessionTime, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
        Map<String, AiChatSession> sessionMap = recentSessions.stream()
                .collect(Collectors.toMap(AiChatSession::getId, session -> session, (left, right) -> left, LinkedHashMap::new));

        List<AiChatMessage> recentMessages = messageRepository.findAll().stream()
                .filter(message -> message.getCreatedAt() == null || !message.getCreatedAt().isBefore(cutoff))
                .sorted(Comparator.comparing(AiChatMessage::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
        List<AiChatMessage> recentAssistantMessages = recentMessages.stream()
                .filter(this::isAssistantMessage)
                .toList();

        List<TraceSnapshot> traceSnapshots = recentAssistantMessages.stream()
                .filter(message -> hasText(message.getAgentTraceJson()))
                .map(message -> toTraceSnapshot(message, sessionMap.get(message.getSessionId())))
                .filter(Objects::nonNull)
                .toList();

        List<RuleExecutionLog> recentRuleLogs = ruleExecutionLogRepository.findAll().stream()
                .filter(log -> log.getTimestamp() == null || !log.getTimestamp().isBefore(cutoff))
                .sorted(Comparator.comparing(RuleExecutionLog::getTimestamp, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();

        List<AgentMessageLog> recentAgentLogs = agentMessageLogRepository.findAll().stream()
                .filter(log -> log.getTimestamp() == null || !log.getTimestamp().isBefore(cutoff))
                .sorted(Comparator.comparing(AgentMessageLog::getTimestamp, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("summary", buildSummary(recentDays, recentSessions, recentAssistantMessages, traceSnapshots, recentRuleLogs, recentAgentLogs));
        result.put("evaluation", buildEvaluation(recentAssistantMessages, traceSnapshots, recentRuleLogs, recentAgentLogs));
        result.put("guardrails", buildGuardrails(traceSnapshots));
        result.put("recommendedCases", buildRecommendedCases(traceSnapshots));
        result.put("toolStats", aggregateCounts(traceSnapshots.stream().map(TraceSnapshot::usedTools).flatMap(Collection::stream).toList()));
        result.put("ownerRoleStats", aggregateCounts(traceSnapshots.stream().map(TraceSnapshot::ownerRole).filter(this::hasText).toList()));
        result.put("ruleGroupStats", aggregateCounts(recentRuleLogs.stream().map(RuleExecutionLog::getRuleGroup).filter(this::hasText).toList()));
        result.put("performativeStats", aggregateCounts(recentAgentLogs.stream().map(AgentMessageLog::getPerformative).filter(this::hasText).toList()));
        result.put("recentTraces", traceSnapshots.stream().limit(normalizedTraceLimit).map(this::toTraceRow).toList());
        result.put("recentRuleExecutions", recentRuleLogs.stream().limit(normalizedRuleLimit).map(this::toRuleRow).toList());
        result.put("recentAgentMessages", recentAgentLogs.stream().limit(normalizedMessageLimit).map(this::toAgentMessageRow).toList());
        return result;
    }

    private Map<String, Object> buildSummary(int recentDays,
                                             List<AiChatSession> sessions,
                                             List<AiChatMessage> assistantMessages,
                                             List<TraceSnapshot> traces,
                                             List<RuleExecutionLog> ruleLogs,
                                             List<AgentMessageLog> agentLogs) {
        long boundedViolationCount = traces.stream().filter(trace -> !trace.boundedExecution()).count();
        long safetyTraceCount = traces.stream().filter(trace -> !trace.safetyFlags().isEmpty()).count();
        long knowledgeGraphTraceCount = traces.stream().filter(TraceSnapshot::hasKnowledgeGraph).count();
        long referenceTraceCount = traces.stream().filter(trace -> !trace.references().isEmpty()).count();
        long highRiskTraceCount = traces.stream()
                .filter(trace -> trace.safetyFlags().stream().anyMatch(flag -> "high_risk_detected".equalsIgnoreCase(flag)))
                .count();

        Set<String> uniqueTools = traces.stream()
                .map(TraceSnapshot::usedTools)
                .flatMap(Collection::stream)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("recentDays", recentDays);
        summary.put("sessionCount", sessions.size());
        summary.put("assistantMessageCount", assistantMessages.size());
        summary.put("traceMessageCount", traces.size());
        summary.put("traceSessionCount", traces.stream().map(TraceSnapshot::sessionId).filter(this::hasText).distinct().count());
        summary.put("traceCoverageRate", assistantMessages.isEmpty() ? 0D : roundToOneDecimal(traces.size() * 100D / assistantMessages.size()));
        summary.put("averageTraceSteps", traces.isEmpty() ? 0D : roundToOneDecimal(
                traces.stream().mapToInt(TraceSnapshot::stepCount).average().orElse(0D)
        ));
        summary.put("averageToolCount", traces.isEmpty() ? 0D : roundToOneDecimal(
                traces.stream().mapToInt(trace -> trace.usedTools().size()).average().orElse(0D)
        ));
        summary.put("maxObservedSteps", traces.stream().mapToInt(TraceSnapshot::stepCount).max().orElse(0));
        summary.put("boundedViolationCount", boundedViolationCount);
        summary.put("safetyTraceCount", safetyTraceCount);
        summary.put("highRiskTraceCount", highRiskTraceCount);
        summary.put("knowledgeGraphTraceCount", knowledgeGraphTraceCount);
        summary.put("referenceTraceCount", referenceTraceCount);
        summary.put("uniqueToolCount", uniqueTools.size());
        summary.put("ruleExecutionCount", ruleLogs.size());
        summary.put("agentMessageCount", agentLogs.size());
        summary.put("latestTraceAt", traces.isEmpty() ? null : traces.get(0).createdAt());
        summary.put("latestRuleExecutionAt", ruleLogs.isEmpty() ? null : ruleLogs.get(0).getTimestamp());
        summary.put("latestAgentMessageAt", agentLogs.isEmpty() ? null : agentLogs.get(0).getTimestamp());
        return summary;
    }

    private Map<String, Object> buildEvaluation(List<AiChatMessage> assistantMessages,
                                                List<TraceSnapshot> traces,
                                                List<RuleExecutionLog> ruleLogs,
                                                List<AgentMessageLog> agentLogs) {
        long completeTraceCount = traces.stream().filter(TraceSnapshot::isCompleteTrace).count();
        long boundedHealthyCount = traces.stream().filter(TraceSnapshot::boundedExecution).count();
        long evidenceTraceCount = traces.stream().filter(TraceSnapshot::hasEvidence).count();
        long collaborativeTraceCount = traces.stream().filter(this::isCollaborativeTrace).count();

        double traceCoverageRate = assistantMessages.isEmpty() ? 0D : traces.size() * 100D / assistantMessages.size();
        double completenessRate = percentage(completeTraceCount, traces.size(), 0D);
        double boundaryRate = traces.isEmpty() ? 100D : percentage(boundedHealthyCount, traces.size(), 100D);
        double evidenceRate = percentage(evidenceTraceCount, traces.size(), 0D);
        double collaborationRate = percentage(collaborativeTraceCount, traces.size(), 0D);
        double logVisibilityRate = traces.isEmpty()
                ? 0D
                : Math.min(100D, roundToOneDecimal((ruleLogs.size() + agentLogs.size()) * 100D / Math.max(1, traces.size() * 2D)));

        int observabilityScore = normalizeScore((traceCoverageRate * 0.55) + (completenessRate * 0.45));
        int boundaryScore = normalizeScore(boundaryRate);
        int evidenceScore = normalizeScore(evidenceRate);
        int collaborationScore = normalizeScore((collaborationRate * 0.6) + (logVisibilityRate * 0.4));
        int overallScore = normalizeScore((observabilityScore + boundaryScore + evidenceScore + collaborationScore) / 4D);

        Map<String, Object> evaluation = new LinkedHashMap<>();
        evaluation.put("overallScore", overallScore);
        evaluation.put("overallStatus", scoreStatus(overallScore));
        evaluation.put("scorecards", List.of(
                scorecard(
                        "observability",
                        "可观测性",
                        observabilityScore,
                        "轨迹覆盖 " + roundToOneDecimal(traceCoverageRate) + "%，完整步骤 " + roundToOneDecimal(completenessRate) + "%。",
                        "衡量助手回复是否被稳定留痕，以及轨迹里是否同时包含工具与步骤。"
                ),
                scorecard(
                        "boundary",
                        "边界约束",
                        boundaryScore,
                        "边界合规 " + roundToOneDecimal(boundaryRate) + "%。",
                        "重点关注是否超过 specialist 上限，或出现非顺序执行步骤。"
                ),
                scorecard(
                        "evidence",
                        "证据附着",
                        evidenceScore,
                        "证据覆盖 " + roundToOneDecimal(evidenceRate) + "%。",
                        "衡量回答是否附带知识图谱支撑或结构化引用证据。"
                ),
                scorecard(
                        "collaboration",
                        "协同留痕",
                        collaborationScore,
                        "多步执行 " + roundToOneDecimal(collaborationRate) + "%，日志可见度 " + roundToOneDecimal(logVisibilityRate) + "%。",
                        "说明智能体协作不是黑盒，而是有规则日志和消息流可回放。"
                )
        ));
        evaluation.put("stageCoverage", List.of(
                coverageRow("trace_coverage", "轨迹留痕覆盖", traces.size(), assistantMessages.size(), traceCoverageRate, "助手回复中带 agentTraceJson 的比例"),
                coverageRow("complete_trace", "完整步骤覆盖", completeTraceCount, traces.size(), completenessRate, "同时含工具与步骤的轨迹比例"),
                coverageRow("evidence_trace", "证据附着覆盖", evidenceTraceCount, traces.size(), evidenceRate, "带知识图谱或引用证据的轨迹比例"),
                coverageRow("bounded_trace", "边界合规覆盖", boundedHealthyCount, traces.size(), boundaryRate, "步数和顺序均未越界的轨迹比例")
        ));
        evaluation.put("scenarioBreakdown", buildScenarioBreakdown(traces));
        return evaluation;
    }

    private List<Map<String, Object>> buildGuardrails(List<TraceSnapshot> traces) {
        long chatViolations = traces.stream().filter(trace -> !trace.boundedExecution()).count();
        List<Map<String, Object>> rows = new ArrayList<>();
        rows.add(guardrailRow(
                "chat_bound",
                "聊天问答执行上限",
                "最多 5 个 ReAct 步骤",
                chatViolations == 0 ? "最近观测无越界" : ("最近观测到 " + chatViolations + " 次越界"),
                "聊天链路改为受控 ReAct 循环，由模型决定是否调用工具，但代码强制限制最大步数与顺序编号。",
                chatViolations == 0
        ));
        rows.add(guardrailRow(
                "followup_bound",
                "智能随访执行上限",
                "最多 4 个 specialist",
                "SmartFollowUpSupervisorService 固定截断为 4 步内。",
                "随访生成链路不做自由递归，只执行排序后的有限 specialist。",
                true
        ));
        rows.add(guardrailRow(
                "report_bound",
                "健康报告执行上限",
                "最多 5 个 specialist",
                "HealthReportSupervisorService 超出上限时直接跳过额外 specialist。",
                "报告链路保持固定结构，避免评估、证据、起草无限扩张。",
                true
        ));
        rows.add(guardrailRow(
                "education_bound",
                "健康宣教执行上限",
                "最多 6 个 specialist",
                "HealthEducationSupervisorService 对 specialist 数量做截断。",
                "宣教链路维持有序执行，超限只告警不追加执行。",
                true
        ));
        rows.add(guardrailRow(
                "reference_bound",
                "引用裁决执行上限",
                "最多 3 个 specialist",
                "ReferenceSelectionSupervisorService 固定 3 个裁决节点。",
                "引用展示先筛选再裁决，避免重复判断和证据回环。",
                true
        ));
        rows.add(guardrailRow(
                "low_signal_short_circuit",
                "低信号消息短路",
                "“你好 / 谢谢 / 空消息”不进入 ReAct 链路",
                "ChatSupervisorService 对低信号消息直接生成简短引导回复。",
                "减少无效工具调用，避免无意义消息反复进入自主决策循环。",
                true
        ));
        return rows;
    }

    private Map<String, Object> guardrailRow(String key,
                                             String title,
                                             String limit,
                                             String signal,
                                             String description,
                                             boolean healthy) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("key", key);
        row.put("title", title);
        row.put("limit", limit);
        row.put("signal", signal);
        row.put("description", description);
        row.put("healthy", healthy);
        return row;
    }

    private Map<String, Object> scorecard(String key,
                                          String title,
                                          int score,
                                          String signal,
                                          String description) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("key", key);
        row.put("title", title);
        row.put("score", score);
        row.put("status", scoreStatus(score));
        row.put("signal", signal);
        row.put("description", description);
        return row;
    }

    private Map<String, Object> coverageRow(String key,
                                            String title,
                                            long count,
                                            long total,
                                            double rate,
                                            String description) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("key", key);
        row.put("title", title);
        row.put("count", count);
        row.put("total", total);
        row.put("rate", roundToOneDecimal(rate));
        row.put("description", description);
        return row;
    }

    private List<Map<String, Object>> buildScenarioBreakdown(List<TraceSnapshot> traces) {
        if (traces.isEmpty()) {
            return List.of();
        }

        Map<String, List<TraceSnapshot>> grouped = traces.stream()
                .collect(Collectors.groupingBy(TraceSnapshot::ownerRole, LinkedHashMap::new, Collectors.toList()));

        List<Map<String, Object>> rows = new ArrayList<>();
        for (String key : List.of("doctor", "patient", "unknown")) {
            List<TraceSnapshot> items = grouped.remove(key);
            if (items != null && !items.isEmpty()) {
                rows.add(toScenarioRow(key, items));
            }
        }
        grouped.forEach((key, items) -> rows.add(toScenarioRow(key, items)));
        rows.sort(Comparator.comparing(row -> ((Number) row.get("traceCount")).intValue(), Comparator.reverseOrder()));
        return rows;
    }

    private Map<String, Object> toScenarioRow(String role, List<TraceSnapshot> traces) {
        long evidenceCount = traces.stream().filter(TraceSnapshot::hasEvidence).count();
        long highRiskCount = traces.stream().filter(TraceSnapshot::hasHighRiskFlag).count();
        long violationCount = traces.stream().filter(trace -> !trace.boundedExecution()).count();
        long completeCount = traces.stream().filter(TraceSnapshot::isCompleteTrace).count();
        double averageSteps = traces.stream().mapToInt(TraceSnapshot::stepCount).average().orElse(0D);

        double boundaryRate = traces.isEmpty() ? 100D : percentage(traces.size() - violationCount, traces.size(), 100D);
        double evidenceRate = percentage(evidenceCount, traces.size(), 0D);
        double completenessRate = percentage(completeCount, traces.size(), 0D);
        int score = normalizeScore((boundaryRate * 0.45) + (evidenceRate * 0.3) + (completenessRate * 0.25));

        Map<String, Object> row = new LinkedHashMap<>();
        row.put("key", role);
        row.put("title", ownerRoleSummaryLabel(role));
        row.put("traceCount", traces.size());
        row.put("highRiskTraceCount", highRiskCount);
        row.put("evidenceTraceCount", evidenceCount);
        row.put("boundedViolationCount", violationCount);
        row.put("averageStepCount", roundToOneDecimal(averageSteps));
        row.put("score", score);
        row.put("status", scoreStatus(score));
        row.put("recommendation", buildScenarioRecommendation(ownerRoleSummaryLabel(role), highRiskCount, evidenceRate, violationCount));
        return row;
    }

    private String buildScenarioRecommendation(String title,
                                               long highRiskCount,
                                               double evidenceRate,
                                               long violationCount) {
        if (violationCount > 0) {
            return title + "存在边界异常样本，适合复盘系统如何发现执行越界。";
        }
        if (highRiskCount > 0) {
            return title + "已经出现高风险样本，适合展示规则兜底和安全拦截。";
        }
        if (evidenceRate >= 60D) {
            return title + "证据附着较稳定，适合展示图谱和引用如何支撑回答。";
        }
        return title + "当前更适合作为常规执行链路样本，建议继续积累高风险和证据型案例。";
    }

    private List<Map<String, Object>> buildRecommendedCases(List<TraceSnapshot> traces) {
        if (traces.isEmpty()) {
            return List.of();
        }

        List<Map<String, Object>> rows = new ArrayList<>();
        Set<Long> selectedMessageIds = new LinkedHashSet<>();
        addRecommendedCase(
                rows,
                selectedMessageIds,
                "risk_demo",
                "高风险拦截样本",
                "优先用于复盘安全标记、规则兜底和风险提示如何联动。",
                traces.stream().filter(TraceSnapshot::hasHighRiskFlag).findFirst().orElse(null)
        );
        addRecommendedCase(
                rows,
                selectedMessageIds,
                "evidence_demo",
                "证据优先样本",
                "用于复盘知识图谱支撑和引用证据如何约束回答生成。",
                traces.stream().filter(TraceSnapshot::hasEvidence).findFirst().orElse(null)
        );
        addRecommendedCase(
                rows,
                selectedMessageIds,
                "boundary_demo",
                "边界治理样本",
                "用于复盘系统如何发现执行链路越界，而不是放任智能体递归扩张。",
                traces.stream().filter(trace -> !trace.boundedExecution()).findFirst().orElse(null)
        );
        addRecommendedCase(
                rows,
                selectedMessageIds,
                "stable_demo",
                "稳定闭环样本",
                "用于复盘边界正常、证据充分、步骤完整的一次标准执行闭环。",
                traces.stream()
                        .filter(trace -> trace.boundedExecution() && trace.hasEvidence() && trace.isCompleteTrace())
                        .findFirst()
                        .orElse(traces.get(0))
        );
        return rows;
    }

    private void addRecommendedCase(List<Map<String, Object>> rows,
                                    Set<Long> selectedMessageIds,
                                    String key,
                                    String title,
                                    String reason,
                                    TraceSnapshot trace) {
        if (trace == null || trace.messageId() == null || !selectedMessageIds.add(trace.messageId())) {
            return;
        }

        Map<String, Object> row = new LinkedHashMap<>();
        row.put("key", key);
        row.put("title", title);
        row.put("reason", reason);
        row.put("messageId", trace.messageId());
        row.put("sessionId", trace.sessionId());
        row.put("sessionTitle", trace.sessionTitle());
        row.put("ownerRoleLabel", ownerRoleLabel(trace.ownerRole(), trace.patientId()));
        row.put("createdAt", trace.createdAt());
        row.put("replyPreview", trace.replyPreview());
        row.put("stepCount", trace.stepCount());
        row.put("toolCount", trace.usedTools().size());
        row.put("qualityScore", traceQualityScore(trace));
        row.put("qualityLabel", traceQualityLabel(trace));
        row.put("tags", buildTraceTags(trace));
        row.put("boundedExecution", trace.boundedExecution());
        rows.add(row);
    }

    private TraceSnapshot toTraceSnapshot(AiChatMessage message, AiChatSession session) {
        Map<String, Object> tracePayload = parseObjectMap(message.getAgentTraceJson());
        List<String> usedTools = toStringList(tracePayload.get("usedTools"));
        List<Map<String, Object>> steps = normalizeSteps(tracePayload.get("steps"));
        List<String> safetyFlags = toStringList(tracePayload.get("safetyFlags"));
        String knowledgeGraphSummary = toText(tracePayload.get("knowledgeGraphSummary"));
        List<Map<String, Object>> knowledgeGraphItems = toMapList(tracePayload.get("knowledgeGraphItems"));
        List<Map<String, Object>> references = parseMapList(message.getReferencesJson());
        boolean boundedExecution = steps.size() <= CHAT_MAX_STEPS && hasSequentialStepIndexes(steps);

        return new TraceSnapshot(
                message.getId(),
                message.getSessionId(),
                session != null && hasText(session.getTitle()) ? session.getTitle() : "未命名对话",
                session != null ? normalizeRole(session.getOwnerRole()) : "unknown",
                session != null ? session.getOwnerUserId() : null,
                session != null ? session.getPatientId() : null,
                message.getCreatedAt(),
                message.getContent(),
                abbreviate(message.getContent(), 140),
                usedTools,
                steps,
                safetyFlags,
                knowledgeGraphSummary,
                knowledgeGraphItems,
                references,
                boundedExecution
        );
    }

    private Map<String, Object> toTraceRow(TraceSnapshot trace) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("messageId", trace.messageId());
        row.put("sessionId", trace.sessionId());
        row.put("sessionTitle", trace.sessionTitle());
        row.put("ownerRole", trace.ownerRole());
        row.put("ownerRoleLabel", ownerRoleLabel(trace.ownerRole(), trace.patientId()));
        row.put("ownerUserId", trace.ownerUserId());
        row.put("patientId", trace.patientId());
        row.put("createdAt", trace.createdAt());
        row.put("reply", trace.reply());
        row.put("replyPreview", trace.replyPreview());
        row.put("usedTools", trace.usedTools());
        row.put("toolCount", trace.usedTools().size());
        row.put("steps", trace.steps());
        row.put("stepCount", trace.stepCount());
        row.put("safetyFlags", trace.safetyFlags());
        row.put("hasSafetyFlags", !trace.safetyFlags().isEmpty());
        row.put("hasHighRisk", trace.hasHighRiskFlag());
        row.put("knowledgeGraphSummary", trace.knowledgeGraphSummary());
        row.put("knowledgeGraphItems", trace.knowledgeGraphItems());
        row.put("references", trace.references());
        row.put("hasReferences", !trace.references().isEmpty());
        row.put("hasEvidence", trace.hasEvidence());
        row.put("boundedExecution", trace.boundedExecution());
        row.put("hasKnowledgeGraph", trace.hasKnowledgeGraph());
        row.put("qualityScore", traceQualityScore(trace));
        row.put("qualityLabel", traceQualityLabel(trace));
        row.put("demoTags", buildTraceTags(trace));
        row.put("guardrailNote", trace.boundedExecution() ? "步数与顺序均在边界内。" : "检测到执行边界异常，请复盘该条消息。");
        return row;
    }

    private Map<String, Object> toRuleRow(RuleExecutionLog log) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", log.getId());
        row.put("agentId", defaultText(log.getAgentId(), "未知智能体"));
        row.put("ruleGroup", defaultText(log.getRuleGroup(), "未分组"));
        row.put("ruleName", defaultText(log.getRuleName(), "未命名规则"));
        row.put("patientId", log.getPatientId());
        row.put("executionTimeMs", log.getExecutionTimeMs());
        row.put("timestamp", log.getTimestamp());
        row.put("inputSummary", summarizeMap(log.getInputFacts()));
        row.put("outputSummary", summarizeMap(log.getOutputResult()));
        return row;
    }

    private Map<String, Object> toAgentMessageRow(AgentMessageLog log) {
        Map<String, Object> row = new LinkedHashMap<>();
        String sanitizedContent = sanitizer.summarizeText(log.getContent());
        row.put("id", log.getId());
        row.put("senderAgent", defaultText(log.getSenderAgent(), "未知发送方"));
        row.put("receiverAgent", defaultText(log.getReceiverAgent(), "未知接收方"));
        row.put("performative", defaultText(log.getPerformative(), "UNKNOWN"));
        row.put("content", sanitizedContent);
        row.put("contentPreview", abbreviate(sanitizedContent, 120));
        row.put("metadata", log.getMetadata() == null ? Map.of() : sanitizer.summarizeMap(log.getMetadata()));
        row.put("metadataSummary", summarizeMap(sanitizer.summarizeMap(log.getMetadata())));
        row.put("timestamp", log.getTimestamp());
        return row;
    }

    private List<Map<String, Object>> aggregateCounts(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        return values.stream()
                .filter(this::hasText)
                .collect(Collectors.groupingBy(this::normalizeLabel, LinkedHashMap::new, Collectors.counting()))
                .entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
                .map(entry -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("name", entry.getKey());
                    row.put("count", entry.getValue());
                    return row;
                })
                .toList();
    }

    private boolean isCollaborativeTrace(TraceSnapshot trace) {
        return trace != null && (trace.stepCount() >= 2 || trace.usedTools().size() >= 2);
    }

    private List<String> buildTraceTags(TraceSnapshot trace) {
        List<String> tags = new ArrayList<>();
        if (trace == null) {
            return tags;
        }
        if (trace.hasHighRiskFlag()) {
            tags.add("高风险");
        }
        if (trace.hasEvidence()) {
            tags.add("证据支撑");
        }
        if (!trace.boundedExecution()) {
            tags.add("边界异常");
        }
        if (trace.isCompleteTrace()) {
            tags.add("步骤完整");
        }
        if (tags.isEmpty()) {
            tags.add("常规轨迹");
        }
        return tags;
    }

    private int traceQualityScore(TraceSnapshot trace) {
        if (trace == null) {
            return 0;
        }
        double score = 0D;
        if (trace.boundedExecution()) {
            score += 35D;
        }
        if (trace.isCompleteTrace()) {
            score += 25D;
        }
        if (trace.hasEvidence()) {
            score += 25D;
        }
        if (trace.hasHighRiskFlag() || isCollaborativeTrace(trace)) {
            score += 15D;
        }
        return normalizeScore(score);
    }

    private String traceQualityLabel(TraceSnapshot trace) {
        return scoreStatus(traceQualityScore(trace));
    }

    private List<Map<String, Object>> normalizeSteps(Object rawValue) {
        List<Map<String, Object>> rows = new ArrayList<>();
        int fallbackIndex = 1;
        for (Map<String, Object> item : toMapList(rawValue)) {
            Map<String, Object> row = new LinkedHashMap<>();
            Object rawIndex = item.get("index");
            int index = rawIndex instanceof Number number ? number.intValue() : fallbackIndex;
            row.put("index", index);
            row.put("actionName", defaultText(toText(item.get("actionName")), "未命名动作"));
            row.put("reasoningSummary", toText(item.get("reasoningSummary")));
            row.put("observationSummary", toText(item.get("observationSummary")));
            rows.add(row);
            fallbackIndex++;
        }
        return rows;
    }

    private boolean hasSequentialStepIndexes(List<Map<String, Object>> steps) {
        for (int i = 0; i < steps.size(); i++) {
            Object index = steps.get(i).get("index");
            if (!(index instanceof Number number) || number.intValue() != (i + 1)) {
                return false;
            }
        }
        return true;
    }

    private Map<String, Object> parseObjectMap(String json) {
        if (!hasText(json)) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception ignored) {
            return Map.of();
        }
    }

    private List<Map<String, Object>> parseMapList(String json) {
        if (!hasText(json)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<Map<String, Object>>>() {});
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private List<Map<String, Object>> toMapList(Object rawValue) {
        if (!(rawValue instanceof List<?> rawList) || rawList.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Object item : rawList) {
            if (item instanceof Map<?, ?> rawMap) {
                Map<String, Object> row = new LinkedHashMap<>();
                rawMap.forEach((key, value) -> row.put(String.valueOf(key), value));
                rows.add(row);
            }
        }
        return rows;
    }

    private List<String> toStringList(Object rawValue) {
        if (!(rawValue instanceof List<?> rawList) || rawList.isEmpty()) {
            return List.of();
        }
        return rawList.stream()
                .filter(Objects::nonNull)
                .map(String::valueOf)
                .filter(this::hasText)
                .toList();
    }

    private LocalDateTime latestSessionTime(AiChatSession session) {
        if (session == null) {
            return null;
        }
        return session.getLastMessageAt() != null ? session.getLastMessageAt() : session.getUpdatedAt();
    }

    private boolean isAssistantMessage(AiChatMessage message) {
        return message != null && "assistant".equalsIgnoreCase(message.getRole());
    }

    private String ownerRoleLabel(String ownerRole, Long patientId) {
        if ("doctor".equalsIgnoreCase(ownerRole)) {
            return patientId != null ? "医生-患者上下文" : "医生-通用咨询";
        }
        if ("patient".equalsIgnoreCase(ownerRole)) {
            return "患者自助咨询";
        }
        return "未知来源";
    }

    private String summarizeMap(Map<String, Object> source) {
        if (source == null || source.isEmpty()) {
            return "—";
        }
        return source.entrySet().stream()
                .limit(4)
                .map(entry -> entry.getKey() + "=" + abbreviate(String.valueOf(entry.getValue()), 24))
                .collect(Collectors.joining("；"));
    }

    private String normalizeRole(String role) {
        if (!hasText(role)) {
            return "unknown";
        }
        return role.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeLabel(String value) {
        return value == null ? "" : value.trim();
    }

    private String defaultText(String value, String defaultValue) {
        return hasText(value) ? value : defaultValue;
    }

    private String toText(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private String abbreviate(String text, int maxLength) {
        if (!hasText(text)) {
            return "";
        }
        String normalized = text.replaceAll("[\\r\\n]+", " ").trim();
        if (normalized.length() <= maxLength) {
            return normalized;
        }
        return normalized.substring(0, maxLength) + "...";
    }

    private double roundToOneDecimal(double value) {
        return Math.round(value * 10D) / 10D;
    }

    private double percentage(long count, long total, double defaultWhenEmpty) {
        if (total <= 0) {
            return defaultWhenEmpty;
        }
        return roundToOneDecimal(count * 100D / total);
    }

    private int normalizeScore(double score) {
        return (int) Math.max(0, Math.min(100, Math.round(score)));
    }

    private String scoreStatus(int score) {
        if (score >= 85) {
            return "优秀";
        }
        if (score >= 70) {
            return "良好";
        }
        if (score >= 50) {
            return "可提升";
        }
        return "关注";
    }

    private String ownerRoleSummaryLabel(String ownerRole) {
        if ("doctor".equalsIgnoreCase(ownerRole)) {
            return "医生侧";
        }
        if ("patient".equalsIgnoreCase(ownerRole)) {
            return "患者侧";
        }
        return "未知来源";
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private int normalize(Integer rawValue, int defaultValue, int min, int max) {
        if (rawValue == null) {
            return defaultValue;
        }
        return Math.max(min, Math.min(max, rawValue));
    }

    private record TraceSnapshot(Long messageId,
                                 String sessionId,
                                 String sessionTitle,
                                 String ownerRole,
                                 Long ownerUserId,
                                 Long patientId,
                                 LocalDateTime createdAt,
                                 String reply,
                                 String replyPreview,
                                 List<String> usedTools,
                                 List<Map<String, Object>> steps,
                                 List<String> safetyFlags,
                                 String knowledgeGraphSummary,
                                 List<Map<String, Object>> knowledgeGraphItems,
                                 List<Map<String, Object>> references,
                                 boolean boundedExecution) {

        private int stepCount() {
            return steps == null ? 0 : steps.size();
        }

        private boolean isCompleteTrace() {
            return steps != null && !steps.isEmpty() && usedTools != null && !usedTools.isEmpty();
        }

        private boolean hasHighRiskFlag() {
            return safetyFlags != null
                    && safetyFlags.stream().anyMatch(flag -> "high_risk_detected".equalsIgnoreCase(flag));
        }

        private boolean hasEvidence() {
            return hasKnowledgeGraph() || (references != null && !references.isEmpty());
        }

        private boolean hasKnowledgeGraph() {
            return (knowledgeGraphSummary != null && !knowledgeGraphSummary.isBlank())
                    || (knowledgeGraphItems != null && !knowledgeGraphItems.isEmpty());
        }
    }
}
