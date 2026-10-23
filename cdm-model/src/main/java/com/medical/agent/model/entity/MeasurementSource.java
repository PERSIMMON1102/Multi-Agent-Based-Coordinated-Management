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
@Table(name = "measurement_source", indexes = {
        @Index(name = "idx_measurement_source_record", columnList = "health_record_id"),
        @Index(name = "idx_measurement_source_encounter", columnList = "encounter_id")
})
public class MeasurementSource {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "health_record_id", nullable = false)
    private Long healthRecordId;

    @Column(name = "encounter_id")
    private Long encounterId;

    @Column(name = "source_kind", length = 50)
    private String sourceKind;

    @Column(name = "source_ref_id")
    private Long sourceRefId;

    @Column(name = "vendor", length = 50)
    private String vendor;

    @Column(name = "device_type", length = 100)
    private String deviceType;

    @Column(name = "device_id", length = 100)
    private String deviceId;

    @Column(name = "raw_payload_json", columnDefinition = "LONGTEXT")
    @Convert(converter = EncryptedStringAttributeConverter.class)
    private String rawPayloadJson;

    @Column(name = "normalized_payload_json", columnDefinition = "LONGTEXT")
    @Convert(converter = EncryptedStringAttributeConverter.class)
    private String normalizedPayloadJson;

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
