package com.medical.agent.service;

import com.medical.agent.common.dto.AssessmentResultDTO;
import com.medical.agent.common.enums.DiseaseType;
import com.medical.agent.model.entity.Patient;
import com.medical.agent.model.entity.RiskAssessment;
import com.medical.agent.model.repository.PatientRepository;
import com.medical.agent.model.repository.RiskAssessmentRepository;
import com.medical.agent.service.support.AssessmentHistoryAlertSupport;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class AssessmentService {

    private final RiskAssessmentRepository assessmentRepository;
    private final PatientRepository patientRepository;

    public AssessmentService(RiskAssessmentRepository assessmentRepository, PatientRepository patientRepository) {
        this.assessmentRepository = assessmentRepository;
        this.patientRepository = patientRepository;
    }

    public List<AssessmentResultDTO> getByPatientId(Long patientId) {
        return assessmentRepository.findByPatientIdOrderByCreatedAtDesc(patientId)
                .stream().map(this::toDTO).collect(Collectors.toList());
    }

    public List<AssessmentResultDTO> getByPatientAndDisease(Long patientId, DiseaseType diseaseType) {
        return assessmentRepository.findByPatientIdAndDiseaseTypeOrderByCreatedAtDesc(patientId, diseaseType)
                .stream().map(this::toDTO).collect(Collectors.toList());
    }

    public AssessmentResultDTO getLatest(Long patientId, DiseaseType diseaseType) {
        return assessmentRepository.findFirstByPatientIdAndDiseaseTypeOrderByCreatedAtDesc(patientId, diseaseType)
                .map(this::toDTO).orElse(null);
    }

    private AssessmentResultDTO toDTO(RiskAssessment a) {
        String patientName = patientRepository.findById(a.getPatientId())
                .map(Patient::getName).orElse("未知");
        List<String> recommendations = splitRecommendations(a.getRecommendations());
        return AssessmentResultDTO.builder()
                .id(a.getId())
                .patientId(a.getPatientId())
                .patientName(patientName)
                .diseaseType(a.getDiseaseType())
                .riskLevel(a.getRiskLevel())
                .score(a.getScore())
                .assessmentDetail(a.getAssessmentDetail())
                .recommendations(recommendations)
                .historyAlerts(AssessmentHistoryAlertSupport.extractHistoryAlerts(a.getAssessmentDetail(), recommendations))
                .agentId(a.getAgentId())
                .assessmentProvider(a.getAssessmentProvider())
                .orchestrationSessionId(a.getOrchestrationSessionId())
                .orchestrationTaskId(a.getOrchestrationTaskId())
                .orchestrationAssignedAgent(a.getOrchestrationAssignedAgent())
                .createdAt(a.getCreatedAt())
                .build();
    }

    private List<String> splitRecommendations(String recommendations) {
        if (recommendations == null || recommendations.isBlank()) {
            return List.of();
        }
        return Arrays.stream(recommendations.split("; "))
                .map(String::trim)
                .filter(item -> !item.isBlank())
                .toList();
    }
}
