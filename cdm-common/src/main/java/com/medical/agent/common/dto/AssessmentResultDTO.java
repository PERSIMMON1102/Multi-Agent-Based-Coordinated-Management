package com.medical.agent.common.dto;

import com.medical.agent.common.enums.DiseaseType;
import com.medical.agent.common.enums.RiskLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssessmentResultDTO {
    private Long id;
    private Long patientId;
    private String patientName;
    private DiseaseType diseaseType;
    private RiskLevel riskLevel;
    private Double score;
    private String assessmentDetail;
    private Map<String, Object> indicators;
    private List<String> recommendations;
    private List<String> historyAlerts;
    private String agentId;
    private String assessmentProvider;
    private String orchestrationSessionId;
    private String orchestrationTaskId;
    private String orchestrationAssignedAgent;
    private LocalDateTime createdAt;
}
