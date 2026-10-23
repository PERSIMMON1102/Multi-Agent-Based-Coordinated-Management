package com.medical.agent.service.chat.agent;

import com.medical.agent.common.enums.DiseaseType;
import com.medical.agent.service.react.model.AgentState;
import com.medical.agent.service.react.tool.RiskAssessmentTool;
import com.medical.agent.service.react.tool.ToolExecutionResult;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

@Component
public class RiskAssessmentSpecialistAgent implements ChatSpecialistAgent {

    private final RiskAssessmentTool riskAssessmentTool;

    public RiskAssessmentSpecialistAgent(RiskAssessmentTool riskAssessmentTool) {
        this.riskAssessmentTool = riskAssessmentTool;
    }

    @Override
    public String name() {
        return "risk_assessment_agent";
    }

    @Override
    public int order() {
        return 20;
    }

    @Override
    public boolean shouldEngage(AgentState state) {
        return state != null && state.getPatientId() != null;
    }

    @Override
    public ChatSpecialistExecutionResult execute(AgentState state) {
        DiseaseType diseaseType = inferDiseaseType(state == null ? null : state.getUserMessage());
        Map<String, Object> input = new LinkedHashMap<>();
        if (diseaseType != null) {
            input.put("diseaseType", diseaseType.name());
        }

        ToolExecutionResult result = riskAssessmentTool.execute(state, input);
        return new ChatSpecialistExecutionResult(
                result.success(),
                "由风险评估智能体结合规则引擎复核当前病种风险等级和管理重点。",
                input,
                result.summary()
        );
    }

    private DiseaseType inferDiseaseType(String userMessage) {
        if (userMessage == null || userMessage.isBlank()) {
            return null;
        }
        String normalized = userMessage.toLowerCase(Locale.ROOT);
        if (normalized.contains("血压") || normalized.contains("降压")) {
            return DiseaseType.HYPERTENSION;
        }
        if (normalized.contains("血糖") || normalized.contains("糖化") || normalized.contains("糖尿病") || normalized.contains("胰岛素")) {
            return DiseaseType.DIABETES;
        }
        if (normalized.contains("呼吸") || normalized.contains("慢阻肺") || normalized.contains("copd") || normalized.contains("咳") || normalized.contains("痰")) {
            return DiseaseType.COPD;
        }
        if (normalized.contains("肾") || normalized.contains("肌酐") || normalized.contains("尿蛋白")) {
            return DiseaseType.CKD;
        }
        if (normalized.contains("冠心病") || normalized.contains("胸痛") || normalized.contains("心绞痛") || normalized.contains("他汀")) {
            return DiseaseType.CHD;
        }
        if (normalized.contains("卒中") || normalized.contains("中风") || normalized.contains("脑梗")) {
            return DiseaseType.STROKE;
        }
        return null;
    }
}
