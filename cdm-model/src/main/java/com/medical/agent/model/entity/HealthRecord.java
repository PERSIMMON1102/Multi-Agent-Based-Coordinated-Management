package com.medical.agent.model.entity;

import jakarta.persistence.*;
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
@Table(name = "health_record")
public class HealthRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long patientId;

    private Long encounterId;

    private LocalDate recordDate;
    private LocalDateTime recordedAt;

    // === 高血压指标 ===
    private Integer systolicBp;
    private Integer diastolicBp;
    private Integer restingHeartRate;

    // === 糖尿病指标 ===
    private Double fastingGlucose;
    private Double postprandialGlucose;
    private Double hba1c;

    // === 通用体征 ===
    private Double bmi;
    private Double weight;
    private Double height;
    private Double totalCholesterol;
    private Double triglycerides;
    private Double ldlCholesterol;
    private Double hdlCholesterol;

    // === COPD指标 ===
    private Double fev1Percent;
    private Integer catScore;
    private Integer mmrcScore;
    private Integer acuteExacerbations;
    private Integer smokingYears;

    // === 慢性肾病指标 ===
    private Double egfr;
    private Double uacr;
    private Double serumCreatinine;
    private Double hemoglobin;

    // === 扩展检验指标（对齐医生录入页）===
    private Double wbc;
    private Integer platelet;
    private Double neutrophilPct;
    private Double alt;
    private Double ast;
    private Double uricAcid;
    private Double ck;
    private Double potassium;
    private String urineProtein;
    private String urineGlucose;
    private String urineBlood;

    // === 冠心病指标 ===
    private Integer chestPainFrequency;
    private Integer exerciseTolerance;
    private Double ejectionFraction;
    private Double bnp;
    private Boolean onAntiplatelet;

    // === 脑卒中指标 ===
    private Boolean hasAtrialFibrillation;
    private Boolean hasDiabetesHistory;
    private Boolean isSmoking;
    private Integer adlScore;
    private Integer mmseScore;
    private Double carotidStenosis;

    // === 用药依从性 ===
    private Integer medicationMissRate;
    private Boolean onRegularMedication;

    // === 生活方式 ===
    private Integer saltIntake;
    private Integer exerciseFrequency;
    private Integer alcoholIntake;

    private String dataSource;
    private String sourceVendor;
    private String sourceDeviceType;
    private String sourceDeviceId;

    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (recordDate == null) recordDate = LocalDate.now();
    }
}
