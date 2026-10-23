package com.medical.agent.service;

import com.medical.agent.common.bridge.JadeAssessmentAgentRuntime;
import com.medical.agent.common.message.AgentMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.function.BiFunction;
import java.util.function.Consumer;

@Service
@ConditionalOnProperty(name = "jade.enabled", havingValue = "true")
public class JadeAssessmentAgentLifecycleService {

    private static final Logger log = LoggerFactory.getLogger(JadeAssessmentAgentLifecycleService.class);

    private final JadeAssessmentAgentRuntime jadeAssessmentAgentRuntime;
    private final LlmService llmService;

    @Value("${clinical.assessment.provider:react}")
    private String clinicalAssessmentProvider = "react";

    public JadeAssessmentAgentLifecycleService(JadeAssessmentAgentRuntime jadeAssessmentAgentRuntime,
                                               LlmService llmService) {
        this.jadeAssessmentAgentRuntime = jadeAssessmentAgentRuntime;
        this.llmService = llmService;
    }

    public void warmupIfConfigured(Consumer<AgentMessage> resultCallback) {
        if (shouldAutoStartAgents()) {
            ensureAssessmentAgentReady(resultCallback);
            return;
        }
        log.info("JADE bridge enabled in standby mode; skipping eager agent startup because clinical.assessment.provider={}",
                clinicalAssessmentProvider);
    }

    public synchronized void ensureAssessmentAgentReady(Consumer<AgentMessage> resultCallback) {
        if (!llmService.isAvailable()) {
            log.info("LLM service not available, agents will use template-based logic");
        } else {
            log.info("LLM service available, agents will use role-specific LLM enhancement");
        }
        jadeAssessmentAgentRuntime.ensureAssessmentAgentReady(resultCallback, buildLlmFunction("慢病风险评估智能体"));
    }

    public void dispatchAssessmentRequest(Consumer<AgentMessage> resultCallback, AgentMessage request) {
        ensureAssessmentAgentReady(resultCallback);
        jadeAssessmentAgentRuntime.dispatchAssessmentRequest(request);
    }

    private BiFunction<String, String, String> buildLlmFunction(String agentRole) {
        if (!llmService.isAvailable()) {
            return null;
        }
        return (context, task) -> llmService.agentAnalyze(agentRole, context, task);
    }

    private boolean shouldAutoStartAgents() {
        return "jade".equalsIgnoreCase(clinicalAssessmentProvider);
    }
}
