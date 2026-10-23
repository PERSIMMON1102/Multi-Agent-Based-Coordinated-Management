package com.medical.agent.service.react;

import com.fasterxml.jackson.databind.JsonNode;
import com.medical.agent.common.dto.RagEvidenceDTO;
import com.medical.agent.common.dto.RagQueryContextDTO;
import com.medical.agent.service.AgentMessageAuditService;
import com.medical.agent.service.LlmService;
import com.medical.agent.service.react.agent.AbstractDelegatingSubAgent;
import com.medical.agent.service.react.agent.ChatSubAgent;
import com.medical.agent.service.react.agent.SubAgentRegistry;
import com.medical.agent.service.react.agent.SubAgentResult;
import com.medical.agent.service.react.model.AgentState;
import com.medical.agent.service.react.model.AgentStep;
import com.medical.agent.service.react.model.ReActResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashMap;

@Component
public class ReActAgentExecutor {

    private static final Logger log = LoggerFactory.getLogger(ReActAgentExecutor.class);
    private static final String EXECUTOR_NAME = "ChatReActExecutor";
    private static final int MAX_STEPS = 5;
    private static final int MAX_REPEAT_PER_AGENT = 1;
    private static final int MAX_REPLANS = 2;
    private static final int FAILED_AGENT_COOLDOWN_STEPS = 2;

    private final SubAgentRegistry subAgentRegistry;
    private final LlmService llmService;
    private final ReActPromptBuilder promptBuilder;
    private final FinalAnswerGuard finalAnswerGuard;
    private final AgentMessageAuditService agentMessageAuditService;

    @Autowired
    public ReActAgentExecutor(SubAgentRegistry subAgentRegistry,
                              LlmService llmService,
                              ReActPromptBuilder promptBuilder,
                              FinalAnswerGuard finalAnswerGuard,
                              AgentMessageAuditService agentMessageAuditService) {
        this.subAgentRegistry = subAgentRegistry;
        this.llmService = llmService;
        this.promptBuilder = promptBuilder;
        this.finalAnswerGuard = finalAnswerGuard;
        this.agentMessageAuditService = agentMessageAuditService;
    }

    public ReActAgentExecutor(SubAgentRegistry subAgentRegistry,
                              LlmService llmService,
                              ReActPromptBuilder promptBuilder) {
        this(subAgentRegistry, llmService, promptBuilder, new FinalAnswerGuard(), null);
    }

    public ReActResult execute(AgentState state) {
        if (state == null) {
            return emptyResult(null);
        }

        Map<String, Integer> failedAgentCooldowns = new HashMap<>();

        for (int iteration = 0; iteration < MAX_STEPS; iteration++) {
            JsonNode decision = llmService.chatJsonObject(
                    promptBuilder.buildDecisionSystemPrompt(subAgentRegistry.listPrimaryAgents(), state.getConversationPerspective()),
                    promptBuilder.buildDecisionUserPrompt(state)
            );
            if (decision == null || !decision.isObject()) {
                log.warn("[ReActAgentExecutor] No structured decision returned at step {}", iteration + 1);
                break;
            }

            String type = textValue(decision.get("type"));
            if ("final_answer".equals(type)) {
                state.setAnswerPlan(textValue(decision.get("answerPlan")));
                updateGoalStateFromDecision(state, decision);
                break;
            }

            if ("replan".equals(type)) {
                handleReplan(state, decision);
                if (state.getReplanCount() >= MAX_REPLANS) {
                    break;
                }
                continue;
            }

            if (!"delegate".equals(type)) {
                log.warn("[ReActAgentExecutor] Unknown decision type '{}' at step {}", type, iteration + 1);
                break;
            }

            String agentName = textValue(decision.get("agentName"));
            String delegatedTask = defaultText(textValue(decision.get("task")), state.getUserMessage());
            String reasoningSummary = defaultText(
                    textValue(decision.get("reasoningSummary")),
                    "模型判断需要委派专业子智能体补充 observation。"
            );
            updateGoalStateFromDecision(state, decision);

            ChatSubAgent subAgent = subAgentRegistry.getAgent(agentName);
            if (subAgent == null) {
                addStep(state, reasoningSummary, defaultText(agentName, "unknown_agent"), Map.of("task", delegatedTask),
                        "子智能体不存在，已跳过该次委派。");
                continue;
            }

            if (isAgentCoolingDown(failedAgentCooldowns, subAgent.name(), iteration)) {
                autoReplanForCoolingDownAgent(state, subAgent.name(), delegatedTask, reasoningSummary, iteration, failedAgentCooldowns);
                if (state.getReplanCount() >= MAX_REPLANS) {
                    break;
                }
                continue;
            }

            if (countAgentUsage(state, subAgent.name()) >= MAX_REPEAT_PER_AGENT) {
                autoReplanForRepeatedAgent(state, subAgent.name(), delegatedTask, reasoningSummary);
                if (state.getReplanCount() >= MAX_REPLANS) {
                    break;
                }
                continue;
            }

            logAgentMessage(
                    EXECUTOR_NAME,
                    subAgent.name(),
                    "REQUEST",
                    reasoningSummary,
                    buildMetadata(state, state.getSteps().size() + 1, Map.of("task", delegatedTask))
            );

            try {
                if (subAgent instanceof AbstractDelegatingSubAgent delegatingSubAgent) {
                    delegatingSubAgent.logSupervisorRequest(state, delegatedTask, reasoningSummary);
                }
                SubAgentResult executionResult = subAgent.execute(state, delegatedTask);
                if (executionResult.success()) {
                    executionResult.usedTools().forEach(state::addUsedTool);
                }
                mergeSubAgentPlanningSignals(state, executionResult);
                addStep(
                        state,
                        reasoningSummary,
                        subAgent.name(),
                        Map.of("task", delegatedTask),
                        defaultText(executionResult.summary(), executionResult.success() ? "子智能体执行完成。" : "子智能体未返回可用 observation。")
                );
                logAgentMessage(
                        subAgent.name(),
                        EXECUTOR_NAME,
                        executionResult.success() ? "INFORM" : "REFUSE",
                        defaultText(executionResult.summary(), executionResult.success() ? "子智能体执行完成。" : "子智能体未返回可用 observation。"),
                        buildMetadata(state, state.getSteps().size(), Map.of("task", delegatedTask, "usedTools", executionResult.usedTools()))
                );
                if (subAgent instanceof AbstractDelegatingSubAgent delegatingSubAgent) {
                    delegatingSubAgent.logSupervisorReply(state, delegatedTask, executionResult.summary(), executionResult.usedTools());
                }
            } catch (RuntimeException ex) {
                String summary = "子智能体执行失败: " + ex.getMessage();
                failedAgentCooldowns.put(subAgent.name(), iteration + FAILED_AGENT_COOLDOWN_STEPS);
                addStep(state, reasoningSummary, subAgent.name(), Map.of("task", delegatedTask), summary);
                addFailureCooldownBlocker(state, subAgent.name());
                logAgentMessage(
                        subAgent.name(),
                        EXECUTOR_NAME,
                        "FAILURE",
                        summary,
                        buildMetadata(state, state.getSteps().size(), Map.of("task", delegatedTask))
                );
            }
        }

        String reply = generateFinalAnswer(state);
        FinalAnswerGuard.GuardResult guardResult = finalAnswerGuard.guard(state, reply);
        reply = guardResult.reply();
        for (String flag : guardResult.flags()) {
            state.addSafetyFlag(flag);
        }
        enforceFinalSafetyHint(state, reply);
        reply = appendDegradationDisclosure(state, reply);
        state.setThoughtSummary(buildThoughtSummary(state));

        return new ReActResult(
                reply,
                state.getAnswerPlan(),
                state.getThoughtSummary(),
                state.getGoal(),
                state.getProgressSummary(),
                state.getNextAction(),
                List.copyOf(state.getBlockers()),
                state.getEvidenceContext(),
                state.getEvidenceCandidates(),
                state.getKnowledgeGraphSummary(),
                state.getKnowledgeGraphItems(),
                state.getUsedTools().stream().toList(),
                state.getSteps(),
                state.getDegradationNotes(),
                state.getSafetyFlags(),
                null
        );
    }

    private void handleReplan(AgentState state, JsonNode decision) {
        String reasoningSummary = defaultText(
                textValue(decision.get("reasoningSummary")),
                "当前路径信息不足，主控准备重新规划。"
        );
        updateGoalStateFromDecision(state, decision);
        state.incrementReplanCount();
        addStep(
                state,
                reasoningSummary,
                "replan",
                Map.of("nextAction", defaultText(state.getNextAction(), "待重新规划")),
                buildReplanObservation(state)
        );
        logAgentMessage(
                EXECUTOR_NAME,
                EXECUTOR_NAME,
                "REPLAN",
                reasoningSummary,
                buildMetadata(state, state.getSteps().size(), Map.of("blockers", state.getBlockers(), "nextAction", state.getNextAction()))
        );
    }

    private void autoReplanForRepeatedAgent(AgentState state,
                                            String agentName,
                                            String delegatedTask,
                                            String reasoningSummary) {
        String blocker = defaultText(agentName, "unknown_agent") + " 重复委派次数已达上限";
        if (!state.getBlockers().contains(blocker)) {
            state.addBlocker(blocker);
        }
        if (state.getProgressSummary() == null || state.getProgressSummary().isBlank()) {
            state.setProgressSummary("当前主路径多次命中同一子智能体，已判定为局部卡住。");
        }
        state.setNextAction("改派其他子智能体补充缺失信息，或在资料不足时直接说明局限。");
        state.incrementReplanCount();
        addStep(
                state,
                defaultText(reasoningSummary, "同一子智能体重复命中上限，主控触发重规划。"),
                "replan",
                Map.of(
                        "blockedAgent", defaultText(agentName, "unknown_agent"),
                        "task", defaultText(delegatedTask, state.getUserMessage())
                ),
                blocker + "，已自动转入重规划。"
        );
        logAgentMessage(
                EXECUTOR_NAME,
                EXECUTOR_NAME,
                "REPLAN",
                blocker,
                buildMetadata(
                        state,
                        state.getSteps().size(),
                        Map.of(
                                "blockedAgent", defaultText(agentName, "unknown_agent"),
                                "task", defaultText(delegatedTask, state.getUserMessage()),
                                "nextAction", state.getNextAction()
                        )
                )
        );
    }

    private boolean isAgentCoolingDown(Map<String, Integer> failedAgentCooldowns, String agentName, int iteration) {
        Integer cooldownUntil = failedAgentCooldowns.get(agentName);
        return cooldownUntil != null && iteration < cooldownUntil;
    }

    private void addFailureCooldownBlocker(AgentState state, String agentName) {
        String blocker = defaultText(agentName, "unknown_agent") + " 最近执行失败，已进入短时冷却";
        if (!state.getBlockers().contains(blocker)) {
            state.addBlocker(blocker);
        }
    }

    private void autoReplanForCoolingDownAgent(AgentState state,
                                               String agentName,
                                               String delegatedTask,
                                               String reasoningSummary,
                                               int iteration,
                                               Map<String, Integer> failedAgentCooldowns) {
        int remaining = Math.max(1, failedAgentCooldowns.getOrDefault(agentName, iteration + 1) - iteration);
        String blocker = defaultText(agentName, "unknown_agent") + " 最近执行失败，冷却中，剩余 " + remaining + " 步";
        if (!state.getBlockers().contains(blocker)) {
            state.addBlocker(blocker);
        }
        if (state.getProgressSummary() == null || state.getProgressSummary().isBlank()) {
            state.setProgressSummary("当前候选子智能体刚刚失败，主路径需要避开短时不稳定节点。");
        }
        state.setNextAction("改派其他子智能体补充信息，或在可用 observation 范围内直接回答。");
        state.incrementReplanCount();
        addStep(
                state,
                defaultText(reasoningSummary, "目标子智能体处于失败冷却期，主控触发重规划。"),
                "replan",
                Map.of(
                        "blockedAgent", defaultText(agentName, "unknown_agent"),
                        "task", defaultText(delegatedTask, state.getUserMessage())
                ),
                blocker + "，已自动转入重规划。"
        );
        logAgentMessage(
                EXECUTOR_NAME,
                EXECUTOR_NAME,
                "REPLAN",
                blocker,
                buildMetadata(
                        state,
                        state.getSteps().size(),
                        Map.of(
                                "blockedAgent", defaultText(agentName, "unknown_agent"),
                                "task", defaultText(delegatedTask, state.getUserMessage()),
                                "nextAction", state.getNextAction()
                        )
                )
        );
    }

    private String buildReplanObservation(AgentState state) {
        List<String> parts = new ArrayList<>();
        if (!state.getBlockers().isEmpty()) {
            parts.add("阻塞项：" + String.join("、", state.getBlockers()));
        }
        if (state.getNextAction() != null && !state.getNextAction().isBlank()) {
            parts.add("改走：" + state.getNextAction().trim());
        }
        if (parts.isEmpty()) {
            parts.add("已触发重规划。");
        }
        return String.join("；", parts);
    }

    private void updateGoalStateFromDecision(AgentState state, JsonNode decision) {
        putIfNotBlank(state::setGoal, textValue(decision.get("goal")));
        putIfNotBlank(state::setProgressSummary, textValue(decision.get("progressSummary")));
        putIfNotBlank(state::setNextAction, textValue(decision.get("nextAction")));
        List<String> blockers = stringArray(decision.get("blockers"));
        if (!blockers.isEmpty()) {
            state.setBlockers(blockers);
        }
    }

    private void mergeSubAgentPlanningSignals(AgentState state, SubAgentResult executionResult) {
        putIfNotBlank(state::setNextAction, executionResult.suggestedNextAction());
        if (executionResult.blockers() != null && !executionResult.blockers().isEmpty()) {
            executionResult.blockers().forEach(state::addBlocker);
        }
        if (executionResult.nextRequiredCapabilities() != null && !executionResult.nextRequiredCapabilities().isEmpty()) {
            String blocker = executionResult.agentName() + " 仍缺能力: " + String.join("、", executionResult.nextRequiredCapabilities());
            if (!state.getBlockers().contains(blocker)) {
                state.addBlocker(blocker);
            }
        }
        if (executionResult.confidence() != null && executionResult.confidence() < 0.55D) {
            String blocker = executionResult.agentName() + " 当前结论置信度偏低";
            if (!state.getBlockers().contains(blocker)) {
                state.addBlocker(blocker);
            }
        }
        if (executionResult.handoffPayload() != null && !executionResult.handoffPayload().isEmpty()) {
            state.addStep(new AgentStep(
                    state.getSteps().size() + 1,
                    executionResult.agentName() + " 生成结构化交接载荷。",
                    executionResult.agentName() + "_handoff",
                    executionResult.handoffPayload(),
                    "已记录交接输入，供后续调度和恢复使用。"
            ));
        }
        if (!executionResult.completed()) {
            String blocker = executionResult.agentName() + " 尚未完成本职任务";
            if (!state.getBlockers().contains(blocker)) {
                state.addBlocker(blocker);
            }
        }
        if (executionResult.requestedSupportAgents() != null && !executionResult.requestedSupportAgents().isEmpty()) {
            List<String> validSupportAgents = executionResult.requestedSupportAgents().stream()
                    .filter(name -> name != null && !name.isBlank())
                    .filter(name -> subAgentRegistry.getAgent(name) != null)
                    .distinct()
                    .toList();
            if (!validSupportAgents.isEmpty()) {
                state.setNextAction("优先协调 " + String.join("、", validSupportAgents) + " 接力处理当前问题。");
                state.addBlocker(executionResult.agentName() + " 请求 " + String.join("、", validSupportAgents) + " 协助。");
            }
        }
        if (executionResult.suggestedHandoffAgent() != null
                && !executionResult.suggestedHandoffAgent().isBlank()
                && subAgentRegistry.getAgent(executionResult.suggestedHandoffAgent()) != null) {
            String nextAction = "下一步优先转交 " + executionResult.suggestedHandoffAgent() + " 继续处理。";
            if (executionResult.confidence() != null) {
                nextAction += " 当前交接置信度 " + Math.round(executionResult.confidence() * 100) + "%。";
            }
            state.setNextAction(nextAction);
            String blocker = executionResult.agentName() + " 建议转交 " + executionResult.suggestedHandoffAgent();
            if (!state.getBlockers().contains(blocker)) {
                state.addBlocker(blocker);
            }
        }
    }

    private void addStep(AgentState state,
                         String reasoningSummary,
                         String actionName,
                         Map<String, Object> actionInput,
                         String observationSummary) {
        state.addStep(new AgentStep(
                state.getSteps().size() + 1,
                reasoningSummary,
                actionName,
                actionInput == null ? Map.of() : actionInput,
                observationSummary
        ));
    }

    private int countAgentUsage(AgentState state, String agentName) {
        int count = 0;
        for (AgentStep step : state.getSteps()) {
            if (agentName.equals(step.actionName())) {
                count++;
            }
        }
        return count;
    }

    private String generateFinalAnswer(AgentState state) {
        if (llmService.isAvailable()) {
            String reply = llmService.chat(
                    promptBuilder.buildFinalAnswerSystemPrompt(state.getConversationPerspective()),
                    promptBuilder.buildFinalAnswerUserPrompt(state)
            );
            if (reply != null && !reply.isBlank() && !isRefusalLike(reply)) {
                return reply;
            }
        }
        return buildFallbackAnswer(state);
    }

    private String buildThoughtSummary(AgentState state) {
        if (state == null) {
            return "";
        }

        StringBuilder summary = new StringBuilder();
        if (state.getGoal() != null && !state.getGoal().isBlank()) {
            summary.append("当前目标：")
                    .append(state.getGoal().trim());
        }

        if (state.getProgressSummary() != null && !state.getProgressSummary().isBlank()) {
            if (summary.length() > 0) {
                summary.append("\n\n");
            }
            summary.append("当前进展：")
                    .append(state.getProgressSummary().trim());
        }

        if (state.getAnswerPlan() != null && !state.getAnswerPlan().isBlank()) {
            if (summary.length() > 0) {
                summary.append("\n\n");
            }
            summary.append("处理目标：")
                    .append(state.getAnswerPlan().trim());
        }

        if (!state.getSteps().isEmpty()) {
            if (summary.length() > 0) {
                summary.append("\n\n");
            }
            summary.append("执行链路：\n");
            int limit = Math.min(3, state.getSteps().size());
            for (int i = 0; i < limit; i++) {
                AgentStep step = state.getSteps().get(i);
                String actionName = step.actionName() == null || step.actionName().isBlank()
                        ? "步骤"
                        : step.actionName();
                String observation = step.observationSummary() == null || step.observationSummary().isBlank()
                        ? "已完成处理"
                        : step.observationSummary().trim();
                summary.append(i + 1)
                        .append(". ")
                        .append(actionName)
                        .append(" -> ")
                        .append(observation);
                if (i < limit - 1) {
                    summary.append('\n');
                }
            }
        }

        if (!state.getDegradationNotes().isEmpty()) {
            if (summary.length() > 0) {
                summary.append("\n\n");
            }
            summary.append("降级处理：")
                    .append(String.join("；", state.getDegradationNotes()));
        }

        if (!state.getSafetyFlags().isEmpty()) {
            if (summary.length() > 0) {
                summary.append("\n\n");
            }
            summary.append("安全检查：已执行必要护栏。");
        }

        if (!state.getBlockers().isEmpty()) {
            if (summary.length() > 0) {
                summary.append("\n\n");
            }
            summary.append("当前阻塞：")
                    .append(String.join("；", state.getBlockers()));
        }

        if (state.getNextAction() != null && !state.getNextAction().isBlank()) {
            if (summary.length() > 0) {
                summary.append("\n\n");
            }
            summary.append("下一步：")
                    .append(state.getNextAction().trim());
        }

        return summary.toString();
    }

    private void enforceFinalSafetyHint(AgentState state, String reply) {
        if (reply == null || reply.isBlank()) {
            return;
        }
        String normalized = reply.toLowerCase();
        if (normalized.contains("及时就医")
                || normalized.contains("线下就医")
                || normalized.contains("尽快就医")
                || normalized.contains("急诊")) {
            state.addSafetyFlag("medical_escalation_included");
        }
    }

    private boolean isRefusalLike(String reply) {
        String normalized = reply == null ? "" : reply.toLowerCase();
        return normalized.contains("敏感信息")
                || normalized.contains("换个话题")
                || normalized.contains("不能继续这个话题")
                || normalized.contains("无法协助处理该请求");
    }

    private String appendDegradationDisclosure(AgentState state, String reply) {
        if (state.getUnavailableExternalSources().isEmpty()) {
            return reply;
        }
        if (reply == null || reply.isBlank()) {
            return "本次分析基于平台内本地数据完成；外部数据源当前不可用。";
        }
        if (reply.contains("基于平台内本地数据") || reply.contains("基于本地数据")) {
            return reply;
        }
        return reply + "\n\n说明：本次分析基于平台内本地数据完成，外部 MCP/Medplum 数据当前不可用或未接入。";
    }

    private String buildFallbackAnswer(AgentState state) {
        StringBuilder sb = new StringBuilder();
        if ("doctor".equalsIgnoreCase(state.getOwnerRole())) {
            sb.append("医生您好。");
        } else {
            sb.append("您好。");
        }

        if (!state.getAssessmentResults().isEmpty()) {
            sb.append("根据当前规则评估，");
            state.getAssessmentResults().stream().limit(2).forEach(item ->
                    sb.append(item.diseaseType().getDisplayName())
                            .append("为")
                            .append(item.riskLevel().getDisplayName())
                            .append("；"));
        } else if (state.getPatientContext() != null && state.getPatientContext().promptContext() != null) {
            sb.append("我已结合当前患者上下文进行分析。");
        } else if (!state.getEvidenceCandidates().isEmpty()) {
            RagEvidenceDTO firstEvidence = state.getEvidenceCandidates().get(0);
            sb.append("我已结合相关慢病管理资料进行分析。");
            if (firstEvidence.getTitle() != null && !firstEvidence.getTitle().isBlank()) {
                sb.append("当前命中的首条资料为《").append(firstEvidence.getTitle()).append("》。");
            }
        } else {
            sb.append("目前可用信息有限。");
        }

        if (state.getSafetyFlags().contains("high_risk_detected")) {
            sb.append("若患者存在明显不适或指标持续升高，请及时线下就医。");
        }
        return sb.toString();
    }

    private String textValue(JsonNode node) {
        return node == null || node.isNull() ? null : node.asText(null);
    }

    private String defaultText(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private List<String> stringArray(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        node.forEach(item -> {
            String value = textValue(item);
            if (value != null && !value.isBlank()) {
                values.add(value.trim());
            }
        });
        return values;
    }

    private void putIfNotBlank(java.util.function.Consumer<String> consumer, String value) {
        if (value != null && !value.isBlank()) {
            consumer.accept(value.trim());
        }
    }

    private Map<String, Object> buildMetadata(AgentState state, int stepIndex, Map<String, Object> input) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("flowType", "chat");
        putIfPresent(metadata, "sessionId", state.getSessionId());
        putIfPresent(metadata, "ownerRole", state.getOwnerRole());
        putIfPresent(metadata, "ownerUserId", state.getOwnerUserId());
        putIfPresent(metadata, "patientId", state.getPatientId());
        metadata.put("stepIndex", stepIndex);
        if (input != null && !input.isEmpty()) {
            metadata.put("input", input);
        }
        return metadata;
    }

    private void putIfPresent(Map<String, Object> metadata, String key, Object value) {
        if (value instanceof String stringValue) {
            if (!stringValue.isBlank()) {
                metadata.put(key, stringValue);
            }
            return;
        }
        if (value != null) {
            metadata.put(key, value);
        }
    }

    private void logAgentMessage(String sender,
                                 String receiver,
                                 String performative,
                                 String content,
                                 Map<String, Object> metadata) {
        if (agentMessageAuditService != null) {
            agentMessageAuditService.log(sender, receiver, performative, content, metadata);
        }
    }

    private ReActResult emptyResult(String reply) {
        return new ReActResult(
                reply,
                null,
                null,
                null,
                null,
                null,
                List.of(),
                (RagQueryContextDTO) null,
                List.<RagEvidenceDTO>of(),
                null,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                Set.of(),
                null
        );
    }
}
