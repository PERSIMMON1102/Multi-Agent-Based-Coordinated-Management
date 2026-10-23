package com.medical.agent.service.education.agent;

import com.medical.agent.common.dto.RagEvidenceDTO;
import com.medical.agent.model.entity.Patient;
import com.medical.agent.model.entity.PatientMedicalHistory;
import com.medical.agent.model.entity.RiskAssessment;
import com.medical.agent.model.repository.PatientRepository;
import com.medical.agent.model.repository.PatientMedicalHistoryRepository;
import com.medical.agent.model.repository.RiskAssessmentRepository;
import com.medical.agent.service.AgentMessageAuditService;
import com.medical.agent.service.ReferenceCurationService;
import com.medical.agent.service.support.PatientMedicalHistoryPromptSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.Period;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class HealthEducationSupervisorService {

    private static final Logger log = LoggerFactory.getLogger(HealthEducationSupervisorService.class);
    private static final String SUPERVISOR_NAME = "EducationSupervisor";
    private static final int MAX_SPECIALISTS = 6;

    private final PatientRepository patientRepository;
    private final PatientMedicalHistoryRepository patientMedicalHistoryRepository;
    private final RiskAssessmentRepository assessmentRepository;
    private final ReferenceCurationService referenceCurationService;
    private final AgentMessageAuditService agentMessageAuditService;
    private final List<EducationSpecialistAgent> specialistAgents;

    @Autowired
    public HealthEducationSupervisorService(PatientRepository patientRepository,
                                            PatientMedicalHistoryRepository patientMedicalHistoryRepository,
                                            RiskAssessmentRepository assessmentRepository,
                                            ReferenceCurationService referenceCurationService,
                                            AgentMessageAuditService agentMessageAuditService,
                                            List<EducationSpecialistAgent> specialistAgents) {
        this.patientRepository = patientRepository;
        this.patientMedicalHistoryRepository = patientMedicalHistoryRepository;
        this.assessmentRepository = assessmentRepository;
        this.referenceCurationService = referenceCurationService;
        this.agentMessageAuditService = agentMessageAuditService;
        this.specialistAgents = specialistAgents.stream()
                .sorted(java.util.Comparator.comparingInt(EducationSpecialistAgent::order))
                .toList();
    }

    public HealthEducationSupervisorService(PatientRepository patientRepository,
                                            PatientMedicalHistoryRepository patientMedicalHistoryRepository,
                                            RiskAssessmentRepository assessmentRepository,
                                            ReferenceCurationService referenceCurationService,
                                            List<EducationSpecialistAgent> specialistAgents) {
        this(patientRepository,
                patientMedicalHistoryRepository,
                assessmentRepository,
                referenceCurationService,
                null,
                specialistAgents);
    }

    public Map<String, Object> generate(Long patientId) {
        long startedAt = System.currentTimeMillis();
        EducationGenerationState state = initState(patientId);

        List<EducationSpecialistAgent> plan = specialistAgents.stream()
                .limit(MAX_SPECIALISTS)
                .toList();
        if (specialistAgents.size() > MAX_SPECIALISTS) {
            log.warn("[HealthEducationSupervisorService] Specialist count {} exceeds limit {}, extra specialists are skipped",
                    specialistAgents.size(), MAX_SPECIALISTS);
        }

        for (EducationSpecialistAgent specialistAgent : plan) {
            int stepIndex = state.getAgentSteps().size() + 1;
            logAgentMessage(
                    SUPERVISOR_NAME,
                    specialistAgent.name(),
                    "REQUEST",
                    "生成患者宣教内容: " + state.getPatient().getName(),
                    buildMetadata(state, stepIndex)
            );
            try {
                specialistAgent.execute(state);
                logAgentMessage(
                        specialistAgent.name(),
                        SUPERVISOR_NAME,
                        "INFORM",
                        resolveStepSummary(state.getAgentSteps(), stepIndex - 1, "已完成宣教片段生成"),
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

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("planId", java.util.UUID.randomUUID().toString());
        result.put("patientId", state.getPatientId());
        result.put("patientName", state.getPatient().getName());
        result.put("generatedAt", state.getGeneratedAt());
        result.put("diseases", new ArrayList<>(state.getDiseaseNames()));
        result.put("diet", state.getDiet());
        result.put("exercise", state.getExercise());
        result.put("lifestyle", state.getLifestyle());
        result.put("dietReferences", state.getDietDisplayReferences());
        result.put("exerciseReferences", state.getExerciseDisplayReferences());
        result.put("lifestyleReferences", state.getLifestyleDisplayReferences());
        result.put("references", referenceCurationService.mergeDistinct(
                4,
                state.getDietDisplayReferences(),
                state.getExerciseDisplayReferences(),
                state.getLifestyleDisplayReferences()
        ));
        result.put("recommendedVideos", state.getRecommendedVideos());
        result.put("agentSteps", state.getAgentSteps());

        log.info("[HealthEducationSupervisorService] generated education for patientId={} in {} ms",
                patientId, System.currentTimeMillis() - startedAt);
        return result;
    }

    private EducationGenerationState initState(Long patientId) {
        Patient patient = patientRepository.findById(patientId)
                .orElseThrow(() -> new RuntimeException("患者不存在: " + patientId));

        List<RiskAssessment> assessments = assessmentRepository.findByPatientIdOrderByCreatedAtDesc(patientId);
        PatientMedicalHistory medicalHistory = patientMedicalHistoryRepository.findByPatientId(patientId).orElse(null);
        int age = patient.getBirthDate() != null
                ? Period.between(patient.getBirthDate(), LocalDate.now()).getYears()
                : 0;

        EducationGenerationState state = new EducationGenerationState();
        state.setPatientId(patientId);
        state.setPatient(patient);
        state.setMedicalHistory(medicalHistory);
        state.setMedicalHistorySummary(PatientMedicalHistoryPromptSupport.buildSummary(medicalHistory));
        state.setAssessments(assessments);
        state.setAge(age);
        return state;
    }

    private Map<String, Object> buildMetadata(EducationGenerationState state, int stepIndex) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("flowType", "education");
        metadata.put("patientId", state.getPatientId());
        metadata.put("patientName", state.getPatient().getName());
        metadata.put("stepIndex", stepIndex);
        metadata.put("diseaseCount", state.getDiseaseNames().size());
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
