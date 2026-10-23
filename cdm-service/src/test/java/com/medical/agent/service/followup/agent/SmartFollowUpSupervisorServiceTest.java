package com.medical.agent.service.followup.agent;

import com.medical.agent.common.enums.DiseaseType;
import com.medical.agent.common.enums.FollowUpFrequency;
import com.medical.agent.common.enums.FollowUpStatus;
import com.medical.agent.model.entity.FollowUpPlan;
import com.medical.agent.model.entity.Patient;
import com.medical.agent.model.entity.PatientMedicalHistory;
import com.medical.agent.model.repository.FollowUpPlanRepository;
import com.medical.agent.model.repository.PatientRepository;
import com.medical.agent.model.repository.PatientMedicalHistoryRepository;
import com.medical.agent.service.testsupport.RepositoryProxyFactory;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SmartFollowUpSupervisorServiceTest {

    @Test
    void shouldFilterPendingPlansAndExecuteSpecialistsInOrder() {
        PatientRepository patientRepository = RepositoryProxyFactory.create(PatientRepository.class, Map.of(
                "findById", args -> Optional.of(patient())
        ));
        PatientMedicalHistoryRepository historyRepository = RepositoryProxyFactory.create(PatientMedicalHistoryRepository.class, Map.of(
                "findByPatientId", args -> Optional.of(history())
        ));
        FollowUpPlanRepository followUpPlanRepository = RepositoryProxyFactory.create(FollowUpPlanRepository.class, Map.of(
                "findByPatientIdOrderByCreatedAtDesc", args -> List.of(
                        plan(DiseaseType.HYPERTENSION, FollowUpStatus.PENDING, LocalDate.of(2026, 4, 20)),
                        plan(DiseaseType.DIABETES, FollowUpStatus.COMPLETED, LocalDate.of(2026, 4, 5))
                )
        ));

        SmartFollowUpSupervisorService service = new SmartFollowUpSupervisorService(
                patientRepository,
                historyRepository,
                followUpPlanRepository,
                List.of(
                        specialist("draft_agent", 30, state -> {
                            state.setMessage("待随访计划数=" + state.getPendingPlans().size());
                            state.addStep("draft_agent", "已生成最终提醒文案。");
                        }),
                        specialist("context_agent", 10, state -> {
                            state.setContext("患者=" + state.getPatient().getName());
                            state.addStep("context_agent", "已读取患者和待执行计划。");
                        }),
                        specialist("focus_agent", 20, state -> {
                            state.setFocusSummary(state.getPendingPlans().get(0).getDiseaseType().getDisplayName());
                            state.addStep("focus_agent", "已锁定唯一待随访病种。");
                        })
                )
        );

        Map<String, Object> result = service.generate(1L);

        assertEquals("测试患者", result.get("patientName"));
        assertEquals("待随访计划数=1", result.get("message"));
        @SuppressWarnings("unchecked")
        List<String> historyAlerts = (List<String>) result.get("historyAlerts");
        assertTrue(historyAlerts.stream().anyMatch(item -> item.contains("药物过敏")));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> agentSteps = (List<Map<String, Object>>) result.get("agentSteps");
        assertEquals(3, agentSteps.size());
        assertEquals("context_agent", agentSteps.get(0).get("agentName"));
        assertEquals("focus_agent", agentSteps.get(1).get("agentName"));
        assertEquals("draft_agent", agentSteps.get(2).get("agentName"));
    }

    @Test
    void shouldSkipFollowUpSpecialistsBeyondBoundedLimit() {
        PatientRepository patientRepository = RepositoryProxyFactory.create(PatientRepository.class, Map.of(
                "findById", args -> Optional.of(patient())
        ));
        PatientMedicalHistoryRepository historyRepository = RepositoryProxyFactory.create(PatientMedicalHistoryRepository.class, Map.of(
                "findByPatientId", args -> Optional.empty()
        ));
        FollowUpPlanRepository followUpPlanRepository = RepositoryProxyFactory.create(FollowUpPlanRepository.class, Map.of(
                "findByPatientIdOrderByCreatedAtDesc", args -> List.of()
        ));
        List<String> executionOrder = new java.util.ArrayList<>();

        SmartFollowUpSupervisorService service = new SmartFollowUpSupervisorService(
                patientRepository,
                historyRepository,
                followUpPlanRepository,
                List.of(
                        trackingSpecialist("agent_5", 50, executionOrder),
                        trackingSpecialist("agent_2", 20, executionOrder),
                        trackingSpecialist("agent_4", 40, executionOrder),
                        trackingSpecialist("agent_1", 10, executionOrder),
                        trackingSpecialist("agent_3", 30, executionOrder)
                )
        );

        service.generate(1L);

        assertEquals(List.of("agent_1", "agent_2", "agent_3", "agent_4"), executionOrder);
    }

    private PatientMedicalHistory history() {
        return PatientMedicalHistory.builder()
                .patientId(1L)
                .drugAllergies("青霉素")
                .build();
    }

    private SmartFollowUpSpecialistAgent specialist(String name,
                                                    int order,
                                                    java.util.function.Consumer<SmartFollowUpGenerationState> consumer) {
        return new SmartFollowUpSpecialistAgent() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public int order() {
                return order;
            }

            @Override
            public void execute(SmartFollowUpGenerationState state) {
                consumer.accept(state);
            }
        };
    }

    private SmartFollowUpSpecialistAgent trackingSpecialist(String name, int order, List<String> executionOrder) {
        return specialist(name, order, state -> {
            executionOrder.add(name);
            state.addStep(name, "executed");
        });
    }

    private Patient patient() {
        return Patient.builder()
                .id(1L)
                .name("测试患者")
                .gender("男")
                .build();
    }

    private FollowUpPlan plan(DiseaseType diseaseType, FollowUpStatus status, LocalDate nextFollowDate) {
        return FollowUpPlan.builder()
                .patientId(1L)
                .diseaseType(diseaseType)
                .frequency(FollowUpFrequency.MONTHLY)
                .status(status)
                .nextFollowDate(nextFollowDate)
                .build();
    }
}
