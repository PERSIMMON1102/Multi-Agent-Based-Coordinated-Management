package com.medical.agent.service.react.orchestration;

import java.util.List;

public record OrchestrationTrace(String taskId,
                                 String goal,
                                 TaskStatus status,
                                 String assignedAgent,
                                 List<String> dependencies,
                                 List<SubTaskTrace> subTasks,
                                 List<TaskEvent> events,
                                 SupervisorDecision nextDecision) {
}
