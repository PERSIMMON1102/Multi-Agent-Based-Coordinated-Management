package com.medical.agent.common.dto;

import com.medical.agent.common.enums.DiseaseType;
import com.medical.agent.common.enums.FollowUpFrequency;
import com.medical.agent.common.enums.FollowUpStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FollowUpPlanDTO {
    private Long id;
    private Long patientId;
    private String patientName;
    private DiseaseType diseaseType;
    private FollowUpFrequency frequency;
    private FollowUpStatus status;
    private LocalDate nextFollowDate;
    private String planContent;
    private List<String> checkItems;
    private String agentId;
}
