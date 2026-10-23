package com.medical.agent.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClinicalDataJobAlertDTO {
    private Long id;
    private String type;
    private String title;
    private String message;
    private Boolean read;
    private String createdAt;
}
