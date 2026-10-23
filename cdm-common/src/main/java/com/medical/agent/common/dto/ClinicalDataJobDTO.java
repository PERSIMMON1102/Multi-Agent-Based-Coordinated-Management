package com.medical.agent.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClinicalDataJobDTO {
    private Long id;
    private String jobType;
    private String jobTypeLabel;
    private String jobStatus;
    private Long patientId;
    private String correlationId;
    private String idempotencyKey;
    private String sourceKind;
    private Long sourceRefId;
    private String sourceFileName;
    private String sourceFileUrl;
    private String vendor;
    private String vendorLabel;
    private String lastStage;
    private Long runAttempt;
    private Integer retryCount;
    private Integer maxRetries;
    private String nextRetryAt;
    private String firstFailedAt;
    private String lastError;
    private String startedAt;
    private String finishedAt;
    private String createdAt;
    private String updatedAt;
}
