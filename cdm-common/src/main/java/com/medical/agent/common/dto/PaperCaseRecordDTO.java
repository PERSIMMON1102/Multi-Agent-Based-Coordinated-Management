package com.medical.agent.common.dto;

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
public class PaperCaseRecordDTO {
    private Long id;
    private Long patientId;
    private LocalDate recordDate;
    private String sourceFileUrl;
    private String sourceFileType;
    private String originalFileName;
    private String ocrText;
    private PaperCasePatientInfoDTO patientInfo;
    private String chiefComplaint;
    private String presentIllness;
    private String physicalExam;
    private String preliminaryDiagnosis;
    private String icdCode;
    private String icdTitle;
    private Double icdConfidence;
    private String icdCodingStatus;
    private String icdCodingSource;
    private Long icdReviewedBy;
    private String icdReviewedAt;
    private List<PaperCaseIcdCandidateDTO> icdCandidates;
    private HealthRecordDTO structuredMetrics;
    private Boolean medicalHistorySynced;
    private String medplumSyncStatus;
    private String medplumSyncMessage;
    private String medplumSyncedAt;
    private ClinicalDataJobDTO job;
    private String createdAt;
    private String updatedAt;
}
