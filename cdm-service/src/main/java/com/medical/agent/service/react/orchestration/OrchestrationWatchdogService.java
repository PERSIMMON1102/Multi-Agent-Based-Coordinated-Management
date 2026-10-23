package com.medical.agent.service.react.orchestration;

import com.medical.agent.common.exception.BusinessException;
import com.medical.agent.model.mongo.OrchestrationTaskSnapshot;
import com.medical.agent.service.ChatService;
import com.medical.agent.service.PlatformMetricsService;
import com.medical.agent.service.react.ReActAgentExecutor;
import com.medical.agent.service.react.agent.ChatSubAgent;
import com.medical.agent.service.react.agent.SubAgentRegistry;
import com.medical.agent.service.react.agent.SubAgentResult;
import com.medical.agent.service.react.model.AgentState;
import com.medical.agent.service.react.model.AgentStep;
import com.medical.agent.service.react.model.ReActResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class OrchestrationWatchdogService {

    private static final Logger log = LoggerFactory.getLogger(OrchestrationWatchdogService.class);

    private final OrchestrationPersistenceService orchestrationPersistenceService;
    private final TaskScheduler taskScheduler;
    private final ReActAgentExecutor reactAgentExecutor;
    private final SubAgentRegistry subAgentRegistry;
    private final SubTaskHandoffRouter subTaskHandoffRouter;
    private final ChatService chatService;
    private final PlatformMetricsService platformMetricsService;

    @Value("${orchestration.watchdog.enabled:true}")
    private boolean watchdogEnabled;

    OrchestrationWatchdogService(OrchestrationPersistenceService orchestrationPersistenceService,
                                 TaskScheduler taskScheduler,
                                 ReActAgentExecutor reactAgentExecutor,
                                 SubAgentRegistry subAgentRegistry,
                                 SubTaskHandoffRouter subTaskHandoffRouter) {
        this(orchestrationPersistenceService, taskScheduler, reactAgentExecutor, subAgentRegistry, subTaskHandoffRouter, null, null);
    }

    @Autowired
    public OrchestrationWatchdogService(OrchestrationPersistenceService orchestrationPersistenceService,
                                        TaskScheduler taskScheduler,
                                        ReActAgentExecutor reactAgentExecutor,
                                        SubAgentRegistry subAgentRegistry,
                                        SubTaskHandoffRouter subTaskHandoffRouter,
                                        @Lazy ChatService chatService,
                                        PlatformMetricsService platformMetricsService) {
        this.orchestrationPersistenceService = orchestrationPersistenceService;
        this.taskScheduler = taskScheduler;
        this.reactAgentExecutor = reactAgentExecutor;
        this.subAgentRegistry = subAgentRegistry;
        this.subTaskHandoffRouter = subTaskHandoffRouter;
        this.chatService = chatService;
        this.platformMetricsService = platformMetricsService;
    }

    public OrchestrationWatchdogService(OrchestrationPersistenceService orchestrationPersistenceService,
                                        TaskScheduler taskScheduler,
                                        ReActAgentExecutor reactAgentExecutor,
                                        SubAgentRegistry subAgentRegistry,
                                        SubTaskHandoffRouter subTaskHandoffRouter,
                                        ChatService chatService) {
        this(orchestrationPersistenceService,
                taskScheduler,
                reactAgentExecutor,
                subAgentRegistry,
                subTaskHandoffRouter,
                chatService,
                null);
    }

    public List<Map<String, Object>> listActiveTasks() {
        return orchestrationPersistenceService.listActiveSnapshots().stream()
                .map(this::toPayload)
                .toList();
    }

    public List<Map<String, Object>> listTaskCards(String controlStatus,
                                                   String reviewTrigger,
                                                   String taskType,
                                                   Boolean circuitBreakerOnly,
                                                   String contractCompatibility,
                                                   Boolean contractValidated,
                                                   Boolean contractErrorsOnly) {
        return listActiveTasks().stream()
                .filter(task -> matchesFilter(stringValue(task.get("controlStatus")), controlStatus))
                .filter(task -> matchesFilter(stringValue(task.get("taskType")), taskType))
                .filter(task -> matchesCircuitFilter(task, circuitBreakerOnly))
                .filter(task -> matchesReviewTrigger(task, reviewTrigger))
                .filter(task -> matchesContractCompatibility(task, contractCompatibility))
                .filter(task -> matchesContractValidated(task, contractValidated))
                .filter(task -> matchesContractErrors(task, contractErrorsOnly))
                .map(this::toTaskCard)
                .toList();
    }

    public Map<String, Object> getOverview() {
        List<Map<String, Object>> activeTasks = listActiveTasks();
        Map<String, Long> recent24hControlEvents = orchestrationPersistenceService.summarizeRecentControlEvents(24);
        List<Map<String, Object>> recent24hHourlyTrend = orchestrationPersistenceService.summarizeRecentControlEventBuckets(24);
        Map<String, Object> recent24hIssueSignals = orchestrationPersistenceService.summarizeRecentIssueSignals(24);
        List<Map<String, Object>> rawRecent24hAgentExecutionHealth = orchestrationPersistenceService.summarizeRecentAgentExecutionHealth(24);
        Map<String, Map<String, Object>> executionHealthStatsByAgent = indexAgentHealthStats(rawRecent24hAgentExecutionHealth);
        List<Map<String, Object>> recent24hAgentIssueSignals = enrichAgentSummaries(
                orchestrationPersistenceService.summarizeRecentAgentIssueSignals(24),
                executionHealthStatsByAgent
        );
        List<Map<String, Object>> recent24hAgentExecutionHealth = enrichAgentSummaries(
                rawRecent24hAgentExecutionHealth,
                executionHealthStatsByAgent
        );
        Map<String, Long> controlStatusCounts = new LinkedHashMap<>();
        Map<String, Long> watchdogDecisionCounts = new LinkedHashMap<>();
        Map<String, Long> reviewReasonCounts = new LinkedHashMap<>();
        Map<String, Long> reviewTriggerCounts = new LinkedHashMap<>();
        Map<String, Long> failureCategoryCounts = new LinkedHashMap<>();
        Map<String, Long> contractCompatibilityCounts = new LinkedHashMap<>();
        Map<String, Long> contractValidationErrorCounts = new LinkedHashMap<>();
        long awaitingReviewTasks = 0;
        long autoAdvanceTasks = 0;
        long manualReviewTasks = 0;
        long circuitBreakerTasks = 0;
        long validatedContractTasks = 0;
        long invalidContractTasks = 0;

        for (Map<String, Object> task : activeTasks) {
            String controlStatus = stringValue(task.get("controlStatus"));
            incrementCount(controlStatusCounts, controlStatus);
            String watchdogDecision = stringValue(task.get("watchdogDecision"));
            incrementCount(watchdogDecisionCounts, watchdogDecision);
            if (OrchestrationControlStatus.AWAITING_REVIEW.name().equals(controlStatus)) {
                awaitingReviewTasks++;
            }
            if (Boolean.TRUE.equals(task.get("watchdogAutoAdvanceAllowed"))) {
                autoAdvanceTasks++;
            }
            if (Boolean.TRUE.equals(task.get("manualReviewRecommended"))) {
                manualReviewTasks++;
            }
            if (Boolean.TRUE.equals(task.get("circuitBreakerOpen"))) {
                circuitBreakerTasks++;
            }
            if (Boolean.TRUE.equals(task.get("latestContractValidated"))) {
                validatedContractTasks++;
            } else if (task.get("latestContractValidated") instanceof Boolean) {
                invalidContractTasks++;
            }
            incrementCount(contractCompatibilityCounts, stringValue(task.get("latestContractCompatibility")));
            for (String validationError : stringList(task.get("latestContractValidationErrors"))) {
                incrementCount(contractValidationErrorCounts, validationError);
            }
            for (String reason : stringList(task.get("manualReviewReasons"))) {
                incrementCount(reviewReasonCounts, reason);
            }
            for (Object item : listValue(task.get("subTasks"))) {
                if (item instanceof Map<?, ?> subTask) {
                    String failureCategory = stringValue(subTask.get("recentFailureCategory"));
                    if (failureCategory != null && !failureCategory.isBlank()) {
                        incrementCount(failureCategoryCounts, failureCategory);
                    }
                    incrementCount(reviewTriggerCounts, stringValue(subTask.get("recommendedAgentReviewTrigger")));
                }
            }
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("activeTaskCount", activeTasks.size());
        payload.put("awaitingReviewTaskCount", awaitingReviewTasks);
        payload.put("autoAdvanceTaskCount", autoAdvanceTasks);
        payload.put("manualReviewRecommendedTaskCount", manualReviewTasks);
        payload.put("circuitBreakerTaskCount", circuitBreakerTasks);
        payload.put("validatedContractTaskCount", validatedContractTasks);
        payload.put("invalidContractTaskCount", invalidContractTasks);
        payload.put("controlStatusCounts", controlStatusCounts);
        payload.put("watchdogDecisionCounts", watchdogDecisionCounts);
        payload.put("contractCompatibilityCounts", contractCompatibilityCounts);
        payload.put("recent24hAwaitingReviewCount", recent24hControlEvents.getOrDefault(TaskEventType.SYSTEM_AWAITING_REVIEW.name(), 0L));
        payload.put("recent24hReviewAckedCount", recent24hControlEvents.getOrDefault(TaskEventType.MANUAL_REVIEW_ACKED.name(), 0L));
        payload.put("recent24hControlEvents", recent24hControlEvents);
        payload.put("recent24hHourlyTrend", recent24hHourlyTrend);
        payload.put("topManualReviewReasons", topEntries(reviewReasonCounts, 5));
        payload.put("topReviewTriggers", topEntries(reviewTriggerCounts, 5));
        payload.put("topFailureCategories", topEntries(failureCategoryCounts, 5));
        payload.put("topContractValidationErrors", topEntries(contractValidationErrorCounts, 5));
        payload.put("recent24hTopFailureCategories", recent24hIssueSignals.getOrDefault("topFailureCategories", List.of()));
        payload.put("recent24hTopMissingCapabilities", recent24hIssueSignals.getOrDefault("topMissingCapabilities", List.of()));
        payload.put("recent24hFailureCategoryTrend", recent24hIssueSignals.getOrDefault("recent24hFailureCategoryTrend", List.of()));
        payload.put("recent24hMissingCapabilityTrend", recent24hIssueSignals.getOrDefault("recent24hMissingCapabilityTrend", List.of()));
        payload.put("recent24hAgentIssueSignals", recent24hAgentIssueSignals);
        payload.put("recent24hAgentExecutionHealth", recent24hAgentExecutionHealth);
        payload.put("topUnhealthyResolvedAgents", topUnhealthyResolvedAgents(recent24hAgentExecutionHealth, 5));
        payload.put("topIssueHeavyResolvedAgents", topIssueHeavyResolvedAgents(recent24hAgentIssueSignals, 5));
        payload.put("dashboardHighlights", buildDashboardHighlights(activeTasks, reviewTriggerCounts, recent24hAgentExecutionHealth));
        return payload;
    }

    public Map<String, Object> getFilterOptions() {
        List<Map<String, Object>> activeTasks = listActiveTasks();
        Map<String, Long> controlStatusCounts = new LinkedHashMap<>();
        Map<String, Long> taskTypeCounts = new LinkedHashMap<>();
        Map<String, Long> reviewTriggerCounts = new LinkedHashMap<>();
        Map<String, Long> contractCompatibilityCounts = new LinkedHashMap<>();
        Map<String, Long> contractValidationStateCounts = new LinkedHashMap<>();
        long circuitBreakerTaskCount = 0L;

        for (Map<String, Object> task : activeTasks) {
            incrementCount(controlStatusCounts, stringValue(task.get("controlStatus")));
            incrementCount(taskTypeCounts, stringValue(task.get("taskType")));
            incrementCount(contractCompatibilityCounts, stringValue(task.get("latestContractCompatibility")));
            incrementCount(contractValidationStateCounts, contractValidationState(task));
            if (Boolean.TRUE.equals(task.get("circuitBreakerOpen"))) {
                circuitBreakerTaskCount++;
            }
            for (Object item : listValue(task.get("subTasks"))) {
                if (item instanceof Map<?, ?> subTask) {
                    incrementCount(reviewTriggerCounts, stringValue(subTask.get("recommendedAgentReviewTrigger")));
                }
            }
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("controlStatuses", topEntries(controlStatusCounts, controlStatusCounts.size()));
        payload.put("taskTypes", topEntries(taskTypeCounts, taskTypeCounts.size()));
        payload.put("reviewTriggers", topEntries(reviewTriggerCounts, reviewTriggerCounts.size()));
        payload.put("contractCompatibilities", topEntries(contractCompatibilityCounts, contractCompatibilityCounts.size()));
        payload.put("contractValidationStates", topEntries(contractValidationStateCounts, contractValidationStateCounts.size()));
        payload.put("circuitBreakerTaskCount", circuitBreakerTaskCount);
        return payload;
    }

    public Optional<Map<String, Object>> getTaskDetail(String taskId) {
        return orchestrationPersistenceService.findLatestByTaskIdPublic(taskId).map(this::toDetailPayload);
    }

    public Optional<Map<String, Object>> getLatestSessionTask(String sessionId) {
        return orchestrationPersistenceService.findLatestBySessionIdPublic(sessionId).map(this::toDetailPayload);
    }

    public Optional<Map<String, Object>> advanceTaskNow(String taskId) {
        OrchestrationTaskSnapshot snapshot = findTaskSnapshot(taskId);
        if (snapshot == null) {
            return Optional.empty();
        }
        if (!isRunnableNow(snapshot)) {
            return Optional.of(toPayload(snapshot));
        }
        advanceSnapshot(snapshot);
        return orchestrationPersistenceService.findLatestByTaskIdPublic(taskId)
                .map(this::toPayload)
                .or(() -> Optional.of(toPayload(snapshot)));
    }

    @Scheduled(fixedDelayString = "${orchestration.watchdog.fixed-delay-ms:60000}")
    public void tick() {
        if (!watchdogEnabled) {
            return;
        }
        for (OrchestrationTaskSnapshot snapshot : orchestrationPersistenceService.listRunnableSnapshots()) {
            advanceSnapshot(snapshot);
        }
    }

    public Map<String, Object> triggerOnce() {
        if (!watchdogEnabled) {
            return Map.of(
                    "enabled", false,
                    "attempted", 0,
                    "deferredForManualReview", 0,
                    "activeTasks", List.of(),
                    "manualReviewRecommendedTasks", 0
            );
        }
        int attempted = 0;
        int deferredForManualReview = 0;
        List<OrchestrationTaskSnapshot> runnableSnapshots = orchestrationPersistenceService.listRunnableSnapshots();
        for (OrchestrationTaskSnapshot snapshot : runnableSnapshots) {
            if (requiresManualReview(snapshot)) {
                orchestrationPersistenceService.deferForManualReview(snapshot, buildManualReviewSummary(enrichSubTasks(snapshot)));
                deferredForManualReview++;
                continue;
            }
            attempted++;
            advanceSnapshot(snapshot);
        }
        List<Map<String, Object>> activeTasks = listActiveTasks();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("attempted", attempted);
        payload.put("deferredForManualReview", deferredForManualReview);
        payload.put("activeTasks", activeTasks);
        payload.put("manualReviewRecommendedTasks", countManualReviewTasks(activeTasks));
        return payload;
    }

    public Optional<Map<String, Object>> pauseTask(String taskId, String operator, String reason) {
        return orchestrationPersistenceService.pauseTask(taskId, operator, reason).map(this::toPayload);
    }

    public Optional<Map<String, Object>> resumeTask(String taskId, String operator, String reason) {
        return orchestrationPersistenceService.resumeTask(taskId, operator, reason).map(this::toPayload);
    }

    public Optional<Map<String, Object>> acknowledgeReview(String taskId, String operator, String reason) {
        requireReason(reason, "确认人工复核必须填写原因");
        return orchestrationPersistenceService.acknowledgeManualReview(taskId, operator, reason).map(this::toPayload);
    }

    public Optional<Map<String, Object>> cancelTask(String taskId, String operator, String reason) {
        requireReason(reason, "取消自治任务必须填写原因");
        return orchestrationPersistenceService.cancelTask(taskId, operator, reason).map(this::toPayload);
    }

    public Optional<Map<String, Object>> retryTask(String taskId, String operator, String reason) {
        requireReason(reason, "强制重跑自治任务必须填写原因");
        return orchestrationPersistenceService.retryTask(taskId, operator, reason).map(snapshot -> {
            postRetryNotice(snapshot, operator, reason);
            advanceSnapshot(snapshot);
            return orchestrationPersistenceService.findLatestByTaskIdPublic(snapshot.getTaskId())
                    .map(this::toPayload)
                    .orElseGet(() -> toPayload(snapshot));
        });
    }

    public Optional<Map<String, Object>> skipSubTask(String taskId, String subTaskId, String operator, String reason) {
        requireReason(reason, "跳过子任务必须填写原因");
        String replacementAgent = selectReplacementAgent(taskId, subTaskId);
        return orchestrationPersistenceService.skipSubTask(taskId, subTaskId, replacementAgent, operator, reason)
                .map(this::toPayload);
    }

    public Optional<Map<String, Object>> resetSubTask(String taskId, String subTaskId, String operator, String reason) {
        return orchestrationPersistenceService.resetSubTask(taskId, subTaskId, operator, reason).map(this::toPayload);
    }

    public Optional<Map<String, Object>> handoffSubTask(String taskId,
                                                        String subTaskId,
                                                        String targetAgent,
                                                        String operator,
                                                        String reason) {
        requireReason(reason, "转派子任务必须填写原因");
        validateTargetAgent(targetAgent);
        return orchestrationPersistenceService.handoffSubTask(taskId, subTaskId, targetAgent, operator, reason)
                .map(this::toPayload);
    }

    public Optional<Map<String, Object>> handoffSubTaskByCapability(String taskId,
                                                                    String subTaskId,
                                                                    String capability,
                                                                    String operator,
                                                                    String reason) {
        requireReason(reason, "按能力补位必须填写原因");
        if (capability == null || capability.isBlank()) {
            throw new BusinessException(400, "capability 不能为空");
        }
        OrchestrationTaskSnapshot snapshot = findTaskSnapshot(taskId);
        if (snapshot == null) {
            return Optional.empty();
        }
        Map<String, Object> subTask = findSubTask(snapshot, subTaskId);
        if (subTask == null) {
            return Optional.empty();
        }
        String currentAgent = stringValue(subTask.get("targetAgent"));
        String targetAgent = subTaskHandoffRouter.selectAgentForCapabilities(List.of(capability.trim()), currentAgent);
        validateTargetAgent(targetAgent);
        return orchestrationPersistenceService.handoffSubTask(taskId, subTaskId, targetAgent, operator, reason)
                .map(this::toPayload);
    }

    void advanceSnapshot(OrchestrationTaskSnapshot snapshot) {
        long startedAt = System.nanoTime();
        String outcome = "skipped";
        try {
            if (snapshot != null && snapshot.getCircuitOpenUntil() != null && snapshot.getCircuitOpenUntil().isAfter(java.time.LocalDateTime.now())) {
                outcome = "circuit_open";
                return;
            }
            if (requiresManualReview(snapshot)) {
                orchestrationPersistenceService.deferForManualReview(snapshot, buildManualReviewSummary(enrichSubTasks(snapshot)));
                outcome = "manual_review";
                return;
            }
            AgentState state = orchestrationPersistenceService.rebuildAgentState(snapshot);
            AutonomousTask task = taskScheduler.restoreFromSnapshot(snapshot);
            if (state == null || task == null) {
                outcome = "missing_state";
                return;
            }
            List<SubTaskExecutionResult> executionResults = taskScheduler.dispatchReadyBatch(task, state);
            if (executionResults.isEmpty()) {
                outcome = "no_ready_subtask";
                return;
            }
            executionResults.forEach(executionResult -> {
                mergeSubTaskExecution(state, executionResult);
                mergeSubTaskState(state, executionResult.stateSnapshot());
            });
            ReActResult result = reactAgentExecutor.execute(state);
            taskScheduler.reconcile(task, result);
            orchestrationPersistenceService.save(state, taskScheduler.snapshot(task));
            publishRetryFollowUpIfNeeded(snapshot.getTaskId(), result);
            outcome = "advanced";
        } catch (RuntimeException ex) {
            orchestrationPersistenceService.markExecutionFailure(snapshot, ex.getMessage());
            outcome = "failed";
        } finally {
            if (platformMetricsService != null) {
                long elapsedMillis = Math.max(0L, (System.nanoTime() - startedAt) / 1_000_000L);
                platformMetricsService.recordWatchdogAdvance(outcome, elapsedMillis);
            }
        }
    }

    private boolean isRunnableNow(OrchestrationTaskSnapshot snapshot) {
        if (snapshot == null) {
            return false;
        }
        if (OrchestrationControlStatus.CANCELLED.name().equals(snapshot.getControlStatus())
                || OrchestrationControlStatus.PAUSED.name().equals(snapshot.getControlStatus())
                || OrchestrationControlStatus.AWAITING_REVIEW.name().equals(snapshot.getControlStatus())) {
            return false;
        }
        if (snapshot.getCircuitOpenUntil() != null && snapshot.getCircuitOpenUntil().isAfter(java.time.LocalDateTime.now())) {
            return false;
        }
        return TaskStatus.PLANNING.name().equals(snapshot.getStatus())
                || TaskStatus.RUNNING.name().equals(snapshot.getStatus())
                || TaskStatus.WAITING_SUPPORT.name().equals(snapshot.getStatus())
                || TaskStatus.BLOCKED.name().equals(snapshot.getStatus());
    }

    private void postRetryNotice(OrchestrationTaskSnapshot snapshot, String operator, String reason) {
        if (snapshot == null || chatService == null) {
            return;
        }
        try {
            chatService.appendOrchestrationRetryNotice(snapshot, operator, reason);
            orchestrationPersistenceService.markRetryNoticePosted(snapshot.getTaskId(), operator, reason);
        } catch (RuntimeException ex) {
            log.warn("Failed to append retry notice for task {}: {}", snapshot.getTaskId(), ex.getMessage());
        }
    }

    private void publishRetryFollowUpIfNeeded(String taskId, ReActResult result) {
        if (taskId == null || taskId.isBlank() || chatService == null) {
            return;
        }
        OrchestrationTaskSnapshot latestSnapshot = orchestrationPersistenceService.findLatestByTaskIdPublic(taskId).orElse(null);
        if (latestSnapshot == null || !orchestrationPersistenceService.hasPendingRetryResultPublication(latestSnapshot)) {
            return;
        }
        if (!TaskStatus.COMPLETED.name().equals(latestSnapshot.getStatus())
                && !TaskStatus.FAILED.name().equals(latestSnapshot.getStatus())
                && !TaskStatus.BLOCKED.name().equals(latestSnapshot.getStatus())) {
            return;
        }
        try {
            chatService.appendOrchestrationRetryResult(latestSnapshot, result == null ? null : result.reply(), result);
            String summary = TaskStatus.BLOCKED.name().equals(latestSnapshot.getStatus())
                    ? "系统已将后台重跑后仍阻塞的结果说明追加到会话窗口。"
                    : "系统已将后台重跑后的补充分析追加到会话窗口。";
            orchestrationPersistenceService.markRetryResultPosted(taskId, summary);
        } catch (RuntimeException ex) {
            log.warn("Failed to append retry follow-up for task {}: {}", taskId, ex.getMessage());
        }
    }

    private Map<String, Object> toPayload(OrchestrationTaskSnapshot snapshot) {
        List<Map<String, Object>> enrichedSubTasks = enrichSubTasks(snapshot);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sessionId", snapshot.getSessionId());
        payload.put("taskId", snapshot.getTaskId());
        payload.put("goal", snapshot.getGoal());
        payload.put("status", snapshot.getStatus());
        payload.put("controlStatus", snapshot.getControlStatus());
        payload.put("lastControlAction", snapshot.getLastControlAction());
        payload.put("lastControlBy", snapshot.getLastControlBy());
        payload.put("lastControlReason", snapshot.getLastControlReason());
        payload.put("lastControlAt", snapshot.getLastControlAt());
        payload.put("assignedAgent", snapshot.getAssignedAgent());
        payload.put("nextDecision", snapshot.getNextDecision());
        payload.put("failureCount", snapshot.getFailureCount());
        payload.put("watchdogFailureStreak", snapshot.getWatchdogFailureStreak());
        payload.put("circuitBreakerOpenUntil", snapshot.getCircuitOpenUntil());
        payload.put("circuitBreakerOpenReason", snapshot.getCircuitOpenReason());
        payload.put("circuitBreakerOpen", snapshot.getCircuitOpenUntil() != null && snapshot.getCircuitOpenUntil().isAfter(java.time.LocalDateTime.now()));
        payload.put("taskType", snapshot.getTaskType());
        payload.put("latestContractValidated", snapshot.getLatestContractValidated());
        payload.put("latestContractSchema", snapshot.getLatestContractSchema());
        payload.put("latestContractVersion", snapshot.getLatestContractVersion());
        payload.put("latestContractCompatibility", snapshot.getLatestContractCompatibility());
        payload.put("latestContractValidationErrorCount", snapshot.getLatestContractValidationErrorCount());
        payload.put("latestContractValidationErrors", snapshot.getLatestContractValidationErrors() == null ? List.of() : snapshot.getLatestContractValidationErrors());
        payload.put("latestContractFailureCategory", snapshot.getLatestContractFailureCategory());
        payload.put("latestContractSuggestedHandoffAgent", snapshot.getLatestContractSuggestedHandoffAgent());
        payload.put("latestContractMissingCapabilities", snapshot.getLatestContractMissingCapabilities() == null ? List.of() : snapshot.getLatestContractMissingCapabilities());
        payload.put("nextRunAt", snapshot.getNextRunAt());
        payload.put("updatedAt", snapshot.getUpdatedAt());
        payload.put("subTaskCount", snapshot.getSubTasks() == null ? 0 : snapshot.getSubTasks().size());
        payload.put("subTasks", enrichedSubTasks);
        payload.put("manualReviewRecommended", hasManualReviewRecommendation(enrichedSubTasks));
        payload.put("manualReviewSubTaskCount", countManualReviewSubTasks(enrichedSubTasks));
        payload.put("manualReviewSubTaskIds", collectManualReviewSubTaskIds(enrichedSubTasks));
        payload.put("manualReviewReasons", collectManualReviewReasons(enrichedSubTasks));
        payload.put("manualReviewSummary", buildManualReviewSummary(enrichedSubTasks));
        payload.put("watchdogAutoAdvanceAllowed", isWatchdogAutoAdvanceAllowed(snapshot, enrichedSubTasks));
        payload.put("watchdogDecision", resolveWatchdogDecision(snapshot, enrichedSubTasks));
        return payload;
    }

    private Map<String, Object> toDetailPayload(OrchestrationTaskSnapshot snapshot) {
        List<Map<String, Object>> enrichedSubTasks = enrichSubTasks(snapshot);
        List<Map<String, Object>> timeline = buildTimeline(snapshot);
        Map<String, Object> payload = new LinkedHashMap<>(toPayload(snapshot));
        payload.put("ownerRole", snapshot.getOwnerRole());
        payload.put("ownerUserId", snapshot.getOwnerUserId());
        payload.put("patientId", snapshot.getPatientId());
        payload.put("userMessage", snapshot.getUserMessage());
        payload.put("progressSummary", snapshot.getProgressSummary());
        payload.put("nextAction", snapshot.getNextAction());
        payload.put("blockers", snapshot.getBlockers() == null ? List.of() : snapshot.getBlockers());
        payload.put("usedTools", snapshot.getUsedTools() == null ? List.of() : snapshot.getUsedTools());
        payload.put("safetyFlags", snapshot.getSafetyFlags() == null ? List.of() : snapshot.getSafetyFlags());
        payload.put("currentStatusSummary", buildCurrentStatusSummary(snapshot, enrichedSubTasks));
        payload.put("pendingReviewAction", buildPendingReviewAction(snapshot, enrichedSubTasks));
        payload.put("timeline", timeline);
        payload.put("latestControlOutcome", buildLatestControlOutcome(snapshot, timeline, enrichedSubTasks));
        return payload;
    }

    private void mergeSubTaskExecution(AgentState state, SubTaskExecutionResult executionResult) {
        if (state == null || executionResult == null) {
            return;
        }
        state.addStep(new AgentStep(
                state.getSteps().size() + 1,
                executionResult.decision() == null ? "Watchdog 推进子任务。" : executionResult.decision().summary(),
                executionResult.resolvedAgentName() == null || executionResult.resolvedAgentName().isBlank()
                        ? executionResult.decision() == null ? "watchdog_subtask" : executionResult.decision().targetAgent()
                        : executionResult.resolvedAgentName(),
                Map.of(
                        "subTaskId", executionResult.subTask() == null ? "" : executionResult.subTask().getSubTaskId(),
                        "goal", executionResult.subTask() == null ? "" : executionResult.subTask().getGoal(),
                        "targetAgent", executionResult.decision() == null ? "" : executionResult.decision().targetAgent(),
                        "resolvedAgentName", executionResult.resolvedAgentName() == null ? "" : executionResult.resolvedAgentName(),
                        "resolvedAgentProvider", executionResult.resolvedAgentProvider() == null ? "" : executionResult.resolvedAgentProvider()
                ),
                executionResult.summary()
        ));
        SubAgentResult subAgentResult = executionResult.subAgentResult();
        if (subAgentResult == null) {
            state.addBlocker(executionResult.summary());
            return;
        }
        if (subAgentResult.usedTools() != null) {
            subAgentResult.usedTools().forEach(state::addUsedTool);
        }
        if (subAgentResult.blockers() != null) {
            subAgentResult.blockers().forEach(state::addBlocker);
        }
        if (subAgentResult.suggestedNextAction() != null && !subAgentResult.suggestedNextAction().isBlank()) {
            state.setNextAction(subAgentResult.suggestedNextAction());
        }
    }

    private void mergeSubTaskState(AgentState state, AgentState subTaskState) {
        if (state == null || subTaskState == null) {
            return;
        }
        if (subTaskState.getPatientContext() != null) {
            state.setPatientContext(subTaskState.getPatientContext());
        }
        if (subTaskState.getAssessmentResults() != null && !subTaskState.getAssessmentResults().isEmpty()) {
            state.setAssessmentResults(subTaskState.getAssessmentResults());
        }
        if (subTaskState.getEvidenceContext() != null) {
            state.setEvidenceContext(subTaskState.getEvidenceContext());
        }
        if (subTaskState.getEvidenceCandidates() != null && !subTaskState.getEvidenceCandidates().isEmpty()) {
            state.setEvidenceCandidates(subTaskState.getEvidenceCandidates());
        }
        if (subTaskState.getKnowledgeGraphSummary() != null && !subTaskState.getKnowledgeGraphSummary().isBlank()) {
            state.setKnowledgeGraphSummary(subTaskState.getKnowledgeGraphSummary());
        }
        if (subTaskState.getKnowledgeGraphItems() != null && !subTaskState.getKnowledgeGraphItems().isEmpty()) {
            state.setKnowledgeGraphItems(subTaskState.getKnowledgeGraphItems());
        }
        if (subTaskState.getUsedTools() != null) {
            subTaskState.getUsedTools().forEach(state::addUsedTool);
        }
        if (subTaskState.getSafetyFlags() != null) {
            subTaskState.getSafetyFlags().forEach(state::addSafetyFlag);
        }
        if (subTaskState.getUnavailableExternalSources() != null) {
            subTaskState.getUnavailableExternalSources().forEach(state::addUnavailableExternalSource);
        }
        if (subTaskState.getDegradationNotes() != null) {
            subTaskState.getDegradationNotes().forEach(state::addDegradationNote);
        }
        if (subTaskState.getBlockers() != null) {
            subTaskState.getBlockers().forEach(state::addBlocker);
        }
    }

    private void requireReason(String reason, String message) {
        if (reason == null || reason.isBlank()) {
            throw new BusinessException(400, message);
        }
    }

    private void validateTargetAgent(String targetAgent) {
        if (targetAgent == null || targetAgent.isBlank()) {
            throw new BusinessException(400, "targetAgent 不能为空");
        }
        if (!subAgentRegistry.hasAgent(targetAgent.trim())) {
            throw new BusinessException(400, "目标智能体不存在: " + targetAgent);
        }
    }

    private String selectReplacementAgent(String taskId, String subTaskId) {
        OrchestrationTaskSnapshot snapshot = findTaskSnapshot(taskId);
        if (snapshot == null || snapshot.getSubTasks() == null) {
            return null;
        }
        Map<String, Object> subTask = findSubTask(snapshot, subTaskId);
        return subTaskHandoffRouter.selectReplacementAgent(snapshot, subTask);
    }

    private OrchestrationTaskSnapshot findTaskSnapshot(String taskId) {
        return orchestrationPersistenceService.listActiveSnapshots().stream()
                .filter(item -> taskId.equals(item.getTaskId()))
                .findFirst()
                .orElse(null);
    }

    private Map<String, Object> findSubTask(OrchestrationTaskSnapshot snapshot, String subTaskId) {
        if (snapshot == null || snapshot.getSubTasks() == null) {
            return null;
        }
        return snapshot.getSubTasks().stream()
                .filter(item -> subTaskId.equals(item.get("subTaskId")))
                .findFirst()
                .orElse(null);
    }

    private List<Map<String, Object>> enrichSubTasks(OrchestrationTaskSnapshot snapshot) {
        if (snapshot == null || snapshot.getSubTasks() == null) {
            return List.of();
        }
        return snapshot.getSubTasks().stream().map(subTask -> {
            Map<String, Object> payload = new LinkedHashMap<>(subTask);
            List<String> missingCapabilities = recentMissingCapabilities(snapshot, subTask);
            String failureCategory = recentFailureCategory(snapshot, subTask);
            Map<String, Object> contractSummary = recentContractSummary(snapshot, subTask);
            Map<String, Object> resolvedAgentSummary = recentResolvedAgentSummary(snapshot, subTask);
            List<Map<String, Object>> recommendedScores = subTaskHandoffRouter.scoreReplacementCandidates(snapshot, subTask);
            Map<String, Object> recommendationSummary = normalizeRecommendationSummary(
                    subTaskHandoffRouter.summarizeRecommendation(recommendedScores)
            );
            payload.put("recentFailureCategory", failureCategory);
            payload.put("recentMissingCapabilities", missingCapabilities);
            payload.put("recentResolvedAgentName", resolvedAgentSummary.get("name"));
            payload.put("recentResolvedAgentRouteKey", resolvedAgentSummary.get("routeKey"));
            payload.put("recentResolvedAgentProvider", resolvedAgentSummary.get("provider"));
            payload.put("recentContractValidated", contractSummary.get("validated"));
            payload.put("recentContractSchema", contractSummary.get("schema"));
            payload.put("recentContractVersion", contractSummary.get("version"));
            payload.put("recentContractCompatibility", contractSummary.get("compatibility"));
            payload.put("recentContractValidationErrors", contractSummary.getOrDefault("validationErrors", List.of()));
            payload.put("recentContractSuggestedHandoffAgent", contractSummary.get("suggestedHandoffAgent"));
            payload.put("recommendedAgent", subTaskHandoffRouter.selectReplacementAgent(snapshot, subTask));
            payload.put("recommendedAgentReason", subTaskHandoffRouter.explainReplacementReason(snapshot, subTask));
            payload.put("recommendedAgentScores", recommendedScores);
            payload.put("recommendedAgentConfidence", recommendationSummary.get("confidence"));
            payload.put("recommendedAgentScoreGap", recommendationSummary.get("scoreGap"));
            payload.put("recommendedAgentNeedsReview", recommendationSummary.get("needsReview"));
            payload.put("recommendedAgentVolatility", recommendationSummary.get("volatilityScore"));
            payload.put("recommendedAgentReviewTrigger", recommendationSummary.get("reviewTrigger"));
            return payload;
        }).toList();
    }

    private Map<String, Object> normalizeRecommendationSummary(Map<String, Object> recommendationSummary) {
        if (recommendationSummary == null || recommendationSummary.isEmpty()) {
            return Map.of();
        }
        String reviewTrigger = stringValue(recommendationSummary.get("reviewTrigger"));
        if (!"low_confidence_score_gap".equals(reviewTrigger)) {
            return recommendationSummary;
        }
        Map<String, Object> payload = new LinkedHashMap<>(recommendationSummary);
        payload.put("needsReview", false);
        payload.put("reviewTrigger", "");
        return payload;
    }

    private List<Map<String, Object>> buildTimeline(OrchestrationTaskSnapshot snapshot) {
        if (snapshot == null || snapshot.getEvents() == null) {
            return List.of();
        }
        return snapshot.getEvents().stream().map(event -> {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("type", event.get("type"));
            payload.put("occurredAt", event.get("occurredAt"));
            payload.put("summary", event.get("summary"));
            Map<String, Object> metadata = metadataMap(event);
            payload.put("stateLabel", timelineStateLabel(event));
            payload.put("displaySummary", timelineDisplaySummary(event));
            payload.put("subTaskId", metadata.getOrDefault("subTaskId", ""));
            payload.put("targetAgent", metadata.getOrDefault("targetAgent", ""));
            payload.put("resolvedAgentName", metadata.getOrDefault("resolvedAgentName", ""));
            payload.put("resolvedAgentRouteKey", metadata.getOrDefault("resolvedAgentRouteKey", ""));
            payload.put("resolvedAgentProvider", metadata.getOrDefault("resolvedAgentProvider", ""));
            payload.put("failureCategory", metadata.getOrDefault("failureCategory", ""));
            payload.put("missingCapabilities", metadata.getOrDefault("missingCapabilities", List.of()));
            payload.put("operator", metadata.getOrDefault("operator", ""));
            payload.put("reason", metadata.getOrDefault("reason", ""));
            payload.put("message", metadata.getOrDefault("message", ""));
            return payload;
        }).toList();
    }

    private Map<String, Object> buildLatestControlOutcome(OrchestrationTaskSnapshot snapshot,
                                                          List<Map<String, Object>> timeline,
                                                          List<Map<String, Object>> enrichedSubTasks) {
        if (snapshot == null || timeline == null || timeline.isEmpty()) {
            return Map.of();
        }
        int controlIndex = -1;
        for (int i = timeline.size() - 1; i >= 0; i--) {
            if (isManualControlEvent(stringValue(timeline.get(i).get("type")))) {
                controlIndex = i;
                break;
            }
        }
        if (controlIndex < 0) {
            return Map.of();
        }

        Map<String, Object> controlEvent = timeline.get(controlIndex);
        List<Map<String, Object>> followUpEvents = timeline.subList(controlIndex + 1, timeline.size());
        List<Map<String, Object>> businessEvents = followUpEvents.stream()
                .filter(event -> !isManualControlEvent(stringValue(event.get("type"))))
                .toList();
        Map<String, Object> latestBusinessEvent = businessEvents.isEmpty() ? null : businessEvents.get(businessEvents.size() - 1);
        String controlType = stringValue(controlEvent.get("type"));
        List<String> blockers = snapshot.getBlockers() == null ? List.of() : snapshot.getBlockers();

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("actionType", controlType);
        payload.put("actionAt", controlEvent.get("occurredAt"));
        payload.put("operator", controlEvent.get("operator"));
        payload.put("reason", controlEvent.get("reason"));
        payload.put("followUpEventCount", businessEvents.size());
        payload.put("status", resolveControlOutcomeStatus(snapshot, latestBusinessEvent, blockers));
        payload.put("summary", resolveControlOutcomeSummary(snapshot, controlType, latestBusinessEvent, blockers, enrichedSubTasks));
        payload.put("nextAction", buildPendingReviewAction(snapshot, enrichedSubTasks));
        payload.put("blockers", blockers);
        payload.put("highlights", businessEvents.stream()
                .map(event -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("type", event.get("type"));
                    item.put("occurredAt", event.get("occurredAt"));
                    item.put("summary", event.get("displaySummary"));
                    return item;
                })
                .toList());
        return payload;
    }

    private boolean isManualControlEvent(String type) {
        if (type == null || type.isBlank()) {
            return false;
        }
        return switch (type) {
            case "MANUAL_PAUSED",
                    "MANUAL_RESUMED",
                    "MANUAL_REVIEW_ACKED",
                    "MANUAL_CANCELLED",
                    "MANUAL_RETRIED",
                    "SUBTASK_SKIPPED",
                    "SUBTASK_RESET",
                    "SUBTASK_HANDOFF" -> true;
            default -> false;
        };
    }

    private String resolveControlOutcomeStatus(OrchestrationTaskSnapshot snapshot,
                                               Map<String, Object> latestBusinessEvent,
                                               List<String> blockers) {
        String eventType = stringValue(latestBusinessEvent == null ? null : latestBusinessEvent.get("type"));
        if ("COMPLETED".equals(eventType) || TaskStatus.COMPLETED.name().equals(snapshot.getStatus())) {
            return "completed";
        }
        if ("FAILED".equals(eventType) || TaskStatus.FAILED.name().equals(snapshot.getStatus())) {
            return "failed";
        }
        if ("BLOCKED".equals(eventType) || (blockers != null && !blockers.isEmpty()) || TaskStatus.BLOCKED.name().equals(snapshot.getStatus())) {
            return "blocked";
        }
        if ("SUPPORT_REQUESTED".equals(eventType) || "EXECUTION_STARTED".equals(eventType) || TaskStatus.RUNNING.name().equals(snapshot.getStatus())) {
            return "running";
        }
        if (OrchestrationControlStatus.PAUSED.name().equals(snapshot.getControlStatus())) {
            return "paused";
        }
        if (OrchestrationControlStatus.CANCELLED.name().equals(snapshot.getControlStatus())) {
            return "cancelled";
        }
        return "active";
    }

    private String resolveControlOutcomeSummary(OrchestrationTaskSnapshot snapshot,
                                                String controlType,
                                                Map<String, Object> latestBusinessEvent,
                                                List<String> blockers,
                                                List<Map<String, Object>> enrichedSubTasks) {
        String eventSummary = stringValue(latestBusinessEvent == null ? null : latestBusinessEvent.get("displaySummary"));
        if (eventSummary != null && !eventSummary.isBlank()) {
            return eventSummary;
        }
        if ("MANUAL_RETRIED".equals(controlType)) {
            if (TaskStatus.COMPLETED.name().equals(snapshot.getStatus())) {
                return "管理员强制重跑后，任务已完成。";
            }
            if (TaskStatus.FAILED.name().equals(snapshot.getStatus())) {
                return "管理员强制重跑后，任务再次失败。";
            }
            if (blockers != null && !blockers.isEmpty()) {
                return "管理员强制重跑后，任务仍存在阻塞。";
            }
            return "管理员强制重跑后，任务已恢复推进。";
        }
        if ("MANUAL_PAUSED".equals(controlType)) {
            return "管理员已暂停该任务，watchdog 不会继续推进。";
        }
        if ("MANUAL_RESUMED".equals(controlType) || "MANUAL_REVIEW_ACKED".equals(controlType)) {
            return buildCurrentStatusSummary(snapshot, enrichedSubTasks);
        }
        if ("MANUAL_CANCELLED".equals(controlType)) {
            return "管理员已取消该任务。";
        }
        if ("SUBTASK_RESET".equals(controlType) || "SUBTASK_HANDOFF".equals(controlType) || "SUBTASK_SKIPPED".equals(controlType)) {
            if (blockers != null && !blockers.isEmpty()) {
                return "管理员处理子任务后，主任务仍存在阻塞。";
            }
            if (TaskStatus.WAITING_SUPPORT.name().equals(snapshot.getStatus()) || TaskStatus.RUNNING.name().equals(snapshot.getStatus())) {
                return "管理员已更新子任务处理策略，watchdog 将在下一轮重新评估并恢复全流程推进。";
            }
            return "管理员已更新子任务处理策略，主任务等待继续推进。";
        }
        return buildCurrentStatusSummary(snapshot, enrichedSubTasks);
    }

    private boolean hasManualReviewRecommendation(List<Map<String, Object>> enrichedSubTasks) {
        return countManualReviewSubTasks(enrichedSubTasks) > 0;
    }

    private boolean requiresManualReview(OrchestrationTaskSnapshot snapshot) {
        return hasManualReviewRecommendation(enrichSubTasks(snapshot));
    }

    private String buildCurrentStatusSummary(OrchestrationTaskSnapshot snapshot,
                                             List<Map<String, Object>> enrichedSubTasks) {
        if (snapshot == null) {
            return null;
        }
        if (OrchestrationControlStatus.AWAITING_REVIEW.name().equals(snapshot.getControlStatus())) {
            String summary = buildManualReviewSummary(enrichedSubTasks);
            if ((summary == null || summary.isBlank()) && snapshot.getLastControlReason() != null && !snapshot.getLastControlReason().isBlank()) {
                summary = snapshot.getLastControlReason();
            }
            return summary == null || summary.isBlank()
                    ? "系统已暂停自动推进，等待管理员确认。"
                    : "系统已暂停自动推进，等待管理员确认。 " + summary;
        }
        if (hasManualReviewRecommendation(enrichedSubTasks)) {
            return "当前存在低置信度接力建议，建议管理员优先审查。";
        }
        if (TaskStatus.WAITING_SUPPORT.name().equals(snapshot.getStatus())) {
            return "当前子任务已具备继续执行条件，watchdog 将在下一轮恢复协同推进。";
        }
        return "当前任务可由 watchdog 自动推进。";
    }

    private String buildPendingReviewAction(OrchestrationTaskSnapshot snapshot,
                                            List<Map<String, Object>> enrichedSubTasks) {
        if (snapshot == null) {
            return null;
        }
        if (OrchestrationControlStatus.AWAITING_REVIEW.name().equals(snapshot.getControlStatus())) {
            return "请在确认转派建议后执行 ack-review 或直接做人工转派。";
        }
        if (hasManualReviewRecommendation(enrichedSubTasks)) {
            return "建议管理员先确认低置信度子任务，再决定是否继续自动推进。";
        }
        return null;
    }

    private String timelineStateLabel(Map<String, Object> event) {
        String type = stringValue(event == null ? null : event.get("type"));
        if (TaskEventType.SYSTEM_AWAITING_REVIEW.name().equals(type)) {
            return "awaiting_review";
        }
        if (TaskEventType.MANUAL_REVIEW_ACKED.name().equals(type)) {
            return "review_acked";
        }
        return "";
    }

    private String timelineDisplaySummary(Map<String, Object> event) {
        String type = stringValue(event == null ? null : event.get("type"));
        String summary = stringValue(event == null ? null : event.get("summary"));
        Map<String, Object> metadata = metadataMap(event);
        String reason = stringValue(metadata.get("reason"));
        if (TaskEventType.SYSTEM_AWAITING_REVIEW.name().equals(type)) {
            return reason == null || reason.isBlank()
                    ? "系统因低置信度接力建议暂停自动推进，等待管理员确认。"
                    : "系统因低置信度接力建议暂停自动推进，等待管理员确认。 " + reason;
        }
        if (TaskEventType.MANUAL_REVIEW_ACKED.name().equals(type)) {
            return reason == null || reason.isBlank()
                    ? "管理员已确认人工复核结果，任务恢复自动推进资格。"
                    : "管理员已确认人工复核结果，任务恢复自动推进资格。 原因: " + reason;
        }
        return summary == null ? "" : summary;
    }

    private boolean isWatchdogAutoAdvanceAllowed(OrchestrationTaskSnapshot snapshot,
                                                 List<Map<String, Object>> enrichedSubTasks) {
        if (snapshot != null && OrchestrationControlStatus.AWAITING_REVIEW.name().equals(snapshot.getControlStatus())) {
            return false;
        }
        return !hasManualReviewRecommendation(enrichedSubTasks);
    }

    private String resolveWatchdogDecision(OrchestrationTaskSnapshot snapshot,
                                           List<Map<String, Object>> enrichedSubTasks) {
        if (snapshot != null && OrchestrationControlStatus.AWAITING_REVIEW.name().equals(snapshot.getControlStatus())) {
            return "await_manual_review";
        }
        return hasManualReviewRecommendation(enrichedSubTasks) ? "recommend_manual_review" : "auto_advance";
    }

    private int countManualReviewTasks(List<Map<String, Object>> activeTasks) {
        if (activeTasks == null || activeTasks.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (Map<String, Object> task : activeTasks) {
            if (Boolean.TRUE.equals(task.get("manualReviewRecommended"))) {
                count++;
            }
        }
        return count;
    }

    private int countManualReviewSubTasks(List<Map<String, Object>> enrichedSubTasks) {
        if (enrichedSubTasks == null || enrichedSubTasks.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (Map<String, Object> subTask : enrichedSubTasks) {
            if (Boolean.TRUE.equals(subTask.get("recommendedAgentNeedsReview"))) {
                count++;
            }
        }
        return count;
    }

    private List<String> collectManualReviewSubTaskIds(List<Map<String, Object>> enrichedSubTasks) {
        if (enrichedSubTasks == null || enrichedSubTasks.isEmpty()) {
            return List.of();
        }
        return enrichedSubTasks.stream()
                .filter(subTask -> Boolean.TRUE.equals(subTask.get("recommendedAgentNeedsReview")))
                .map(subTask -> stringValue(subTask.get("subTaskId")))
                .filter(text -> text != null && !text.isBlank())
                .toList();
    }

    private List<String> collectManualReviewReasons(List<Map<String, Object>> enrichedSubTasks) {
        if (enrichedSubTasks == null || enrichedSubTasks.isEmpty()) {
            return List.of();
        }
        return enrichedSubTasks.stream()
                .filter(subTask -> Boolean.TRUE.equals(subTask.get("recommendedAgentNeedsReview")))
                .map(subTask -> {
                    String trigger = stringValue(subTask.get("recommendedAgentReviewTrigger"));
                    String reason = stringValue(subTask.get("recommendedAgentReason"));
                    return trigger == null || trigger.isBlank() ? reason : trigger + ":" + reason;
                })
                .filter(text -> text != null && !text.isBlank())
                .distinct()
                .toList();
    }

    private String buildManualReviewSummary(List<Map<String, Object>> enrichedSubTasks) {
        int reviewCount = countManualReviewSubTasks(enrichedSubTasks);
        if (reviewCount <= 0) {
            return null;
        }
        List<String> reasons = collectManualReviewReasons(enrichedSubTasks);
        if (reasons.isEmpty()) {
            return "存在低置信度接力建议，建议人工确认。";
        }
        return "存在 " + reviewCount + " 个低置信度子任务，建议人工确认: " + String.join("; ", reasons);
    }

    private String recentFailureCategory(OrchestrationTaskSnapshot snapshot, Map<String, Object> subTask) {
        if (snapshot == null || snapshot.getEvents() == null) {
            return null;
        }
        String subTaskId = stringValue(subTask.get("subTaskId"));
        if (subTaskId == null || subTaskId.isBlank()) {
            return null;
        }
        for (int i = snapshot.getEvents().size() - 1; i >= 0; i--) {
            Map<String, Object> event = snapshot.getEvents().get(i);
            if (!subTaskId.equals(metadataValue(event, "subTaskId"))) {
                continue;
            }
            String failureCategory = metadataValue(event, "failureCategory");
            if (failureCategory != null && !failureCategory.isBlank()) {
                return failureCategory;
            }
        }
        return null;
    }

    private Map<String, Object> recentContractSummary(OrchestrationTaskSnapshot snapshot, Map<String, Object> subTask) {
        if (snapshot == null || snapshot.getEvents() == null) {
            return Map.of();
        }
        String subTaskId = stringValue(subTask.get("subTaskId"));
        if (subTaskId == null || subTaskId.isBlank()) {
            return Map.of();
        }
        for (int i = snapshot.getEvents().size() - 1; i >= 0; i--) {
            Map<String, Object> event = snapshot.getEvents().get(i);
            if (!subTaskId.equals(metadataValue(event, "subTaskId"))) {
                continue;
            }
            Map<String, Object> metadata = metadataMap(event);
            if (!metadata.containsKey("contractSchema")
                    && !metadata.containsKey("contractVersion")
                    && !metadata.containsKey("contractCompatibility")
                    && !metadata.containsKey("contractValidationErrors")) {
                continue;
            }
            Map<String, Object> compatibility = nestedMap(metadata.get("contractCompatibility"));
            List<String> validationErrors = stringList(metadata.get("contractValidationErrors"));
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("schema", stringValue(metadata.get("contractSchema")));
            payload.put("version", stringValue(metadata.get("contractVersion")));
            payload.put("compatibility", compatibilityLabel(compatibility));
            payload.put("validated", validatedFlag(compatibility, validationErrors));
            payload.put("validationErrors", validationErrors);
            payload.put("suggestedHandoffAgent", stringValue(metadata.get("suggestedHandoffAgent")));
            return payload;
        }
        return Map.of();
    }

    private Map<String, Object> recentResolvedAgentSummary(OrchestrationTaskSnapshot snapshot, Map<String, Object> subTask) {
        if (snapshot == null || snapshot.getEvents() == null) {
            return Map.of();
        }
        String subTaskId = stringValue(subTask.get("subTaskId"));
        if (subTaskId == null || subTaskId.isBlank()) {
            return Map.of();
        }
        for (int i = snapshot.getEvents().size() - 1; i >= 0; i--) {
            Map<String, Object> event = snapshot.getEvents().get(i);
            if (!subTaskId.equals(metadataValue(event, "subTaskId"))) {
                continue;
            }
            Map<String, Object> metadata = metadataMap(event);
            String resolvedAgentName = stringValue(metadata.get("resolvedAgentName"));
            String resolvedAgentRouteKey = stringValue(metadata.get("resolvedAgentRouteKey"));
            String resolvedAgentProvider = stringValue(metadata.get("resolvedAgentProvider"));
            if ((resolvedAgentName == null || resolvedAgentName.isBlank())
                    && (resolvedAgentRouteKey == null || resolvedAgentRouteKey.isBlank())
                    && (resolvedAgentProvider == null || resolvedAgentProvider.isBlank())) {
                continue;
            }
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("name", resolvedAgentName);
            payload.put("routeKey", resolvedAgentRouteKey);
            payload.put("provider", resolvedAgentProvider);
            return payload;
        }
        return Map.of();
    }

    private List<String> recentMissingCapabilities(OrchestrationTaskSnapshot snapshot, Map<String, Object> subTask) {
        if (snapshot == null || snapshot.getEvents() == null) {
            return List.of();
        }
        String subTaskId = stringValue(subTask.get("subTaskId"));
        if (subTaskId == null || subTaskId.isBlank()) {
            return List.of();
        }
        for (int i = snapshot.getEvents().size() - 1; i >= 0; i--) {
            Map<String, Object> event = snapshot.getEvents().get(i);
            if (!subTaskId.equals(metadataValue(event, "subTaskId"))) {
                continue;
            }
            Object value = metadataMap(event).get("missingCapabilities");
            if (value instanceof List<?> items && !items.isEmpty()) {
                return items.stream().map(this::stringValue).filter(text -> text != null && !text.isBlank()).toList();
            }
        }
        return List.of();
    }

    private String metadataValue(Map<String, Object> event, String key) {
        return stringValue(metadataMap(event).get(key));
    }

    private Map<String, Object> nestedMap(Object value) {
        if (!(value instanceof Map<?, ?> metadata)) {
            return Map.of();
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        metadata.forEach((key, item) -> {
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
        boolean schemaCompatible = Boolean.TRUE.equals(booleanValue(compatibility.get("schemaCompatible")));
        boolean versionCompatible = Boolean.TRUE.equals(booleanValue(compatibility.get("versionCompatible")));
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

    private String stringValue(Object value) {
        return value == null ? null : value.toString();
    }

    private List<?> listValue(Object value) {
        return value instanceof List<?> items ? items : List.of();
    }

    private List<String> stringList(Object value) {
        return listValue(value).stream()
                .map(this::stringValue)
                .filter(text -> text != null && !text.isBlank())
                .toList();
    }

    private void incrementCount(Map<String, Long> counts, String key) {
        if (key == null || key.isBlank()) {
            return;
        }
        counts.merge(key, 1L, Long::sum);
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

    private double doubleValue(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value == null) {
            return 0D;
        }
        try {
            return Double.parseDouble(value.toString());
        } catch (NumberFormatException ignored) {
            return 0D;
        }
    }

    private long longValue(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value == null) {
            return 0L;
        }
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException ignored) {
            return 0L;
        }
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

    private Map<String, Object> buildDashboardHighlights(List<Map<String, Object>> activeTasks,
                                                         Map<String, Long> reviewTriggerCounts,
                                                         List<Map<String, Object>> recent24hAgentExecutionHealth) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("actionRequiredTaskIds", activeTasks.stream()
                .filter(task -> Boolean.TRUE.equals(task.get("manualReviewRecommended")) || Boolean.TRUE.equals(task.get("circuitBreakerOpen")))
                .map(task -> stringValue(task.get("taskId")))
                .filter(text -> text != null && !text.isBlank())
                .toList());
        payload.put("dominantReviewTrigger", topEntries(reviewTriggerCounts, 1).stream().findFirst().orElse(Map.of()));
        payload.put("unstableAgents", recent24hAgentExecutionHealth.stream()
                .filter(item -> doubleValue(item.get("stabilityScore")) < 0.15D || doubleValue(item.get("successRate")) < 0.5D)
                .limit(5)
                .toList());
        return payload;
    }

    private List<Map<String, Object>> enrichAgentSummaries(List<Map<String, Object>> items,
                                                           Map<String, Map<String, Object>> executionHealthStatsByAgent) {
        if (items == null || items.isEmpty()) {
            return List.of();
        }
        return items.stream()
                .map(item -> enrichAgentSummary(item, executionHealthStatsByAgent))
                .toList();
    }

    private Map<String, Object> enrichAgentSummary(Map<String, Object> item,
                                                   Map<String, Map<String, Object>> executionHealthStatsByAgent) {
        if (item == null || item.isEmpty()) {
            return Map.of();
        }
        LinkedHashMap<String, Object> payload = new LinkedHashMap<>(item);
        String agentName = stringValue(payload.get("agent"));
        if (agentName == null || agentName.isBlank()) {
            return payload;
        }
        payload.put("agentName", agentName);
        ChatSubAgent agent = subAgentRegistry == null ? null : subAgentRegistry.getAgent(agentName);
        if (agent == null) {
            return payload;
        }
        payload.put("resolvedAgentName", agent.name());
        payload.put("routeKey", agent.routeKey());
        payload.put("provider", agent.provider());
        payload.put("estimatedCostScore", agent.estimatedCostScore());
        payload.put("baselineHealthScore", agent.baselineHealthScore());
        payload.put("healthState", subAgentRegistry.implementationHealthState(agent.name(), executionHealthStatsByAgent));
        payload.put("providerHealthState", subAgentRegistry.providerHealthState(agent.provider(), executionHealthStatsByAgent));
        payload.put("routingPreferred", subAgentRegistry.isPrimaryImplementation(agent.name(), executionHealthStatsByAgent));
        return payload;
    }

    private Map<String, Map<String, Object>> indexAgentHealthStats(List<Map<String, Object>> items) {
        if (items == null || items.isEmpty()) {
            return Map.of();
        }
        LinkedHashMap<String, Map<String, Object>> payload = new LinkedHashMap<>();
        for (Map<String, Object> item : items) {
            String agent = stringValue(item.get("agent"));
            if (agent == null || agent.isBlank()) {
                continue;
            }
            payload.put(agent.trim().toLowerCase(), item);
        }
        return payload;
    }

    private List<Map<String, Object>> topUnhealthyResolvedAgents(List<Map<String, Object>> items, int limit) {
        if (items == null || items.isEmpty()) {
            return List.of();
        }
        return items.stream()
                .filter(item -> longValue(item.get("executionCount")) > 0L)
                .sorted((left, right) -> {
                    int successRateOrder = Double.compare(doubleValue(left.get("successRate")), doubleValue(right.get("successRate")));
                    if (successRateOrder != 0) {
                        return successRateOrder;
                    }
                    int stabilityOrder = Double.compare(doubleValue(left.get("stabilityScore")), doubleValue(right.get("stabilityScore")));
                    if (stabilityOrder != 0) {
                        return stabilityOrder;
                    }
                    return Long.compare(longValue(right.get("executionCount")), longValue(left.get("executionCount")));
                })
                .limit(limit)
                .toList();
    }

    private List<Map<String, Object>> topIssueHeavyResolvedAgents(List<Map<String, Object>> items, int limit) {
        if (items == null || items.isEmpty()) {
            return List.of();
        }
        return items.stream()
                .sorted((left, right) -> {
                    int issueCountOrder = Long.compare(longValue(right.get("issueCount")), longValue(left.get("issueCount")));
                    if (issueCountOrder != 0) {
                        return issueCountOrder;
                    }
                    return String.valueOf(left.getOrDefault("agentName", left.get("agent")))
                            .compareToIgnoreCase(String.valueOf(right.getOrDefault("agentName", right.get("agent"))));
                })
                .limit(limit)
                .toList();
    }

    private boolean matchesFilter(String actualValue, String expectedValue) {
        if (expectedValue == null || expectedValue.isBlank()) {
            return true;
        }
        return expectedValue.equalsIgnoreCase(actualValue);
    }

    private boolean matchesCircuitFilter(Map<String, Object> task, Boolean circuitBreakerOnly) {
        if (circuitBreakerOnly == null || !circuitBreakerOnly) {
            return true;
        }
        return Boolean.TRUE.equals(task.get("circuitBreakerOpen"));
    }

    private boolean matchesReviewTrigger(Map<String, Object> task, String reviewTrigger) {
        if (reviewTrigger == null || reviewTrigger.isBlank()) {
            return true;
        }
        for (Object item : listValue(task.get("subTasks"))) {
            if (item instanceof Map<?, ?> subTask && reviewTrigger.equalsIgnoreCase(stringValue(subTask.get("recommendedAgentReviewTrigger")))) {
                return true;
            }
        }
        return false;
    }

    private boolean matchesContractCompatibility(Map<String, Object> task, String contractCompatibility) {
        if (contractCompatibility == null || contractCompatibility.isBlank()) {
            return true;
        }
        return contractCompatibility.equalsIgnoreCase(stringValue(task.get("latestContractCompatibility")));
    }

    private boolean matchesContractValidated(Map<String, Object> task, Boolean contractValidated) {
        if (contractValidated == null) {
            return true;
        }
        return contractValidated.equals(booleanValue(task.get("latestContractValidated")));
    }

    private boolean matchesContractErrors(Map<String, Object> task, Boolean contractErrorsOnly) {
        if (!Boolean.TRUE.equals(contractErrorsOnly)) {
            return true;
        }
        return (intValue(task.get("latestContractValidationErrorCount")) != null
                && intValue(task.get("latestContractValidationErrorCount")) > 0)
                || Boolean.FALSE.equals(booleanValue(task.get("latestContractValidated")));
    }

    private String contractValidationState(Map<String, Object> task) {
        Boolean validated = booleanValue(task.get("latestContractValidated"));
        Integer errorCount = intValue(task.get("latestContractValidationErrorCount"));
        if (Boolean.TRUE.equals(validated) && (errorCount == null || errorCount <= 0)) {
            return "validated";
        }
        if (Boolean.FALSE.equals(validated) || (errorCount != null && errorCount > 0)) {
            return "invalid";
        }
        return "unknown";
    }

    private Map<String, Object> toTaskCard(Map<String, Object> task) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("taskId", task.get("taskId"));
        payload.put("sessionId", task.get("sessionId"));
        payload.put("goal", task.get("goal"));
        payload.put("taskType", task.get("taskType"));
        payload.put("status", task.get("status"));
        payload.put("controlStatus", task.get("controlStatus"));
        payload.put("watchdogDecision", task.get("watchdogDecision"));
        payload.put("manualReviewRecommended", task.get("manualReviewRecommended"));
        payload.put("manualReviewSummary", task.get("manualReviewSummary"));
        payload.put("manualReviewReasons", task.get("manualReviewReasons"));
        payload.put("latestControlAction", task.get("lastControlAction"));
        payload.put("latestControlBy", task.get("lastControlBy"));
        payload.put("latestControlReason", task.get("lastControlReason"));
        payload.put("latestControlAt", task.get("lastControlAt"));
        payload.put("circuitBreakerOpen", task.get("circuitBreakerOpen"));
        payload.put("circuitBreakerOpenUntil", task.get("circuitBreakerOpenUntil"));
        payload.put("circuitBreakerOpenReason", task.get("circuitBreakerOpenReason"));
        payload.put("watchdogFailureStreak", task.get("watchdogFailureStreak"));
        payload.put("nextRunAt", task.get("nextRunAt"));
        payload.put("updatedAt", task.get("updatedAt"));
        payload.put("reviewTriggers", collectReviewTriggers(task));
        payload.put("recommendedAgents", collectRecommendedAgents(task));
        payload.put("subTaskCount", task.get("subTaskCount"));
        return payload;
    }

    private List<String> collectReviewTriggers(Map<String, Object> task) {
        return listValue(task.get("subTasks")).stream()
                .filter(Map.class::isInstance)
                .map(Map.class::cast)
                .map(subTask -> stringValue(subTask.get("recommendedAgentReviewTrigger")))
                .filter(text -> text != null && !text.isBlank())
                .distinct()
                .toList();
    }

    private List<String> collectRecommendedAgents(Map<String, Object> task) {
        return listValue(task.get("subTasks")).stream()
                .filter(Map.class::isInstance)
                .map(Map.class::cast)
                .map(subTask -> stringValue(subTask.get("recommendedAgent")))
                .filter(text -> text != null && !text.isBlank())
                .distinct()
                .toList();
    }
}
