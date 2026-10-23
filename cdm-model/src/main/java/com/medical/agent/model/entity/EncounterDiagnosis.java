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

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "encounter_diagnosis", indexes = {
        @Index(name = "idx_encounter_diagnosis_encounter", columnList = "encounter_id")
})
public class EncounterDiagnosis {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "encounter_id", nullable = false)
    private Long encounterId;

    @Column(name = "diagnosis_text", columnDefinition = "TEXT", nullable = false)
    @Convert(converter = EncryptedStringAttributeConverter.class)
    private String diagnosisText;

    @Column(name = "is_primary_diagnosis")
    private Boolean primaryDiagnosis;

    @Column(name = "icd11_code", length = 50)
    private String icd11Code;

    @Column(name = "icd11_title", length = 255)
    private String icd11Title;

    @Column(name = "coding_status", length = 50)
    private String codingStatus;

    @Column(name = "coding_source", length = 50)
    private String codingSource;

    @Column(name = "coding_confidence")
    private Double codingConfidence;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
        if (primaryDiagnosis == null) {
            primaryDiagnosis = true;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
