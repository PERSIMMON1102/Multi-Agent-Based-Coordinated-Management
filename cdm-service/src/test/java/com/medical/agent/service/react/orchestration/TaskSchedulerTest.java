package com.medical.agent.service.react.orchestration;

import com.medical.agent.service.react.agent.ChatSubAgent;
import com.medical.agent.service.react.agent.SubAgentRegistry;
import com.medical.agent.service.react.agent.SubAgentResult;
import com.medical.agent.service.react.model.AgentState;
import com.medical.agent.service.react.model.ReActResult;
import com.medical.agent.service.react.tool.AgentTool;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

class TaskSchedulerTest {

    @Test
    void shouldCreateAndCompleteAutonomousTaskTrace() {
        TaskScheduler scheduler = new TaskScheduler(
                new TaskStateMachine(),
                new SubAgentRegistry(List.of(agent("patient_profile_agent"))),
                new SupervisorLoop()
        );
        AgentState state = new AgentState();
        state.setUserMessage("请分析这个患者。");

        AutonomousTask task = scheduler.createRootTask(state);
        scheduler.markPlanning(task);
        scheduler.reconcile(task, new ReActResult(
                "这是答复。",
                "先画像再回答。",
                null,
                state.getUserMessage(),
                "已执行画像。",
                "直接输出答复。",
                List.of(),
                null,
                List.of(),
                null,
                List.of(),
                List.of("patient_context"),
                List.of(),
                List.of(),
                Set.of(),
                null
        ));

        OrchestrationTrace trace = scheduler.snapshot(task);

        assertNotNull(trace);
        assertEquals(TaskStatus.COMPLETED, trace.status());
        assertTrue(trace.events().size() >= 3);
        assertTrue(trace.subTasks().isEmpty());
    }

    @Test
    void shouldMarkWaitingSupportWhenNextActionRequestsSupport() {
        TaskScheduler scheduler = new TaskScheduler(
                new TaskStateMachine(),
                new SubAgentRegistry(List.of(agent("patient_profile_agent"), agent("risk_assessment_agent"))),
                new SupervisorLoop()
        );
        AgentState state = new AgentState();
        state.setUserMessage("请分析这个患者。");
        AutonomousTask task = scheduler.createRootTask(state);

        scheduler.reconcile(task, new ReActResult(
                "阶段性答复。",
                null,
                null,
                state.getUserMessage(),
                "已完成画像。",
                "优先协调 risk_assessment_agent 接力处理当前问题。",
                List.of("patient_profile_agent 请求 risk_assessment_agent 协助。"),
                null,
                List.of(),
                null,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                Set.of(),
                null
        ));

        OrchestrationTrace trace = scheduler.snapshot(task);
        assertEquals(TaskStatus.WAITING_SUPPORT, trace.status());
        assertEquals(1, trace.subTasks().size());
        assertEquals("risk_assessment_agent", trace.subTasks().get(0).targetAgent());
        assertNotNull(trace.nextDecision());
        assertEquals("risk_assessment_agent", trace.nextDecision().targetAgent());
    }

    @Test
    void shouldDispatchNextSubTaskThroughRegistry() {
        TaskScheduler scheduler = new TaskScheduler(
                new TaskStateMachine(),
                new SubAgentRegistry(List.of(agent("risk_assessment_agent"))),
                new SupervisorLoop()
        );
        AgentState state = new AgentState();
        state.setUserMessage("请分析这个患者。");
        AutonomousTask task = scheduler.createRootTask(state);

        scheduler.reconcile(task, new ReActResult(
                "阶段性答复。",
                null,
                null,
                state.getUserMessage(),
                "已完成画像。",
                "优先协调 risk_assessment_agent 接力处理当前问题。",
                List.of("patient_profile_agent 请求 risk_assessment_agent 协助。"),
                null,
                List.of(),
                null,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                Set.of(),
                null
        ));

        SubTaskExecutionResult executionResult = scheduler.dispatchNext(task, state);

        assertNotNull(executionResult);
        assertTrue(executionResult.success());
        assertEquals(SubTaskStatus.COMPLETED, executionResult.subTask().getStatus());
        assertEquals("risk_assessment_agent", executionResult.decision().targetAgent());
    }

    @Test
    void shouldDispatchReadyBatchForIndependentSubTasks() {
        TaskScheduler scheduler = new TaskScheduler(
                new TaskStateMachine(),
                new SubAgentRegistry(List.of(agent("risk_assessment_agent"), agent("evidence_support_agent"))),
                new SupervisorLoop()
        );
        AgentState state = new AgentState();
        state.setUserMessage("请分析这个患者。");
        AutonomousTask task = scheduler.createRootTask(state);

        scheduler.reconcile(task, new ReActResult(
                "阶段性答复。",
                null,
                null,
                state.getUserMessage(),
                "已完成画像。",
                "优先协调 risk_assessment_agent 和 evidence_support_agent 接力处理当前问题。",
                List.of("patient_profile_agent 请求 risk_assessment_agent 协助。", "patient_profile_agent 请求 evidence_support_agent 协助。"),
                null,
                List.of(),
                null,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                Set.of(),
                null
        ));

        List<SubTaskExecutionResult> results = scheduler.dispatchReadyBatch(task, state);

        assertEquals(2, results.size());
        assertTrue(results.stream().allMatch(SubTaskExecutionResult::success));
        assertTrue(results.stream().anyMatch(item -> "risk_assessment_agent".equals(item.decision().targetAgent())));
        assertTrue(results.stream().anyMatch(item -> "evidence_support_agent".equals(item.decision().targetAgent())));
    }

    @Test
    void shouldPropagateSubAgentHandoffContractIntoExecutionEvents() {
        TaskScheduler scheduler = new TaskScheduler(
                new TaskStateMachine(),
                new SubAgentRegistry(List.of(agentWithContract("patient_profile_agent"))),
                new SupervisorLoop()
        );
        AgentState state = new AgentState();
        state.setUserMessage("请补充患者上下文。");
        AutonomousTask task = scheduler.createRootTask(state);
        SubTask subTask = new SubTask("sub-1", "补充患者画像", "patient_profile_agent", 80);
        subTask.setStatus(SubTaskStatus.READY);
        task.addSubTask(subTask);

        SubTaskExecutionResult result = scheduler.dispatchNext(task, state);

        assertNotNull(result);
        assertFalse(result.success());
        TaskEvent blockedEvent = task.getHistory().stream()
                .filter(event -> event.type() == TaskEventType.BLOCKED)
                .findFirst()
                .orElseThrow();
        assertEquals("risk_assessment_agent", blockedEvent.metadata().get("suggestedHandoffAgent"));
        assertEquals(List.of("patient_context:bp_history"), blockedEvent.metadata().get("evidenceRefs"));
        assertEquals(List.of("risk_assessment"), blockedEvent.metadata().get("nextRequiredCapabilities"));
        assertEquals(0.42D, blockedEvent.metadata().get("confidence"));
        assertEquals("sub-agent-result", blockedEvent.metadata().get("contractSchema"));
        assertEquals("1.0", blockedEvent.metadata().get("contractVersion"));
    }

    @Test
    void shouldDispatchLogicalTargetAgentToPrimaryRouteImplementation() {
        AtomicInteger primaryCalls = new AtomicInteger();
        AtomicInteger backupCalls = new AtomicInteger();
        TaskScheduler scheduler = new TaskScheduler(
                new TaskStateMachine(),
                new SubAgentRegistry(List.of(
                        agent("risk_assessment_agent_primary", "risk_assessment_agent", primaryCalls),
                        agent("risk_assessment_agent_backup", "risk_assessment_agent", backupCalls)
                )),
                new SupervisorLoop()
        );
        AgentState state = new AgentState();
        state.setUserMessage("请继续处理。");
        AutonomousTask task = scheduler.createRootTask(state);
        SubTask subTask = new SubTask("sub-risk", "完成风险评估", "risk_assessment_agent", 90);
        subTask.setStatus(SubTaskStatus.READY);
        task.addSubTask(subTask);

        SubTaskExecutionResult result = scheduler.dispatchNext(task, state);

        assertNotNull(result);
        assertTrue(result.success());
        assertEquals("risk_assessment_agent", result.decision().targetAgent());
        assertEquals("risk_assessment_agent_primary", result.resolvedAgentName());
        assertEquals("risk_assessment_agent", result.resolvedAgentRouteKey());
        assertEquals("local", result.resolvedAgentProvider());
        TaskEvent completedEvent = task.getHistory().stream()
                .filter(event -> event.type() == TaskEventType.COMPLETED)
                .findFirst()
                .orElseThrow();
        assertEquals("risk_assessment_agent_primary", completedEvent.metadata().get("resolvedAgentName"));
        assertEquals("risk_assessment_agent", completedEvent.metadata().get("resolvedAgentRouteKey"));
        assertEquals("local", completedEvent.metadata().get("resolvedAgentProvider"));
        assertEquals(1, primaryCalls.get());
        assertEquals(0, backupCalls.get());
    }

    private ChatSubAgent agent(String name) {
        return agent(name, name, null);
    }

    private ChatSubAgent agent(String name, String routeKey, AtomicInteger counter) {
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
            public String provider() {
                return name.contains("primary") ? "local" : "remote";
            }

            @Override
            public String description() {
                return "test";
            }

            @Override
            public List<AgentTool> tools() {
                return List.of();
            }

            @Override
            public SubAgentResult execute(AgentState state, String delegatedTask) {
                if (counter != null) {
                    counter.incrementAndGet();
                }
                return new SubAgentResult(name, true, "ok", List.of());
            }
        };
    }

    private ChatSubAgent agentWithContract(String name) {
        return new ChatSubAgent() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public String description() {
                return "test";
            }

            @Override
            public List<AgentTool> tools() {
                return List.of();
            }

            @Override
            public SubAgentResult execute(AgentState state, String delegatedTask) {
                return new SubAgentResult(
                        name,
                        true,
                        false,
                        "已补全部分画像，但仍需风险评估。",
                        List.of("patient_context"),
                        "请转交风险评估智能体。",
                        List.of("仍缺风险结论"),
                        List.of("risk_assessment_agent"),
                        "risk_assessment_agent",
                        "risk_reassessment",
                        List.of("risk_assessment"),
                        0.42D,
                        List.of("patient_context:bp_history"),
                        List.of(Map.of("kind", "profile_summary", "summary", "已提取血压病史")),
                        Map.of("patientSnapshot", "bp-history-loaded"),
                        List.of("risk_assessment"),
                        "sub-agent-result",
                        "1.0",
                        Map.of("schemaCompatible", true, "versionCompatible", true, "validated", true),
                        List.of()
                );
            }
        };
    }
}
