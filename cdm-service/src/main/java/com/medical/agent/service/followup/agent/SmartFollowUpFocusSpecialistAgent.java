package com.medical.agent.service.followup.agent;

import com.medical.agent.model.entity.FollowUpPlan;
import org.springframework.stereotype.Component;

import java.util.stream.Collectors;

@Component
public class SmartFollowUpFocusSpecialistAgent implements SmartFollowUpSpecialistAgent {

    @Override
    public String name() {
        return "followup_focus_agent";
    }

    @Override
    public int order() {
        return 20;
    }

    @Override
    public void execute(SmartFollowUpGenerationState state) {
        if (state.getPendingPlans().isEmpty()) {
            String focusSummary = hasDrugAllergy(state)
                    ? "当前以通用服药提醒、症状询问和药物过敏史核对为主。"
                    : "当前以通用服药提醒和症状询问为主。";
            state.setFocusSummary(focusSummary);
            state.addStep(name(), hasDrugAllergy(state)
                    ? "未发现待执行计划，已回退到通用随访重点，并补充药物过敏史核对提醒。"
                    : "未发现待执行计划，已回退到通用随访重点。");
            return;
        }

        String focusSummary = state.getPendingPlans().stream()
                .limit(2)
                .map(this::toPlanFocus)
                .collect(Collectors.joining("；"));
        if (hasDrugAllergy(state)) {
            focusSummary = focusSummary + "；用药沟通前请先核对药物过敏史";
        }
        state.setFocusSummary(focusSummary);
        state.addStep(name(), hasDrugAllergy(state)
                ? "已提取本次随访提醒的病种、时间节点，并补充药物过敏史核对重点。"
                : "已提取本次随访提醒应重点覆盖的病种和时间节点。");
    }

    private String toPlanFocus(FollowUpPlan plan) {
        return plan.getDiseaseType().getDisplayName()
                + "需在"
                + plan.getNextFollowDate()
                + "前后重点关注"
                + plan.getFrequency().getDisplayName()
                + "随访安排";
    }

    private boolean hasDrugAllergy(SmartFollowUpGenerationState state) {
        return state != null
                && state.getMedicalHistory() != null
                && state.getMedicalHistory().getDrugAllergies() != null
                && !state.getMedicalHistory().getDrugAllergies().isBlank();
    }
}
