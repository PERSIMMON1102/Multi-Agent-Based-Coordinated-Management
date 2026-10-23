package com.medical.agent.service.chat;

import com.medical.agent.common.fact.PatientHealthFact;
import com.medical.agent.common.enums.DiseaseType;
import com.medical.agent.common.enums.RiskLevel;
import com.medical.agent.common.util.PatientMedicalHistoryUtil;
import com.medical.agent.model.entity.HealthRecord;
import com.medical.agent.model.entity.Patient;
import com.medical.agent.model.entity.PatientMedicalHistory;
import com.medical.agent.model.entity.RiskAssessment;
import org.kie.api.runtime.KieContainer;
import org.kie.api.runtime.KieSession;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import com.medical.agent.service.cache.CacheNames;

import java.time.LocalDate;
import java.time.Period;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class RuleAssessmentService {

    private final KieContainer kieContainer;

    public RuleAssessmentService(KieContainer kieContainer) {
        this.kieContainer = kieContainer;
    }

    @Cacheable(cacheNames = CacheNames.CHAT_RULE_ASSESSMENT,
            key = "@aiCacheKeyFactory.assessment(#patientContext, #userQuestion, #preferredDiseaseType, @aiChatCacheVersionService.assessmentVersion(#patientContext))",
            sync = true)
    public List<RuleAssessmentResult> assess(ChatPatientContextData patientContext,
                                             String userQuestion,
                                             DiseaseType preferredDiseaseType) {
        if (patientContext == null || patientContext.patient() == null) {
            return List.of();
        }

        Set<DiseaseType> diseaseTypes = determineDiseaseTypes(patientContext, userQuestion, preferredDiseaseType);
        if (diseaseTypes.isEmpty()) {
            return fallbackFromStoredAssessments(patientContext, preferredDiseaseType);
        }

        PatientHealthFact baseFact = buildFact(patientContext.patient(), patientContext.latestRecord());
        if (baseFact == null) {
            return fallbackFromStoredAssessments(patientContext, preferredDiseaseType);
        }

        List<RuleAssessmentResult> results = new ArrayList<>();
        for (DiseaseType diseaseType : diseaseTypes) {
            PatientHealthFact fact = cloneFact(baseFact);
            fact.setAssessedDiseaseType(diseaseType);
            KieSession session = kieContainer.newKieSession();
            try {
                session.insert(fact);
                session.fireAllRules(100);
            } finally {
                session.dispose();
            }
            if (fact.getRiskLevel() != null) {
                results.add(enrichWithMedicalHistory(patientContext, new RuleAssessmentResult(
                        diseaseType,
                        fact.getRiskLevel(),
                        fact.getRiskScore(),
                        fact.getAssessmentDetail(),
                        fact.getRecommendations() == null ? List.of() : List.copyOf(fact.getRecommendations()),
                        "drools"
                )));
            }
        }

        if (results.isEmpty()) {
            return fallbackFromStoredAssessments(patientContext, preferredDiseaseType);
        }

        return results.stream()
                .sorted(Comparator.comparingInt((RuleAssessmentResult item) -> item.riskLevel().getLevel()).reversed())
                .toList();
    }

    private Set<DiseaseType> determineDiseaseTypes(ChatPatientContextData patientContext,
                                                   String userQuestion,
                                                   DiseaseType preferredDiseaseType) {
        LinkedHashSet<DiseaseType> types = new LinkedHashSet<>();
        if (preferredDiseaseType != null) {
            types.add(preferredDiseaseType);
            return types;
        }

        String normalized = userQuestion == null ? "" : userQuestion.toLowerCase(Locale.ROOT);
        if (normalized.contains("血压") || normalized.contains("降压")) {
            types.add(DiseaseType.HYPERTENSION);
        }
        if (normalized.contains("血糖") || normalized.contains("糖化") || normalized.contains("胰岛素") || normalized.contains("糖尿病")) {
            types.add(DiseaseType.DIABETES);
        }
        if (normalized.contains("呼吸") || normalized.contains("copd") || normalized.contains("咳") || normalized.contains("痰")) {
            types.add(DiseaseType.COPD);
        }
        if (normalized.contains("肾") || normalized.contains("肌酐") || normalized.contains("尿蛋白")) {
            types.add(DiseaseType.CKD);
        }
        if (normalized.contains("冠心病") || normalized.contains("胸痛") || normalized.contains("他汀") || normalized.contains("心绞痛")) {
            types.add(DiseaseType.CHD);
        }
        if (normalized.contains("卒中") || normalized.contains("中风") || normalized.contains("脑梗")) {
            types.add(DiseaseType.STROKE);
        }
        if (patientContext.medicalHistory() != null) {
            types.addAll(PatientMedicalHistoryUtil.detectDiseaseTypes(
                    patientContext.medicalHistory().getPastDiseases(),
                    patientContext.medicalHistorySummary()
            ));
        }

        if (patientContext.assessments() != null) {
            patientContext.assessments().stream()
                    .limit(3)
                    .map(RiskAssessment::getDiseaseType)
                    .filter(item -> item != null)
                    .forEach(types::add);
        }

        if (types.isEmpty()) {
            HealthRecord latestRecord = patientContext.latestRecord();
            if (latestRecord != null) {
                if (latestRecord.getSystolicBp() != null || latestRecord.getDiastolicBp() != null) {
                    types.add(DiseaseType.HYPERTENSION);
                }
                if (latestRecord.getFastingGlucose() != null || latestRecord.getHba1c() != null) {
                    types.add(DiseaseType.DIABETES);
                }
                if (latestRecord.getSerumCreatinine() != null || latestRecord.getEgfr() != null || latestRecord.getUacr() != null) {
                    types.add(DiseaseType.CKD);
                }
                if (latestRecord.getLdlCholesterol() != null || latestRecord.getTotalCholesterol() != null) {
                    types.add(DiseaseType.CHD);
                }
            }
        }

        return types.stream().limit(3).collect(LinkedHashSet::new, LinkedHashSet::add, LinkedHashSet::addAll);
    }

    private PatientHealthFact buildFact(Patient patient, HealthRecord latestRecord) {
        if (patient == null) {
            return null;
        }
        Integer age = patient.getBirthDate() != null
                ? Period.between(patient.getBirthDate(), LocalDate.now()).getYears()
                : null;

        PatientHealthFact.PatientHealthFactBuilder builder = PatientHealthFact.builder()
                .patientId(patient.getId())
                .patientName(patient.getName())
                .age(age);

        if (latestRecord != null) {
            builder.systolicBp(latestRecord.getSystolicBp());
            builder.diastolicBp(latestRecord.getDiastolicBp());
            builder.bmi(latestRecord.getBmi());
            builder.hemoglobin(toInteger(latestRecord.getHemoglobin()));
            builder.creatinine(latestRecord.getSerumCreatinine());
            builder.totalCholesterol(latestRecord.getTotalCholesterol());
            builder.triglyceride(latestRecord.getTriglycerides());
            builder.ldlCholesterol(latestRecord.getLdlCholesterol());
            builder.hdlCholesterol(latestRecord.getHdlCholesterol());
            builder.fastingGlucose(latestRecord.getFastingGlucose());
            builder.hba1c(latestRecord.getHba1c());
        }

        return builder.build();
    }

    private PatientHealthFact cloneFact(PatientHealthFact source) {
        return PatientHealthFact.builder()
                .patientId(source.getPatientId())
                .patientName(source.getPatientName())
                .age(source.getAge())
                .systolicBp(source.getSystolicBp())
                .diastolicBp(source.getDiastolicBp())
                .bmi(source.getBmi())
                .hemoglobin(source.getHemoglobin())
                .wbc(source.getWbc())
                .platelet(source.getPlatelet())
                .neutrophilPct(source.getNeutrophilPct())
                .alt(source.getAlt())
                .ast(source.getAst())
                .creatinine(source.getCreatinine())
                .uricAcid(source.getUricAcid())
                .totalCholesterol(source.getTotalCholesterol())
                .triglyceride(source.getTriglyceride())
                .ldlCholesterol(source.getLdlCholesterol())
                .hdlCholesterol(source.getHdlCholesterol())
                .fastingGlucose(source.getFastingGlucose())
                .hba1c(source.getHba1c())
                .ck(source.getCk())
                .potassium(source.getPotassium())
                .urineProtein(source.getUrineProtein())
                .urineGlucose(source.getUrineGlucose())
                .urineBlood(source.getUrineBlood())
                .recommendations(new ArrayList<>())
                .build();
    }

    private List<RuleAssessmentResult> fallbackFromStoredAssessments(ChatPatientContextData patientContext,
                                                                     DiseaseType preferredDiseaseType) {
        if (patientContext.assessments() == null || patientContext.assessments().isEmpty()) {
            return List.of();
        }
        return patientContext.assessments().stream()
                .filter(item -> item.getDiseaseType() != null && item.getRiskLevel() != null)
                .filter(item -> preferredDiseaseType == null || preferredDiseaseType == item.getDiseaseType())
                .limit(3)
                .map(item -> enrichWithMedicalHistory(patientContext, new RuleAssessmentResult(
                        item.getDiseaseType(),
                        item.getRiskLevel(),
                        item.getScore(),
                        item.getAssessmentDetail(),
                        splitRecommendations(item.getRecommendations()),
                        "stored_assessment"
                )))
                .toList();
    }

    private RuleAssessmentResult enrichWithMedicalHistory(ChatPatientContextData patientContext,
                                                          RuleAssessmentResult result) {
        if (patientContext == null || result == null || patientContext.medicalHistory() == null) {
            return result;
        }

        PatientMedicalHistory history = patientContext.medicalHistory();
        List<String> recommendations = new ArrayList<>(result.recommendations() == null ? List.of() : result.recommendations());
        List<String> notes = new ArrayList<>();

        if (PatientMedicalHistoryUtil.hasDiseaseHistory(
                result.diseaseType(),
                history.getPastDiseases(),
                patientContext.medicalHistorySummary()
        )) {
            notes.add("既往" + result.diseaseType().getDisplayName() + "病史");
            addRecommendationIfAbsent(recommendations,
                    "结合既往" + result.diseaseType().getDisplayName() + "病史，建议继续按长期管理方案复核指标与治疗依从性。");
        }

        if (history.getDrugAllergies() != null && !history.getDrugAllergies().isBlank()) {
            notes.add("药物过敏史：" + shorten(history.getDrugAllergies(), 40));
            addRecommendationIfAbsent(recommendations,
                    "已记录药物过敏史，调整或新增药物前请先核对：" + shorten(history.getDrugAllergies(), 40));
        }

        if (history.getFoodAllergies() != null && !history.getFoodAllergies().isBlank()) {
            addRecommendationIfAbsent(recommendations,
                    "已记录食物过敏史，制定饮食建议前请先核对：" + shorten(history.getFoodAllergies(), 40));
        }

        String detail = result.detail();
        if (!notes.isEmpty()) {
            String historyNote = "病史提示：" + String.join("；", notes);
            detail = (detail == null || detail.isBlank()) ? historyNote : detail + "\n" + historyNote;
        }

        return new RuleAssessmentResult(
                result.diseaseType(),
                result.riskLevel(),
                result.score(),
                detail,
                List.copyOf(recommendations),
                result.source()
        );
    }

    private List<String> splitRecommendations(String recommendations) {
        if (recommendations == null || recommendations.isBlank()) {
            return List.of();
        }
        return java.util.Arrays.stream(recommendations.split("[;；\\n]"))
                .map(String::trim)
                .filter(item -> !item.isBlank())
                .toList();
    }

    private Integer toInteger(Double value) {
        return value == null ? null : value.intValue();
    }

    private void addRecommendationIfAbsent(List<String> recommendations, String recommendation) {
        if (recommendations == null || recommendation == null || recommendation.isBlank()) {
            return;
        }
        boolean exists = recommendations.stream().anyMatch(item -> recommendation.equalsIgnoreCase(String.valueOf(item)));
        if (!exists) {
            recommendations.add(recommendation);
        }
    }

    private String shorten(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String normalized = value.replaceAll("[\\r\\n]+", "；").trim();
        if (normalized.length() <= maxLength) {
            return normalized;
        }
        return normalized.substring(0, maxLength) + "...";
    }
}
