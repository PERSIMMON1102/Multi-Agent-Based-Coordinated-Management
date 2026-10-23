package com.medical.agent.service.react.orchestration;

import com.medical.agent.model.mongo.OrchestrationTaskSnapshot;
import com.medical.agent.model.mongo.repository.OrchestrationTaskSnapshotRepository;
import com.medical.agent.service.react.model.AgentState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@Service
public class OrchestrationPersistenceService {

    private static final Logger log = LoggerFactory.getLogger(OrchestrationPersistenceService.class);
    private static final int BASE_BACKOFF_SECONDS = 30;
    private static final int MAX_BACKOFF_SECONDS = 10 * 60;
    private static final int CIRCUIT_BREAKER_FAILURE_THRESHOLD = 3;
    private static final int CIRCUIT_BREAKER_OPEN_MINUTES = 15;
    private static final int ROUTING_MEMORY_SNAPSHOT_LIMIT = 200;
    private static final List<String> ACTIVE_STATUSES = List.of(
            TaskStatus.PLANNING.name(),
            TaskStatus.RUNNING.name(),
            TaskStatus.WAITING_SUPPORT.name(),
            TaskStatus.BLOCKED.name()
    );

    private final OrchestrationTaskSnapshotRepository snapshotRepository;

    public OrchestrationPersistenceService(OrchestrationTaskSnapshotRepository snapshotRepository) {
        this.snapshotRepository = snapshotRepository;
    }

    public void save(AgentState state, OrchestrationTrace trace) {
        if (state == null || state.getSessionId() == null || state.getSessionId().isBlank() || trace == null) {
            return;
        }
        try {
            OrchestrationTaskSnapshot existingSnapshot = findLatestByTaskId(trace.taskId()).orElse(null);
            List<Map<String, Object>> eventPayload = mergeEventPayload(trace, existingSnapshot);
            Map<String, Object> latestContractSummary = latestContractSummary(eventPayload);
            OrchestrationTaskSnapshot snapshot = OrchestrationTaskSnapshot.builder()
                    .id(existingSnapshot == null ? null : existingSnapshot.getId())
                    .sessionId(state.getSessionId())
                    .taskId(trace.taskId())
                    .ownerRole(state.getOwnerRole())
                    .ownerUserId(state.getOwnerUserId())
                    .patientId(state.getPatientId())
                    .userMessage(state.getUserMessage())
                    .progressSummary(state.getProgressSummary())
                    .nextAction(state.getNextAction())
                    .blockers(state.getBlockers() == null ? List.of() : List.copyOf(state.getBlockers()))
                    .usedTools(state.getUsedTools() == null ? List.of() : state.getUsedTools().stream().toList())
                    .safetyFlags(state.getSafetyFlags() == null ? List.of() : state.getSafetyFlags().stream().toList())
                    .goal(trace.goal())
                    .taskType(existingSnapshot == null ? classifyTaskType(trace.goal()) : existingSnapshot.getTaskType())
                    .status(trace.status() == null ? null : trace.status().name())
                    .assignedAgent(trace.assignedAgent())
                    .latestContractValidated(booleanValue(latestContractSummary.get("validated")))
                    .latestContractSchema(stringValue(latestContractSummary.get("schema")))
                    .latestContractVersion(stringValue(latestContractSummary.get("version")))
                    .latestContractCompatibility(stringValue(latestContractSummary.get("compatibility")))
                    .latestContractValidationErrorCount(intValue(latestContractSummary.get("validationErrorCount")))
                    .latestContractValidationErrors(stringList(latestContractSummary.get("validationErrors")))
                    .latestContractFailureCategory(stringValue(latestContractSummary.get("failureCategory")))
                    .latestContractSuggestedHandoffAgent(stringValue(latestContractSummary.get("suggestedHandoffAgent")))
                    .latestContractMissingCapabilities(stringList(latestContractSummary.get("missingCapabilities")))
                    .dependencies(trace.dependencies() == null ? List.of() : trace.dependencies())
                    .subTasks(toSubTaskPayload(trace))
                    .events(eventPayload)
                    .nextDecision(toDecisionPayload(trace.nextDecision()))
                    .controlStatus(resolveControlStatus(existingSnapshot))
                    .lastControlAction(existingSnapshot == null ? null : existingSnapshot.getLastControlAction())
                    .lastControlBy(existingSnapshot == null ? null : existingSnapshot.getLastControlBy())
                    .lastControlReason(existingSnapshot == null ? null : existingSnapshot.getLastControlReason())
                    .lastControlAt(existingSnapshot == null ? null : existingSnapshot.getLastControlAt())
                    .failureCount(resolveFailureCount(trace, existingSnapshot))
                    .watchdogFailureStreak(0)
                    .circuitOpenUntil(null)
                    .circuitOpenReason(null)
                    .nextRunAt(resolveNextRunAt(trace, existingSnapshot))
                    .updatedAt(LocalDateTime.now())
                    .build();
            snapshotRepository.save(snapshot);
        } catch (Exception ex) {
            log.warn("Failed to persist orchestration snapshot for session {}: {}", state.getSessionId(), ex.getMessage());
        }
    }

    public Optional<OrchestrationTaskSnapshot> findLatestActiveBySessionId(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return Optional.empty();
        }
        try {
            return snapshotRepository.findBySessionIdOrderByUpdatedAtDesc(sessionId).stream()
                    .filter(this::isRunnableSnapshot)
                    .findFirst();
        } catch (Exception ex) {
            log.warn("Failed to load active orchestration snapshot for session {}: {}", sessionId, ex.getMessage());
            return Optional.empty();
        }
    }

    public List<OrchestrationTaskSnapshot> listActiveSnapshots() {
        return reduceLatestSnapshots(loadActiveSnapshots(), false);
    }

    public List<OrchestrationTaskSnapshot> listRunnableSnapshots() {
        return reduceLatestSnapshots(
                loadRunnableSnapshots(),
                true
        );
    }

    public Optional<OrchestrationTaskSnapshot> findLatestByTaskIdPublic(String taskId) {
        try {
            return findLatestByTaskId(taskId);
        } catch (Exception ex) {
            log.warn("Failed to load orchestration snapshot for task {}: {}", taskId, ex.getMessage());
            return Optional.empty();
        }
    }

    public Optional<OrchestrationTaskSnapshot> findLatestBySessionIdPublic(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return Optional.empty();
        }
        try {
            return snapshotRepository.findFirstBySessionIdOrderByUpdatedAtDesc(sessionId);
        } catch (Exception ex) {
            log.warn("Failed to load latest orchestration snapshot for session {}: {}", sessionId, ex.getMessage());
            return Optional.empty();
        }
    }

    public Map<String, Map<String, Integer>> summarizeRecentAgentPerformance() {
        return reduceLatestSnapshots(loadRecentSnapshots(), false).stream()
                .limit(ROUTING_MEMORY_SNAPSHOT_LIMIT)
                .flatMap(snapshot -> snapshot.getEvents() == null ? java.util.stream.Stream.empty() : snapshot.getEvents().stream())
                .map(this::toAgentPerformanceEvent)
                .filter(item -> item != null)
                .collect(Collectors.toMap(
                        AgentPerformanceEvent::agent,
                        this::toPerformanceStats,
                        this::mergePerformanceStats,
                        LinkedHashMap::new
                ));
    }

    public Map<String, Long> summarizeRecentControlEvents(int recentHours) {
        LocalDateTime cutoff = LocalDateTime.now().minusHours(Math.max(recentHours, 1));
        Map<String, Long> counts = new LinkedHashMap<>();
        reduceLatestSnapshots(loadRecentSnapshots(), false).stream()
                .limit(ROUTING_MEMORY_SNAPSHOT_LIMIT)
                .flatMap(snapshot -> snapshot.getEvents() == null ? java.util.stream.Stream.empty() : snapshot.getEvents().stream())
                .forEach(event -> {
                    String type = event.get("type") instanceof String text ? text : null;
                    LocalDateTime occurredAt = event.get("occurredAt") instanceof LocalDateTime time ? time : null;
                    if (type == null || occurredAt == null || occurredAt.isBefore(cutoff)) {
                        return;
                    }
                    counts.merge(type, 1L, Long::sum);
                });
        return counts;
    }

    public List<Map<String, Object>> summarizeRecentControlEventBuckets(int recentHours) {
        int hours = Math.max(recentHours, 1);
        LocalDateTime latestBucket = LocalDateTime.now().truncatedTo(ChronoUnit.HOURS);
        LocalDateTime earliestBucket = latestBucket.minusHours(hours - 1L);
        Map<LocalDateTime, Map<String, Long>> bucketCounts = new LinkedHashMap<>();
        for (int index = 0; index < hours; index++) {
            LocalDateTime bucketStart = earliestBucket.plusHours(index);
            bucketCounts.put(bucketStart, new LinkedHashMap<>());
        }
        reduceLatestSnapshots(loadRecentSnapshots(), false).stream()
                .limit(ROUTING_MEMORY_SNAPSHOT_LIMIT)
                .flatMap(snapshot -> snapshot.getEvents() == null ? java.util.stream.Stream.empty() : snapshot.getEvents().stream())
                .forEach(event -> {
                    String type = event.get("type") instanceof String text ? text : null;
                    LocalDateTime occurredAt = event.get("occurredAt") instanceof LocalDateTime time ? time : null;
                    if (type == null || occurredAt == null) {
                        return;
                    }
                    LocalDateTime bucketStart = occurredAt.truncatedTo(ChronoUnit.HOURS);
                    if (bucketStart.isBefore(earliestBucket) || bucketStart.isAfter(latestBucket)) {
                        return;
                    }
                    bucketCounts.computeIfAbsent(bucketStart, ignored -> new LinkedHashMap<>())
                            .merge(type, 1L, Long::sum);
                });
        return bucketCounts.entrySet().stream()
                .map(entry -> {
                    Map<String, Object> payload = new LinkedHashMap<>();
                    payload.put("bucketStart", entry.getKey());
                    payload.put("bucketEnd", entry.getKey().plusHours(1));
                    payload.put("awaitingReviewCount", entry.getValue().getOrDefault(TaskEventType.SYSTEM_AWAITING_REVIEW.name(), 0L));
                    payload.put("reviewAckedCount", entry.getValue().getOrDefault(TaskEventType.MANUAL_REVIEW_ACKED.name(), 0L));
                    payload.put("controlEventCount", entry.getValue().values().stream().mapToLong(Long::longValue).sum());
                    payload.put("eventCounts", entry.getValue());
                    return payload;
                })
                .toList();
    }

    public Map<String, Object> summarizeRecentIssueSignals(int recentHours) {
        int hours = Math.max(recentHours, 1);
        LocalDateTime latestBucket = LocalDateTime.now().truncatedTo(ChronoUnit.HOURS);
        LocalDateTime earliestBucket = latestBucket.minusHours(hours - 1L);
        Map<String, Long> failureCategoryCounts = new LinkedHashMap<>();
        Map<String, Long> missingCapabilityCounts = new LinkedHashMap<>();
        Map<LocalDateTime, Map<String, Long>> failureBuckets = initializeHourlyBuckets(earliestBucket, hours);
        Map<LocalDateTime, Map<String, Long>> capabilityBuckets = initializeHourlyBuckets(earliestBucket, hours);

        reduceLatestSnapshots(loadRecentSnapshots(), false).stream()
                .limit(ROUTING_MEMORY_SNAPSHOT_LIMIT)
                .flatMap(snapshot -> snapshot.getEvents() == null ? java.util.stream.Stream.empty() : snapshot.getEvents().stream())
                .forEach(event -> collectIssueSignal(event, earliestBucket, latestBucket, failureCategoryCounts, missingCapabilityCounts, failureBuckets, capabilityBuckets));

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("topFailureCategories", topEntries(failureCategoryCounts, 5));
        payload.put("topMissingCapabilities", topEntries(missingCapabilityCounts, 5));
        payload.put("recent24hFailureCategoryTrend", toSignalTrend(failureBuckets));
        payload.put("recent24hMissingCapabilityTrend", toSignalTrend(capabilityBuckets));
        return payload;
    }

    public List<Map<String, Object>> summarizeRecentAgentIssueSignals(int recentHours) {
        int hours = Math.max(recentHours, 1);
        LocalDateTime latestBucket = LocalDateTime.now().truncatedTo(ChronoUnit.HOURS);
        LocalDateTime earliestBucket = latestBucket.minusHours(hours - 1L);
        Map<String, AgentIssueAccumulator> byAgent = new LinkedHashMap<>();

        reduceLatestSnapshots(loadRecentSnapshots(), false).stream()
                .limit(ROUTING_MEMORY_SNAPSHOT_LIMIT)
                .flatMap(snapshot -> snapshot.getEvents() == null ? java.util.stream.Stream.empty() : snapshot.getEvents().stream())
                .forEach(event -> collectAgentIssueSignal(event, earliestBucket, latestBucket, byAgent, hours));

        return byAgent.entrySet().stream()
                .sorted((left, right) -> Long.compare(right.getValue().totalIssueCount(), left.getValue().totalIssueCount()))
                .map(entry -> {
                    Map<String, Object> payload = new LinkedHashMap<>();
                    payload.put("agent", entry.getKey());
                    payload.put("issueCount", entry.getValue().totalIssueCount());
                    payload.put("failureCategoryCounts", entry.getValue().failureCategoryCounts());
                    payload.put("missingCapabilityCounts", entry.getValue().missingCapabilityCounts());
                    payload.put("topFailureCategories", topEntries(entry.getValue().failureCategoryCounts(), 3));
                    payload.put("topMissingCapabilities", topEntries(entry.getValue().missingCapabilityCounts(), 3));
                    payload.put("recent24hFailureCategoryTrend", toSignalTrend(entry.getValue().failureBuckets()));
                    payload.put("recent24hMissingCapabilityTrend", toSignalTrend(entry.getValue().capabilityBuckets()));
                    return payload;
                })
                .toList();
    }

    public List<Map<String, Object>> summarizeRecentAgentExecutionHealth(int recentHours) {
        int hours = Math.max(recentHours, 1);
        LocalDateTime latestBucket = LocalDateTime.now().truncatedTo(ChronoUnit.HOURS);
        LocalDateTime earliestBucket = latestBucket.minusHours(hours - 1L);
        Map<String, AgentExecutionAccumulator> byAgent = new LinkedHashMap<>();

        reduceLatestSnapshots(loadRecentSnapshots(), false).stream()
                .limit(ROUTING_MEMORY_SNAPSHOT_LIMIT)
                .flatMap(snapshot -> snapshot.getEvents() == null ? java.util.stream.Stream.empty() : snapshot.getEvents().stream())
                .forEach(event -> collectAgentExecutionHealth(event, earliestBucket, latestBucket, byAgent, hours));

        return byAgent.entrySet().stream()
                .sorted((left, right) -> Double.compare(right.getValue().stabilityScore(), left.getValue().stabilityScore()))
                .map(entry -> {
                    Map<String, Object> payload = new LinkedHashMap<>();
                    payload.put("agent", entry.getKey());
                    payload.put("completedCount", entry.getValue().completedCount());
                    payload.put("failedCount", entry.getValue().failedCount());
                    payload.put("blockedCount", entry.getValue().blockedCount());
                    payload.put("reroutedCount", entry.getValue().reroutedCount());
                    payload.put("executionCount", entry.getValue().executionCount());
                    payload.put("successRate", entry.getValue().successRate());
                    payload.put("stabilityScore", entry.getValue().stabilityScore());
                    payload.put("recentConsecutiveFailureCount", entry.getValue().recentConsecutiveFailureCount());
                    payload.put("lastExecutionAt", entry.getValue().lastExecutionAt());
                    payload.put("lastSuccessfulAt", entry.getValue().lastSuccessfulAt());
                    payload.put("recent24hExecutionTrend", toExecutionTrend(entry.getValue().executionBuckets()));
                    return payload;
                })
                .toList();
    }

    private List<OrchestrationTaskSnapshot> loadActiveSnapshots() {
        try {
            return snapshotRepository.findByStatusInOrderByUpdatedAtAsc(ACTIVE_STATUSES);
        } catch (Exception ex) {
            log.warn("Failed to load active orchestration snapshots: {}", ex.getMessage());
            return List.of();
        }
    }

    private List<OrchestrationTaskSnapshot> loadRunnableSnapshots() {
        try {
            return snapshotRepository.findByStatusInAndNextRunAtLessThanEqualOrderByUpdatedAtAsc(ACTIVE_STATUSES, LocalDateTime.now());
        } catch (Exception ex) {
            log.warn("Failed to load runnable orchestration snapshots: {}", ex.getMessage());
            return List.of();
        }
    }

    private List<OrchestrationTaskSnapshot> loadRecentSnapshots() {
        try {
            return snapshotRepository.findTop200ByOrderByUpdatedAtDesc();
        } catch (Exception ex) {
            log.warn("Failed to load recent orchestration snapshots: {}", ex.getMessage());
            return List.of();
        }
    }

    public AgentState rebuildAgentState(OrchestrationTaskSnapshot snapshot) {
        if (snapshot == null) {
            return null;
        }
        AgentState state = new AgentState();
        state.setSessionId(snapshot.getSessionId());
        state.setOwnerRole(snapshot.getOwnerRole());
        state.setOwnerUserId(snapshot.getOwnerUserId());
        state.setPatientId(snapshot.getPatientId());
        state.setUserMessage(snapshot.getUserMessage());
        state.setGoal(snapshot.getGoal());
        state.setProgressSummary(snapshot.getProgressSummary());
        state.setNextAction(snapshot.getNextAction());
        if (snapshot.getBlockers() != null) {
            state.setBlockers(snapshot.getBlockers());
        }
        if (snapshot.getUsedTools() != null) {
            snapshot.getUsedTools().forEach(state::addUsedTool);
        }
        if (snapshot.getSafetyFlags() != null) {
            snapshot.getSafetyFlags().forEach(state::addSafetyFlag);
        }
        return state;
    }

    public void markExecutionFailure(OrchestrationTaskSnapshot snapshot, String reason) {
        if (snapshot == null) {
            return;
        }
        int failureCount = snapshot.getFailureCount() == null ? 0 : snapshot.getFailureCount();
        failureCount++;
        snapshot.setFailureCount(failureCount);
        int watchdogFailureStreak = snapshot.getWatchdogFailureStreak() == null ? 0 : snapshot.getWatchdogFailureStreak();
        watchdogFailureStreak++;
        snapshot.setWatchdogFailureStreak(watchdogFailureStreak);
        LocalDateTime nextRunAt = LocalDateTime.now().plusSeconds(computeBackoffSeconds(failureCount));
        snapshot.setNextRunAt(nextRunAt);
        if (watchdogFailureStreak >= CIRCUIT_BREAKER_FAILURE_THRESHOLD) {
            snapshot.setCircuitOpenUntil(LocalDateTime.now().plusMinutes(CIRCUIT_BREAKER_OPEN_MINUTES));
            snapshot.setCircuitOpenReason(reason == null || reason.isBlank() ? "watchdog_failure_threshold_reached" : reason);
            snapshot.setNextRunAt(snapshot.getCircuitOpenUntil());
        }
        snapshot.setUpdatedAt(LocalDateTime.now());
        try {
            snapshotRepository.save(snapshot);
        } catch (Exception ex) {
            log.warn("Failed to update orchestration failure state for session {}: {}", snapshot.getSessionId(), ex.getMessage());
        }
    }

    public void deferForManualReview(OrchestrationTaskSnapshot snapshot, String summary) {
        if (snapshot == null) {
            return;
        }
        snapshot.setControlStatus(OrchestrationControlStatus.AWAITING_REVIEW.name());
        snapshot.setNextRunAt(LocalDateTime.now());
        snapshot.setUpdatedAt(LocalDateTime.now());
        snapshot.setLastControlAction(TaskEventType.SYSTEM_AWAITING_REVIEW.name());
        snapshot.setLastControlBy("system_watchdog");
        snapshot.setLastControlReason(summary);
        snapshot.setLastControlAt(LocalDateTime.now());
        List<Map<String, Object>> events = snapshot.getEvents() == null ? new java.util.ArrayList<>() : new java.util.ArrayList<>(snapshot.getEvents());
        events.add(Map.of(
                "type", TaskEventType.SYSTEM_AWAITING_REVIEW.name(),
                "occurredAt", LocalDateTime.now(),
                "summary", summary == null || summary.isBlank() ? "Watchdog 将任务切换为等待人工确认。" : summary,
                "metadata", Map.of(
                        "operator", "system_watchdog",
                        "reason", summary == null ? "" : summary
                )
        ));
        snapshot.setEvents(events);
        try {
            snapshotRepository.save(snapshot);
        } catch (Exception ex) {
            log.warn("Failed to mark orchestration task {} as awaiting review: {}", snapshot.getTaskId(), ex.getMessage());
        }
    }

    public Optional<OrchestrationTaskSnapshot> pauseTask(String taskId, String operator, String reason) {
        return updateTaskControl(taskId, snapshot -> {
            snapshot.setControlStatus(OrchestrationControlStatus.PAUSED.name());
            applyManualAudit(snapshot, TaskEventType.MANUAL_PAUSED, operator, reason, "管理员暂停了自治任务。");
        });
    }

    public Optional<OrchestrationTaskSnapshot> resumeTask(String taskId, String operator, String reason) {
        return updateTaskControl(taskId, snapshot -> {
            if (OrchestrationControlStatus.CANCELLED.name().equals(snapshot.getControlStatus())) {
                return;
            }
            snapshot.setControlStatus(OrchestrationControlStatus.ACTIVE.name());
            snapshot.setNextRunAt(LocalDateTime.now());
            resetCircuit(snapshot);
            applyManualAudit(snapshot, TaskEventType.MANUAL_RESUMED, operator, reason, "管理员恢复了自治任务。");
        });
    }

    public Optional<OrchestrationTaskSnapshot> acknowledgeManualReview(String taskId, String operator, String reason) {
        return updateTaskControl(taskId, snapshot -> {
            if (OrchestrationControlStatus.CANCELLED.name().equals(snapshot.getControlStatus())) {
                return;
            }
            snapshot.setControlStatus(OrchestrationControlStatus.ACTIVE.name());
            snapshot.setNextRunAt(LocalDateTime.now());
            resetCircuit(snapshot);
            applyManualAudit(snapshot, TaskEventType.MANUAL_REVIEW_ACKED, operator, reason, "管理员确认了人工复核结果，允许任务继续推进。");
        });
    }

    public Optional<OrchestrationTaskSnapshot> cancelTask(String taskId, String operator, String reason) {
        return updateTaskControl(taskId, snapshot -> {
            snapshot.setControlStatus(OrchestrationControlStatus.CANCELLED.name());
            applyManualAudit(snapshot, TaskEventType.MANUAL_CANCELLED, operator, reason, "管理员取消了自治任务。");
        });
    }

    public Optional<OrchestrationTaskSnapshot> retryTask(String taskId, String operator, String reason) {
        return updateTaskControl(taskId, snapshot -> {
            snapshot.setControlStatus(OrchestrationControlStatus.ACTIVE.name());
            snapshot.setFailureCount(0);
            snapshot.setNextRunAt(LocalDateTime.now());
            resetCircuit(snapshot);
            if (TaskStatus.COMPLETED.name().equals(snapshot.getStatus()) || TaskStatus.FAILED.name().equals(snapshot.getStatus())) {
                snapshot.setStatus(TaskStatus.BLOCKED.name());
            }
            snapshot.setSubTasks(resetSubTasksForRetry(snapshot.getSubTasks()));
            applyManualAudit(snapshot, TaskEventType.MANUAL_RETRIED, operator, reason, "管理员强制重跑了自治任务。");
        });
    }

    public boolean hasPendingRetryResultPublication(OrchestrationTaskSnapshot snapshot) {
        LocalDateTime latestRetryAt = latestEventTime(snapshot, TaskEventType.MANUAL_RETRIED);
        if (latestRetryAt == null) {
            return false;
        }
        LocalDateTime latestPublishedAt = latestEventTime(snapshot, TaskEventType.RETRY_RESULT_POSTED);
        return latestPublishedAt == null || latestPublishedAt.isBefore(latestRetryAt);
    }

    public Optional<OrchestrationTaskSnapshot> markRetryNoticePosted(String taskId, String operator, String reason) {
        return appendSystemEvent(
                taskId,
                TaskEventType.RETRY_NOTICE_POSTED,
                "系统已将“后台重新执行中”的提示追加到医生端对话窗口。",
                Map.of(
                        "operator", operator == null ? "" : operator,
                        "reason", reason == null ? "" : reason
                )
        );
    }

    public Optional<OrchestrationTaskSnapshot> markRetryResultPosted(String taskId, String summary) {
        return appendSystemEvent(
                taskId,
                TaskEventType.RETRY_RESULT_POSTED,
                summary == null || summary.isBlank()
                        ? "系统已将后台重新执行后的补充分析追加到对话窗口。"
                        : summary,
                Map.of("operator", "system_chat_bridge")
        );
    }

    public Optional<OrchestrationTaskSnapshot> skipSubTask(String taskId,
                                                          String subTaskId,
                                                          String replacementAgent,
                                                          String operator,
                                                          String reason) {
        return updateTaskControl(taskId, snapshot -> {
            Map<String, Object> subTask = findSubTask(snapshot, subTaskId);
            if (subTask == null) {
                return;
            }
            subTask.put("status", SubTaskStatus.SKIPPED.name());
            if (replacementAgent != null && !replacementAgent.isBlank()) {
                addReplacementSubTask(snapshot, subTask, replacementAgent.trim());
            }
            normalizeTaskAfterManualSubTaskControl(snapshot);
            applySubTaskAudit(snapshot, TaskEventType.SUBTASK_SKIPPED, subTaskId, operator, reason, subTask, "管理员跳过了子任务。");
        });
    }

    public Optional<OrchestrationTaskSnapshot> resetSubTask(String taskId, String subTaskId, String operator, String reason) {
        return updateTaskControl(taskId, snapshot -> {
            Map<String, Object> subTask = findSubTask(snapshot, subTaskId);
            if (subTask == null) {
                return;
            }
            subTask.put("status", SubTaskStatus.READY.name());
            snapshot.setControlStatus(OrchestrationControlStatus.ACTIVE.name());
            snapshot.setNextRunAt(LocalDateTime.now());
            resetCircuit(snapshot);
            normalizeTaskAfterManualSubTaskControl(snapshot);
            applySubTaskAudit(snapshot, TaskEventType.SUBTASK_RESET, subTaskId, operator, reason, subTask, "管理员重置了子任务。");
        });
    }

    public Optional<OrchestrationTaskSnapshot> handoffSubTask(String taskId,
                                                              String subTaskId,
                                                              String targetAgent,
                                                              String operator,
                                                              String reason) {
        return updateTaskControl(taskId, snapshot -> {
            Map<String, Object> subTask = findSubTask(snapshot, subTaskId);
            if (subTask == null || targetAgent == null || targetAgent.isBlank()) {
                return;
            }
            subTask.put("targetAgent", targetAgent.trim());
            subTask.put("status", SubTaskStatus.READY.name());
            snapshot.setControlStatus(OrchestrationControlStatus.ACTIVE.name());
            snapshot.setNextRunAt(LocalDateTime.now());
            resetCircuit(snapshot);
            normalizeTaskAfterManualSubTaskControl(snapshot);
            applySubTaskAudit(snapshot, TaskEventType.SUBTASK_HANDOFF, subTaskId, operator, reason, subTask, "管理员转派了子任务。");
        });
    }

    private int resolveFailureCount(OrchestrationTrace trace, OrchestrationTaskSnapshot existingSnapshot) {
        if (existingSnapshot != null && existingSnapshot.getFailureCount() != null) {
            return existingSnapshot.getFailureCount();
        }
        if (trace == null || trace.events() == null) {
            return 0;
        }
        return (int) trace.events().stream()
                .filter(event -> event.type() == TaskEventType.FAILED)
                .count();
    }

    private LocalDateTime resolveNextRunAt(OrchestrationTrace trace, OrchestrationTaskSnapshot existingSnapshot) {
        if (trace == null || trace.status() == null) {
            return LocalDateTime.now();
        }
        if (existingSnapshot != null && !isRunnableControlStatus(existingSnapshot.getControlStatus())) {
            return existingSnapshot.getNextRunAt();
        }
        if (trace.status() == TaskStatus.COMPLETED || trace.status() == TaskStatus.FAILED) {
            return LocalDateTime.now();
        }
        return LocalDateTime.now();
    }

    private String classifyTaskType(String goal) {
        if (goal == null || goal.isBlank()) {
            return "general_coordination";
        }
        String normalized = goal.toLowerCase();
        if (containsAny(normalized, "画像", "病史", "资料", "档案", "患者", "medplum", "context", "profile", "history")) {
            return "context_enrichment";
        }
        if (containsAny(normalized, "证据", "指南", "文献", "知识图谱", "evidence", "guideline", "citation")) {
            return "evidence_synthesis";
        }
        if (containsAny(normalized, "风险", "分层", "高危", "评估", "risk", "triage", "stratification")) {
            return "risk_triage";
        }
        return "general_coordination";
    }

    private String resolveControlStatus(OrchestrationTaskSnapshot existingSnapshot) {
        if (existingSnapshot == null || existingSnapshot.getControlStatus() == null || existingSnapshot.getControlStatus().isBlank()) {
            return OrchestrationControlStatus.ACTIVE.name();
        }
        return existingSnapshot.getControlStatus();
    }

    private long computeBackoffSeconds(int failureCount) {
        long backoff = (long) BASE_BACKOFF_SECONDS * (1L << Math.max(0, failureCount - 1));
        return Math.min(backoff, MAX_BACKOFF_SECONDS);
    }

    private void resetCircuit(OrchestrationTaskSnapshot snapshot) {
        if (snapshot == null) {
            return;
        }
        snapshot.setWatchdogFailureStreak(0);
        snapshot.setCircuitOpenUntil(null);
        snapshot.setCircuitOpenReason(null);
    }

    private Map<LocalDateTime, Map<String, Long>> initializeHourlyBuckets(LocalDateTime earliestBucket, int hours) {
        Map<LocalDateTime, Map<String, Long>> bucketCounts = new LinkedHashMap<>();
        for (int index = 0; index < hours; index++) {
            bucketCounts.put(earliestBucket.plusHours(index), new LinkedHashMap<>());
        }
        return bucketCounts;
    }

    private void collectIssueSignal(Map<String, Object> event,
                                    LocalDateTime earliestBucket,
                                    LocalDateTime latestBucket,
                                    Map<String, Long> failureCategoryCounts,
                                    Map<String, Long> missingCapabilityCounts,
                                    Map<LocalDateTime, Map<String, Long>> failureBuckets,
                                    Map<LocalDateTime, Map<String, Long>> capabilityBuckets) {
        if (event == null || !(event.get("occurredAt") instanceof LocalDateTime occurredAt)) {
            return;
        }
        LocalDateTime bucketStart = occurredAt.truncatedTo(ChronoUnit.HOURS);
        if (bucketStart.isBefore(earliestBucket) || bucketStart.isAfter(latestBucket)) {
            return;
        }
        Map<String, Object> metadata = metadataMap(event);
        String failureCategory = metadata.get("failureCategory") instanceof String text ? text : null;
        if (failureCategory != null && !failureCategory.isBlank()) {
            failureCategoryCounts.merge(failureCategory, 1L, Long::sum);
            failureBuckets.computeIfAbsent(bucketStart, ignored -> new LinkedHashMap<>()).merge(failureCategory, 1L, Long::sum);
        }
        Object missingCapabilities = metadata.get("missingCapabilities");
        if (missingCapabilities instanceof List<?> items) {
            items.stream()
                    .map(item -> item == null ? null : item.toString())
                    .filter(text -> text != null && !text.isBlank())
                    .forEach(capability -> {
                        missingCapabilityCounts.merge(capability, 1L, Long::sum);
                        capabilityBuckets.computeIfAbsent(bucketStart, ignored -> new LinkedHashMap<>()).merge(capability, 1L, Long::sum);
                    });
        }
    }

    private void collectAgentIssueSignal(Map<String, Object> event,
                                         LocalDateTime earliestBucket,
                                         LocalDateTime latestBucket,
                                         Map<String, AgentIssueAccumulator> byAgent,
                                         int hours) {
        if (event == null || !(event.get("occurredAt") instanceof LocalDateTime occurredAt)) {
            return;
        }
        LocalDateTime bucketStart = occurredAt.truncatedTo(ChronoUnit.HOURS);
        if (bucketStart.isBefore(earliestBucket) || bucketStart.isAfter(latestBucket)) {
            return;
        }
        Map<String, Object> metadata = metadataMap(event);
        String agent = trackedAgentName(metadata);
        if (agent == null || agent.isBlank()) {
            return;
        }
        AgentIssueAccumulator accumulator = byAgent.computeIfAbsent(
                agent,
                ignored -> new AgentIssueAccumulator(
                        initializeHourlyBuckets(earliestBucket, hours),
                        initializeHourlyBuckets(earliestBucket, hours)
                )
        );
        String failureCategory = metadata.get("failureCategory") instanceof String text ? text : null;
        if (failureCategory != null && !failureCategory.isBlank()) {
            accumulator.failureCategoryCounts().merge(failureCategory, 1L, Long::sum);
            accumulator.failureBuckets().computeIfAbsent(bucketStart, ignored -> new LinkedHashMap<>()).merge(failureCategory, 1L, Long::sum);
        }
        Object missingCapabilities = metadata.get("missingCapabilities");
        if (missingCapabilities instanceof List<?> items) {
            items.stream()
                    .map(item -> item == null ? null : item.toString())
                    .filter(text -> text != null && !text.isBlank())
                    .forEach(capability -> {
                        accumulator.missingCapabilityCounts().merge(capability, 1L, Long::sum);
                        accumulator.capabilityBuckets().computeIfAbsent(bucketStart, ignored -> new LinkedHashMap<>()).merge(capability, 1L, Long::sum);
                    });
        }
    }

    private void collectAgentExecutionHealth(Map<String, Object> event,
                                             LocalDateTime earliestBucket,
                                             LocalDateTime latestBucket,
                                             Map<String, AgentExecutionAccumulator> byAgent,
                                             int hours) {
        if (event == null || !(event.get("occurredAt") instanceof LocalDateTime occurredAt) || !(event.get("type") instanceof String type)) {
            return;
        }
        if (!isExecutionHealthEvent(type)) {
            return;
        }
        LocalDateTime bucketStart = occurredAt.truncatedTo(ChronoUnit.HOURS);
        if (bucketStart.isBefore(earliestBucket) || bucketStart.isAfter(latestBucket)) {
            return;
        }
        Map<String, Object> metadata = metadataMap(event);
        String agent = trackedAgentName(metadata);
        if (agent == null || agent.isBlank()) {
            return;
        }
        AgentExecutionAccumulator accumulator = byAgent.computeIfAbsent(
                agent,
                ignored -> new AgentExecutionAccumulator(initializeHourlyBuckets(earliestBucket, hours))
        );
        accumulator.record(type, bucketStart, occurredAt);
    }

    private Optional<OrchestrationTaskSnapshot> findLatestByTaskId(String taskId) {
        if (taskId == null || taskId.isBlank()) {
            return Optional.empty();
        }
        return snapshotRepository.findFirstByTaskIdOrderByUpdatedAtDesc(taskId);
    }

    private Optional<OrchestrationTaskSnapshot> updateTaskControl(String taskId, Consumer<OrchestrationTaskSnapshot> mutator) {
        Optional<OrchestrationTaskSnapshot> snapshotOptional = findLatestByTaskId(taskId);
        snapshotOptional.ifPresent(snapshot -> {
            mutator.accept(snapshot);
            snapshot.setUpdatedAt(LocalDateTime.now());
            try {
                snapshotRepository.save(snapshot);
            } catch (Exception ex) {
                log.warn("Failed to update orchestration task {}: {}", taskId, ex.getMessage());
            }
        });
        return snapshotOptional;
    }

    private Optional<OrchestrationTaskSnapshot> appendSystemEvent(String taskId,
                                                                  TaskEventType eventType,
                                                                  String summary,
                                                                  Map<String, Object> metadata) {
        return findLatestByTaskId(taskId).map(snapshot -> {
            List<Map<String, Object>> events = snapshot.getEvents() == null
                    ? new java.util.ArrayList<>()
                    : new java.util.ArrayList<>(snapshot.getEvents());
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("type", eventType.name());
            payload.put("occurredAt", LocalDateTime.now());
            payload.put("summary", summary);
            payload.put("metadata", metadata == null ? Map.of() : metadata);
            events.add(payload);
            snapshot.setEvents(events);
            snapshot.setUpdatedAt(LocalDateTime.now());
            try {
                snapshotRepository.save(snapshot);
            } catch (Exception ex) {
                log.warn("Failed to append orchestration event {} for task {}: {}", eventType, taskId, ex.getMessage());
            }
            return snapshot;
        });
    }

    private List<Map<String, Object>> resetSubTasksForRetry(List<Map<String, Object>> subTasks) {
        if (subTasks == null) {
            return List.of();
        }
        return subTasks.stream().map(item -> {
            Map<String, Object> payload = new LinkedHashMap<>(item);
            Object status = payload.get("status");
            if (SubTaskStatus.FAILED.name().equals(status)
                    || SubTaskStatus.BLOCKED.name().equals(status)
                    || SubTaskStatus.COMPLETED.name().equals(status)
                    || SubTaskStatus.SKIPPED.name().equals(status)) {
                payload.put("status", SubTaskStatus.READY.name());
            }
            return payload;
        }).toList();
    }

    private List<OrchestrationTaskSnapshot> reduceLatestSnapshots(List<OrchestrationTaskSnapshot> snapshots, boolean runnableOnly) {
        return snapshots.stream()
                .filter(snapshot -> runnableOnly ? isRunnableSnapshot(snapshot) : isActiveSnapshot(snapshot))
                .collect(Collectors.toMap(
                        snapshot -> snapshot.getTaskId() == null ? snapshot.getId() : snapshot.getTaskId(),
                        snapshot -> snapshot,
                        (left, right) -> latestOf(left, right),
                        LinkedHashMap::new
                ))
                .values().stream()
                .sorted(Comparator.comparing(OrchestrationTaskSnapshot::getUpdatedAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    private OrchestrationTaskSnapshot latestOf(OrchestrationTaskSnapshot left, OrchestrationTaskSnapshot right) {
        LocalDateTime leftTime = left == null ? null : left.getUpdatedAt();
        LocalDateTime rightTime = right == null ? null : right.getUpdatedAt();
        if (leftTime == null) {
            return right;
        }
        if (rightTime == null) {
            return left;
        }
        return rightTime.isAfter(leftTime) ? right : left;
    }

    private boolean isActiveSnapshot(OrchestrationTaskSnapshot snapshot) {
        return snapshot != null
                && snapshot.getStatus() != null
                && ACTIVE_STATUSES.contains(snapshot.getStatus())
                && !OrchestrationControlStatus.CANCELLED.name().equals(snapshot.getControlStatus());
    }

    private boolean isRunnableSnapshot(OrchestrationTaskSnapshot snapshot) {
        return isActiveSnapshot(snapshot)
                && isRunnableControlStatus(snapshot.getControlStatus())
                && !isCircuitOpen(snapshot);
    }

    private boolean isCircuitOpen(OrchestrationTaskSnapshot snapshot) {
        return snapshot != null
                && snapshot.getCircuitOpenUntil() != null
                && snapshot.getCircuitOpenUntil().isAfter(LocalDateTime.now());
    }

    private boolean isRunnableControlStatus(String controlStatus) {
        return controlStatus == null
                || controlStatus.isBlank()
                || OrchestrationControlStatus.ACTIVE.name().equals(controlStatus);
    }

    private boolean containsAny(String text, String... patterns) {
        if (text == null || text.isBlank()) {
            return false;
        }
        for (String pattern : patterns) {
            if (text.contains(pattern)) {
                return true;
            }
        }
        return false;
    }

    private Map<String, Object> findSubTask(OrchestrationTaskSnapshot snapshot, String subTaskId) {
        if (snapshot == null || snapshot.getSubTasks() == null || subTaskId == null || subTaskId.isBlank()) {
            return null;
        }
        return snapshot.getSubTasks().stream()
                .filter(item -> subTaskId.equals(item.get("subTaskId")))
                .findFirst()
                .orElse(null);
    }

    private boolean hasNoRunnableSubTask(List<Map<String, Object>> subTasks) {
        if (subTasks == null || subTasks.isEmpty()) {
            return true;
        }
        return subTasks.stream().noneMatch(this::isRunnableSubTaskPayload);
    }

    private boolean isRunnableSubTaskPayload(Map<String, Object> payload) {
        if (payload == null || !(payload.get("status") instanceof String statusText)) {
            return false;
        }
        return SubTaskStatus.PENDING.name().equals(statusText) || SubTaskStatus.READY.name().equals(statusText);
    }

    private void addReplacementSubTask(OrchestrationTaskSnapshot snapshot,
                                       Map<String, Object> sourceSubTask,
                                       String replacementAgent) {
        if (snapshot == null || sourceSubTask == null || replacementAgent == null || replacementAgent.isBlank()) {
            return;
        }
        List<Map<String, Object>> subTasks = snapshot.getSubTasks() == null ? new java.util.ArrayList<>() : new java.util.ArrayList<>(snapshot.getSubTasks());
        Map<String, Object> replacement = new LinkedHashMap<>();
        replacement.put("subTaskId", UUID.randomUUID().toString());
        replacement.put("goal", sourceSubTask.getOrDefault("goal", ""));
        replacement.put("targetAgent", replacementAgent);
        replacement.put("priority", sourceSubTask.getOrDefault("priority", 70));
        replacement.put("status", SubTaskStatus.READY.name());
        replacement.put("dependencies", sourceSubTask.getOrDefault("dependencies", List.of()));
        replacement.put("replacesSubTaskId", sourceSubTask.getOrDefault("subTaskId", ""));
        subTasks.add(replacement);
        snapshot.setSubTasks(subTasks);
        snapshot.setControlStatus(OrchestrationControlStatus.ACTIVE.name());
        snapshot.setNextRunAt(LocalDateTime.now());
    }

    private void normalizeTaskAfterManualSubTaskControl(OrchestrationTaskSnapshot snapshot) {
        if (snapshot == null) {
            return;
        }
        snapshot.setControlStatus(OrchestrationControlStatus.ACTIVE.name());
        snapshot.setNextRunAt(LocalDateTime.now());
        clearStaleBlockers(snapshot);
        if (hasRunnableSubTask(snapshot.getSubTasks())) {
            snapshot.setStatus(TaskStatus.WAITING_SUPPORT.name());
            return;
        }
        if (TaskStatus.COMPLETED.name().equals(snapshot.getStatus()) || TaskStatus.FAILED.name().equals(snapshot.getStatus())) {
            snapshot.setStatus(TaskStatus.BLOCKED.name());
            return;
        }
        if (TaskStatus.BLOCKED.name().equals(snapshot.getStatus())) {
            snapshot.setStatus(TaskStatus.RUNNING.name());
        }
    }

    private boolean hasRunnableSubTask(List<Map<String, Object>> subTasks) {
        return !hasNoRunnableSubTask(subTasks);
    }

    private void clearStaleBlockers(OrchestrationTaskSnapshot snapshot) {
        if (snapshot == null) {
            return;
        }
        List<String> blockers = snapshot.getBlockers();
        if (blockers == null || blockers.isEmpty()) {
            return;
        }
        snapshot.setBlockers(blockers.stream()
                .filter(Objects::nonNull)
                .filter(text -> {
                    String normalized = text.trim().toLowerCase();
                    return !normalized.contains("子任务")
                            && !normalized.contains("阻塞")
                            && !normalized.contains("协助")
                            && !normalized.contains("support");
                })
                .toList());
    }

    private void applyManualAudit(OrchestrationTaskSnapshot snapshot,
                                  TaskEventType eventType,
                                  String operator,
                                  String reason,
                                  String defaultSummary) {
        if (snapshot == null) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        snapshot.setLastControlAction(eventType.name());
        snapshot.setLastControlBy(operator);
        snapshot.setLastControlReason(reason);
        snapshot.setLastControlAt(now);
        List<Map<String, Object>> events = snapshot.getEvents() == null ? new java.util.ArrayList<>() : new java.util.ArrayList<>(snapshot.getEvents());
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", eventType.name());
        payload.put("occurredAt", now);
        payload.put("summary", defaultSummary);
        payload.put("metadata", Map.of(
                "operator", operator == null ? "" : operator,
                "reason", reason == null ? "" : reason
        ));
        events.add(payload);
        snapshot.setEvents(events);
    }

    private void applySubTaskAudit(OrchestrationTaskSnapshot snapshot,
                                   TaskEventType eventType,
                                   String subTaskId,
                                   String operator,
                                   String reason,
                                   Map<String, Object> subTask,
                                   String defaultSummary) {
        applyManualAudit(snapshot, eventType, operator, reason, defaultSummary);
        List<Map<String, Object>> events = snapshot.getEvents() == null ? new java.util.ArrayList<>() : new java.util.ArrayList<>(snapshot.getEvents());
        if (events.isEmpty()) {
            return;
        }
        Map<String, Object> last = events.get(events.size() - 1);
        Map<String, Object> metadata = new LinkedHashMap<>();
        if (last.get("metadata") instanceof Map<?, ?> existingMetadata) {
            existingMetadata.forEach((key, value) -> {
                if (key != null) {
                    metadata.put(key.toString(), value);
                }
            });
        }
        metadata.put("subTaskId", subTaskId == null ? "" : subTaskId);
        if (subTask != null) {
            metadata.put("targetAgent", subTask.getOrDefault("targetAgent", ""));
            metadata.put("subTaskStatus", subTask.getOrDefault("status", ""));
        }
        last.put("metadata", metadata);
    }

    private LocalDateTime latestEventTime(OrchestrationTaskSnapshot snapshot, TaskEventType eventType) {
        if (snapshot == null || snapshot.getEvents() == null || eventType == null) {
            return null;
        }
        return snapshot.getEvents().stream()
                .filter(Objects::nonNull)
                .filter(event -> eventType.name().equals(event.get("type")))
                .map(event -> event.get("occurredAt"))
                .filter(LocalDateTime.class::isInstance)
                .map(LocalDateTime.class::cast)
                .max(LocalDateTime::compareTo)
                .orElse(null);
    }

    private List<Map<String, Object>> toSubTaskPayload(OrchestrationTrace trace) {
        if (trace.subTasks() == null) {
            return List.of();
        }
        return trace.subTasks().stream().map(item -> {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("subTaskId", item.subTaskId());
            payload.put("goal", item.goal());
            payload.put("targetAgent", item.targetAgent());
            payload.put("priority", item.priority());
            payload.put("status", item.status() == null ? null : item.status().name());
            payload.put("dependencies", item.dependencies() == null ? List.of() : item.dependencies());
            return payload;
        }).toList();
    }

    private List<Map<String, Object>> toEventPayload(OrchestrationTrace trace) {
        if (trace.events() == null) {
            return List.of();
        }
        return trace.events().stream().map(item -> {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("type", item.type() == null ? null : item.type().name());
            payload.put("occurredAt", item.occurredAt());
            payload.put("summary", item.summary());
            payload.put("metadata", item.metadata() == null ? Map.of() : item.metadata());
            return payload;
        }).toList();
    }

    private List<Map<String, Object>> mergeEventPayload(OrchestrationTrace trace, OrchestrationTaskSnapshot existingSnapshot) {
        List<Map<String, Object>> merged = new java.util.ArrayList<>(toEventPayload(trace));
        if (existingSnapshot == null || existingSnapshot.getEvents() == null) {
            return merged;
        }
        existingSnapshot.getEvents().stream()
                .filter(this::isManualAuditEvent)
                .forEach(merged::add);
        return merged;
    }

    private boolean isManualAuditEvent(Map<String, Object> payload) {
        if (payload == null || !(payload.get("type") instanceof String typeText)) {
            return false;
        }
        return typeText.startsWith("MANUAL_");
    }

    private Map<String, Object> latestContractSummary(List<Map<String, Object>> events) {
        if (events == null || events.isEmpty()) {
            return Map.of();
        }
        for (int index = events.size() - 1; index >= 0; index--) {
            Map<String, Object> event = events.get(index);
            Map<String, Object> metadata = metadataMap(event);
            if (!metadata.containsKey("contractSchema")
                    && !metadata.containsKey("contractVersion")
                    && !metadata.containsKey("contractCompatibility")
                    && !metadata.containsKey("contractValidationErrors")) {
                continue;
            }
            Map<String, Object> compatibility = nestedMap(metadata.get("contractCompatibility"));
            List<String> validationErrors = stringList(metadata.get("contractValidationErrors"));
            LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
            payload.put("schema", stringValue(metadata.get("contractSchema")));
            payload.put("version", stringValue(metadata.get("contractVersion")));
            payload.put("compatibility", compatibilityLabel(compatibility));
            payload.put("validated", validatedFlag(compatibility, validationErrors));
            payload.put("validationErrorCount", validationErrors.size());
            payload.put("validationErrors", validationErrors);
            payload.put("failureCategory", stringValue(metadata.get("failureCategory")));
            payload.put("suggestedHandoffAgent", stringValue(metadata.get("suggestedHandoffAgent")));
            payload.put("missingCapabilities", stringList(metadata.get("missingCapabilities")));
            return payload;
        }
        return Map.of();
    }

    private Map<String, Object> toDecisionPayload(SupervisorDecision decision) {
        if (decision == null) {
            return null;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("taskId", decision.taskId());
        payload.put("subTaskId", decision.subTaskId());
        payload.put("targetAgent", decision.targetAgent());
        payload.put("summary", decision.summary());
        payload.put("score", decision.score());
        return payload;
    }

    private Map<String, Object> metadataMap(Map<String, Object> event) {
        if (event == null || !(event.get("metadata") instanceof Map<?, ?> metadata)) {
            return Map.of();
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        metadata.forEach((key, value) -> {
            if (key != null) {
                payload.put(key.toString(), value);
            }
        });
        return payload;
    }

    private Map<String, Object> nestedMap(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return Map.of();
        }
        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        map.forEach((key, item) -> {
            if (key != null) {
                payload.put(key.toString(), item);
            }
        });
        return payload;
    }

    private String compatibilityLabel(Map<String, Object> compatibility) {
        if (compatibility == null || compatibility.isEmpty()) {
            return null;
        }
        boolean schemaCompatible = booleanValue(compatibility.get("schemaCompatible")) == Boolean.TRUE;
        boolean versionCompatible = booleanValue(compatibility.get("versionCompatible")) == Boolean.TRUE;
        if (schemaCompatible && versionCompatible) {
            return "compatible";
        }
        if (schemaCompatible) {
            return "schema_only";
        }
        if (versionCompatible) {
            return "version_only";
        }
        return "incompatible";
    }

    private Boolean validatedFlag(Map<String, Object> compatibility, List<String> validationErrors) {
        Boolean compatibilityValidated = booleanValue(compatibility == null ? null : compatibility.get("validated"));
        if (compatibilityValidated != null) {
            return compatibilityValidated && (validationErrors == null || validationErrors.isEmpty());
        }
        return validationErrors == null || validationErrors.isEmpty();
    }

    private Boolean booleanValue(Object value) {
        if (value instanceof Boolean flag) {
            return flag;
        }
        if (value instanceof String text) {
            if ("true".equalsIgnoreCase(text)) {
                return Boolean.TRUE;
            }
            if ("false".equalsIgnoreCase(text)) {
                return Boolean.FALSE;
            }
        }
        return null;
    }

    private Integer intValue(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null) {
            return null;
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private String stringValue(Object value) {
        return value == null ? null : value.toString();
    }

    private List<String> stringList(Object value) {
        if (!(value instanceof List<?> items) || items.isEmpty()) {
            return List.of();
        }
        return items.stream()
                .filter(Objects::nonNull)
                .map(Object::toString)
                .filter(text -> !text.isBlank())
                .toList();
    }

    private List<Map<String, Object>> topEntries(Map<String, Long> counts, int limit) {
        return counts.entrySet().stream()
                .sorted((left, right) -> Long.compare(right.getValue(), left.getValue()))
                .limit(Math.max(limit, 0))
                .map(entry -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("key", entry.getKey());
                    item.put("count", entry.getValue());
                    return item;
                })
                .toList();
    }

    private List<Map<String, Object>> toSignalTrend(Map<LocalDateTime, Map<String, Long>> bucketCounts) {
        return bucketCounts.entrySet().stream()
                .map(entry -> {
                    Map<String, Object> payload = new LinkedHashMap<>();
                    payload.put("bucketStart", entry.getKey());
                    payload.put("bucketEnd", entry.getKey().plusHours(1));
                    payload.put("totalCount", entry.getValue().values().stream().mapToLong(Long::longValue).sum());
                    payload.put("topSignals", topEntries(entry.getValue(), 3));
                    payload.put("signalCounts", entry.getValue());
                    return payload;
                })
                .toList();
    }

    private List<Map<String, Object>> toExecutionTrend(Map<LocalDateTime, Map<String, Long>> bucketCounts) {
        return bucketCounts.entrySet().stream()
                .map(entry -> {
                    Map<String, Object> payload = new LinkedHashMap<>();
                    long completed = entry.getValue().getOrDefault("completed", 0L);
                    long failed = entry.getValue().getOrDefault("failed", 0L);
                    long blocked = entry.getValue().getOrDefault("blocked", 0L);
                    long rerouted = entry.getValue().getOrDefault("rerouted", 0L);
                    long total = completed + failed + blocked + rerouted;
                    payload.put("bucketStart", entry.getKey());
                    payload.put("bucketEnd", entry.getKey().plusHours(1));
                    payload.put("completedCount", completed);
                    payload.put("failedCount", failed);
                    payload.put("blockedCount", blocked);
                    payload.put("reroutedCount", rerouted);
                    payload.put("executionCount", total);
                    payload.put("successRate", total <= 0 ? 0D : roundRatio((double) completed / total));
                    return payload;
                })
                .toList();
    }

    private boolean isExecutionHealthEvent(String type) {
        return "COMPLETED".equals(type)
                || "FAILED".equals(type)
                || "BLOCKED".equals(type)
                || "SUBTASK_HANDOFF".equals(type);
    }

    private static double roundRatio(double value) {
        return Math.round(value * 1000D) / 1000D;
    }

    private AgentPerformanceEvent toAgentPerformanceEvent(Map<String, Object> payload) {
        if (payload == null || !(payload.get("type") instanceof String typeText) || !(payload.get("metadata") instanceof Map<?, ?> metadata)) {
            return null;
        }
        String agent = trackedAgentName(metadata);
        if (agent == null || agent.isBlank()) {
            return null;
        }
        return new AgentPerformanceEvent(agent, typeText);
    }

    private String trackedAgentName(Map<?, ?> metadata) {
        if (metadata == null) {
            return null;
        }
        Object resolvedAgentName = metadata.get("resolvedAgentName");
        if (resolvedAgentName != null && !resolvedAgentName.toString().isBlank()) {
            return resolvedAgentName.toString();
        }
        Object targetAgent = metadata.get("targetAgent");
        if (targetAgent != null && !targetAgent.toString().isBlank()) {
            return targetAgent.toString();
        }
        return null;
    }

    private Map<String, Integer> toPerformanceStats(AgentPerformanceEvent event) {
        Map<String, Integer> stats = new LinkedHashMap<>();
        stats.put("completed", "COMPLETED".equals(event.type()) ? 1 : 0);
        stats.put("failed", "FAILED".equals(event.type()) ? 1 : 0);
        stats.put("blocked", "BLOCKED".equals(event.type()) ? 1 : 0);
        stats.put("rerouted", "SUBTASK_HANDOFF".equals(event.type()) ? 1 : 0);
        stats.put("score", switch (event.type()) {
            case "COMPLETED" -> 18;
            case "FAILED" -> -18;
            case "BLOCKED" -> -10;
            case "SUBTASK_HANDOFF" -> -7;
            default -> 0;
        });
        return stats;
    }

    private Map<String, Integer> mergePerformanceStats(Map<String, Integer> left, Map<String, Integer> right) {
        Map<String, Integer> merged = new LinkedHashMap<>();
        merged.put("completed", left.getOrDefault("completed", 0) + right.getOrDefault("completed", 0));
        merged.put("failed", left.getOrDefault("failed", 0) + right.getOrDefault("failed", 0));
        merged.put("blocked", left.getOrDefault("blocked", 0) + right.getOrDefault("blocked", 0));
        merged.put("rerouted", left.getOrDefault("rerouted", 0) + right.getOrDefault("rerouted", 0));
        merged.put("score", left.getOrDefault("score", 0) + right.getOrDefault("score", 0));
        return merged;
    }

    private record AgentPerformanceEvent(String agent, String type) {
    }

    private record AgentIssueAccumulator(Map<String, Long> failureCategoryCounts,
                                         Map<String, Long> missingCapabilityCounts,
                                         Map<LocalDateTime, Map<String, Long>> failureBuckets,
                                         Map<LocalDateTime, Map<String, Long>> capabilityBuckets) {

        private AgentIssueAccumulator(Map<LocalDateTime, Map<String, Long>> failureBuckets,
                                      Map<LocalDateTime, Map<String, Long>> capabilityBuckets) {
            this(new LinkedHashMap<>(), new LinkedHashMap<>(), failureBuckets, capabilityBuckets);
        }

        private long totalIssueCount() {
            return failureCategoryCounts.values().stream().mapToLong(Long::longValue).sum()
                    + missingCapabilityCounts.values().stream().mapToLong(Long::longValue).sum();
        }
    }

    private static final class AgentExecutionAccumulator {
        private long completedCount;
        private long failedCount;
        private long blockedCount;
        private long reroutedCount;
        private final Map<LocalDateTime, Map<String, Long>> executionBuckets;
        private final List<ExecutionSample> recentExecutions = new java.util.ArrayList<>();

        private AgentExecutionAccumulator(Map<LocalDateTime, Map<String, Long>> executionBuckets) {
            this.executionBuckets = executionBuckets;
        }

        private void record(String type, LocalDateTime bucketStart, LocalDateTime occurredAt) {
            String key = switch (type) {
                case "COMPLETED" -> {
                    completedCount++;
                    yield "completed";
                }
                case "FAILED" -> {
                    failedCount++;
                    yield "failed";
                }
                case "BLOCKED" -> {
                    blockedCount++;
                    yield "blocked";
                }
                case "SUBTASK_HANDOFF" -> {
                    reroutedCount++;
                    yield "rerouted";
                }
                default -> null;
            };
            if (key != null) {
                executionBuckets.computeIfAbsent(bucketStart, ignored -> new LinkedHashMap<>()).merge(key, 1L, Long::sum);
                if (occurredAt != null) {
                    recentExecutions.add(new ExecutionSample(occurredAt, key));
                }
            }
        }

        private long executionCount() {
            return completedCount + failedCount + blockedCount + reroutedCount;
        }

        private double successRate() {
            long total = executionCount();
            return total <= 0 ? 0D : roundRatio((double) completedCount / total);
        }

        private double stabilityScore() {
            long total = executionCount();
            if (total <= 0) {
                return 0D;
            }
            double score = (completedCount * 1.0 - failedCount * 1.2 - blockedCount * 0.6 - reroutedCount * 0.4) / total;
            return roundRatio(score);
        }

        private long completedCount() {
            return completedCount;
        }

        private long failedCount() {
            return failedCount;
        }

        private long blockedCount() {
            return blockedCount;
        }

        private long reroutedCount() {
            return reroutedCount;
        }

        private Map<LocalDateTime, Map<String, Long>> executionBuckets() {
            return executionBuckets;
        }

        private long recentConsecutiveFailureCount() {
            if (recentExecutions.isEmpty()) {
                return 0L;
            }
            return orderedExecutions().stream()
                    .sorted((left, right) -> right.occurredAt().compareTo(left.occurredAt()))
                    .takeWhile(sample -> !"completed".equals(sample.outcome()))
                    .count();
        }

        private LocalDateTime lastExecutionAt() {
            return orderedExecutions().stream()
                    .map(ExecutionSample::occurredAt)
                    .max(LocalDateTime::compareTo)
                    .orElse(null);
        }

        private LocalDateTime lastSuccessfulAt() {
            return orderedExecutions().stream()
                    .filter(sample -> "completed".equals(sample.outcome()))
                    .map(ExecutionSample::occurredAt)
                    .max(LocalDateTime::compareTo)
                    .orElse(null);
        }

        private List<ExecutionSample> orderedExecutions() {
            return recentExecutions.stream()
                    .sorted(Comparator.comparing(ExecutionSample::occurredAt))
                    .toList();
        }
    }

    private record ExecutionSample(LocalDateTime occurredAt, String outcome) {
    }
}
