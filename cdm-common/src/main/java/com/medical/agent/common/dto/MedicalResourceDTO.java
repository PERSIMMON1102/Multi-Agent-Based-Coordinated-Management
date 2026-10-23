package com.medical.agent.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MedicalResourceDTO {
    private Long id;
    private String name;
    private String type;
    private String address;
    private Double latitude;
    private Double longitude;
    private String specialty;
    private String phone;
    private Double distance;
    private String recommendReason;
}
