package com.medical.agent.service.support;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;

public final class AssessmentHistoryAlertSupport {

    private AssessmentHistoryAlertSupport() {
    }

    public static List<String> extractHistoryAlerts(String assessmentDetail, List<String> recommendations) {
        LinkedHashSet<String> alerts = new LinkedHashSet<>();
        if (assessmentDetail != null && !assessmentDetail.isBlank()) {
            String prefix = "病史提示：";
            for (String line : assessmentDetail.replace("\r", "").split("\n")) {
                int index = line.indexOf(prefix);
                if (index < 0) {
                    continue;
                }
                String content = line.substring(index + prefix.length()).trim();
                if (content.isBlank()) {
                    continue;
                }
                Arrays.stream(content.split("[；;]"))
                        .map(String::trim)
                        .filter(item -> !item.isBlank())
                        .forEach(alerts::add);
            }
        }
        if (alerts.isEmpty() && recommendations != null) {
            recommendations.stream()
                    .filter(item -> item != null && !item.isBlank())
                    .filter(item -> item.contains("过敏史") || item.contains("既往"))
                    .map(String::trim)
                    .forEach(alerts::add);
        }
        return alerts.isEmpty() ? List.of() : new ArrayList<>(alerts);
    }

    public static String appendHistoryAlertsToNotificationMessage(String baseMessage, List<String> historyAlerts) {
        String normalizedMessage = baseMessage == null ? "" : baseMessage.trim();
        if (historyAlerts == null || historyAlerts.isEmpty()) {
            return normalizedMessage;
        }
        String alertSummary = historyAlerts.stream()
                .limit(2)
                .map(String::trim)
                .filter(item -> !item.isBlank())
                .reduce((left, right) -> left + "；" + right)
                .orElse("");
        if (alertSummary.isBlank()) {
            return normalizedMessage;
        }
        if (normalizedMessage.isBlank()) {
            return "病史提醒：" + alertSummary;
        }
        return normalizedMessage + "；病史提醒：" + alertSummary;
    }
}
