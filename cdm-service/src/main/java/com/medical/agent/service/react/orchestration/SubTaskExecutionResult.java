package com.medical.agent.service.react.orchestration;

import com.medical.agent.service.react.agent.SubAgentResult;
import com.medical.agent.service.react.model.AgentState;

public record SubTaskExecutionResult(SupervisorDecision decision,
                                     SubTask subTask,
                                     SubAgentResult subAgentResult,
                                     boolean success,
                                     String summary,
                                     AgentState stateSnapshot,
                                     String resolvedAgentName,
                                     String resolvedAgentRouteKey,
                                     String resolvedAgentProvider) {
}
