package com.medical.agent.agent.runtime;

import com.medical.agent.agent.AssessmentAgent;
import com.medical.agent.agent.config.JadeConfig;
import com.medical.agent.common.bridge.JadeAssessmentAgentRuntime;
import com.medical.agent.common.message.AgentMessage;
import jade.wrapper.AgentContainer;
import jade.wrapper.AgentController;
import jade.wrapper.StaleProxyException;
import org.kie.api.runtime.KieContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.function.BiFunction;
import java.util.function.Consumer;

@Component
@ConditionalOnProperty(name = "jade.enabled", havingValue = "true")
public class DefaultJadeAssessmentAgentRuntime implements JadeAssessmentAgentRuntime {

    private static final Logger log = LoggerFactory.getLogger(DefaultJadeAssessmentAgentRuntime.class);

    private final AgentContainer mainContainer;
    private final JadeConfig jadeConfig;
    private final KieContainer kieContainer;

    private AgentController assessmentAgent;
    private boolean initialized;

    public DefaultJadeAssessmentAgentRuntime(AgentContainer mainContainer,
                                             JadeConfig jadeConfig,
                                             KieContainer kieContainer) {
        this.mainContainer = mainContainer;
        this.jadeConfig = jadeConfig;
        this.kieContainer = kieContainer;
    }

    @Override
    public synchronized void ensureAssessmentAgentReady(Consumer<AgentMessage> resultCallback,
                                                        BiFunction<String, String, String> llmFunction) {
        if (initialized) {
            return;
        }
        assessmentAgent = jadeConfig.createAgent(
                mainContainer,
                "AssessmentAgent",
                AssessmentAgent.class.getName(),
                new Object[]{
                        kieContainer,
                        resultCallback,
                        llmFunction
                }
        );
        initialized = assessmentAgent != null;
        log.info("Assessment JADE agent initialized successfully");
    }

    @Override
    public void dispatchAssessmentRequest(AgentMessage request) {
        if (assessmentAgent == null) {
            throw new IllegalStateException("AssessmentAgent not initialized");
        }
        try {
            assessmentAgent.putO2AObject(request, AgentController.ASYNC);
        } catch (StaleProxyException e) {
            throw new RuntimeException("Failed to dispatch assessment request to JADE agent", e);
        }
    }
}
