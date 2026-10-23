package com.medical.agent.service.report.agent;

import com.medical.agent.common.exception.BusinessException;
import com.medical.agent.model.entity.FollowUpPlan;
import com.medical.agent.model.entity.HealthRecord;
import com.medical.agent.model.entity.Patient;
import com.medical.agent.model.entity.PatientMedicalHistory;
import com.medical.agent.model.entity.RiskAssessment;
import com.medical.agent.model.repository.FollowUpPlanRepository;
import com.medical.agent.model.repository.HealthRecordRepository;
import com.medical.agent.model.repository.PatientRepository;
import com.medical.agent.model.repository.PatientMedicalHistoryRepository;
import com.medical.agent.model.repository.RiskAssessmentRepository;
import com.medical.agent.service.AgentMessageAuditService;
import com.medical.agent.service.support.PatientMedicalHistoryPromptSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class HealthReportSupervisorService {

    private static final Logger log = LoggerFactory.getLogger(HealthReportSupervisorService.class);
    private static final String SUPERVISOR_NAME = "ReportSupervisor";
    private static final int MAX_SPECIALISTS = 5;

    private final PatientRepository patientRepository;
    private final PatientMedicalHistoryRepository patientMedicalHistoryRepository;
    private final RiskAssessmentRepository riskAssessmentRepository;
    private final FollowUpPlanRepository followUpPlanRepository;
    private final HealthRecordRepository healthRecordRepository;
    private final AgentMessageAuditService agentMessageAuditService;
    private final List<ReportSpecialistAgent> specialistAgents;

    @Autowired
    public HealthReportSupervisorService(PatientRepository patientRepository,
                                         PatientMedicalHistoryRepository patientMedicalHistoryRepository,
                                         RiskAssessmentRepository riskAssessmentRepository,
                                         FollowUpPlanRepository followUpPlanRepository,
                                         HealthRecordRepository healthRecordRepository,
                                         AgentMessageAuditService agentMessageAuditService,
                                         List<ReportSpecialistAgent> specialistAgents) {
        this.patientRepository = patientRepository;
        this.patientMedicalHistoryRepository = patientMedicalHistoryRepository;
        this.riskAssessmentRepository = riskAssessmentRepository;
        this.followUpPlanRepository = followUpPlanRepository;
        this.healthRecordRepository = healthRecordRepository;
        this.agentMessageAuditService = agentMessageAuditService;
        this.specialistAgents = specialistAgents.stream()
                .sorted(java.util.Comparator.comparingInt(ReportSpecialistAgent::order))
                .toList();
    }

    public HealthReportSupervisorService(PatientRepository patientRepository,
                                         PatientMedicalHistoryRepository patientMedicalHistoryRepository,
                                         RiskAssessmentRepository riskAssessmentRepository,
                                         FollowUpPlanRepository followUpPlanRepository,
                                         HealthRecordRepository healthRecordRepository,
                                         List<ReportSpecialistAgent> specialistAgents) {
        this(patientRepository,
                patientMedicalHistoryRepository,
                riskAssessmentRepository,
                followUpPlanRepository,
                healthRecordRepository,
                null,
                specialistAgents);
    }

    public ReportGenerationState generate(Long patientId, String type) {
        long startedAt = System.currentTimeMillis();
        ReportGenerationState state = initState(patientId, type);
        List<ReportSpecialistAgent> plan = specialistAgents.stream()
                .limit(MAX_SPECIALISTS)
                .toList();
        if (specialistAgents.size() > MAX_SPECIALISTS) {
            log.warn("[HealthReportSupervisorService] Specialist count {} exceeds limit {}, extra specialists are skipped",
                    specialistAgents.size(), MAX_SPECIALISTS);
        }
        for (ReportSpecialistAgent specialistAgent : plan) {
            int stepIndex = state.getAgentSteps().size() + 1;
            logAgentMessage(
                    SUPERVISOR_NAME,
                    specialistAgent.name(),
                    "REQUEST",
                    "生成健康报告片段: " + state.getType(),
                    buildMetadata(state, stepIndex)
            );
            try {
                specialistAgent.execute(state);
                logAgentMessage(
                        specialistAgent.name(),
                        SUPERVISOR_NAME,
                        "INFORM",
                        resolveStepSummary(state.getAgentSteps(), stepIndex - 1, "已完成报告片段生成"),
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
        log.info("[HealthReportSupervisorService] generated report draft for patientId={}, type={} in {} ms",
                patientId, type, System.currentTimeMillis() - startedAt);
        return state;
    }

    private ReportGenerationState initState(Long patientId, String type) {
        Patient patient = patientRepository.findById(patientId)
                .orElseThrow(() -> new BusinessException(404, "患者不存在"));
        PatientMedicalHistory medicalHistory = patientMedicalHistoryRepository.findByPatientId(patientId).orElse(null);
        List<RiskAssessment> assessments = riskAssessmentRepository.findByPatientIdOrderByCreatedAtDesc(patientId);
        List<FollowUpPlan> plans = followUpPlanRepository.findByPatientIdOrderByCreatedAtDesc(patientId);
        HealthRecord latestRecord = healthRecordRepository.findFirstByPatientIdOrderByRecordDateDesc(patientId).orElse(null);

        ReportGenerationState state = new ReportGenerationState();
        state.setPatientId(patientId);
        state.setType(type);
        state.setPatient(patient);
        state.setMedicalHistory(medicalHistory);
        state.setMedicalHistorySummary(PatientMedicalHistoryPromptSupport.buildSummary(medicalHistory));
        state.setAssessments(assessments);
        state.setPlans(plans);
        state.setLatestRecord(latestRecord);
        return state;
    }

    private Map<String, Object> buildMetadata(ReportGenerationState state, int stepIndex) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("flowType", "report");
        metadata.put("patientId", state.getPatientId());
        metadata.put("patientName", state.getPatient().getName());
        metadata.put("reportType", state.getType());
        metadata.put("stepIndex", stepIndex);
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
