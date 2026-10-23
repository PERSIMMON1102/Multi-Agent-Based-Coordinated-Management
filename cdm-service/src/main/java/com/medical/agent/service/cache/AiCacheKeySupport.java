package com.medical.agent.service.cache;

import com.medical.agent.common.dto.RagQueryContextDTO;
import com.medical.agent.common.enums.DiseaseType;
import com.medical.agent.service.chat.ChatPatientContextData;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public final class AiCacheKeySupport {

    private AiCacheKeySupport() {
    }

    public static String patientContext(Long patientId, String version) {
        return scope(patientId, version);
    }

    public static String assessment(ChatPatientContextData patientContext,
                                    String userQuestion,
                                    DiseaseType preferredDiseaseType,
                                    String version) {
        Long patientId = patientContext == null || patientContext.patient() == null
                ? null
                : patientContext.patient().getId();
        String diseaseSegment = preferredDiseaseType == null ? "disease:auto" : "disease:" + preferredDiseaseType.name();
        String questionSegment = "q:" + shortHash(normalize(userQuestion));
        return scope(patientId, version) + "::" + diseaseSegment + "::" + questionSegment;
    }

    public static String evidence(RagQueryContextDTO context, int limit, String version) {
        if (context == null) {
            return scope(null, version) + "::context:none::limit:" + limit;
        }
        String scenarioSegment = "scene:" + shortHash(normalize(context.getScenario()));
        String focusSegment = "focus:" + shortHash(normalize(context.getFocus()));
        String diseasesSegment = "diseases:" + shortHash(normalizeDiseaseKeys(context.getDiseaseKeys()));
        String querySegment = "query:" + shortHash(normalize(context.getQueryText()));
        return scope(context.getPatientId(), version) + "::" + scenarioSegment + "::" + focusSegment + "::" + diseasesSegment + "::" + querySegment + "::limit:" + limit;
    }

    private static String scope(Long patientId, String version) {
        String patientSegment = patientId == null ? "patient:none" : "patient:" + patientId;
        String normalizedVersion = version == null || version.isBlank() ? "v0" : version;
        return patientSegment + "::" + normalizedVersion;
    }

    private static String normalizeDiseaseKeys(List<String> diseaseKeys) {
        if (diseaseKeys == null || diseaseKeys.isEmpty()) {
            return "";
        }
        return diseaseKeys.stream()
                .filter(item -> item != null && !item.isBlank())
                .map(item -> item.trim().toLowerCase(Locale.ROOT))
                .sorted()
                .collect(Collectors.joining(","));
    }

    private static String normalize(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        return text.replaceAll("\\s+", " ").trim().toLowerCase(Locale.ROOT);
    }

    private static String shortHash(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 8 && i < hash.length; i++) {
                sb.append(String.format("%02x", hash[i]));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            return Integer.toHexString(raw.hashCode());
        }
    }
}
