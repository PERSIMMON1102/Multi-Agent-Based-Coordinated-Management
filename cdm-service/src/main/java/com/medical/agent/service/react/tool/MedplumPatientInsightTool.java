package com.medical.agent.service.react.tool;

import com.medical.agent.service.MedplumService;
import com.medical.agent.service.react.model.AgentState;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class MedplumPatientInsightTool implements AgentTool {

    private final MedplumService medplumService;

    public MedplumPatientInsightTool(MedplumService medplumService) {
        this.medplumService = medplumService;
    }

    @Override
    public String name() {
        return "medplum_patient_insight";
    }

    @Override
    public String description() {
        return "读取 Medplum Cloud 中与本地患者映射的 Patient 和最新 Observation，用于补充云端标准 FHIR 视图。";
    }

    @Override
    public ToolExecutionResult execute(AgentState state, Map<String, Object> input) {
        if (state == null || state.getPatientId() == null) {
            return new ToolExecutionResult(name(), false, "当前未绑定患者，无法读取 Medplum 云端数据。");
        }

        int limit = resolveObservationLimit(input);
        Map<String, Object> patientResult = medplumService.getPatientByLocalId(state.getPatientId());
        if (!Boolean.TRUE.equals(patientResult.get("available"))) {
            String message = stringValue(patientResult.get("message"), "Medplum 当前不可用。");
            rememberDegradation(state, message);
            return new ToolExecutionResult(name(), false, message);
        }
        if (!Boolean.TRUE.equals(patientResult.get("found"))) {
            return new ToolExecutionResult(name(), false, stringValue(patientResult.get("message"), "Medplum 中未找到对应患者。"));
        }

        Map<String, Object> observationResult = medplumService.getLatestObservationsForLocalPatient(state.getPatientId(), limit);
        StringBuilder summary = new StringBuilder("已读取 Medplum 云端患者视图。");
        appendPatientSummary(summary, patientResult);
        appendObservationSummary(summary, observationResult);
        return new ToolExecutionResult(name(), true, summary.toString());
    }

    private int resolveObservationLimit(Map<String, Object> input) {
        if (input == null) {
            return 5;
        }
        Object rawLimit = input.get("limit");
        if (rawLimit instanceof Number number) {
            return Math.min(Math.max(number.intValue(), 1), 10);
        }
        if (rawLimit instanceof String string && !string.isBlank()) {
            try {
                return Math.min(Math.max(Integer.parseInt(string.trim()), 1), 10);
            } catch (NumberFormatException ignored) {
                return 5;
            }
        }
        return 5;
    }

    private void appendPatientSummary(StringBuilder summary, Map<String, Object> patientResult) {
        String name = stringValue(patientResult.get("name"), "");
        String gender = stringValue(patientResult.get("gender"), "");
        String birthDate = stringValue(patientResult.get("birthDate"), "");
        String medplumPatientId = stringValue(patientResult.get("medplumPatientId"), "");
        String emergencyContact = stringValue(patientResult.get("emergencyContact"), "");
        String emergencyPhone = stringValue(patientResult.get("emergencyPhone"), "");
        String assignedDoctorId = stringValue(patientResult.get("assignedDoctorId"), "");
        String medicalHistorySummary = stringValue(patientResult.get("medicalHistorySummary"), "");
        Object profileCompleted = patientResult.get("profileCompleted");

        if (!name.isBlank()) {
            summary.append(" 患者=").append(name).append("。");
        }
        if (!gender.isBlank() || !birthDate.isBlank()) {
            summary.append(" 基本信息=");
            if (!gender.isBlank()) {
                summary.append(gender);
            }
            if (!birthDate.isBlank()) {
                if (!gender.isBlank()) {
                    summary.append("，");
                }
                summary.append("出生日期 ").append(birthDate);
            }
            summary.append("。");
        }
        if (!medplumPatientId.isBlank()) {
            summary.append(" Medplum Patient ID=").append(medplumPatientId).append("。");
        }
        if (!assignedDoctorId.isBlank()) {
            summary.append(" 责任医生ID=").append(assignedDoctorId).append("。");
        }
        if (profileCompleted instanceof Boolean completed) {
            summary.append(" 档案完整度=").append(completed ? "已完善" : "未完善").append("。");
        }
        if (!emergencyContact.isBlank() || !emergencyPhone.isBlank()) {
            summary.append(" 紧急联系人=");
            if (!emergencyContact.isBlank()) {
                summary.append(emergencyContact);
            }
            if (!emergencyPhone.isBlank()) {
                if (!emergencyContact.isBlank()) {
                    summary.append(" ");
                }
                summary.append(emergencyPhone);
            }
            summary.append("。");
        }
        if (!medicalHistorySummary.isBlank()) {
            summary.append(" 病史摘要=").append(medicalHistorySummary.replace('\n', '；')).append("。");
        }
    }

    @SuppressWarnings("unchecked")
    private void appendObservationSummary(StringBuilder summary, Map<String, Object> observationResult) {
        if (observationResult == null || !Boolean.TRUE.equals(observationResult.get("found"))) {
            summary.append(" 云端暂无可用 Observation。");
            return;
        }

        List<Map<String, Object>> observations =
                observationResult.get("observations") instanceof List<?> list ? (List<Map<String, Object>>) list : List.of();
        if (observations.isEmpty()) {
            summary.append(" 云端暂无可用 Observation。");
            return;
        }

        summary.append(" 最新 Observation ");
        int count = Math.min(observations.size(), 3);
        for (int index = 0; index < count; index++) {
            Map<String, Object> observation = observations.get(index);
            if (index > 0) {
                summary.append("；");
            }
            String code = stringValue(observation.get("code"), "未命名指标");
            String value = stringValue(observation.get("value"), "无值");
            String time = stringValue(observation.get("effectiveDateTime"), "");
            summary.append(code).append("=").append(value);
            if (!time.isBlank()) {
                summary.append(" @ ").append(time);
            }
        }
        summary.append("。");
    }

    private String stringValue(Object value, String fallback) {
        if (value == null) {
            return fallback;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? fallback : text;
    }

    private void rememberDegradation(AgentState state, String summary) {
        if (state == null) {
            return;
        }
        state.addUnavailableExternalSource(name());
        state.addDegradationNote("Medplum/MCP 外部数据当前不可用，本轮分析已自动降级为基于平台内本地数据。"
                + (summary == null || summary.isBlank() ? "" : " 原因：" + summary));
        state.addSafetyFlag("external_data_degraded");
    }
}
