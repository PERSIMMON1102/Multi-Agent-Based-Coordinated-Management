package com.medical.agent.service.react.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.medical.agent.service.AgentMessageAuditService;
import com.medical.agent.service.LlmService;
import com.medical.agent.service.react.ReActPromptBuilder;
import com.medical.agent.service.react.model.AgentState;
import com.medical.agent.service.react.tool.AgentTool;
import com.medical.agent.service.react.tool.ToolExecutionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public abstract class AbstractDelegatingSubAgent implements ChatSubAgent {

    private static final Logger log = LoggerFactory.getLogger(AbstractDelegatingSubAgent.class);
    private static final String SUPERVISOR_NAME = "ChatReActExecutor";
    private static final int MAX_INTERNAL_STEPS = 3;
    private static final int MAX_REPEAT_PER_TOOL = 2;
    private static final String MEDPLUM_TOOL = "medplum_patient_insight";

    private final LlmService llmService;
    private final ReActPromptBuilder promptBuilder;
    private final AgentMessageAuditService auditService;
    private final Map<String, AgentTool> toolMap;
    private final SubAgentResultContractSupport contractSupport;

    protected AbstractDelegatingSubAgent(LlmService llmService,
                                         ReActPromptBuilder promptBuilder,
                                         AgentMessageAuditService auditService,
                                         SubAgentResultContractSupport contractSupport,
                                         List<AgentTool> tools) {
        this.llmService = llmService;
        this.promptBuilder = promptBuilder;
        this.auditService = auditService;
        this.contractSupport = contractSupport;
        LinkedHashMap<String, AgentTool> registered = new LinkedHashMap<>();
        for (AgentTool tool : tools) {
            registered.put(tool.name(), tool);
        }
        this.toolMap = registered;
    }

    @Override
    public List<AgentTool> tools() {
        return List.copyOf(toolMap.values());
    }

    @Override
    public SubAgentResult execute(AgentState state, String delegatedTask) {
        if (state == null) {
            return new SubAgentResult(name(), false, "上下文缺失，无法执行子智能体分析。", List.of());
        }

        if (!llmService.isAvailable()) {
            return executeFallback(state, delegatedTask);
        }

        List<String> localNotes = new ArrayList<>();
        Set<String> usedTools = new LinkedHashSet<>();
        for (int iteration = 0; iteration < MAX_INTERNAL_STEPS; iteration++) {
            JsonNode decision = llmService.chatJsonObject(
                    promptBuilder.buildSubAgentDecisionSystemPrompt(name(), description(), tools(), state.getConversationPerspective()),
                    promptBuilder.buildSubAgentDecisionUserPrompt(state, delegatedTask, localNotes)
            );
            if (decision == null || !decision.isObject()) {
                log.warn("[{}] No structured decision returned, fallback to heuristic path", name());
                return executeFallback(state, delegatedTask);
            }

            String type = textValue(decision.get("type"));
            if ("final".equals(type)) {
                String summary = defaultText(textValue(decision.get("summary")), buildFallbackSummary(delegatedTask, localNotes));
                boolean completed = booleanValue(decision.get("completed"), !usedTools.isEmpty() || !summary.isBlank());
                String suggestedNextAction = textValue(decision.get("suggestedNextAction"));
                List<String> blockers = contractSupport.stringArray(decision.get("blockers"));
                List<String> requestedSupportAgents = contractSupport.stringArray(decision.get("requestedSupportAgents"));
                String suggestedHandoffAgent = textValue(decision.get("suggestedHandoffAgent"));
                String failureCategory = textValue(decision.get("failureCategory"));
                List<String> missingCapabilities = contractSupport.stringArray(decision.get("missingCapabilities"));
                Double confidence = contractSupport.doubleValue(decision.get("confidence"));
                List<String> evidenceRefs = contractSupport.stringArray(decision.get("evidenceRefs"));
                List<Map<String, Object>> artifacts = contractSupport.objectArray(decision.get("artifacts"));
                Map<String, Object> handoffPayload = contractSupport.readObject(decision.get("handoffPayload"));
                List<String> nextRequiredCapabilities = contractSupport.stringArray(decision.get("nextRequiredCapabilities"));
                return contractSupport.normalizeFromJson(
                        name(),
                        true,
                        completed,
                        summary,
                        new ArrayList<>(usedTools),
                        suggestedNextAction,
                        blockers,
                        requestedSupportAgents,
                        suggestedHandoffAgent,
                        failureCategory,
                        missingCapabilities,
                        confidence,
                        evidenceRefs,
                        artifacts,
                        handoffPayload,
                        nextRequiredCapabilities,
                        contractSupport.textValue(decision.get("contractSchema")),
                        contractSupport.textValue(decision.get("contractVersion"))
                );
            }

            if (!"tool_call".equals(type)) {
                log.warn("[{}] Unknown decision type '{}', fallback to heuristic path", name(), type);
                return executeFallback(state, delegatedTask);
            }

            String toolName = textValue(decision.get("toolName"));
            AgentTool tool = toolMap.get(toolName);
            Map<String, Object> toolInput = contractSupport.readObject(decision.get("toolInput"));
            String reasoningSummary = defaultText(textValue(decision.get("reasoningSummary")), "子智能体需要补充结构化信息。");
            if (tool == null) {
                localNotes.add("候选工具 " + defaultText(toolName, "unknown_tool") + " 不在该子智能体授权范围内，已跳过。");
                continue;
            }

            if (isUnavailableExternalTool(state, tool.name())) {
                localNotes.add("外部数据源当前不可用，已跳过重复尝试并继续基于本地数据分析。");
                continue;
            }

            if (countUsage(localNotes, tool.name()) >= MAX_REPEAT_PER_TOOL) {
                localNotes.add(tool.name() + " 重复调用次数已达上限，已停止继续尝试。");
                break;
            }

            logMessage(name(), tool.name(), "REQUEST", reasoningSummary, buildMetadata(state, delegatedTask, toolInput));
            ToolExecutionResult executionResult;
            try {
                executionResult = tool.execute(state, toolInput);
            } catch (RuntimeException ex) {
                String summary = "工具执行失败: " + ex.getMessage();
                localNotes.add(summary);
                logMessage(tool.name(), name(), "FAILURE", summary, buildMetadata(state, delegatedTask, toolInput));
                continue;
            }

            if (executionResult.success()) {
                usedTools.add(tool.name());
            }
            localNotes.add(tool.name() + "：" + defaultText(executionResult.summary(), executionResult.success() ? "执行完成。" : "未获取到可用结果。"));
            logMessage(
                    tool.name(),
                    name(),
                    executionResult.success() ? "INFORM" : "REFUSE",
                    defaultText(executionResult.summary(), executionResult.success() ? "执行完成。" : "未获取到可用结果。"),
                    buildMetadata(state, delegatedTask, toolInput)
            );
        }

        return new SubAgentResult(
                name(),
                !usedTools.isEmpty(),
                !usedTools.isEmpty(),
                buildFallbackSummary(delegatedTask, localNotes),
                new ArrayList<>(usedTools)
        );
    }

    protected abstract SubAgentResult executeFallback(AgentState state, String delegatedTask);

    protected SubAgentResult runTools(AgentState state, String delegatedTask, List<ToolInvocation> invocations, String completionSummary) {
        if (invocations == null || invocations.isEmpty()) {
            return new SubAgentResult(name(), false, false, "当前没有可执行的子智能体动作。", List.of());
        }
        List<String> notes = new ArrayList<>();
        Set<String> usedTools = new LinkedHashSet<>();
        for (ToolInvocation invocation : invocations) {
            if (invocation == null || invocation.toolName() == null || invocation.toolName().isBlank()) {
                continue;
            }
            AgentTool tool = toolMap.get(invocation.toolName());
            if (tool == null) {
                notes.add("工具 " + invocation.toolName() + " 不在授权范围内。");
                continue;
            }
            if (isUnavailableExternalTool(state, tool.name())) {
                notes.add("外部数据源当前不可用，已跳过重复尝试并继续基于本地数据分析。");
                continue;
            }
            logMessage(name(), tool.name(), "REQUEST", defaultText(invocation.reasoningSummary(), "执行子智能体动作。"),
                    buildMetadata(state, delegatedTask, invocation.toolInput()));
            ToolExecutionResult result = tool.execute(state, invocation.toolInput());
            notes.add(tool.name() + "：" + defaultText(result.summary(), result.success() ? "执行完成。" : "未获取到可用结果。"));
            if (result.success()) {
                usedTools.add(tool.name());
            }
            logMessage(tool.name(), name(), result.success() ? "INFORM" : "REFUSE",
                    defaultText(result.summary(), result.success() ? "执行完成。" : "未获取到可用结果。"),
                    buildMetadata(state, delegatedTask, invocation.toolInput()));
        }
        String summary = completionSummary;
        if ((summary == null || summary.isBlank()) && !notes.isEmpty()) {
            summary = buildFallbackSummary(delegatedTask, notes);
        }
        return new SubAgentResult(
                name(),
                !usedTools.isEmpty(),
                !usedTools.isEmpty(),
                defaultText(summary, "子智能体已完成本轮分析。"),
                new ArrayList<>(usedTools)
        );
    }

    protected String buildFallbackSummary(String delegatedTask, List<String> localNotes) {
        StringBuilder summary = new StringBuilder("子智能体已围绕任务");
        if (delegatedTask != null && !delegatedTask.isBlank()) {
            summary.append("“").append(delegatedTask.trim()).append("”");
        }
        summary.append("完成分析");
        if (localNotes != null && !localNotes.isEmpty()) {
            summary.append("：").append(String.join("；", localNotes));
        } else {
            summary.append("。");
        }
        return summary.toString();
    }

    protected Map<String, Object> mapOf(Object... values) {
        LinkedHashMap<String, Object> map = new LinkedHashMap<>();
        if (values == null) {
            return map;
        }
        for (int i = 0; i + 1 < values.length; i += 2) {
            Object key = values[i];
            Object value = values[i + 1];
            if (key != null && value != null) {
                map.put(String.valueOf(key), value);
            }
        }
        return map;
    }

    public void logSupervisorRequest(AgentState state, String delegatedTask, String reasoningSummary) {
        logMessage(SUPERVISOR_NAME, name(), "REQUEST", reasoningSummary, buildMetadata(state, delegatedTask, Map.of()));
    }

    public void logSupervisorReply(AgentState state, String delegatedTask, String summary, List<String> usedTools) {
        LinkedHashMap<String, Object> metadata = new LinkedHashMap<>(buildMetadata(state, delegatedTask, Map.of()));
        if (usedTools != null && !usedTools.isEmpty()) {
            metadata.put("usedTools", usedTools);
        }
        logMessage(name(), SUPERVISOR_NAME, "INFORM", summary, metadata);
    }

    private int countUsage(List<String> notes, String toolName) {
        int count = 0;
        for (String note : notes) {
            if (note != null && note.startsWith(toolName + "：")) {
                count++;
            }
        }
        return count;
    }

    private boolean isUnavailableExternalTool(AgentState state, String toolName) {
        return MEDPLUM_TOOL.equals(toolName)
                && state.getUnavailableExternalSources().stream()
                .anyMatch(source -> toolName.equalsIgnoreCase(source));
    }

    private String textValue(JsonNode node) {
        return contractSupport.textValue(node);
    }

    private boolean booleanValue(JsonNode node, boolean fallback) {
        return contractSupport.booleanValue(node, fallback);
    }

    private String defaultText(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private Map<String, Object> buildMetadata(AgentState state, String delegatedTask, Map<String, Object> toolInput) {
        LinkedHashMap<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("flowType", "chat-sub-agent");
        if (state.getSessionId() != null && !state.getSessionId().isBlank()) {
            metadata.put("sessionId", state.getSessionId());
        }
        if (state.getOwnerRole() != null && !state.getOwnerRole().isBlank()) {
            metadata.put("ownerRole", state.getOwnerRole());
        }
        if (state.getOwnerUserId() != null) {
            metadata.put("ownerUserId", state.getOwnerUserId());
        }
        if (state.getPatientId() != null) {
            metadata.put("patientId", state.getPatientId());
        }
        if (delegatedTask != null && !delegatedTask.isBlank()) {
            metadata.put("delegatedTask", delegatedTask);
        }
        if (toolInput != null && !toolInput.isEmpty()) {
            metadata.put("toolInput", toolInput);
        }
        return metadata;
    }

    private void logMessage(String sender, String receiver, String performative, String content, Map<String, Object> metadata) {
        if (auditService != null) {
            auditService.log(sender, receiver, performative, content, metadata);
        }
    }

    public record ToolInvocation(String toolName, Map<String, Object> toolInput, String reasoningSummary) {
    }
}
