package com.medical.agent.common.fact;

import com.medical.agent.common.enums.DiseaseType;
import com.medical.agent.common.enums.RiskLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Drools规则引擎的共享事实对象，承载患者健康数据和评估结果。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PatientHealthFact {
    private Long patientId;
    private String patientName;
    private Integer age;

    private Integer systolicBp;
    private Integer diastolicBp;
    private Double bmi;

    private Integer hemoglobin;
    private Double wbc;
    private Integer platelet;
    private Integer neutrophilPct;

    private Integer alt;
    private Integer ast;

    private Double creatinine;
    private Double uricAcid;

    private Double totalCholesterol;
    private Double triglyceride;
    private Double hdlCholesterol;
    private Double ldlCholesterol;

    private Double fastingGlucose;
    private Double hba1c;

    private Integer ck;
    private Double potassium;

    private String urineProtein;
    private String urineGlucose;
    private String urineBlood;

    private DiseaseType assessedDiseaseType;
    private RiskLevel riskLevel;
    private Double riskScore;
    private String assessmentDetail;
    @Builder.Default
    private List<String> recommendations = new ArrayList<>();

    public void addRecommendation(String rec) {
        if (recommendations == null) {
            recommendations = new ArrayList<>();
        }
        recommendations.add(rec);
    }
}
