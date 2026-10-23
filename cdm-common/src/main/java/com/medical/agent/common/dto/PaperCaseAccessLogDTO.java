package com.medical.agent.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaperCaseAccessLogDTO {
    private String id;
    private Long paperCaseId;
    private Long patientId;
    private Long actorUserId;
    private String actorRole;
    private String action;
    private String outcome;
    private String resourceType;
    private String requestIp;
    private String userAgent;
    private String targetSummary;
    private String detailSummary;
    private String timestamp;
}
