package com.medical.agent.service;

import com.medical.agent.common.bridge.JadeAssessmentAgentRuntime;
import com.medical.agent.common.message.AgentMessage;
import com.medical.agent.service.cache.AiChatCacheVersionService;
import com.medical.agent.service.cache.AiChatCacheInvalidationService;
import org.junit.jupiter.api.Test;
import org.springframework.cache.support.NoOpCacheManager;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiFunction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentBridgeServiceTest {

    @Test
    void initAgentsShouldInjectNullLlmFunctionsWhenLlmIsUnavailable() {
        RecordingRuntime runtime = new RecordingRuntime();
        JadeAssessmentAgentLifecycleService lifecycleService =
                new JadeAssessmentAgentLifecycleService(runtime, new DisabledLlmService());
        AgentBridgeService service = new AgentBridgeService(
                lifecycleService,
                null,
                null,
                null
        );

        service.initAgents();

        assertEquals(1, runtime.ensureCalls);
        assertNull(runtime.llmFunction);
    }

    @Test
    void initAgentsShouldInjectRoleSpecificLlmFunctionsWhenLlmIsAvailable() {
        RecordingRuntime runtime = new RecordingRuntime();
        RecordingLlmService llmService = new RecordingLlmService();
        JadeAssessmentAgentLifecycleService lifecycleService =
                new JadeAssessmentAgentLifecycleService(runtime, llmService);
        AgentBridgeService service = new AgentBridgeService(
                lifecycleService,
                null,
                null,
                null
        );

        service.initAgents();

        assertEquals(1, runtime.ensureCalls);
        assertNotNull(runtime.llmFunction);
        assertEquals("慢病风险评估智能体|ctx-a|task-a", runtime.llmFunction.apply("ctx-a", "task-a"));
        assertEquals(
                java.util.List.of("慢病风险评估智能体"),
                llmService.roles
        );
    }

    @Test
    void warmupShouldSkipEagerAgentStartupWhenReactIsDefaultProvider() {
        RecordingRuntime runtime = new RecordingRuntime();
        JadeAssessmentAgentLifecycleService lifecycleService =
                new JadeAssessmentAgentLifecycleService(runtime, new DisabledLlmService());
        AgentBridgeService service = new AgentBridgeService(
                lifecycleService,
                null,
                null,
                null
        );
        ReflectionTestUtils.setField(lifecycleService, "clinicalAssessmentProvider", "react");

        service.warmupAgentsIfConfigured();

        assertEquals(0, runtime.ensureCalls);
    }

    private static class RecordingRuntime implements JadeAssessmentAgentRuntime {
        private int ensureCalls;
        private BiFunction<String, String, String> llmFunction;
        @Override
        public void ensureAssessmentAgentReady(java.util.function.Consumer<AgentMessage> resultCallback,
                                               BiFunction<String, String, String> llmFunction) {
            ensureCalls++;
            this.llmFunction = llmFunction;
        }

        @Override
        public void dispatchAssessmentRequest(AgentMessage request) {
        }
    }

    private static class DisabledLlmService extends LlmService {
        @Override
        public boolean isAvailable() {
            return false;
        }
    }

    private static class RecordingLlmService extends LlmService {
        private final java.util.List<String> roles = new java.util.ArrayList<>();

        @Override
        public boolean isAvailable() {
            return true;
        }

        @Override
        public String agentAnalyze(String agentRole, String patientContext, String task) {
            roles.add(agentRole);
            return agentRole + "|" + patientContext + "|" + task;
        }
    }
}
