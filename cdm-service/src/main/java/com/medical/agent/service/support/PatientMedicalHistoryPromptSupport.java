package com.medical.agent.service.support;

import com.medical.agent.model.entity.PatientMedicalHistory;

import java.util.ArrayList;
import java.util.List;

public final class PatientMedicalHistoryPromptSupport {

    private static final int FIELD_LIMIT = 120;

    private PatientMedicalHistoryPromptSupport() {
    }

    public static String buildSummary(PatientMedicalHistory history) {
        if (history == null) {
            return "";
        }
        StringBuilder summary = new StringBuilder();
        append(summary, "既往患病情况", history.getPastDiseases());
        append(summary, "手术史", history.getSurgicalHistory());
        append(summary, "药物过敏", history.getDrugAllergies());
        append(summary, "食物过敏", history.getFoodAllergies());
        append(summary, "预防接种", history.getImmunizationHistory());
        append(summary, "家族史", history.getFamilyHistory());
        append(summary, "其他备注", history.getOtherNotes());
        return summary.toString();
    }

    public static boolean hasContent(PatientMedicalHistory history) {
        return history != null && hasAny(
                history.getPastDiseases(),
                history.getSurgicalHistory(),
                history.getDrugAllergies(),
                history.getFoodAllergies(),
                history.getImmunizationHistory(),
                history.getFamilyHistory(),
                history.getOtherNotes()
        );
    }

    public static List<String> buildAlerts(PatientMedicalHistory history) {
        if (history == null) {
            return List.of();
        }
        List<String> alerts = new ArrayList<>();
        appendAlert(alerts, "既往患病情况", history.getPastDiseases());
        appendAlert(alerts, "药物过敏", history.getDrugAllergies());
        appendAlert(alerts, "食物过敏", history.getFoodAllergies());
        appendAlert(alerts, "手术史", history.getSurgicalHistory());
        return alerts.isEmpty() ? List.of() : List.copyOf(alerts);
    }

    private static boolean hasAny(String... values) {
        if (values == null) {
            return false;
        }
        for (String value : values) {
            if (normalize(value) != null) {
                return true;
            }
        }
        return false;
    }

    private static void append(StringBuilder summary, String label, String value) {
        String normalized = normalize(value);
        if (normalized == null) {
            return;
        }
        if (summary.length() > 0) {
            summary.append('\n');
        }
        summary.append(label).append("：").append(normalized);
    }

    private static void appendAlert(List<String> alerts, String label, String value) {
        String normalized = normalize(value);
        if (normalized == null) {
            return;
        }
        alerts.add(label + "：" + normalized);
    }

    private static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.replaceAll("[\\r\\n]+", "；")
                .replaceAll("\\s+", " ")
                .trim();
        if (normalized.isBlank()) {
            return null;
        }
        if (normalized.length() > FIELD_LIMIT) {
            return normalized.substring(0, FIELD_LIMIT) + "...";
        }
        return normalized;
    }
}
