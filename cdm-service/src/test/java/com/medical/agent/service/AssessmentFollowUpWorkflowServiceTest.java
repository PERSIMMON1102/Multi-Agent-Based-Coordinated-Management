package com.medical.agent.service;

import com.medical.agent.common.dto.NotificationDTO;
import com.medical.agent.common.enums.DiseaseType;
import com.medical.agent.common.enums.FollowUpFrequency;
import com.medical.agent.common.enums.FollowUpStatus;
import com.medical.agent.common.enums.RiskLevel;
import com.medical.agent.model.entity.FollowUpPlan;
import com.medical.agent.model.entity.Patient;
import com.medical.agent.model.entity.ResourceRecommendation;
import com.medical.agent.model.repository.FollowUpPlanRepository;
import com.medical.agent.model.repository.ResourceRecommendationRepository;
import com.medical.agent.service.cache.AiChatCacheInvalidationService;
import com.medical.agent.service.cache.AiChatCacheVersionService;
import com.medical.agent.service.chat.RuleAssessmentResult;
import com.medical.agent.service.support.AssessmentWorkflowPayload;
import com.medical.agent.service.testsupport.RepositoryProxyFactory;
import org.junit.jupiter.api.Test;
import org.springframework.cache.support.NoOpCacheManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AssessmentFollowUpWorkflowServiceTest {

    @Test
    void shouldCreateJadeFollowUpAndPushNotificationOnlyForNewPlan() {
        AtomicReference<FollowUpPlan> savedPlan = new AtomicReference<>();
        List<NotificationDTO> notifications = new ArrayList<>();
        AssessmentFollowUpWorkflowService service = service(
                RepositoryProxyFactory.create(FollowUpPlanRepository.class, Map.of(
                        "findByPatientIdAndDiseaseTypeAndStatusInOrderByCreatedAtDesc", args -> List.of(),
                        "save", args -> {
                            FollowUpPlan plan = (FollowUpPlan) args[0];
                            savedPlan.set(plan);
                            return plan;
                        }
                )),
                RepositoryProxyFactory.create(ResourceRecommendationRepository.class, Map.of()),
                notifications
        );

        FollowUpPlan plan = service.applyBridgeFollowUpMessage(
                new AssessmentWorkflowPayload(
                        "f-1",
                        1L,
                        "患者甲",
                        DiseaseType.HYPERTENSION,
                        null,
                        null,
                        null,
                        List.of(),
                        Map.of(
                                "frequency", FollowUpFrequency.MONTHLY.name(),
                                "checkItems", List.of("测量血压"),
                                "planContent", "按时复查"
                        )
                ),
                "FollowUpAgent",
                false
        );

        assertNotNull(plan);
        assertEquals(FollowUpFrequency.MONTHLY, savedPlan.get().getFrequency());
        assertEquals(1, notifications.size());
        assertEquals("FOLLOW_UP_REMINDER", notifications.get(0).getType());
    }

    @Test
    void shouldCreateReactResourceRecommendationAndPushJadeNotification() {
        AtomicReference<ResourceRecommendation> savedRecommendation = new AtomicReference<>();
        List<NotificationDTO> notifications = new ArrayList<>();
        AssessmentFollowUpWorkflowService service = service(
                RepositoryProxyFactory.create(FollowUpPlanRepository.class, Map.of()),
                RepositoryProxyFactory.create(ResourceRecommendationRepository.class, Map.of(
                        "save", args -> {
                            ResourceRecommendation recommendation = (ResourceRecommendation) args[0];
                            savedRecommendation.set(recommendation);
                            return recommendation;
                        }
                )),
                notifications
        );

        ResourceRecommendation reactRecommendation = service.createReactResourceRecommendation(
                Patient.builder().id(1L).name("患者甲").build(),
                null,
                new RuleAssessmentResult(
                        DiseaseType.CHD,
                        RiskLevel.HIGH,
                        90.0,
                        "需尽快转诊",
                        List.of(),
                        "react"
                ),
                "ReActResourceOrchestrator"
        );
        ResourceRecommendation jadeRecommendation = service.applyBridgeResourceRecommendation(
                new AssessmentWorkflowPayload(
                        "r-1",
                        1L,
                        "患者甲",
                        null,
                        null,
                        null,
                        "建议尽快转诊心内科",
                        List.of(),
                        Map.of()
                ),
                "ResourceCoordinatorAgent"
        );

        assertNotNull(reactRecommendation);
        assertEquals("ReActResourceOrchestrator", reactRecommendation.getAgentId());
        assertNotNull(jadeRecommendation);
        assertEquals("ResourceCoordinatorAgent", savedRecommendation.get().getAgentId());
        assertEquals(1, notifications.size());
        assertTrue(notifications.get(0).getMessage().contains("建议尽快转诊心内科"));
    }

    @Test
    void shouldCreateAssessmentDrivenFollowUpPlan() {
        AtomicReference<FollowUpPlan> savedPlan = new AtomicReference<>();
        List<NotificationDTO> notifications = new ArrayList<>();
        AssessmentFollowUpWorkflowService service = service(
                RepositoryProxyFactory.create(FollowUpPlanRepository.class, Map.of(
                        "findByPatientIdAndDiseaseTypeAndStatusInOrderByCreatedAtDesc", args -> List.of(),
                        "save", args -> {
                            FollowUpPlan plan = (FollowUpPlan) args[0];
                            savedPlan.set(plan);
                            return plan;
                        }
                )),
                RepositoryProxyFactory.create(ResourceRecommendationRepository.class, Map.of()),
                notifications
        );

        FollowUpPlan plan = service.upsertAssessmentDrivenFollowUp(
                new AssessmentWorkflowPayload(
                        "a-1",
                        1L,
                        "患者甲",
                        DiseaseType.DIABETES,
                        RiskLevel.MEDIUM,
                        null,
                        "需要继续关注血糖波动",
                        List.of(),
                        Map.of()
                ),
                "AssessmentAgent"
        );

        assertNotNull(plan);
        assertEquals(FollowUpFrequency.MONTHLY, savedPlan.get().getFrequency());
        assertEquals("AssessmentAgent", savedPlan.get().getAgentId());
        assertEquals(1, notifications.size());
        assertEquals("FOLLOW_UP_REMINDER", notifications.get(0).getType());
    }

    @Test
    void shouldCreateAssessmentDrivenResourceRecommendationAndNotify() {
        AtomicReference<ResourceRecommendation> savedRecommendation = new AtomicReference<>();
        List<NotificationDTO> notifications = new ArrayList<>();
        AssessmentFollowUpWorkflowService service = service(
                RepositoryProxyFactory.create(FollowUpPlanRepository.class, Map.of()),
                RepositoryProxyFactory.create(ResourceRecommendationRepository.class, Map.of(
                        "save", args -> {
                            ResourceRecommendation recommendation = (ResourceRecommendation) args[0];
                            savedRecommendation.set(recommendation);
                            return recommendation;
                        }
                )),
                notifications
        );

        ResourceRecommendation recommendation = service.createAssessmentDrivenResourceRecommendation(
                new AssessmentWorkflowPayload(
                        "a-2",
                        1L,
                        "患者甲",
                        null,
                        null,
                        null,
                        "建议尽快转诊心内科",
                        List.of(),
                        Map.of()
                ),
                "AssessmentAgent"
        );

        assertNotNull(recommendation);
        assertEquals("AssessmentAgent", savedRecommendation.get().getAgentId());
        assertEquals(1, notifications.size());
        assertEquals("RESOURCE_RECOMMENDATION", notifications.get(0).getType());
    }

    private AssessmentFollowUpWorkflowService service(FollowUpPlanRepository followUpPlanRepository,
                                                      ResourceRecommendationRepository recommendationRepository,
                                                      List<NotificationDTO> notifications) {
        return new AssessmentFollowUpWorkflowService(
                followUpPlanRepository,
                recommendationRepository,
                new RecordingNotificationGateway(notifications),
                new AiChatCacheInvalidationService(new NoOpCacheManager(), new AiChatCacheVersionService())
        );
    }

    private record RecordingNotificationGateway(List<NotificationDTO> notifications)
            implements ClinicalNotificationGateway {
        @Override
        public void setNotificationConsumer(java.util.function.Consumer<NotificationDTO> consumer) {
        }

        @Override
        public void sendNotification(NotificationDTO notification) {
            notifications.add(notification);
        }
    }
}
