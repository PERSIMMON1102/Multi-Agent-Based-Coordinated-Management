package com.medical.agent.service.react.tool;

import com.medical.agent.service.react.model.AgentState;

import java.util.Map;

public interface AgentTool {

    String name();

    String description();

    ToolExecutionResult execute(AgentState state, Map<String, Object> input);
}
