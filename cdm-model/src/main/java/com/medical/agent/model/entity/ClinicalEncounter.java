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
@Table(name = "clinical_encounter", indexes = {
        @Index(name = "idx_clinical_encounter_patient", columnList = "patient_id"),
        @Index(name = "idx_clinical_encounter_source", columnList = "source_kind,source_ref_id")
})
public class ClinicalEncounter {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "patient_id", nullable = false)
    private Long patientId;

    @Column(name = "encounter_type", length = 50, nullable = false)
    private String encounterType;

    @Column(name = "encounter_date")
    private LocalDate encounterDate;

    @Column(name = "status", length = 50)
    private String status;

    @Column(name = "chief_complaint", columnDefinition = "TEXT")
    @Convert(converter = EncryptedStringAttributeConverter.class)
    private String chiefComplaint;

    @Column(name = "present_illness", columnDefinition = "TEXT")
    @Convert(converter = EncryptedStringAttributeConverter.class)
    private String presentIllness;

    @Column(name = "physical_exam", columnDefinition = "TEXT")
    @Convert(converter = EncryptedStringAttributeConverter.class)
    private String physicalExam;

    @Column(name = "source_kind", length = 50)
    private String sourceKind;

    @Column(name = "source_ref_id")
    private Long sourceRefId;

    @Column(name = "source_file_url", length = 500)
    private String sourceFileUrl;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
        if (status == null || status.isBlank()) {
            status = "finished";
        }
        if (encounterDate == null) {
            encounterDate = LocalDate.now();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
