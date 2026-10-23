package com.medical.agent.service.chat;

import com.medical.agent.model.entity.Doctor;
import com.medical.agent.model.entity.FollowUpPlan;
import com.medical.agent.model.entity.HealthRecord;
import com.medical.agent.model.entity.Patient;
import com.medical.agent.model.entity.PatientMedicalHistory;
import com.medical.agent.model.entity.RiskAssessment;
import com.medical.agent.model.repository.DoctorRepository;
import com.medical.agent.model.repository.FollowUpPlanRepository;
import com.medical.agent.model.repository.HealthRecordRepository;
import com.medical.agent.model.repository.PatientMedicalHistoryRepository;
import com.medical.agent.model.repository.PatientRepository;
import com.medical.agent.model.repository.RiskAssessmentRepository;
import com.medical.agent.service.cache.AiCacheKeySupport;
import com.medical.agent.service.cache.CacheNames;
import com.medical.agent.service.support.PatientMedicalHistoryPromptSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.Period;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Service
public class ChatContextService {

    private static final Logger log = LoggerFactory.getLogger(ChatContextService.class);

    private final PatientRepository patientRepository;
    private final DoctorRepository doctorRepository;
    private final RiskAssessmentRepository assessmentRepository;
    private final FollowUpPlanRepository followUpPlanRepository;
    private final HealthRecordRepository healthRecordRepository;
    private final PatientMedicalHistoryRepository patientMedicalHistoryRepository;

    public ChatContextService(PatientRepository patientRepository,
                              DoctorRepository doctorRepository,
                              RiskAssessmentRepository assessmentRepository,
                              FollowUpPlanRepository followUpPlanRepository,
                              HealthRecordRepository healthRecordRepository,
                              PatientMedicalHistoryRepository patientMedicalHistoryRepository) {
        this.patientRepository = patientRepository;
        this.doctorRepository = doctorRepository;
        this.assessmentRepository = assessmentRepository;
        this.followUpPlanRepository = followUpPlanRepository;
        this.healthRecordRepository = healthRecordRepository;
        this.patientMedicalHistoryRepository = patientMedicalHistoryRepository;
    }

    @Cacheable(cacheNames = CacheNames.CHAT_PATIENT_CONTEXT,
            key = "@aiCacheKeyFactory.patientContext(#patientId, @aiChatCacheVersionService.patientContextVersion(#patientId))",
            sync = true)
    public ChatPatientContextData loadPatientContext(Long patientId) {
        try {
            if (patientId == null) {
                return null;
            }
            Optional<Patient> patientOpt = patientRepository.findById(patientId);
            if (patientOpt.isEmpty()) {
                return null;
            }

            Patient patient = patientOpt.get();
            List<RiskAssessment> assessments = assessmentRepository.findByPatientIdOrderByCreatedAtDesc(patientId);
            List<FollowUpPlan> plans = followUpPlanRepository.findByPatientIdOrderByCreatedAtDesc(patientId);
            HealthRecord latestRecord = healthRecordRepository.findFirstByPatientIdOrderByRecordDateDesc(patientId).orElse(null);
            PatientMedicalHistory medicalHistory = patientMedicalHistoryRepository.findByPatientId(patientId).orElse(null);
            String medicalHistorySummary = PatientMedicalHistoryPromptSupport.buildSummary(medicalHistory);
            List<String> diseaseKeys = buildDiseaseKeys(assessments, plans);
            List<String> activeConcernThemes = buildActiveConcernThemes(latestRecord, medicalHistorySummary, assessments);
            Map<String, Object> structuredContext = buildStructuredContext(patient, assessments, plans, latestRecord, medicalHistorySummary, diseaseKeys, activeConcernThemes);

            StringBuilder ctx = new StringBuilder();
            int age = patient.getBirthDate() != null
                    ? Period.between(patient.getBirthDate(), LocalDate.now()).getYears()
                    : 0;
            ctx.append("姓名：该患者")
                    .append("，性别：").append(patient.getGender())
                    .append("，年龄：").append(age).append("岁\n");

            if (!assessments.isEmpty()) {
                ctx.append("最近评估结果：\n");
                assessments.stream().limit(3).forEach(a -> {
                    if (a.getDiseaseType() == null || a.getRiskLevel() == null) {
                        return;
                    }
                    ctx.append("  - ").append(a.getDiseaseType().getDisplayName())
                            .append("：").append(a.getRiskLevel().getDisplayName())
                            .append("（评分：").append(a.getScore()).append("）\n");
                    if (a.getAssessmentDetail() != null) {
                        ctx.append("    详情：").append(a.getAssessmentDetail()).append("\n");
                    }
                });
            }

            if (!plans.isEmpty()) {
                ctx.append("当前随访计划：\n");
                plans.stream().limit(2).forEach(p -> {
                    if (p.getDiseaseType() == null || p.getFrequency() == null) {
                        return;
                    }
                    ctx.append("  - ").append(p.getDiseaseType().getDisplayName())
                            .append("：").append(p.getFrequency().getDisplayName())
                            .append("，下次随访：").append(p.getNextFollowDate()).append("\n");
                });
            }

            if (latestRecord != null) {
                ctx.append("最新健康记录：\n");
                appendMetric(ctx, "收缩压", latestRecord.getSystolicBp(), "mmHg");
                appendMetric(ctx, "舒张压", latestRecord.getDiastolicBp(), "mmHg");
                appendMetric(ctx, "空腹血糖", latestRecord.getFastingGlucose(), "mmol/L");
                appendMetric(ctx, "HbA1c", latestRecord.getHba1c(), "%");
                appendMetric(ctx, "BMI", latestRecord.getBmi(), "");
                appendMetric(ctx, "每周运动次数", latestRecord.getExerciseFrequency(), "次");
            }

            if (!medicalHistorySummary.isBlank()) {
                ctx.append("病史档案摘要：\n");
                for (String line : medicalHistorySummary.split("\\n")) {
                    ctx.append("  - ").append(line).append('\n');
                }
            }

            return new ChatPatientContextData(
                    patient,
                    assessments,
                    plans,
                    latestRecord,
                    medicalHistory,
                    medicalHistorySummary,
                    ctx.toString(),
                    diseaseKeys,
                    activeConcernThemes,
                    structuredContext
            );
        } catch (Exception e) {
            log.warn("[ChatContextService] Failed to build patient context: {}", e.getMessage());
            return null;
        }
    }

    public String buildConversationPerspective(String ownerRole,
                                               Long ownerUserId,
                                               Long boundPatientId,
                                               ChatPatientContextData patientContext) {
        if ("doctor".equalsIgnoreCase(ownerRole)) {
            String doctorName = resolveDoctorName(ownerUserId);
            StringBuilder sb = new StringBuilder();
            sb.append("【当前对话角色】\n");
            sb.append("当前提问者是医生");
            if (doctorName != null && !doctorName.isBlank()) {
                sb.append("（").append(doctorName).append("）");
            }
            sb.append("。\n");
            sb.append("请始终面向医生回答，不要把患者当成正在与你直接对话的人。\n");
            sb.append("若需要称呼，对医生使用");
            sb.append(doctorName != null && !doctorName.isBlank() ? doctorName : "医生");
            sb.append("，不要以上下文中的患者姓名开场问候。\n");
            if (boundPatientId != null) {
                sb.append("当前绑定的是一名已授权患者，讨论患者时请统一使用“该患者”的第三人称表达，不要输出患者实名。\n");
            } else {
                sb.append("当前为通用咨询模式，没有绑定具体患者，请给出面向基层医生的通用慢病管理建议。\n");
            }
            return sb.toString();
        }

        if ("patient".equalsIgnoreCase(ownerRole)) {
            String patientName = resolvePatientOwnerName(ownerUserId);
            StringBuilder sb = new StringBuilder();
            sb.append("【当前对话角色】\n");
            sb.append("当前提问者是患者");
            if (patientName != null && !patientName.isBlank()) {
                sb.append("（").append(patientName).append("）");
            }
            sb.append("。\n");
            sb.append("请面向患者本人回答，使用通俗表达。");
            return sb.toString();
        }

        return "【当前对话角色】\n当前提问者身份未明确，请使用中性、专业的表达。";
    }

    public String resolveDoctorName(Long doctorId) {
        if (doctorId == null) {
            return null;
        }
        return doctorRepository.findById(doctorId)
                .map(Doctor::getName)
                .orElse(null);
    }

    public String resolvePatientOwnerName(Long patientId) {
        if (patientId == null) {
            return null;
        }
        return patientRepository.findById(patientId)
                .map(Patient::getName)
                .orElse(null);
    }

    public String resolvePatientName(Long patientId) {
        return resolvePatientOwnerName(patientId);
    }

    private void appendMetric(StringBuilder ctx, String label, Object value, String unit) {
        if (value == null) {
            return;
        }
        ctx.append("  - ").append(label).append("：").append(value);
        if (unit != null && !unit.isBlank()) {
            ctx.append(' ').append(unit);
        }
        ctx.append('\n');
    }

    private List<String> buildDiseaseKeys(List<RiskAssessment> assessments, List<FollowUpPlan> plans) {
        LinkedHashSet<String> diseaseKeys = new LinkedHashSet<>();
        if (assessments != null) {
            assessments.stream()
                    .limit(4)
                    .map(RiskAssessment::getDiseaseType)
                    .filter(java.util.Objects::nonNull)
                    .map(type -> type.name().toLowerCase(Locale.ROOT))
                    .forEach(diseaseKeys::add);
        }
        if (plans != null) {
            plans.stream()
                    .limit(4)
                    .map(FollowUpPlan::getDiseaseType)
                    .filter(java.util.Objects::nonNull)
                    .map(type -> type.name().toLowerCase(Locale.ROOT))
                    .forEach(diseaseKeys::add);
        }
        return new ArrayList<>(diseaseKeys);
    }

    private List<String> buildActiveConcernThemes(HealthRecord latestRecord,
                                                  String medicalHistorySummary,
                                                  List<RiskAssessment> assessments) {
        LinkedHashSet<String> themes = new LinkedHashSet<>();
        if (latestRecord != null) {
            if (latestRecord.getSystolicBp() != null || latestRecord.getDiastolicBp() != null) {
                themes.add("metrics");
                themes.add("monitoring");
            }
            if (latestRecord.getFastingGlucose() != null || latestRecord.getHba1c() != null) {
                themes.add("metrics");
                themes.add("diet");
            }
            if (latestRecord.getExerciseFrequency() != null) {
                themes.add("exercise");
                themes.add("lifestyle");
            }
            if (latestRecord.getMedicationMissRate() != null && latestRecord.getMedicationMissRate() > 0D) {
                themes.add("medication");
            }
            if (latestRecord.getSaltIntake() != null && latestRecord.getSaltIntake() >= 3D) {
                themes.add("diet");
            }
        }
        if (medicalHistorySummary != null && !medicalHistorySummary.isBlank()) {
            String normalized = medicalHistorySummary.toLowerCase(Locale.ROOT);
            if (normalized.contains("过敏")) {
                themes.add("safety");
            }
            if (normalized.contains("手术")) {
                themes.add("followup");
            }
        }
        if (assessments != null) {
            assessments.stream()
                    .limit(3)
                    .filter(item -> item.getRiskLevel() != null)
                    .forEach(item -> {
                        themes.add("risk");
                        if ("HIGH".equalsIgnoreCase(item.getRiskLevel().name()) || "CRITICAL".equalsIgnoreCase(item.getRiskLevel().name())) {
                            themes.add("followup");
                            themes.add("safety");
                        }
                    });
        }
        if (themes.isEmpty()) {
            themes.add("general");
        }
        return new ArrayList<>(themes);
    }

    private Map<String, Object> buildStructuredContext(Patient patient,
                                                       List<RiskAssessment> assessments,
                                                       List<FollowUpPlan> plans,
                                                       HealthRecord latestRecord,
                                                       String medicalHistorySummary,
                                                       List<String> diseaseKeys,
                                                       List<String> activeConcernThemes) {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("patientId", patient == null ? null : patient.getId());
        context.put("gender", patient == null ? null : patient.getGender());
        context.put("age", patient != null && patient.getBirthDate() != null
                ? Period.between(patient.getBirthDate(), LocalDate.now()).getYears()
                : null);
        context.put("diseaseKeys", diseaseKeys);
        context.put("activeConcernThemes", activeConcernThemes);
        context.put("medicalHistorySummary", medicalHistorySummary);
        context.put("hasAssessments", assessments != null && !assessments.isEmpty());
        context.put("hasFollowUpPlan", plans != null && !plans.isEmpty());

        Map<String, Object> metrics = new LinkedHashMap<>();
        if (latestRecord != null) {
            metrics.put("systolicBp", latestRecord.getSystolicBp());
            metrics.put("diastolicBp", latestRecord.getDiastolicBp());
            metrics.put("fastingGlucose", latestRecord.getFastingGlucose());
            metrics.put("hba1c", latestRecord.getHba1c());
            metrics.put("bmi", latestRecord.getBmi());
            metrics.put("exerciseFrequency", latestRecord.getExerciseFrequency());
            metrics.put("saltIntake", latestRecord.getSaltIntake());
            metrics.put("medicationMissRate", latestRecord.getMedicationMissRate());
        }
        context.put("latestMetrics", metrics);
        return context;
    }
}
