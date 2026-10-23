package com.medical.agent.service.followup.agent;

import com.medical.agent.model.entity.FollowUpPlan;
import com.medical.agent.service.LlmService;
import org.springframework.stereotype.Component;

@Component
public class SmartFollowUpDraftingSpecialistAgent implements SmartFollowUpSpecialistAgent {

    private static final String FALLBACK_SUFFIX = "请注意按时服药、监测指标，如有不适请及时就医。祝您健康！";

    private final LlmService llmService;

    public SmartFollowUpDraftingSpecialistAgent(LlmService llmService) {
        this.llmService = llmService;
    }

    @Override
    public String name() {
        return "followup_drafting_agent";
    }

    @Override
    public int order() {
        return 30;
    }

    @Override
    public void execute(SmartFollowUpGenerationState state) {
        String message = null;
        if (llmService.isAvailable()) {
            String task = "请生成一条温馨的随访提醒短信，内容包括：1.问候患者 2.提醒近期需要关注的健康事项 "
                    + "3.询问最近的身体状况(如血压/血糖控制情况) 4.鼓励患者坚持治疗。"
                    + "控制在200字以内，语气亲切温暖。\n"
                    + "本次重点：" + (state.getFocusSummary() == null ? "通用随访提醒" : state.getFocusSummary());
            message = llmService.agentAnalyze("随访沟通智能体", state.getContext(), task);
        }
        if (message == null || message.isBlank()) {
            message = buildFallbackMessage(state);
        }
        state.setMessage(message);
        state.addStep(name(), llmService.isAvailable()
                ? "已生成患者可直接发送的随访提醒文案。"
                : "模型不可用，已回退到模板随访提醒文案。");
    }

    private String buildFallbackMessage(SmartFollowUpGenerationState state) {
        StringBuilder sb = new StringBuilder();
        sb.append(state.getPatient().getName()).append("您好！这是您的健康管家定期提醒。");
        if (!state.getPendingPlans().isEmpty()) {
            FollowUpPlan firstPlan = state.getPendingPlans().get(0);
            sb.append("您有")
                    .append(firstPlan.getDiseaseType().getDisplayName())
                    .append("的随访计划，下次随访日期为")
                    .append(firstPlan.getNextFollowDate())
                    .append("。");
        }
        if (state.getMedicalHistory() != null
                && state.getMedicalHistory().getDrugAllergies() != null
                && !state.getMedicalHistory().getDrugAllergies().isBlank()) {
            sb.append("另外，请在服药前再次确认药物过敏史：")
                    .append(shorten(state.getMedicalHistory().getDrugAllergies(), 24))
                    .append("。");
        }
        sb.append(FALLBACK_SUFFIX);
        return sb.toString();
    }

    private String shorten(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String normalized = value.replaceAll("[\\r\\n]+", "；").trim();
        if (normalized.length() <= maxLength) {
            return normalized;
        }
        return normalized.substring(0, maxLength) + "...";
    }
}
