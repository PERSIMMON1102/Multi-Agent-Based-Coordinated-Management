package com.medical.agent.service.react.orchestration;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Component
public class SupervisorLoop {

    private static final String PROFILE_AGENT = "patient_profile_agent";
    private static final String RISK_AGENT = "risk_assessment_agent";
    private static final String EVIDENCE_AGENT = "evidence_support_agent";

    public SupervisorDecision pickNext(AutonomousTask task) {
        return candidateSubTasks(task).stream()
                .map(subTask -> toDecision(task, subTask))
                .findFirst()
                .orElse(null);
    }

    public List<SubTask> rankCandidates(AutonomousTask task, int limit) {
        if (task == null || task.getSubTasks().isEmpty()) {
            return List.of();
        }
        return task.getSubTasks().stream()
                .filter(this::isDispatchable)
                .sorted(candidateComparator(task))
                .limit(Math.max(1, limit))
                .toList();
    }

    public int score(AutonomousTask task, SubTask subTask) {
        if (task == null || subTask == null) {
            return Integer.MIN_VALUE;
        }
        int score = 0;
        score += subTask.getPriority() * 6;
        score += subTask.getStatus() == SubTaskStatus.READY ? 70 : 40;
        score += dependencyScore(task, subTask);
        score += goalAlignmentScore(task, subTask);
        score += recoveryRouteOverrideScore(task, subTask);
        score += blockerReliefScore(task, subTask);
        score += recentManualRecoveryScore(task, subTask);
        score += agentContinuityScore(task, subTask);
        score += waitingSupportBoost(task, subTask);
        score -= stalledPenalty(task, subTask);
        score -= agePenalty(subTask);
        return score;
    }

    private List<SubTask> candidateSubTasks(AutonomousTask task) {
        return rankCandidates(task, Integer.MAX_VALUE);
    }

    private SupervisorDecision toDecision(AutonomousTask task, SubTask subTask) {
        int score = score(task, subTask);
        return new SupervisorDecision(
                task.getTaskId(),
                subTask.getSubTaskId(),
                subTask.getTargetAgent(),
                buildDecisionSummary(task, subTask, score),
                score
        );
    }

    private Comparator<SubTask> candidateComparator(AutonomousTask task) {
        return Comparator
                .comparingInt((SubTask subTask) -> score(task, subTask))
                .reversed()
                .thenComparing(SubTask::getCreatedAt);
    }

    private boolean isDispatchable(SubTask subTask) {
        return subTask != null
                && (subTask.getStatus() == SubTaskStatus.PENDING || subTask.getStatus() == SubTaskStatus.READY);
    }

    private int dependencyScore(AutonomousTask task, SubTask subTask) {
        if (subTask.getDependencies().isEmpty()) {
            return 28;
        }
        long completedDependencies = subTask.getDependencies().stream()
                .filter(dependency -> isDependencySatisfied(task, dependency))
                .count();
        if (completedDependencies == subTask.getDependencies().size()) {
            return 44;
        }
        if (completedDependencies > 0) {
            return 12;
        }
        return -20;
    }

    private boolean isDependencySatisfied(AutonomousTask task, String dependency) {
        if (dependency == null || dependency.isBlank() || task == null) {
            return false;
        }
        return task.getSubTasks().stream().anyMatch(item ->
                dependency.equals(item.getSubTaskId())
                        && item.getStatus() == SubTaskStatus.COMPLETED
        );
    }

    private int goalAlignmentScore(AutonomousTask task, SubTask subTask) {
        String taskGoal = normalize(task.getGoal());
        String subGoal = normalize(subTask.getGoal());
        String agent = normalize(subTask.getTargetAgent());
        int score = 0;
        if (containsAny(taskGoal, "风险", "高危", "评估", "risk")) {
            if (RISK_AGENT.equals(agent)) {
                score += 40;
            }
            if (containsAny(subGoal, "风险", "分层", "评估", "risk")) {
                score += 18;
            }
        }
        if (containsAny(taskGoal, "画像", "病史", "资料", "context", "profile", "患者")) {
            if (PROFILE_AGENT.equals(agent)) {
                score += 36;
            }
            if (containsAny(subGoal, "画像", "病史", "上下文", "profile", "context")) {
                score += 18;
            }
        }
        if (containsAny(taskGoal, "证据", "指南", "文献", "知识图谱", "evidence")) {
            if (EVIDENCE_AGENT.equals(agent)) {
                score += 36;
            }
            if (containsAny(subGoal, "证据", "指南", "文献", "知识图谱", "evidence")) {
                score += 18;
            }
        }
        return score;
    }

    private int blockerReliefScore(AutonomousTask task, SubTask subTask) {
        if (task == null || task.getHistory().isEmpty()) {
            return 0;
        }
        String agent = normalize(subTask.getTargetAgent());
        String goal = normalize(subTask.getGoal());
        Optional<TaskEvent> latestIssue = latestIssueEvent(task, subTask);
        if (latestIssue.isEmpty()) {
            return 0;
        }
        Map<String, Object> metadata = latestIssue.get().metadata() == null ? Map.of() : latestIssue.get().metadata();
        String failureCategory = normalize(stringValue(metadata.get("failureCategory")));
        List<String> missingCapabilities = metadata.get("missingCapabilities") instanceof List<?> items
                ? items.stream().map(Object::toString).map(this::normalize).toList()
                : List.of();
        int score = 0;
        if ("context_gap".equals(failureCategory) && PROFILE_AGENT.equals(agent)) {
            score += 45;
        }
        if ("context_gap".equals(failureCategory) && RISK_AGENT.equals(agent)) {
            score -= 28;
        }
        if ("evidence_gap".equals(failureCategory) && EVIDENCE_AGENT.equals(agent)) {
            score += 45;
        }
        if ("evidence_gap".equals(failureCategory) && PROFILE_AGENT.equals(agent)) {
            score -= 16;
        }
        if ("risk_reassessment".equals(failureCategory) && RISK_AGENT.equals(agent)) {
            score += 45;
        }
        if ("risk_reassessment".equals(failureCategory) && PROFILE_AGENT.equals(agent)) {
            score -= 12;
        }
        if (missingCapabilities.stream().anyMatch(item -> item.contains("context") || item.contains("profile") || item.contains("画像"))
                && PROFILE_AGENT.equals(agent)) {
            score += 38;
        }
        if (missingCapabilities.stream().anyMatch(item -> item.contains("context") || item.contains("profile") || item.contains("画像"))
                && RISK_AGENT.equals(agent)) {
            score -= 18;
        }
        if (missingCapabilities.stream().anyMatch(item -> item.contains("evidence") || item.contains("guideline") || item.contains("证据"))
                && EVIDENCE_AGENT.equals(agent)) {
            score += 22;
        }
        if (missingCapabilities.stream().anyMatch(item -> item.contains("risk") || item.contains("assessment") || item.contains("风险"))
                && RISK_AGENT.equals(agent)) {
            score += 22;
        }
        if (containsAny(goal, "接力", "补充", "恢复", "重评")) {
            score += 10;
        }
        return score;
    }

    private int recoveryRouteOverrideScore(AutonomousTask task, SubTask subTask) {
        if (task == null || subTask == null) {
            return 0;
        }
        Optional<TaskEvent> latestIssue = task.getHistory().stream()
                .filter(event -> event != null && (event.type() == TaskEventType.BLOCKED || event.type() == TaskEventType.FAILED))
                .max(Comparator.comparing(TaskEvent::occurredAt, Comparator.nullsLast(LocalDateTime::compareTo)));
        Optional<TaskEvent> latestManualRecovery = task.getHistory().stream()
                .filter(event -> event != null && isManualRecoveryEvent(event.type()))
                .max(Comparator.comparing(TaskEvent::occurredAt, Comparator.nullsLast(LocalDateTime::compareTo)));
        if (latestIssue.isEmpty() || latestManualRecovery.isEmpty()) {
            return 0;
        }
        Map<String, Object> issueMetadata = latestIssue.get().metadata() == null ? Map.of() : latestIssue.get().metadata();
        String failureCategory = normalize(stringValue(issueMetadata.get("failureCategory")));
        String recoveredAgent = normalize(stringValue(latestManualRecovery.get().metadata() == null ? null : latestManualRecovery.get().metadata().get("targetAgent")));
        if ("context_gap".equals(failureCategory)
                && PROFILE_AGENT.equals(normalize(subTask.getTargetAgent()))
                && PROFILE_AGENT.equals(recoveredAgent)) {
            return 140;
        }
        if ("evidence_gap".equals(failureCategory)
                && EVIDENCE_AGENT.equals(normalize(subTask.getTargetAgent()))
                && EVIDENCE_AGENT.equals(recoveredAgent)) {
            return 120;
        }
        return 0;
    }

    private Optional<TaskEvent> latestIssueEvent(AutonomousTask task, SubTask subTask) {
        return task.getHistory().stream()
                .filter(event -> event != null && (event.type() == TaskEventType.BLOCKED || event.type() == TaskEventType.FAILED))
                .filter(event -> matchesSubTask(event, subTask))
                .max(Comparator.comparing(TaskEvent::occurredAt, Comparator.nullsLast(LocalDateTime::compareTo)));
    }

    private int recentManualRecoveryScore(AutonomousTask task, SubTask subTask) {
        if (task == null || task.getHistory().isEmpty()) {
            return 0;
        }
        Optional<TaskEvent> latestControlEvent = task.getHistory().stream()
                .filter(event -> event != null && isManualRecoveryEvent(event.type()))
                .filter(event -> matchesSubTask(event, subTask))
                .max(Comparator.comparing(TaskEvent::occurredAt, Comparator.nullsLast(LocalDateTime::compareTo)));
        if (latestControlEvent.isEmpty()) {
            return 0;
        }
        long minutes = minutesSince(latestControlEvent.get().occurredAt());
        int freshnessBoost = minutes <= 10 ? 36 : minutes <= 60 ? 18 : 8;
        if (latestControlEvent.get().type() == TaskEventType.SUBTASK_HANDOFF) {
            freshnessBoost += 12;
        }
        if (latestControlEvent.get().type() == TaskEventType.SUBTASK_RESET) {
            freshnessBoost += 8;
        }
        return freshnessBoost;
    }

    private boolean isManualRecoveryEvent(TaskEventType type) {
        return type == TaskEventType.SUBTASK_HANDOFF
                || type == TaskEventType.SUBTASK_RESET
                || type == TaskEventType.SUBTASK_SKIPPED
                || type == TaskEventType.MANUAL_REVIEW_ACKED
                || type == TaskEventType.MANUAL_RESUMED;
    }

    private int agentContinuityScore(AutonomousTask task, SubTask subTask) {
        if (task == null || subTask == null) {
            return 0;
        }
        String assignedAgent = normalize(task.getAssignedAgent());
        String targetAgent = normalize(subTask.getTargetAgent());
        if (!assignedAgent.isBlank() && assignedAgent.equals(targetAgent)) {
            return -10;
        }
        long sameAgentPending = task.getSubTasks().stream()
                .filter(item -> item != subTask)
                .filter(this::isDispatchable)
                .filter(item -> targetAgent.equals(normalize(item.getTargetAgent())))
                .count();
        return sameAgentPending > 0 ? -8 : 6;
    }

    private int waitingSupportBoost(AutonomousTask task, SubTask subTask) {
        if (task == null || task.getStatus() != TaskStatus.WAITING_SUPPORT) {
            return 0;
        }
        String nextAction = normalize(latestNextAction(task));
        String agent = normalize(subTask.getTargetAgent());
        if (!nextAction.isBlank() && nextAction.contains(agent)) {
            return 26;
        }
        return 14;
    }

    private String latestNextAction(AutonomousTask task) {
        return task.getHistory().stream()
                .filter(event -> event != null && event.metadata() != null)
                .map(TaskEvent::metadata)
                .map(metadata -> metadata.get("nextAction"))
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .reduce((first, second) -> second)
                .orElse(task.getGoal());
    }

    private int stalledPenalty(AutonomousTask task, SubTask subTask) {
        if (task == null || subTask == null) {
            return 0;
        }
        long failedAttempts = task.getHistory().stream()
                .filter(event -> event != null && (event.type() == TaskEventType.BLOCKED || event.type() == TaskEventType.FAILED))
                .filter(event -> matchesSubTask(event, subTask))
                .count();
        return (int) Math.min(48, failedAttempts * 16);
    }

    private int agePenalty(SubTask subTask) {
        long hours = Duration.between(subTask.getCreatedAt(), LocalDateTime.now()).toHours();
        return (int) Math.min(18, Math.max(0, hours));
    }

    private boolean matchesSubTask(TaskEvent event, SubTask subTask) {
        if (event == null || subTask == null || event.metadata() == null) {
            return false;
        }
        Object subTaskId = event.metadata().get("subTaskId");
        if (subTaskId != null && subTask.getSubTaskId().equals(subTaskId.toString())) {
            return true;
        }
        Object targetAgent = event.metadata().get("targetAgent");
        return targetAgent != null && normalize(subTask.getTargetAgent()).equals(normalize(targetAgent.toString()));
    }

    private long minutesSince(LocalDateTime occurredAt) {
        if (occurredAt == null) {
            return Long.MAX_VALUE;
        }
        return Math.max(0, Duration.between(occurredAt, LocalDateTime.now()).toMinutes());
    }

    private String buildDecisionSummary(AutonomousTask task, SubTask subTask, int score) {
        StringBuilder summary = new StringBuilder("Supervisor 已按目标驱动评分选择子任务。");
        summary.append(" target=").append(subTask.getTargetAgent());
        summary.append(" score=").append(score);
        if (!subTask.getDependencies().isEmpty()) {
            summary.append(" deps=").append(subTask.getDependencies().size());
        }
        if (task != null && task.getStatus() == TaskStatus.WAITING_SUPPORT) {
            summary.append(" waitingSupportBoost");
        }
        return summary.toString();
    }

    private String stringValue(Object value) {
        return value == null ? null : value.toString();
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private boolean containsAny(String text, String... patterns) {
        if (text == null || text.isBlank()) {
            return false;
        }
        for (String pattern : patterns) {
            if (text.contains(pattern.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }
}
