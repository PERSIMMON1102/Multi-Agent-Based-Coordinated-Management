package com.medical.agent.service;

import com.medical.agent.common.message.AgentMessage;
import com.medical.agent.common.dto.NotificationDTO;
import com.medical.agent.common.enums.DiseaseType;
import com.medical.agent.common.enums.RiskLevel;
import com.medical.agent.model.entity.FollowUpPlan;
import com.medical.agent.model.entity.Patient;
import com.medical.agent.model.entity.ResourceRecommendation;
import com.medical.agent.model.entity.RiskAssessment;
import com.medical.agent.model.mongo.AgentMessageLog;
import com.medical.agent.model.mongo.repository.AgentMessageLogRepository;
import com.medical.agent.model.repository.FollowUpPlanRepository;
import com.medical.agent.model.repository.PatientRepository;
import com.medical.agent.model.repository.ResourceRecommendationRepository;
import com.medical.agent.model.repository.RiskAssessmentRepository;
import com.medical.agent.service.cache.AiChatCacheInvalidationService;
import com.medical.agent.service.cache.AiChatCacheVersionService;
import com.medical.agent.service.testsupport.RepositoryProxyFactory;
import org.junit.jupiter.api.Test;
import org.kie.api.runtime.KieContainer;
import org.springframework.cache.support.NoOpCacheManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class JadeAgentMessageCoordinatorTest {

    @Test
    void shouldPersistAssessmentAndAuditMessage() {
        AtomicReference<RiskAssessment> savedAssessment = new AtomicReference<>();
        AtomicReference<FollowUpPlan> savedPlan = new AtomicReference<>();
        AtomicReference<ResourceRecommendation> savedRecommendation = new AtomicReference<>();
        AtomicReference<AgentMessageLog> savedAudit = new AtomicReference<>();
        List<NotificationDTO> notifications = new ArrayList<>();
        JadeAgentMessageCoordinator coordinator = coordinator(
                RepositoryProxyFactory.create(RiskAssessmentRepository.class, Map.of(
                        "save", args -> {
                            savedAssessment.set((RiskAssessment) args[0]);
                            return args[0];
                        }
                )),
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
                RepositoryProxyFactory.create(AgentMessageLogRepository.class, Map.of(
                        "save", args -> {
                            savedAudit.set((AgentMessageLog) args[0]);
                            return args[0];
                        }
                )),
                notifications
        );

        coordinator.handleAssessmentResult(AgentMessage.builder()
                .messageId("a-1")
                .patientId(1L)
                .patientName("患者甲")
                .diseaseType(DiseaseType.HYPERTENSION)
                .riskLevel(RiskLevel.HIGH)
                .detail("评估完成")
                .recommendations(List.of("规律复查"))
                .data(Map.of())
                .build());

        assertNotNull(savedAssessment.get());
        assertNotNull(savedPlan.get());
        assertNotNull(savedRecommendation.get());
        assertEquals("jade", savedAssessment.get().getAssessmentProvider());
        assertNotNull(savedAudit.get());
        assertEquals("AssessmentAgent", savedAudit.get().getSenderAgent());
        assertEquals(3, notifications.size());
        assertEquals("ASSESSMENT_RESULT", notifications.get(0).getType());
        assertEquals("FOLLOW_UP_REMINDER", notifications.get(1).getType());
        assertEquals("RESOURCE_RECOMMENDATION", notifications.get(2).getType());
    }

    @Test
    void shouldPersistResourceRecommendationAndNotify() {
        AtomicReference<ResourceRecommendation> savedRecommendation = new AtomicReference<>();
        List<NotificationDTO> notifications = new ArrayList<>();
        JadeAgentMessageCoordinator coordinator = coordinator(
                RepositoryProxyFactory.create(RiskAssessmentRepository.class, Map.of(
                        "save", args -> args[0]
                )),
                RepositoryProxyFactory.create(FollowUpPlanRepository.class, Map.of(
                        "findByPatientIdAndDiseaseTypeAndStatusInOrderByCreatedAtDesc", args -> List.of(),
                        "save", args -> args[0]
                )),
                RepositoryProxyFactory.create(ResourceRecommendationRepository.class, Map.of(
                        "save", args -> {
                            savedRecommendation.set((ResourceRecommendation) args[0]);
                            return args[0];
                        }
                )),
                RepositoryProxyFactory.create(AgentMessageLogRepository.class, Map.of(
                        "save", args -> args[0]
                )),
                notifications
        );

        coordinator.handleResourceRecommended(AgentMessage.builder()
                .messageId("r-1")
                .patientId(1L)
                .patientName("患者甲")
                .detail("建议尽快转诊心内科")
                .build());

        assertNotNull(savedRecommendation.get());
        assertEquals("建议尽快转诊心内科", savedRecommendation.get().getReason());
        assertEquals(1, notifications.size());
        assertEquals("RESOURCE_RECOMMENDATION", notifications.get(0).getType());
    }

    @Test
    void shouldSuppressAssessmentAndResourceNotificationsButStillCreateFollowUp() {
        AtomicReference<FollowUpPlan> savedPlan = new AtomicReference<>();
        List<NotificationDTO> notifications = new ArrayList<>();
        JadeAgentMessageCoordinator coordinator = coordinator(
                RepositoryProxyFactory.create(RiskAssessmentRepository.class, Map.of(
                        "save", args -> args[0]
                )),
                RepositoryProxyFactory.create(FollowUpPlanRepository.class, Map.of(
                        "findByPatientIdAndDiseaseTypeAndStatusInOrderByCreatedAtDesc", args -> List.of(),
                        "save", args -> {
                            savedPlan.set((FollowUpPlan) args[0]);
                            return args[0];
                        }
                )),
                RepositoryProxyFactory.create(ResourceRecommendationRepository.class, Map.of(
                        "save", args -> args[0]
                )),
                RepositoryProxyFactory.create(AgentMessageLogRepository.class, Map.of(
                        "save", args -> args[0]
                )),
                notifications
        );

        coordinator.handleAssessmentResult(AgentMessage.builder()
                .messageId("a-2")
                .patientId(1L)
                .patientName("患者甲")
                .diseaseType(DiseaseType.DIABETES)
                .riskLevel(RiskLevel.HIGH)
                .detail("评估完成")
                .recommendations(List.of("规律复查"))
                .data(Map.of("_silentPatientNotification", true))
                .build());

        assertNotNull(savedPlan.get());
        assertEquals(1, notifications.size());
        assertEquals("FOLLOW_UP_REMINDER", notifications.get(0).getType());
    }

    private JadeAgentMessageCoordinator coordinator(RiskAssessmentRepository assessmentRepository,
                                                    FollowUpPlanRepository followUpPlanRepository,
                                                    ResourceRecommendationRepository recommendationRepository,
                                                    AgentMessageLogRepository messageLogRepository,
                                                    List<NotificationDTO> notifications) {
        KieContainer kieContainer = RepositoryProxyFactory.create(KieContainer.class, Map.of());
        return new JadeAgentMessageCoordinator(
                new AssessmentResultPersistenceService(
                        assessmentRepository,
                        RepositoryProxyFactory.create(PatientRepository.class, Map.of(
                                "findById", args -> Optional.of(Patient.builder().id(1L).name("患者甲").build())
                        )),
                        new AiChatCacheInvalidationService(new NoOpCacheManager(), new AiChatCacheVersionService())
                ),
                new AssessmentFollowUpWorkflowService(
                        followUpPlanRepository,
                        recommendationRepository,
                        new RecordingNotificationGateway(notifications),
                        new AiChatCacheInvalidationService(new NoOpCacheManager(), new AiChatCacheVersionService())
                ),
                new JadeAssessmentWorkflowService(kieContainer),
                new AgentMessageAuditService(messageLogRepository),
                new RecordingNotificationGateway(notifications)
        );
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
