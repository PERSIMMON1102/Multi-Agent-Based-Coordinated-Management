package com.medical.agent.service.education.agent;

import com.medical.agent.service.LlmService;
import com.medical.agent.service.RagRetrievalService;
import com.medical.agent.service.ReferenceCurationService;
import org.springframework.stereotype.Component;

@Component
public class LifestyleEducationSpecialistAgent implements EducationSpecialistAgent {

    private static final String FALLBACK_TEXT = "AI服务暂未启用，无法生成个性化作息方案。基本建议：早睡早起，保持心情愉快，按时服药。";

    private final LlmService llmService;
    private final RagRetrievalService ragRetrievalService;
    private final ReferenceCurationService referenceCurationService;

    public LifestyleEducationSpecialistAgent(LlmService llmService,
                                             RagRetrievalService ragRetrievalService,
                                             ReferenceCurationService referenceCurationService) {
        this.llmService = llmService;
        this.ragRetrievalService = ragRetrievalService;
        this.referenceCurationService = referenceCurationService;
    }

    @Override
    public String name() {
        return "lifestyle_education_agent";
    }

    @Override
    public int order() {
        return 50;
    }

    @Override
    public void execute(EducationGenerationState state) {
        String lifestyle = FALLBACK_TEXT;
        if (llmService.isAvailable() && !state.getAssessments().isEmpty()) {
            String task = "请为该患者生成个性化作息与生活方式方案，包括：建议作息时间表、睡眠建议、情绪管理、用药提醒、" +
                    "自我监测指标（如何测量和记录）。控制在300字以内。\n\n" +
                    ragRetrievalService.renderEvidenceBlock(state.getLifestylePromptReferences());
            String generated = llmService.agentAnalyze("健康管理师", state.getBaseContext(), task);
            if (generated != null && !generated.isBlank()) {
                lifestyle = generated;
            }
        }
        state.setLifestyle(lifestyle);
        state.setLifestyleDisplayReferences(referenceCurationService.curateEducationDisplayReferences(
                state.getLifestyleContext(),
                "lifestyle",
                lifestyle,
                state.getLifestyleCandidates(),
                llmService.isAvailable() && !state.getAssessments().isEmpty() ? 2 : 1
        ));
        state.addStep(name(), "已生成生活方式方案并筛出可展示的生活方式证据。");
    }
}
