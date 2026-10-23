package com.medical.agent.model.entity;

import com.medical.agent.model.entity.converter.EncryptedStringAttributeConverter;
import jakarta.persistence.Convert;
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

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "paper_case_record", indexes = {
        @Index(name = "idx_paper_case_patient", columnList = "patient_id"),
        @Index(name = "idx_paper_case_created", columnList = "created_at")
})
public class PaperCaseRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "patient_id", nullable = false)
    private Long patientId;

    @Column(name = "record_date")
    private LocalDate recordDate;

    @Column(name = "source_file_url", length = 500, nullable = false)
    private String sourceFileUrl;

    @Column(name = "source_object_key", length = 255, nullable = false)
    private String sourceObjectKey;

    @Column(name = "source_file_type", length = 50, nullable = false)
    private String sourceFileType;

    @Column(name = "original_file_name", length = 255)
    private String originalFileName;

    @Column(name = "ocr_text", columnDefinition = "TEXT")
    @Convert(converter = EncryptedStringAttributeConverter.class)
    private String ocrText;

    @Column(name = "patient_name", length = 100)
    @Convert(converter = EncryptedStringAttributeConverter.class)
    private String patientName;

    @Column(length = 20)
    private String gender;

    private Integer age;

    @Column(name = "chief_complaint", columnDefinition = "TEXT")
    @Convert(converter = EncryptedStringAttributeConverter.class)
    private String chiefComplaint;

    @Column(name = "present_illness", columnDefinition = "TEXT")
    @Convert(converter = EncryptedStringAttributeConverter.class)
    private String presentIllness;

    @Column(name = "physical_exam", columnDefinition = "TEXT")
    @Convert(converter = EncryptedStringAttributeConverter.class)
    private String physicalExam;

    @Column(name = "preliminary_diagnosis", columnDefinition = "TEXT")
    @Convert(converter = EncryptedStringAttributeConverter.class)
    private String preliminaryDiagnosis;

    @Column(name = "icd_code", length = 50)
    private String icdCode;

    @Column(name = "icd_title", length = 255)
    private String icdTitle;

    @Column(name = "icd_confidence")
    private Double icdConfidence;

    @Column(name = "icd_coding_status", length = 50)
    private String icdCodingStatus;

    @Column(name = "icd_coding_source", length = 50)
    private String icdCodingSource;

    @Column(name = "icd_reviewed_by")
    private Long icdReviewedBy;

    @Column(name = "icd_reviewed_at")
    private LocalDateTime icdReviewedAt;

    @Column(name = "icd_candidates_json", columnDefinition = "TEXT")
    @Convert(converter = EncryptedStringAttributeConverter.class)
    private String icdCandidatesJson;

    @Column(name = "structured_metrics_json", columnDefinition = "TEXT")
    @Convert(converter = EncryptedStringAttributeConverter.class)
    private String structuredMetricsJson;

    @Column(name = "raw_model_json", columnDefinition = "TEXT")
    @Convert(converter = EncryptedStringAttributeConverter.class)
    private String rawModelJson;

    @Column(name = "medical_history_synced")
    private Boolean medicalHistorySynced;

    @Column(name = "medplum_sync_status", length = 50)
    private String medplumSyncStatus;

    @Column(name = "medplum_sync_message", columnDefinition = "TEXT")
    private String medplumSyncMessage;

    @Column(name = "medplum_synced_at")
    private LocalDateTime medplumSyncedAt;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (recordDate == null) {
            recordDate = LocalDate.now();
        }
        if (medicalHistorySynced == null) {
            medicalHistorySynced = false;
        }
        if (icdCodingStatus == null || icdCodingStatus.isBlank()) {
            icdCodingStatus = "not_coded";
        }
        if (medplumSyncStatus == null || medplumSyncStatus.isBlank()) {
            medplumSyncStatus = "pending";
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
