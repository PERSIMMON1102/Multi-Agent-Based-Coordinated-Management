package com.medical.agent.service.security;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
import java.util.Map;

public class PrivacyLogSanitizer {

    private static final int MAX_TEXT_LENGTH = 80;

    public String summarizeText(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String normalized = value.replaceAll("\\s+", " ").trim();
        if (normalized.length() <= MAX_TEXT_LENGTH) {
            return normalized;
        }
        return normalized.substring(0, MAX_TEXT_LENGTH - 3) + "...";
    }

    public Map<String, Object> summarizeMap(Map<String, Object> value) {
        LinkedHashMap<String, Object> summary = new LinkedHashMap<>();
        if (value == null || value.isEmpty()) {
            return summary;
        }
        value.forEach((key, item) -> {
            if (key == null || key.isBlank() || item == null) {
                return;
            }
            summary.put(key, summarizeValue(item));
        });
        return summary;
    }

    public String digest(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (byte b : bytes) {
                builder.append(String.format("%02x", b));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException ex) {
            return Integer.toHexString(value.hashCode());
        }
    }

    private Object summarizeValue(Object value) {
        if (value instanceof String text) {
            return summarizeText(text);
        }
        if (value instanceof Map<?, ?> map) {
            LinkedHashMap<String, Object> nested = new LinkedHashMap<>();
            map.forEach((key, nestedValue) -> {
                if (key != null && nestedValue != null) {
                    nested.put(key.toString(), summarizeValue(nestedValue));
                }
            });
            return nested;
        }
        if (value instanceof Iterable<?> iterable) {
            LinkedHashMap<String, Object> summary = new LinkedHashMap<>();
            int index = 0;
            for (Object item : iterable) {
                if (item == null) {
                    continue;
                }
                summary.put(String.valueOf(index++), summarizeValue(item));
                if (index >= 5) {
                    break;
                }
            }
            return summary;
        }
        return value;
    }
}
