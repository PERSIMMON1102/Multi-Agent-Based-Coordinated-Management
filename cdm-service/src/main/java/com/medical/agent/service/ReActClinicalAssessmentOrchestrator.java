package com.medical.agent.service;

import com.medical.agent.common.dto.NotificationDTO;
import com.medical.agent.common.enums.DiseaseType;
import com.medical.agent.common.enums.FollowUpFrequency;
import com.medical.agent.common.enums.FollowUpStatus;
import com.medical.agent.common.enums.RiskLevel;
import com.medical.agent.model.entity.FollowUpPlan;
import com.medical.agent.model.entity.Patient;
import com.medical.agent.model.entity.ResourceRecommendation;
import com.medical.agent.model.entity.RiskAssessment;
import com.medical.agent.model.repository.PatientRepository;
import com.medical.agent.service.cache.AiChatCacheInvalidationService;
import com.medical.agent.service.chat.RuleAssessmentResult;
import com.medical.agent.service.chat.agent.ChatSupervisorService;
import com.medical.agent.service.react.model.AgentState;
import com.medical.agent.service.react.model.ReActResult;
import com.medical.agent.service.react.orchestration.OrchestrationTrace;
import com.medical.agent.service.react.tool.RiskAssessmentTool;
import com.medical.agent.service.react.tool.ToolExecutionResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Component
public class ReActClinicalAssessmentOrchestrator implements ClinicalAssessmentOrchestrator {

    private final ChatSupervisorService chatSupervisorService;
    private final RiskAssessmentTool riskAssessmentTool;
    private final PatientRepository patientRepository;
    private final AiChatCacheInvalidationService aiChatCacheInvalidationService;
    private final ClinicalNotificationGateway clinicalNotificationGateway;
    private final AssessmentResultPersistenceService assessmentResultPersistenceService;
    private final AssessmentFollowUpWorkflowService assessmentFollowUpWorkflowService;

    @Value("${clinical.assessment.react.notifications-enabled:true}")
    private boolean notificationsEnabled = true;

    @Autowired
    public ReActClinicalAssessmentOrchestrator(ChatSupervisorService chatSupervisorService,
                                               RiskAssessmentTool riskAssessmentTool,
                                               PatientRepository patientRepository,
                                               AiChatCacheInvalidationService aiChatCacheInvalidationService,
                                               ClinicalNotificationGateway clinicalNotificationGateway,
                                               AssessmentResultPersistenceService assessmentResultPersistenceService,
                                               AssessmentFollowUpWorkflowService assessmentFollowUpWorkflowService) {
        this.chatSupervisorService = chatSupervisorService;
        this.riskAssessmentTool = riskAssessmentTool;
        this.patientRepository = patientRepository;
        this.aiChatCacheInvalidationService = aiChatCacheInvalidationService;
        this.clinicalNotificationGateway = clinicalNotificationGateway;
        this.assessmentResultPersistenceService = assessmentResultPersistenceService;
        this.assessmentFollowUpWorkflowService = assessmentFollowUpWorkflowService;
    }

    ReActClinicalAssessmentOrchestrator(RiskAssessmentTool riskAssessmentTool,
                                        PatientRepository patientRepository,
                                        AiChatCacheInvalidationService aiChatCacheInvalidationService,
                                        ClinicalNotificationGateway clinicalNotificationGateway,
                                        AssessmentResultPersistenceService assessmentResultPersistenceService,
                                        AssessmentFollowUpWorkflowService assessmentFollowUpWorkflowService) {
        this(null,
                riskAssessmentTool,
                patientRepository,
                aiChatCacheInvalidationService,
                clinicalNotificationGateway,
                assessmentResultPersistenceService,
                assessmentFollowUpWorkflowService);
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
        Patient patient = patientRepository.findById(patientId)
                .orElseThrow(() -> new RuntimeException("Patient not found: " + patientId));

        AgentState state = new AgentState();
        state.setSessionId("assessment-" + patientId + "-" + UUID.randomUUID());
        state.setOwnerRole("system");
        state.setPatientId(patientId);
        state.setConversationPerspective("【当前调用角色】系统风险评估编排。请仅围绕患者风险评估给出结构化 observation。");
        state.setUserMessage(buildAssessmentPrompt(patient, diseaseType, healthData));

        Map<String, Object> requestData = new HashMap<>();
        if (healthData != null) {
            requestData.putAll(healthData);
        }
        if (diseaseType != null) {
            requestData.put("diseaseType", diseaseType.name());
        }
        state.setRequestData(requestData);

        ReActResult orchestrationResult = chatSupervisorService == null ? null : chatSupervisorService.run(state);
        if (state.getAssessmentResults().isEmpty()) {
            ToolExecutionResult executionResult = riskAssessmentTool.execute(state, requestData);
            if (!executionResult.success() || state.getAssessmentResults().isEmpty()) {
                throw new RuntimeException("ReAct risk assessment pipeline returned no usable assessment results");
            }
        }
        if (state.getAssessmentResults().isEmpty()) {
            throw new RuntimeException("ReAct risk assessment pipeline returned no usable assessment results");
        }

        boolean suppressNotification = isMetaFlagEnabled(assessmentOptions, "_silentPatientNotification");
        boolean suppressFollowUpCreation = isMetaFlagEnabled(assessmentOptions, "_suppressFollowUpCreation");
        OrchestrationTrace orchestrationTrace = orchestrationResult == null ? null : orchestrationResult.orchestrationTrace();
        for (RuleAssessmentResult result : state.getAssessmentResults()) {
            RiskAssessment assessment = persistAssessment(patient, result, state, orchestrationTrace);
            if (!suppressFollowUpCreation) {
                assessmentFollowUpWorkflowService.upsertReactFollowUpPlan(
                        patient,
                        result,
                        "ReActFollowUpOrchestrator"
                );
            }
            if (result.riskLevel() != null && result.riskLevel().getLevel() >= RiskLevel.HIGH.getLevel()) {
                assessmentFollowUpWorkflowService.createReactResourceRecommendation(
                        patient,
                        assessment,
                        result,
                        "ReActResourceOrchestrator"
                );
            }
            if (notificationsEnabled && !suppressNotification) {
                pushAssessmentNotification(patient, result);
            }
        }
        if (orchestrationResult != null && orchestrationResult.orchestrationTrace() != null) {
            aiChatCacheInvalidationService.invalidatePatient(patientId);
            return;
        }
        aiChatCacheInvalidationService.invalidatePatient(patientId);
    }

    private String buildAssessmentPrompt(Patient patient, DiseaseType diseaseType, Map<String, Object> healthData) {
        StringBuilder prompt = new StringBuilder("请基于当前患者上下文执行风险评估。");
        if (diseaseType != null) {
            prompt.append("重点病种：").append(diseaseType.getDisplayName()).append("。");
        }
        if (patient.getName() != null && !patient.getName().isBlank()) {
            prompt.append("患者：").append(patient.getName()).append("。");
        }
        if (healthData != null && !healthData.isEmpty()) {
            prompt.append("最新指标：").append(healthData);
        }
        return prompt.toString();
    }

    private RiskAssessment persistAssessment(Patient patient,
                                             RuleAssessmentResult result,
                                             AgentState state,
                                             OrchestrationTrace orchestrationTrace) {
        RiskAssessment assessment = RiskAssessment.builder()
                .build();
        return assessmentResultPersistenceService.saveReactAssessment(
                patient.getId(),
                result,
                "ReActAssessmentOrchestrator",
                state == null ? null : state.getSessionId(),
                orchestrationTrace == null ? null : orchestrationTrace.taskId(),
                orchestrationTrace == null ? null : orchestrationTrace.assignedAgent()
        );
    }

    private void pushAssessmentNotification(Patient patient, RuleAssessmentResult result) {
        clinicalNotificationGateway.sendNotification(
                assessmentResultPersistenceService.buildAssessmentNotification(
                        patient.getId(),
                        patient.getName(),
                        result.diseaseType(),
                        result.riskLevel(),
                        result.detail(),
                        result.recommendations(),
                        result
                )
        );
    }

    private boolean isMetaFlagEnabled(Map<String, Object> data, String key) {
        if (data == null || !data.containsKey(key)) {
            return false;
        }
        Object value = data.get(key);
        if (value instanceof Boolean boolValue) {
            return boolValue;
        }
        return "true".equalsIgnoreCase(String.valueOf(value));
    }
}
