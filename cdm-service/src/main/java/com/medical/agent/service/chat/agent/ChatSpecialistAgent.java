package com.medical.agent.service.chat.agent;

import com.medical.agent.service.react.model.AgentState;

public interface ChatSpecialistAgent {

    String name();

    int order();

    boolean shouldEngage(AgentState state);

    ChatSpecialistExecutionResult execute(AgentState state);
}
