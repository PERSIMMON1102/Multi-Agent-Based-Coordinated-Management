package com.medical.agent.service.report.agent;

import com.medical.agent.common.enums.DiseaseType;
import com.medical.agent.common.enums.FollowUpFrequency;
import com.medical.agent.common.enums.FollowUpStatus;
import com.medical.agent.common.enums.RiskLevel;
import com.medical.agent.model.entity.FollowUpPlan;
import com.medical.agent.model.entity.HealthRecord;
import com.medical.agent.model.entity.Patient;
import com.medical.agent.model.entity.PatientMedicalHistory;
import com.medical.agent.model.entity.RiskAssessment;
import com.medical.agent.model.repository.FollowUpPlanRepository;
import com.medical.agent.model.repository.HealthRecordRepository;
import com.medical.agent.model.repository.PatientRepository;
import com.medical.agent.model.repository.PatientMedicalHistoryRepository;
import com.medical.agent.model.repository.RiskAssessmentRepository;
import com.medical.agent.service.testsupport.RepositoryProxyFactory;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HealthReportSupervisorServiceTest {

    @Test
    void shouldLoadRepositoriesAndExecuteReportSpecialistsInOrder() {
        PatientRepository patientRepository = RepositoryProxyFactory.create(PatientRepository.class, Map.of(
                "findById", args -> Optional.of(patient())
        ));
        PatientMedicalHistoryRepository historyRepository = RepositoryProxyFactory.create(PatientMedicalHistoryRepository.class, Map.of(
                "findByPatientId", args -> Optional.of(history())
        ));
        RiskAssessmentRepository riskAssessmentRepository = RepositoryProxyFactory.create(RiskAssessmentRepository.class, Map.of(
                "findByPatientIdOrderByCreatedAtDesc", args -> List.of(assessment())
        ));
        FollowUpPlanRepository followUpPlanRepository = RepositoryProxyFactory.create(FollowUpPlanRepository.class, Map.of(
                "findByPatientIdOrderByCreatedAtDesc", args -> List.of(plan())
        ));
        HealthRecordRepository healthRecordRepository = RepositoryProxyFactory.create(HealthRecordRepository.class, Map.of(
                "findFirstByPatientIdOrderByRecordDateDesc", args -> Optional.of(record())
        ));

        HealthReportSupervisorService service = new HealthReportSupervisorService(
                patientRepository,
                historyRepository,
                riskAssessmentRepository,
                followUpPlanRepository,
                healthRecordRepository,
                List.of(
                        specialist("draft_agent", 30, state -> {
                            state.setContent(state.getPatient().getName() + " / assessments=" + state.getAssessments().size());
                            state.addStep("draft_agent", "已写入报告正文。");
                        }),
                        specialist("context_agent", 10, state -> {
                            state.setTitle("测试报告");
                            state.addStep("context_agent", "已装载患者、评估、随访与健康记录。");
                        }),
                        specialist("evidence_agent", 20, state -> {
                            state.setReportTopicKey(state.getType());
                            state.addStep("evidence_agent", "已准备报告主题键。");
                        })
                )
        );

        ReportGenerationState state = service.generate(1L, "doctor_assessment");

        assertEquals("测试报告", state.getTitle());
        assertEquals("周报患者 / assessments=1", state.getContent());
        assertEquals(1, state.getAssessments().size());
        assertEquals(1, state.getPlans().size());
        assertNotNull(state.getLatestRecord());
        assertEquals("doctor_assessment", state.getReportTopicKey());
        assertTrue(state.getMedicalHistorySummary().contains("高血压"));
        assertEquals(3, state.getAgentSteps().size());
        assertEquals("context_agent", state.getAgentSteps().get(0).get("agentName"));
        assertEquals("evidence_agent", state.getAgentSteps().get(1).get("agentName"));
        assertEquals("draft_agent", state.getAgentSteps().get(2).get("agentName"));
    }

    @Test
    void shouldSkipReportSpecialistsBeyondBoundedLimit() {
        PatientRepository patientRepository = RepositoryProxyFactory.create(PatientRepository.class, Map.of(
                "findById", args -> Optional.of(patient())
        ));
        PatientMedicalHistoryRepository historyRepository = RepositoryProxyFactory.create(PatientMedicalHistoryRepository.class, Map.of(
                "findByPatientId", args -> Optional.empty()
        ));
        RiskAssessmentRepository riskAssessmentRepository = RepositoryProxyFactory.create(RiskAssessmentRepository.class, Map.of(
                "findByPatientIdOrderByCreatedAtDesc", args -> List.of()
        ));
        FollowUpPlanRepository followUpPlanRepository = RepositoryProxyFactory.create(FollowUpPlanRepository.class, Map.of(
                "findByPatientIdOrderByCreatedAtDesc", args -> List.of()
        ));
        HealthRecordRepository healthRecordRepository = RepositoryProxyFactory.create(HealthRecordRepository.class, Map.of(
                "findFirstByPatientIdOrderByRecordDateDesc", args -> Optional.empty()
        ));
        List<String> executionOrder = new java.util.ArrayList<>();

        HealthReportSupervisorService service = new HealthReportSupervisorService(
                patientRepository,
                historyRepository,
                riskAssessmentRepository,
                followUpPlanRepository,
                healthRecordRepository,
                List.of(
                        trackingSpecialist("agent_6", 60, executionOrder),
                        trackingSpecialist("agent_3", 30, executionOrder),
                        trackingSpecialist("agent_1", 10, executionOrder),
                        trackingSpecialist("agent_5", 50, executionOrder),
                        trackingSpecialist("agent_2", 20, executionOrder),
                        trackingSpecialist("agent_4", 40, executionOrder)
                )
        );

        service.generate(1L, "doctor_assessment");

        assertEquals(List.of("agent_1", "agent_2", "agent_3", "agent_4", "agent_5"), executionOrder);
    }

    private PatientMedicalHistory history() {
        return PatientMedicalHistory.builder()
                .patientId(1L)
                .pastDiseases("高血压")
                .surgicalHistory("阑尾切除术后")
                .build();
    }

    private ReportSpecialistAgent specialist(String name,
                                             int order,
                                             java.util.function.Consumer<ReportGenerationState> consumer) {
        return new ReportSpecialistAgent() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public int order() {
                return order;
            }

            @Override
            public void execute(ReportGenerationState state) {
                consumer.accept(state);
            }
        };
    }

    private ReportSpecialistAgent trackingSpecialist(String name, int order, List<String> executionOrder) {
        return specialist(name, order, state -> {
            executionOrder.add(name);
            state.addStep(name, "executed");
        });
    }

    private Patient patient() {
        return Patient.builder()
                .id(1L)
                .name("周报患者")
                .gender("男")
                .build();
    }

    private RiskAssessment assessment() {
        return RiskAssessment.builder()
                .patientId(1L)
                .diseaseType(DiseaseType.HYPERTENSION)
                .riskLevel(RiskLevel.HIGH)
                .score(82.0)
                .build();
    }

    private FollowUpPlan plan() {
        return FollowUpPlan.builder()
                .patientId(1L)
                .diseaseType(DiseaseType.HYPERTENSION)
                .frequency(FollowUpFrequency.MONTHLY)
                .status(FollowUpStatus.PENDING)
                .nextFollowDate(LocalDate.of(2026, 4, 18))
                .build();
    }

    private HealthRecord record() {
        return HealthRecord.builder()
                .patientId(1L)
                .recordDate(LocalDate.of(2026, 4, 10))
                .systolicBp(145)
                .build();
    }
}
