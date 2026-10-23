package com.medical.agent.service.report.agent;

import com.medical.agent.model.entity.FollowUpPlan;
import com.medical.agent.model.entity.HealthRecord;
import com.medical.agent.model.entity.Patient;
import com.medical.agent.model.entity.RiskAssessment;
import com.medical.agent.service.HealthReportService;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class HealthReportPromptSupport {

    private static final String SECTION_PURPOSE = "## 报告用途";
    private static final String SECTION_RISK = "## 一、风险分层结论";
    private static final String SECTION_EVIDENCE = "## 二、关键依据";
    private static final String SECTION_ISSUES = "## 三、当前重点问题";
    private static final String SECTION_ACTIONS = "## 四、医生干预建议";
    private static final String SECTION_FOLLOW_UP = "## 五、随访建议";
    private static final String SECTION_CONCLUSION = "## 六、结论";

    private static final Pattern HEADING_PATTERN = Pattern.compile("(?m)^(#{2,4})\\s*(.+?)\\s*$");

    private HealthReportPromptSupport() {
    }

    static String buildContext(Patient patient,
                               String medicalHistorySummary,
                               List<RiskAssessment> assessments,
                               List<FollowUpPlan> plans,
                               HealthRecord latestRecord) {
        StringBuilder sb = new StringBuilder();
        sb.append("患者姓名：").append(patient.getName()).append('\n');
        if (patient.getGender() != null) sb.append("性别：").append(patient.getGender()).append('\n');
        if (patient.getBirthDate() != null) sb.append("出生日期：").append(patient.getBirthDate()).append('\n');
        if (patient.getPhone() != null) sb.append("联系电话：").append(patient.getPhone()).append('\n');
        if (patient.getAddress() != null) sb.append("住址：").append(patient.getAddress()).append('\n');
        if (medicalHistorySummary != null && !medicalHistorySummary.isBlank()) {
            sb.append("病史档案：\n");
            for (String line : medicalHistorySummary.split("\\n")) {
                sb.append("- ").append(line).append('\n');
            }
        }

        sb.append("\n最近风险评估：\n");
        if (assessments.isEmpty()) {
            sb.append("- 暂无风险评估记录\n");
        } else {
            assessments.stream().limit(5).forEach(item -> {
                sb.append("- ")
                        .append(item.getDiseaseType().getDisplayName())
                        .append(" / ")
                        .append(item.getRiskLevel().getDisplayName());
                if (item.getScore() != null) {
                    sb.append(" / 评分 ").append(String.format("%.1f", item.getScore()));
                }
                sb.append('\n');
                if (item.getAssessmentDetail() != null && !item.getAssessmentDetail().isBlank()) {
                    sb.append("  详情：").append(item.getAssessmentDetail()).append('\n');
                }
            });
        }

        sb.append("\n随访计划：\n");
        if (plans.isEmpty()) {
            sb.append("- 暂无随访计划\n");
        } else {
            plans.stream().limit(3).forEach(plan -> sb.append("- ")
                    .append(plan.getDiseaseType().getDisplayName())
                    .append(" / ").append(plan.getStatus().getDisplayName())
                    .append(" / 下次随访 ").append(plan.getNextFollowDate())
                    .append('\n'));
        }

        sb.append("\n最新健康记录：\n");
        if (latestRecord == null) {
            sb.append("- 暂无健康记录\n");
        } else {
            sb.append("- 记录日期：").append(latestRecord.getRecordDate()).append('\n');
            appendMetric(sb, "收缩压", latestRecord.getSystolicBp(), "mmHg");
            appendMetric(sb, "舒张压", latestRecord.getDiastolicBp(), "mmHg");
            appendMetric(sb, "静息心率", latestRecord.getRestingHeartRate(), "次/分");
            appendMetric(sb, "体重", latestRecord.getWeight(), "kg");
            appendMetric(sb, "BMI", latestRecord.getBmi(), "");
            appendMetric(sb, "空腹血糖", latestRecord.getFastingGlucose(), "mmol/L");
            appendMetric(sb, "HbA1c", latestRecord.getHba1c(), "%");
            appendMetric(sb, "总胆固醇", latestRecord.getTotalCholesterol(), "mmol/L");
            appendMetric(sb, "LDL-C", latestRecord.getLdlCholesterol(), "mmol/L");
            appendMetric(sb, "HDL-C", latestRecord.getHdlCholesterol(), "mmol/L");
            appendMetric(sb, "血红蛋白", latestRecord.getHemoglobin(), "g/L");
            appendMetric(sb, "血肌酐", latestRecord.getSerumCreatinine(), "μmol/L");
        }
        return sb.toString();
    }

    static String buildFallbackReport(String type,
                                      Patient patient,
                                      String medicalHistorySummary,
                                      List<RiskAssessment> assessments,
                                      List<FollowUpPlan> plans,
                                      HealthRecord latestRecord) {
        StringBuilder sb = new StringBuilder();
        if (HealthReportService.TYPE_PATIENT_WEEKLY.equals(type)) {
            sb.append("## 本周健康概览\n");
            sb.append("- 患者：").append(patient.getName()).append('\n');
            if (medicalHistorySummary != null && !medicalHistorySummary.isBlank()) {
                sb.append("- 重要病史：").append(medicalHistorySummary.replace('\n', '；')).append('\n');
            }
            if (latestRecord != null) {
                if (latestRecord.getSystolicBp() != null || latestRecord.getDiastolicBp() != null) {
                    sb.append("- 最近血压：")
                            .append(latestRecord.getSystolicBp() != null ? latestRecord.getSystolicBp() : "--")
                            .append('/')
                            .append(latestRecord.getDiastolicBp() != null ? latestRecord.getDiastolicBp() : "--")
                            .append(" mmHg\n");
                }
                if (latestRecord.getFastingGlucose() != null) {
                    sb.append("- 最近空腹血糖：").append(latestRecord.getFastingGlucose()).append(" mmol/L\n");
                }
                if (latestRecord.getWeight() != null) {
                    sb.append("- 最近体重：").append(latestRecord.getWeight()).append(" kg\n");
                }
            }
            sb.append("\n## 主要风险提醒\n");
            if (assessments.isEmpty()) {
                sb.append("- 暂无近期风险评估，请按时录入健康数据并联系医生评估。\n");
            } else {
                assessments.stream().limit(3).forEach(item -> sb.append("- ")
                        .append(item.getDiseaseType().getDisplayName())
                        .append("：")
                        .append(item.getRiskLevel().getDisplayName())
                        .append('\n'));
            }
            sb.append("\n## 本周建议\n");
            sb.append("- 继续按时记录血压、血糖、体重等关键指标。\n");
            sb.append("- 规律服药，若出现明显不适或指标持续异常，请尽快联系医生。\n");
            sb.append("- 保持低盐饮食、适量运动和充足睡眠。\n");
            sb.append("\n## 随访提醒\n");
            if (plans.isEmpty()) {
                sb.append("- 当前暂无已生成的随访计划，建议与医生确认复诊安排。\n");
            } else {
                plans.stream().limit(2).forEach(plan -> sb.append("- ")
                        .append(plan.getDiseaseType().getDisplayName())
                        .append("：下次随访日期 ")
                        .append(plan.getNextFollowDate())
                        .append('\n'));
            }
        } else {
            sb.append(buildDoctorAssessmentFallback(
                    patient,
                    medicalHistorySummary,
                    assessments,
                    plans,
                    latestRecord
            ));
        }
        return sb.toString();
    }

    static String normalizeDoctorAssessmentReport(String raw,
                                                  Patient patient,
                                                  String medicalHistorySummary,
                                                  List<RiskAssessment> assessments,
                                                  List<FollowUpPlan> plans,
                                                  HealthRecord latestRecord) {
        String fallback = buildDoctorAssessmentFallback(patient, medicalHistorySummary, assessments, plans, latestRecord);
        if (raw == null || raw.isBlank()) {
            return fallback;
        }

        String sanitized = sanitizeDoctorReportRawText(raw);
        Map<String, String> fallbackSections = splitSections(fallback);
        Map<String, String> extractedSections = extractDoctorAssessmentSections(sanitized);

        StringBuilder sb = new StringBuilder();
        appendSection(sb, SECTION_PURPOSE, fallbackSections.get(SECTION_PURPOSE));
        appendSection(sb, SECTION_RISK, preferSection(extractedSections.get(SECTION_RISK), fallbackSections.get(SECTION_RISK)));
        appendSection(sb, SECTION_EVIDENCE, preferSection(extractedSections.get(SECTION_EVIDENCE), fallbackSections.get(SECTION_EVIDENCE)));
        appendSection(sb, SECTION_ISSUES, preferSection(extractedSections.get(SECTION_ISSUES), fallbackSections.get(SECTION_ISSUES)));
        appendSection(sb, SECTION_ACTIONS, preferSection(extractedSections.get(SECTION_ACTIONS), fallbackSections.get(SECTION_ACTIONS)));
        appendSection(sb, SECTION_FOLLOW_UP, preferSection(extractedSections.get(SECTION_FOLLOW_UP), fallbackSections.get(SECTION_FOLLOW_UP)));
        appendSection(sb, SECTION_CONCLUSION, preferSection(extractedSections.get(SECTION_CONCLUSION), fallbackSections.get(SECTION_CONCLUSION)));
        return sb.toString().trim();
    }

    private static String buildDoctorAssessmentFallback(Patient patient,
                                                        String medicalHistorySummary,
                                                        List<RiskAssessment> assessments,
                                                        List<FollowUpPlan> plans,
                                                        HealthRecord latestRecord) {
        StringBuilder sb = new StringBuilder();
        appendSection(sb, SECTION_PURPOSE, String.join("\n",
                "- 供医生快速复核当前风险状态、干预重点和后续随访安排。",
                "- 作为本次医生干预动作的留痕记录，便于后续回看与沟通。"));
        appendSection(sb, SECTION_RISK, buildRiskSection(assessments));
        appendSection(sb, SECTION_EVIDENCE, buildEvidenceSection(medicalHistorySummary, latestRecord));
        appendSection(sb, SECTION_ISSUES, buildIssuesSection(assessments));
        appendSection(sb, SECTION_ACTIONS, String.join("\n",
                "- 优先围绕中高风险病种确认当前治疗方案是否需要调整。",
                "- 重点核对患者用药依从性、生活方式执行情况和近期异常指标变化。",
                "- 如关键指标持续异常或症状加重，建议尽快安排复诊或线下检查。"));
        appendSection(sb, SECTION_FOLLOW_UP, buildFollowUpSection(plans));
        appendSection(sb, SECTION_CONCLUSION, buildConclusionSection(assessments));
        return sb.toString().trim();
    }

    private static String buildRiskSection(List<RiskAssessment> assessments) {
        if (assessments == null || assessments.isEmpty()) {
            return "- 暂无规则评估记录，建议补充健康数据后重新评估。";
        }
        StringBuilder sb = new StringBuilder();
        assessments.stream().limit(4).forEach(item -> {
            sb.append("- ")
                    .append(item.getDiseaseType().getDisplayName())
                    .append("：")
                    .append(item.getRiskLevel().getDisplayName());
            if (item.getScore() != null) {
                sb.append("（评分 ").append(String.format("%.1f", item.getScore())).append("）");
            }
            sb.append('\n');
        });
        return sb.toString().trim();
    }

    private static String buildEvidenceSection(String medicalHistorySummary, HealthRecord latestRecord) {
        List<String> evidenceItems = new ArrayList<>();
        if (medicalHistorySummary != null && !medicalHistorySummary.isBlank()) {
            for (String line : medicalHistorySummary.split("\\n")) {
                if (line != null && !line.isBlank()) {
                    evidenceItems.add(line.trim());
                }
            }
        }
        if (latestRecord != null) {
            if (latestRecord.getSystolicBp() != null || latestRecord.getDiastolicBp() != null) {
                evidenceItems.add("最近血压："
                        + (latestRecord.getSystolicBp() != null ? latestRecord.getSystolicBp() : "--")
                        + "/"
                        + (latestRecord.getDiastolicBp() != null ? latestRecord.getDiastolicBp() : "--")
                        + " mmHg");
            }
            if (latestRecord.getFastingGlucose() != null) {
                evidenceItems.add("最近空腹血糖：" + latestRecord.getFastingGlucose() + " mmol/L");
            }
            if (latestRecord.getHba1c() != null) {
                evidenceItems.add("最近 HbA1c：" + latestRecord.getHba1c() + " %");
            }
            if (latestRecord.getSerumCreatinine() != null) {
                evidenceItems.add("最近血肌酐：" + latestRecord.getSerumCreatinine() + " μmol/L");
            }
            if (latestRecord.getBmi() != null) {
                evidenceItems.add("最近 BMI：" + latestRecord.getBmi());
            }
        }
        if (evidenceItems.isEmpty()) {
            return "- 暂无足够依据，建议先补充病史和近期指标。";
        }
        return evidenceItems.stream()
                .limit(6)
                .map(item -> "- " + item)
                .collect(Collectors.joining("\n"));
    }

    private static String buildIssuesSection(List<RiskAssessment> assessments) {
        if (assessments == null || assessments.isEmpty()) {
            return "- 目前缺少可用于医生判断的风险评估结果。";
        }
        List<String> assessmentDetails = assessments.stream()
                .map(RiskAssessment::getAssessmentDetail)
                .filter(detail -> detail != null && !detail.isBlank())
                .limit(3)
                .toList();
        if (assessmentDetails.isEmpty()) {
            return "- 当前重点问题待医生结合原始评估进一步确认。";
        }
        return assessmentDetails.stream()
                .map(detail -> "- " + detail.trim())
                .collect(Collectors.joining("\n"));
    }

    private static String buildFollowUpSection(List<FollowUpPlan> plans) {
        if (plans == null || plans.isEmpty()) {
            return "- 当前尚未看到明确随访计划，建议补齐随访频次、下次时间和检查项目。";
        }
        StringBuilder sb = new StringBuilder();
        plans.stream().limit(3).forEach(plan -> {
            sb.append("- ")
                    .append(plan.getDiseaseType().getDisplayName())
                    .append("：")
                    .append(plan.getStatus().getDisplayName());
            if (plan.getNextFollowDate() != null) {
                sb.append("，下次随访 ").append(plan.getNextFollowDate());
            }
            if (plan.getCheckItems() != null && !plan.getCheckItems().isBlank()) {
                sb.append("，建议关注 ").append(normalizeCheckItems(plan.getCheckItems()));
            }
            sb.append('\n');
        });
        return sb.toString().trim();
    }

    private static String buildConclusionSection(List<RiskAssessment> assessments) {
        if (assessments == null || assessments.isEmpty()) {
            return "- 建议先补录数据并完成风险评估，再生成正式医生报告。";
        }
        return "- 当前报告已覆盖风险判断、关键依据、干预建议和随访建议，可作为本次医生干预记录。";
    }

    private static Map<String, String> splitSections(String report) {
        Map<String, String> sections = new LinkedHashMap<>();
        String currentHeading = null;
        StringBuilder currentBody = new StringBuilder();
        for (String line : report.split("\\R")) {
            if (line.startsWith("## ")) {
                if (currentHeading != null) {
                    sections.put(currentHeading, currentBody.toString().trim());
                }
                currentHeading = line.trim();
                currentBody = new StringBuilder();
            } else if (currentHeading != null) {
                if (currentBody.length() > 0) {
                    currentBody.append('\n');
                }
                currentBody.append(line);
            }
        }
        if (currentHeading != null) {
            sections.put(currentHeading, currentBody.toString().trim());
        }
        return sections;
    }

    private static Map<String, String> extractDoctorAssessmentSections(String raw) {
        Map<String, String> sections = new LinkedHashMap<>();
        Matcher matcher = HEADING_PATTERN.matcher(raw);
        List<int[]> headingRanges = new ArrayList<>();
        List<String> headingTitles = new ArrayList<>();
        while (matcher.find()) {
            headingRanges.add(new int[]{matcher.start(), matcher.end()});
            headingTitles.add(matcher.group(2).trim());
        }
        for (int i = 0; i < headingTitles.size(); i++) {
            String heading = headingTitles.get(i);
            int bodyStart = headingRanges.get(i)[1];
            int bodyEnd = i + 1 < headingTitles.size() ? headingRanges.get(i + 1)[0] : raw.length();
            String body = raw.substring(bodyStart, bodyEnd).trim();
            String canonicalHeading = resolveDoctorAssessmentHeading(heading);
            if (canonicalHeading == null || body.isBlank()) {
                continue;
            }
            sections.merge(canonicalHeading, body, (left, right) -> left);
        }
        return sections;
    }

    private static String resolveDoctorAssessmentHeading(String heading) {
        String normalized = heading
                .replace("*", "")
                .replace("：", "")
                .replace(":", "")
                .replace(" ", "")
                .trim();
        if (normalized.contains("报告用途")) return SECTION_PURPOSE;
        if (normalized.contains("风险分层结论") || normalized.contains("总体风险分层结论")) return SECTION_RISK;
        if (normalized.contains("关键依据")
                || normalized.contains("关键证据")
                || normalized.contains("关键临床证据")
                || normalized.contains("风险评估依据")) return SECTION_EVIDENCE;
        if (normalized.contains("当前重点问题") || normalized.contains("重点问题")) return SECTION_ISSUES;
        if (normalized.contains("医生干预建议")
                || normalized.contains("管理建议")
                || normalized.contains("干预建议")
                || normalized.contains("重点临床干预与管理建议")) return SECTION_ACTIONS;
        if (normalized.contains("随访建议")
                || normalized.contains("随访安排")
                || normalized.contains("近期随访安排建议")) return SECTION_FOLLOW_UP;
        if (normalized.equals("结论") || normalized.contains("总结")) return SECTION_CONCLUSION;
        return null;
    }

    private static String sanitizeDoctorReportRawText(String raw) {
        return raw
                .replace("[医生姓名]", "当前接诊医生")
                .replace("[请填写]", "暂无")
                .replace("[请补充]", "建议补充")
                .replace("***", "")
                .trim();
    }

    private static String preferSection(String candidate, String fallback) {
        if (candidate == null || candidate.isBlank()) {
            return fallback;
        }
        String normalized = candidate.replace("[医生姓名]", "")
                .replace("[请填写]", "")
                .replace("[请补充]", "")
                .trim();
        if (normalized.isBlank()) {
            return fallback;
        }
        return candidate;
    }

    private static void appendSection(StringBuilder sb, String heading, String body) {
        if (sb.length() > 0) {
            sb.append("\n\n");
        }
        sb.append(heading).append('\n');
        sb.append(body == null || body.isBlank() ? "- 暂无" : body.trim());
    }

    private static String normalizeCheckItems(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        return java.util.Arrays.stream(raw.split("[,，；;\\n]"))
                .map(String::trim)
                .filter(item -> !item.isBlank())
                .limit(5)
                .collect(Collectors.joining("、"));
    }

    private static void appendMetric(StringBuilder sb, String label, Object value, String unit) {
        if (value == null) {
            return;
        }
        sb.append("  - ").append(label).append("：").append(value);
        if (unit != null && !unit.isBlank()) {
            sb.append(' ').append(unit);
        }
        sb.append('\n');
    }
}
