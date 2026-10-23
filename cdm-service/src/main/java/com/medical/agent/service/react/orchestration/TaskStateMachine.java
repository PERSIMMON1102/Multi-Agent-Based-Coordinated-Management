package com.medical.agent.service.react.orchestration;

import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;

@Component
public class TaskStateMachine {

    public void markPlanning(AutonomousTask task, String summary) {
        transition(task, TaskStatus.PLANNING, TaskEventType.PLAN_STARTED, summary, Map.of());
    }

    public void markRunning(AutonomousTask task, String assignedAgent, String summary) {
        task.setAssignedAgent(assignedAgent);
        transition(task, TaskStatus.RUNNING, TaskEventType.EXECUTION_STARTED, summary, Map.of("assignedAgent", assignedAgent));
    }

    public void markWaitingSupport(AutonomousTask task, String summary, Map<String, Object> metadata) {
        transition(task, TaskStatus.WAITING_SUPPORT, TaskEventType.SUPPORT_REQUESTED, summary, metadata);
    }

    public void markBlocked(AutonomousTask task, String summary, Map<String, Object> metadata) {
        transition(task, TaskStatus.BLOCKED, TaskEventType.BLOCKED, summary, metadata);
    }

    public void markCompleted(AutonomousTask task, String summary) {
        transition(task, TaskStatus.COMPLETED, TaskEventType.COMPLETED, summary, Map.of());
    }

    public void markFailed(AutonomousTask task, String summary) {
        transition(task, TaskStatus.FAILED, TaskEventType.FAILED, summary, Map.of());
    }

    public void markSubTaskReady(SubTask subTask) {
        if (subTask != null) {
            subTask.setStatus(SubTaskStatus.READY);
        }
    }

    public void markSubTaskRunning(SubTask subTask) {
        if (subTask != null) {
            subTask.setStatus(SubTaskStatus.RUNNING);
        }
    }

    public void markSubTaskBlocked(SubTask subTask) {
        if (subTask != null) {
            subTask.setStatus(SubTaskStatus.BLOCKED);
        }
    }

    public void markSubTaskCompleted(SubTask subTask) {
        if (subTask != null) {
            subTask.setStatus(SubTaskStatus.COMPLETED);
        }
    }

    public void markSubTaskSkipped(SubTask subTask) {
        if (subTask != null) {
            subTask.setStatus(SubTaskStatus.SKIPPED);
        }
    }

    public void markSubTaskFailed(SubTask subTask) {
        if (subTask != null) {
            subTask.setStatus(SubTaskStatus.FAILED);
        }
    }

    private void transition(AutonomousTask task,
                            TaskStatus status,
                            TaskEventType eventType,
                            String summary,
                            Map<String, Object> metadata) {
        if (task == null) {
            return;
        }
        task.setStatus(status);
        task.addEvent(new TaskEvent(eventType, LocalDateTime.now(), summary, metadata == null ? Map.of() : metadata));
    }
}
