package com.medical.agent.service.chat.agent;

import com.medical.agent.service.react.model.AgentState;
import com.medical.agent.service.react.tool.PatientContextTool;
import com.medical.agent.service.react.tool.ToolExecutionResult;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class PatientContextSpecialistAgent implements ChatSpecialistAgent {

    private final PatientContextTool patientContextTool;

    public PatientContextSpecialistAgent(PatientContextTool patientContextTool) {
        this.patientContextTool = patientContextTool;
    }

    @Override
    public String name() {
        return "patient_context_agent";
    }

    @Override
    public int order() {
        return 10;
    }

    @Override
    public boolean shouldEngage(AgentState state) {
        return state != null && state.getPatientId() != null;
    }

    @Override
    public ChatSpecialistExecutionResult execute(AgentState state) {
        ToolExecutionResult result = patientContextTool.execute(state, Map.of());
        return new ChatSpecialistExecutionResult(
                result.success(),
                "由患者上下文智能体读取基础资料、病史档案、近期评估、随访计划和最新健康记录。",
                Map.of("patientId", state.getPatientId()),
                result.summary()
        );
    }
}
