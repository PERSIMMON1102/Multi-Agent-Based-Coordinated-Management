package com.medical.agent.service.react.orchestration;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class SubTask {

    private final String subTaskId;
    private final String goal;
    private String targetAgent;
    private final int priority;
    private final LocalDateTime createdAt;
    private SubTaskStatus status;
    private final List<String> dependencies = new ArrayList<>();

    public SubTask(String subTaskId, String goal, String targetAgent, int priority) {
        this.subTaskId = subTaskId;
        this.goal = goal;
        this.targetAgent = targetAgent;
        this.priority = priority;
        this.createdAt = LocalDateTime.now();
        this.status = SubTaskStatus.PENDING;
    }

    public String getSubTaskId() {
        return subTaskId;
    }

    public String getGoal() {
        return goal;
    }

    public String getTargetAgent() {
        return targetAgent;
    }

    public void setTargetAgent(String targetAgent) {
        this.targetAgent = targetAgent;
    }

    public int getPriority() {
        return priority;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public SubTaskStatus getStatus() {
        return status;
    }

    public void setStatus(SubTaskStatus status) {
        this.status = status;
    }

    public List<String> getDependencies() {
        return dependencies;
    }
}
