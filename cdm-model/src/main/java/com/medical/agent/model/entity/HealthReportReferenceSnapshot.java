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
@Table(name = "health_report_reference_snapshot", indexes = {
    @Index(name = "idx_report_reference_snapshot_report", columnList = "report_id"),
    @Index(name = "idx_report_reference_snapshot_ref", columnList = "ref_type, ref_id")
})
public class HealthReportReferenceSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "report_id", nullable = false)
    private Long reportId;

    @Column(name = "ref_type", nullable = false, length = 50)
    private String refType;

    @Column(name = "ref_id")
    private Long refId;

    @Column(name = "video_id")
    private Long videoId;

    @Column(name = "disease_key", length = 50)
    private String diseaseKey;

    @Column(length = 200)
    private String title;

    @Column(length = 200)
    private String section;

    @Column(length = 200)
    private String source;

    @Column(name = "published_at", length = 30)
    private String publishedAt;

    @Column(name = "content_snippet", columnDefinition = "TEXT")
    private String contentSnippet;

    @Column
    private Double score;

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
