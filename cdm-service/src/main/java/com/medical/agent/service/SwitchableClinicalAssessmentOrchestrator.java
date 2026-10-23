package com.medical.agent.service;

import com.medical.agent.common.enums.DiseaseType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import org.springframework.lang.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.Locale;
import java.util.Map;

@Primary
@Component
public class SwitchableClinicalAssessmentOrchestrator implements ClinicalAssessmentOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(SwitchableClinicalAssessmentOrchestrator.class);

    private final JadeClinicalAssessmentOrchestrator jadeClinicalAssessmentOrchestrator;
    private final ReActClinicalAssessmentOrchestrator reActClinicalAssessmentOrchestrator;

    @Value("${clinical.assessment.provider:react}")
    private String provider = "react";

    public SwitchableClinicalAssessmentOrchestrator(@Nullable JadeClinicalAssessmentOrchestrator jadeClinicalAssessmentOrchestrator,
                                                    ReActClinicalAssessmentOrchestrator reActClinicalAssessmentOrchestrator) {
        this.jadeClinicalAssessmentOrchestrator = jadeClinicalAssessmentOrchestrator;
        this.reActClinicalAssessmentOrchestrator = reActClinicalAssessmentOrchestrator;
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
        resolveDelegate().triggerAssessment(patientId, diseaseType, healthData, assessmentOptions);
    }

    private ClinicalAssessmentOrchestrator resolveDelegate() {
        String resolvedProvider = normalize(provider);
        if ("react".equalsIgnoreCase(resolvedProvider)) {
            return reActClinicalAssessmentOrchestrator;
        }
        if ("jade".equalsIgnoreCase(resolvedProvider) && jadeClinicalAssessmentOrchestrator != null) {
            return jadeClinicalAssessmentOrchestrator;
        }
        if ("jade".equalsIgnoreCase(resolvedProvider)) {
            log.warn("clinical.assessment.provider=jade but JADE bridge is disabled or unavailable, falling back to ReAct");
        } else {
            log.warn("clinical.assessment.provider={} is unsupported, falling back to ReAct", resolvedProvider);
        }
        return reActClinicalAssessmentOrchestrator;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
