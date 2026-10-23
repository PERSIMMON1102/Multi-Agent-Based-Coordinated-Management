package com.medical.agent.service;

import com.medical.agent.common.dto.NotificationDTO;
import com.medical.agent.common.enums.DiseaseType;
import com.medical.agent.common.enums.RiskLevel;
import com.medical.agent.model.entity.Patient;
import com.medical.agent.model.entity.RiskAssessment;
import com.medical.agent.model.repository.PatientRepository;
import com.medical.agent.model.repository.RiskAssessmentRepository;
import com.medical.agent.service.chat.RuleAssessmentResult;
import com.medical.agent.service.cache.AiChatCacheInvalidationService;
import com.medical.agent.service.support.AssessmentWorkflowPayload;
import com.medical.agent.service.support.AssessmentHistoryAlertSupport;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AssessmentResultPersistenceService {

    private final RiskAssessmentRepository riskAssessmentRepository;
    private final PatientRepository patientRepository;
    private final AiChatCacheInvalidationService aiChatCacheInvalidationService;

    public AssessmentResultPersistenceService(RiskAssessmentRepository riskAssessmentRepository,
                                              PatientRepository patientRepository,
                                              AiChatCacheInvalidationService aiChatCacheInvalidationService) {
        this.riskAssessmentRepository = riskAssessmentRepository;
        this.patientRepository = patientRepository;
        this.aiChatCacheInvalidationService = aiChatCacheInvalidationService;
    }

    public RiskAssessment saveReactAssessment(Long patientId,
                                              RuleAssessmentResult result,
                                              String agentId,
                                              String sessionId,
                                              String taskId,
                                              String assignedAgent) {
        RiskAssessment assessment = RiskAssessment.builder()
                .patientId(patientId)
                .diseaseType(result.diseaseType())
                .riskLevel(result.riskLevel())
                .score(result.score())
                .assessmentDetail(result.detail())
                .recommendations(joinRecommendations(result.recommendations()))
                .agentId(agentId)
                .assessmentProvider("react")
                .orchestrationSessionId(sessionId)
                .orchestrationTaskId(taskId)
                .orchestrationAssignedAgent(assignedAgent)
                .build();
        RiskAssessment saved = riskAssessmentRepository.save(assessment);
        aiChatCacheInvalidationService.invalidatePatient(patientId);
        return saved;
    }

    public RiskAssessment saveBridgeAssessment(AssessmentWorkflowPayload result,
                                               String provider,
                                               String agentId) {
        RiskAssessment assessment = RiskAssessment.builder()
                .patientId(result.patientId())
                .diseaseType(result.diseaseType())
                .riskLevel(result.riskLevel())
                .score(result.score())
                .assessmentDetail(result.detail())
                .recommendations(joinRecommendations(result.recommendations()))
                .agentId(agentId)
                .assessmentProvider(provider)
                .orchestrationSessionId(result.messageId())
                .orchestrationAssignedAgent(agentId)
                .build();
        RiskAssessment saved = riskAssessmentRepository.save(assessment);
        aiChatCacheInvalidationService.invalidatePatient(result.patientId());
        return saved;
    }

    public NotificationDTO buildAssessmentNotification(Long patientId,
                                                       String patientName,
                                                       DiseaseType diseaseType,
                                                       RiskLevel riskLevel,
                                                       String detail,
                                                       List<String> recommendations,
                                                       Object payload) {
        String resolvedPatientName = normalizePatientName(patientId, patientName);
        List<String> historyAlerts = AssessmentHistoryAlertSupport.extractHistoryAlerts(detail, recommendations);
        String notificationMessage = AssessmentHistoryAlertSupport.appendHistoryAlertsToNotificationMessage(
                String.format("患者%s的%s评估结果：%s",
                        resolvedPatientName,
                        diseaseType.getDisplayName(),
                        riskLevel.getDisplayName()),
                historyAlerts
        );
        return NotificationDTO.builder()
                .type("ASSESSMENT_RESULT")
                .patientId(patientId)
                .title(diseaseType.getDisplayName() + "风险评估完成")
                .message(notificationMessage)
                .historyAlerts(historyAlerts)
                .payload(payload)
                .timestamp(System.currentTimeMillis())
                .build();
    }

    private String normalizePatientName(Long patientId, String patientName) {
        if (patientName != null && !patientName.isBlank()) {
            return patientName;
        }
        return patientRepository.findById(patientId)
                .map(Patient::getName)
                .filter(name -> name != null && !name.isBlank())
                .orElse("未知患者");
    }

    private String joinRecommendations(List<String> recommendations) {
        return recommendations == null ? "" : String.join("; ", recommendations);
    }
}
