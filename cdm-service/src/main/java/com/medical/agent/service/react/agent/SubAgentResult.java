package com.medical.agent.service.react.agent;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record SubAgentResult(String agentName,
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
                             String contractSchema,
                             String contractVersion,
                             Map<String, Object> contractCompatibility,
                             List<String> contractValidationErrors) {

    public SubAgentResult(String agentName,
                          boolean success,
                          String summary,
                          List<String> usedTools) {
        this(agentName, success, success, summary, usedTools, null, List.of(), List.of(), null, null, List.of(), null, List.of(), List.of(), Map.of(), List.of(),
                SubAgentResultContractSupport.CURRENT_SCHEMA,
                SubAgentResultContractSupport.CURRENT_VERSION,
                Map.of(
                        "expectedSchema", SubAgentResultContractSupport.CURRENT_SCHEMA,
                        "expectedVersion", SubAgentResultContractSupport.CURRENT_VERSION,
                        "schemaCompatible", true,
                        "versionCompatible", true,
                        "validated", true
                ),
                List.of());
    }

    public SubAgentResult(String agentName,
                          boolean success,
                          boolean completed,
                          String summary,
                          List<String> usedTools) {
        this(agentName, success, completed, summary, usedTools, null, List.of(), List.of(), null, null, List.of(), null, List.of(), List.of(), Map.of(), List.of(),
                SubAgentResultContractSupport.CURRENT_SCHEMA,
                SubAgentResultContractSupport.CURRENT_VERSION,
                Map.of(
                        "expectedSchema", SubAgentResultContractSupport.CURRENT_SCHEMA,
                        "expectedVersion", SubAgentResultContractSupport.CURRENT_VERSION,
                        "schemaCompatible", true,
                        "versionCompatible", true,
                        "validated", true
                ),
                List.of());
    }

    public SubAgentResult(String agentName,
                          boolean success,
                          boolean completed,
                          String summary,
                          List<String> usedTools,
                          String suggestedNextAction,
                          List<String> blockers) {
        this(agentName, success, completed, summary, usedTools, suggestedNextAction, blockers, List.of(), null, null, List.of(), null, List.of(), List.of(), Map.of(), List.of(),
                SubAgentResultContractSupport.CURRENT_SCHEMA,
                SubAgentResultContractSupport.CURRENT_VERSION,
                Map.of(
                        "expectedSchema", SubAgentResultContractSupport.CURRENT_SCHEMA,
                        "expectedVersion", SubAgentResultContractSupport.CURRENT_VERSION,
                        "schemaCompatible", true,
                        "versionCompatible", true,
                        "validated", true
                ),
                List.of());
    }

    public SubAgentResult {
        usedTools = immutableList(usedTools);
        blockers = immutableList(blockers);
        requestedSupportAgents = immutableList(requestedSupportAgents);
        missingCapabilities = immutableList(missingCapabilities);
        evidenceRefs = immutableList(evidenceRefs);
        artifacts = immutableMapList(artifacts);
        handoffPayload = immutableMap(handoffPayload);
        nextRequiredCapabilities = immutableList(nextRequiredCapabilities);
        contractCompatibility = immutableMap(contractCompatibility);
        contractValidationErrors = immutableList(contractValidationErrors);
    }

    private static List<String> immutableList(List<String> values) {
        return values == null ? List.of() : List.copyOf(values);
    }

    private static List<Map<String, Object>> immutableMapList(List<Map<String, Object>> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        return values.stream().map(SubAgentResult::immutableMap).toList();
    }

    private static Map<String, Object> immutableMap(Map<String, Object> values) {
        if (values == null || values.isEmpty()) {
            return Map.of();
        }
        return Map.copyOf(new LinkedHashMap<>(values));
    }
}
