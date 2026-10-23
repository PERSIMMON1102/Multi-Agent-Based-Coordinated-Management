package com.medical.agent.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PatientMedicalHistoryDTO {
    private String pastDiseases;
    private String surgicalHistory;
    private String drugAllergies;
    private String foodAllergies;
    private String immunizationHistory;
    private String familyHistory;
    private String otherNotes;
    private String updatedAt;
}
