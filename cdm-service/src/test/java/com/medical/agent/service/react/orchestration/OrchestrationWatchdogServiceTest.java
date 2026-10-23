package com.medical.agent.service.react.orchestration;

import com.medical.agent.model.mongo.OrchestrationTaskSnapshot;
import com.medical.agent.service.react.ReActAgentExecutor;
import com.medical.agent.service.react.agent.ChatSubAgent;
import com.medical.agent.service.react.agent.SubAgentRegistry;
import com.medical.agent.service.react.agent.SubAgentResult;
import com.medical.agent.service.react.model.AgentState;
import com.medical.agent.service.react.model.ReActResult;
import com.medical.agent.service.react.tool.AgentTool;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class OrchestrationWatchdogServiceTest {

    @Test
    void shouldAdvancePersistedSnapshot() {
        OrchestrationPersistenceService persistenceService = mock(OrchestrationPersistenceService.class);
        SubAgentRegistry registry = new SubAgentRegistry(List.of(
                agent("risk_assessment_agent"),
                agent("patient_profile_agent"),
                agent("evidence_support_agent")
        ));
        TaskScheduler scheduler = new TaskScheduler(
                new TaskStateMachine(),
                new SubAgentRegistry(List.of(agent("risk_assessment_agent"))),
                new SupervisorLoop()
        );
        ReActAgentExecutor executor = mock(ReActAgentExecutor.class);
        OrchestrationWatchdogService watchdogService = new OrchestrationWatchdogService(
                persistenceService,
                scheduler,
                executor,
                registry,
                new SubTaskHandoffRouter(registry, null),
                mock(com.medical.agent.service.ChatService.class)
        );

        OrchestrationTaskSnapshot snapshot = OrchestrationTaskSnapshot.builder()
                .sessionId("session-1")
                .taskId("task-1")
                .ownerRole("doctor")
                .patientId(1L)
                .userMessage("请继续分析")
                .goal("请继续分析")
                .status(TaskStatus.WAITING_SUPPORT.name())
                .subTasks(List.of(Map.of(
                        "subTaskId", "sub-1",
                        "goal", "请补充患者画像和病史后继续分析",
                        "targetAgent", "risk_assessment_agent",
                        "priority", 100,
                        "status", SubTaskStatus.READY.name(),
                        "dependencies", List.of()
                )))
                .events(List.of(Map.of(
                        "type", TaskEventType.SUPPORT_REQUESTED.name(),
                        "summary", "已为协作请求生成子任务。"
                )))
                .nextDecision(Map.of("targetAgent", "risk_assessment_agent"))
                .updatedAt(LocalDateTime.now())
                .build();

        AgentState rebuiltState = new AgentState();
        rebuiltState.setSessionId("session-1");
        rebuiltState.setOwnerRole("doctor");
        rebuiltState.setPatientId(1L);
        rebuiltState.setUserMessage("请继续分析");
        OrchestrationTaskSnapshot runnableSnapshot = OrchestrationTaskSnapshot.builder()
                .sessionId("session-1")
                .taskId("task-1")
                .status(TaskStatus.WAITING_SUPPORT.name())
                .controlStatus(OrchestrationControlStatus.ACTIVE.name())
                .subTasks(List.of(Map.of(
                        "subTaskId", "sub-1",
                        "goal", "请补充患者画像和病史后继续分析",
                        "targetAgent", "risk_assessment_agent",
                        "priority", 100,
                        "status", SubTaskStatus.READY.name(),
                        "dependencies", List.of()
                )))
                .events(List.of(Map.of(
                        "type", TaskEventType.BLOCKED.name(),
                        "metadata", Map.of(
                                "subTaskId", "sub-1",
                                "targetAgent", "risk_assessment_agent",
                                "missingCapabilities", List.of("patient_context")
                        )
                )))
                .updatedAt(LocalDateTime.now())
                .build();
        when(persistenceService.rebuildAgentState(runnableSnapshot)).thenReturn(rebuiltState);
        when(executor.execute(rebuiltState)).thenReturn(new ReActResult(
                "watchdog 已推进。",
                null,
                null,
                "请继续分析",
                "已完成风险评估。",
                "直接输出结论。",
                List.of(),
                null,
                List.of(),
                null,
                List.of(),
                List.of("risk_assessment"),
                List.of(),
                List.of(),
                Set.of(),
                null
        ));

        watchdogService.advanceSnapshot(runnableSnapshot);

        verify(executor).execute(rebuiltState);
        verify(persistenceService).save(org.mockito.ArgumentMatchers.eq(rebuiltState), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void shouldExposeManualTriggerSummary() {
        OrchestrationPersistenceService persistenceService = mock(OrchestrationPersistenceService.class);
        TaskScheduler scheduler = mock(TaskScheduler.class);
        ReActAgentExecutor executor = mock(ReActAgentExecutor.class);
        SubAgentRegistry registry = new SubAgentRegistry(List.of(
                agent("risk_assessment_agent"),
                agent("patient_profile_agent"),
                agent("evidence_support_agent")
        ));
        OrchestrationWatchdogService watchdogService = new OrchestrationWatchdogService(
                persistenceService,
                scheduler,
                executor,
                registry,
                new SubTaskHandoffRouter(registry, null)
        );

        OrchestrationTaskSnapshot reviewSnapshot = OrchestrationTaskSnapshot.builder()
                .taskId("task-review")
                .sessionId("session-review")
                .status(TaskStatus.BLOCKED.name())
                .controlStatus(OrchestrationControlStatus.ACTIVE.name())
                .subTasks(List.of(Map.of(
                        "subTaskId", "sub-review",
                        "targetAgent", "risk_assessment_agent",
                        "goal", "请继续分析",
                        "priority", 100,
                        "status", SubTaskStatus.BLOCKED.name()
                )))
                .updatedAt(LocalDateTime.now())
                .build();
        when(persistenceService.listRunnableSnapshots()).thenReturn(List.of(reviewSnapshot));
        when(persistenceService.listActiveSnapshots()).thenReturn(List.of(reviewSnapshot));

        Map<String, Object> payload = watchdogService.triggerOnce();

        assertEquals(1, payload.get("attempted"));
        assertEquals(0, payload.get("deferredForManualReview"));
        assertEquals(0, payload.get("manualReviewRecommendedTasks"));
    }

    @Test
    void shouldExposeManualControlOperations() {
        OrchestrationPersistenceService persistenceService = mock(OrchestrationPersistenceService.class);
        TaskScheduler scheduler = mock(TaskScheduler.class);
        ReActAgentExecutor executor = mock(ReActAgentExecutor.class);
        OrchestrationWatchdogService watchdogService = new OrchestrationWatchdogService(
                persistenceService,
                scheduler,
                executor,
                new SubAgentRegistry(List.of(agent("risk_assessment_agent"))),
                new SubTaskHandoffRouter(new SubAgentRegistry(List.of(agent("risk_assessment_agent"))), null)
        );

        OrchestrationTaskSnapshot snapshot = OrchestrationTaskSnapshot.builder()
                .taskId("task-1")
                .sessionId("session-1")
                .status(TaskStatus.BLOCKED.name())
                .controlStatus(OrchestrationControlStatus.PAUSED.name())
                .updatedAt(LocalDateTime.now())
                .build();
        when(persistenceService.pauseTask("task-1", "admin", "人工暂停")).thenReturn(Optional.of(snapshot));
        when(persistenceService.resumeTask("task-1", "admin", "恢复执行")).thenReturn(Optional.of(OrchestrationTaskSnapshot.builder()
                .taskId("task-1")
                .sessionId("session-1")
                .status(TaskStatus.BLOCKED.name())
                .controlStatus(OrchestrationControlStatus.ACTIVE.name())
                .lastControlBy("admin")
                .lastControlReason("恢复执行")
                .updatedAt(LocalDateTime.now())
                .build()));
        when(persistenceService.acknowledgeManualReview("task-1", "admin", "已人工确认")).thenReturn(Optional.of(OrchestrationTaskSnapshot.builder()
                .taskId("task-1")
                .sessionId("session-1")
                .status(TaskStatus.BLOCKED.name())
                .controlStatus(OrchestrationControlStatus.ACTIVE.name())
                .lastControlAction(TaskEventType.MANUAL_REVIEW_ACKED.name())
                .lastControlBy("admin")
                .lastControlReason("已人工确认")
                .updatedAt(LocalDateTime.now())
                .build()));

        Map<String, Object> paused = watchdogService.pauseTask("task-1", "admin", "人工暂停").orElseThrow();
        Map<String, Object> resumed = watchdogService.resumeTask("task-1", "admin", "恢复执行").orElseThrow();
        Map<String, Object> acknowledged = watchdogService.acknowledgeReview("task-1", "admin", "已人工确认").orElseThrow();

        assertNotNull(paused);
        assertEquals(OrchestrationControlStatus.PAUSED.name(), paused.get("controlStatus"));
        assertEquals(OrchestrationControlStatus.ACTIVE.name(), resumed.get("controlStatus"));
        assertEquals("admin", resumed.get("lastControlBy"));
        assertEquals("恢复执行", resumed.get("lastControlReason"));
        assertEquals(OrchestrationControlStatus.ACTIVE.name(), acknowledged.get("controlStatus"));
        assertEquals(TaskEventType.MANUAL_REVIEW_ACKED.name(), acknowledged.get("lastControlAction"));
        assertEquals("已人工确认", acknowledged.get("lastControlReason"));
    }

    @Test
    void shouldExposeSubTaskControlOperations() {
        OrchestrationPersistenceService persistenceService = mock(OrchestrationPersistenceService.class);
        TaskScheduler scheduler = mock(TaskScheduler.class);
        ReActAgentExecutor executor = mock(ReActAgentExecutor.class);
        SubAgentRegistry registry = new SubAgentRegistry(List.of(
                agent("risk_assessment_agent"),
                agent("patient_profile_agent"),
                agent("evidence_support_agent")
        ));
        OrchestrationWatchdogService watchdogService = new OrchestrationWatchdogService(
                persistenceService,
                scheduler,
                executor,
                registry,
                new SubTaskHandoffRouter(registry, null)
        );

        OrchestrationTaskSnapshot snapshot = OrchestrationTaskSnapshot.builder()
                .taskId("task-1")
                .sessionId("session-1")
                .status(TaskStatus.BLOCKED.name())
                .controlStatus(OrchestrationControlStatus.ACTIVE.name())
                .subTasks(List.of(Map.of(
                        "subTaskId", "sub-1",
                        "targetAgent", "risk_assessment_agent",
                        "goal", "请补充患者画像后继续分析",
                        "priority", 100,
                        "status", SubTaskStatus.SKIPPED.name()
                )))
                .updatedAt(LocalDateTime.now())
                .build();
        when(persistenceService.listActiveSnapshots()).thenReturn(List.of(OrchestrationTaskSnapshot.builder()
                .taskId("task-1")
                .sessionId("session-1")
                .status(TaskStatus.BLOCKED.name())
                .controlStatus(OrchestrationControlStatus.ACTIVE.name())
                .subTasks(List.of(Map.of(
                        "subTaskId", "sub-1",
                        "targetAgent", "risk_assessment_agent",
                        "goal", "请补充患者画像后继续分析",
                        "priority", 100,
                        "status", SubTaskStatus.READY.name()
                )))
                .updatedAt(LocalDateTime.now())
                .build()));
        when(persistenceService.skipSubTask("task-1", "sub-1", "patient_profile_agent", "admin", "无须执行"))
                .thenReturn(Optional.of(snapshot));
        when(persistenceService.resetSubTask("task-1", "sub-1", "admin", "重新纳入流程"))
                .thenReturn(Optional.of(OrchestrationTaskSnapshot.builder()
                        .taskId("task-1")
                        .sessionId("session-1")
                        .status(TaskStatus.WAITING_SUPPORT.name())
                        .controlStatus(OrchestrationControlStatus.ACTIVE.name())
                        .subTasks(List.of(Map.of(
                                "subTaskId", "sub-1",
                                "targetAgent", "risk_assessment_agent",
                                "goal", "请补充患者画像后继续分析",
                                "priority", 100,
                                "status", SubTaskStatus.READY.name()
                        )))
                        .updatedAt(LocalDateTime.now())
                        .build()));

        Map<String, Object> payload = watchdogService.skipSubTask("task-1", "sub-1", "admin", "无须执行").orElseThrow();
        Map<String, Object> resetPayload = watchdogService.resetSubTask("task-1", "sub-1", "admin", "重新纳入流程").orElseThrow();

        assertEquals(1, ((List<?>) payload.get("subTasks")).size());
        assertEquals(TaskStatus.WAITING_SUPPORT.name(), resetPayload.get("status"));
    }

    @Test
    void shouldAdvanceTaskImmediatelyAfterManualSubTaskRecovery() {
        OrchestrationPersistenceService persistenceService = mock(OrchestrationPersistenceService.class);
        ReActAgentExecutor executor = mock(ReActAgentExecutor.class);
        SubAgentRegistry schedulerRegistry = new SubAgentRegistry(List.of(successAgent("patient_profile_agent")));
        SubAgentRegistry watchdogRegistry = new SubAgentRegistry(List.of(
                successAgent("patient_profile_agent"),
                agent("risk_assessment_agent")
        ));
        TaskScheduler scheduler = new TaskScheduler(
                new TaskStateMachine(),
                schedulerRegistry,
                new SupervisorLoop()
        );
        OrchestrationWatchdogService watchdogService = new OrchestrationWatchdogService(
                persistenceService,
                scheduler,
                executor,
                watchdogRegistry,
                new SubTaskHandoffRouter(watchdogRegistry, null)
        );

        OrchestrationTaskSnapshot runnable = OrchestrationTaskSnapshot.builder()
                .taskId("task-recovered")
                .sessionId("session-recovered")
                .ownerRole("doctor")
                .patientId(1L)
                .userMessage("请继续分析")
                .goal("请继续分析")
                .status(TaskStatus.WAITING_SUPPORT.name())
                .controlStatus(OrchestrationControlStatus.ACTIVE.name())
                .subTasks(List.of(Map.of(
                        "subTaskId", "sub-recovered",
                        "goal", "请补充患者画像后继续分析",
                        "targetAgent", "patient_profile_agent",
                        "priority", 100,
                        "status", SubTaskStatus.READY.name(),
                        "dependencies", List.of()
                )))
                .events(List.of(Map.of(
                        "type", TaskEventType.SUBTASK_RESET.name(),
                        "occurredAt", LocalDateTime.now(),
                        "metadata", Map.of(
                                "subTaskId", "sub-recovered",
                                "targetAgent", "patient_profile_agent"
                        )
                )))
                .updatedAt(LocalDateTime.now())
                .build();

        AgentState rebuiltState = new AgentState();
        rebuiltState.setSessionId("session-recovered");
        rebuiltState.setOwnerRole("doctor");
        rebuiltState.setPatientId(1L);
        rebuiltState.setUserMessage("请继续分析");

        when(persistenceService.listActiveSnapshots()).thenReturn(List.of(runnable));
        when(persistenceService.rebuildAgentState(runnable)).thenReturn(rebuiltState);
        when(executor.execute(rebuiltState)).thenReturn(new ReActResult(
                "watchdog 已基于恢复后的子任务给出完整答复。",
                null,
                null,
                "请继续分析",
                "已补充患者画像并完成分析。",
                "直接输出结论。",
                List.of(),
                null,
                List.of(),
                null,
                List.of(),
                List.of("patient_profile"),
                List.of(),
                List.of(),
                Set.of(),
                null
        ));
        when(persistenceService.findLatestByTaskIdPublic("task-recovered")).thenReturn(Optional.of(runnable));

        Map<String, Object> payload = watchdogService.advanceTaskNow("task-recovered").orElseThrow();

        assertEquals("task-recovered", payload.get("taskId"));
        verify(executor).execute(rebuiltState);
        verify(persistenceService).save(any(AgentState.class), any(OrchestrationTrace.class));
        assertTrue(Boolean.TRUE.equals(payload.get("watchdogAutoAdvanceAllowed")));
    }

    @Test
    void shouldExposeCapabilityAwareSubTaskPayloadAndHandoff() {
        OrchestrationPersistenceService persistenceService = mock(OrchestrationPersistenceService.class);
        TaskScheduler scheduler = mock(TaskScheduler.class);
        ReActAgentExecutor executor = mock(ReActAgentExecutor.class);
        SubAgentRegistry registry = new SubAgentRegistry(List.of(
                agent("risk_assessment_agent"),
                agent("patient_profile_agent"),
                agent("evidence_support_agent")
        ));
        OrchestrationWatchdogService watchdogService = new OrchestrationWatchdogService(
                persistenceService,
                scheduler,
                executor,
                registry,
                new SubTaskHandoffRouter(registry, null)
        );

        OrchestrationTaskSnapshot active = OrchestrationTaskSnapshot.builder()
                .taskId("task-2")
                .sessionId("session-2")
                .status(TaskStatus.BLOCKED.name())
                .controlStatus(OrchestrationControlStatus.ACTIVE.name())
                .subTasks(List.of(Map.of(
                        "subTaskId", "sub-2",
                        "targetAgent", "risk_assessment_agent",
                        "goal", "请继续分析",
                        "priority", 100,
                        "status", SubTaskStatus.BLOCKED.name()
                )))
                .events(List.of(Map.of(
                        "type", TaskEventType.BLOCKED.name(),
                        "metadata", Map.of(
                                "subTaskId", "sub-2",
                                "targetAgent", "risk_assessment_agent",
                                "failureCategory", "tool_or_runtime_failure",
                                "missingCapabilities", List.of("patient_context")
                        )
                )))
                .updatedAt(LocalDateTime.now())
                .build();
        when(persistenceService.listActiveSnapshots()).thenReturn(List.of(active));
        when(persistenceService.handoffSubTask("task-2", "sub-2", "patient_profile_agent", "admin", "按能力补位"))
                .thenReturn(Optional.of(active));

        List<Map<String, Object>> tasks = watchdogService.listActiveTasks();
        Map<String, Object> firstTask = tasks.get(0);
        Map<String, Object> firstSubTask = (Map<String, Object>) ((List<?>) firstTask.get("subTasks")).get(0);
        Map<String, Object> handoff = watchdogService.handoffSubTaskByCapability("task-2", "sub-2", "patient_context", "admin", "按能力补位")
                .orElseThrow();

        assertEquals("patient_profile_agent", firstSubTask.get("recommendedAgent"));
        assertEquals("missing_capabilities:patient_context,patient_profile,medplum_insight", firstSubTask.get("recommendedAgentReason"));
        assertEquals(List.of("patient_context"), firstSubTask.get("recentMissingCapabilities"));
        List<?> recommendedScores = (List<?>) firstSubTask.get("recommendedAgentScores");
        assertEquals("patient_profile_agent", ((Map<?, ?>) recommendedScores.get(0)).get("agent"));
        assertEquals("high", firstSubTask.get("recommendedAgentConfidence"));
        assertEquals(Boolean.FALSE, firstSubTask.get("recommendedAgentNeedsReview"));
        assertEquals("patient_profile_agent", ((Map<String, Object>) ((List<?>) handoff.get("subTasks")).get(0)).get("recommendedAgent"));
    }

    @Test
    void shouldExposeTaskTimelineDetail() {
        OrchestrationPersistenceService persistenceService = mock(OrchestrationPersistenceService.class);
        TaskScheduler scheduler = mock(TaskScheduler.class);
        ReActAgentExecutor executor = mock(ReActAgentExecutor.class);
        SubAgentRegistry registry = new SubAgentRegistry(List.of(
                agent("risk_assessment_agent"),
                agent("patient_profile_agent"),
                agent("evidence_support_agent")
        ));
        OrchestrationWatchdogService watchdogService = new OrchestrationWatchdogService(
                persistenceService,
                scheduler,
                executor,
                registry,
                new SubTaskHandoffRouter(registry, null)
        );

        OrchestrationTaskSnapshot detail = OrchestrationTaskSnapshot.builder()
                .taskId("task-3")
                .sessionId("session-3")
                .ownerRole("doctor")
                .ownerUserId(7L)
                .patientId(9L)
                .goal("请继续分析")
                .status(TaskStatus.BLOCKED.name())
                .controlStatus(OrchestrationControlStatus.AWAITING_REVIEW.name())
                .lastControlReason("存在 1 个低置信度子任务，建议人工确认: fallback_from:risk_assessment_agent")
                .subTasks(List.of(Map.of(
                        "subTaskId", "sub-3",
                        "targetAgent", "risk_assessment_agent",
                        "goal", "请继续分析",
                        "status", SubTaskStatus.BLOCKED.name()
                )))
                .events(List.of(
                        Map.of(
                                "type", TaskEventType.SYSTEM_AWAITING_REVIEW.name(),
                                "occurredAt", LocalDateTime.now(),
                                "summary", "系统暂停自动推进。",
                                "metadata", Map.of(
                                        "operator", "system_watchdog",
                                        "reason", "存在 1 个低置信度子任务，建议人工确认: fallback_from:risk_assessment_agent"
                                )
                        ),
                        Map.of(
                                "type", TaskEventType.MANUAL_REVIEW_ACKED.name(),
                                "occurredAt", LocalDateTime.now(),
                                "summary", "管理员确认复核完成。",
                                "metadata", Map.of(
                                        "operator", "admin",
                                        "reason", "已人工确认"
                                )
                        ),
                        Map.of(
                                "type", TaskEventType.BLOCKED.name(),
                                "occurredAt", LocalDateTime.now(),
                                "summary", "子任务未完成，进入阻塞。",
                                "metadata", Map.of(
                                        "subTaskId", "sub-3",
                                        "targetAgent", "risk_assessment_agent",
                                        "failureCategory", "context_gap",
                                        "missingCapabilities", List.of("patient_context"),
                                        "operator", "admin",
                                        "reason", "人工审查"
                                )
                        )
                ))
                .updatedAt(LocalDateTime.now())
                .build();
        when(persistenceService.findLatestByTaskIdPublic("task-3")).thenReturn(Optional.of(detail));

        Map<String, Object> payload = watchdogService.getTaskDetail("task-3").orElseThrow();
        Map<String, Object> awaitingReviewItem = (Map<String, Object>) ((List<?>) payload.get("timeline")).get(0);
        Map<String, Object> reviewAckedItem = (Map<String, Object>) ((List<?>) payload.get("timeline")).get(1);
        Map<String, Object> blockedItem = (Map<String, Object>) ((List<?>) payload.get("timeline")).get(2);

        assertEquals("系统已暂停自动推进，等待管理员确认。 存在 1 个低置信度子任务，建议人工确认: fallback_from:risk_assessment_agent", payload.get("currentStatusSummary"));
        assertEquals("请在确认转派建议后执行 ack-review 或直接做人工转派。", payload.get("pendingReviewAction"));
        assertEquals("awaiting_review", awaitingReviewItem.get("stateLabel"));
        assertEquals("review_acked", reviewAckedItem.get("stateLabel"));
        assertEquals("系统因低置信度接力建议暂停自动推进，等待管理员确认。 存在 1 个低置信度子任务，建议人工确认: fallback_from:risk_assessment_agent", awaitingReviewItem.get("displaySummary"));
        assertEquals("管理员已确认人工复核结果，任务恢复自动推进资格。 原因: 已人工确认", reviewAckedItem.get("displaySummary"));
        assertEquals("context_gap", blockedItem.get("failureCategory"));
        assertEquals(List.of("patient_context"), blockedItem.get("missingCapabilities"));
        assertEquals("admin", blockedItem.get("operator"));
        assertEquals("人工审查", blockedItem.get("reason"));
    }

    @Test
    void shouldReturnLatestTaskBySession() {
        OrchestrationPersistenceService persistenceService = mock(OrchestrationPersistenceService.class);
        TaskScheduler scheduler = mock(TaskScheduler.class);
        ReActAgentExecutor executor = mock(ReActAgentExecutor.class);
        SubAgentRegistry registry = new SubAgentRegistry(List.of(agent("risk_assessment_agent")));
        OrchestrationWatchdogService watchdogService = new OrchestrationWatchdogService(
                persistenceService,
                scheduler,
                executor,
                registry,
                new SubTaskHandoffRouter(registry, null)
        );

        OrchestrationTaskSnapshot snapshot = OrchestrationTaskSnapshot.builder()
                .taskId("task-session")
                .sessionId("session-linked")
                .status(TaskStatus.RUNNING.name())
                .controlStatus(OrchestrationControlStatus.ACTIVE.name())
                .updatedAt(LocalDateTime.now())
                .build();
        when(persistenceService.findLatestBySessionIdPublic("session-linked")).thenReturn(Optional.of(snapshot));

        Map<String, Object> payload = watchdogService.getLatestSessionTask("session-linked").orElseThrow();

        assertEquals("task-session", payload.get("taskId"));
        assertEquals("session-linked", payload.get("sessionId"));
    }

    @Test
    void shouldNotBlockWatchdogForLowConfidenceScoreGapRecommendation() {
        OrchestrationPersistenceService persistenceService = mock(OrchestrationPersistenceService.class);
        TaskScheduler scheduler = mock(TaskScheduler.class);
        ReActAgentExecutor executor = mock(ReActAgentExecutor.class);
        SubAgentRegistry registry = new SubAgentRegistry(List.of(
                agent("risk_assessment_agent"),
                agent("patient_profile_agent"),
                agent("evidence_support_agent")
        ));
        OrchestrationWatchdogService watchdogService = new OrchestrationWatchdogService(
                persistenceService,
                scheduler,
                executor,
                registry,
                new SubTaskHandoffRouter(registry, null)
        );

        OrchestrationTaskSnapshot active = OrchestrationTaskSnapshot.builder()
                .taskId("task-4")
                .sessionId("session-4")
                .status(TaskStatus.BLOCKED.name())
                .controlStatus(OrchestrationControlStatus.ACTIVE.name())
                .subTasks(List.of(Map.of(
                        "subTaskId", "sub-4",
                        "targetAgent", "risk_assessment_agent",
                        "goal", "请继续分析",
                        "priority", 100,
                        "status", SubTaskStatus.BLOCKED.name()
                )))
                .updatedAt(LocalDateTime.now())
                .build();
        when(persistenceService.listActiveSnapshots()).thenReturn(List.of(active));

        Map<String, Object> firstTask = watchdogService.listActiveTasks().get(0);
        Map<String, Object> firstSubTask = (Map<String, Object>) ((List<?>) firstTask.get("subTasks")).get(0);

        assertEquals("low", firstSubTask.get("recommendedAgentConfidence"));
        assertEquals(Boolean.FALSE, firstSubTask.get("recommendedAgentNeedsReview"));
        assertEquals("", firstSubTask.get("recommendedAgentReviewTrigger"));
        assertEquals(Boolean.FALSE, firstTask.get("manualReviewRecommended"));
        assertEquals(0, firstTask.get("manualReviewSubTaskCount"));
        assertEquals(List.of(), firstTask.get("manualReviewSubTaskIds"));
        assertEquals(List.of(), firstTask.get("manualReviewReasons"));
        assertEquals(null, firstTask.get("manualReviewSummary"));
        assertEquals(Boolean.TRUE, firstTask.get("watchdogAutoAdvanceAllowed"));
        assertEquals("auto_advance", firstTask.get("watchdogDecision"));
    }

    @Test
    void shouldExposeOverviewAggregation() {
        OrchestrationPersistenceService persistenceService = mock(OrchestrationPersistenceService.class);
        TaskScheduler scheduler = mock(TaskScheduler.class);
        ReActAgentExecutor executor = mock(ReActAgentExecutor.class);
        SubAgentRegistry registry = new SubAgentRegistry(List.of(
                agent("risk_assessment_agent"),
                agent("patient_profile_agent"),
                agent("evidence_support_agent")
        ));
        OrchestrationWatchdogService watchdogService = new OrchestrationWatchdogService(
                persistenceService,
                scheduler,
                executor,
                registry,
                new SubTaskHandoffRouter(registry, null)
        );
        LocalDateTime bucketBase = LocalDateTime.now().truncatedTo(ChronoUnit.HOURS);

        OrchestrationTaskSnapshot awaitingReview = OrchestrationTaskSnapshot.builder()
                .taskId("task-overview-1")
                .sessionId("session-overview-1")
                .taskType("general_coordination")
                .status(TaskStatus.BLOCKED.name())
                .controlStatus(OrchestrationControlStatus.AWAITING_REVIEW.name())
                .latestContractValidated(Boolean.FALSE)
                .latestContractSchema("sub-agent-result")
                .latestContractVersion("0.9")
                .latestContractCompatibility("schema_only")
                .latestContractValidationErrorCount(1)
                .latestContractValidationErrors(List.of("unsupported_contract_version:0.9"))
                .latestContractFailureCategory("context_gap")
                .latestContractSuggestedHandoffAgent("patient_profile_agent")
                .latestContractMissingCapabilities(List.of("patient_context"))
                .lastControlReason("存在 1 个低置信度子任务，建议人工确认: fallback_from:risk_assessment_agent")
                .watchdogFailureStreak(1)
                .subTasks(List.of(Map.of(
                        "subTaskId", "sub-overview-1",
                        "targetAgent", "risk_assessment_agent",
                        "goal", "请继续分析",
                        "priority", 100,
                        "status", SubTaskStatus.BLOCKED.name()
                )))
                .updatedAt(LocalDateTime.now())
                .build();
        OrchestrationTaskSnapshot autoAdvance = OrchestrationTaskSnapshot.builder()
                .taskId("task-overview-2")
                .sessionId("session-overview-2")
                .taskType("context_enrichment")
                .status(TaskStatus.BLOCKED.name())
                .controlStatus(OrchestrationControlStatus.ACTIVE.name())
                .latestContractValidated(Boolean.TRUE)
                .latestContractSchema("sub-agent-result")
                .latestContractVersion("1.0")
                .latestContractCompatibility("compatible")
                .latestContractValidationErrorCount(0)
                .latestContractValidationErrors(List.of())
                .latestContractFailureCategory("context_gap")
                .latestContractSuggestedHandoffAgent("patient_profile_agent")
                .latestContractMissingCapabilities(List.of("patient_context"))
                .watchdogFailureStreak(3)
                .circuitOpenUntil(LocalDateTime.now().plusMinutes(30))
                .circuitOpenReason("watchdog_failure_threshold_reached")
                .subTasks(List.of(Map.of(
                        "subTaskId", "sub-overview-2",
                        "targetAgent", "risk_assessment_agent",
                        "goal", "请补充患者画像和病史后继续分析",
                        "priority", 100,
                        "status", SubTaskStatus.BLOCKED.name()
                )))
                .events(List.of(Map.of(
                        "type", TaskEventType.BLOCKED.name(),
                        "metadata", Map.of(
                                "subTaskId", "sub-overview-2",
                                "targetAgent", "risk_assessment_agent",
                                "failureCategory", "context_gap",
                                "missingCapabilities", List.of("patient_context")
                        )
                )))
                .updatedAt(LocalDateTime.now())
                .build();
        when(persistenceService.listActiveSnapshots()).thenReturn(List.of(awaitingReview, autoAdvance));
        when(persistenceService.summarizeRecentControlEvents(24)).thenReturn(Map.of(
                TaskEventType.SYSTEM_AWAITING_REVIEW.name(), 3L,
                TaskEventType.MANUAL_REVIEW_ACKED.name(), 2L
        ));
        when(persistenceService.summarizeRecentControlEventBuckets(24)).thenReturn(List.of(
                Map.of(
                        "bucketStart", bucketBase.minusHours(1),
                        "bucketEnd", bucketBase,
                        "awaitingReviewCount", 2L,
                        "reviewAckedCount", 0L,
                        "controlEventCount", 2L,
                        "eventCounts", Map.of(TaskEventType.SYSTEM_AWAITING_REVIEW.name(), 2L)
                ),
                Map.of(
                        "bucketStart", bucketBase,
                        "bucketEnd", bucketBase.plusHours(1),
                        "awaitingReviewCount", 1L,
                        "reviewAckedCount", 2L,
                        "controlEventCount", 3L,
                        "eventCounts", Map.of(
                                TaskEventType.SYSTEM_AWAITING_REVIEW.name(), 1L,
                                TaskEventType.MANUAL_REVIEW_ACKED.name(), 2L
                        )
                )
        ));
        when(persistenceService.summarizeRecentIssueSignals(24)).thenReturn(Map.of(
                "topFailureCategories", List.of(Map.of("key", "context_gap", "count", 4L)),
                "topMissingCapabilities", List.of(Map.of("key", "patient_context", "count", 5L)),
                "recent24hFailureCategoryTrend", List.of(Map.of(
                        "bucketStart", bucketBase.minusHours(1),
                        "bucketEnd", bucketBase,
                        "totalCount", 2L,
                        "topSignals", List.of(Map.of("key", "context_gap", "count", 2L)),
                        "signalCounts", Map.of("context_gap", 2L)
                )),
                "recent24hMissingCapabilityTrend", List.of(Map.of(
                        "bucketStart", bucketBase.minusHours(1),
                        "bucketEnd", bucketBase,
                        "totalCount", 3L,
                        "topSignals", List.of(Map.of("key", "patient_context", "count", 3L)),
                        "signalCounts", Map.of("patient_context", 3L)
                ))
        ));
        when(persistenceService.summarizeRecentAgentIssueSignals(24)).thenReturn(List.of(
                Map.of(
                        "agent", "risk_assessment_agent",
                        "issueCount", 7L,
                        "failureCategoryCounts", Map.of("context_gap", 4L),
                        "missingCapabilityCounts", Map.of("patient_context", 3L),
                        "topFailureCategories", List.of(Map.of("key", "context_gap", "count", 4L)),
                        "topMissingCapabilities", List.of(Map.of("key", "patient_context", "count", 3L)),
                        "recent24hFailureCategoryTrend", List.of(Map.of(
                                "bucketStart", bucketBase.minusHours(1),
                                "bucketEnd", bucketBase,
                                "totalCount", 2L,
                                "topSignals", List.of(Map.of("key", "context_gap", "count", 2L)),
                                "signalCounts", Map.of("context_gap", 2L)
                        )),
                        "recent24hMissingCapabilityTrend", List.of(Map.of(
                                "bucketStart", bucketBase.minusHours(1),
                                "bucketEnd", bucketBase,
                                "totalCount", 1L,
                                "topSignals", List.of(Map.of("key", "patient_context", "count", 1L)),
                                "signalCounts", Map.of("patient_context", 1L)
                        ))
                )
        ));
        when(persistenceService.summarizeRecentAgentExecutionHealth(24)).thenReturn(List.of(
                Map.of(
                        "agent", "risk_assessment_agent",
                        "completedCount", 6L,
                        "failedCount", 1L,
                        "blockedCount", 2L,
                        "reroutedCount", 1L,
                        "executionCount", 10L,
                        "successRate", 0.6D,
                        "stabilityScore", 0.34D,
                        "recent24hExecutionTrend", List.of(Map.of(
                                "bucketStart", bucketBase.minusHours(1),
                                "bucketEnd", bucketBase,
                                "completedCount", 2L,
                                "failedCount", 0L,
                                "blockedCount", 1L,
                                "reroutedCount", 0L,
                                "executionCount", 3L,
                                "successRate", 0.667D
                        ))
                )
        ));

        Map<String, Object> overview = watchdogService.getOverview();
        List<?> topReasons = (List<?>) overview.get("topManualReviewReasons");
        List<?> topFailureCategories = (List<?>) overview.get("topFailureCategories");
        List<?> hourlyTrend = (List<?>) overview.get("recent24hHourlyTrend");
        List<?> recentFailureTrend = (List<?>) overview.get("recent24hFailureCategoryTrend");
        List<?> recentCapabilityTrend = (List<?>) overview.get("recent24hMissingCapabilityTrend");
        List<?> recentTopMissingCapabilities = (List<?>) overview.get("recent24hTopMissingCapabilities");
        List<?> agentIssueSignals = (List<?>) overview.get("recent24hAgentIssueSignals");
        List<?> agentExecutionHealth = (List<?>) overview.get("recent24hAgentExecutionHealth");
        List<?> topUnhealthyResolvedAgents = (List<?>) overview.get("topUnhealthyResolvedAgents");
        List<?> topIssueHeavyResolvedAgents = (List<?>) overview.get("topIssueHeavyResolvedAgents");
        List<?> topReviewTriggers = (List<?>) overview.get("topReviewTriggers");
        List<?> topContractValidationErrors = (List<?>) overview.get("topContractValidationErrors");
        Map<?, ?> dashboardHighlights = (Map<?, ?>) overview.get("dashboardHighlights");

        assertEquals(2, overview.get("activeTaskCount"));
        assertEquals(1L, overview.get("awaitingReviewTaskCount"));
        assertEquals(1L, overview.get("autoAdvanceTaskCount"));
        assertEquals(0L, overview.get("manualReviewRecommendedTaskCount"));
        assertEquals(1L, overview.get("circuitBreakerTaskCount"));
        assertEquals(1L, overview.get("validatedContractTaskCount"));
        assertEquals(1L, overview.get("invalidContractTaskCount"));
        assertEquals(1L, ((Map<?, ?>) overview.get("controlStatusCounts")).get(OrchestrationControlStatus.AWAITING_REVIEW.name()));
        assertEquals(1L, ((Map<?, ?>) overview.get("watchdogDecisionCounts")).get("await_manual_review"));
        assertEquals(1L, ((Map<?, ?>) overview.get("contractCompatibilityCounts")).get("schema_only"));
        assertEquals(1L, ((Map<?, ?>) overview.get("contractCompatibilityCounts")).get("compatible"));
        assertEquals(3L, overview.get("recent24hAwaitingReviewCount"));
        assertEquals(2L, overview.get("recent24hReviewAckedCount"));
        assertEquals(2, hourlyTrend.size());
        assertEquals(2L, ((Map<?, ?>) hourlyTrend.get(0)).get("awaitingReviewCount"));
        assertEquals(2L, ((Map<?, ?>) hourlyTrend.get(1)).get("reviewAckedCount"));
        assertEquals(0, topReasons.size());
        assertEquals("context_gap", ((Map<?, ?>) topFailureCategories.get(0)).get("key"));
        assertEquals("unsupported_contract_version:0.9", ((Map<?, ?>) topContractValidationErrors.get(0)).get("key"));
        assertEquals("context_gap", ((Map<?, ?>) ((List<?>) ((Map<?, ?>) recentFailureTrend.get(0)).get("topSignals")).get(0)).get("key"));
        assertEquals("patient_context", ((Map<?, ?>) recentTopMissingCapabilities.get(0)).get("key"));
        assertEquals(3L, ((Map<?, ?>) recentCapabilityTrend.get(0)).get("totalCount"));
        assertEquals("risk_assessment_agent", ((Map<?, ?>) agentIssueSignals.get(0)).get("agent"));
        assertEquals(7L, ((Map<?, ?>) agentIssueSignals.get(0)).get("issueCount"));
        assertEquals("risk_assessment_agent", ((Map<?, ?>) agentIssueSignals.get(0)).get("routeKey"));
        assertEquals("default", ((Map<?, ?>) agentIssueSignals.get(0)).get("provider"));
        assertEquals(SubAgentRegistry.HEALTH_STATE_DEGRADED, ((Map<?, ?>) agentIssueSignals.get(0)).get("healthState"));
        assertEquals(SubAgentRegistry.HEALTH_STATE_DEGRADED, ((Map<?, ?>) agentIssueSignals.get(0)).get("providerHealthState"));
        assertEquals(Boolean.TRUE, ((Map<?, ?>) agentIssueSignals.get(0)).get("routingPreferred"));
        assertEquals("risk_assessment_agent", ((Map<?, ?>) agentExecutionHealth.get(0)).get("agent"));
        assertEquals(0.6D, ((Map<?, ?>) agentExecutionHealth.get(0)).get("successRate"));
        assertEquals(0.34D, ((Map<?, ?>) agentExecutionHealth.get(0)).get("stabilityScore"));
        assertEquals("risk_assessment_agent", ((Map<?, ?>) agentExecutionHealth.get(0)).get("routeKey"));
        assertEquals("default", ((Map<?, ?>) agentExecutionHealth.get(0)).get("provider"));
        assertEquals(SubAgentRegistry.HEALTH_STATE_DEGRADED, ((Map<?, ?>) agentExecutionHealth.get(0)).get("healthState"));
        assertEquals(SubAgentRegistry.HEALTH_STATE_DEGRADED, ((Map<?, ?>) agentExecutionHealth.get(0)).get("providerHealthState"));
        assertEquals(Boolean.TRUE, ((Map<?, ?>) agentExecutionHealth.get(0)).get("routingPreferred"));
        assertEquals("risk_assessment_agent", ((Map<?, ?>) topUnhealthyResolvedAgents.get(0)).get("routeKey"));
        assertEquals("risk_assessment_agent", ((Map<?, ?>) topIssueHeavyResolvedAgents.get(0)).get("routeKey"));
        assertEquals(0, topReviewTriggers.size());
        assertEquals(Map.of(), dashboardHighlights.get("dominantReviewTrigger"));
    }

    @Test
    void shouldExposeTaskCardsAndFilterOptions() {
        OrchestrationPersistenceService persistenceService = mock(OrchestrationPersistenceService.class);
        TaskScheduler scheduler = mock(TaskScheduler.class);
        ReActAgentExecutor executor = mock(ReActAgentExecutor.class);
        SubAgentRegistry registry = new SubAgentRegistry(List.of(
                agent("risk_assessment_agent"),
                agent("patient_profile_agent"),
                agent("evidence_support_agent")
        ));
        OrchestrationWatchdogService watchdogService = new OrchestrationWatchdogService(
                persistenceService,
                scheduler,
                executor,
                registry,
                new SubTaskHandoffRouter(registry, null)
        );

        OrchestrationTaskSnapshot reviewTask = OrchestrationTaskSnapshot.builder()
                .taskId("task-card-1")
                .sessionId("session-card-1")
                .taskType("risk_triage")
                .status(TaskStatus.BLOCKED.name())
                .controlStatus(OrchestrationControlStatus.ACTIVE.name())
                .subTasks(List.of(Map.of(
                        "subTaskId", "sub-card-1",
                        "targetAgent", "risk_assessment_agent",
                        "goal", "请继续分析",
                        "priority", 100,
                        "status", SubTaskStatus.BLOCKED.name()
                )))
                .updatedAt(LocalDateTime.now())
                .build();
        OrchestrationTaskSnapshot circuitTask = OrchestrationTaskSnapshot.builder()
                .taskId("task-card-2")
                .sessionId("session-card-2")
                .taskType("context_enrichment")
                .status(TaskStatus.BLOCKED.name())
                .controlStatus(OrchestrationControlStatus.ACTIVE.name())
                .circuitOpenUntil(LocalDateTime.now().plusMinutes(5))
                .circuitOpenReason("watchdog_failure_threshold_reached")
                .subTasks(List.of(Map.of(
                        "subTaskId", "sub-card-2",
                        "targetAgent", "risk_assessment_agent",
                        "goal", "请补充患者画像和病史后继续分析",
                        "priority", 100,
                        "status", SubTaskStatus.BLOCKED.name()
                )))
                .events(List.of(Map.of(
                        "type", TaskEventType.BLOCKED.name(),
                        "metadata", Map.of(
                                "subTaskId", "sub-card-2",
                                "targetAgent", "risk_assessment_agent",
                                "failureCategory", "context_gap",
                                "missingCapabilities", List.of("patient_context")
                        )
                )))
                .updatedAt(LocalDateTime.now())
                .build();
        when(persistenceService.listActiveSnapshots()).thenReturn(List.of(reviewTask, circuitTask));

        List<Map<String, Object>> cards = watchdogService.listTaskCards(null, "low_confidence_score_gap", null, null, null, null, null);
        Map<String, Object> filterOptions = watchdogService.getFilterOptions();

        assertEquals(0, cards.size());
        assertFalse(((List<?>) filterOptions.get("taskTypes")).isEmpty());
        assertEquals(1L, filterOptions.get("circuitBreakerTaskCount"));
    }

    @Test
    void shouldFilterAndExposeContractHealthCards() {
        OrchestrationPersistenceService persistenceService = mock(OrchestrationPersistenceService.class);
        TaskScheduler scheduler = mock(TaskScheduler.class);
        ReActAgentExecutor executor = mock(ReActAgentExecutor.class);
        SubAgentRegistry registry = new SubAgentRegistry(List.of(
                agent("risk_assessment_agent"),
                agent("patient_profile_agent"),
                agent("evidence_support_agent")
        ));
        OrchestrationWatchdogService watchdogService = new OrchestrationWatchdogService(
                persistenceService,
                scheduler,
                executor,
                registry,
                new SubTaskHandoffRouter(registry, null)
        );

        OrchestrationTaskSnapshot validatedSnapshot = OrchestrationTaskSnapshot.builder()
                .taskId("task-validated")
                .sessionId("session-validated")
                .status(TaskStatus.RUNNING.name())
                .controlStatus(OrchestrationControlStatus.ACTIVE.name())
                .latestContractValidated(Boolean.TRUE)
                .latestContractSchema("sub-agent-result")
                .latestContractVersion("1.0")
                .latestContractCompatibility("compatible")
                .latestContractValidationErrorCount(0)
                .subTasks(List.of(Map.of(
                        "subTaskId", "sub-validated",
                        "targetAgent", "risk_assessment_agent",
                        "goal", "请继续分析",
                        "priority", 100,
                        "status", SubTaskStatus.READY.name()
                )))
                .updatedAt(LocalDateTime.now())
                .build();
        OrchestrationTaskSnapshot invalidSnapshot = OrchestrationTaskSnapshot.builder()
                .taskId("task-invalid")
                .sessionId("session-invalid")
                .status(TaskStatus.BLOCKED.name())
                .controlStatus(OrchestrationControlStatus.ACTIVE.name())
                .latestContractValidated(Boolean.FALSE)
                .latestContractSchema("sub-agent-result")
                .latestContractVersion("0.9")
                .latestContractCompatibility("schema_only")
                .latestContractValidationErrorCount(2)
                .latestContractValidationErrors(List.of("unsupported_contract_version:0.9", "missing_summary"))
                .subTasks(List.of(Map.of(
                        "subTaskId", "sub-invalid",
                        "targetAgent", "risk_assessment_agent",
                        "goal", "请继续分析",
                        "priority", 100,
                        "status", SubTaskStatus.BLOCKED.name()
                )))
                .updatedAt(LocalDateTime.now())
                .build();
        OrchestrationTaskSnapshot unknownSnapshot = OrchestrationTaskSnapshot.builder()
                .taskId("task-unknown")
                .sessionId("session-unknown")
                .status(TaskStatus.WAITING_SUPPORT.name())
                .controlStatus(OrchestrationControlStatus.ACTIVE.name())
                .subTasks(List.of(Map.of(
                        "subTaskId", "sub-unknown",
                        "targetAgent", "risk_assessment_agent",
                        "goal", "请继续分析",
                        "priority", 100,
                        "status", SubTaskStatus.READY.name()
                )))
                .updatedAt(LocalDateTime.now())
                .build();
        when(persistenceService.listActiveSnapshots()).thenReturn(List.of(validatedSnapshot, invalidSnapshot, unknownSnapshot));

        List<Map<String, Object>> compatibleCards = watchdogService.listTaskCards(
                null, null, null, null, "compatible", null, null);
        List<Map<String, Object>> validatedCards = watchdogService.listTaskCards(
                null, null, null, null, null, Boolean.TRUE, null);
        List<Map<String, Object>> errorsOnlyCards = watchdogService.listTaskCards(
                null, null, null, null, null, null, Boolean.TRUE);
        Map<String, Object> filterOptions = watchdogService.getFilterOptions();

        assertEquals(1, compatibleCards.size());
        assertEquals("task-validated", compatibleCards.get(0).get("taskId"));
        assertEquals(1, validatedCards.size());
        assertEquals("task-validated", validatedCards.get(0).get("taskId"));
        assertEquals(1, errorsOnlyCards.size());
        assertEquals("task-invalid", errorsOnlyCards.get(0).get("taskId"));
        assertEquals(2, ((List<?>) filterOptions.get("contractCompatibilities")).size());
        assertEquals(3, ((List<?>) filterOptions.get("contractValidationStates")).size());
    }

    @Test
    void shouldExposeContractSummaryInTaskPayload() {
        OrchestrationPersistenceService persistenceService = mock(OrchestrationPersistenceService.class);
        TaskScheduler scheduler = mock(TaskScheduler.class);
        ReActAgentExecutor executor = mock(ReActAgentExecutor.class);
        SubAgentRegistry registry = new SubAgentRegistry(List.of(
                agent("risk_assessment_agent"),
                agent("patient_profile_agent"),
                agent("evidence_support_agent")
        ));
        OrchestrationWatchdogService watchdogService = new OrchestrationWatchdogService(
                persistenceService,
                scheduler,
                executor,
                registry,
                new SubTaskHandoffRouter(registry, null)
        );

        OrchestrationTaskSnapshot snapshot = OrchestrationTaskSnapshot.builder()
                .taskId("task-contract-card")
                .sessionId("session-contract-card")
                .status(TaskStatus.BLOCKED.name())
                .controlStatus(OrchestrationControlStatus.ACTIVE.name())
                .latestContractValidated(Boolean.FALSE)
                .latestContractSchema("sub-agent-result")
                .latestContractVersion("0.9")
                .latestContractCompatibility("schema_only")
                .latestContractValidationErrorCount(1)
                .latestContractValidationErrors(List.of("unsupported_contract_version:0.9"))
                .latestContractFailureCategory("context_gap")
                .latestContractSuggestedHandoffAgent("patient_profile_agent")
                .latestContractMissingCapabilities(List.of("patient_context"))
                .subTasks(List.of(Map.of(
                        "subTaskId", "sub-contract-card",
                        "targetAgent", "risk_assessment_agent",
                        "goal", "请继续分析",
                        "priority", 100,
                        "status", SubTaskStatus.BLOCKED.name()
                )))
                .events(List.of(Map.of(
                        "type", TaskEventType.BLOCKED.name(),
                        "metadata", Map.ofEntries(
                                Map.entry("subTaskId", "sub-contract-card"),
                                Map.entry("targetAgent", "risk_assessment_agent"),
                                Map.entry("resolvedAgentName", "risk_assessment_agent_backup"),
                                Map.entry("resolvedAgentRouteKey", "risk_assessment_agent"),
                                Map.entry("resolvedAgentProvider", "remote"),
                                Map.entry("failureCategory", "context_gap"),
                                Map.entry("missingCapabilities", List.of("patient_context")),
                                Map.entry("suggestedHandoffAgent", "patient_profile_agent"),
                                Map.entry("contractSchema", "sub-agent-result"),
                                Map.entry("contractVersion", "0.9"),
                                Map.entry("contractCompatibility", Map.of(
                                        "expectedSchema", "sub-agent-result",
                                        "expectedVersion", "1.0",
                                        "schemaCompatible", true,
                                        "versionCompatible", false,
                                        "validated", false
                                )),
                                Map.entry("contractValidationErrors", List.of("unsupported_contract_version:0.9"))
                        )
                )))
                .updatedAt(LocalDateTime.now())
                .build();
        when(persistenceService.listActiveSnapshots()).thenReturn(List.of(snapshot));

        Map<String, Object> task = watchdogService.listActiveTasks().get(0);
        List<?> subTasks = (List<?>) task.get("subTasks");
        Map<?, ?> firstSubTask = (Map<?, ?>) subTasks.get(0);

        assertEquals(Boolean.FALSE, task.get("latestContractValidated"));
        assertEquals("sub-agent-result", task.get("latestContractSchema"));
        assertEquals("0.9", task.get("latestContractVersion"));
        assertEquals("schema_only", task.get("latestContractCompatibility"));
        assertEquals(List.of("unsupported_contract_version:0.9"), task.get("latestContractValidationErrors"));
        assertEquals("context_gap", task.get("latestContractFailureCategory"));
        assertEquals("patient_profile_agent", task.get("latestContractSuggestedHandoffAgent"));
        assertEquals(List.of("patient_context"), task.get("latestContractMissingCapabilities"));
        assertEquals(Boolean.FALSE, firstSubTask.get("recentContractValidated"));
        assertEquals("risk_assessment_agent_backup", firstSubTask.get("recentResolvedAgentName"));
        assertEquals("risk_assessment_agent", firstSubTask.get("recentResolvedAgentRouteKey"));
        assertEquals("remote", firstSubTask.get("recentResolvedAgentProvider"));
        assertEquals("sub-agent-result", firstSubTask.get("recentContractSchema"));
        assertEquals("0.9", firstSubTask.get("recentContractVersion"));
        assertEquals("schema_only", firstSubTask.get("recentContractCompatibility"));
        assertEquals(List.of("unsupported_contract_version:0.9"), firstSubTask.get("recentContractValidationErrors"));
        assertEquals("patient_profile_agent", firstSubTask.get("recentContractSuggestedHandoffAgent"));
    }

    @Test
    void shouldAdvanceSnapshotWhenOnlyLowConfidenceScoreGapExists() {
        OrchestrationPersistenceService persistenceService = mock(OrchestrationPersistenceService.class);
        TaskScheduler scheduler = mock(TaskScheduler.class);
        ReActAgentExecutor executor = mock(ReActAgentExecutor.class);
        SubAgentRegistry registry = new SubAgentRegistry(List.of(
                agent("risk_assessment_agent"),
                agent("patient_profile_agent"),
                agent("evidence_support_agent")
        ));
        OrchestrationWatchdogService watchdogService = new OrchestrationWatchdogService(
                persistenceService,
                scheduler,
                executor,
                registry,
                new SubTaskHandoffRouter(registry, null)
        );

        OrchestrationTaskSnapshot snapshot = OrchestrationTaskSnapshot.builder()
                .taskId("task-manual")
                .sessionId("session-manual")
                .status(TaskStatus.BLOCKED.name())
                .controlStatus(OrchestrationControlStatus.ACTIVE.name())
                .subTasks(List.of(Map.of(
                        "subTaskId", "sub-manual",
                        "targetAgent", "risk_assessment_agent",
                        "goal", "请继续分析",
                        "priority", 100,
                        "status", SubTaskStatus.BLOCKED.name()
                )))
                .updatedAt(LocalDateTime.now())
                .build();

        watchdogService.advanceSnapshot(snapshot);

        verify(scheduler).restoreFromSnapshot(snapshot);
        verify(persistenceService).rebuildAgentState(snapshot);
        verify(persistenceService, never()).deferForManualReview(snapshot, "存在 1 个低置信度子任务，建议人工确认: low_confidence_score_gap:fallback_from:risk_assessment_agent");
    }

    @Test
    void shouldNotAdvanceSnapshotWhenCircuitBreakerIsOpen() {
        OrchestrationPersistenceService persistenceService = mock(OrchestrationPersistenceService.class);
        TaskScheduler scheduler = mock(TaskScheduler.class);
        ReActAgentExecutor executor = mock(ReActAgentExecutor.class);
        SubAgentRegistry registry = new SubAgentRegistry(List.of(
                agent("risk_assessment_agent"),
                agent("patient_profile_agent"),
                agent("evidence_support_agent")
        ));
        OrchestrationWatchdogService watchdogService = new OrchestrationWatchdogService(
                persistenceService,
                scheduler,
                executor,
                registry,
                new SubTaskHandoffRouter(registry, null)
        );

        OrchestrationTaskSnapshot snapshot = OrchestrationTaskSnapshot.builder()
                .taskId("task-circuit")
                .sessionId("session-circuit")
                .status(TaskStatus.BLOCKED.name())
                .controlStatus(OrchestrationControlStatus.ACTIVE.name())
                .circuitOpenUntil(LocalDateTime.now().plusMinutes(10))
                .subTasks(List.of(Map.of(
                        "subTaskId", "sub-circuit",
                        "targetAgent", "risk_assessment_agent",
                        "goal", "请继续分析",
                        "priority", 100,
                        "status", SubTaskStatus.READY.name()
                )))
                .updatedAt(LocalDateTime.now())
                .build();

        watchdogService.advanceSnapshot(snapshot);

        verifyNoInteractions(executor);
        verify(scheduler, never()).restoreFromSnapshot(snapshot);
        verify(persistenceService, never()).rebuildAgentState(snapshot);
    }

    private ChatSubAgent agent(String name) {
        return new ChatSubAgent() {
            @Override
            public String name() {
                return name;
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
            public SubAgentResult execute(AgentState state, String delegatedTask) {
                return new SubAgentResult(name, true, true, "watchdog 已执行子任务。", List.of("risk_assessment"));
            }
        };
    }

    private ChatSubAgent successAgent(String name) {
        return new ChatSubAgent() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public String description() {
                return "success stub";
            }

            @Override
            public List<AgentTool> tools() {
                return List.of();
            }

            @Override
            public SubAgentResult execute(AgentState state, String delegatedTask) {
                return new SubAgentResult(
                        name,
                        true,
                        true,
                        "子任务已恢复完成",
                        List.of("patient_profile")
                );
            }
        };
    }
}
