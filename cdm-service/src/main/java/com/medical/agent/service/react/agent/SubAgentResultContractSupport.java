package com.medical.agent.service.react.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class SubAgentResultContractSupport {

    public static final String CURRENT_SCHEMA = "sub-agent-result";
    public static final String CURRENT_VERSION = "1.0";
    private static final String CONTRACT_RESOURCE = "contracts/sub-agent-result-1.0.schema.json";
    private static final Set<String> ALLOWED_FAILURE_CATEGORIES = Set.of(
            "context_gap",
            "evidence_gap",
            "risk_reassessment",
            "tool_or_runtime_failure",
            "missing_agent",
            "general_failure"
    );
    private final ObjectMapper objectMapper;
    private final JsonSchema schemaValidator;

    public SubAgentResultContractSupport() {
        this(new ObjectMapper(), loadSchema());
    }

    SubAgentResultContractSupport(ObjectMapper objectMapper, JsonSchema schemaValidator) {
        this.objectMapper = objectMapper;
        this.schemaValidator = schemaValidator;
    }

    public SubAgentResult normalizeFromJson(String agentName,
                                            boolean success,
                                            boolean completed,
                                            String summary,
                                            List<String> usedTools,
                                            String suggestedNextAction,
                                            List<String> blockers,
                                            List<String> requestedSupportAgents,
                                            String suggestedHandoffAgent,
                                            String failureCategory,
                                            List<String> missingCapabilities,
                                            Double confidence,
                                            List<String> evidenceRefs,
                                            List<Map<String, Object>> artifacts,
                                            Map<String, Object> handoffPayload,
                                            List<String> nextRequiredCapabilities,
                                            String schema,
                                            String version) {
        List<String> validationErrors = new ArrayList<>();
        validationErrors.addAll(validateAgainstSchema(success,
                completed,
                summary,
                requestedSupportAgents,
                suggestedHandoffAgent,
                blockers,
                suggestedNextAction,
                failureCategory,
                missingCapabilities,
                confidence,
                evidenceRefs,
                artifacts,
                handoffPayload,
                nextRequiredCapabilities,
                schema,
                version));
        String normalizedSchema = normalizeSchema(schema, validationErrors);
        String normalizedVersion = normalizeVersion(version, validationErrors);
        String normalizedFailureCategory = normalizeFailureCategory(failureCategory, validationErrors);
        Double normalizedConfidence = normalizeConfidence(confidence, validationErrors);
        Map<String, Object> compatibility = buildCompatibility(normalizedSchema, normalizedVersion, validationErrors);
        return new SubAgentResult(
                agentName,
                success,
                completed,
                summary,
                usedTools,
                suggestedNextAction,
                blockers,
                requestedSupportAgents,
                suggestedHandoffAgent,
                normalizedFailureCategory,
                missingCapabilities,
                normalizedConfidence,
                evidenceRefs,
                artifacts,
                handoffPayload,
                nextRequiredCapabilities,
                normalizedSchema,
                normalizedVersion,
                compatibility,
                validationErrors
        );
    }

    public String textValue(JsonNode node) {
        return node == null || node.isNull() ? null : node.asText(null);
    }

    public boolean booleanValue(JsonNode node, boolean fallback) {
        return node == null || node.isNull() ? fallback : node.asBoolean(fallback);
    }

    public Double doubleValue(JsonNode node) {
        if (node == null || node.isNull() || !node.isNumber()) {
            return null;
        }
        double value = node.asDouble();
        return Double.isFinite(value) ? value : null;
    }

    public List<String> stringArray(JsonNode node) {
        if (node == null || node.isNull() || !node.isArray()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        node.forEach(item -> {
            String text = textValue(item);
            if (text != null && !text.isBlank()) {
                values.add(text.trim());
            }
        });
        return values;
    }

    public List<Map<String, Object>> objectArray(JsonNode node) {
        if (node == null || node.isNull() || !node.isArray()) {
            return List.of();
        }
        List<Map<String, Object>> values = new ArrayList<>();
        node.forEach(item -> {
            if (item != null && item.isObject()) {
                values.add(readObject(item));
            }
        });
        return values;
    }

    public Map<String, Object> readObject(JsonNode node) {
        if (node == null || node.isNull() || !node.isObject()) {
            return Map.of();
        }
        LinkedHashMap<String, Object> values = new LinkedHashMap<>();
        node.fields().forEachRemaining(entry -> values.put(entry.getKey(), readJsonValue(entry.getValue())));
        return values;
    }

    public Map<String, Object> metadata(SubAgentResult result) {
        LinkedHashMap<String, Object> metadata = new LinkedHashMap<>();
        if (result == null) {
            return metadata;
        }
        metadata.put("contractSchema", result.contractSchema());
        metadata.put("contractVersion", result.contractVersion());
        metadata.put("contractCompatibility", result.contractCompatibility());
        metadata.put("contractValidationErrors", result.contractValidationErrors());
        return metadata;
    }

    private List<String> validateAgainstSchema(boolean success,
                                               boolean completed,
                                               String summary,
                                               List<String> requestedSupportAgents,
                                               String suggestedHandoffAgent,
                                               List<String> blockers,
                                               String suggestedNextAction,
                                               String failureCategory,
                                               List<String> missingCapabilities,
                                               Double confidence,
                                               List<String> evidenceRefs,
                                               List<Map<String, Object>> artifacts,
                                               Map<String, Object> handoffPayload,
                                               List<String> nextRequiredCapabilities,
                                               String schema,
                                               String version) {
        if (schemaValidator == null) {
            return List.of("contract_schema_validator_unavailable");
        }
        try {
            JsonNode payload = objectMapper.valueToTree(buildSchemaValidationPayload(
                    completed,
                    summary,
                    requestedSupportAgents,
                    suggestedHandoffAgent,
                    blockers,
                    suggestedNextAction,
                    failureCategory,
                    missingCapabilities,
                    confidence,
                    evidenceRefs,
                    artifacts,
                    handoffPayload,
                    nextRequiredCapabilities,
                    schema,
                    version
            ));
            Set<ValidationMessage> violations = schemaValidator.validate(payload);
            if (violations == null || violations.isEmpty()) {
                return List.of();
            }
            return violations.stream()
                    .sorted(Comparator.comparing(ValidationMessage::getEvaluationPath))
                    .map(message -> "schema_violation:" + message.getEvaluationPath() + ":" + message.getMessage())
                    .collect(Collectors.toList());
        } catch (RuntimeException ex) {
            return List.of("contract_schema_validation_failed:" + ex.getClass().getSimpleName());
        }
    }

    private Map<String, Object> buildSchemaValidationPayload(boolean completed,
                                                             String summary,
                                                             List<String> requestedSupportAgents,
                                                             String suggestedHandoffAgent,
                                                             List<String> blockers,
                                                             String suggestedNextAction,
                                                             String failureCategory,
                                                             List<String> missingCapabilities,
                                                             Double confidence,
                                                             List<String> evidenceRefs,
                                                             List<Map<String, Object>> artifacts,
                                                             Map<String, Object> handoffPayload,
                                                             List<String> nextRequiredCapabilities,
                                                             String schema,
                                                             String version) {
        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", "final");
        payload.put("contractSchema", schema);
        payload.put("contractVersion", version);
        payload.put("summary", summary);
        payload.put("completed", completed);
        if (requestedSupportAgents != null && !requestedSupportAgents.isEmpty()) {
            payload.put("requestedSupportAgents", requestedSupportAgents);
        }
        if (suggestedHandoffAgent != null) {
            payload.put("suggestedHandoffAgent", suggestedHandoffAgent);
        }
        if (blockers != null && !blockers.isEmpty()) {
            payload.put("blockers", blockers);
        }
        if (suggestedNextAction != null) {
            payload.put("suggestedNextAction", suggestedNextAction);
        }
        if (failureCategory != null) {
            payload.put("failureCategory", failureCategory);
        }
        if (missingCapabilities != null && !missingCapabilities.isEmpty()) {
            payload.put("missingCapabilities", missingCapabilities);
        }
        if (confidence != null) {
            payload.put("confidence", confidence);
        }
        if (evidenceRefs != null && !evidenceRefs.isEmpty()) {
            payload.put("evidenceRefs", evidenceRefs);
        }
        if (artifacts != null && !artifacts.isEmpty()) {
            payload.put("artifacts", artifacts);
        }
        if (handoffPayload != null && !handoffPayload.isEmpty()) {
            payload.put("handoffPayload", handoffPayload);
        }
        if (nextRequiredCapabilities != null && !nextRequiredCapabilities.isEmpty()) {
            payload.put("nextRequiredCapabilities", nextRequiredCapabilities);
        }
        return payload;
    }

    private Object readJsonValue(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isTextual()) {
            return node.asText();
        }
        if (node.isInt() || node.isLong()) {
            return node.asLong();
        }
        if (node.isFloat() || node.isDouble() || node.isBigDecimal()) {
            return node.asDouble();
        }
        if (node.isBoolean()) {
            return node.asBoolean();
        }
        if (node.isArray()) {
            List<Object> items = new ArrayList<>();
            node.forEach(item -> items.add(readJsonValue(item)));
            return items;
        }
        if (node.isObject()) {
            return readObject(node);
        }
        return node.asText();
    }

    private String normalizeSchema(String schema, List<String> validationErrors) {
        if (schema == null || schema.isBlank()) {
            validationErrors.add("missing_contract_schema");
            return CURRENT_SCHEMA;
        }
        String normalized = schema.trim();
        if (!CURRENT_SCHEMA.equals(normalized)) {
            validationErrors.add("unsupported_contract_schema:" + normalized);
        }
        return normalized;
    }

    private String normalizeVersion(String version, List<String> validationErrors) {
        if (version == null || version.isBlank()) {
            validationErrors.add("missing_contract_version");
            return CURRENT_VERSION;
        }
        String normalized = version.trim();
        if (!CURRENT_VERSION.equals(normalized)) {
            validationErrors.add("unsupported_contract_version:" + normalized);
        }
        return normalized;
    }

    private String normalizeFailureCategory(String failureCategory, List<String> validationErrors) {
        if (failureCategory == null || failureCategory.isBlank()) {
            return null;
        }
        String normalized = failureCategory.trim();
        if (!ALLOWED_FAILURE_CATEGORIES.contains(normalized)) {
            validationErrors.add("invalid_failure_category:" + normalized);
            return "general_failure";
        }
        return normalized;
    }

    private Double normalizeConfidence(Double confidence, List<String> validationErrors) {
        if (confidence == null) {
            return null;
        }
        if (!Double.isFinite(confidence)) {
            validationErrors.add("invalid_confidence:not_finite");
            return null;
        }
        if (confidence < 0.0D || confidence > 1.0D) {
            validationErrors.add("invalid_confidence:out_of_range");
            return Math.max(0.0D, Math.min(1.0D, confidence));
        }
        return confidence;
    }

    private Map<String, Object> buildCompatibility(String schema,
                                                   String version,
                                                   List<String> validationErrors) {
        LinkedHashMap<String, Object> compatibility = new LinkedHashMap<>();
        compatibility.put("expectedSchema", CURRENT_SCHEMA);
        compatibility.put("expectedVersion", CURRENT_VERSION);
        compatibility.put("schemaCompatible", CURRENT_SCHEMA.equals(schema));
        compatibility.put("versionCompatible", CURRENT_VERSION.equals(version));
        compatibility.put("validated", validationErrors.isEmpty());
        return compatibility;
    }

    private static JsonSchema loadSchema() {
        JsonSchemaFactory factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012);
        try (InputStream inputStream = SubAgentResultContractSupport.class.getClassLoader().getResourceAsStream(CONTRACT_RESOURCE)) {
            if (inputStream == null) {
                throw new IllegalStateException("Missing contract schema resource: " + CONTRACT_RESOURCE);
            }
            return factory.getSchema(inputStream);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to load contract schema resource: " + CONTRACT_RESOURCE, ex);
        }
    }
}
