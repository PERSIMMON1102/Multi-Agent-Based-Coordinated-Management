package com.medical.agent.service.followup.agent;

import com.medical.agent.model.entity.FollowUpPlan;
import org.springframework.stereotype.Component;

@Component
public class SmartFollowUpContextSpecialistAgent implements SmartFollowUpSpecialistAgent {

    @Override
    public String name() {
        return "followup_context_agent";
    }

    @Override
    public int order() {
        return 10;
    }

    @Override
    public void execute(SmartFollowUpGenerationState state) {
        StringBuilder context = new StringBuilder();
        context.append("患者姓名：").append(state.getPatient().getName()).append("\n");
        if (state.getMedicalHistorySummary() != null && !state.getMedicalHistorySummary().isBlank()) {
            context.append("病史档案摘要：\n");
            for (String line : state.getMedicalHistorySummary().split("\\n")) {
                context.append("  - ").append(line).append("\n");
            }
        }
        if (!state.getPendingPlans().isEmpty()) {
            context.append("待执行随访计划：\n");
            for (FollowUpPlan plan : state.getPendingPlans()) {
                context.append("  - ")
                        .append(plan.getDiseaseType().getDisplayName())
                        .append("，下次随访：").append(plan.getNextFollowDate())
                        .append("，频次：").append(plan.getFrequency().getDisplayName())
                        .append("\n");
                if (plan.getPlanContent() != null) {
                    context.append("    内容：")
                            .append(plan.getPlanContent(), 0, Math.min(200, plan.getPlanContent().length()))
                            .append("\n");
                }
            }
        } else {
            context.append("当前暂无待执行随访计划。\n");
        }
        state.setContext(context.toString());
        state.addStep(name(), state.getMedicalHistorySummary() == null || state.getMedicalHistorySummary().isBlank()
                ? "已整理患者姓名与待执行随访计划。"
                : "已整理患者姓名、病史档案摘要与待执行随访计划。");
    }
}
