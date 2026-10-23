package com.medical.agent.service.followup.agent;

import com.medical.agent.common.enums.FollowUpStatus;
import com.medical.agent.model.entity.FollowUpPlan;
import com.medical.agent.model.entity.Patient;
import com.medical.agent.model.entity.PatientMedicalHistory;
import com.medical.agent.model.repository.FollowUpPlanRepository;
import com.medical.agent.model.repository.PatientRepository;
import com.medical.agent.model.repository.PatientMedicalHistoryRepository;
import com.medical.agent.service.AgentMessageAuditService;
import com.medical.agent.service.support.PatientMedicalHistoryPromptSupport;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class SmartFollowUpSupervisorService {

    private static final String SUPERVISOR_NAME = "FollowUpSupervisor";
    private static final int MAX_SPECIALISTS = 4;

    private final PatientRepository patientRepository;
    private final PatientMedicalHistoryRepository patientMedicalHistoryRepository;
    private final FollowUpPlanRepository followUpPlanRepository;
    private final AgentMessageAuditService agentMessageAuditService;
    private final List<SmartFollowUpSpecialistAgent> specialistAgents;

    @Autowired
    public SmartFollowUpSupervisorService(PatientRepository patientRepository,
                                          PatientMedicalHistoryRepository patientMedicalHistoryRepository,
                                          FollowUpPlanRepository followUpPlanRepository,
                                          AgentMessageAuditService agentMessageAuditService,
                                          List<SmartFollowUpSpecialistAgent> specialistAgents) {
        this.patientRepository = patientRepository;
        this.patientMedicalHistoryRepository = patientMedicalHistoryRepository;
        this.followUpPlanRepository = followUpPlanRepository;
        this.agentMessageAuditService = agentMessageAuditService;
        this.specialistAgents = specialistAgents.stream()
                .sorted(java.util.Comparator.comparingInt(SmartFollowUpSpecialistAgent::order))
                .toList();
    }

    public SmartFollowUpSupervisorService(PatientRepository patientRepository,
                                          PatientMedicalHistoryRepository patientMedicalHistoryRepository,
                                          FollowUpPlanRepository followUpPlanRepository,
                                          List<SmartFollowUpSpecialistAgent> specialistAgents) {
        this(patientRepository,
                patientMedicalHistoryRepository,
                followUpPlanRepository,
                null,
                specialistAgents);
    }

    public Map<String, Object> generate(Long patientId) {
        Patient patient = patientRepository.findById(patientId)
                .orElseThrow(() -> new RuntimeException("患者不存在: " + patientId));
        PatientMedicalHistory medicalHistory = patientMedicalHistoryRepository.findByPatientId(patientId).orElse(null);
        List<String> historyAlerts = PatientMedicalHistoryPromptSupport.buildAlerts(medicalHistory);

        List<FollowUpPlan> pendingPlans = followUpPlanRepository.findByPatientIdOrderByCreatedAtDesc(patientId)
                .stream()
                .filter(plan -> plan.getStatus() == FollowUpStatus.PENDING)
                .collect(Collectors.toList());

        SmartFollowUpGenerationState state = new SmartFollowUpGenerationState();
        state.setPatientId(patientId);
        state.setPatient(patient);
        state.setMedicalHistory(medicalHistory);
        state.setMedicalHistorySummary(PatientMedicalHistoryPromptSupport.buildSummary(medicalHistory));
        state.setPendingPlans(pendingPlans);

        List<SmartFollowUpSpecialistAgent> plan = specialistAgents.stream()
                .limit(MAX_SPECIALISTS)
                .toList();
        for (SmartFollowUpSpecialistAgent specialistAgent : plan) {
            int stepIndex = state.getAgentSteps().size() + 1;
            logAgentMessage(
                    SUPERVISOR_NAME,
                    specialistAgent.name(),
                    "REQUEST",
                    "生成随访消息: " + patient.getName(),
                    buildMetadata(state, stepIndex)
            );
            try {
                specialistAgent.execute(state);
                logAgentMessage(
                        specialistAgent.name(),
                        SUPERVISOR_NAME,
                        "INFORM",
                        resolveStepSummary(state.getAgentSteps(), stepIndex - 1, "已完成随访分析"),
                        buildMetadata(state, stepIndex)
                );
            } catch (RuntimeException ex) {
                logAgentMessage(
                        specialistAgent.name(),
                        SUPERVISOR_NAME,
                        "FAILURE",
                        "执行失败: " + ex.getMessage(),
                        buildMetadata(state, stepIndex)
                );
                throw ex;
            }
        }
        String message = state.getMessage() == null ? "" : state.getMessage();

        return Map.of(
                "id", System.currentTimeMillis(),
                "patientId", patientId,
                "patientName", patient.getName(),
                "message", message,
                "channel", "系统通知",
                "status", "pending",
                "createdAt", java.time.LocalDate.now().toString(),
                "historyAlerts", historyAlerts,
                "agentSteps", state.getAgentSteps()
        );
    }

    private Map<String, Object> buildMetadata(SmartFollowUpGenerationState state, int stepIndex) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("flowType", "followup");
        metadata.put("patientId", state.getPatientId());
        metadata.put("patientName", state.getPatient().getName());
        metadata.put("stepIndex", stepIndex);
        metadata.put("pendingPlanCount", state.getPendingPlans().size());
        return metadata;
    }

    private String resolveStepSummary(List<Map<String, Object>> steps, int previousSize, String fallback) {
        if (steps == null || steps.size() <= previousSize) {
            return fallback;
        }
        Object summary = steps.get(steps.size() - 1).get("summary");
        if (summary == null || summary.toString().isBlank()) {
            return fallback;
        }
        return summary.toString();
    }

    private void logAgentMessage(String sender,
                                 String receiver,
                                 String performative,
                                 String content,
                                 Map<String, Object> metadata) {
        if (agentMessageAuditService != null) {
            agentMessageAuditService.log(sender, receiver, performative, content, metadata);
        }
    }
}
