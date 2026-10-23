package com.medical.agent.service.react.orchestration;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class AutonomousTask {

    private final String taskId;
    private final String goal;
    private final String ownerAgent;
    private final int priority;
    private final LocalDateTime createdAt;
    private TaskStatus status;
    private String assignedAgent;
    private final List<String> dependencies = new ArrayList<>();
    private final List<SubTask> subTasks = new ArrayList<>();
    private final List<TaskEvent> history = new ArrayList<>();

    public AutonomousTask(String taskId, String goal, String ownerAgent, int priority) {
        this.taskId = taskId;
        this.goal = goal;
        this.ownerAgent = ownerAgent;
        this.priority = priority;
        this.createdAt = LocalDateTime.now();
        this.status = TaskStatus.CREATED;
    }

    public String getTaskId() {
        return taskId;
    }

    public String getGoal() {
        return goal;
    }

    public String getOwnerAgent() {
        return ownerAgent;
    }

    public int getPriority() {
        return priority;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public TaskStatus getStatus() {
        return status;
    }

    public void setStatus(TaskStatus status) {
        this.status = status;
    }

    public String getAssignedAgent() {
        return assignedAgent;
    }

    public void setAssignedAgent(String assignedAgent) {
        this.assignedAgent = assignedAgent;
    }

    public List<String> getDependencies() {
        return dependencies;
    }

    public List<SubTask> getSubTasks() {
        return subTasks;
    }

    public List<TaskEvent> getHistory() {
        return history;
    }

    public void addEvent(TaskEvent event) {
        if (event != null) {
            history.add(event);
        }
    }

    public void addSubTask(SubTask subTask) {
        if (subTask != null) {
            subTasks.add(subTask);
        }
    }
}
