package com.medical.agent.service;

import com.medical.agent.common.enums.DiseaseType;
import com.medical.agent.common.message.AgentMessage;
import com.medical.agent.common.message.MessageType;
import com.medical.agent.common.util.PatientMedicalHistoryUtil;
import com.medical.agent.model.entity.Patient;
import com.medical.agent.model.entity.PatientMedicalHistory;
import com.medical.agent.model.repository.PatientMedicalHistoryRepository;
import com.medical.agent.model.repository.PatientRepository;
import com.medical.agent.service.support.PatientMedicalHistoryPromptSupport;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@ConditionalOnProperty(name = "jade.enabled", havingValue = "true")
public class JadeAssessmentRequestFactory {

    private final PatientRepository patientRepository;
    private final PatientMedicalHistoryRepository patientMedicalHistoryRepository;

    public JadeAssessmentRequestFactory(PatientRepository patientRepository,
                                        PatientMedicalHistoryRepository patientMedicalHistoryRepository) {
        this.patientRepository = patientRepository;
        this.patientMedicalHistoryRepository = patientMedicalHistoryRepository;
    }

    public PreparedJadeAssessmentRequest prepare(Long patientId,
                                                 DiseaseType diseaseType,
                                                 Map<String, Object> healthData,
                                                 Map<String, Object> assessmentOptions) {
        Patient patient = patientRepository.findById(patientId)
                .orElseThrow(() -> new RuntimeException("Patient not found: " + patientId));

        Map<String, Object> requestData = new HashMap<>();
        if (healthData != null) {
            requestData.putAll(healthData);
        }
        if (assessmentOptions != null) {
            requestData.putAll(assessmentOptions);
        }
        requestData.putIfAbsent("_disableJadeSecondaryAgents", true);
        enrichAssessmentRequestData(patientId, requestData);

        AgentMessage request = AgentMessage.builder()
                .messageId(UUID.randomUUID().toString())
                .messageType(MessageType.ASSESS_REQUEST)
                .patientId(patientId)
                .patientName(patient.getName())
                .diseaseType(diseaseType)
                .data(requestData)
                .timestamp(System.currentTimeMillis())
                .build();
        return new PreparedJadeAssessmentRequest(patient, request);
    }

    private void enrichAssessmentRequestData(Long patientId, Map<String, Object> requestData) {
        if (requestData == null || patientId == null || patientMedicalHistoryRepository == null) {
            return;
        }
        PatientMedicalHistory history = patientMedicalHistoryRepository.findByPatientId(patientId).orElse(null);
        if (history == null) {
            return;
        }

        putIfAbsent(requestData, "pastDiseases", history.getPastDiseases());
        putIfAbsent(requestData, "surgicalHistory", history.getSurgicalHistory());
        putIfAbsent(requestData, "drugAllergies", history.getDrugAllergies());
        putIfAbsent(requestData, "foodAllergies", history.getFoodAllergies());
        putIfAbsent(requestData, "immunizationHistory", history.getImmunizationHistory());
        putIfAbsent(requestData, "familyHistory", history.getFamilyHistory());
        String historySummary = PatientMedicalHistoryPromptSupport.buildSummary(history);
        putIfAbsent(requestData, "medicalHistorySummary", historySummary);

        if (!requestData.containsKey("historyDiseaseTypes")) {
            List<String> diseaseTypes = PatientMedicalHistoryUtil.detectDiseaseTypes(
                            history.getPastDiseases(),
                            history.getFamilyHistory(),
                            historySummary
                    ).stream()
                    .map(DiseaseType::name)
                    .toList();
            if (!diseaseTypes.isEmpty()) {
                requestData.put("historyDiseaseTypes", diseaseTypes);
            }
        }
    }

    private void putIfAbsent(Map<String, Object> target, String key, String value) {
        if (target.containsKey(key) || value == null || value.isBlank()) {
            return;
        }
        target.put(key, value);
    }

    public record PreparedJadeAssessmentRequest(Patient patient, AgentMessage request) {
    }
}
