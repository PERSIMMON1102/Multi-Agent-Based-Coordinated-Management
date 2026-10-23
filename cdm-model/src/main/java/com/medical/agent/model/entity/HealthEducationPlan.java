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
@Table(name = "health_education_plan", indexes = {
        @Index(name = "idx_health_education_patient", columnList = "patient_id"),
        @Index(name = "idx_health_education_generated_at", columnList = "generated_at")
})
public class HealthEducationPlan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "plan_id", nullable = false, unique = true, length = 64)
    private String planId;

    @Column(name = "patient_id", nullable = false)
    private Long patientId;

    @Column(name = "patient_name", length = 100)
    private String patientName;

    @Column(name = "generated_at")
    private LocalDateTime generatedAt;

    @Column(name = "diseases_json", columnDefinition = "TEXT")
    private String diseasesJson;

    @Column(columnDefinition = "TEXT")
    private String diet;

    @Column(columnDefinition = "TEXT")
    private String exercise;

    @Column(columnDefinition = "TEXT")
    private String lifestyle;

    @Column(name = "diet_references_json", columnDefinition = "TEXT")
    private String dietReferencesJson;

    @Column(name = "exercise_references_json", columnDefinition = "TEXT")
    private String exerciseReferencesJson;

    @Column(name = "lifestyle_references_json", columnDefinition = "TEXT")
    private String lifestyleReferencesJson;

    @Column(name = "references_json", columnDefinition = "TEXT")
    private String referencesJson;

    @Column(name = "recommended_videos_json", columnDefinition = "TEXT")
    private String recommendedVideosJson;

    @Column(name = "agent_trace_json", columnDefinition = "TEXT")
    private String agentTraceJson;

    @PrePersist
    protected void onCreate() {
        if (generatedAt == null) {
            generatedAt = LocalDateTime.now();
        }
    }
}
