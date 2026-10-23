package com.medical.agent.service.device;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;

@Component
public class DeviceImportNormalizer {

    public String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    public String textOf(JsonNode node, String... names) {
        if (node == null || node.isNull()) {
            return null;
        }
        for (String name : names) {
            JsonNode value = node.get(name);
            if (value != null && !value.isNull()) {
                String text = value.asText(null);
                if (text != null && !text.isBlank()) {
                    return text.trim();
                }
            }
        }
        return null;
    }

    public boolean hasAny(JsonNode node, String... names) {
        if (node == null || node.isNull()) {
            return false;
        }
        for (String name : names) {
            if (node.has(name)) {
                return true;
            }
        }
        return false;
    }

    public Double numberOf(JsonNode node, String... names) {
        if (node == null || node.isNull()) {
            return null;
        }
        for (String name : names) {
            JsonNode value = node.get(name);
            Double parsed = parseNumber(value);
            if (parsed != null) {
                return parsed;
            }
        }
        return null;
    }

    public Integer integerOf(JsonNode node, String... names) {
        Double number = numberOf(node, names);
        return number == null ? null : (int) Math.round(number);
    }

    public LocalDateTime parseDateTime(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String trimmed = raw.trim();
        try {
            return LocalDateTime.parse(trimmed);
        } catch (DateTimeParseException ignored) {
        }
        try {
            return Instant.parse(trimmed).atZone(ZoneId.systemDefault()).toLocalDateTime();
        } catch (DateTimeParseException ignored) {
        }
        try {
            long epoch = Long.parseLong(trimmed);
            if (trimmed.length() >= 13) {
                return Instant.ofEpochMilli(epoch).atZone(ZoneId.systemDefault()).toLocalDateTime();
            }
            return Instant.ofEpochSecond(epoch).atZone(ZoneId.systemDefault()).toLocalDateTime();
        } catch (NumberFormatException ignored) {
        }
        return null;
    }

    public String inferDeviceType(JsonNode properties) {
        if (properties == null || !properties.isObject()) {
            return "wearable";
        }
        if (hasAny(properties, "systolic_bp", "diastolic_bp", "sbp", "dbp", "high_pressure", "low_pressure")) {
            return "blood_pressure_monitor";
        }
        if (hasAny(properties, "blood_glucose", "glucose", "fasting_glucose", "postprandial_glucose")) {
            return "glucose_meter";
        }
        if (hasAny(properties, "heart_rate", "pulse", "hr")) {
            return "wearable";
        }
        return "wearable";
    }

    private Double parseNumber(JsonNode value) {
        if (value == null || value.isNull()) {
            return null;
        }
        if (value.isNumber()) {
            return value.asDouble();
        }
        if (value.isTextual()) {
            String text = value.asText().trim();
            if (text.isEmpty() || text.contains("/")) {
                return null;
            }
            try {
                return Double.parseDouble(text);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }
}
