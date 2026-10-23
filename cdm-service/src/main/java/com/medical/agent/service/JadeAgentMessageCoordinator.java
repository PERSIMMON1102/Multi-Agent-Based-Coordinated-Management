package com.medical.agent.service;

import com.medical.agent.common.message.AgentMessage;
import com.medical.agent.common.enums.RiskLevel;
import com.medical.agent.service.support.AssessmentWorkflowPayload;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@ConditionalOnProperty(name = "jade.enabled", havingValue = "true")
public class JadeAgentMessageCoordinator {

    private static final Logger log = LoggerFactory.getLogger(JadeAgentMessageCoordinator.class);

    private final AssessmentResultPersistenceService assessmentResultPersistenceService;
    private final AssessmentFollowUpWorkflowService assessmentFollowUpWorkflowService;
    private final JadeAssessmentWorkflowService jadeAssessmentWorkflowService;
    private final AgentMessageAuditService agentMessageAuditService;
    private final ClinicalNotificationGateway clinicalNotificationGateway;

    public JadeAgentMessageCoordinator(AssessmentResultPersistenceService assessmentResultPersistenceService,
                                       AssessmentFollowUpWorkflowService assessmentFollowUpWorkflowService,
                                       JadeAssessmentWorkflowService jadeAssessmentWorkflowService,
                                       AgentMessageAuditService agentMessageAuditService,
                                       ClinicalNotificationGateway clinicalNotificationGateway) {
        this.assessmentResultPersistenceService = assessmentResultPersistenceService;
        this.assessmentFollowUpWorkflowService = assessmentFollowUpWorkflowService;
        this.jadeAssessmentWorkflowService = jadeAssessmentWorkflowService;
        this.agentMessageAuditService = agentMessageAuditService;
        this.clinicalNotificationGateway = clinicalNotificationGateway;
    }

    public void handleAssessmentResult(AgentMessage result) {
        AssessmentWorkflowPayload payload = toWorkflowPayload(result);
        log.info("Assessment result received: patient={}, disease={}, risk={}",
                payload.patientId(), payload.diseaseType(), payload.riskLevel());
        jadeAssessmentWorkflowService.applyAssessmentResult(
                result,
                assessmentResultPersistenceService,
                clinicalNotificationGateway
        );
        handleAssessmentDrivenFollowUp(payload);
        handleAssessmentDrivenResourceRecommendation(payload);
        logAgentMessage("AssessmentAgent", "Spring", "INFORM", result);
    }

    public void handleFollowUpCreated(AgentMessage followUpMsg) {
        log.info("Follow-up plan created for patient {}", followUpMsg.getPatientId());

        if (jadeAssessmentWorkflowService.shouldSuppressFollowUpCreation(followUpMsg.getData())) {
            log.info("Skip auto follow-up creation for patient {} due to silent reassessment context",
                    followUpMsg.getPatientId());
            logAgentMessage("FollowUpAgent", "Spring", "SUPPRESS", followUpMsg);
            return;
        }

        boolean existed = assessmentFollowUpWorkflowService.hasActiveFollowUpPlan(
                followUpMsg.getPatientId(),
                followUpMsg.getDiseaseType()
        );
        var plan = assessmentFollowUpWorkflowService.applyBridgeFollowUpMessage(
                toWorkflowPayload(followUpMsg),
                "FollowUpAgent",
                false
        );
        if (plan != null && existed) {
            log.info("Existing active follow-up plan {} refreshed for patient {}",
                    plan.getId(), followUpMsg.getPatientId());
        } else if (plan != null) {
            log.info("New follow-up plan {} created for patient {}",
                    plan.getId(), followUpMsg.getPatientId());
        }

        logAgentMessage("FollowUpAgent", "Spring", "INFORM", followUpMsg);
    }

    public void handleResourceRecommended(AgentMessage resourceMsg) {
        log.info("Resource recommendation for patient {}", resourceMsg.getPatientId());
        assessmentFollowUpWorkflowService.applyBridgeResourceRecommendation(
                toWorkflowPayload(resourceMsg),
                "ResourceCoordinatorAgent"
        );
        logAgentMessage("ResourceCoordinatorAgent", "Spring", "INFORM", resourceMsg);
    }

    public void logOutboundRequest(String receiver, AgentMessage content) {
        logAgentMessage("Spring", receiver, "REQUEST", content);
    }

    private void handleAssessmentDrivenFollowUp(AssessmentWorkflowPayload payload) {
        if (jadeAssessmentWorkflowService.shouldSuppressFollowUpCreation(payload.data())) {
            log.info("Skip auto follow-up creation for patient {} due to silent reassessment context",
                    payload.patientId());
            return;
        }

        boolean existed = assessmentFollowUpWorkflowService.hasActiveFollowUpPlan(
                payload.patientId(),
                payload.diseaseType()
        );
        var plan = assessmentFollowUpWorkflowService.upsertAssessmentDrivenFollowUp(payload, "AssessmentAgent");
        if (plan != null && existed) {
            log.info("Existing active follow-up plan {} refreshed for patient {}",
                    plan.getId(), payload.patientId());
        } else if (plan != null) {
            log.info("New assessment-driven follow-up plan {} created for patient {}",
                    plan.getId(), payload.patientId());
        }
    }

    private void handleAssessmentDrivenResourceRecommendation(AssessmentWorkflowPayload payload) {
        if (payload == null || payload.riskLevel() == null) {
            return;
        }
        if (jadeAssessmentWorkflowService.shouldSuppressPatientNotification(payload.data())) {
            log.info("Skip resource recommendation for patient {} due to silent patient notification context",
                    payload.patientId());
            return;
        }
        if (payload.riskLevel().getLevel() < RiskLevel.HIGH.getLevel()) {
            return;
        }
        assessmentFollowUpWorkflowService.createAssessmentDrivenResourceRecommendation(payload, "AssessmentAgent");
        log.info("Assessment-driven resource recommendation created for patient {}", payload.patientId());
    }

    private void logAgentMessage(String sender, String receiver, String performative, AgentMessage content) {
        agentMessageAuditService.log(
                sender,
                receiver,
                performative,
                content == null ? null : content.getMessageType() + ": " + content.getDetail(),
                buildMetadata(content)
        );
    }

    private Map<String, Object> buildMetadata(AgentMessage content) {
        if (content == null) {
            return Map.of();
        }
        return Map.of(
                "patientId", content.getPatientId() != null ? content.getPatientId() : 0L,
                "diseaseType", content.getDiseaseType() != null ? content.getDiseaseType().name() : "UNKNOWN",
                "riskLevel", content.getRiskLevel() != null ? content.getRiskLevel().name() : "UNKNOWN"
        );
    }

    private AssessmentWorkflowPayload toWorkflowPayload(AgentMessage message) {
        if (message == null) {
            return null;
        }
        return new AssessmentWorkflowPayload(
                message.getMessageId(),
                message.getPatientId(),
                message.getPatientName(),
                message.getDiseaseType(),
                message.getRiskLevel(),
                message.getScore(),
                message.getDetail(),
                message.getRecommendations(),
                message.getData()
        );
    }
}
