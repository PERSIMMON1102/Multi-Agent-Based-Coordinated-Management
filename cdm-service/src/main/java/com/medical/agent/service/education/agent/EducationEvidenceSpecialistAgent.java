package com.medical.agent.service.education.agent;

import com.medical.agent.common.dto.RagEvidenceDTO;
import com.medical.agent.common.dto.RagQueryContextDTO;
import com.medical.agent.service.RagRetrievalService;
import com.medical.agent.service.ReferenceCurationService;
import com.medical.agent.service.VideoResourceService;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class EducationEvidenceSpecialistAgent implements EducationSpecialistAgent {

    private final RagRetrievalService ragRetrievalService;
    private final ReferenceCurationService referenceCurationService;
    private final VideoResourceService videoResourceService;

    public EducationEvidenceSpecialistAgent(RagRetrievalService ragRetrievalService,
                                            ReferenceCurationService referenceCurationService,
                                            VideoResourceService videoResourceService) {
        this.ragRetrievalService = ragRetrievalService;
        this.referenceCurationService = referenceCurationService;
        this.videoResourceService = videoResourceService;
    }

    @Override
    public String name() {
        return "education_evidence_agent";
    }

    @Override
    public int order() {
        return 20;
    }

    @Override
    public void execute(EducationGenerationState state) {
        RagQueryContextDTO dietContext = ragRetrievalService.buildContext(
                "AI健康宣教", "饮食方案", state.getPatient(), state.getAssessments(), List.of(), null, state.getMedicalHistorySummary()
        );
        RagQueryContextDTO exerciseContext = ragRetrievalService.buildContext(
                "AI健康宣教", "运动方案", state.getPatient(), state.getAssessments(), List.of(), null, state.getMedicalHistorySummary()
        );
        RagQueryContextDTO lifestyleContext = ragRetrievalService.buildContext(
                "AI健康宣教", "作息与生活方式", state.getPatient(), state.getAssessments(), List.of(), null, state.getMedicalHistorySummary()
        );

        List<RagEvidenceDTO> dietCandidates = ragRetrievalService.retrieveKnowledge(dietContext, 6);
        List<RagEvidenceDTO> exerciseCandidates = ragRetrievalService.retrieveKnowledge(exerciseContext, 6);
        List<RagEvidenceDTO> lifestyleCandidates = ragRetrievalService.retrieveKnowledge(lifestyleContext, 6);

        state.setDietContext(dietContext);
        state.setExerciseContext(exerciseContext);
        state.setLifestyleContext(lifestyleContext);
        state.setDietCandidates(dietCandidates);
        state.setExerciseCandidates(exerciseCandidates);
        state.setLifestyleCandidates(lifestyleCandidates);

        state.setDietPromptReferences(referenceCurationService.curateEducationPromptReferences(
                dietContext, "diet", dietCandidates, 4
        ));
        state.setExercisePromptReferences(referenceCurationService.curateEducationPromptReferences(
                exerciseContext, "exercise", exerciseCandidates, 4
        ));
        state.setLifestylePromptReferences(referenceCurationService.curateEducationPromptReferences(
                lifestyleContext, "lifestyle", lifestyleCandidates, 4
        ));
        state.setRecommendedVideos(videoResourceService.getRecommendedVideos(state.getPatientId(), 4));

        state.addStep(name(), "已检索饮食、运动和生活方式三类知识证据，并准备引用候选。");
    }
}
