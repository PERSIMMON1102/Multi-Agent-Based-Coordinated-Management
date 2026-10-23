package com.medical.agent.service.react.agent;

import com.medical.agent.service.react.model.AgentState;
import com.medical.agent.service.react.orchestration.OrchestrationPersistenceService;
import com.medical.agent.service.react.tool.AgentTool;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SubAgentRegistryTest {

    @Test
    void shouldPreferHealthierImplementationWithinSameRoute() {
        OrchestrationPersistenceService persistenceService = mock(OrchestrationPersistenceService.class);
        when(persistenceService.summarizeRecentAgentExecutionHealth(24)).thenReturn(List.of(
                Map.of(
                        "agent", "risk_assessment_agent_primary",
                        "executionCount", 10,
                        "successRate", 0.2D,
                        "stabilityScore", -0.35D,
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
                        "executionCount", 10,
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
                        "risk_assessment_agent_primary",
                        "risk_assessment_agent",
                        "local",
                        25,
                        95,
                        ChatSubAgent.CAPABILITY_RISK_ASSESSMENT
                ),
                agent(
                        "risk_assessment_agent_backup",
                        "risk_assessment_agent",
                        "remote",
                        60,
                        72,
                        ChatSubAgent.CAPABILITY_RISK_ASSESSMENT
                )
        ), persistenceService);

        ChatSubAgent primary = registry.getPrimaryAgent("risk_assessment_agent");

        assertEquals("risk_assessment_agent_backup", primary.name());
    }

    @Test
    void shouldFallbackToBaselineOrderingWhenRuntimeHealthIsMissing() {
        OrchestrationPersistenceService persistenceService = mock(OrchestrationPersistenceService.class);
        when(persistenceService.summarizeRecentAgentExecutionHealth(24)).thenReturn(List.of());
        SubAgentRegistry registry = new SubAgentRegistry(List.of(
                agent(
                        "risk_assessment_agent_primary",
                        "risk_assessment_agent",
                        "local",
                        25,
                        95,
                        ChatSubAgent.CAPABILITY_RISK_ASSESSMENT
                ),
                agent(
                        "risk_assessment_agent_backup",
                        "risk_assessment_agent",
                        "remote",
                        60,
                        72,
                        ChatSubAgent.CAPABILITY_RISK_ASSESSMENT
                )
        ), persistenceService);

        ChatSubAgent primary = registry.getPrimaryAgent("risk_assessment_agent");

        assertEquals("risk_assessment_agent_primary", primary.name());
    }

    @Test
    void shouldReuseSingleExecutionHealthQueryWhenListingPrimaryAgents() {
        OrchestrationPersistenceService persistenceService = mock(OrchestrationPersistenceService.class);
        when(persistenceService.summarizeRecentAgentExecutionHealth(24)).thenReturn(List.of());
        SubAgentRegistry registry = new SubAgentRegistry(List.of(
                agent("risk_assessment_agent", ChatSubAgent.CAPABILITY_RISK_ASSESSMENT),
                agent("patient_profile_agent", ChatSubAgent.CAPABILITY_PATIENT_PROFILE),
                agent("evidence_support_agent", ChatSubAgent.CAPABILITY_MEDICAL_EVIDENCE)
        ), persistenceService);

        registry.listPrimaryAgents();

        verify(persistenceService, times(1)).summarizeRecentAgentExecutionHealth(24);
    }

    @Test
    void shouldExposeQuarantinedImplementationHealthStateAndPrimarySelection() {
        OrchestrationPersistenceService persistenceService = mock(OrchestrationPersistenceService.class);
        when(persistenceService.summarizeRecentAgentExecutionHealth(24)).thenReturn(List.of(
                Map.of(
                        "agent", "risk_assessment_agent_primary",
                        "executionCount", 8,
                        "successRate", 0.25D,
                        "stabilityScore", -0.35D
                ),
                Map.of(
                        "agent", "risk_assessment_agent_backup",
                        "executionCount", 8,
                        "successRate", 0.95D,
                        "stabilityScore", 0.82D
                )
        ));
        SubAgentRegistry registry = new SubAgentRegistry(List.of(
                agent(
                        "risk_assessment_agent_primary",
                        "risk_assessment_agent",
                        "local",
                        25,
                        95,
                        ChatSubAgent.CAPABILITY_RISK_ASSESSMENT
                ),
                agent(
                        "risk_assessment_agent_backup",
                        "risk_assessment_agent",
                        "remote",
                        60,
                        72,
                        ChatSubAgent.CAPABILITY_RISK_ASSESSMENT
                )
        ), persistenceService);

        assertEquals(SubAgentRegistry.HEALTH_STATE_QUARANTINED, registry.implementationHealthState("risk_assessment_agent_primary"));
        assertEquals(SubAgentRegistry.HEALTH_STATE_HEALTHY, registry.implementationHealthState("risk_assessment_agent_backup"));
        assertFalse(registry.isPrimaryImplementation("risk_assessment_agent_primary"));
        assertTrue(registry.isPrimaryImplementation("risk_assessment_agent_backup"));
        assertEquals("risk_assessment_agent_backup", registry.listRouteImplementations("risk_assessment_agent").get(0).name());
    }

    @Test
    void shouldQuarantineImplementationOnRecentFailureStreakWithinCooldownWindow() {
        LocalDateTime now = LocalDateTime.now();
        OrchestrationPersistenceService persistenceService = mock(OrchestrationPersistenceService.class);
        when(persistenceService.summarizeRecentAgentExecutionHealth(24)).thenReturn(List.of(
                Map.of(
                        "agent", "risk_assessment_agent_primary",
                        "executionCount", 3,
                        "successRate", 0.34D,
                        "stabilityScore", -0.1D,
                        "recentConsecutiveFailureCount", 3,
                        "lastExecutionAt", now.minusMinutes(5)
                ),
                Map.of(
                        "agent", "risk_assessment_agent_backup",
                        "executionCount", 3,
                        "successRate", 0.8D,
                        "stabilityScore", 0.6D,
                        "recentConsecutiveFailureCount", 0,
                        "lastExecutionAt", now.minusMinutes(2)
                )
        ));
        SubAgentRegistry registry = new SubAgentRegistry(List.of(
                agent(
                        "risk_assessment_agent_primary",
                        "risk_assessment_agent",
                        "local",
                        25,
                        95,
                        ChatSubAgent.CAPABILITY_RISK_ASSESSMENT
                ),
                agent(
                        "risk_assessment_agent_backup",
                        "risk_assessment_agent",
                        "remote",
                        60,
                        72,
                        ChatSubAgent.CAPABILITY_RISK_ASSESSMENT
                )
        ), persistenceService);

        assertEquals(SubAgentRegistry.HEALTH_STATE_QUARANTINED, registry.implementationHealthState("risk_assessment_agent_primary"));
        assertEquals("risk_assessment_agent_backup", registry.getPrimaryAgent("risk_assessment_agent").name());
    }

    @Test
    void shouldDowngradeExpiredFailureStreakToLongTermHealthAssessment() {
        LocalDateTime now = LocalDateTime.now();
        OrchestrationPersistenceService persistenceService = mock(OrchestrationPersistenceService.class);
        when(persistenceService.summarizeRecentAgentExecutionHealth(24)).thenReturn(List.of(
                Map.of(
                        "agent", "risk_assessment_agent_primary",
                        "executionCount", 3,
                        "successRate", 0.34D,
                        "stabilityScore", -0.1D,
                        "recentConsecutiveFailureCount", 3,
                        "lastExecutionAt", now.minusHours(2)
                )
        ));
        SubAgentRegistry registry = new SubAgentRegistry(List.of(
                agent(
                        "risk_assessment_agent_primary",
                        "risk_assessment_agent",
                        "local",
                        25,
                        95,
                        ChatSubAgent.CAPABILITY_RISK_ASSESSMENT
                )
        ), persistenceService);

        assertEquals(SubAgentRegistry.HEALTH_STATE_DEGRADED, registry.implementationHealthState("risk_assessment_agent_primary"));
    }

    @Test
    void shouldKeepSingleImplementationRouteAvailableEvenWhenQuarantined() {
        OrchestrationPersistenceService persistenceService = mock(OrchestrationPersistenceService.class);
        when(persistenceService.summarizeRecentAgentExecutionHealth(24)).thenReturn(List.of(
                Map.of(
                        "agent", "patient_profile_agent_primary",
                        "executionCount", 9,
                        "successRate", 0.22D,
                        "stabilityScore", -0.4D
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
                )
        ), persistenceService);

        ChatSubAgent primary = registry.getPrimaryAgent("patient_profile_agent");

        assertEquals(SubAgentRegistry.HEALTH_STATE_QUARANTINED, registry.implementationHealthState("patient_profile_agent_primary"));
        assertEquals("patient_profile_agent_primary", primary.name());
    }

    @Test
    void shouldPreferHealthyProviderWhenRouteImplementationsTieOnDirectHealth() {
        OrchestrationPersistenceService persistenceService = mock(OrchestrationPersistenceService.class);
        when(persistenceService.summarizeRecentAgentExecutionHealth(24)).thenReturn(List.of(
                Map.of(
                        "agent", "patient_profile_agent_local_observer",
                        "executionCount", 9,
                        "successRate", 0.15D,
                        "stabilityScore", -0.45D
                ),
                Map.of(
                        "agent", "evidence_support_agent_remote_observer",
                        "executionCount", 9,
                        "successRate", 0.95D,
                        "stabilityScore", 0.85D
                )
        ));
        SubAgentRegistry registry = new SubAgentRegistry(List.of(
                agent(
                        "risk_assessment_agent_local",
                        "risk_assessment_agent",
                        "local",
                        35,
                        90,
                        ChatSubAgent.CAPABILITY_RISK_ASSESSMENT
                ),
                agent(
                        "risk_assessment_agent_remote",
                        "risk_assessment_agent",
                        "remote",
                        35,
                        90,
                        ChatSubAgent.CAPABILITY_RISK_ASSESSMENT
                ),
                agent(
                        "patient_profile_agent_local_observer",
                        "patient_profile_agent",
                        "local",
                        55,
                        70,
                        ChatSubAgent.CAPABILITY_PATIENT_PROFILE
                ),
                agent(
                        "evidence_support_agent_remote_observer",
                        "evidence_support_agent",
                        "remote",
                        55,
                        70,
                        ChatSubAgent.CAPABILITY_MEDICAL_EVIDENCE
                )
        ), persistenceService);

        assertEquals(SubAgentRegistry.HEALTH_STATE_QUARANTINED, registry.providerHealthState("local"));
        assertEquals(SubAgentRegistry.HEALTH_STATE_HEALTHY, registry.providerHealthState("remote"));
        assertEquals("risk_assessment_agent_remote", registry.getPrimaryAgent("risk_assessment_agent").name());
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
