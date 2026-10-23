package com.medical.agent.service.education.agent;

import com.medical.agent.service.LlmService;
import com.medical.agent.service.RagRetrievalService;
import com.medical.agent.service.ReferenceCurationService;
import org.springframework.stereotype.Component;

@Component
public class DietEducationSpecialistAgent implements EducationSpecialistAgent {

    private static final String FALLBACK_TEXT = "AI服务暂未启用，无法生成个性化饮食方案。基本建议：低盐低脂饮食，多吃蔬菜水果，控制主食摄入量。";

    private final LlmService llmService;
    private final RagRetrievalService ragRetrievalService;
    private final ReferenceCurationService referenceCurationService;

    public DietEducationSpecialistAgent(LlmService llmService,
                                        RagRetrievalService ragRetrievalService,
                                        ReferenceCurationService referenceCurationService) {
        this.llmService = llmService;
        this.ragRetrievalService = ragRetrievalService;
        this.referenceCurationService = referenceCurationService;
    }

    @Override
    public String name() {
        return "diet_education_agent";
    }

    @Override
    public int order() {
        return 30;
    }

    @Override
    public void execute(EducationGenerationState state) {
        String diet = FALLBACK_TEXT;
        if (llmService.isAvailable() && !state.getAssessments().isEmpty()) {
            String task = "请为该患者生成个性化饮食方案，包括：每日推荐食物清单、禁忌食物、餐食搭配建议、每日盐/糖/脂肪摄入建议。" +
                    "按早餐/午餐/晚餐/加餐格式组织。控制在400字以内。\n\n" +
                    ragRetrievalService.renderEvidenceBlock(state.getDietPromptReferences());
            String generated = llmService.agentAnalyze("营养师", state.getBaseContext(), task);
            if (generated != null && !generated.isBlank()) {
                diet = generated;
            }
        }
        state.setDiet(diet);
        state.setDietDisplayReferences(referenceCurationService.curateEducationDisplayReferences(
                state.getDietContext(),
                "diet",
                diet,
                state.getDietCandidates(),
                llmService.isAvailable() && !state.getAssessments().isEmpty() ? 2 : 1
        ));
        state.addStep(name(), "已生成饮食方案并筛出可展示的饮食证据。");
    }
}
