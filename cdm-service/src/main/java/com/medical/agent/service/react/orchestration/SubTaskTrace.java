package com.medical.agent.service.react.orchestration;

import java.util.List;

public record SubTaskTrace(String subTaskId,
                           String goal,
                           String targetAgent,
                           int priority,
                           SubTaskStatus status,
                           List<String> dependencies) {
}
