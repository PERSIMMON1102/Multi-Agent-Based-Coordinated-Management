package com.medical.agent.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "smart_follow_up_message", indexes = {
        @Index(name = "idx_smart_followup_patient", columnList = "patient_id"),
        @Index(name = "idx_smart_followup_status", columnList = "status"),
        @Index(name = "idx_smart_followup_created_at", columnList = "created_at")
})
public class SmartFollowUpMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "patient_id", nullable = false)
    private Long patientId;

    @Column(name = "patient_name", length = 100)
    private String patientName;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String message;

    @Column(length = 50)
    private String channel;

    @Column(nullable = false, length = 30)
    private String status;

    @Column(name = "agent_trace_json", columnDefinition = "TEXT")
    private String agentTraceJson;

    @Column(name = "history_alert_json", columnDefinition = "TEXT")
    private String historyAlertJson;

    @Column(columnDefinition = "TEXT")
    private String feedback;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "feedback_received_at")
    private LocalDateTime feedbackReceivedAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
