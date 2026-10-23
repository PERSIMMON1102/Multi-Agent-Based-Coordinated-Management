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
import com.medical.agent.service.testsupport.RepositoryProxyFactory;
import org.junit.jupiter.api.Test;
import org.kie.api.runtime.KieContainer;
import org.kie.api.runtime.KieSession;
import org.springframework.cache.support.NoOpCacheManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JadeAssessmentWorkflowServiceTest {

    @Test
    void shouldProduceFallbackAssessmentWithHistoryHints() {
        JadeAssessmentWorkflowService service = new JadeAssessmentWorkflowService(fallbackKieContainer());

        AgentMessage result = service.runFallbackAssessment(
                Patient.builder().id(1L).name("患者甲").build(),
                DiseaseType.DIABETES,
                Map.of(
                        "pastDiseases", "糖尿病",
                        "drugAllergies", "青霉素"
                )
        );

        assertNotNull(result);
        assertEquals(DiseaseType.DIABETES, result.getDiseaseType());
        assertEquals(RiskLevel.LOW, result.getRiskLevel());
        assertTrue(result.getDetail().contains("病史提示"));
    }

    @Test
    void shouldHonorSilentNotificationFlagWhenApplyingAssessmentResult() {
        AtomicReference<RiskAssessment> savedAssessment = new AtomicReference<>();
        List<NotificationDTO> notifications = new ArrayList<>();
        AssessmentResultPersistenceService persistenceService = new AssessmentResultPersistenceService(
                RepositoryProxyFactory.create(RiskAssessmentRepository.class, Map.of(
                        "save", args -> {
                            RiskAssessment assessment = (RiskAssessment) args[0];
                            savedAssessment.set(assessment);
                            return assessment;
                        }
                )),
                RepositoryProxyFactory.create(PatientRepository.class, Map.of(
                        "findById", args -> Optional.of(Patient.builder().id(1L).name("患者甲").build())
                )),
                new AiChatCacheInvalidationService(new NoOpCacheManager(), new AiChatCacheVersionService())
        );
        JadeAssessmentWorkflowService service = new JadeAssessmentWorkflowService(fallbackKieContainer());

        service.applyAssessmentResult(
                AgentMessage.builder()
                        .messageId("a-1")
                        .patientId(1L)
                        .patientName("患者甲")
                        .diseaseType(DiseaseType.HYPERTENSION)
                        .riskLevel(RiskLevel.HIGH)
                        .detail("评估完成")
                        .recommendations(List.of("规律复查"))
                        .data(Map.of("_silentPatientNotification", true))
                        .build(),
                persistenceService,
                new RecordingNotificationGateway(notifications)
        );

        assertNotNull(savedAssessment.get());
        assertEquals("jade", savedAssessment.get().getAssessmentProvider());
        assertTrue(notifications.isEmpty());
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
