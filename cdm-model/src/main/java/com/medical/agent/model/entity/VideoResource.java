package com.medical.agent.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
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
@Table(name = "video_resource", indexes = {
    @Index(name = "idx_video_resource_disease", columnList = "disease_type"),
    @Index(name = "idx_video_resource_active", columnList = "active"),
    @Index(name = "idx_video_resource_sort", columnList = "sort_order")
})
public class VideoResource {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "disease_type", length = 50)
    private String diseaseType;

    @Column(length = 50)
    private String category;

    @Column(name = "content_tags", length = 500)
    private String contentTags;

    @Column(name = "cover_url", length = 500)
    private String coverUrl;

    @Column(name = "video_url", nullable = false, length = 500)
    private String videoUrl;

    @Column(name = "object_key", length = 255)
    private String objectKey;

    @Column(name = "cover_object_key", length = 255)
    private String coverObjectKey;

    @Column(name = "source_type", length = 30)
    private String sourceType;

    @Column(name = "duration_seconds")
    private Long durationSeconds;

    @Builder.Default
    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;

    @Builder.Default
    @Column(nullable = false)
    private Boolean active = true;

    @Builder.Default
    @Column(name = "has_transcript", nullable = false)
    private Boolean hasTranscript = false;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
        if (sortOrder == null) {
            sortOrder = 0;
        }
        if (active == null) {
            active = true;
        }
        if (hasTranscript == null) {
            hasTranscript = false;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
        if (sortOrder == null) {
            sortOrder = 0;
        }
        if (active == null) {
            active = true;
        }
        if (hasTranscript == null) {
            hasTranscript = false;
        }
    }
}
