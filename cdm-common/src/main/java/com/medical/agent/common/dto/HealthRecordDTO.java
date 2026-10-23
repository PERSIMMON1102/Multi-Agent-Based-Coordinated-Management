package com.medical.agent.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 健康记录录入DTO，涵盖6种慢性病的日常录入指标
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HealthRecordDTO {
    private Long id;
    private Long patientId;
    private Long encounterId;
    private LocalDate recordDate;
    private LocalDateTime recordedAt;

    // === 高血压指标 ===
    private Integer systolicBp;           // 收缩压 mmHg
    private Integer diastolicBp;          // 舒张压 mmHg
    private Integer restingHeartRate;     // 静息心率

    // === 糖尿病指标 ===
    private Double fastingGlucose;        // 空腹血糖 mmol/L
    private Double postprandialGlucose;   // 餐后2h血糖 mmol/L
    private Double hba1c;                 // 糖化血红蛋白 %

    // === 通用体征 ===
    private Double bmi;                   // BMI
    private Double weight;                // 体重 kg
    private Double height;                // 身高 cm
    private Double totalCholesterol;      // 总胆固醇 mmol/L
    private Double triglycerides;         // 甘油三酯 mmol/L
    private Double ldlCholesterol;        // LDL-C mmol/L
    private Double hdlCholesterol;        // HDL-C mmol/L

    // === COPD指标 ===
    private Double fev1Percent;           // FEV1%预计值
    private Integer catScore;             // CAT评分 0-40
    private Integer mmrcScore;            // mMRC呼吸困难评分 0-4
    private Integer acuteExacerbations;   // 年急性加重次数
    private Integer smokingYears;         // 吸烟年数

    // === 慢性肾病指标 ===
    private Double egfr;                  // eGFR ml/min/1.73m²
    private Double uacr;                  // 尿白蛋白/肌酐比值 mg/g
    private Double serumCreatinine;       // 血肌酐 μmol/L
    private Double hemoglobin;            // 血红蛋白 g/L

    // === 扩展检验指标（对齐医生录入页）===
    private Double wbc;                   // 白细胞 ×10^9/L
    private Integer platelet;             // 血小板 ×10^9/L
    private Double neutrophilPct;         // 中性粒细胞 %
    private Double alt;                   // ALT U/L
    private Double ast;                   // AST U/L
    private Double uricAcid;              // 尿酸 μmol/L
    private Double ck;                    // CK U/L
    private Double potassium;             // 血钾 mmol/L
    private String urineProtein;          // 尿蛋白分级
    private String urineGlucose;          // 尿糖分级
    private String urineBlood;            // 尿潜血分级

    // === 冠心病指标 ===
    private Integer chestPainFrequency;   // 胸痛频率 次/周
    private Integer exerciseTolerance;    // 运动耐量评分 1-5
    private Double ejectionFraction;      // 射血分数 %
    private Double bnp;                   // BNP pg/mL
    private Boolean onAntiplatelet;       // 是否服用抗血小板药

    // === 脑卒中指标 ===
    private Boolean hasAtrialFibrillation;// 房颤史
    private Boolean hasDiabetesHistory;   // 糖尿病史
    private Boolean isSmoking;            // 当前吸烟
    private Integer adlScore;             // 生活自理能力评分 0-100
    private Integer mmseScore;            // MMSE评分 0-30
    private Double carotidStenosis;       // 颈动脉狭窄程度 %

    // === 用药依从性 ===
    private Integer medicationMissRate;   // 漏服率 %
    private Boolean onRegularMedication;  // 是否规律服药

    // === 生活方式 ===
    private Integer saltIntake;           // 盐摄入评分 1-5 (1低5高)
    private Integer exerciseFrequency;    // 每周运动次数
    private Integer alcoholIntake;        // 饮酒量评分 0-5

    private String dataSource;            // manual / paper_case / iot_import
    private String sourceVendor;          // e.g. huawei
    private String sourceDeviceType;      // e.g. blood_pressure_monitor
    private String sourceDeviceId;        // vendor device id
    private Long recoveryJobId;           // optional failed clinical job to close after manual recovery
}
