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
@Table(name = "medplum_sync_checkpoint", indexes = {
        @Index(name = "idx_medplum_sync_patient_scope", columnList = "patient_id,sync_scope", unique = true),
        @Index(name = "idx_medplum_sync_updated", columnList = "updated_at")
})
public class MedplumSyncCheckpoint {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "patient_id", nullable = false)
    private Long patientId;

    @Column(name = "sync_scope", nullable = false, length = 50)
    private String syncScope;

    @Column(name = "payload_hash", length = 64)
    private String payloadHash;

    @Column(name = "resource_count")
    private Integer resourceCount;

    @Column(name = "resource_id", length = 100)
    private String resourceId;

    @Column(name = "resource_summary_json", columnDefinition = "LONGTEXT")
    private String resourceSummaryJson;

    @Column(name = "synced_at")
    private LocalDateTime syncedAt;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
        if (syncedAt == null) {
            syncedAt = now;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
