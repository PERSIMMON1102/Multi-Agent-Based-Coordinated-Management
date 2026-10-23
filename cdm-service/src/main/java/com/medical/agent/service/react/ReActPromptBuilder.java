package com.medical.agent.service.react;

import com.medical.agent.common.dto.RagEvidenceDTO;
import com.medical.agent.service.chat.RuleAssessmentResult;
import com.medical.agent.service.react.agent.ChatSubAgent;
import com.medical.agent.service.react.model.AgentState;
import com.medical.agent.service.react.model.AgentStep;
import com.medical.agent.service.react.tool.AgentTool;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class ReActPromptBuilder {

    public String buildDecisionSystemPrompt(List<ChatSubAgent> subAgents, String conversationPerspective) {
        String perspective = conversationPerspective == null ? "" : conversationPerspective;
        String subAgentBlock = subAgents.stream()
                .map(agent -> "- " + agent.routeKey() + ": " + agent.description())
                .collect(Collectors.joining("\n"));

        return "你是「慢病管家」的 ReAct 决策器。你的职责是先判断需不需要调用工具，再决定最终如何回答。\n" +
                "你必须遵守：\n" +
                "1. 优先把任务委派给最合适的专业子智能体，只有在 observation 已足够时才直接进入最终回答\n" +
                "2. 不得编造患者数据、评估结果或文献\n" +
                "3. 若出现高风险线索，优先考虑委派 risk_assessment_agent\n" +
                "4. 你需要持续维护任务状态，明确 goal、progressSummary、blockers、nextAction\n" +
                "5. 当当前路径信息不足、工具失败或 observation 冲突时，可以输出 replan，重新规划下一步\n" +
                "6. 如果 blockers 或已执行步骤已经表明某个子智能体已被委派过，不得继续委派同一个子智能体，应优先 replan 或改派其他子智能体\n" +
                "7. 如果 blockers 或已执行步骤表明某个子智能体刚刚失败且仍在冷却中，不得再次选择它\n" +
                "8. 只输出 JSON，不输出额外文字\n" +
                "9. JSON 结构只能是以下三种之一：\n" +
                "   {\"type\":\"delegate\",\"agentName\":\"risk_assessment_agent\",\"task\":\"...\",\"reasoningSummary\":\"...\",\"goal\":\"...\",\"progressSummary\":\"...\",\"blockers\":[\"...\"],\"nextAction\":\"...\"}\n" +
                "   {\"type\":\"replan\",\"reasoningSummary\":\"...\",\"goal\":\"...\",\"progressSummary\":\"...\",\"blockers\":[\"...\"],\"nextAction\":\"...\"}\n" +
                "   {\"type\":\"final_answer\",\"answerPlan\":\"...\",\"reasoningSummary\":\"...\",\"goal\":\"...\",\"progressSummary\":\"...\",\"blockers\":[\"...\"],\"nextAction\":\"...\"}\n" +
                "10. 当前场景是院内/平台内已授权的慢病管理分析，不要因为患者信息而拒答，也不要输出“涉及敏感信息、换个话题”之类的安全套话；若信息不足，应直接说明缺什么。\n\n" +
                perspective + "\n\n" +
                "可用子智能体：\n" + subAgentBlock;
    }

    public String buildDecisionUserPrompt(AgentState state) {
        StringBuilder sb = new StringBuilder();
        sb.append("【用户本轮问题】\n").append(state.getUserMessage()).append("\n\n");
        appendHistory(sb, state.getHistoryMessages());
        appendObservations(sb, state);
        sb.append("请判断下一步是调用工具还是直接进入最终回答。");
        return sb.toString();
    }

    public String buildFinalAnswerSystemPrompt(String conversationPerspective) {
        String perspective = conversationPerspective == null ? "" : conversationPerspective;
        return "你是「慢病管家」，一个基层慢病协同管理平台的 AI 健康顾问。\n" +
                "你的最终回答必须：\n" +
                "1. 严格基于已有 observation 作答，不得编造未观察到的事实\n" +
                "2. 语言专业、克制、清晰\n" +
                "3. 若为医生端，面向医生回答；若为患者端，面向患者本人回答\n" +
                "4. 若信息不足，明确说明局限\n" +
                "5. 若存在高风险或危急提示，必须提醒及时线下就医\n" +
                "6. 当前属于平台内已授权的慢病管理问答，不要输出“涉及敏感信息、让我们换个话题”等拒答套话；如果缺资料，就直接指出缺少哪些资料。\n\n" +
                perspective;
    }

    public String buildFinalAnswerUserPrompt(AgentState state) {
        StringBuilder sb = new StringBuilder();
        sb.append("【用户问题】\n").append(state.getUserMessage()).append("\n\n");
        if (state.getAnswerPlan() != null && !state.getAnswerPlan().isBlank()) {
            sb.append("【回答计划】\n").append(state.getAnswerPlan()).append("\n\n");
        }
        appendObservations(sb, state);
        sb.append("请基于以上 observation 生成最终回答，不要暴露内部推理过程。");
        return sb.toString();
    }

    public String buildSubAgentDecisionSystemPrompt(String agentName,
                                                    String description,
                                                    List<AgentTool> tools,
                                                    String conversationPerspective) {
        String perspective = conversationPerspective == null ? "" : conversationPerspective;
        String toolBlock = tools.stream()
                .map(tool -> "- " + tool.name() + ": " + tool.description())
                .collect(Collectors.joining("\n"));
        return "你是专业子智能体 " + agentName + "。\n" +
                "你的职责：" + description + "\n" +
                "你现在是多智能体协作链路中的一个独立节点，需要先自己判断该调用哪些底层能力，再返回你的专业结论。\n" +
                "你必须遵守：\n" +
                "1. 只使用授权给你的工具，不能越权调用其他工具\n" +
                "2. 如 observation 已足够，可以直接给出 final，总结你完成了什么\n" +
                "3. 不得编造患者资料、评估结果或证据\n" +
                "4. 只输出 JSON，不输出额外文字\n" +
                "5. JSON 结构只能是以下两种之一：\n" +
                "   {\"type\":\"tool_call\",\"toolName\":\"patient_context\",\"toolInput\":{},\"reasoningSummary\":\"...\"}\n" +
                "   {\"type\":\"final\",\"contractSchema\":\"sub-agent-result\",\"contractVersion\":\"1.0\",\"summary\":\"...\",\"completed\":true,\"requestedSupportAgents\":[\"...\"],\"suggestedHandoffAgent\":\"...\",\"blockers\":[\"...\"],\"suggestedNextAction\":\"...\",\"failureCategory\":\"context_gap|evidence_gap|risk_reassessment|tool_or_runtime_failure|missing_agent|general_failure\",\"missingCapabilities\":[\"...\"],\"confidence\":0.0,\"evidenceRefs\":[\"...\"],\"artifacts\":[{\"kind\":\"...\",\"summary\":\"...\"}],\"handoffPayload\":{\"key\":\"value\"},\"nextRequiredCapabilities\":[\"...\"]}\n" +
                "6. 当你已完成本职分析时，completed=true；当你只能部分完成、需要其他子智能体接力时，completed=false，并明确给出 requestedSupportAgents 或 suggestedHandoffAgent\n" +
                "7. 如果你要求接力，尽量补充 confidence、evidenceRefs、artifacts、handoffPayload、nextRequiredCapabilities，便于后续调度和人工恢复\n" +
                "8. final 结果必须始终输出 contractSchema=sub-agent-result 且 contractVersion=1.0\n\n" +
                perspective + "\n\n" +
                "你可用的底层工具：\n" + toolBlock;
    }

    public String buildSubAgentDecisionUserPrompt(AgentState state,
                                                  String delegatedTask,
                                                  List<String> localNotes) {
        StringBuilder sb = new StringBuilder();
        sb.append("【主控委派任务】\n").append(delegatedTask == null ? "" : delegatedTask).append("\n\n");
        sb.append("【用户本轮问题】\n").append(state.getUserMessage()).append("\n\n");
        appendHistory(sb, state.getHistoryMessages());
        appendObservations(sb, state);
        if (localNotes != null && !localNotes.isEmpty()) {
            sb.append("【你已完成的子智能体动作】\n");
            for (String note : localNotes) {
                sb.append("- ").append(note).append('\n');
            }
            sb.append('\n');
        }
        sb.append("请判断下一步是调用某个底层工具还是结束并输出你的专业总结。");
        return sb.toString();
    }

    private void appendHistory(StringBuilder sb, List<Map<String, String>> historyMessages) {
        if (historyMessages == null || historyMessages.isEmpty()) {
            return;
        }
        sb.append("【最近对话历史】\n");
        historyMessages.stream().skip(Math.max(0, historyMessages.size() - 6)).forEach(message -> {
            String role = message.getOrDefault("role", "unknown");
            String content = message.getOrDefault("content", "");
            sb.append("- ").append(role).append("：").append(content).append('\n');
        });
        sb.append('\n');
    }

    private void appendObservations(StringBuilder sb, AgentState state) {
        if (state.getSteps().isEmpty() && state.getPatientContext() == null
                && state.getAssessmentResults().isEmpty()
                && state.getEvidenceCandidates().isEmpty()) {
            return;
        }
        sb.append("【当前 observation】\n");
        if (state.getPatientContext() != null && state.getPatientContext().promptContext() != null) {
            sb.append("患者上下文：\n")
                    .append(state.getPatientContext().focusedPromptContext(state.getUserMessage()))
                    .append("\n");
        }
        if (!state.getAssessmentResults().isEmpty()) {
            sb.append("规则评估结果：\n");
            for (RuleAssessmentResult result : state.getAssessmentResults()) {
                sb.append("- ").append(result.diseaseType().getDisplayName())
                        .append("：").append(result.riskLevel().getDisplayName());
                if (result.detail() != null && !result.detail().isBlank()) {
                    sb.append("，").append(result.detail());
                }
                sb.append('\n');
            }
        }
        if (!state.getEvidenceCandidates().isEmpty()) {
            sb.append("证据候选：\n");
            int limit = Math.min(3, state.getEvidenceCandidates().size());
            for (int i = 0; i < limit; i++) {
                RagEvidenceDTO item = state.getEvidenceCandidates().get(i);
                sb.append("- ").append(item.getTitle() == null ? "知识片段" : item.getTitle());
                if (item.getContentSnippet() != null && !item.getContentSnippet().isBlank()) {
                    sb.append("：").append(item.getContentSnippet());
                }
                sb.append('\n');
            }
        }
        if (state.getKnowledgeGraphSummary() != null && !state.getKnowledgeGraphSummary().isBlank()) {
            sb.append("知识图谱支撑：\n").append(state.getKnowledgeGraphSummary()).append('\n');
        }
        if (!state.getSteps().isEmpty()) {
            sb.append("已执行步骤：\n");
            for (AgentStep step : state.getSteps()) {
                sb.append("- step ").append(step.index()).append(" / ").append(step.actionName())
                        .append("：").append(step.observationSummary()).append('\n');
            }
        }
        if (!state.getUnavailableExternalSources().isEmpty()) {
            sb.append("不可用外部数据源：").append(String.join("、", state.getUnavailableExternalSources())).append('\n');
        }
        if (state.getGoal() != null && !state.getGoal().isBlank()) {
            sb.append("当前目标：").append(state.getGoal()).append('\n');
        }
        if (state.getProgressSummary() != null && !state.getProgressSummary().isBlank()) {
            sb.append("当前进展：").append(state.getProgressSummary()).append('\n');
        }
        if (state.getNextAction() != null && !state.getNextAction().isBlank()) {
            sb.append("建议下一步：").append(state.getNextAction()).append('\n');
        }
        if (!state.getBlockers().isEmpty()) {
            sb.append("当前阻塞：").append(String.join("、", state.getBlockers())).append('\n');
        }
        if (!state.getDegradationNotes().isEmpty()) {
            sb.append("降级说明：\n");
            for (String note : state.getDegradationNotes()) {
                sb.append("- ").append(note).append('\n');
            }
        }
        if (!state.getSafetyFlags().isEmpty()) {
            sb.append("安全标记：").append(String.join("、", state.getSafetyFlags())).append('\n');
        }
        sb.append('\n');
    }
}
