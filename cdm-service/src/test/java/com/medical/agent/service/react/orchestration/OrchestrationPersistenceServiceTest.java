package com.medical.agent.service.react.orchestration;

import com.medical.agent.model.mongo.OrchestrationTaskSnapshot;
import com.medical.agent.model.mongo.repository.OrchestrationTaskSnapshotRepository;
import com.medical.agent.service.testsupport.RepositoryProxyFactory;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrchestrationPersistenceServiceTest {

    @Test
    void shouldNormalizeTaskStateAfterSkippingBlockedSubTask() {
        AtomicReference<OrchestrationTaskSnapshot> holder = new AtomicReference<>();
        OrchestrationTaskSnapshotRepository repository = RepositoryProxyFactory.create(OrchestrationTaskSnapshotRepository.class, Map.of(
                "save", args -> {
                    OrchestrationTaskSnapshot entity = (OrchestrationTaskSnapshot) args[0];
                    holder.set(entity);
                    return entity;
                },
                "findFirstByTaskIdOrderByUpdatedAtDesc", args -> {
                    OrchestrationTaskSnapshot snapshot = holder.get();
                    return snapshot != null && args[0].equals(snapshot.getTaskId()) ? Optional.of(snapshot) : Optional.empty();
                },
                "findBySessionIdOrderByUpdatedAtDesc", args -> {
                    OrchestrationTaskSnapshot snapshot = holder.get();
                    return snapshot != null && args[0].equals(snapshot.getSessionId()) ? List.of(snapshot) : List.of();
                },
                "findFirstBySessionIdOrderByUpdatedAtDesc", args -> {
                    OrchestrationTaskSnapshot snapshot = holder.get();
                    return snapshot != null && args[0].equals(snapshot.getSessionId()) ? Optional.of(snapshot) : Optional.empty();
                },
                "findTop200ByOrderByUpdatedAtDesc", args -> holder.get() == null ? List.of() : List.of(holder.get()),
                "findByStatusInOrderByUpdatedAtAsc", args -> holder.get() == null ? List.of() : List.of(holder.get()),
                "findByStatusInAndNextRunAtLessThanEqualOrderByUpdatedAtAsc", args -> holder.get() == null ? List.of() : List.of(holder.get())
        ));
        OrchestrationPersistenceService service = new OrchestrationPersistenceService(repository);

        OrchestrationTaskSnapshot snapshot = OrchestrationTaskSnapshot.builder()
                .id("mongo-1")
                .taskId("task-1")
                .sessionId("session-1")
                .status(TaskStatus.BLOCKED.name())
                .controlStatus(OrchestrationControlStatus.ACTIVE.name())
                .blockers(List.of("子任务阻塞：风险评估等待协助"))
                .subTasks(new ArrayList<>(List.of(
                        mapOf(
                                "subTaskId", "sub-1",
                                "goal", "请补充患者画像后继续分析",
                                "targetAgent", "risk_assessment_agent",
                                "priority", 100,
                                "status", SubTaskStatus.BLOCKED.name(),
                                "dependencies", List.of()
                        )
                )))
                .events(new ArrayList<>())
                .updatedAt(LocalDateTime.now())
                .build();
        holder.set(snapshot);

        Optional<OrchestrationTaskSnapshot> updated = service.skipSubTask("task-1", "sub-1", "patient_profile_agent", "admin", "改走画像补位");

        assertFalse(updated.isEmpty());
        OrchestrationTaskSnapshot saved = updated.orElseThrow();
        assertEquals(TaskStatus.WAITING_SUPPORT.name(), saved.getStatus());
        assertEquals(OrchestrationControlStatus.ACTIVE.name(), saved.getControlStatus());
        assertNotNull(saved.getNextRunAt());
        assertEquals(List.of(), saved.getBlockers());
        assertEquals(2, saved.getSubTasks().size());
        assertEquals(SubTaskStatus.SKIPPED.name(), saved.getSubTasks().get(0).get("status"));
        assertEquals(SubTaskStatus.READY.name(), saved.getSubTasks().get(1).get("status"));
    }

    @Test
    void shouldIgnoreSnapshotLookupFailureWhenMongoIsUnavailable() {
        OrchestrationTaskSnapshotRepository repository = RepositoryProxyFactory.create(OrchestrationTaskSnapshotRepository.class, Map.of(
                "findBySessionIdOrderByUpdatedAtDesc", args -> {
                    throw new RuntimeException("mongo unavailable");
                }
        ));
        OrchestrationPersistenceService service = new OrchestrationPersistenceService(repository);

        Optional<OrchestrationTaskSnapshot> snapshot = service.findLatestActiveBySessionId("session-down");

        assertTrue(snapshot.isEmpty());
    }

    @Test
    void shouldIgnoreRecentSnapshotSummaryFailureWhenMongoIsUnavailable() {
        OrchestrationTaskSnapshotRepository repository = RepositoryProxyFactory.create(OrchestrationTaskSnapshotRepository.class, Map.of(
                "findTop200ByOrderByUpdatedAtDesc", args -> {
                    throw new RuntimeException("mongo unavailable");
                }
        ));
        OrchestrationPersistenceService service = new OrchestrationPersistenceService(repository);

        List<Map<String, Object>> summary = service.summarizeRecentAgentExecutionHealth(24);

        assertTrue(summary.isEmpty());
    }

    @Test
    void shouldExcludeSkippedSubTasksFromAgentExecutionHealthPenalty() {
        AtomicReference<OrchestrationTaskSnapshot> holder = new AtomicReference<>();
        OrchestrationTaskSnapshotRepository repository = RepositoryProxyFactory.create(OrchestrationTaskSnapshotRepository.class, Map.of(
                "save", args -> {
                    OrchestrationTaskSnapshot entity = (OrchestrationTaskSnapshot) args[0];
                    holder.set(entity);
                    return entity;
                },
                "findFirstByTaskIdOrderByUpdatedAtDesc", args -> Optional.ofNullable(holder.get()),
                "findBySessionIdOrderByUpdatedAtDesc", args -> holder.get() == null ? List.of() : List.of(holder.get()),
                "findFirstBySessionIdOrderByUpdatedAtDesc", args -> Optional.ofNullable(holder.get()),
                "findTop200ByOrderByUpdatedAtDesc", args -> holder.get() == null ? List.of() : List.of(holder.get()),
                "findByStatusInOrderByUpdatedAtAsc", args -> holder.get() == null ? List.of() : List.of(holder.get()),
                "findByStatusInAndNextRunAtLessThanEqualOrderByUpdatedAtAsc", args -> holder.get() == null ? List.of() : List.of(holder.get())
        ));
        OrchestrationPersistenceService service = new OrchestrationPersistenceService(repository);

        LocalDateTime now = LocalDateTime.now().withMinute(0).withSecond(0).withNano(0);
        holder.set(OrchestrationTaskSnapshot.builder()
                .id("mongo-2")
                .taskId("task-2")
                .sessionId("session-2")
                .status(TaskStatus.WAITING_SUPPORT.name())
                .controlStatus(OrchestrationControlStatus.ACTIVE.name())
                .events(new ArrayList<>(List.of(
                        event("SUBTASK_SKIPPED", now.plusMinutes(5), Map.of(
                                "targetAgent", "risk_assessment_agent",
                                "resolvedAgentName", "risk_assessment_agent_primary",
                                "resolvedAgentProvider", "local"
                        )),
                        event("SUBTASK_HANDOFF", now.plusMinutes(15), Map.of(
                                "targetAgent", "risk_assessment_agent",
                                "resolvedAgentName", "risk_assessment_agent_primary",
                                "resolvedAgentProvider", "local"
                        )),
                        event("COMPLETED", now.plusMinutes(25), Map.of(
                                "targetAgent", "risk_assessment_agent",
                                "resolvedAgentName", "risk_assessment_agent_primary",
                                "resolvedAgentProvider", "local"
                        ))
                )))
                .updatedAt(now.plusMinutes(30))
                .build());

        List<Map<String, Object>> health = service.summarizeRecentAgentExecutionHealth(24);
        assertEquals(1, health.size());
        Map<String, Object> row = health.get(0);
        assertEquals("risk_assessment_agent_primary", row.get("agent"));
        assertEquals(1L, row.get("completedCount"));
        assertEquals(1L, row.get("reroutedCount"));
        assertEquals(0L, row.get("failedCount"));
        assertEquals(0L, row.get("blockedCount"));
        assertEquals(2L, row.get("executionCount"));
        assertTrue(((Number) row.get("successRate")).doubleValue() > 0.4D);
    }

    @Test
    void shouldAggregateRecentAgentPerformanceByResolvedAgentName() {
        AtomicReference<OrchestrationTaskSnapshot> holder = new AtomicReference<>();
        OrchestrationTaskSnapshotRepository repository = RepositoryProxyFactory.create(OrchestrationTaskSnapshotRepository.class, Map.of(
                "save", args -> {
                    OrchestrationTaskSnapshot entity = (OrchestrationTaskSnapshot) args[0];
                    holder.set(entity);
                    return entity;
                },
                "findFirstByTaskIdOrderByUpdatedAtDesc", args -> Optional.ofNullable(holder.get()),
                "findBySessionIdOrderByUpdatedAtDesc", args -> holder.get() == null ? List.of() : List.of(holder.get()),
                "findFirstBySessionIdOrderByUpdatedAtDesc", args -> Optional.ofNullable(holder.get()),
                "findTop200ByOrderByUpdatedAtDesc", args -> holder.get() == null ? List.of() : List.of(holder.get()),
                "findByStatusInOrderByUpdatedAtAsc", args -> holder.get() == null ? List.of() : List.of(holder.get()),
                "findByStatusInAndNextRunAtLessThanEqualOrderByUpdatedAtAsc", args -> holder.get() == null ? List.of() : List.of(holder.get())
        ));
        OrchestrationPersistenceService service = new OrchestrationPersistenceService(repository);

        holder.set(OrchestrationTaskSnapshot.builder()
                .id("mongo-2b")
                .taskId("task-2b")
                .sessionId("session-2b")
                .status(TaskStatus.RUNNING.name())
                .controlStatus(OrchestrationControlStatus.ACTIVE.name())
                .events(new ArrayList<>(List.of(
                        event("COMPLETED", LocalDateTime.now().minusMinutes(20), Map.of(
                                "targetAgent", "risk_assessment_agent",
                                "resolvedAgentName", "risk_assessment_agent_primary"
                        )),
                        event("FAILED", LocalDateTime.now().minusMinutes(10), Map.of(
                                "targetAgent", "risk_assessment_agent",
                                "resolvedAgentName", "risk_assessment_agent_backup"
                        ))
                )))
                .updatedAt(LocalDateTime.now())
                .build());

        Map<String, Map<String, Integer>> performance = service.summarizeRecentAgentPerformance();

        assertEquals(2, performance.size());
        assertEquals(18, performance.get("risk_assessment_agent_primary").get("score"));
        assertEquals(-18, performance.get("risk_assessment_agent_backup").get("score"));
    }

    @Test
    void shouldExposeRecentConsecutiveFailureStreakInExecutionHealthSummary() {
        AtomicReference<OrchestrationTaskSnapshot> holder = new AtomicReference<>();
        OrchestrationTaskSnapshotRepository repository = RepositoryProxyFactory.create(OrchestrationTaskSnapshotRepository.class, Map.of(
                "save", args -> {
                    OrchestrationTaskSnapshot entity = (OrchestrationTaskSnapshot) args[0];
                    holder.set(entity);
                    return entity;
                },
                "findFirstByTaskIdOrderByUpdatedAtDesc", args -> Optional.ofNullable(holder.get()),
                "findBySessionIdOrderByUpdatedAtDesc", args -> holder.get() == null ? List.of() : List.of(holder.get()),
                "findFirstBySessionIdOrderByUpdatedAtDesc", args -> Optional.ofNullable(holder.get()),
                "findTop200ByOrderByUpdatedAtDesc", args -> holder.get() == null ? List.of() : List.of(holder.get()),
                "findByStatusInOrderByUpdatedAtAsc", args -> holder.get() == null ? List.of() : List.of(holder.get()),
                "findByStatusInAndNextRunAtLessThanEqualOrderByUpdatedAtAsc", args -> holder.get() == null ? List.of() : List.of(holder.get())
        ));
        OrchestrationPersistenceService service = new OrchestrationPersistenceService(repository);

        LocalDateTime now = LocalDateTime.now().withSecond(0).withNano(0);
        holder.set(OrchestrationTaskSnapshot.builder()
                .id("mongo-streak")
                .taskId("task-streak")
                .sessionId("session-streak")
                .status(TaskStatus.RUNNING.name())
                .controlStatus(OrchestrationControlStatus.ACTIVE.name())
                .events(new ArrayList<>(List.of(
                        event("COMPLETED", now.minusMinutes(20), Map.of(
                                "targetAgent", "risk_assessment_agent",
                                "resolvedAgentName", "risk_assessment_agent_primary"
                        )),
                        event("FAILED", now.minusMinutes(12), Map.of(
                                "targetAgent", "risk_assessment_agent",
                                "resolvedAgentName", "risk_assessment_agent_primary"
                        )),
                        event("SUBTASK_HANDOFF", now.minusMinutes(8), Map.of(
                                "targetAgent", "risk_assessment_agent",
                                "resolvedAgentName", "risk_assessment_agent_primary"
                        )),
                        event("BLOCKED", now.minusMinutes(3), Map.of(
                                "targetAgent", "risk_assessment_agent",
                                "resolvedAgentName", "risk_assessment_agent_primary"
                        ))
                )))
                .updatedAt(now)
                .build());

        List<Map<String, Object>> health = service.summarizeRecentAgentExecutionHealth(24);

        assertEquals(1, health.size());
        Map<String, Object> row = health.get(0);
        assertEquals("risk_assessment_agent_primary", row.get("agent"));
        assertEquals(3L, ((Number) row.get("recentConsecutiveFailureCount")).longValue());
        assertEquals(now.minusMinutes(3), row.get("lastExecutionAt"));
        assertEquals(now.minusMinutes(20), row.get("lastSuccessfulAt"));
    }

    @Test
    void shouldPersistLatestContractSummaryOnSnapshotTopLevel() {
        AtomicReference<OrchestrationTaskSnapshot> holder = new AtomicReference<>();
        OrchestrationTaskSnapshotRepository repository = RepositoryProxyFactory.create(OrchestrationTaskSnapshotRepository.class, Map.of(
                "save", args -> {
                    OrchestrationTaskSnapshot entity = (OrchestrationTaskSnapshot) args[0];
                    holder.set(entity);
                    return entity;
                },
                "findFirstByTaskIdOrderByUpdatedAtDesc", args -> Optional.ofNullable(holder.get()),
                "findBySessionIdOrderByUpdatedAtDesc", args -> holder.get() == null ? List.of() : List.of(holder.get()),
                "findFirstBySessionIdOrderByUpdatedAtDesc", args -> Optional.ofNullable(holder.get()),
                "findTop200ByOrderByUpdatedAtDesc", args -> holder.get() == null ? List.of() : List.of(holder.get()),
                "findByStatusInOrderByUpdatedAtAsc", args -> holder.get() == null ? List.of() : List.of(holder.get()),
                "findByStatusInAndNextRunAtLessThanEqualOrderByUpdatedAtAsc", args -> holder.get() == null ? List.of() : List.of(holder.get())
        ));
        OrchestrationPersistenceService service = new OrchestrationPersistenceService(repository);

        com.medical.agent.service.react.model.AgentState state = new com.medical.agent.service.react.model.AgentState();
        state.setSessionId("session-contract");
        state.setOwnerRole("doctor");
        state.setOwnerUserId(7L);
        state.setPatientId(88L);
        state.setUserMessage("请继续分析");
        state.setProgressSummary("已完成画像补充");
        state.setNextAction("等待继续推进");

        OrchestrationTrace trace = new OrchestrationTrace(
                "task-contract",
                "请继续分析",
                TaskStatus.BLOCKED,
                "risk_assessment_agent",
                List.of(),
                List.of(new SubTaskTrace(
                        "sub-contract",
                        "请补充患者画像后继续分析",
                        "risk_assessment_agent",
                        100,
                        SubTaskStatus.BLOCKED,
                        List.of()
                )),
                List.of(new TaskEvent(
                        TaskEventType.BLOCKED,
                        LocalDateTime.now(),
                        "子任务需要人工确认",
                        Map.of(
                                "subTaskId", "sub-contract",
                                "targetAgent", "risk_assessment_agent",
                                "failureCategory", "context_gap",
                                "missingCapabilities", List.of("patient_context"),
                                "suggestedHandoffAgent", "patient_profile_agent",
                                "contractSchema", "sub-agent-result",
                                "contractVersion", "0.9",
                                "contractCompatibility", Map.of(
                                        "expectedSchema", "sub-agent-result",
                                        "expectedVersion", "1.0",
                                        "schemaCompatible", true,
                                        "versionCompatible", false,
                                        "validated", false
                                ),
                                "contractValidationErrors", List.of("unsupported_contract_version:0.9")
                        )
                )),
                null
        );

        service.save(state, trace);

        OrchestrationTaskSnapshot saved = holder.get();
        assertNotNull(saved);
        assertEquals(Boolean.FALSE, saved.getLatestContractValidated());
        assertEquals("sub-agent-result", saved.getLatestContractSchema());
        assertEquals("0.9", saved.getLatestContractVersion());
        assertEquals("schema_only", saved.getLatestContractCompatibility());
        assertEquals(1, saved.getLatestContractValidationErrorCount());
        assertEquals(List.of("unsupported_contract_version:0.9"), saved.getLatestContractValidationErrors());
        assertEquals("context_gap", saved.getLatestContractFailureCategory());
        assertEquals("patient_profile_agent", saved.getLatestContractSuggestedHandoffAgent());
        assertEquals(List.of("patient_context"), saved.getLatestContractMissingCapabilities());
    }

    @Test
    void shouldLeaveContractSummaryEmptyWhenNoContractMetadataExists() {
        AtomicReference<OrchestrationTaskSnapshot> holder = new AtomicReference<>();
        OrchestrationTaskSnapshotRepository repository = RepositoryProxyFactory.create(OrchestrationTaskSnapshotRepository.class, Map.of(
                "save", args -> {
                    OrchestrationTaskSnapshot entity = (OrchestrationTaskSnapshot) args[0];
                    holder.set(entity);
                    return entity;
                },
                "findFirstByTaskIdOrderByUpdatedAtDesc", args -> Optional.ofNullable(holder.get()),
                "findBySessionIdOrderByUpdatedAtDesc", args -> holder.get() == null ? List.of() : List.of(holder.get()),
                "findFirstBySessionIdOrderByUpdatedAtDesc", args -> Optional.ofNullable(holder.get()),
                "findTop200ByOrderByUpdatedAtDesc", args -> holder.get() == null ? List.of() : List.of(holder.get()),
                "findByStatusInOrderByUpdatedAtAsc", args -> holder.get() == null ? List.of() : List.of(holder.get()),
                "findByStatusInAndNextRunAtLessThanEqualOrderByUpdatedAtAsc", args -> holder.get() == null ? List.of() : List.of(holder.get())
        ));
        OrchestrationPersistenceService service = new OrchestrationPersistenceService(repository);

        com.medical.agent.service.react.model.AgentState state = new com.medical.agent.service.react.model.AgentState();
        state.setSessionId("session-no-contract");
        state.setGoal("请继续分析");

        OrchestrationTrace trace = new OrchestrationTrace(
                "task-no-contract",
                "请继续分析",
                TaskStatus.RUNNING,
                "risk_assessment_agent",
                List.of(),
                List.of(),
                List.of(new TaskEvent(
                        TaskEventType.SUPPORT_REQUESTED,
                        LocalDateTime.now(),
                        "等待协助",
                        Map.of("subTaskId", "sub-plain")
                )),
                null
        );

        service.save(state, trace);

        OrchestrationTaskSnapshot saved = holder.get();
        assertNotNull(saved);
        assertNull(saved.getLatestContractValidated());
        assertNull(saved.getLatestContractSchema());
        assertNull(saved.getLatestContractVersion());
        assertNull(saved.getLatestContractCompatibility());
        assertNull(saved.getLatestContractValidationErrorCount());
        assertEquals(List.of(), saved.getLatestContractValidationErrors());
    }

    private static Map<String, Object> event(String type, LocalDateTime occurredAt, Map<String, Object> metadata) {
        java.util.LinkedHashMap<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("type", type);
        payload.put("occurredAt", occurredAt);
        payload.put("metadata", metadata);
        return payload;
    }

    private static Map<String, Object> mapOf(Object... entries) {
        java.util.LinkedHashMap<String, Object> map = new java.util.LinkedHashMap<>();
        for (int i = 0; i < entries.length; i += 2) {
            map.put(String.valueOf(entries[i]), entries[i + 1]);
        }
        return map;
    }

}
