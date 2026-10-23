package com.medical.agent.service.react.orchestration;

public record SupervisorDecision(String taskId,
                                 String subTaskId,
                                 String targetAgent,
                                 String summary,
                                 int score) {

    public SupervisorDecision(String taskId,
                              String subTaskId,
                              String targetAgent,
                              String summary) {
        this(taskId, subTaskId, targetAgent, summary, 0);
    }
}
