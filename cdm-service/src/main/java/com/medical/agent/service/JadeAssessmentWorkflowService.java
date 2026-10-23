package com.medical.agent.service;

import com.medical.agent.common.fact.PatientHealthFact;
import com.medical.agent.common.enums.DiseaseType;
import com.medical.agent.common.enums.RiskLevel;
import com.medical.agent.common.message.AgentMessage;
import com.medical.agent.common.message.MessageType;
import com.medical.agent.common.util.PatientMedicalHistoryUtil;
import com.medical.agent.model.entity.Patient;
import com.medical.agent.service.support.AssessmentWorkflowPayload;
import org.kie.api.runtime.KieContainer;
import org.kie.api.runtime.KieSession;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
@ConditionalOnProperty(name = "jade.enabled", havingValue = "true")
public class JadeAssessmentWorkflowService {

    private final KieContainer kieContainer;

    public JadeAssessmentWorkflowService(KieContainer kieContainer) {
        this.kieContainer = kieContainer;
    }

    public AgentMessage runFallbackAssessment(Patient patient,
                                              DiseaseType diseaseType,
                                              Map<String, Object> healthData) {
        KieContainer fallbackRules = Objects.requireNonNull(kieContainer, "KieContainer is required for fallback assessment");
        DiseaseType resolvedDiseaseType = diseaseType;
        if (resolvedDiseaseType == null) {
            resolvedDiseaseType = resolveDiseaseTypeFromMedicalHistory(healthData);
        }

        AgentMessage request = AgentMessage.builder()
                .messageId(UUID.randomUUID().toString())
                .messageType(MessageType.ASSESS_REQUEST)
                .patientId(patient.getId())
                .patientName(patient.getName())
                .diseaseType(resolvedDiseaseType)
                .data(healthData)
                .timestamp(System.currentTimeMillis())
                .build();

        KieSession session = fallbackRules.newKieSession();
        try {
            PatientHealthFact fact = new PatientHealthFact();
            fact.setPatientId(patient.getId());
            fact.setPatientName(patient.getName());
            if (resolvedDiseaseType != null) {
                fact.setAssessedDiseaseType(resolvedDiseaseType);
            }
            session.insert(fact);
            session.fireAllRules();
            applyAssessmentHistoryContext(resolvedDiseaseType, fact, healthData);

            return AgentMessage.builder()
                    .messageId(UUID.randomUUID().toString())
                    .messageType(MessageType.ASSESS_RESULT)
                    .patientId(patient.getId())
                    .patientName(patient.getName())
                    .diseaseType(resolvedDiseaseType)
                    .riskLevel(fact.getRiskLevel() != null ? fact.getRiskLevel() : RiskLevel.LOW)
                    .score(fact.getRiskScore() != null ? fact.getRiskScore() : 0.0)
                    .detail(fact.getAssessmentDetail())
                    .recommendations(fact.getRecommendations())
                    .data(request.getData())
                    .timestamp(System.currentTimeMillis())
                    .build();
        } finally {
            session.dispose();
        }
    }

    public void applyAssessmentResult(AgentMessage result,
                                      AssessmentResultPersistenceService assessmentResultPersistenceService,
                                      ClinicalNotificationGateway clinicalNotificationGateway) {
        AssessmentWorkflowPayload payload = new AssessmentWorkflowPayload(
                result.getMessageId(),
                result.getPatientId(),
                result.getPatientName(),
                result.getDiseaseType(),
                result.getRiskLevel(),
                result.getScore(),
                result.getDetail(),
                result.getRecommendations(),
                result.getData()
        );
        assessmentResultPersistenceService.saveBridgeAssessment(payload, "jade", "AssessmentAgent");
        if (!isMetaFlagEnabled(result.getData(), "_silentPatientNotification")) {
            clinicalNotificationGateway.sendNotification(
                    assessmentResultPersistenceService.buildAssessmentNotification(
                            result.getPatientId(),
                            result.getPatientName(),
                            result.getDiseaseType(),
                            result.getRiskLevel(),
                            result.getDetail(),
                            result.getRecommendations(),
                            result
                    )
            );
        }
    }

    public boolean shouldSuppressFollowUpCreation(Map<String, Object> data) {
        return isMetaFlagEnabled(data, "_suppressFollowUpCreation");
    }

    public boolean shouldSuppressPatientNotification(Map<String, Object> data) {
        return isMetaFlagEnabled(data, "_silentPatientNotification");
    }

    private DiseaseType resolveDiseaseTypeFromMedicalHistory(Map<String, Object> data) {
        if (data == null) {
            return null;
        }
        Set<DiseaseType> detected = new LinkedHashSet<>();
        Object historyDiseaseTypes = data.get("historyDiseaseTypes");
        if (historyDiseaseTypes instanceof Iterable<?> iterable) {
            for (Object item : iterable) {
                if (item == null) {
                    continue;
                }
                try {
                    detected.add(DiseaseType.valueOf(item.toString()));
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
        detected.addAll(PatientMedicalHistoryUtil.detectDiseaseTypes(
                asString(data.get("pastDiseases")),
                asString(data.get("medicalHistorySummary"))
        ));
        return detected.stream().findFirst().orElse(null);
    }

    private void applyAssessmentHistoryContext(DiseaseType diseaseType,
                                               PatientHealthFact fact,
                                               Map<String, Object> data) {
        if (fact == null || data == null) {
            return;
        }
        if (fact.getRecommendations() == null) {
            fact.setRecommendations(new ArrayList<>());
        }

        List<String> notes = new ArrayList<>();
        if (diseaseType != null && PatientMedicalHistoryUtil.hasDiseaseHistory(
                diseaseType,
                asString(data.get("pastDiseases")),
                asString(data.get("medicalHistorySummary"))
        )) {
            notes.add("既往" + diseaseType.getDisplayName() + "病史");
            addRecommendationIfAbsent(
                    fact.getRecommendations(),
                    "结合既往" + diseaseType.getDisplayName() + "病史，建议继续按长期管理方案复核指标与治疗依从性。"
            );
        }

        String drugAllergies = asString(data.get("drugAllergies"));
        if (drugAllergies != null && !drugAllergies.isBlank()) {
            notes.add("药物过敏史：" + shorten(drugAllergies, 40));
            addRecommendationIfAbsent(
                    fact.getRecommendations(),
                    "已记录药物过敏史，调整或新增药物前请先核对：" + shorten(drugAllergies, 40)
            );
        }

        String foodAllergies = asString(data.get("foodAllergies"));
        if (foodAllergies != null && !foodAllergies.isBlank()) {
            addRecommendationIfAbsent(
                    fact.getRecommendations(),
                    "已记录食物过敏史，制定饮食建议前请先核对：" + shorten(foodAllergies, 40)
            );
        }

        if (!notes.isEmpty()) {
            String historyNote = "病史提示：" + String.join("；", notes);
            String detail = fact.getAssessmentDetail();
            if (detail == null || detail.isBlank()) {
                fact.setAssessmentDetail(historyNote);
            } else if (!detail.contains(historyNote)) {
                fact.setAssessmentDetail(detail + "\n" + historyNote);
            }
        }
    }

    private void addRecommendationIfAbsent(List<String> recommendations, String recommendation) {
        if (recommendations == null || recommendation == null || recommendation.isBlank()) {
            return;
        }
        boolean exists = recommendations.stream().anyMatch(item -> recommendation.equalsIgnoreCase(String.valueOf(item)));
        if (!exists) {
            recommendations.add(recommendation);
        }
    }

    private boolean isMetaFlagEnabled(Map<String, Object> data, String key) {
        if (data == null || !data.containsKey(key)) {
            return false;
        }
        Object value = data.get(key);
        if (value instanceof Boolean boolValue) {
            return boolValue;
        }
        return "true".equalsIgnoreCase(String.valueOf(value));
    }

    private String asString(Object value) {
        return value == null ? null : value.toString();
    }

    private String shorten(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String normalized = value.replaceAll("[\\r\\n]+", "；").trim();
        if (normalized.length() <= maxLength) {
            return normalized;
        }
        return normalized.substring(0, maxLength) + "...";
    }
}
