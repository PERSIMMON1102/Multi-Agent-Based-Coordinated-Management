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
@Table(name = "clinical_attachment", indexes = {
        @Index(name = "idx_clinical_attachment_encounter", columnList = "encounter_id"),
        @Index(name = "idx_clinical_attachment_source", columnList = "source_kind,source_ref_id")
})
public class ClinicalAttachment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "encounter_id")
    private Long encounterId;

    @Column(name = "attachment_type", length = 50)
    private String attachmentType;

    @Column(name = "content_type", length = 100)
    private String contentType;

    @Column(name = "file_name", length = 255)
    private String fileName;

    @Column(name = "file_url", length = 500)
    private String fileUrl;

    @Column(name = "ocr_text", columnDefinition = "LONGTEXT")
    @Convert(converter = EncryptedStringAttributeConverter.class)
    private String ocrText;

    @Column(name = "document_narrative", columnDefinition = "LONGTEXT")
    @Convert(converter = EncryptedStringAttributeConverter.class)
    private String documentNarrative;

    @Column(name = "source_kind", length = 50)
    private String sourceKind;

    @Column(name = "source_ref_id")
    private Long sourceRefId;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
