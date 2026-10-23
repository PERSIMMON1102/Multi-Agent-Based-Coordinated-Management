package com.medical.agent.model.entity;

import com.medical.agent.model.entity.converter.EncryptedStringAttributeConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
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
@Table(name = "clinical_data_job",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_clinical_job_idempotency", columnNames = "idempotency_key")
        },
        indexes = {
        @Index(name = "idx_clinical_job_patient", columnList = "patient_id"),
        @Index(name = "idx_clinical_job_type_status", columnList = "job_type,job_status"),
        @Index(name = "idx_clinical_job_correlation", columnList = "correlation_id"),
        @Index(name = "idx_clinical_job_idempotency", columnList = "idempotency_key", unique = true),
        @Index(name = "idx_clinical_job_created", columnList = "created_at")
})
public class ClinicalDataJob {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "job_type", nullable = false, length = 50)
    private String jobType;

    @Column(name = "job_status", nullable = false, length = 50)
    private String jobStatus;

    @Column(name = "patient_id", nullable = false)
    private Long patientId;

    @Column(name = "correlation_id", length = 100)
    private String correlationId;

    @Column(name = "idempotency_key", length = 160)
    private String idempotencyKey;

    @Column(name = "source_kind", length = 50)
    private String sourceKind;

    @Column(name = "source_ref_id")
    private Long sourceRefId;

    @Column(name = "source_file_name", length = 255)
    private String sourceFileName;

    @Column(name = "source_file_url", length = 500)
    private String sourceFileUrl;

    @Column(name = "vendor", length = 50)
    private String vendor;

    @Column(name = "payload_json", columnDefinition = "LONGTEXT")
    @Convert(converter = EncryptedStringAttributeConverter.class)
    private String payloadJson;

    @Column(name = "result_json", columnDefinition = "LONGTEXT")
    @Convert(converter = EncryptedStringAttributeConverter.class)
    private String resultJson;

    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    @Column(name = "retry_count")
    private Integer retryCount;

    @Column(name = "max_retries")
    private Integer maxRetries;

    @Column(name = "last_stage", length = 50)
    private String lastStage;

    @Column(name = "next_retry_at")
    private LocalDateTime nextRetryAt;

    @Column(name = "first_failed_at")
    private LocalDateTime firstFailedAt;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    @Column(name = "run_attempt")
    private Long runAttempt;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Version
    @Column(name = "lock_version")
    private Long lockVersion;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
        if (jobStatus == null || jobStatus.isBlank()) {
            jobStatus = "received";
        }
        if (retryCount == null) {
            retryCount = 0;
        }
        if (maxRetries == null) {
            maxRetries = 3;
        }
        if (runAttempt == null) {
            runAttempt = 0L;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
