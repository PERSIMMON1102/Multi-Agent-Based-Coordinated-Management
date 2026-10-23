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
@Table(name = "video_watch_session", indexes = {
    @Index(name = "idx_video_watch_patient", columnList = "patient_id"),
    @Index(name = "idx_video_watch_video", columnList = "video_id"),
    @Index(name = "idx_video_watch_active", columnList = "last_active_at"),
    @Index(name = "idx_video_watch_source", columnList = "source_type"),
    @Index(name = "idx_video_watch_completed", columnList = "completed")
})
public class VideoWatchSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "patient_id", nullable = false)
    private Long patientId;

    @Column(name = "video_id", nullable = false)
    private Long videoId;

    @Column(name = "source_type", length = 50)
    private String sourceType;

    @Column(name = "source_ref_id")
    private Long sourceRefId;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "last_active_at", nullable = false)
    private LocalDateTime lastActiveAt;

    @Builder.Default
    @Column(name = "watched_seconds", nullable = false)
    private Long watchedSeconds = 0L;

    @Column(name = "duration_seconds")
    private Long durationSeconds;

    @Builder.Default
    @Column(name = "progress_percent", nullable = false)
    private Double progressPercent = 0D;

    @Builder.Default
    @Column(nullable = false)
    private Boolean completed = false;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (startedAt == null) {
            startedAt = now;
        }
        if (lastActiveAt == null) {
            lastActiveAt = now;
        }
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
        if (watchedSeconds == null) {
            watchedSeconds = 0L;
        }
        if (progressPercent == null) {
            progressPercent = 0D;
        }
        if (completed == null) {
            completed = false;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
        if (lastActiveAt == null) {
            lastActiveAt = updatedAt;
        }
        if (watchedSeconds == null) {
            watchedSeconds = 0L;
        }
        if (progressPercent == null) {
            progressPercent = 0D;
        }
        if (completed == null) {
            completed = false;
        }
    }
}
