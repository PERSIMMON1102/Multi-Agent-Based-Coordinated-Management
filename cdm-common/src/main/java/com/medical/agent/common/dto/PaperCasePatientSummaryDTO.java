package com.medical.agent.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaperCasePatientSummaryDTO {
    private Long patientId;
    private PaperCasePatientInfoDTO patientInfo;
    @Builder.Default
    private Long totalRecords = 0L;
    @Builder.Default
    private Long pendingReviewCount = 0L;
    @Builder.Default
    private Long failedSyncCount = 0L;
    @Builder.Default
    private Long failedJobCount = 0L;
    @Builder.Default
    private List<PaperCaseRecordDTO> attentionRecords = new ArrayList<>();
    @Builder.Default
    private List<PaperCaseRecordDTO> pendingReviewRecords = new ArrayList<>();
    @Builder.Default
    private List<PaperCaseRecordDTO> failedSyncRecords = new ArrayList<>();
    @Builder.Default
    private List<PaperCaseRecordDTO> allRecords = new ArrayList<>();
}
