package com.medical.agent.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClinicalDataJobSummaryDTO {
    @Builder.Default
    private Long totalJobs = 0L;
    @Builder.Default
    private Long retryableCount = 0L;
    @Builder.Default
    private Long runningTimeoutCount = 0L;
    @Builder.Default
    private Long recoveredCount = 0L;
    @Builder.Default
    private Long averageRecoveryDurationMinutes = 0L;
    @Builder.Default
    private Map<String, Long> statusCounts = new LinkedHashMap<>();
    @Builder.Default
    private Map<String, Long> recentAlertCounts = new LinkedHashMap<>();
    @Builder.Default
    private List<ClinicalDataJobBucketDTO> deadLetterByJobType = new ArrayList<>();
    @Builder.Default
    private List<ClinicalDataJobBucketDTO> deadLetterByStage = new ArrayList<>();
    @Builder.Default
    private List<ClinicalDataJobBucketDTO> deadLetterByVendor = new ArrayList<>();
    @Builder.Default
    private List<ClinicalDataJobBucketDTO> runningTimeoutByJobType = new ArrayList<>();
    @Builder.Default
    private List<ClinicalDataJobBucketDTO> runningTimeoutByVendor = new ArrayList<>();
    @Builder.Default
    private List<ClinicalDataJobBucketDTO> topDeadLetterErrors = new ArrayList<>();
    @Builder.Default
    private List<ClinicalDataJobDTO> recentDeadLetters = new ArrayList<>();
    @Builder.Default
    private List<ClinicalDataJobDTO> recentRunningTimeouts = new ArrayList<>();
    @Builder.Default
    private List<ClinicalDataJobDTO> recentRecoveredJobs = new ArrayList<>();
    @Builder.Default
    private List<ClinicalDataJobAlertDTO> recentAlerts = new ArrayList<>();
    @Builder.Default
    private List<ClinicalDataJobTypeSummaryDTO> jobTypeSummaries = new ArrayList<>();
}
