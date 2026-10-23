package com.medical.agent.service.support;

import com.medical.agent.common.enums.DiseaseType;
import com.medical.agent.common.enums.RiskLevel;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record AssessmentWorkflowPayload(String messageId,
                                        Long patientId,
                                        String patientName,
                                        DiseaseType diseaseType,
                                        RiskLevel riskLevel,
                                        Double score,
                                        String detail,
                                        List<String> recommendations,
                                        Map<String, Object> data) {

    public AssessmentWorkflowPayload {
        recommendations = recommendations == null ? List.of() : List.copyOf(recommendations);
        data = data == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(data));
    }
}
