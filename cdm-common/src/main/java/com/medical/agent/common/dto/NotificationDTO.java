package com.medical.agent.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * WebSocket推送通知DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationDTO {
    private String type;         // ASSESSMENT_RESULT, FOLLOW_UP_REMINDER, RESOURCE_RECOMMENDATION
    private Long patientId;
    private String title;
    private String message;
    private List<String> historyAlerts;
    private Object payload;
    private long timestamp;
}
