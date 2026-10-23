package com.medical.agent.service;

import com.medical.agent.common.dto.NotificationDTO;
import com.medical.agent.common.enums.DiseaseType;
import com.medical.agent.common.enums.FollowUpFrequency;
import com.medical.agent.common.enums.FollowUpStatus;
import com.medical.agent.common.enums.RiskLevel;
import com.medical.agent.model.entity.FollowUpPlan;
import com.medical.agent.model.entity.HealthRecord;
import com.medical.agent.model.entity.Patient;
import com.medical.agent.model.entity.ResourceRecommendation;
import com.medical.agent.model.entity.RiskAssessment;
import com.medical.agent.model.repository.FollowUpPlanRepository;
import com.medical.agent.model.repository.HealthRecordRepository;
import com.medical.agent.model.repository.PatientRepository;
import com.medical.agent.model.repository.PatientMedicalHistoryRepository;
import com.medical.agent.model.repository.ResourceRecommendationRepository;
import com.medical.agent.model.repository.RiskAssessmentRepository;
import com.medical.agent.service.cache.AiChatCacheInvalidationService;
import com.medical.agent.service.cache.AiChatCacheVersionService;
import com.medical.agent.service.chat.ChatContextService;
import com.medical.agent.service.react.model.AgentState;
import com.medical.agent.service.react.model.ReActResult;
import com.medical.agent.service.react.orchestration.OrchestrationTrace;
import com.medical.agent.service.react.orchestration.TaskStatus;
import com.medical.agent.service.react.tool.RiskAssessmentTool;
import com.medical.agent.service.react.tool.ToolExecutionResult;
import com.medical.agent.service.chat.RuleAssessmentService;
import com.medical.agent.service.chat.agent.ChatSupervisorService;
import org.junit.jupiter.api.Test;
import org.springframework.cache.support.NoOpCacheManager;

import java.time.LocalDate;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReActClinicalAssessmentOrchestratorTest {

    @Test
    void shouldPersistAssessmentAndPushNotificationFromReactProvider() throws Exception {
        AtomicReference<RiskAssessment> savedAssessment = new AtomicReference<>();
        AtomicReference<FollowUpPlan> savedFollowUpPlan = new AtomicReference<>();
        AtomicReference<ResourceRecommendation> savedRecommendation = new AtomicReference<>();
        AtomicReference<NotificationDTO> notification = new AtomicReference<>();

        ReActClinicalAssessmentOrchestrator orchestrator = new ReActClinicalAssessmentOrchestrator(
                new StubRiskAssessmentTool(),
                patientRepository(),
                new AiChatCacheInvalidationService(new NoOpCacheManager(), new AiChatCacheVersionService()),
                new StubClinicalNotificationGateway(notification),
                assessmentResultPersistenceService(repository(args -> {
                    RiskAssessment assessment = (RiskAssessment) args[0];
                    savedAssessment.set(assessment);
                    return assessment;
                })),
                assessmentFollowUpWorkflowService(savedFollowUpPlan, savedRecommendation)
        );
        setField(orchestrator, "notificationsEnabled", true);

        orchestrator.triggerAssessment(
                9L,
                DiseaseType.DIABETES,
                Map.of("fastingGlucose", 8.1),
                Map.of("_silentPatientNotification", false)
        );

        assertNotNull(savedAssessment.get());
        assertEquals(9L, savedAssessment.get().getPatientId());
        assertEquals(DiseaseType.DIABETES, savedAssessment.get().getDiseaseType());
        assertEquals(RiskLevel.HIGH, savedAssessment.get().getRiskLevel());
        assertEquals("ReActAssessmentOrchestrator", savedAssessment.get().getAgentId());
        assertEquals("react", savedAssessment.get().getAssessmentProvider());
        assertTrue(savedAssessment.get().getOrchestrationSessionId().startsWith("assessment-9-"));
        assertNotNull(savedFollowUpPlan.get());
        assertEquals(FollowUpFrequency.WEEKLY, savedFollowUpPlan.get().getFrequency());
        assertEquals(FollowUpStatus.PENDING, savedFollowUpPlan.get().getStatus());
        assertNotNull(savedRecommendation.get());
        assertEquals("ReActResourceOrchestrator", savedRecommendation.get().getAgentId());
        assertNotNull(notification.get());
        assertEquals("ASSESSMENT_RESULT", notification.get().getType());
        assertTrue(notification.get().getMessage().contains("病史提醒"));
    }

    @Test
    void shouldPreferChatSupervisorResultsBeforeDirectToolFallback() throws Exception {
        AtomicReference<RiskAssessment> savedAssessment = new AtomicReference<>();
        AtomicInteger toolCalls = new AtomicInteger();

        ReActClinicalAssessmentOrchestrator orchestrator = new ReActClinicalAssessmentOrchestrator(
                new StubChatSupervisorService(state -> {
                    state.setAssessmentResults(List.of(
                            new com.medical.agent.service.chat.RuleAssessmentResult(
                                    DiseaseType.DIABETES,
                                    RiskLevel.HIGH,
                                    91.0,
                                    "主编排已完成风险评估",
                                    List.of("尽快复诊"),
                                    "react_orchestration"
                            )
                    ));
                    return new ReActResult(
                            "阶段性说明",
                            "完成风险评估",
                            null,
                            "完成风险评估",
                            "主编排已完成",
                            null,
                            List.of(),
                            null,
                            List.of(),
                            null,
                            List.of(),
                            List.of("risk_assessment"),
                            List.of(),
                            List.of(),
                            java.util.Set.of(),
                            new OrchestrationTrace(
                                    "task-react",
                                    "完成风险评估",
                                    TaskStatus.COMPLETED,
                                    "supervisor",
                                    List.of(),
                                    List.of(),
                                    List.of(),
                                    null
                            )
                    );
                }),
                new CountingRiskAssessmentTool(toolCalls),
                patientRepository(),
                new AiChatCacheInvalidationService(new NoOpCacheManager(), new AiChatCacheVersionService()),
                new StubClinicalNotificationGateway(new AtomicReference<>()),
                assessmentResultPersistenceService(repository(args -> {
                    RiskAssessment assessment = (RiskAssessment) args[0];
                    savedAssessment.set(assessment);
                    return assessment;
                })),
                assessmentFollowUpWorkflowService(new AtomicReference<>(), new AtomicReference<>())
        );
        setField(orchestrator, "notificationsEnabled", false);

        orchestrator.triggerAssessment(
                9L,
                DiseaseType.DIABETES,
                Map.of("fastingGlucose", 8.8),
                Map.of()
        );

        assertEquals(0, toolCalls.get());
        assertNotNull(savedAssessment.get());
        assertEquals(DiseaseType.DIABETES, savedAssessment.get().getDiseaseType());
        assertEquals(91.0, savedAssessment.get().getScore());
        assertEquals("ReActAssessmentOrchestrator", savedAssessment.get().getAgentId());
        assertEquals("react", savedAssessment.get().getAssessmentProvider());
        assertTrue(savedAssessment.get().getOrchestrationSessionId().startsWith("assessment-9-"));
        assertEquals("task-react", savedAssessment.get().getOrchestrationTaskId());
        assertEquals("supervisor", savedAssessment.get().getOrchestrationAssignedAgent());
    }

    @Test
    void shouldFallbackToDirectToolWhenChatSupervisorProducesNoAssessment() throws Exception {
        AtomicReference<RiskAssessment> savedAssessment = new AtomicReference<>();
        AtomicInteger toolCalls = new AtomicInteger();

        ReActClinicalAssessmentOrchestrator orchestrator = new ReActClinicalAssessmentOrchestrator(
                new StubChatSupervisorService(state -> new ReActResult(
                        "阶段性说明",
                        "未形成评估结果",
                        null,
                        "继续评估",
                        "主编排未形成结构化结果",
                        "回退到直接规则评估",
                        List.of("缺少结构化评估"),
                        null,
                        List.of(),
                        null,
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        java.util.Set.of(),
                        null
                )),
                new CountingRiskAssessmentTool(toolCalls, List.of(
                        new com.medical.agent.service.chat.RuleAssessmentResult(
                                DiseaseType.HYPERTENSION,
                                RiskLevel.HIGH,
                                95.0,
                                "直接工具评估完成",
                                List.of("加强监测"),
                                "direct_tool"
                        )
                )),
                patientRepository(),
                new AiChatCacheInvalidationService(new NoOpCacheManager(), new AiChatCacheVersionService()),
                new StubClinicalNotificationGateway(new AtomicReference<>()),
                assessmentResultPersistenceService(repository(args -> {
                    RiskAssessment assessment = (RiskAssessment) args[0];
                    savedAssessment.set(assessment);
                    return assessment;
                })),
                assessmentFollowUpWorkflowService(new AtomicReference<>(), new AtomicReference<>())
        );
        setField(orchestrator, "notificationsEnabled", false);

        orchestrator.triggerAssessment(
                9L,
                DiseaseType.HYPERTENSION,
                Map.of("systolicBp", 180, "diastolicBp", 110),
                Map.of()
        );

        assertEquals(1, toolCalls.get());
        assertNotNull(savedAssessment.get());
        assertEquals(DiseaseType.HYPERTENSION, savedAssessment.get().getDiseaseType());
        assertEquals(95.0, savedAssessment.get().getScore());
        assertEquals("react", savedAssessment.get().getAssessmentProvider());
        assertTrue(savedAssessment.get().getOrchestrationSessionId().startsWith("assessment-9-"));
    }

    @Test
    void shouldUseRequestHealthDataOverridesWhenTriggeringReactAssessment() throws Exception {
        AtomicReference<RiskAssessment> savedAssessment = new AtomicReference<>();
        AtomicReference<HealthRecord> assessedRecord = new AtomicReference<>();
        RuleAssessmentService capturingAssessmentService = new RuleAssessmentService(null) {
            @Override
            public List<com.medical.agent.service.chat.RuleAssessmentResult> assess(
                    com.medical.agent.service.chat.ChatPatientContextData patientContext,
                    String userQuestion,
                    DiseaseType preferredDiseaseType) {
                assessedRecord.set(patientContext.latestRecord());
                return List.of(new com.medical.agent.service.chat.RuleAssessmentResult(
                        preferredDiseaseType == null ? DiseaseType.HYPERTENSION : preferredDiseaseType,
                        patientContext.latestRecord() != null
                                && patientContext.latestRecord().getSystolicBp() != null
                                && patientContext.latestRecord().getSystolicBp() >= 180
                                ? RiskLevel.HIGH
                                : RiskLevel.LOW,
                        patientContext.latestRecord() != null
                                && patientContext.latestRecord().getSystolicBp() != null
                                && patientContext.latestRecord().getSystolicBp() >= 180
                                ? 95.0
                                : 20.0,
                        "capture",
                        List.of(),
                        "test"
                ));
            }
        };

        ReActClinicalAssessmentOrchestrator orchestrator = new ReActClinicalAssessmentOrchestrator(
                new RiskAssessmentTool(chatContextServiceWithBaselineRecord(), capturingAssessmentService),
                patientRepository(),
                new AiChatCacheInvalidationService(new NoOpCacheManager(), new AiChatCacheVersionService()),
                new StubClinicalNotificationGateway(new AtomicReference<>()),
                assessmentResultPersistenceService(repository(args -> {
                    RiskAssessment assessment = (RiskAssessment) args[0];
                    savedAssessment.set(assessment);
                    return assessment;
                })),
                assessmentFollowUpWorkflowService(new AtomicReference<>(), new AtomicReference<>())
        );
        setField(orchestrator, "notificationsEnabled", false);

        orchestrator.triggerAssessment(
                9L,
                DiseaseType.HYPERTENSION,
                Map.of("systolicBp", 182, "diastolicBp", 112),
                Map.of("_silentPatientNotification", true)
        );

        assertNotNull(savedAssessment.get());
        assertNotNull(assessedRecord.get());
        assertEquals(182, assessedRecord.get().getSystolicBp());
        assertEquals(112, assessedRecord.get().getDiastolicBp());
        assertEquals(DiseaseType.HYPERTENSION, savedAssessment.get().getDiseaseType());
        assertEquals(95.0, savedAssessment.get().getScore());
        assertEquals(RiskLevel.HIGH, savedAssessment.get().getRiskLevel());
    }

    @Test
    void shouldUseAgentStateRequestDataWhenToolInputIsEmpty() {
        AtomicReference<HealthRecord> assessedRecord = new AtomicReference<>();
        RuleAssessmentService capturingAssessmentService = new RuleAssessmentService(null) {
            @Override
            public List<com.medical.agent.service.chat.RuleAssessmentResult> assess(
                    com.medical.agent.service.chat.ChatPatientContextData patientContext,
                    String userQuestion,
                    DiseaseType preferredDiseaseType) {
                assessedRecord.set(patientContext.latestRecord());
                return List.of(
                        new com.medical.agent.service.chat.RuleAssessmentResult(
                                DiseaseType.HYPERTENSION,
                                RiskLevel.HIGH,
                                90.0,
                                "capture",
                                List.of(),
                                "test"
                        )
                );
            }
        };
        RiskAssessmentTool tool = new RiskAssessmentTool(chatContextServiceWithBaselineRecord(), capturingAssessmentService);
        AgentState state = new AgentState();
        state.setPatientId(9L);
        state.setUserMessage("请完成高血压风险评估");
        state.setRequestData(Map.of("systolicBp", 188, "diastolicBp", 116, "diseaseType", "HYPERTENSION"));

        ToolExecutionResult result = tool.execute(state, Map.of());

        assertTrue(result.success());
        assertNotNull(assessedRecord.get());
        assertEquals(188, assessedRecord.get().getSystolicBp());
        assertEquals(116, assessedRecord.get().getDiastolicBp());
    }

    private PatientRepository patientRepository() {
        return com.medical.agent.service.testsupport.RepositoryProxyFactory.create(PatientRepository.class, Map.of(
                "findById", args -> Optional.of(Patient.builder().id(9L).name("评估患者").build())
        ));
    }

    private ChatContextService chatContextServiceWithBaselineRecord() {
        PatientRepository patientRepository = com.medical.agent.service.testsupport.RepositoryProxyFactory.create(PatientRepository.class, Map.of(
                "findById", args -> Optional.of(Patient.builder().id(9L).name("评估患者").birthDate(LocalDate.of(1980, 1, 1)).build())
        ));
        HealthRecordRepository healthRecordRepository = com.medical.agent.service.testsupport.RepositoryProxyFactory.create(HealthRecordRepository.class, Map.of(
                "findFirstByPatientIdOrderByRecordDateDesc", args -> Optional.of(
                        HealthRecord.builder()
                                .patientId(9L)
                                .recordDate(LocalDate.now())
                                .systolicBp(126)
                                .diastolicBp(82)
                                .build()
                )
        ));
        PatientMedicalHistoryRepository historyRepository = com.medical.agent.service.testsupport.RepositoryProxyFactory.create(PatientMedicalHistoryRepository.class, Map.of());
        return new ChatContextService(
                patientRepository,
                com.medical.agent.service.testsupport.RepositoryProxyFactory.create(com.medical.agent.model.repository.DoctorRepository.class, Map.of()),
                com.medical.agent.service.testsupport.RepositoryProxyFactory.create(RiskAssessmentRepository.class, Map.of(
                        "findByPatientIdOrderByCreatedAtDesc", args -> List.of()
                )),
                com.medical.agent.service.testsupport.RepositoryProxyFactory.create(FollowUpPlanRepository.class, Map.of(
                        "findByPatientIdOrderByCreatedAtDesc", args -> List.of()
                )),
                healthRecordRepository,
                historyRepository
        );
    }

    private RiskAssessmentRepository repository(java.util.function.Function<Object[], Object> saveHandler) {
        return com.medical.agent.service.testsupport.RepositoryProxyFactory.create(RiskAssessmentRepository.class, Map.of(
                "save", saveHandler
        ));
    }

    private AssessmentResultPersistenceService assessmentResultPersistenceService(RiskAssessmentRepository riskAssessmentRepository) {
        return new AssessmentResultPersistenceService(
                riskAssessmentRepository,
                patientRepository(),
                new AiChatCacheInvalidationService(new NoOpCacheManager(), new AiChatCacheVersionService())
        );
    }

    private AssessmentFollowUpWorkflowService assessmentFollowUpWorkflowService(AtomicReference<FollowUpPlan> savedFollowUpPlan,
                                                                                AtomicReference<ResourceRecommendation> savedRecommendation) {
        return new AssessmentFollowUpWorkflowService(
                followUpPlanRepository(savedFollowUpPlan),
                resourceRecommendationRepository(savedRecommendation),
                new StubClinicalNotificationGateway(new AtomicReference<>()),
                new AiChatCacheInvalidationService(new NoOpCacheManager(), new AiChatCacheVersionService())
        );
    }

    private FollowUpPlanRepository followUpPlanRepository(AtomicReference<FollowUpPlan> savedFollowUpPlan) {
        return com.medical.agent.service.testsupport.RepositoryProxyFactory.create(FollowUpPlanRepository.class, Map.of(
                "findByPatientIdAndDiseaseTypeAndStatusInOrderByCreatedAtDesc", args -> List.of(),
                "save", args -> {
                    FollowUpPlan plan = (FollowUpPlan) args[0];
                    savedFollowUpPlan.set(plan);
                    return plan;
                }
        ));
    }

    private ResourceRecommendationRepository resourceRecommendationRepository(AtomicReference<ResourceRecommendation> savedRecommendation) {
        return com.medical.agent.service.testsupport.RepositoryProxyFactory.create(ResourceRecommendationRepository.class, Map.of(
                "save", args -> {
                    ResourceRecommendation recommendation = (ResourceRecommendation) args[0];
                    savedRecommendation.set(recommendation);
                    return recommendation;
                }
        ));
    }

    private void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static final class StubRiskAssessmentTool extends RiskAssessmentTool {

        private StubRiskAssessmentTool() {
            super(null, null);
        }

        @Override
        public ToolExecutionResult execute(AgentState state, Map<String, Object> input) {
            state.setAssessmentResults(List.of(
                    new com.medical.agent.service.chat.RuleAssessmentResult(
                            DiseaseType.DIABETES,
                            RiskLevel.HIGH,
                            88.0,
                            "评估完成\n病史提示：药物过敏史：青霉素",
                            List.of("规律复查"),
                            "drools"
                    )
            ));
            return new ToolExecutionResult(name(), true, "已完成规则评估。结果：糖尿病：高危");
        }
    }

    private static final class CountingRiskAssessmentTool extends RiskAssessmentTool {
        private final AtomicInteger toolCalls;
        private final List<com.medical.agent.service.chat.RuleAssessmentResult> results;

        private CountingRiskAssessmentTool(AtomicInteger toolCalls) {
            this(toolCalls, List.of());
        }

        private CountingRiskAssessmentTool(AtomicInteger toolCalls,
                                           List<com.medical.agent.service.chat.RuleAssessmentResult> results) {
            super(null, null);
            this.toolCalls = toolCalls;
            this.results = results;
        }

        @Override
        public ToolExecutionResult execute(AgentState state, Map<String, Object> input) {
            toolCalls.incrementAndGet();
            if (results != null && !results.isEmpty()) {
                state.setAssessmentResults(results);
                return new ToolExecutionResult(name(), true, "回退直接工具执行成功");
            }
            return new ToolExecutionResult(name(), false, "direct tool should not be called");
        }
    }

    private static final class StubChatSupervisorService extends ChatSupervisorService {
        private final java.util.function.Function<AgentState, ReActResult> runner;

        private StubChatSupervisorService(java.util.function.Function<AgentState, ReActResult> runner) {
            super(null, null, null, null, null);
            this.runner = runner;
        }

        @Override
        public ReActResult run(AgentState state) {
            return runner.apply(state);
        }
    }

    private record StubClinicalNotificationGateway(AtomicReference<NotificationDTO> notification)
            implements ClinicalNotificationGateway {

        @Override
        public void setNotificationConsumer(java.util.function.Consumer<NotificationDTO> consumer) {
        }

        @Override
        public void sendNotification(NotificationDTO notification) {
            this.notification.set(notification);
        }
    }
}
