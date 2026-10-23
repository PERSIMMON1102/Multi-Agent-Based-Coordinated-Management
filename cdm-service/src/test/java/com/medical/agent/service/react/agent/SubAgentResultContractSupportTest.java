package com.medical.agent.service.react.agent;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SubAgentResultContractSupportTest {

    @Test
    void shouldBackfillMissingSchemaAndVersionWithCompatibilityWarnings() {
        SubAgentResultContractSupport support = new SubAgentResultContractSupport();

        SubAgentResult result = support.normalizeFromJson(
                "patient_profile_agent",
                true,
                false,
                "阶段性完成",
                List.of("patient_context"),
                "继续风险评估",
                List.of("仍缺风险结论"),
                List.of("risk_assessment_agent"),
                "risk_assessment_agent",
                "risk_reassessment",
                List.of("risk_assessment"),
                1.4D,
                List.of("evidence-1"),
                List.of(Map.of("kind", "summary")),
                Map.of("patientSnapshot", "ready"),
                List.of("risk_assessment"),
                null,
                null
        );

        assertEquals("sub-agent-result", result.contractSchema());
        assertEquals("1.0", result.contractVersion());
        assertEquals(1.0D, result.confidence());
        assertTrue(result.contractValidationErrors().contains("missing_contract_schema"));
        assertTrue(result.contractValidationErrors().contains("missing_contract_version"));
        assertTrue(result.contractValidationErrors().contains("invalid_confidence:out_of_range"));
        assertEquals(false, result.contractCompatibility().get("validated"));
    }

    @Test
    void shouldPassSchemaValidationWhenIncompleteResultCarriesHandoffContext() {
        SubAgentResultContractSupport support = new SubAgentResultContractSupport();

        SubAgentResult result = support.normalizeFromJson(
                "risk_assessment_agent",
                true,
                false,
                "需要画像智能体补充患者上下文。",
                List.of("risk_assessment"),
                "等待画像补充后继续风险评估",
                List.of("缺少患者画像"),
                List.of("patient_profile_agent"),
                "patient_profile_agent",
                "context_gap",
                List.of("patient_context"),
                0.62D,
                List.of("context-evidence"),
                List.of(Map.of("kind", "handoff", "summary", "缺上下文")),
                Map.of("patientSnapshot", "missing"),
                List.of("patient_context"),
                "sub-agent-result",
                "1.0"
        );

        assertEquals("sub-agent-result", result.contractSchema());
        assertEquals("1.0", result.contractVersion());
        assertEquals(true, result.contractCompatibility().get("validated"));
        assertFalse(result.contractValidationErrors().stream().anyMatch(item -> item.startsWith("schema_violation:")));
    }

    @Test
    void shouldReportSchemaViolationWhenIncompleteResultOmitsHandoffHints() {
        SubAgentResultContractSupport support = new SubAgentResultContractSupport();

        SubAgentResult result = support.normalizeFromJson(
                "risk_assessment_agent",
                true,
                false,
                "当前只能部分完成。",
                List.of("risk_assessment"),
                "等待其他智能体协助",
                List.of("缺少上下文"),
                List.of(),
                null,
                "context_gap",
                List.of("patient_context"),
                0.45D,
                List.of(),
                List.of(),
                Map.of(),
                List.of(),
                "sub-agent-result",
                "1.0"
        );

        assertEquals(false, result.contractCompatibility().get("validated"));
        assertTrue(result.contractValidationErrors().stream().anyMatch(item -> item.startsWith("schema_violation:")));
    }
}
