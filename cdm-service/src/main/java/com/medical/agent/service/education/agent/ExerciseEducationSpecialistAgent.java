package com.medical.agent.service.education.agent;

import com.medical.agent.service.LlmService;
import com.medical.agent.service.RagRetrievalService;
import com.medical.agent.service.ReferenceCurationService;
import org.springframework.stereotype.Component;

@Component
public class ExerciseEducationSpecialistAgent implements EducationSpecialistAgent {

    private static final String FALLBACK_TEXT = "AI服务暂未启用，无法生成个性化运动方案。基本建议：每天步行30分钟，避免剧烈运动。";

    private final LlmService llmService;
    private final RagRetrievalService ragRetrievalService;
    private final ReferenceCurationService referenceCurationService;

    public ExerciseEducationSpecialistAgent(LlmService llmService,
                                            RagRetrievalService ragRetrievalService,
                                            ReferenceCurationService referenceCurationService) {
        this.llmService = llmService;
        this.ragRetrievalService = ragRetrievalService;
        this.referenceCurationService = referenceCurationService;
    }

    @Override
    public String name() {
        return "exercise_education_agent";
    }

    @Override
    public int order() {
        return 40;
    }

    @Override
    public void execute(EducationGenerationState state) {
        String exercise = FALLBACK_TEXT;
        if (llmService.isAvailable() && !state.getAssessments().isEmpty()) {
            String task = "请为该患者生成个性化运动方案，包括：推荐运动类型、运动强度、每周运动频率、每次时长、运动注意事项和禁忌。" +
                    "控制在300字以内。\n\n" +
                    ragRetrievalService.renderEvidenceBlock(state.getExercisePromptReferences());
            String generated = llmService.agentAnalyze("康复运动师", state.getBaseContext(), task);
            if (generated != null && !generated.isBlank()) {
                exercise = generated;
            }
        }
        state.setExercise(exercise);
        state.setExerciseDisplayReferences(referenceCurationService.curateEducationDisplayReferences(
                state.getExerciseContext(),
                "exercise",
                exercise,
                state.getExerciseCandidates(),
                llmService.isAvailable() && !state.getAssessments().isEmpty() ? 2 : 1
        ));
        state.addStep(name(), "已生成运动方案并筛出可展示的运动证据。");
    }
}
