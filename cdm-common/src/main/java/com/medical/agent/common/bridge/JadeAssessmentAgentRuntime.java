package com.medical.agent.common.bridge;

import com.medical.agent.common.message.AgentMessage;

import java.util.function.BiFunction;
import java.util.function.Consumer;

public interface JadeAssessmentAgentRuntime {

    void ensureAssessmentAgentReady(Consumer<AgentMessage> resultCallback,
                                    BiFunction<String, String, String> llmFunction);

    void dispatchAssessmentRequest(AgentMessage request);
}
