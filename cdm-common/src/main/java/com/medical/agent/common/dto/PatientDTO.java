package com.medical.agent.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PatientDTO {
    private Long id;
    @NotBlank(message = "姓名不能为空")
    private String name;
    @NotNull(message = "性别不能为空")
    private String gender;
    private LocalDate birthDate;
    private String phone;
    private String idCard;
    private String address;
    private String fhirId;
    private String emergencyContact;
    private String emergencyPhone;
    private Long assignedDoctorId;
    private Boolean profileCompleted;
    private Long paperCaseTotalCount;
    private Long paperCasePendingReviewCount;
    private Long paperCaseFailedSyncCount;
    private Long paperCaseAttentionCount;
    private PatientMedicalHistoryDTO medicalHistory;
}
