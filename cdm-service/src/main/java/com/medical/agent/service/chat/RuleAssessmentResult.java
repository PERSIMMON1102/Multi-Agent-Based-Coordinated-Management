package com.medical.agent.service.chat;

import com.medical.agent.common.enums.DiseaseType;
import com.medical.agent.common.enums.RiskLevel;

import java.util.List;

public record RuleAssessmentResult(DiseaseType diseaseType,
                                   RiskLevel riskLevel,
                                   Double score,
                                   String detail,
                                   List<String> recommendations,
                                   String source) {
}
