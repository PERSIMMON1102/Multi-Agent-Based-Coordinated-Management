package com.medical.agent.service.reference.agent;

import com.medical.agent.common.dto.RagEvidenceDTO;
import com.medical.agent.service.AgentMessageAuditService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ReferenceSelectionSupervisorService {

    private static final String SUPERVISOR_NAME = "ReferenceSelectionSupervisor";
    private static final int MAX_SPECIALISTS = 3;

    private final AgentMessageAuditService agentMessageAuditService;
    private final List<ReferenceSelectionSpecialistAgent> specialistAgents;

    @Autowired
    public ReferenceSelectionSupervisorService(AgentMessageAuditService agentMessageAuditService,
                                               List<ReferenceSelectionSpecialistAgent> specialistAgents) {
        this.agentMessageAuditService = agentMessageAuditService;
        this.specialistAgents = specialistAgents.stream()
                .sorted(java.util.Comparator.comparingInt(ReferenceSelectionSpecialistAgent::order))
                .toList();
    }

    public ReferenceSelectionSupervisorService(List<ReferenceSelectionSpecialistAgent> specialistAgents) {
        this(null, specialistAgents);
    }

    public List<RagEvidenceDTO> judgeChatDisplayReferences(String userQuestion,
                                                           String reply,
                                                           List<RagEvidenceDTO> candidates,
                                                           int limit) {
        ReferenceSelectionState state = new ReferenceSelectionState();
        state.setMode(ReferenceSelectionState.Mode.CHAT);
        state.setUserQuestion(userQuestion);
        state.setReply(reply);
        state.setCandidates(candidates);
        state.setLimit(limit);
        run(state);
        return state.getSelectedReferences().isEmpty() ? null : state.getSelectedReferences();
    }

    public List<RagEvidenceDTO> judgeContentDisplayReferences(String topicKey,
                                                              String generatedContent,
                                                              List<RagEvidenceDTO> candidates,
                                                              int limit) {
        ReferenceSelectionState state = new ReferenceSelectionState();
        state.setMode(ReferenceSelectionState.Mode.CONTENT);
        state.setTopicKey(topicKey);
        state.setGeneratedContent(generatedContent);
        state.setCandidates(candidates);
        state.setLimit(limit);
        run(state);
        return state.getSelectedReferences().isEmpty() ? null : state.getSelectedReferences();
    }

    private void run(ReferenceSelectionState state) {
        List<ReferenceSelectionSpecialistAgent> plan = specialistAgents.stream()
                .limit(MAX_SPECIALISTS)
                .toList();
        for (ReferenceSelectionSpecialistAgent specialistAgent : plan) {
            int stepIndex = state.getAgentSteps().size() + 1;
            logAgentMessage(
                    SUPERVISOR_NAME,
                    specialistAgent.name(),
                    "REQUEST",
                    buildRequestContent(state),
                    buildMetadata(state, stepIndex)
            );
            try {
                specialistAgent.execute(state);
                logAgentMessage(
                        specialistAgent.name(),
                        SUPERVISOR_NAME,
                        "INFORM",
                        resolveStepSummary(state.getAgentSteps(), stepIndex - 1, "已完成引用筛选"),
                        buildMetadata(state, stepIndex)
                );
            } catch (RuntimeException ex) {
                logAgentMessage(
                        specialistAgent.name(),
                        SUPERVISOR_NAME,
                        "FAILURE",
                        "执行失败: " + ex.getMessage(),
                        buildMetadata(state, stepIndex)
                );
                throw ex;
            }
        }
    }

    private String buildRequestContent(ReferenceSelectionState state) {
        if (state.getMode() == ReferenceSelectionState.Mode.CHAT) {
            return "筛选聊天引用: " + preview(state.getUserQuestion());
        }
        return "筛选内容引用: " + preview(state.getTopicKey());
    }

    private Map<String, Object> buildMetadata(ReferenceSelectionState state, int stepIndex) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("flowType", "reference_selection");
        metadata.put("mode", state.getMode() == null ? "UNKNOWN" : state.getMode().name());
        metadata.put("candidateCount", state.getCandidates().size());
        metadata.put("selectedLimit", state.getLimit());
        metadata.put("stepIndex", stepIndex);
        return metadata;
    }

    private String resolveStepSummary(List<Map<String, Object>> steps, int previousSize, String fallback) {
        if (steps == null || steps.size() <= previousSize) {
            return fallback;
        }
        Object summary = steps.get(steps.size() - 1).get("summary");
        if (summary == null || summary.toString().isBlank()) {
            return fallback;
        }
        return summary.toString();
    }

    private String preview(String text) {
        if (text == null || text.isBlank()) {
            return "未提供上下文";
        }
        String normalized = text.trim();
        return normalized.length() <= 60 ? normalized : normalized.substring(0, 57) + "...";
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
}
