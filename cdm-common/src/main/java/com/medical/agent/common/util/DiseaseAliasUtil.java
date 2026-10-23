package com.medical.agent.common.util;

import com.medical.agent.common.enums.DiseaseType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class DiseaseAliasUtil {

    public static final String GENERAL = "GENERAL";

    private static final Map<String, String> ALIAS_TO_KEY = Map.ofEntries(
            Map.entry("GENERAL", GENERAL),
            Map.entry("通用", GENERAL),
            Map.entry("ALL", GENERAL),
            Map.entry("DIABETES", DiseaseType.DIABETES.name()),
            Map.entry("糖尿病", DiseaseType.DIABETES.name()),
            Map.entry("2型糖尿病", DiseaseType.DIABETES.name()),
            Map.entry("T2DM", DiseaseType.DIABETES.name()),
            Map.entry("HYPERTENSION", DiseaseType.HYPERTENSION.name()),
            Map.entry("高血压", DiseaseType.HYPERTENSION.name()),
            Map.entry("原发性高血压", DiseaseType.HYPERTENSION.name()),
            Map.entry("HTN", DiseaseType.HYPERTENSION.name()),
            Map.entry("COPD", DiseaseType.COPD.name()),
            Map.entry("慢阻肺", DiseaseType.COPD.name()),
            Map.entry("慢性阻塞性肺疾病", DiseaseType.COPD.name()),
            Map.entry("CKD", DiseaseType.CKD.name()),
            Map.entry("慢性肾病", DiseaseType.CKD.name()),
            Map.entry("慢性肾脏病", DiseaseType.CKD.name()),
            Map.entry("CHD", DiseaseType.CHD.name()),
            Map.entry("冠心病", DiseaseType.CHD.name()),
            Map.entry("STROKE", DiseaseType.STROKE.name()),
            Map.entry("脑卒中", DiseaseType.STROKE.name())
    );

    private DiseaseAliasUtil() {
    }

    public static String normalizeKey(String raw) {
        if (raw == null || raw.isBlank()) {
            return GENERAL;
        }
        String normalized = raw.trim();
        String direct = ALIAS_TO_KEY.get(normalized);
        if (direct != null) {
            return direct;
        }
        direct = ALIAS_TO_KEY.get(normalized.toUpperCase(Locale.ROOT));
        if (direct != null) {
            return direct;
        }
        return normalized.toUpperCase(Locale.ROOT);
    }

    public static String displayName(String diseaseKey) {
        return switch (normalizeKey(diseaseKey)) {
            case "DIABETES" -> "2型糖尿病";
            case "HYPERTENSION" -> "高血压";
            case "COPD" -> "慢阻肺";
            case "CKD" -> "慢性肾病";
            case "CHD" -> "冠心病";
            case "STROKE" -> "脑卒中";
            default -> "通用";
        };
    }

    public static String knowledgeLabel(String diseaseKey) {
        return switch (normalizeKey(diseaseKey)) {
            case "DIABETES" -> "糖尿病";
            case "HYPERTENSION" -> "高血压";
            case "COPD" -> "慢阻肺";
            case "CKD" -> "慢性肾病";
            case "CHD" -> "冠心病";
            case "STROKE" -> "脑卒中";
            default -> "通用";
        };
    }

    public static List<String> normalizeKeys(Collection<String> values) {
        LinkedHashSet<String> keys = new LinkedHashSet<>();
        if (values != null) {
            for (String value : values) {
                String normalized = normalizeKey(value);
                if (normalized != null && !normalized.isBlank()) {
                    keys.add(normalized);
                }
            }
        }
        return new ArrayList<>(keys);
    }
}
