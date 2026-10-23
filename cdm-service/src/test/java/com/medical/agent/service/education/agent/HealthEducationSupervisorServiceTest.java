package com.medical.agent.service.education.agent;

import com.medical.agent.common.dto.RagEvidenceDTO;
import com.medical.agent.common.enums.DiseaseType;
import com.medical.agent.common.enums.RiskLevel;
import com.medical.agent.model.entity.Patient;
import com.medical.agent.model.entity.PatientMedicalHistory;
import com.medical.agent.model.entity.RiskAssessment;
import com.medical.agent.model.repository.PatientRepository;
import com.medical.agent.model.repository.PatientMedicalHistoryRepository;
import com.medical.agent.model.repository.RiskAssessmentRepository;
import com.medical.agent.service.ReferenceCurationService;
import com.medical.agent.service.reference.agent.ReferenceSelectionSupervisorService;
import com.medical.agent.service.testsupport.RepositoryProxyFactory;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.Period;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HealthEducationSupervisorServiceTest {

    @Test
    void shouldInitializeStateAndMergeReferencesInOrder() {
        Patient patient = Patient.builder()
                .id(1L)
                .name("宣教患者")
                .gender("女")
                .birthDate(LocalDate.of(1980, 4, 11))
                .build();
        List<RiskAssessment> assessments = List.of(
                assessment(DiseaseType.HYPERTENSION, RiskLevel.HIGH, 78.0),
                assessment(DiseaseType.DIABETES, RiskLevel.MEDIUM, 61.0)
        );

        PatientRepository patientRepository = RepositoryProxyFactory.create(PatientRepository.class, Map.of(
                "findById", args -> Optional.of(patient)
        ));
        PatientMedicalHistoryRepository historyRepository = RepositoryProxyFactory.create(PatientMedicalHistoryRepository.class, Map.of(
                "findByPatientId", args -> Optional.of(history())
        ));
        RiskAssessmentRepository riskAssessmentRepository = RepositoryProxyFactory.create(RiskAssessmentRepository.class, Map.of(
                "findByPatientIdOrderByCreatedAtDesc", args -> assessments
        ));

        HealthEducationSupervisorService service = new HealthEducationSupervisorService(
                patientRepository,
                historyRepository,
                riskAssessmentRepository,
                new ReferenceCurationService(new ReferenceSelectionSupervisorService(List.of())),
                List.of(
                        specialist("lifestyle_agent", 50, state -> {
                            state.setLifestyle("作息建议");
                            state.setLifestyleDisplayReferences(List.of(reference("生活方式指南", "ref-2")));
                            state.addStep("lifestyle_agent", "已生成生活方式宣教。");
                        }),
                        new EducationContextSpecialistAgent(),
                        specialist("diet_agent", 30, state -> {
                            state.setDiet("饮食建议");
                            state.setDietDisplayReferences(List.of(reference("饮食指南", "ref-1")));
                            state.addStep("diet_agent", "已生成饮食宣教。");
                        }),
                        specialist("exercise_agent", 40, state -> {
                            state.setExercise("运动建议");
                            state.setExerciseDisplayReferences(List.of(reference("饮食指南", "ref-1")));
                            state.addStep("exercise_agent", "已生成运动宣教。");
                        })
                )
        );

        Map<String, Object> result = service.generate(1L);

        assertEquals("宣教患者", result.get("patientName"));
        assertEquals("饮食建议", result.get("diet"));
        assertEquals("运动建议", result.get("exercise"));
        assertEquals("作息建议", result.get("lifestyle"));
        assertEquals(List.of("原发性高血压", "2型糖尿病"), result.get("diseases"));
        assertEquals(
                Period.between(patient.getBirthDate(), LocalDate.now()).getYears(),
                Period.between(patient.getBirthDate(), LocalDate.now()).getYears()
        );
        @SuppressWarnings("unchecked")
        List<RagEvidenceDTO> references = (List<RagEvidenceDTO>) result.get("references");
        assertEquals(2, references.size());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> agentSteps = (List<Map<String, Object>>) result.get("agentSteps");
        assertEquals("education_context_agent", agentSteps.get(0).get("agentName"));
        assertTrue(String.valueOf(agentSteps.get(0).get("summary")).contains("病史档案"));
        assertEquals("diet_agent", agentSteps.get(1).get("agentName"));
        assertEquals("exercise_agent", agentSteps.get(2).get("agentName"));
        assertEquals("lifestyle_agent", agentSteps.get(3).get("agentName"));
    }

    @Test
    void shouldSkipEducationSpecialistsBeyondBoundedLimit() {
        PatientRepository patientRepository = RepositoryProxyFactory.create(PatientRepository.class, Map.of(
                "findById", args -> Optional.of(Patient.builder().id(1L).name("宣教患者").gender("女").build())
        ));
        PatientMedicalHistoryRepository historyRepository = RepositoryProxyFactory.create(PatientMedicalHistoryRepository.class, Map.of(
                "findByPatientId", args -> Optional.empty()
        ));
        RiskAssessmentRepository riskAssessmentRepository = RepositoryProxyFactory.create(RiskAssessmentRepository.class, Map.of(
                "findByPatientIdOrderByCreatedAtDesc", args -> List.of()
        ));
        List<String> executionOrder = new java.util.ArrayList<>();

        HealthEducationSupervisorService service = new HealthEducationSupervisorService(
                patientRepository,
                historyRepository,
                riskAssessmentRepository,
                new ReferenceCurationService(new ReferenceSelectionSupervisorService(List.of())),
                List.of(
                        trackingSpecialist("agent_7", 70, executionOrder),
                        trackingSpecialist("agent_3", 30, executionOrder),
                        trackingSpecialist("agent_1", 10, executionOrder),
                        trackingSpecialist("agent_6", 60, executionOrder),
                        trackingSpecialist("agent_2", 20, executionOrder),
                        trackingSpecialist("agent_5", 50, executionOrder),
                        trackingSpecialist("agent_4", 40, executionOrder)
                )
        );

        service.generate(1L);

        assertEquals(List.of("agent_1", "agent_2", "agent_3", "agent_4", "agent_5", "agent_6"), executionOrder);
    }

    private PatientMedicalHistory history() {
        return PatientMedicalHistory.builder()
                .patientId(1L)
                .pastDiseases("高血压、糖尿病")
                .drugAllergies("青霉素")
                .build();
    }

    private EducationSpecialistAgent specialist(String name,
                                                int order,
                                                java.util.function.Consumer<EducationGenerationState> consumer) {
        return new EducationSpecialistAgent() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public int order() {
                return order;
            }

            @Override
            public void execute(EducationGenerationState state) {
                consumer.accept(state);
            }
        };
    }

    private EducationSpecialistAgent trackingSpecialist(String name, int order, List<String> executionOrder) {
        return specialist(name, order, state -> {
            executionOrder.add(name);
            state.addStep(name, "executed");
        });
    }

    private RiskAssessment assessment(DiseaseType diseaseType, RiskLevel riskLevel, double score) {
        return RiskAssessment.builder()
                .patientId(1L)
                .diseaseType(diseaseType)
                .riskLevel(riskLevel)
                .score(score)
                .assessmentDetail("需要持续管理")
                .build();
    }

    private RagEvidenceDTO reference(String title, String source) {
        return RagEvidenceDTO.builder()
                .title(title)
                .source(source)
                .sourceType("guideline")
                .publishedAt("2025-01-01")
                .build();
    }
}
