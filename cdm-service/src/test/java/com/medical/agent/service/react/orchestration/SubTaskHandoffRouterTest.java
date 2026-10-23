package com.medical.agent.service.react.orchestration;

import com.medical.agent.model.mongo.OrchestrationTaskSnapshot;
import com.medical.agent.service.react.agent.ChatSubAgent;
import com.medical.agent.service.react.agent.SubAgentRegistry;
import com.medical.agent.service.react.agent.SubAgentResult;
import com.medical.agent.service.react.model.AgentState;
import com.medical.agent.service.react.tool.AgentTool;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SubTaskHandoffRouterTest {

    @Test
    void shouldPreferPatientProfileAgentForProfileLikeGoal() {
        SubTaskHandoffRouter router = new SubTaskHandoffRouter(registry(), null);

        String agent = router.selectReplacementAgent(null, Map.of(
                "targetAgent", "risk_assessment_agent",
                "goal", "请补充患者画像和病史后继续分析"
        ));

        assertEquals("patient_profile_agent", agent);
    }

    @Test
    void shouldPreferEvidenceAgentForEvidenceLikeGoal() {
        SubTaskHandoffRouter router = new SubTaskHandoffRouter(registry(), null);

        String agent = router.selectReplacementAgent(null, Map.of(
                "targetAgent", "patient_profile_agent",
                "goal", "请补充指南证据和知识图谱依据"
        ));

        assertEquals("evidence_support_agent", agent);
    }

    @Test
    void shouldExposeTaskTemplateWeightsForEvidenceTask() {
        SubTaskHandoffRouter router = new SubTaskHandoffRouter(registry(), null);

        List<Map<String, Object>> candidates = router.scoreReplacementCandidates(null, Map.of(
                "targetAgent", "patient_profile_agent",
                "goal", "请补充指南证据和知识图谱依据"
        ));

        Map<?, ?> breakdown = (Map<?, ?>) candidates.get(0).get("breakdown");
        Map<?, ?> taskTemplateWeights = (Map<?, ?>) breakdown.get("taskTemplateWeights");
        assertEquals("evidence_synthesis", breakdown.get("taskType"));
        assertEquals(95, taskTemplateWeights.get("goalSemanticRoute"));
        assertEquals("evidence_support_agent", candidates.get(0).get("agent"));
    }

    @Test
    void shouldAvoidRecentlyFailedAgentForSameSubTask() {
        SubTaskHandoffRouter router = new SubTaskHandoffRouter(registry(), null);

        String agent = router.selectReplacementAgent(OrchestrationTaskSnapshot.builder()
                .events(List.of(Map.of(
                        "type", TaskEventType.FAILED.name(),
                        "metadata", Map.of(
                                "subTaskId", "sub-1",
                                "targetAgent", "patient_profile_agent"
                        )
                )))
                .build(), Map.of(
                "subTaskId", "sub-1",
                "targetAgent", "risk_assessment_agent",
                "goal", "请补充患者画像和病史后继续分析"
        ));

        assertEquals("evidence_support_agent", agent);
    }

    @Test
    void shouldPreferEvidenceAgentWhenFailureCategoryIndicatesEvidenceGap() {
        SubTaskHandoffRouter router = new SubTaskHandoffRouter(registry(), null);

        String agent = router.selectReplacementAgent(OrchestrationTaskSnapshot.builder()
                .events(List.of(Map.of(
                        "type", TaskEventType.BLOCKED.name(),
                        "metadata", Map.of(
                                "subTaskId", "sub-2",
                                "targetAgent", "risk_assessment_agent",
                                "failureCategory", "evidence_gap"
                        )
                )))
                .build(), Map.of(
                "subTaskId", "sub-2",
                "targetAgent", "risk_assessment_agent",
                "goal", "请继续分析"
        ));

        assertEquals("evidence_support_agent", agent);
    }

    @Test
    void shouldPreferPatientProfileAgentWhenFailureCategoryIndicatesContextGap() {
        SubTaskHandoffRouter router = new SubTaskHandoffRouter(registry(), null);

        String agent = router.selectReplacementAgent(OrchestrationTaskSnapshot.builder()
                .events(List.of(Map.of(
                        "type", TaskEventType.FAILED.name(),
                        "metadata", Map.of(
                                "subTaskId", "sub-3",
                                "targetAgent", "evidence_support_agent",
                                "failureCategory", "context_gap"
                        )
                )))
                .build(), Map.of(
                "subTaskId", "sub-3",
                "targetAgent", "evidence_support_agent",
                "goal", "请继续分析"
        ));

        assertEquals("patient_profile_agent", agent);
    }

    @Test
    void shouldPreferRiskAgentWhenFailureCategoryIndicatesRiskReassessment() {
        SubTaskHandoffRouter router = new SubTaskHandoffRouter(registry(), null);

        String agent = router.selectReplacementAgent(OrchestrationTaskSnapshot.builder()
                .events(List.of(Map.of(
                        "type", TaskEventType.BLOCKED.name(),
                        "metadata", Map.of(
                                "subTaskId", "sub-4",
                                "targetAgent", "patient_profile_agent",
                                "failureCategory", "risk_reassessment"
                        )
                )))
                .build(), Map.of(
                "subTaskId", "sub-4",
                "targetAgent", "patient_profile_agent",
                "goal", "请继续分析"
        ));

        assertEquals("risk_assessment_agent", agent);
    }

    @Test
    void shouldPreferPatientProfileAgentWhenMissingPatientContextCapability() {
        SubTaskHandoffRouter router = new SubTaskHandoffRouter(registry(), null);

        String agent = router.selectReplacementAgent(OrchestrationTaskSnapshot.builder()
                .events(List.of(Map.of(
                        "type", TaskEventType.BLOCKED.name(),
                        "metadata", Map.of(
                                "subTaskId", "sub-5",
                                "targetAgent", "evidence_support_agent",
                                "failureCategory", "tool_or_runtime_failure",
                                "missingCapabilities", List.of("patient_context")
                        )
                )))
                .build(), Map.of(
                "subTaskId", "sub-5",
                "targetAgent", "evidence_support_agent",
                "goal", "请继续分析"
        ));

        assertEquals("patient_profile_agent", agent);
    }

    @Test
    void shouldPreferEvidenceAgentWhenMissingEvidenceCapability() {
        SubTaskHandoffRouter router = new SubTaskHandoffRouter(registry(), null);

        String agent = router.selectReplacementAgent(OrchestrationTaskSnapshot.builder()
                .events(List.of(Map.of(
                        "type", TaskEventType.FAILED.name(),
                        "metadata", Map.of(
                                "subTaskId", "sub-6",
                                "targetAgent", "patient_profile_agent",
                                "failureCategory", "general_failure",
                                "missingCapabilities", List.of("medical_evidence_search")
                        )
                )))
                .build(), Map.of(
                "subTaskId", "sub-6",
                "targetAgent", "patient_profile_agent",
                "goal", "请继续分析"
        ));

        assertEquals("evidence_support_agent", agent);
    }

    @Test
    void shouldSelectAgentByDeclaredCapabilityForCapabilityRouting() {
        SubTaskHandoffRouter router = new SubTaskHandoffRouter(registry(), null);

        String agent = router.selectAgentForCapabilities(List.of("knowledge_graph"), "patient_profile_agent");

        assertEquals("evidence_support_agent", agent);
    }

    @Test
    void shouldAvoidRecentlyFailedResolvedImplementationForSameSubTask() {
        OrchestrationPersistenceService persistenceService = mock(OrchestrationPersistenceService.class);
        when(persistenceService.summarizeRecentAgentExecutionHealth(24)).thenReturn(List.of(
                Map.of(
                        "agent", "patient_profile_agent_primary",
                        "executionCount", 8,
                        "successRate", 1D,
                        "stabilityScore", 0.8D,
                        "recent24hExecutionTrend", List.of(
                                Map.of(
                                        "completedCount", 3L,
                                        "failedCount", 0L,
                                        "blockedCount", 0L,
                                        "reroutedCount", 0L,
                                        "successRate", 1D
                                )
                        )
                )
        ));
        SubAgentRegistry registry = new SubAgentRegistry(List.of(
                agent(
                        "patient_profile_agent_primary",
                        "patient_profile_agent",
                        "local",
                        30,
                        90,
                        ChatSubAgent.CAPABILITY_PATIENT_CONTEXT,
                        ChatSubAgent.CAPABILITY_PATIENT_PROFILE
                ),
                agent(
                        "patient_profile_agent_backup",
                        "patient_profile_agent",
                        "remote",
                        70,
                        60,
                        ChatSubAgent.CAPABILITY_PATIENT_CONTEXT,
                        ChatSubAgent.CAPABILITY_PATIENT_PROFILE
                ),
                agent(
                        "evidence_support_agent",
                        "evidence_support_agent",
                        "default",
                        50,
                        80,
                        ChatSubAgent.CAPABILITY_MEDICAL_EVIDENCE
                ),
                agent(
                        "risk_assessment_agent",
                        "risk_assessment_agent",
                        "default",
                        50,
                        80,
                        ChatSubAgent.CAPABILITY_RISK_ASSESSMENT
                )
        ), persistenceService);
        SubTaskHandoffRouter router = new SubTaskHandoffRouter(registry, persistenceService);

        String agent = router.selectReplacementAgent(OrchestrationTaskSnapshot.builder()
                .events(List.of(Map.of(
                        "type", TaskEventType.FAILED.name(),
                        "metadata", Map.of(
                                "subTaskId", "sub-impl-1",
                                "targetAgent", "patient_profile_agent",
                                "resolvedAgentName", "patient_profile_agent_primary"
                        )
                )))
                .build(), Map.of(
                "subTaskId", "sub-impl-1",
                "targetAgent", "risk_assessment_agent",
                "goal", "请补充患者画像和病史后继续分析"
        ));

        assertEquals("evidence_support_agent", agent);
    }

    @Test
    void shouldRouteByHealthiestPrimaryImplementationWhenSameCapabilityHasMultipleProviders() {
        OrchestrationPersistenceService persistenceService = mock(OrchestrationPersistenceService.class);
        when(persistenceService.summarizeRecentAgentExecutionHealth(24)).thenReturn(List.of(
                Map.of(
                        "agent", "risk_assessment_agent_primary",
                        "executionCount", 8,
                        "successRate", 0.25D,
                        "stabilityScore", -0.4D,
                        "recent24hExecutionTrend", List.of(
                                Map.of(
                                        "completedCount", 0L,
                                        "failedCount", 2L,
                                        "blockedCount", 1L,
                                        "reroutedCount", 0L,
                                        "successRate", 0D
                                )
                        )
                ),
                Map.of(
                        "agent", "risk_assessment_agent_backup",
                        "executionCount", 8,
                        "successRate", 1D,
                        "stabilityScore", 0.85D,
                        "recent24hExecutionTrend", List.of(
                                Map.of(
                                        "completedCount", 3L,
                                        "failedCount", 0L,
                                        "blockedCount", 0L,
                                        "reroutedCount", 0L,
                                        "successRate", 1D
                                )
                        )
                )
        ));
        SubAgentRegistry registry = new SubAgentRegistry(List.of(
                agent(
                        "risk_assessment_agent_primary",
                        "risk_assessment_agent",
                        "local",
                        35,
                        92,
                        ChatSubAgent.CAPABILITY_PATIENT_CONTEXT,
                        ChatSubAgent.CAPABILITY_RISK_ASSESSMENT
                ),
                agent(
                        "risk_assessment_agent_backup",
                        "risk_assessment_agent",
                        "remote",
                        65,
                        70,
                        ChatSubAgent.CAPABILITY_PATIENT_CONTEXT,
                        ChatSubAgent.CAPABILITY_RISK_ASSESSMENT
                ),
                agent(
                        "patient_profile_agent",
                        "patient_profile_agent",
                        "default",
                        45,
                        82,
                        ChatSubAgent.CAPABILITY_PATIENT_CONTEXT,
                        ChatSubAgent.CAPABILITY_PATIENT_PROFILE
                )
        ), persistenceService);
        SubTaskHandoffRouter router = new SubTaskHandoffRouter(registry, persistenceService);

        String replacement = router.selectReplacementAgent(null, Map.of(
                "targetAgent", "patient_profile_agent",
                "goal", "请继续完成风险评估"
        ));

        assertEquals("risk_assessment_agent", replacement);
        assertEquals("risk_assessment_agent_backup", registry.getAgent(replacement).name());
    }

    @Test
    void shouldExposeProviderHealthInCandidateBreakdown() {
        OrchestrationPersistenceService persistenceService = mock(OrchestrationPersistenceService.class);
        when(persistenceService.summarizeRecentAgentExecutionHealth(24)).thenReturn(List.of(
                Map.of(
                        "agent", "risk_assessment_agent_primary",
                        "executionCount", 8,
                        "successRate", 0.22D,
                        "stabilityScore", -0.4D
                ),
                Map.of(
                        "agent", "risk_assessment_agent_backup",
                        "executionCount", 8,
                        "successRate", 0.96D,
                        "stabilityScore", 0.84D
                )
        ));
        SubAgentRegistry registry = new SubAgentRegistry(List.of(
                agent(
                        "risk_assessment_agent_primary",
                        "risk_assessment_agent",
                        "local",
                        35,
                        92,
                        ChatSubAgent.CAPABILITY_PATIENT_CONTEXT,
                        ChatSubAgent.CAPABILITY_RISK_ASSESSMENT
                ),
                agent(
                        "risk_assessment_agent_backup",
                        "risk_assessment_agent",
                        "remote",
                        65,
                        70,
                        ChatSubAgent.CAPABILITY_PATIENT_CONTEXT,
                        ChatSubAgent.CAPABILITY_RISK_ASSESSMENT
                ),
                agent(
                        "patient_profile_agent",
                        "patient_profile_agent",
                        "default",
                        45,
                        82,
                        ChatSubAgent.CAPABILITY_PATIENT_CONTEXT,
                        ChatSubAgent.CAPABILITY_PATIENT_PROFILE
                )
        ), persistenceService);
        SubTaskHandoffRouter router = new SubTaskHandoffRouter(registry, persistenceService);

        List<Map<String, Object>> candidates = router.scoreReplacementCandidates(null, Map.of(
                "targetAgent", "patient_profile_agent",
                "goal", "请继续完成风险评估"
        ));

        Map<?, ?> breakdown = (Map<?, ?>) candidates.get(0).get("breakdown");
        assertEquals("healthy", breakdown.get("providerHealthState"));
        assertTrue(((Number) breakdown.get("providerHealth")).intValue() > 0);
    }

    @Test
    void shouldPreferNonQuarantinedCandidateWhenHealthyAlternativeExists() {
        OrchestrationPersistenceService persistenceService = mock(OrchestrationPersistenceService.class);
        when(persistenceService.summarizeRecentAgentExecutionHealth(24)).thenReturn(List.of(
                Map.of(
                        "agent", "risk_assessment_agent_local",
                        "executionCount", 9,
                        "successRate", 0.22D,
                        "stabilityScore", -0.4D
                ),
                Map.of(
                        "agent", "risk_assessment_agent_remote",
                        "executionCount", 9,
                        "successRate", 0.95D,
                        "stabilityScore", 0.84D
                ),
                Map.of(
                        "agent", "patient_profile_agent",
                        "executionCount", 8,
                        "successRate", 0.9D,
                        "stabilityScore", 0.8D
                )
        ));
        SubAgentRegistry registry = new SubAgentRegistry(List.of(
                agent(
                        "risk_assessment_agent_local",
                        "risk_assessment_agent",
                        "local",
                        35,
                        92,
                        ChatSubAgent.CAPABILITY_PATIENT_CONTEXT,
                        ChatSubAgent.CAPABILITY_RISK_ASSESSMENT
                ),
                agent(
                        "risk_assessment_agent_remote",
                        "risk_assessment_agent",
                        "remote",
                        65,
                        70,
                        ChatSubAgent.CAPABILITY_PATIENT_CONTEXT,
                        ChatSubAgent.CAPABILITY_RISK_ASSESSMENT
                ),
                agent(
                        "patient_profile_agent",
                        "patient_profile_agent",
                        "default",
                        45,
                        82,
                        ChatSubAgent.CAPABILITY_PATIENT_CONTEXT,
                        ChatSubAgent.CAPABILITY_PATIENT_PROFILE
                )
        ), persistenceService);
        SubTaskHandoffRouter router = new SubTaskHandoffRouter(registry, persistenceService);

        String agent = router.selectReplacementAgent(null, Map.of(
                "targetAgent", "patient_profile_agent",
                "goal", "请继续完成风险评估"
        ));

        assertEquals("risk_assessment_agent", agent);
        assertEquals(SubAgentRegistry.HEALTH_STATE_QUARANTINED, registry.implementationHealthState("risk_assessment_agent_local"));
        assertEquals(SubAgentRegistry.HEALTH_STATE_HEALTHY, registry.implementationHealthState("risk_assessment_agent_remote"));
    }

    @Test
    void shouldQuarantineByRecentFailureStreakBeforeLongTermThreshold() {
        LocalDateTime now = LocalDateTime.now();
        OrchestrationPersistenceService persistenceService = mock(OrchestrationPersistenceService.class);
        when(persistenceService.summarizeRecentAgentExecutionHealth(24)).thenReturn(List.of(
                Map.of(
                        "agent", "risk_assessment_agent_local",
                        "executionCount", 3,
                        "successRate", 0.3D,
                        "stabilityScore", -0.1D,
                        "recentConsecutiveFailureCount", 3,
                        "lastExecutionAt", now.minusMinutes(4)
                ),
                Map.of(
                        "agent", "risk_assessment_agent_remote",
                        "executionCount", 8,
                        "successRate", 0.95D,
                        "stabilityScore", 0.84D,
                        "recentConsecutiveFailureCount", 0,
                        "lastExecutionAt", now.minusMinutes(1)
                ),
                Map.of(
                        "agent", "patient_profile_agent",
                        "executionCount", 8,
                        "successRate", 0.9D,
                        "stabilityScore", 0.8D,
                        "recentConsecutiveFailureCount", 0,
                        "lastExecutionAt", now.minusMinutes(1)
                )
        ));
        SubAgentRegistry registry = new SubAgentRegistry(List.of(
                agent(
                        "risk_assessment_agent_local",
                        "risk_assessment_agent",
                        "local",
                        35,
                        92,
                        ChatSubAgent.CAPABILITY_PATIENT_CONTEXT,
                        ChatSubAgent.CAPABILITY_RISK_ASSESSMENT
                ),
                agent(
                        "risk_assessment_agent_remote",
                        "risk_assessment_agent",
                        "remote",
                        65,
                        70,
                        ChatSubAgent.CAPABILITY_PATIENT_CONTEXT,
                        ChatSubAgent.CAPABILITY_RISK_ASSESSMENT
                ),
                agent(
                        "patient_profile_agent",
                        "patient_profile_agent",
                        "default",
                        45,
                        82,
                        ChatSubAgent.CAPABILITY_PATIENT_CONTEXT,
                        ChatSubAgent.CAPABILITY_PATIENT_PROFILE
                )
        ), persistenceService);
        SubTaskHandoffRouter router = new SubTaskHandoffRouter(registry, persistenceService);

        String replacement = router.selectReplacementAgent(null, Map.of(
                "targetAgent", "patient_profile_agent",
                "goal", "请继续完成风险评估"
        ));
        List<Map<String, Object>> candidates = router.scoreReplacementCandidates(null, Map.of(
                "targetAgent", "patient_profile_agent",
                "goal", "请继续完成风险评估"
        ));

        assertEquals("risk_assessment_agent", replacement);
        assertEquals(SubAgentRegistry.HEALTH_STATE_QUARANTINED, registry.implementationHealthState("risk_assessment_agent_local"));
        assertEquals(SubAgentRegistry.HEALTH_STATE_QUARANTINED, registry.providerHealthState("local"));
        assertTrue(candidates.stream().noneMatch(item -> "risk_assessment_agent_local".equals(item.get("agent"))));
    }

    @Test
    void shouldFallbackToQuarantinedCandidateWhenNoHealthyAlternativeExists() {
        OrchestrationPersistenceService persistenceService = mock(OrchestrationPersistenceService.class);
        when(persistenceService.summarizeRecentAgentExecutionHealth(24)).thenReturn(List.of(
                Map.of(
                        "agent", "risk_assessment_agent_local",
                        "executionCount", 9,
                        "successRate", 0.22D,
                        "stabilityScore", -0.4D
                )
        ));
        SubAgentRegistry registry = new SubAgentRegistry(List.of(
                agent(
                        "risk_assessment_agent_local",
                        "risk_assessment_agent",
                        "local",
                        35,
                        92,
                        ChatSubAgent.CAPABILITY_PATIENT_CONTEXT,
                        ChatSubAgent.CAPABILITY_RISK_ASSESSMENT
                ),
                agent(
                        "patient_profile_agent",
                        "patient_profile_agent",
                        "default",
                        45,
                        82,
                        ChatSubAgent.CAPABILITY_PATIENT_CONTEXT,
                        ChatSubAgent.CAPABILITY_PATIENT_PROFILE
                )
        ), persistenceService);
        SubTaskHandoffRouter router = new SubTaskHandoffRouter(registry, persistenceService);

        String agent = router.selectReplacementAgent(null, Map.of(
                "targetAgent", "patient_profile_agent",
                "goal", "请继续完成风险评估"
        ));

        assertEquals("risk_assessment_agent", agent);
    }

    @Test
    void shouldPreferRecentlySuccessfulAgentWhenCapabilitiesTie() {
        SubTaskHandoffRouter router = new SubTaskHandoffRouter(registry(), null);

        String agent = router.selectReplacementAgent(OrchestrationTaskSnapshot.builder()
                .events(List.of(
                        Map.of(
                                "type", TaskEventType.COMPLETED.name(),
                                "metadata", Map.of(
                                        "subTaskId", "sub-7",
                                        "targetAgent", "patient_profile_agent"
                                )
                        ),
                        Map.of(
                                "type", TaskEventType.FAILED.name(),
                                "metadata", Map.of(
                                        "subTaskId", "sub-7",
                                        "targetAgent", "evidence_support_agent"
                                )
                        )
                ))
                .build(), Map.of(
                "subTaskId", "sub-7",
                "targetAgent", "risk_assessment_agent",
                "goal", "请补充患者上下文后继续分析"
        ));

        assertEquals("patient_profile_agent", agent);
    }

    @Test
    void shouldPreferAgentWithStrongerLongTermPerformanceWhenSignalsTie() {
        OrchestrationPersistenceService persistenceService = mock(OrchestrationPersistenceService.class);
        when(persistenceService.summarizeRecentAgentPerformance()).thenReturn(Map.of(
                "patient_profile_agent", Map.of("completed", 3, "failed", 0, "blocked", 0, "rerouted", 0, "score", 54),
                "evidence_support_agent", Map.of("completed", 0, "failed", 2, "blocked", 1, "rerouted", 0, "score", -46)
        ));
        when(persistenceService.summarizeRecentAgentExecutionHealth(24)).thenReturn(List.of());
        SubTaskHandoffRouter router = new SubTaskHandoffRouter(registry(), persistenceService);

        String agent = router.selectReplacementAgent(null, Map.of(
                "targetAgent", "risk_assessment_agent",
                "goal", "请继续分析"
        ));

        assertEquals("patient_profile_agent", agent);
    }

    @Test
    void shouldPreferAgentWithBetterExecutionHealthWhenOtherSignalsTie() {
        OrchestrationPersistenceService persistenceService = mock(OrchestrationPersistenceService.class);
        when(persistenceService.summarizeRecentAgentPerformance()).thenReturn(Map.of());
        when(persistenceService.summarizeRecentAgentExecutionHealth(24)).thenReturn(List.of(
                Map.of(
                        "agent", "patient_profile_agent",
                        "executionCount", 8,
                        "successRate", 0.875D,
                        "stabilityScore", 0.625D,
                        "recent24hExecutionTrend", List.of()
                ),
                Map.of(
                        "agent", "evidence_support_agent",
                        "executionCount", 8,
                        "successRate", 0.25D,
                        "stabilityScore", -0.25D,
                        "recent24hExecutionTrend", List.of()
                )
        ));
        SubTaskHandoffRouter router = new SubTaskHandoffRouter(registry(), persistenceService);

        String agent = router.selectReplacementAgent(null, Map.of(
                "targetAgent", "risk_assessment_agent",
                "goal", "请继续分析"
        ));

        assertEquals("patient_profile_agent", agent);
    }

    @Test
    void shouldPreferAgentWithStrongerRecentTrendOverOlderAverage() {
        OrchestrationPersistenceService persistenceService = mock(OrchestrationPersistenceService.class);
        when(persistenceService.summarizeRecentAgentPerformance()).thenReturn(Map.of());
        when(persistenceService.summarizeRecentAgentExecutionHealth(24)).thenReturn(List.of(
                Map.of(
                        "agent", "patient_profile_agent",
                        "executionCount", 10,
                        "successRate", 0.6D,
                        "stabilityScore", 0.15D,
                        "recent24hExecutionTrend", List.of(
                                Map.of(
                                        "completedCount", 0L,
                                        "failedCount", 2L,
                                        "blockedCount", 1L,
                                        "reroutedCount", 0L,
                                        "successRate", 0D
                                ),
                                Map.of(
                                        "completedCount", 3L,
                                        "failedCount", 0L,
                                        "blockedCount", 0L,
                                        "reroutedCount", 0L,
                                        "successRate", 1D
                                )
                        )
                ),
                Map.of(
                        "agent", "evidence_support_agent",
                        "executionCount", 10,
                        "successRate", 0.7D,
                        "stabilityScore", 0.25D,
                        "recent24hExecutionTrend", List.of(
                                Map.of(
                                        "completedCount", 4L,
                                        "failedCount", 0L,
                                        "blockedCount", 0L,
                                        "reroutedCount", 0L,
                                        "successRate", 1D
                                ),
                                Map.of(
                                        "completedCount", 0L,
                                        "failedCount", 2L,
                                        "blockedCount", 1L,
                                        "reroutedCount", 0L,
                                        "successRate", 0D
                                )
                        )
                )
        ));
        SubTaskHandoffRouter router = new SubTaskHandoffRouter(registry(), persistenceService);

        String agent = router.selectReplacementAgent(null, Map.of(
                "targetAgent", "risk_assessment_agent",
                "goal", "请继续分析"
        ));

        assertEquals("patient_profile_agent", agent);
    }

    @Test
    void shouldRequireManualReviewWhenCandidatesAreCloseAndRecentVolatilityIsHigh() {
        SubTaskHandoffRouter router = new SubTaskHandoffRouter(registry(), null);

        Map<String, Object> summary = router.summarizeRecommendation(List.of(
                Map.of(
                        "agent", "patient_profile_agent",
                        "score", 112,
                        "breakdown", Map.of(
                                "executionHealthStats", Map.of(
                                        "recent24hExecutionTrend", List.of(
                                                Map.of(
                                                        "completedCount", 3L,
                                                        "failedCount", 0L,
                                                        "blockedCount", 0L,
                                                        "reroutedCount", 0L,
                                                        "successRate", 1D
                                                ),
                                                Map.of(
                                                        "completedCount", 0L,
                                                        "failedCount", 2L,
                                                        "blockedCount", 1L,
                                                        "reroutedCount", 0L,
                                                        "successRate", 0D
                                                )
                                        )
                                )
                        )
                ),
                Map.of(
                        "agent", "evidence_support_agent",
                        "score", 104,
                        "breakdown", Map.of(
                                "executionHealthStats", Map.of(
                                        "recent24hExecutionTrend", List.of(
                                                Map.of(
                                                        "completedCount", 2L,
                                                        "failedCount", 0L,
                                                        "blockedCount", 0L,
                                                        "reroutedCount", 0L,
                                                        "successRate", 1D
                                                ),
                                                Map.of(
                                                        "completedCount", 2L,
                                                        "failedCount", 0L,
                                                        "blockedCount", 0L,
                                                        "reroutedCount", 0L,
                                                        "successRate", 1D
                                                )
                                        )
                                )
                        )
                )
        ));

        assertEquals("low", summary.get("confidence"));
        assertEquals(Boolean.TRUE, summary.get("needsReview"));
        assertEquals("high_recent_volatility", summary.get("reviewTrigger"));
        assertTrue(((Double) summary.get("volatilityScore")) > 14D);
    }

    private SubAgentRegistry registry() {
        return new SubAgentRegistry(List.of(
                agent(
                        "risk_assessment_agent",
                        ChatSubAgent.CAPABILITY_PATIENT_CONTEXT,
                        ChatSubAgent.CAPABILITY_RISK_ASSESSMENT
                ),
                agent(
                        "patient_profile_agent",
                        ChatSubAgent.CAPABILITY_PATIENT_CONTEXT,
                        ChatSubAgent.CAPABILITY_PATIENT_PROFILE,
                        ChatSubAgent.CAPABILITY_MEDPLUM_INSIGHT
                ),
                agent(
                        "evidence_support_agent",
                        ChatSubAgent.CAPABILITY_PATIENT_CONTEXT,
                        ChatSubAgent.CAPABILITY_MEDICAL_EVIDENCE,
                        ChatSubAgent.CAPABILITY_KNOWLEDGE_GRAPH
                )
        ));
    }

    private ChatSubAgent agent(String name, String... capabilities) {
        return agent(name, name, "default", 50, 80, capabilities);
    }

    private ChatSubAgent agent(String name,
                               String routeKey,
                               String provider,
                               int estimatedCost,
                               int baselineHealth,
                               String... capabilities) {
        return new ChatSubAgent() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public String routeKey() {
                return routeKey;
            }

            @Override
            public String description() {
                return "test";
            }

            @Override
            public List<AgentTool> tools() {
                return List.of();
            }

            @Override
            public List<String> capabilities() {
                return List.of(capabilities);
            }

            @Override
            public String provider() {
                return provider;
            }

            @Override
            public int estimatedCostScore() {
                return estimatedCost;
            }

            @Override
            public int baselineHealthScore() {
                return baselineHealth;
            }

            @Override
            public SubAgentResult execute(AgentState state, String delegatedTask) {
                return new SubAgentResult(name, true, true, "ok", List.of());
            }
        };
    }
}
