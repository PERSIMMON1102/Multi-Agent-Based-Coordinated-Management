package com.medical.agent.model.entity;

import com.medical.agent.common.enums.DiseaseType;
import com.medical.agent.common.enums.RiskLevel;
import jakarta.persistence.*;
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
@Table(name = "risk_assessment")
public class RiskAssessment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long patientId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DiseaseType diseaseType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RiskLevel riskLevel;

    private Double score;

    @Column(columnDefinition = "TEXT")
    private String assessmentDetail;

    @Column(columnDefinition = "TEXT")
    private String recommendations;

    @Column(length = 100)
    private String agentId;

    @Column(length = 50)
    private String assessmentProvider;

    @Column(length = 120)
    private String orchestrationSessionId;

    @Column(length = 120)
    private String orchestrationTaskId;

    @Column(length = 100)
    private String orchestrationAssignedAgent;

    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
