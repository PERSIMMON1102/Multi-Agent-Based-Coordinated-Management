package com.medical.agent.service;

import com.medical.agent.common.bridge.JadeAssessmentAgentRuntime;
import com.medical.agent.common.message.AgentMessage;
import com.medical.agent.common.dto.NotificationDTO;
import com.medical.agent.common.enums.DiseaseType;
import com.medical.agent.common.enums.FollowUpFrequency;
import com.medical.agent.common.enums.FollowUpStatus;
import com.medical.agent.common.enums.RiskLevel;
import com.medical.agent.model.entity.FollowUpPlan;
import com.medical.agent.model.entity.ResourceRecommendation;
import com.medical.agent.model.entity.RiskAssessment;
import com.medical.agent.model.mongo.repository.AgentMessageLogRepository;
import com.medical.agent.model.repository.FollowUpPlanRepository;
import com.medical.agent.model.repository.ResourceRecommendationRepository;
import com.medical.agent.model.repository.RiskAssessmentRepository;
import com.medical.agent.service.cache.AiChatCacheVersionService;
import com.medical.agent.service.cache.AiChatCacheInvalidationService;
import com.medical.agent.service.testsupport.RepositoryProxyFactory;
import org.junit.jupiter.api.Test;
import org.kie.api.runtime.KieContainer;
import org.springframework.cache.support.NoOpCacheManager;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentBridgeServiceMessageHandlerTest {

    @Test
    void shouldPersistAssessmentAndPushNotification() throws Exception {
        AtomicReference<RiskAssessment> savedAssessment = new AtomicReference<>();
        AtomicReference<FollowUpPlan> savedPlan = new AtomicReference<>();
        AtomicReference<ResourceRecommendation> savedRecommendation = new AtomicReference<>();
        List<NotificationDTO> notifications = new ArrayList<>();

        RiskAssessmentRepository assessmentRepository = RepositoryProxyFactory.create(RiskAssessmentRepository.class, Map.of(
                "save", args -> {
                    savedAssessment.set((RiskAssessment) args[0]);
                    return args[0];
                }
        ));

        AgentBridgeService service = service(
                assessmentRepository,
                RepositoryProxyFactory.create(FollowUpPlanRepository.class, Map.of(
                        "findByPatientIdAndDiseaseTypeAndStatusInOrderByCreatedAtDesc", args -> List.of(),
                        "save", args -> {
                            savedPlan.set((FollowUpPlan) args[0]);
                            return args[0];
                        }
                )),
                RepositoryProxyFactory.create(ResourceRecommendationRepository.class, Map.of(
                        "save", args -> {
                            savedRecommendation.set((ResourceRecommendation) args[0]);
                            return args[0];
                        }
                )),
                notifications
        );
        coordinator(service).handleAssessmentResult(message(
                DiseaseType.HYPERTENSION,
                RiskLevel.HIGH,
                Map.of(),
                "评估完成\n病史提示：既往高血压病史；药物过敏史：青霉素",
                List.of("规律复查")
        ));

        assertNotNull(savedAssessment.get());
        assertNotNull(savedPlan.get());
        assertNotNull(savedRecommendation.get());
        assertEquals(DiseaseType.HYPERTENSION, savedAssessment.get().getDiseaseType());
        assertEquals(RiskLevel.HIGH, savedAssessment.get().getRiskLevel());
        assertEquals("jade", savedAssessment.get().getAssessmentProvider());
        assertEquals("a-1", savedAssessment.get().getOrchestrationSessionId());
        assertEquals("AssessmentAgent", savedAssessment.get().getOrchestrationAssignedAgent());
        assertEquals(3, notifications.size());
        assertEquals("ASSESSMENT_RESULT", notifications.get(0).getType());
        assertEquals("FOLLOW_UP_REMINDER", notifications.get(1).getType());
        assertEquals("RESOURCE_RECOMMENDATION", notifications.get(2).getType());
        assertEquals(List.of("既往高血压病史", "药物过敏史：青霉素"), notifications.get(0).getHistoryAlerts());
        assertTrue(notifications.get(0).getMessage().contains("病史提醒"));
    }

    @Test
    void shouldSkipAssessmentNotificationWhenSuppressed() throws Exception {
        List<NotificationDTO> notifications = new ArrayList<>();
        RiskAssessmentRepository assessmentRepository = RepositoryProxyFactory.create(RiskAssessmentRepository.class, Map.of(
                "save", args -> args[0]
        ));

        AgentBridgeService service = service(assessmentRepository, emptyFollowUpRepo(), emptyRecommendationRepo(), notifications);
        coordinator(service).handleAssessmentResult(message(
                DiseaseType.DIABETES,
                RiskLevel.MEDIUM,
                Map.of("_silentPatientNotification", true),
                "评估完成",
                List.of("规律复查")
        ));

        assertEquals(1, notifications.size());
        assertEquals("FOLLOW_UP_REMINDER", notifications.get(0).getType());
    }

    @Test
    void shouldCreateNewFollowUpPlanAndNotifyWhenNoActivePlanExists() throws Exception {
        AtomicReference<FollowUpPlan> savedPlan = new AtomicReference<>();
        List<NotificationDTO> notifications = new ArrayList<>();

        FollowUpPlanRepository followUpPlanRepository = RepositoryProxyFactory.create(FollowUpPlanRepository.class, Map.of(
                "findByPatientIdAndDiseaseTypeAndStatusInOrderByCreatedAtDesc", args -> List.of(),
                "save", args -> {
                    savedPlan.set((FollowUpPlan) args[0]);
                    return args[0];
                }
        ));

        AgentBridgeService service = service(emptyAssessmentRepo(), followUpPlanRepository, emptyRecommendationRepo(), notifications);
        AgentMessage followUpMessage = AgentMessage.builder()
                .messageId("f-1")
                .patientId(1L)
                .patientName("患者甲")
                .diseaseType(DiseaseType.HYPERTENSION)
                .riskLevel(RiskLevel.HIGH)
                .data(Map.of(
                        "frequency", FollowUpFrequency.MONTHLY.name(),
                        "checkItems", List.of("测量血压", "评估服药依从性"),
                        "planContent", "请本月按时复查"
                ))
                .build();

        coordinator(service).handleFollowUpCreated(followUpMessage);

        assertNotNull(savedPlan.get());
        assertEquals(DiseaseType.HYPERTENSION, savedPlan.get().getDiseaseType());
        assertEquals(FollowUpFrequency.MONTHLY, savedPlan.get().getFrequency());
        assertTrue(savedPlan.get().getCheckItems().contains("测量血压"));
        assertEquals(1, notifications.size());
        assertEquals("FOLLOW_UP_REMINDER", notifications.get(0).getType());
    }

    @Test
    void shouldRefreshExistingFollowUpPlanWithoutPushingNotification() throws Exception {
        List<NotificationDTO> notifications = new ArrayList<>();
        AtomicReference<FollowUpPlan> savedPlan = new AtomicReference<>();
        FollowUpPlan existingPlan = FollowUpPlan.builder()
                .id(99L)
                .patientId(1L)
                .diseaseType(DiseaseType.DIABETES)
                .frequency(FollowUpFrequency.QUARTERLY)
                .status(FollowUpStatus.PENDING)
                .build();

        FollowUpPlanRepository followUpPlanRepository = RepositoryProxyFactory.create(FollowUpPlanRepository.class, Map.of(
                "findByPatientIdAndDiseaseTypeAndStatusInOrderByCreatedAtDesc", args -> List.of(existingPlan),
                "save", args -> {
                    savedPlan.set((FollowUpPlan) args[0]);
                    return args[0];
                }
        ));

        AgentBridgeService service = service(emptyAssessmentRepo(), followUpPlanRepository, emptyRecommendationRepo(), notifications);
        AgentMessage followUpMessage = AgentMessage.builder()
                .messageId("f-2")
                .patientId(1L)
                .patientName("患者乙")
                .diseaseType(DiseaseType.DIABETES)
                .riskLevel(RiskLevel.MEDIUM)
                .data(Map.of(
                        "frequency", FollowUpFrequency.MONTHLY.name(),
                        "checkItems", List.of("空腹血糖检测"),
                        "planContent", "请更新复查安排"
                ))
                .build();

        coordinator(service).handleFollowUpCreated(followUpMessage);

        assertNotNull(savedPlan.get());
        assertEquals(99L, savedPlan.get().getId());
        assertEquals(FollowUpFrequency.MONTHLY, savedPlan.get().getFrequency());
        assertTrue(notifications.isEmpty());
    }

    @Test
    void shouldPersistResourceRecommendationAndPushNotification() throws Exception {
        AtomicReference<ResourceRecommendation> savedRecommendation = new AtomicReference<>();
        List<NotificationDTO> notifications = new ArrayList<>();

        ResourceRecommendationRepository recommendationRepository =
                RepositoryProxyFactory.create(ResourceRecommendationRepository.class, Map.of(
                        "save", args -> {
                            savedRecommendation.set((ResourceRecommendation) args[0]);
                            return args[0];
                        }
                ));

        AgentBridgeService service = service(emptyAssessmentRepo(), emptyFollowUpRepo(), recommendationRepository, notifications);
        AgentMessage resourceMessage = AgentMessage.builder()
                .messageId("r-1")
                .patientId(1L)
                .patientName("患者丙")
                .diseaseType(DiseaseType.CHD)
                .riskLevel(RiskLevel.HIGH)
                .detail("建议尽快转诊心内科")
                .build();

        coordinator(service).handleResourceRecommended(resourceMessage);

        assertNotNull(savedRecommendation.get());
        assertEquals(1L, savedRecommendation.get().getPatientId());
        assertEquals("建议尽快转诊心内科", savedRecommendation.get().getReason());
        assertEquals(1, notifications.size());
        assertEquals("RESOURCE_RECOMMENDATION", notifications.get(0).getType());
    }

    private AgentBridgeService service(RiskAssessmentRepository assessmentRepository,
                                       FollowUpPlanRepository followUpPlanRepository,
                                       ResourceRecommendationRepository recommendationRepository,
                                       List<NotificationDTO> notifications) {
        AgentMessageLogRepository messageLogRepository = RepositoryProxyFactory.create(AgentMessageLogRepository.class, Map.of(
                "save", args -> args[0]
        ));
        KieContainer kieContainer = RepositoryProxyFactory.create(KieContainer.class, Map.of());
        return new AgentBridgeService(
                new JadeAssessmentAgentLifecycleService(new NoopRuntime(), new DisabledLlmService()),
                null,
                new JadeAssessmentWorkflowService(kieContainer),
                new JadeAgentMessageCoordinator(
                        assessmentResultPersistenceService(assessmentRepository),
                        assessmentFollowUpWorkflowService(followUpPlanRepository, recommendationRepository, notifications),
                        new JadeAssessmentWorkflowService(kieContainer),
                        new AgentMessageAuditService(messageLogRepository),
                        new RecordingNotificationGateway(notifications)
                )
        );
    }

    private RiskAssessmentRepository emptyAssessmentRepo() {
        return RepositoryProxyFactory.create(RiskAssessmentRepository.class, Map.of(
                "save", args -> args[0]
        ));
    }

    private AssessmentResultPersistenceService assessmentResultPersistenceService(RiskAssessmentRepository assessmentRepository) {
        return new AssessmentResultPersistenceService(
                assessmentRepository,
                RepositoryProxyFactory.create(com.medical.agent.model.repository.PatientRepository.class, Map.of()),
                new AiChatCacheInvalidationService(new NoOpCacheManager(), new AiChatCacheVersionService())
        );
    }

    private AssessmentFollowUpWorkflowService assessmentFollowUpWorkflowService(FollowUpPlanRepository followUpPlanRepository,
                                                                                ResourceRecommendationRepository recommendationRepository,
                                                                                List<NotificationDTO> notifications) {
        return new AssessmentFollowUpWorkflowService(
                followUpPlanRepository,
                recommendationRepository,
                new RecordingNotificationGateway(notifications),
                new AiChatCacheInvalidationService(new NoOpCacheManager(), new AiChatCacheVersionService())
        );
    }

    private FollowUpPlanRepository emptyFollowUpRepo() {
        return RepositoryProxyFactory.create(FollowUpPlanRepository.class, Map.of(
                "findByPatientIdAndDiseaseTypeAndStatusInOrderByCreatedAtDesc", args -> List.of(),
                "save", args -> args[0]
        ));
    }

    private ResourceRecommendationRepository emptyRecommendationRepo() {
        return RepositoryProxyFactory.create(ResourceRecommendationRepository.class, Map.of(
                "save", args -> args[0]
        ));
    }

    private AgentMessage message(DiseaseType diseaseType,
                                 RiskLevel riskLevel,
                                 Map<String, Object> data,
                                 String detail,
                                 List<String> recommendations) {
        return AgentMessage.builder()
                .messageId("a-1")
                .patientId(1L)
                .patientName("患者甲")
                .diseaseType(diseaseType)
                .riskLevel(riskLevel)
                .score(80.0)
                .detail(detail)
                .recommendations(recommendations)
                .data(data)
                .build();
    }

    private JadeAgentMessageCoordinator coordinator(AgentBridgeService service) throws Exception {
        Field field = AgentBridgeService.class.getDeclaredField("jadeAgentMessageCoordinator");
        field.setAccessible(true);
        return (JadeAgentMessageCoordinator) field.get(service);
    }

    private static class DisabledLlmService extends LlmService {
        @Override
        public boolean isAvailable() {
            return false;
        }
    }

    private static class NoopRuntime implements JadeAssessmentAgentRuntime {
        @Override
        public void ensureAssessmentAgentReady(java.util.function.Consumer<AgentMessage> resultCallback,
                                               java.util.function.BiFunction<String, String, String> llmFunction) {
        }

        @Override
        public void dispatchAssessmentRequest(AgentMessage request) {
        }
    }

    private record RecordingNotificationGateway(List<NotificationDTO> notifications) implements ClinicalNotificationGateway {
        @Override
        public void setNotificationConsumer(java.util.function.Consumer<NotificationDTO> consumer) {
        }

        @Override
        public void sendNotification(NotificationDTO notification) {
            notifications.add(notification);
        }
    }
}
