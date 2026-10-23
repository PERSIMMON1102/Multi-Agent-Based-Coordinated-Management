package com.medical.agent.service.chat.agent;

import com.medical.agent.service.react.model.AgentState;
import com.medical.agent.service.react.tool.MedplumPatientInsightTool;
import com.medical.agent.service.react.tool.ToolExecutionResult;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class MedplumPatientInsightSpecialistAgent implements ChatSpecialistAgent {

    private final MedplumPatientInsightTool medplumPatientInsightTool;

    public MedplumPatientInsightSpecialistAgent(MedplumPatientInsightTool medplumPatientInsightTool) {
        this.medplumPatientInsightTool = medplumPatientInsightTool;
    }

    @Override
    public String name() {
        return "medplum_patient_insight_agent";
    }

    @Override
    public int order() {
        return 15;
    }

    @Override
    public boolean shouldEngage(AgentState state) {
        return state != null && state.getPatientId() != null;
    }

    @Override
    public ChatSpecialistExecutionResult execute(AgentState state) {
        Map<String, Object> input = Map.of("limit", 5);
        ToolExecutionResult result = medplumPatientInsightTool.execute(state, input);
        return new ChatSpecialistExecutionResult(
                result.success(),
                "由 Medplum 云端患者视图智能体补充标准 FHIR Patient 与最新 Observation。",
                input,
                result.summary()
        );
    }
}
