package com.medical.agent.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClinicalDataJobTypeSummaryDTO {
    private String jobType;
    private String jobTypeLabel;
    @Builder.Default
    private Long totalCount = 0L;
    @Builder.Default
    private Long failedCount = 0L;
    @Builder.Default
    private Long deadLetterCount = 0L;
    @Builder.Default
    private Long reviewPendingCount = 0L;
    @Builder.Default
    private Long runningCount = 0L;
    @Builder.Default
    private Long retryableCount = 0L;
    @Builder.Default
    private Long runningTimeoutCount = 0L;
    @Builder.Default
    private Long recoveredCount = 0L;
    @Builder.Default
    private Long averageRecoveryDurationMinutes = 0L;
}
