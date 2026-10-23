package com.medical.agent.service;

import com.medical.agent.common.enums.DiseaseType;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Map;

@Component
@ConditionalOnProperty(name = "jade.enabled", havingValue = "true")
public class JadeClinicalAssessmentOrchestrator implements ClinicalAssessmentOrchestrator {

    private final JadeAssessmentTriggerGateway jadeAssessmentTriggerGateway;

    public JadeClinicalAssessmentOrchestrator(JadeAssessmentTriggerGateway jadeAssessmentTriggerGateway) {
        this.jadeAssessmentTriggerGateway = jadeAssessmentTriggerGateway;
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
        jadeAssessmentTriggerGateway.triggerAssessment(patientId, diseaseType, healthData, assessmentOptions);
    }
}
