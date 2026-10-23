package com.medical.agent.service.education.agent;

import com.medical.agent.model.entity.Patient;
import com.medical.agent.model.entity.RiskAssessment;
import org.springframework.stereotype.Component;

@Component
public class EducationContextSpecialistAgent implements EducationSpecialistAgent {

    @Override
    public String name() {
        return "education_context_agent";
    }

    @Override
    public int order() {
        return 10;
    }

    @Override
    public void execute(EducationGenerationState state) {
        Patient patient = state.getPatient();
        StringBuilder context = new StringBuilder();
        context.append("患者：").append(patient.getName())
                .append("，性别：").append(patient.getGender())
                .append("，年龄：").append(state.getAge()).append("岁\n");
        if (state.getMedicalHistorySummary() != null && !state.getMedicalHistorySummary().isBlank()) {
            context.append("病史档案摘要：\n");
            for (String line : state.getMedicalHistorySummary().split("\\n")) {
                context.append("  - ").append(line).append("\n");
            }
        }
        context.append("诊断的慢性病及风险等级：\n");

        for (RiskAssessment assessment : state.getAssessments().stream().limit(6).toList()) {
            if (assessment.getDiseaseType() != null) {
                state.getDiseaseNames().add(assessment.getDiseaseType().getDisplayName());
            }
            context.append("  - ")
                    .append(assessment.getDiseaseType() == null ? "未知病种" : assessment.getDiseaseType().getDisplayName())
                    .append("：")
                    .append(assessment.getRiskLevel() == null ? "未知风险" : assessment.getRiskLevel().getDisplayName())
                    .append("（评分：")
                    .append(assessment.getScore())
                    .append("）\n");
        }

        state.setBaseContext(context.toString());
        state.addStep(name(), state.getMedicalHistorySummary() == null || state.getMedicalHistorySummary().isBlank()
                ? "已整理患者基本信息、年龄和近期待管理病种。"
                : "已整理患者基本信息、病史档案摘要和近期待管理病种。");
    }
}
