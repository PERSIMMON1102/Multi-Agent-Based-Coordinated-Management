package com.medical.agent.service;

import com.medical.agent.common.enums.DiseaseType;

import java.util.Map;

public interface ClinicalAssessmentOrchestrator {

    void triggerAssessment(Long patientId, DiseaseType diseaseType, Map<String, Object> healthData);

    void triggerAssessment(Long patientId,
                           DiseaseType diseaseType,
                           Map<String, Object> healthData,
                           Map<String, Object> assessmentOptions);
}
