package com.medical.agent.service.chat;

import com.medical.agent.model.entity.FollowUpPlan;
import com.medical.agent.model.entity.HealthRecord;
import com.medical.agent.model.entity.Patient;
import com.medical.agent.model.entity.PatientMedicalHistory;
import com.medical.agent.model.entity.RiskAssessment;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public record ChatPatientContextData(Patient patient,
                                     List<RiskAssessment> assessments,
                                     List<FollowUpPlan> plans,
                                     HealthRecord latestRecord,
                                     PatientMedicalHistory medicalHistory,
                                     String medicalHistorySummary,
                                     String promptContext,
                                     List<String> diseaseKeys,
                                     List<String> activeConcernThemes,
                                     Map<String, Object> structuredContext) {

    public String focusedPromptContext(String userQuestion) {
        String base = promptContext == null ? "" : promptContext.trim();
        if (base.isBlank()) {
            return "";
        }
        List<String> themes = inferQueryThemes(userQuestion);
        if (themes.isEmpty() || themes.contains("general")) {
            return base;
        }

        StringBuilder sb = new StringBuilder();
        appendLine(sb, buildPatientSummary());
        if (themes.contains("metrics") || themes.contains("monitoring")) {
            appendLine(sb, buildMetricsSummary());
        }
        if (themes.contains("risk")) {
            appendLine(sb, buildAssessmentSummary());
        }
        if (themes.contains("followup")) {
            appendLine(sb, buildPlanSummary());
        }
        if (themes.contains("medication") || themes.contains("safety")) {
            appendLine(sb, buildMedicalHistoryFocus());
        }
        if (themes.contains("diet") || themes.contains("exercise") || themes.contains("lifestyle")) {
            appendLine(sb, buildLifestyleSummary());
        }
        String focused = sb.toString().trim();
        return focused.isBlank() ? base : focused;
    }

    public List<String> inferQueryThemes(String userQuestion) {
        LinkedHashSet<String> themes = new LinkedHashSet<>();
        String text = userQuestion == null ? "" : userQuestion.toLowerCase(Locale.ROOT);
        if (containsAny(text, "血压", "血糖", "糖化", "指标", "化验", "bmi")) {
            themes.add("metrics");
        }
        if (containsAny(text, "监测", "测量", "记录")) {
            themes.add("monitoring");
        }
        if (containsAny(text, "风险", "危险", "严重", "急诊", "就医", "胸痛", "气短", "头晕")) {
            themes.add("risk");
        }
        if (containsAny(text, "随访", "复查", "复诊", "下次")) {
            themes.add("followup");
        }
        if (containsAny(text, "药", "服药", "用药", "漏服", "依从")) {
            themes.add("medication");
        }
        if (containsAny(text, "过敏", "禁忌", "手术")) {
            themes.add("safety");
        }
        if (containsAny(text, "饮食", "吃", "主食", "控盐", "低糖")) {
            themes.add("diet");
        }
        if (containsAny(text, "运动", "锻炼", "走路", "活动")) {
            themes.add("exercise");
        }
        if (containsAny(text, "作息", "生活方式", "睡眠", "情绪")) {
            themes.add("lifestyle");
        }
        if (themes.isEmpty() && activeConcernThemes != null) {
            themes.addAll(activeConcernThemes);
        }
        if (themes.isEmpty()) {
            themes.add("general");
        }
        return new ArrayList<>(themes);
    }

    private String buildPatientSummary() {
        if (patient == null) {
            return "";
        }
        List<String> parts = new ArrayList<>();
        parts.add("患者概况：该患者");
        if (patient.getGender() != null && !patient.getGender().isBlank()) {
            parts.add("性别 " + patient.getGender());
        }
        Object age = structuredContext == null ? null : structuredContext.get("age");
        if (age != null) {
            parts.add("年龄 " + age + " 岁");
        }
        if (diseaseKeys != null && !diseaseKeys.isEmpty()) {
            parts.add("相关病种 " + String.join("、", diseaseKeys));
        }
        return String.join("，", parts);
    }

    private String buildMetricsSummary() {
        if (latestRecord == null) {
            return "";
        }
        List<String> parts = new ArrayList<>();
        addMetric(parts, "收缩压", latestRecord.getSystolicBp(), "mmHg");
        addMetric(parts, "舒张压", latestRecord.getDiastolicBp(), "mmHg");
        addMetric(parts, "空腹血糖", latestRecord.getFastingGlucose(), "mmol/L");
        addMetric(parts, "HbA1c", latestRecord.getHba1c(), "%");
        addMetric(parts, "BMI", latestRecord.getBmi(), "");
        return parts.isEmpty() ? "" : "关键指标：" + String.join("；", parts);
    }

    private String buildAssessmentSummary() {
        if (assessments == null || assessments.isEmpty()) {
            return "";
        }
        List<String> parts = assessments.stream()
                .filter(Objects::nonNull)
                .limit(3)
                .map(item -> {
                    if (item.getDiseaseType() == null || item.getRiskLevel() == null) {
                        return null;
                    }
                    return item.getDiseaseType().getDisplayName() + " " + item.getRiskLevel().getDisplayName()
                            + (item.getScore() == null ? "" : "（" + item.getScore() + " 分）");
                })
                .filter(Objects::nonNull)
                .toList();
        return parts.isEmpty() ? "" : "最近评估：" + String.join("；", parts);
    }

    private String buildPlanSummary() {
        if (plans == null || plans.isEmpty()) {
            return "";
        }
        List<String> parts = plans.stream()
                .filter(Objects::nonNull)
                .limit(2)
                .map(item -> {
                    StringBuilder sb = new StringBuilder();
                    if (item.getDiseaseType() != null) {
                        sb.append(item.getDiseaseType().getDisplayName());
                    }
                    if (item.getFrequency() != null) {
                        if (sb.length() > 0) {
                            sb.append(" / ");
                        }
                        sb.append(item.getFrequency().getDisplayName());
                    }
                    if (item.getNextFollowDate() != null) {
                        sb.append(" / 下次 ").append(item.getNextFollowDate());
                    }
                    return sb.toString();
                })
                .filter(value -> value != null && !value.isBlank())
                .toList();
        return parts.isEmpty() ? "" : "随访计划：" + String.join("；", parts);
    }

    private String buildMedicalHistoryFocus() {
        if (medicalHistorySummary == null || medicalHistorySummary.isBlank()) {
            return "";
        }
        List<String> lines = new ArrayList<>();
        for (String line : medicalHistorySummary.split("\\n")) {
            String normalized = line == null ? "" : line.trim();
            if (normalized.isBlank()) {
                continue;
            }
            if (containsAny(normalized.toLowerCase(Locale.ROOT), "过敏", "手术", "既往", "家族史", "备注")) {
                lines.add(normalized);
            }
            if (lines.size() >= 3) {
                break;
            }
        }
        if (lines.isEmpty()) {
            return "";
        }
        return "病史重点：" + String.join("；", lines);
    }

    private String buildLifestyleSummary() {
        if (latestRecord == null) {
            return "";
        }
        List<String> parts = new ArrayList<>();
        addMetric(parts, "每周运动次数", latestRecord.getExerciseFrequency(), "次");
        addMetric(parts, "盐摄入评分", latestRecord.getSaltIntake(), "");
        addMetric(parts, "漏服率", latestRecord.getMedicationMissRate(), "%");
        return parts.isEmpty() ? "" : "生活方式相关：" + String.join("；", parts);
    }

    private void addMetric(List<String> parts, String label, Object value, String unit) {
        if (value == null) {
            return;
        }
        parts.add(label + " " + value + (unit == null || unit.isBlank() ? "" : " " + unit));
    }

    private void appendLine(StringBuilder sb, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        if (sb.length() > 0) {
            sb.append('\n');
        }
        sb.append(value.trim());
    }

    private boolean containsAny(String text, String... keywords) {
        if (text == null || text.isBlank() || keywords == null) {
            return false;
        }
        for (String keyword : keywords) {
            if (keyword != null && !keyword.isBlank() && text.contains(keyword.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }
}
