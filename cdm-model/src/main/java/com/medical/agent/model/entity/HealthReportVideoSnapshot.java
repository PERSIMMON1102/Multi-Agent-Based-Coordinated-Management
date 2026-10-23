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
@Table(name = "health_report_video_snapshot", indexes = {
    @Index(name = "idx_report_video_snapshot_report", columnList = "report_id"),
    @Index(name = "idx_report_video_snapshot_video", columnList = "video_id")
})
public class HealthReportVideoSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "report_id", nullable = false)
    private Long reportId;

    @Column(name = "video_id")
    private Long videoId;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "disease_type", length = 50)
    private String diseaseType;

    @Column(length = 50)
    private String category;

    @Column(name = "cover_url", length = 500)
    private String coverUrl;

    @Column(name = "video_url", nullable = false, length = 500)
    private String videoUrl;

    @Column(name = "object_key", length = 255)
    private String objectKey;

    @Column(name = "cover_object_key", length = 255)
    private String coverObjectKey;

    @Column(name = "duration_seconds")
    private Long durationSeconds;

    @Column(name = "recommend_reason", length = 500)
    private String recommendReason;

    @Builder.Default
    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (sortOrder == null) {
            sortOrder = 0;
        }
    }
}
