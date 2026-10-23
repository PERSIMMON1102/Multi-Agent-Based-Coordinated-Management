package com.medical.agent.service.react;

import com.fasterxml.jackson.databind.JsonNode;
import com.medical.agent.service.LlmService;
import com.medical.agent.service.react.agent.ChatSubAgent;
import com.medical.agent.service.react.agent.SubAgentRegistry;
import com.medical.agent.service.react.agent.SubAgentResult;
import com.medical.agent.service.react.model.AgentState;
import com.medical.agent.service.react.model.AgentStep;
import com.medical.agent.service.react.model.ReActResult;
import com.medical.agent.service.react.tool.AgentTool;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReActAgentExecutorTest {

    @Test
    void shouldDelegateToSubAgentBeforeFinalAnswer() {
        AtomicInteger agentCalls = new AtomicInteger();
        ChatSubAgent patientAgent = subAgent(
                "patient_profile_agent",
                state -> {
                    agentCalls.incrementAndGet();
                    state.addSafetyFlag("patient_context_loaded");
                    return new SubAgentResult("patient_profile_agent", true, "已读取患者上下文。", List.of("patient_context"));
                }
        );
        SubAgentRegistry subAgentRegistry = new SubAgentRegistry(List.of(patientAgent));
        SequenceLlmService llmService = new SequenceLlmService(
                "{\"type\":\"delegate\",\"agentName\":\"patient_profile_agent\",\"task\":\"补充患者基础资料\",\"reasoningSummary\":\"先补充患者上下文\"}",
                "{\"type\":\"final_answer\",\"answerPlan\":\"结合患者上下文回答\",\"reasoningSummary\":\"信息已足够\"}",
                "这是最终回答。"
        );
        llmService.setEnabledForTest(true);

        ReActAgentExecutor executor = new ReActAgentExecutor(subAgentRegistry, llmService, new ReActPromptBuilder());
        AgentState state = new AgentState();
        state.setOwnerRole("patient");
        state.setPatientId(10L);
        state.setUserMessage("我最近血压偏高怎么办？");

        ReActResult result = executor.execute(state);

        assertNotNull(result);
        assertEquals(1, agentCalls.get());
        assertEquals(List.of("patient_context"), result.usedTools());
        assertEquals(1, result.steps().size());
        assertEquals("patient_profile_agent", result.steps().get(0).actionName());
        assertEquals("结合患者上下文回答", state.getAnswerPlan());
        assertEquals("这是最终回答。", result.reply());
        assertTrue(result.safetyFlags().contains("patient_context_loaded"));
    }

    @Test
    void shouldStopWhenSubAgentRepeatLimitReached() {
        AtomicInteger agentCalls = new AtomicInteger();
        ChatSubAgent riskAgent = subAgent(
                "risk_assessment_agent",
                state -> {
                    agentCalls.incrementAndGet();
                    state.addSafetyFlag("high_risk_detected");
                    return new SubAgentResult("risk_assessment_agent", true, "已完成规则评估。", List.of("risk_assessment"));
                }
        );
        SubAgentRegistry subAgentRegistry = new SubAgentRegistry(List.of(riskAgent));
        SequenceLlmService llmService = new SequenceLlmService(
                "{\"type\":\"delegate\",\"agentName\":\"risk_assessment_agent\",\"task\":\"第一次评估\",\"reasoningSummary\":\"第一次评估\"}",
                "{\"type\":\"delegate\",\"agentName\":\"risk_assessment_agent\",\"task\":\"第二次评估\",\"reasoningSummary\":\"第二次评估\"}",
                "{\"type\":\"delegate\",\"agentName\":\"risk_assessment_agent\",\"task\":\"第三次评估\",\"reasoningSummary\":\"第三次评估\"}",
                "评估显示风险较高，请及时线下就医。"
        );
        llmService.setEnabledForTest(true);

        ReActAgentExecutor executor = new ReActAgentExecutor(subAgentRegistry, llmService, new ReActPromptBuilder());
        AgentState state = new AgentState();
        state.setOwnerRole("patient");
        state.setPatientId(11L);
        state.setUserMessage("我的情况严不严重？");

        ReActResult result = executor.execute(state);

        assertNotNull(result);
        assertEquals(1, agentCalls.get());
        assertTrue(result.steps().stream().anyMatch(step -> "replan".equals(step.actionName())));
        assertTrue(result.steps().stream().anyMatch(step -> String.valueOf(step.observationSummary()).contains("重复委派次数已达上限")));
        assertTrue(result.reply().contains("及时线下就医"));
        assertTrue(result.safetyFlags().contains("high_risk_detected"));
    }

    @Test
    void shouldCooldownRecentlyFailedSubAgent() {
        AtomicInteger agentCalls = new AtomicInteger();
        ChatSubAgent patientAgent = subAgent(
                "patient_profile_agent",
                state -> {
                    agentCalls.incrementAndGet();
                    throw new RuntimeException("外部画像源暂不可用");
                }
        );
        SubAgentRegistry subAgentRegistry = new SubAgentRegistry(List.of(patientAgent));
        SequenceLlmService llmService = new SequenceLlmService(
                "{\"type\":\"delegate\",\"agentName\":\"patient_profile_agent\",\"task\":\"第一次读取画像\",\"reasoningSummary\":\"先读取患者画像\"}",
                "{\"type\":\"delegate\",\"agentName\":\"patient_profile_agent\",\"task\":\"第二次读取画像\",\"reasoningSummary\":\"再次尝试读取患者画像\"}",
                "当前先基于已有信息回答。"
        );
        llmService.setEnabledForTest(true);

        ReActAgentExecutor executor = new ReActAgentExecutor(subAgentRegistry, llmService, new ReActPromptBuilder());
        AgentState state = new AgentState();
        state.setOwnerRole("doctor");
        state.setPatientId(12L);
        state.setUserMessage("先帮我补一下患者画像。");

        ReActResult result = executor.execute(state);

        assertNotNull(result);
        assertEquals(1, agentCalls.get());
        assertEquals("patient_profile_agent", result.steps().get(0).actionName());
        assertTrue(result.steps().stream().anyMatch(step -> "replan".equals(step.actionName())));
        assertTrue(result.steps().stream().anyMatch(step -> String.valueOf(step.observationSummary()).contains("冷却中")));
        assertTrue(state.getBlockers().stream().anyMatch(item -> item.contains("最近执行失败")));
        assertNotNull(result.reply());
        assertFalse(result.reply().isBlank());
    }

    @Test
    void shouldMergeSubAgentCollaborationSignals() {
        AtomicInteger patientCalls = new AtomicInteger();
        ChatSubAgent patientAgent = subAgent(
                "patient_profile_agent",
                state -> {
                    patientCalls.incrementAndGet();
                    return new SubAgentResult(
                            "patient_profile_agent",
                            true,
                            false,
                            "已补齐基础画像，但还缺风险判断。",
                            List.of("patient_context"),
                            "需要风险判断接力。",
                            List.of("仍缺风险结论"),
                            List.of("risk_assessment_agent"),
                            "risk_assessment_agent",
                            "risk_reassessment",
                            List.of("risk_assessment"),
                            0.42D,
                            List.of("patient_context:profile"),
                            List.of(Map.of("kind", "profile_summary", "summary", "已补齐基础画像")),
                            Map.of("patientSnapshot", "profile-ready"),
                            List.of("risk_assessment"),
                            "sub-agent-result",
                            "1.0",
                            Map.of("schemaCompatible", true, "versionCompatible", true, "validated", true),
                            List.of()
                    );
                }
        );
        ChatSubAgent riskAgent = subAgent(
                "risk_assessment_agent",
                state -> new SubAgentResult("risk_assessment_agent", true, "已完成风险评估。", List.of("risk_assessment"))
        );
        SubAgentRegistry subAgentRegistry = new SubAgentRegistry(List.of(patientAgent, riskAgent));
        SequenceLlmService llmService = new SequenceLlmService(
                "{\"type\":\"delegate\",\"agentName\":\"patient_profile_agent\",\"task\":\"先补患者画像\",\"reasoningSummary\":\"先看患者资料\"}",
                "{\"type\":\"final_answer\",\"answerPlan\":\"先说明已完成部分，再提示后续应补风险评估\",\"reasoningSummary\":\"当前先结束\"}",
                "先基于现有画像给出阶段性说明。"
        );
        llmService.setEnabledForTest(true);

        ReActAgentExecutor executor = new ReActAgentExecutor(subAgentRegistry, llmService, new ReActPromptBuilder());
        AgentState state = new AgentState();
        state.setOwnerRole("doctor");
        state.setPatientId(13L);
        state.setUserMessage("请先分析这个患者。");

        ReActResult result = executor.execute(state);

        assertNotNull(result);
        assertEquals(1, patientCalls.get());
        assertTrue(state.getBlockers().stream().anyMatch(item -> item.contains("尚未完成本职任务")));
        assertTrue(state.getBlockers().stream().anyMatch(item -> item.contains("请求 risk_assessment_agent 协助")));
        assertTrue(state.getBlockers().stream().anyMatch(item -> item.contains("建议转交 risk_assessment_agent")));
        assertTrue(state.getBlockers().stream().anyMatch(item -> item.contains("当前结论置信度偏低")));
        assertTrue(state.getBlockers().stream().anyMatch(item -> item.contains("仍缺能力")));
        assertEquals("下一步优先转交 risk_assessment_agent 继续处理。 当前交接置信度 42%。", state.getNextAction());
        assertTrue(result.reply().contains("阶段性说明"));
    }

    @Test
    void shouldKeepContractMetadataOnSubAgentResult() {
        ChatSubAgent patientAgent = subAgent(
                "patient_profile_agent",
                state -> new SubAgentResult(
                        "patient_profile_agent",
                        true,
                        false,
                        "已补齐基础画像，但还缺风险判断。",
                        List.of("patient_context"),
                        "需要风险判断接力。",
                        List.of("仍缺风险结论"),
                        List.of("risk_assessment_agent"),
                        "risk_assessment_agent",
                        "risk_reassessment",
                        List.of("risk_assessment"),
                        0.42D,
                        List.of("patient_context:profile"),
                        List.of(Map.of("kind", "profile_summary", "summary", "已补齐基础画像")),
                        Map.of("patientSnapshot", "profile-ready"),
                        List.of("risk_assessment"),
                        "sub-agent-result",
                        "1.0",
                        Map.of("schemaCompatible", true, "versionCompatible", true, "validated", true),
                        List.of()
                )
        );
        SubAgentRegistry subAgentRegistry = new SubAgentRegistry(List.of(patientAgent));
        SequenceLlmService llmService = new SequenceLlmService(
                "{\"type\":\"delegate\",\"agentName\":\"patient_profile_agent\",\"task\":\"先补患者画像\",\"reasoningSummary\":\"先看患者资料\"}",
                "{\"type\":\"final_answer\",\"answerPlan\":\"阶段性结束\",\"reasoningSummary\":\"当前先结束\"}",
                "阶段性说明。"
        );
        llmService.setEnabledForTest(true);

        ReActAgentExecutor executor = new ReActAgentExecutor(subAgentRegistry, llmService, new ReActPromptBuilder());
        AgentState state = new AgentState();
        state.setOwnerRole("doctor");
        state.setPatientId(13L);
        state.setUserMessage("请先分析这个患者。");

        executor.execute(state);

        AgentStep handoffStep = state.getSteps().stream()
                .filter(step -> "patient_profile_agent_handoff".equals(step.actionName()))
                .findFirst()
                .orElseThrow();
        assertEquals("profile-ready", handoffStep.actionInput().get("patientSnapshot"));
    }

    @Test
    void shouldResolveDelegationByRouteKeyToPrimaryImplementation() {
        AtomicInteger primaryCalls = new AtomicInteger();
        AtomicInteger backupCalls = new AtomicInteger();
        ChatSubAgent primary = subAgent(
                "risk_assessment_agent_primary",
                "risk_assessment_agent",
                state -> {
                    primaryCalls.incrementAndGet();
                    return new SubAgentResult("risk_assessment_agent_primary", true, "主实现已完成风险评估。", List.of("risk_assessment"));
                }
        );
        ChatSubAgent backup = subAgent(
                "risk_assessment_agent_backup",
                "risk_assessment_agent",
                state -> {
                    backupCalls.incrementAndGet();
                    return new SubAgentResult("risk_assessment_agent_backup", true, "备实现已完成风险评估。", List.of("risk_assessment"));
                }
        );
        SubAgentRegistry subAgentRegistry = new SubAgentRegistry(List.of(primary, backup));
        SequenceLlmService llmService = new SequenceLlmService(
                "{\"type\":\"delegate\",\"agentName\":\"risk_assessment_agent\",\"task\":\"请完成风险评估\",\"reasoningSummary\":\"优先走风险评估能力\"}",
                "{\"type\":\"final_answer\",\"answerPlan\":\"结合风险评估结果回答\",\"reasoningSummary\":\"信息已足够\"}",
                "这是最终回答。"
        );
        llmService.setEnabledForTest(true);

        ReActAgentExecutor executor = new ReActAgentExecutor(subAgentRegistry, llmService, new ReActPromptBuilder());
        AgentState state = new AgentState();
        state.setOwnerRole("doctor");
        state.setPatientId(21L);
        state.setUserMessage("请完成这个患者的风险评估。");

        ReActResult result = executor.execute(state);

        assertNotNull(result);
        assertEquals(1, primaryCalls.get());
        assertEquals(0, backupCalls.get());
        assertEquals("risk_assessment_agent_primary", result.steps().get(0).actionName());
    }

    @Test
    void shouldGuardUnsupportedPatientAssertionWhenNoObservationExists() {
        SubAgentRegistry subAgentRegistry = new SubAgentRegistry(List.of());
        SequenceLlmService llmService = new SequenceLlmService(
                "{\"type\":\"final_answer\",\"answerPlan\":\"直接回答\",\"reasoningSummary\":\"无需委派\"}",
                "根据当前患者情况，血压控制不佳，建议立即调整方案。"
        );
        llmService.setEnabledForTest(true);

        ReActAgentExecutor executor = new ReActAgentExecutor(subAgentRegistry, llmService, new ReActPromptBuilder());
        AgentState state = new AgentState();
        state.setOwnerRole("patient");
        state.setUserMessage("我现在怎么样？");

        ReActResult result = executor.execute(state);

        assertNotNull(result);
        assertTrue(result.reply().contains("缺少足够的患者资料或证据"));
        assertTrue(result.safetyFlags().contains("guard_replaced_unsupported_patient_assertion"));
    }

    @Test
    void shouldAppendDegradationDisclosureWhenExternalSourceUnavailable() {
        ChatSubAgent patientAgent = subAgent(
                "patient_profile_agent",
                state -> {
                    state.addUnavailableExternalSource("medplum_patient_insight");
                    state.addDegradationNote("Medplum/MCP 外部数据当前不可用，本轮分析已自动降级为基于平台内本地数据。 原因：Medplum 当前不可用。");
                    state.addSafetyFlag("external_data_degraded");
                    return new SubAgentResult("patient_profile_agent", false, "Medplum 当前不可用。", List.of());
                }
        );
        SubAgentRegistry subAgentRegistry = new SubAgentRegistry(List.of(patientAgent));
        SequenceLlmService llmService = new SequenceLlmService(
                "{\"type\":\"delegate\",\"agentName\":\"patient_profile_agent\",\"task\":\"读取外部视图\",\"reasoningSummary\":\"先尝试读取外部数据\"}",
                "{\"type\":\"final_answer\",\"answerPlan\":\"基于现有资料回答\",\"reasoningSummary\":\"改走本地数据\"}",
                "可以先结合现有资料进行分析。"
        );
        llmService.setEnabledForTest(true);

        ReActAgentExecutor executor = new ReActAgentExecutor(subAgentRegistry, llmService, new ReActPromptBuilder());
        AgentState state = new AgentState();
        state.setOwnerRole("doctor");
        state.setPatientId(10L);
        state.setUserMessage("请结合外部数据看一下这个患者。");

        ReActResult result = executor.execute(state);

        assertTrue(state.getUnavailableExternalSources().contains("medplum_patient_insight"));
        assertTrue(state.getDegradationNotes().stream().anyMatch(note -> note.contains("Medplum/MCP 外部数据当前不可用")));
        assertTrue(result.safetyFlags().contains("external_data_degraded"));
        assertTrue(result.reply().contains("基于平台内本地数据"));
        assertEquals(1, result.steps().size());
        assertTrue(result.steps().get(0).observationSummary().contains("Medplum 当前不可用"));
    }

    private ChatSubAgent subAgent(String name, java.util.function.Function<AgentState, SubAgentResult> runner) {
        return subAgent(name, name, runner);
    }

    private ChatSubAgent subAgent(String name, String routeKey, java.util.function.Function<AgentState, SubAgentResult> runner) {
        return new ChatSubAgent() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public String routeKey() {
                return routeKey;
            }

            @Override
            public int estimatedCostScore() {
                return name.contains("primary") ? 35 : 70;
            }

            @Override
            public int baselineHealthScore() {
                return name.contains("primary") ? 92 : 68;
            }

            @Override
            public String description() {
                return "test sub agent";
            }

            @Override
            public List<AgentTool> tools() {
                return List.of();
            }

            @Override
            public SubAgentResult execute(AgentState state, String delegatedTask) {
                return runner.apply(state);
            }
        };
    }

    private static class SequenceLlmService extends LlmService {
        private final java.util.ArrayDeque<String> responses = new java.util.ArrayDeque<>();
        private boolean enabledForTest;

        private SequenceLlmService(String... rawResponses) {
            responses.addAll(List.of(rawResponses));
        }

        private void setEnabledForTest(boolean enabled) {
            this.enabledForTest = enabled;
        }

        @Override
        public boolean isAvailable() {
            return enabledForTest;
        }

        @Override
        public String chat(String systemPrompt, String userMessage) {
            return responses.isEmpty() ? null : responses.removeFirst();
        }

        @Override
        public JsonNode chatJsonObject(String systemPrompt, String userMessage) {
            String raw = chat(systemPrompt, userMessage);
            return raw == null ? null : parseJsonObject(raw);
        }
    }
}
