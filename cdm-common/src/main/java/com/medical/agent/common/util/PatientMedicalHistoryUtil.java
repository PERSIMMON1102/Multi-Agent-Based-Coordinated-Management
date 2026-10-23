package com.medical.agent.common.util;

import com.medical.agent.common.enums.DiseaseType;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class PatientMedicalHistoryUtil {

    private static final Map<DiseaseType, List<String>> DISEASE_ALIASES = new LinkedHashMap<>();

    static {
        DISEASE_ALIASES.put(DiseaseType.HYPERTENSION, List.of("高血压", "原发性高血压", "htn", "hypertension"));
        DISEASE_ALIASES.put(DiseaseType.DIABETES, List.of("糖尿病", "2型糖尿病", "t2dm", "diabetes"));
        DISEASE_ALIASES.put(DiseaseType.COPD, List.of("慢阻肺", "慢性阻塞性肺疾病", "copd"));
        DISEASE_ALIASES.put(DiseaseType.CKD, List.of("慢性肾病", "慢性肾脏病", "慢性肾功能衰竭", "ckd"));
        DISEASE_ALIASES.put(DiseaseType.CHD, List.of("冠心病", "心绞痛", "心肌缺血", "chd"));
        DISEASE_ALIASES.put(DiseaseType.STROKE, List.of("脑卒中", "中风", "脑梗", "脑梗死", "脑梗塞", "脑出血", "stroke"));
    }

    private PatientMedicalHistoryUtil() {
    }

    public static Set<DiseaseType> detectDiseaseTypes(String... texts) {
        LinkedHashSet<DiseaseType> detected = new LinkedHashSet<>();
        if (texts == null) {
            return detected;
        }
        for (String text : texts) {
            String normalized = normalize(text);
            if (normalized == null) {
                continue;
            }
            for (Map.Entry<DiseaseType, List<String>> entry : DISEASE_ALIASES.entrySet()) {
                if (containsAny(normalized, entry.getValue())) {
                    detected.add(entry.getKey());
                }
            }
        }
        return detected;
    }

    public static boolean hasDiseaseHistory(DiseaseType diseaseType, String... texts) {
        if (diseaseType == null) {
            return false;
        }
        return detectDiseaseTypes(texts).contains(diseaseType);
    }

    private static boolean containsAny(String normalized, List<String> aliases) {
        if (aliases == null || aliases.isEmpty()) {
            return false;
        }
        for (String alias : aliases) {
            if (alias != null && !alias.isBlank() && normalized.contains(alias.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private static String normalize(String text) {
        if (text == null) {
            return null;
        }
        String normalized = text.toLowerCase(Locale.ROOT)
                .replaceAll("[\\r\\n]+", " ")
                .replaceAll("\\s+", " ")
                .trim();
        return normalized.isBlank() ? null : normalized;
    }
}
