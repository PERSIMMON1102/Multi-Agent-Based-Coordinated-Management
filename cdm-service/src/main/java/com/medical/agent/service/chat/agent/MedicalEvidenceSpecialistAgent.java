package com.medical.agent.service.chat.agent;

import com.medical.agent.service.react.model.AgentState;
import com.medical.agent.service.react.tool.MedicalEvidenceSearchTool;
import com.medical.agent.service.react.tool.ToolExecutionResult;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class MedicalEvidenceSpecialistAgent implements ChatSpecialistAgent {

    private final MedicalEvidenceSearchTool medicalEvidenceSearchTool;

    public MedicalEvidenceSpecialistAgent(MedicalEvidenceSearchTool medicalEvidenceSearchTool) {
        this.medicalEvidenceSearchTool = medicalEvidenceSearchTool;
    }

    @Override
    public String name() {
        return "medical_evidence_agent";
    }

    @Override
    public int order() {
        return 30;
    }

    @Override
    public boolean shouldEngage(AgentState state) {
        return state != null && state.getUserMessage() != null && !state.getUserMessage().isBlank();
    }

    @Override
    public ChatSpecialistExecutionResult execute(AgentState state) {
        Map<String, Object> input = Map.of("focus", state.getUserMessage());
        ToolExecutionResult result = medicalEvidenceSearchTool.execute(state, input);
        return new ChatSpecialistExecutionResult(
                result.success(),
                "由慢病证据智能体检索与当前问题最相关的知识库和指南证据。",
                input,
                result.summary()
        );
    }
}
