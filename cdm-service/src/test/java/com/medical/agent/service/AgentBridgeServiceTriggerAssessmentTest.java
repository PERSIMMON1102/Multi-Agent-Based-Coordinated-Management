package com.medical.agent.service;

import com.medical.agent.common.bridge.JadeAssessmentAgentRuntime;
import com.medical.agent.common.message.AgentMessage;
import com.medical.agent.common.message.MessageType;
import com.medical.agent.common.dto.NotificationDTO;
import com.medical.agent.common.enums.DiseaseType;
import com.medical.agent.common.enums.RiskLevel;
import com.medical.agent.model.entity.Patient;
import com.medical.agent.model.entity.PatientMedicalHistory;
import com.medical.agent.model.entity.RiskAssessment;
import com.medical.agent.model.mongo.repository.AgentMessageLogRepository;
import com.medical.agent.model.repository.FollowUpPlanRepository;
import com.medical.agent.model.repository.PatientMedicalHistoryRepository;
import com.medical.agent.model.repository.PatientRepository;
import com.medical.agent.model.repository.ResourceRecommendationRepository;
import com.medical.agent.model.repository.RiskAssessmentRepository;
import com.medical.agent.service.cache.AiChatCacheVersionService;
import com.medical.agent.service.cache.AiChatCacheInvalidationService;
import com.medical.agent.service.testsupport.RepositoryProxyFactory;
import org.junit.jupiter.api.Test;
import org.kie.api.runtime.KieContainer;
import org.kie.api.runtime.KieSession;
import org.springframework.cache.support.NoOpCacheManager;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentBridgeServiceTriggerAssessmentTest {

    @Test
    void shouldSendMergedAssessmentRequestToAgent() throws Exception {
        AtomicReference<AgentMessage> forwardedMessage = new AtomicReference<>();
        RecordingRuntime runtime = new RecordingRuntime();
        AgentBridgeService service = service(
                runtime,
                RepositoryProxyFactory.create(PatientRepository.class, Map.of(
                        "findById", args -> Optional.of(patient())
                )),
                RepositoryProxyFactory.create(PatientMedicalHistoryRepository.class, Map.of(
                        "findByPatientId", args -> Optional.of(history())
                )),
                RepositoryProxyFactory.create(RiskAssessmentRepository.class, Map.of()),
                RepositoryProxyFactory.create(FollowUpPlanRepository.class, Map.of(
                        "findByPatientIdAndDiseaseTypeAndStatusInOrderByCreatedAtDesc", args -> List.of(),
                        "save", args -> args[0]
                )),
                disabledKieContainer(),
                new SilentNotificationGateway()
        );

        runtime.dispatchHook = forwardedMessage::set;

        service.triggerAssessment(
                1L,
                DiseaseType.HYPERTENSION,
                Map.of("systolicBp", 156, "diastolicBp", 96),
                Map.of("_silentPatientNotification", true, "source", "manual_test")
        );

        assertNotNull(forwardedMessage.get());
        assertEquals(MessageType.ASSESS_REQUEST, forwardedMessage.get().getMessageType());
        assertEquals(DiseaseType.HYPERTENSION, forwardedMessage.get().getDiseaseType());
        assertEquals(156, forwardedMessage.get().getData().get("systolicBp"));
        assertEquals(96, forwardedMessage.get().getData().get("diastolicBp"));
        assertEquals(true, forwardedMessage.get().getData().get("_silentPatientNotification"));
        assertEquals(true, forwardedMessage.get().getData().get("_disableJadeSecondaryAgents"));
        assertEquals("manual_test", forwardedMessage.get().getData().get("source"));
        assertEquals("高血压、糖尿病", forwardedMessage.get().getData().get("pastDiseases"));
        assertEquals("青霉素", forwardedMessage.get().getData().get("drugAllergies"));
        assertEquals(List.of("HYPERTENSION", "DIABETES"), forwardedMessage.get().getData().get("historyDiseaseTypes"));
    }

    @Test
    void shouldFallbackAndPersistAssessmentWhenAgentSendFails() throws Exception {
        AtomicReference<RiskAssessment> savedAssessment = new AtomicReference<>();
        AtomicReference<com.medical.agent.model.entity.FollowUpPlan> savedPlan = new AtomicReference<>();
        List<NotificationDTO> notifications = new ArrayList<>();
        RecordingRuntime runtime = new RecordingRuntime();
        runtime.dispatchFailure = new RuntimeException("simulated send failure");

        AgentBridgeService service = service(
                runtime,
                RepositoryProxyFactory.create(PatientRepository.class, Map.of(
                        "findById", args -> Optional.of(patient())
                )),
                RepositoryProxyFactory.create(PatientMedicalHistoryRepository.class, Map.of(
                        "findByPatientId", args -> Optional.of(history())
                )),
                RepositoryProxyFactory.create(RiskAssessmentRepository.class, Map.of(
                        "save", args -> {
                            savedAssessment.set((RiskAssessment) args[0]);
                            return args[0];
                        }
                )),
                RepositoryProxyFactory.create(FollowUpPlanRepository.class, Map.of(
                        "findByPatientIdAndDiseaseTypeAndStatusInOrderByCreatedAtDesc", args -> List.of(),
                        "save", args -> {
                            com.medical.agent.model.entity.FollowUpPlan plan =
                                    (com.medical.agent.model.entity.FollowUpPlan) args[0];
                            savedPlan.set(plan);
                            return plan;
                        }
                )),
                fallbackKieContainer(),
                new RecordingNotificationGateway(notifications)
        );
        service.triggerAssessment(
                1L,
                DiseaseType.DIABETES,
                Map.of("fastingGlucose", 8.2),
                Map.of()
        );

        assertNotNull(savedAssessment.get());
        assertNotNull(savedPlan.get());
        assertEquals(1L, savedAssessment.get().getPatientId());
        assertEquals(DiseaseType.DIABETES, savedAssessment.get().getDiseaseType());
        assertEquals(RiskLevel.LOW, savedAssessment.get().getRiskLevel());
        assertEquals(0.0, savedAssessment.get().getScore());
        assertEquals(2, notifications.size());
        assertEquals("ASSESSMENT_RESULT", notifications.get(0).getType());
        assertEquals("FOLLOW_UP_REMINDER", notifications.get(1).getType());
        assertTrue(notifications.get(0).getMessage().contains("病史提醒"));
        assertTrue(notifications.get(0).getHistoryAlerts().stream().anyMatch(item -> item.contains("药物过敏史")));
        assertTrue(savedAssessment.get().getAssessmentDetail().contains("病史提示"));
    }

    private AgentBridgeService service(JadeAssessmentAgentRuntime runtime,
                                       PatientRepository patientRepository,
                                       PatientMedicalHistoryRepository historyRepository,
                                       RiskAssessmentRepository assessmentRepository,
                                       FollowUpPlanRepository followUpPlanRepository,
                                       KieContainer kieContainer,
                                       ClinicalNotificationGateway notificationGateway) {
        ResourceRecommendationRepository recommendationRepository =
                RepositoryProxyFactory.create(ResourceRecommendationRepository.class, Map.of());
        AgentMessageLogRepository messageLogRepository =
                RepositoryProxyFactory.create(AgentMessageLogRepository.class, Map.of(
                        "save", args -> args[0]
                ));

        return new AgentBridgeService(
                new JadeAssessmentAgentLifecycleService(runtime, new DisabledLlmService()),
                new JadeAssessmentRequestFactory(patientRepository, historyRepository),
                new JadeAssessmentWorkflowService(kieContainer),
                new JadeAgentMessageCoordinator(
                        new AssessmentResultPersistenceService(
                                assessmentRepository,
                                patientRepository,
                                new AiChatCacheInvalidationService(new NoOpCacheManager(), new AiChatCacheVersionService())
                        ),
                        new AssessmentFollowUpWorkflowService(
                                followUpPlanRepository,
                                recommendationRepository,
                                notificationGateway,
                                new AiChatCacheInvalidationService(new NoOpCacheManager(), new AiChatCacheVersionService())
                        ),
                        new JadeAssessmentWorkflowService(kieContainer),
                        new AgentMessageAuditService(messageLogRepository),
                        notificationGateway
                )
        );
    }

    private KieContainer fallbackKieContainer() {
        KieSession kieSession = RepositoryProxyFactory.create(KieSession.class, Map.of(
                "insert", args -> null,
                "fireAllRules", args -> 0,
                "dispose", args -> null
        ));
        return RepositoryProxyFactory.create(KieContainer.class, Map.of(
                "newKieSession", args -> kieSession
        ));
    }

    private KieContainer disabledKieContainer() {
        return RepositoryProxyFactory.create(KieContainer.class, Map.of());
    }

    private Patient patient() {
        return Patient.builder()
                .id(1L)
                .name("触发测试患者")
                .gender("男")
                .build();
    }

    private PatientMedicalHistory history() {
        return PatientMedicalHistory.builder()
                .patientId(1L)
                .pastDiseases("高血压、糖尿病")
                .drugAllergies("青霉素")
                .build();
    }

    private static class DisabledLlmService extends LlmService {
        @Override
        public boolean isAvailable() {
            return false;
        }
    }

    private static class RecordingRuntime implements JadeAssessmentAgentRuntime {
        private java.util.function.Consumer<AgentMessage> dispatchHook;
        private RuntimeException dispatchFailure;

        @Override
        public void ensureAssessmentAgentReady(java.util.function.Consumer<AgentMessage> resultCallback,
                                               java.util.function.BiFunction<String, String, String> llmFunction) {
        }

        @Override
        public void dispatchAssessmentRequest(AgentMessage request) {
            if (dispatchFailure != null) {
                throw dispatchFailure;
            }
            if (dispatchHook != null) {
                dispatchHook.accept(request);
            }
        }
    }

    private static class SilentNotificationGateway implements ClinicalNotificationGateway {
        @Override
        public void setNotificationConsumer(java.util.function.Consumer<NotificationDTO> consumer) {
        }

        @Override
        public void sendNotification(NotificationDTO notification) {
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
