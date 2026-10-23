package com.medical.agent.service.education.agent;

import com.medical.agent.common.enums.DiseaseType;
import com.medical.agent.common.enums.RiskLevel;
import com.medical.agent.model.entity.Patient;
import com.medical.agent.model.entity.RiskAssessment;
import com.medical.agent.service.LlmService;
import com.medical.agent.service.RagRetrievalService;
import com.medical.agent.service.ReferenceCurationService;
import com.medical.agent.service.reference.agent.ReferenceSelectionSupervisorService;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EducationSpecialistAgentTest {

    @Test
    void contextAgentShouldBuildBaseContextAndDiseaseSet() {
        EducationGenerationState state = new EducationGenerationState();
        state.setPatient(Patient.builder()
                .id(1L)
                .name("赵六")
                .gender("女")
                .birthDate(LocalDate.of(1970, 5, 1))
                .build());
        state.setAge(55);
        state.setAssessments(java.util.List.of(
                assessment(DiseaseType.HYPERTENSION, RiskLevel.HIGH, 85.0),
                assessment(DiseaseType.DIABETES, RiskLevel.MEDIUM, 62.0)
        ));

        new EducationContextSpecialistAgent().execute(state);

        assertTrue(state.getBaseContext().contains("赵六"));
        assertTrue(state.getBaseContext().contains("55岁"));
        assertTrue(state.getBaseContext().contains("原发性高血压"));
        assertTrue(state.getBaseContext().contains("2型糖尿病"));
        assertEquals(java.util.Set.of("原发性高血压", "2型糖尿病"), state.getDiseaseNames());
        assertEquals(1, state.getAgentSteps().size());
    }

    @Test
    void dietAgentShouldFallbackWithoutLlm() {
        EducationGenerationState state = new EducationGenerationState();
        state.setDietCandidates(java.util.List.of());
        state.setAssessments(java.util.List.of(assessment(DiseaseType.HYPERTENSION, RiskLevel.MEDIUM, 50.0)));
        state.setBaseContext("患者：赵六");

        new DietEducationSpecialistAgent(
                new LlmService(),
                new RagRetrievalService(null, null, null),
                new ReferenceCurationService(new ReferenceSelectionSupervisorService(java.util.List.of()))
        ).execute(state);

        assertTrue(state.getDiet().contains("AI服务暂未启用"));
        assertTrue(state.getDietDisplayReferences().isEmpty());
        assertEquals(1, state.getAgentSteps().size());
    }

    @Test
    void exerciseAndLifestyleAgentsShouldFallbackWithoutLlm() {
        EducationGenerationState state = new EducationGenerationState();
        state.setExerciseCandidates(java.util.List.of());
        state.setLifestyleCandidates(java.util.List.of());
        state.setAssessments(java.util.List.of(assessment(DiseaseType.DIABETES, RiskLevel.LOW, 30.0)));
        state.setBaseContext("患者：钱七");

        RagRetrievalService ragRetrievalService = new RagRetrievalService(null, null, null);
        ReferenceCurationService referenceCurationService =
                new ReferenceCurationService(new ReferenceSelectionSupervisorService(java.util.List.of()));

        new ExerciseEducationSpecialistAgent(new LlmService(), ragRetrievalService, referenceCurationService).execute(state);
        new LifestyleEducationSpecialistAgent(new LlmService(), ragRetrievalService, referenceCurationService).execute(state);

        assertTrue(state.getExercise().contains("AI服务暂未启用"));
        assertTrue(state.getLifestyle().contains("AI服务暂未启用"));
        assertFalse(state.getAgentSteps().isEmpty());
        assertEquals(2, state.getAgentSteps().size());
    }

    private RiskAssessment assessment(DiseaseType diseaseType, RiskLevel riskLevel, double score) {
        return RiskAssessment.builder()
                .patientId(1L)
                .diseaseType(diseaseType)
                .riskLevel(riskLevel)
                .score(score)
                .assessmentDetail("需加强管理")
                .build();
    }
}
