package com.medical.agent.service;

import com.medical.agent.common.bridge.JadeAssessmentAgentRuntime;
import com.medical.agent.common.enums.DiseaseType;
import com.medical.agent.common.message.AgentMessage;
import com.medical.agent.model.entity.Patient;
import jakarta.annotation.PostConstruct;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.Map;

/**
 * Spring与JADE智能体的桥接服务
 * 负责启动智能体并发送评估请求，回调分发逻辑交给独立协调器处理。
 */
@Service
@ConditionalOnProperty(name = "jade.enabled", havingValue = "true")
public class AgentBridgeService implements JadeAssessmentTriggerGateway {

    private static final Logger log = LoggerFactory.getLogger(AgentBridgeService.class);

    private final JadeAssessmentAgentLifecycleService jadeAssessmentAgentLifecycleService;
    private final JadeAssessmentRequestFactory jadeAssessmentRequestFactory;
    private final JadeAssessmentWorkflowService jadeAssessmentWorkflowService;
    private final JadeAgentMessageCoordinator jadeAgentMessageCoordinator;

    public AgentBridgeService(JadeAssessmentAgentLifecycleService jadeAssessmentAgentLifecycleService,
                              JadeAssessmentRequestFactory jadeAssessmentRequestFactory,
                              JadeAssessmentWorkflowService jadeAssessmentWorkflowService,
                              JadeAgentMessageCoordinator jadeAgentMessageCoordinator) {
        this.jadeAssessmentAgentLifecycleService = jadeAssessmentAgentLifecycleService;
        this.jadeAssessmentRequestFactory = jadeAssessmentRequestFactory;
        this.jadeAssessmentWorkflowService = jadeAssessmentWorkflowService;
        this.jadeAgentMessageCoordinator = jadeAgentMessageCoordinator;
    }

    @PostConstruct
    public void warmupAgentsIfConfigured() {
        jadeAssessmentAgentLifecycleService.warmupIfConfigured(this::onAssessmentResult);
    }

    public synchronized void initAgents() {
        jadeAssessmentAgentLifecycleService.ensureAssessmentAgentReady(this::onAssessmentResult);
    }

    @Override
    public void triggerAssessment(Long patientId, DiseaseType diseaseType, Map<String, Object> healthData) {
        triggerAssessment(patientId, diseaseType, healthData, Collections.emptyMap());
    }

    @Override
    public void triggerAssessment(Long patientId,
                                  DiseaseType diseaseType,
                                  Map<String, Object> healthData,
                                  Map<String, Object> assessmentOptions) {
        JadeAssessmentRequestFactory.PreparedJadeAssessmentRequest prepared =
                jadeAssessmentRequestFactory.prepare(patientId, diseaseType, healthData, assessmentOptions);
        Patient patient = prepared.patient();
        AgentMessage request = prepared.request();

        try {
            jadeAssessmentAgentLifecycleService.dispatchAssessmentRequest(this::onAssessmentResult, request);
            jadeAgentMessageCoordinator.logOutboundRequest("AssessmentAgent", request);
            log.info("Assessment request sent for patient {} (disease: {})", patientId,
                    diseaseType != null ? diseaseType.getCode() : "AUTO");
        } catch (Exception e) {
            log.error("Failed to trigger assessment for patient {}", patientId, e);
            fallbackAssessment(patient, diseaseType, request.getData());
        }
    }

    private void fallbackAssessment(Patient patient, DiseaseType diseaseType, Map<String, Object> healthData) {
        log.warn("Using fallback assessment for patient {}", patient.getId());
        AgentMessage result = jadeAssessmentWorkflowService.runFallbackAssessment(
                patient,
                diseaseType,
                healthData
        );
        onAssessmentResult(result);
    }

    private void onAssessmentResult(AgentMessage result) {
        jadeAgentMessageCoordinator.handleAssessmentResult(result);
    }
}
