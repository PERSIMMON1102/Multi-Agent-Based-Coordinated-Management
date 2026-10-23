package com.medical.agent.service;

import com.medical.agent.common.message.AgentMessage;
import com.medical.agent.common.dto.NotificationDTO;
import com.medical.agent.common.enums.DiseaseType;
import com.medical.agent.common.enums.RiskLevel;
import com.medical.agent.model.entity.Patient;
import com.medical.agent.model.entity.RiskAssessment;
import com.medical.agent.model.repository.PatientRepository;
import com.medical.agent.model.repository.RiskAssessmentRepository;
import com.medical.agent.service.cache.AiChatCacheInvalidationService;
import com.medical.agent.service.cache.AiChatCacheVersionService;
import com.medical.agent.service.chat.RuleAssessmentResult;
import com.medical.agent.service.testsupport.RepositoryProxyFactory;
import org.junit.jupiter.api.Test;
import org.springframework.cache.support.NoOpCacheManager;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AssessmentResultPersistenceServiceTest {

    @Test
    void shouldPersistReactAssessmentWithUnifiedMetadata() {
        AtomicReference<RiskAssessment> saved = new AtomicReference<>();
        AssessmentResultPersistenceService service = service(saved);

        RiskAssessment assessment = service.saveReactAssessment(
                9L,
                new RuleAssessmentResult(
                        DiseaseType.DIABETES,
                        RiskLevel.HIGH,
                        88.0,
                        "评估完成",
                        List.of("规律复查"),
                        "react"
                ),
                "ReActAssessmentOrchestrator",
                "session-1",
                "task-1",
                "supervisor"
        );

        assertNotNull(assessment);
        assertEquals("react", saved.get().getAssessmentProvider());
        assertEquals("session-1", saved.get().getOrchestrationSessionId());
        assertEquals("task-1", saved.get().getOrchestrationTaskId());
        assertEquals("supervisor", saved.get().getOrchestrationAssignedAgent());
    }

    @Test
    void shouldBuildHistoryAwareNotificationForJadeOrReactPayload() {
        AssessmentResultPersistenceService service = service(new AtomicReference<>());

        NotificationDTO notification = service.buildAssessmentNotification(
                9L,
                "患者甲",
                DiseaseType.HYPERTENSION,
                RiskLevel.HIGH,
                "评估完成\n病史提示：既往高血压病史；药物过敏史：青霉素",
                List.of("规律复查"),
                AgentMessage.builder().messageId("msg-1").build()
        );

        assertEquals("ASSESSMENT_RESULT", notification.getType());
        assertEquals(List.of("既往高血压病史", "药物过敏史：青霉素"), notification.getHistoryAlerts());
        assertTrue(notification.getMessage().contains("病史提醒"));
    }

    private AssessmentResultPersistenceService service(AtomicReference<RiskAssessment> saved) {
        RiskAssessmentRepository assessmentRepository = RepositoryProxyFactory.create(RiskAssessmentRepository.class, Map.of(
                "save", args -> {
                    RiskAssessment assessment = (RiskAssessment) args[0];
                    saved.set(assessment);
                    return assessment;
                }
        ));
        PatientRepository patientRepository = RepositoryProxyFactory.create(PatientRepository.class, Map.of(
                "findById", args -> Optional.of(Patient.builder().id(9L).name("患者甲").build())
        ));
        return new AssessmentResultPersistenceService(
                assessmentRepository,
                patientRepository,
                new AiChatCacheInvalidationService(new NoOpCacheManager(), new AiChatCacheVersionService())
        );
    }
}
