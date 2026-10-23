package com.medical.agent.service.react.orchestration;

import com.medical.agent.model.mongo.OrchestrationTaskSnapshot;
import com.medical.agent.service.PlatformMetricsService;
import com.medical.agent.service.react.agent.ChatSubAgent;
import com.medical.agent.service.react.agent.SubAgentResult;
import com.medical.agent.service.react.agent.SubAgentResultContractSupport;
import com.medical.agent.service.react.agent.SubAgentRegistry;
import com.medical.agent.service.react.model.AgentState;
import com.medical.agent.service.react.model.ReActResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class TaskScheduler {

    private static final Pattern AGENT_NAME_PATTERN = Pattern.compile("\\b[a-z_]+_agent\\b");

    private final TaskStateMachine taskStateMachine;
    private final SubAgentRegistry subAgentRegistry;
    private final SupervisorLoop supervisorLoop;
    private final SubAgentResultContractSupport contractSupport;
    private final Executor orchestrationTaskExecutor;
    private final int maxParallelDispatches;
    private final PlatformMetricsService platformMetricsService;

    @Autowired
    public TaskScheduler(TaskStateMachine taskStateMachine,
                         SubAgentRegistry subAgentRegistry,
                         SupervisorLoop supervisorLoop,
                         PlatformMetricsService platformMetricsService,
                         @Qualifier("orchestrationTaskExecutor") Executor orchestrationTaskExecutor,
                         @Value("${orchestration.dispatch.max-parallelism:2}") int maxParallelDispatches) {
        this.taskStateMachine = taskStateMachine;
        this.subAgentRegistry = subAgentRegistry;
        this.supervisorLoop = supervisorLoop;
        this.contractSupport = new SubAgentResultContractSupport();
        this.orchestrationTaskExecutor = orchestrationTaskExecutor;
        this.maxParallelDispatches = Math.max(1, maxParallelDispatches);
        this.platformMetricsService = platformMetricsService;
    }

    public TaskScheduler(TaskStateMachine taskStateMachine,
                         SubAgentRegistry subAgentRegistry,
                         SupervisorLoop supervisorLoop) {
        this(taskStateMachine, subAgentRegistry, supervisorLoop, null, Runnable::run, 2);
    }

    public AutonomousTask createRootTask(AgentState state) {
        String goal = state == null || state.getUserMessage() == null || state.getUserMessage().isBlank()
                ? "处理当前对话任务"
                : state.getUserMessage().trim();
        AutonomousTask task = new AutonomousTask(UUID.randomUUID().toString(), goal, "ChatSupervisorService", 100);
        task.addEvent(new TaskEvent(TaskEventType.CREATED, java.time.LocalDateTime.now(), "已创建根任务。", Map.of()));
        return task;
    }

    public AutonomousTask restoreFromSnapshot(OrchestrationTaskSnapshot snapshot) {
        if (snapshot == null) {
            return null;
        }
        AutonomousTask task = new AutonomousTask(
                snapshot.getTaskId() == null || snapshot.getTaskId().isBlank() ? UUID.randomUUID().toString() : snapshot.getTaskId(),
                snapshot.getGoal() == null || snapshot.getGoal().isBlank() ? "恢复未完成任务" : snapshot.getGoal(),
                "Watchdog",
                100
        );
        if (snapshot.getStatus() != null) {
            try {
                task.setStatus(TaskStatus.valueOf(snapshot.getStatus()));
            } catch (IllegalArgumentException ignored) {
                task.setStatus(TaskStatus.BLOCKED);
            }
        }
        task.setAssignedAgent(snapshot.getAssignedAgent());
        if (snapshot.getDependencies() != null) {
            task.getDependencies().addAll(snapshot.getDependencies());
        }
        if (snapshot.getSubTasks() != null) {
            for (Map<String, Object> item : snapshot.getSubTasks()) {
                SubTask subTask = toSubTask(item);
                if (subTask != null) {
                    task.addSubTask(subTask);
                }
            }
        }
        if (snapshot.getEvents() != null) {
            for (Map<String, Object> item : snapshot.getEvents()) {
                TaskEvent event = toTaskEvent(item);
                if (event != null) {
                    task.addEvent(event);
                }
            }
        }
        return task;
    }

    public void markPlanning(AutonomousTask task) {
        taskStateMachine.markPlanning(task, "主控开始规划任务。");
    }

    public void reconcile(AutonomousTask task, ReActResult result) {
        if (task == null || result == null) {
            return;
        }
        String assignedAgent = detectAssignedAgent(result);
        if (assignedAgent != null) {
            taskStateMachine.markRunning(task, assignedAgent, "主控已将任务推进到执行阶段。");
        }
        if (result.nextAction() != null && (result.nextAction().contains("协助") || result.nextAction().contains("协调"))) {
            enqueueSupportSubTasks(task, result);
            taskStateMachine.markWaitingSupport(
                    task,
                    "当前任务需要其他子智能体协助。",
                    Map.of("nextAction", result.nextAction(), "blockers", result.blockers() == null ? List.of() : result.blockers())
            );
            return;
        }
        if (result.blockers() != null && !result.blockers().isEmpty()) {
            enqueueSupportSubTasks(task, result);
            taskStateMachine.markBlocked(task, "当前任务存在阻塞，等待下一轮恢复。", new LinkedHashMap<>(Map.of(
                    "blockers", result.blockers(),
                    "nextAction", result.nextAction() == null ? "" : result.nextAction()
            )));
            return;
        }
        if (result.reply() != null && !result.reply().isBlank()) {
            taskStateMachine.markCompleted(task, "当前任务已产出最终答复。");
            return;
        }
        taskStateMachine.markFailed(task, "当前任务未生成有效答复。");
    }

    public OrchestrationTrace snapshot(AutonomousTask task) {
        if (task == null) {
            return null;
        }
        SupervisorDecision decision = supervisorLoop.pickNext(task);
        return new OrchestrationTrace(
                task.getTaskId(),
                task.getGoal(),
                task.getStatus(),
                task.getAssignedAgent(),
                List.copyOf(task.getDependencies()),
                task.getSubTasks().stream()
                        .map(subTask -> new SubTaskTrace(
                                subTask.getSubTaskId(),
                                subTask.getGoal(),
                                subTask.getTargetAgent(),
                                subTask.getPriority(),
                                subTask.getStatus(),
                                List.copyOf(subTask.getDependencies())
                        ))
                        .toList(),
                List.copyOf(task.getHistory()),
                decision
        );
    }

    public SubTaskExecutionResult dispatchNext(AutonomousTask task, AgentState state) {
        List<SubTaskExecutionResult> batch = dispatchReadyBatch(task, state, 1);
        return batch.isEmpty() ? null : batch.get(0);
    }

    public List<SubTaskExecutionResult> dispatchReadyBatch(AutonomousTask task, AgentState state) {
        return dispatchReadyBatch(task, state, Integer.MAX_VALUE);
    }

    private List<SubTaskExecutionResult> dispatchReadyBatch(AutonomousTask task, AgentState state, int limit) {
        if (task == null || state == null) {
            return List.of();
        }
        List<SubTask> readySubTasks = supervisorLoop.rankCandidates(task, Math.max(1, Math.min(limit, maxParallelDispatches))).stream()
                .limit(Math.max(1, Math.min(limit, maxParallelDispatches)))
                .toList();
        if (readySubTasks.isEmpty()) {
            return List.of();
        }

        List<SubTaskExecutionResult> immediateResults = new ArrayList<>();
        List<PendingSubTaskDispatch> pendingDispatches = new ArrayList<>();
        for (SubTask subTask : readySubTasks) {
            SupervisorDecision decision = buildDecision(task, subTask);
            ChatSubAgent subAgent = subAgentRegistry.getAgent(decision.targetAgent());
            if (subAgent == null) {
                taskStateMachine.markSubTaskFailed(subTask);
                task.addEvent(new TaskEvent(
                        TaskEventType.FAILED,
                        LocalDateTime.now(),
                        "子任务目标智能体不存在。",
                        Map.of(
                                "subTaskId", subTask.getSubTaskId(),
                                "targetAgent", decision.targetAgent(),
                                "failureCategory", "missing_agent",
                                "supervisorScore", decision.score()
                        )
                ));
                immediateResults.add(new SubTaskExecutionResult(
                        decision,
                        subTask,
                        null,
                        false,
                        "子任务目标智能体不存在。",
                        state.copy(),
                        null,
                        null,
                        null
                ));
                recordDispatchOutcome(decision.targetAgent(), null, null, "missing_agent");
                continue;
            }
            taskStateMachine.markSubTaskRunning(subTask);
            task.addEvent(new TaskEvent(
                    TaskEventType.EXECUTION_STARTED,
                    LocalDateTime.now(),
                    decision.summary(),
                    executionMetadata(subTask, decision, subAgent, Map.of())
            ));
            pendingDispatches.add(new PendingSubTaskDispatch(decision, subTask, subAgent, state.copy()));
        }

        List<CompletableFuture<SubTaskExecutionOutcome>> futures = pendingDispatches.stream()
                .map(dispatch -> CompletableFuture.supplyAsync(() -> executeSubTask(dispatch), orchestrationTaskExecutor))
                .toList();

        List<SubTaskExecutionResult> results = new ArrayList<>(immediateResults);
        for (CompletableFuture<SubTaskExecutionOutcome> future : futures) {
            SubTaskExecutionOutcome outcome = future.join();
            SubTask subTask = outcome.subTask();
            SupervisorDecision decision = outcome.decision();
            SubAgentResult subAgentResult = outcome.subAgentResult();

            if (outcome.runtimeError() == null) {
                if (subAgentResult != null && subAgentResult.success() && subAgentResult.completed()) {
                    taskStateMachine.markSubTaskCompleted(subTask);
                    task.addEvent(new TaskEvent(
                            TaskEventType.COMPLETED,
                            LocalDateTime.now(),
                            "子任务执行完成。",
                            completionMetadata(subTask, decision, outcome.subAgent(), subAgentResult)
                    ));
                    results.add(new SubTaskExecutionResult(
                            decision,
                            subTask,
                            subAgentResult,
                            true,
                            subAgentResult.summary(),
                            outcome.stateSnapshot(),
                            outcome.resolvedAgentName(),
                            outcome.resolvedAgentRouteKey(),
                            outcome.resolvedAgentProvider()
                    ));
                    recordDispatchOutcome(decision.targetAgent(), outcome.resolvedAgentName(), outcome.resolvedAgentProvider(), "completed");
                    continue;
                }

                taskStateMachine.markSubTaskBlocked(subTask);
                String failureCategory = resolveFailureCategory(subTask, subAgentResult, null);
                LinkedHashMap<String, Object> blockedMetadata = new LinkedHashMap<>(executionMetadata(subTask, decision, outcome.subAgent(), Map.of()));
                blockedMetadata.put("failureCategory", failureCategory);
                blockedMetadata.put("missingCapabilities", subAgentResult == null ? List.of() : subAgentResult.missingCapabilities());
                blockedMetadata.put("requestedSupportAgents", subAgentResult == null ? List.of() : subAgentResult.requestedSupportAgents());
                blockedMetadata.put("suggestedHandoffAgent", subAgentResult == null ? "" : subAgentResult.suggestedHandoffAgent());
                blockedMetadata.put("confidence", subAgentResult == null ? null : subAgentResult.confidence());
                blockedMetadata.put("evidenceRefs", subAgentResult == null ? List.of() : subAgentResult.evidenceRefs());
                blockedMetadata.put("artifacts", subAgentResult == null ? List.of() : subAgentResult.artifacts());
                blockedMetadata.put("handoffPayload", subAgentResult == null ? Map.of() : subAgentResult.handoffPayload());
                blockedMetadata.put("nextRequiredCapabilities", subAgentResult == null ? List.of() : subAgentResult.nextRequiredCapabilities());
                if (subAgentResult != null) {
                    blockedMetadata.putAll(contractSupport.metadata(subAgentResult));
                }
                blockedMetadata.put("supervisorScore", decision.score());
                task.addEvent(new TaskEvent(
                        TaskEventType.BLOCKED,
                        LocalDateTime.now(),
                        "子任务未完成，进入阻塞。",
                        blockedMetadata
                ));
                results.add(new SubTaskExecutionResult(
                        decision,
                        subTask,
                        subAgentResult,
                        false,
                        subAgentResult == null ? "子任务未返回结果。" : subAgentResult.summary(),
                        outcome.stateSnapshot(),
                        outcome.resolvedAgentName(),
                        outcome.resolvedAgentRouteKey(),
                        outcome.resolvedAgentProvider()
                ));
                recordDispatchOutcome(decision.targetAgent(), outcome.resolvedAgentName(), outcome.resolvedAgentProvider(), "blocked");
                continue;
            }

            taskStateMachine.markSubTaskFailed(subTask);
            String failureCategory = resolveFailureCategory(subTask, null, outcome.runtimeError());
            task.addEvent(new TaskEvent(
                    TaskEventType.FAILED,
                    LocalDateTime.now(),
                    "子任务执行失败。",
                    executionMetadata(subTask, decision, outcome.subAgent(), Map.of(
                            "message", outcome.runtimeError(),
                            "failureCategory", failureCategory
                    ))
            ));
            results.add(new SubTaskExecutionResult(
                    decision,
                    subTask,
                    null,
                    false,
                    "子任务执行失败: " + outcome.runtimeError(),
                    outcome.stateSnapshot(),
                    outcome.resolvedAgentName(),
                    outcome.resolvedAgentRouteKey(),
                    outcome.resolvedAgentProvider()
            ));
            recordDispatchOutcome(decision.targetAgent(), outcome.resolvedAgentName(), outcome.resolvedAgentProvider(), "failed");
        }
        return results;
    }

    private String resolveFailureCategory(SubTask subTask, SubAgentResult subAgentResult, String runtimeMessage) {
        if (subAgentResult != null && subAgentResult.failureCategory() != null && !subAgentResult.failureCategory().isBlank()) {
            return subAgentResult.failureCategory().trim();
        }
        return classifyFailureCategory(
                subTask,
                subAgentResult == null ? runtimeMessage : subAgentResult.summary(),
                subAgentResult == null ? List.of() : subAgentResult.blockers()
        );
    }

    private String classifyFailureCategory(SubTask subTask, String summary, List<String> blockers) {
        String currentAgent = subTask == null ? "" : stringValue(subTask.getTargetAgent());
        String goal = subTask == null ? "" : stringValue(subTask.getGoal());
        String combined = ((summary == null ? "" : summary) + " " + (goal == null ? "" : goal) + " " + String.join(" ", blockers == null ? List.of() : blockers))
                .toLowerCase();
        if (combined.contains("不存在")) {
            return "missing_agent";
        }
        if (containsAny(combined, "上下文", "画像", "病史", "medplum", "患者资料", "档案")) {
            return "context_gap";
        }
        if (containsAny(combined, "证据", "指南", "文献", "知识图谱", "依据")) {
            return "evidence_gap";
        }
        if (containsAny(combined, "风险", "高危", "分层", "评估")) {
            return "risk_reassessment";
        }
        if (containsAny(combined, "超时", "timeout", "连接", "不可用", "失败", "异常")) {
            return "tool_or_runtime_failure";
        }
        if ("patient_profile_agent".equals(currentAgent)) {
            return "context_gap";
        }
        if ("evidence_support_agent".equals(currentAgent)) {
            return "evidence_gap";
        }
        if ("risk_assessment_agent".equals(currentAgent)) {
            return "risk_reassessment";
        }
        return "general_failure";
    }

    private boolean containsAny(String text, String... patterns) {
        for (String pattern : patterns) {
            if (text.contains(pattern)) {
                return true;
            }
        }
        return false;
    }

    private void enqueueSupportSubTasks(AutonomousTask task, ReActResult result) {
        if (task == null || result == null) {
            return;
        }
        Set<String> candidateAgents = new LinkedHashSet<>();
        candidateAgents.addAll(extractKnownAgents(result.nextAction()));
        if (result.blockers() != null) {
            result.blockers().forEach(blocker -> candidateAgents.addAll(extractKnownAgents(blocker)));
        }
        if (candidateAgents.isEmpty()) {
            return;
        }
        for (String agentName : candidateAgents) {
            if (task.getSubTasks().stream().anyMatch(subTask -> agentName.equals(subTask.getTargetAgent()))) {
                continue;
            }
            SubTask subTask = new SubTask(
                    UUID.randomUUID().toString(),
                    buildSubTaskGoal(task, result, agentName),
                    agentName,
                    computePriority(agentName, result)
            );
            taskStateMachine.markSubTaskReady(subTask);
            task.addSubTask(subTask);
            task.addEvent(new TaskEvent(
                    TaskEventType.SUPPORT_REQUESTED,
                    LocalDateTime.now(),
                    "已为协作请求生成子任务。",
                    Map.of("subTaskId", subTask.getSubTaskId(), "targetAgent", agentName)
            ));
        }
    }

    private List<String> extractKnownAgents(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        String normalized = text.toLowerCase();
        List<String> agents = new ArrayList<>();
        Matcher matcher = AGENT_NAME_PATTERN.matcher(text);
        while (matcher.find()) {
            String candidate = matcher.group();
            if (subAgentRegistry.getAgent(candidate) != null) {
                if (normalized.contains("请求 " + candidate.toLowerCase() + " 协助")
                        || normalized.contains("优先协调 " + candidate.toLowerCase())
                        || normalized.contains("转交 " + candidate.toLowerCase())
                        || normalized.endsWith(candidate.toLowerCase())
                        || normalized.contains(candidate.toLowerCase() + " 接力")) {
                    agents.add(candidate);
                    continue;
                }
                if (normalized.contains(candidate.toLowerCase() + " 请求")) {
                    continue;
                }
                agents.add(candidate);
            }
        }
        return agents;
    }

    private String buildSubTaskGoal(AutonomousTask task, ReActResult result, String agentName) {
        String baseGoal = result.nextAction() != null && !result.nextAction().isBlank()
                ? result.nextAction().trim()
                : task.getGoal();
        return baseGoal + " -> " + agentName;
    }

    private int computePriority(String agentName, ReActResult result) {
        if ("risk_assessment_agent".equals(agentName)) {
            return 100;
        }
        if (result.nextAction() != null && result.nextAction().contains(agentName)) {
            return 90;
        }
        return 80;
    }

    private String detectAssignedAgent(ReActResult result) {
        if (result.steps() == null || result.steps().isEmpty()) {
            return null;
        }
        return result.steps().stream()
                .map(step -> step.actionName())
                .filter(name -> name != null && subAgentRegistry.getAgent(name) != null)
                .findFirst()
                .orElse(null);
    }

    private SubTask toSubTask(Map<String, Object> payload) {
        if (payload == null) {
            return null;
        }
        String subTaskId = stringValue(payload.get("subTaskId"));
        String goal = stringValue(payload.get("goal"));
        String targetAgent = stringValue(payload.get("targetAgent"));
        if (targetAgent == null || targetAgent.isBlank()) {
            return null;
        }
        int priority = payload.get("priority") instanceof Number number ? number.intValue() : 0;
        SubTask subTask = new SubTask(
                subTaskId == null || subTaskId.isBlank() ? UUID.randomUUID().toString() : subTaskId,
                goal == null ? "" : goal,
                targetAgent,
                priority
        );
        if (payload.get("status") instanceof String statusText) {
            try {
                subTask.setStatus(SubTaskStatus.valueOf(statusText));
            } catch (IllegalArgumentException ignored) {
                subTask.setStatus(SubTaskStatus.PENDING);
            }
        }
        Object dependencies = payload.get("dependencies");
        if (dependencies instanceof List<?> dependencyList) {
            dependencyList.stream().map(this::stringValue).filter(text -> text != null && !text.isBlank()).forEach(subTask.getDependencies()::add);
        }
        return subTask;
    }

    private TaskEvent toTaskEvent(Map<String, Object> payload) {
        if (payload == null || !(payload.get("type") instanceof String typeText)) {
            return null;
        }
        try {
            TaskEventType type = TaskEventType.valueOf(typeText);
            LocalDateTime occurredAt = payload.get("occurredAt") instanceof LocalDateTime value ? value : LocalDateTime.now();
            Map<String, Object> metadata = new LinkedHashMap<>();
            if (payload.get("metadata") instanceof Map<?, ?> rawMetadata) {
                rawMetadata.forEach((key, value) -> {
                    if (key != null) {
                        metadata.put(key.toString(), value);
                    }
                });
            }
            return new TaskEvent(type, occurredAt, stringValue(payload.get("summary")), metadata);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private SupervisorDecision buildDecision(AutonomousTask task, SubTask subTask) {
        SupervisorDecision candidate = supervisorLoop.pickNext(singleSubTaskTask(task, subTask));
        if (candidate != null) {
            return new SupervisorDecision(
                    task.getTaskId(),
                    subTask.getSubTaskId(),
                    subTask.getTargetAgent(),
                    candidate.summary(),
                    supervisorLoop.score(task, subTask)
            );
        }
        return new SupervisorDecision(
                task.getTaskId(),
                subTask.getSubTaskId(),
                subTask.getTargetAgent(),
                "Supervisor 已识别下一个可执行子任务。",
                supervisorLoop.score(task, subTask)
        );
    }

    private AutonomousTask singleSubTaskTask(AutonomousTask task, SubTask subTask) {
        AutonomousTask lightweight = new AutonomousTask(task.getTaskId(), task.getGoal(), task.getOwnerAgent(), task.getPriority());
        lightweight.setAssignedAgent(task.getAssignedAgent());
        lightweight.setStatus(task.getStatus());
        lightweight.getDependencies().addAll(task.getDependencies());
        task.getHistory().forEach(lightweight::addEvent);
        lightweight.addSubTask(subTask);
        return lightweight;
    }

    private Map<String, Object> completionMetadata(SubTask subTask,
                                                   SupervisorDecision decision,
                                                   ChatSubAgent subAgent,
                                                   SubAgentResult subAgentResult) {
        LinkedHashMap<String, Object> metadata = new LinkedHashMap<>(executionMetadata(subTask, decision, subAgent, Map.of()));
        if (subAgentResult != null) {
            metadata.put("confidence", subAgentResult.confidence());
            metadata.put("evidenceRefs", subAgentResult.evidenceRefs());
            metadata.put("artifacts", subAgentResult.artifacts());
            metadata.put("handoffPayload", subAgentResult.handoffPayload());
            metadata.put("nextRequiredCapabilities", subAgentResult.nextRequiredCapabilities());
            metadata.putAll(contractSupport.metadata(subAgentResult));
        }
        return metadata;
    }

    private Map<String, Object> executionMetadata(SubTask subTask,
                                                  SupervisorDecision decision,
                                                  ChatSubAgent subAgent,
                                                  Map<String, Object> extraMetadata) {
        LinkedHashMap<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("subTaskId", subTask.getSubTaskId());
        metadata.put("targetAgent", decision.targetAgent());
        metadata.put("supervisorScore", decision.score());
        if (subAgent != null) {
            metadata.put("resolvedAgentName", subAgent.name());
            metadata.put("resolvedAgentRouteKey", subAgent.routeKey());
            metadata.put("resolvedAgentProvider", subAgent.provider());
        }
        if (extraMetadata != null && !extraMetadata.isEmpty()) {
            metadata.putAll(extraMetadata);
        }
        return metadata;
    }

    private String stringValue(Object value) {
        return value == null ? null : value.toString();
    }

    private void recordDispatchOutcome(String targetAgent,
                                       String resolvedAgentName,
                                       String resolvedAgentProvider,
                                       String outcome) {
        if (platformMetricsService != null) {
            platformMetricsService.recordSubTaskDispatch(targetAgent, resolvedAgentName, resolvedAgentProvider, outcome);
        }
    }

    private SubTaskExecutionOutcome executeSubTask(PendingSubTaskDispatch dispatch) {
        try {
            SubAgentResult subAgentResult = dispatch.subAgent().execute(dispatch.stateSnapshot(), dispatch.subTask().getGoal());
            return new SubTaskExecutionOutcome(
                    dispatch.decision(),
                    dispatch.subTask(),
                    dispatch.subAgent(),
                    subAgentResult,
                    dispatch.stateSnapshot(),
                    null
            );
        } catch (RuntimeException ex) {
            return new SubTaskExecutionOutcome(
                    dispatch.decision(),
                    dispatch.subTask(),
                    dispatch.subAgent(),
                    null,
                    dispatch.stateSnapshot(),
                    ex.getMessage()
            );
        }
    }

    private record PendingSubTaskDispatch(SupervisorDecision decision,
                                          SubTask subTask,
                                          ChatSubAgent subAgent,
                                          AgentState stateSnapshot) {
    }

    private record SubTaskExecutionOutcome(SupervisorDecision decision,
                                           SubTask subTask,
                                           ChatSubAgent subAgent,
                                           SubAgentResult subAgentResult,
                                           AgentState stateSnapshot,
                                           String runtimeError) {
        private String resolvedAgentName() {
            return subAgent == null ? null : subAgent.name();
        }

        private String resolvedAgentRouteKey() {
            return subAgent == null ? null : subAgent.routeKey();
        }

        private String resolvedAgentProvider() {
            return subAgent == null ? null : subAgent.provider();
        }
    }
}
