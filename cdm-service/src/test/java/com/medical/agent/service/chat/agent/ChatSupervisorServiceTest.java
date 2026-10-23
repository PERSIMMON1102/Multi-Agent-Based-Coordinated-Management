package com.medical.agent.service.chat.agent;

import com.medical.agent.service.LlmService;
import com.medical.agent.service.react.ReActAgentExecutor;
import com.medical.agent.service.react.ReActPromptBuilder;
import com.medical.agent.service.react.agent.ChatSubAgent;
import com.medical.agent.service.react.agent.SubAgentRegistry;
import com.medical.agent.service.react.agent.SubAgentResult;
import com.medical.agent.service.react.model.AgentState;
import com.medical.agent.service.react.model.ReActResult;
import com.medical.agent.service.react.orchestration.AutonomousTask;
import com.medical.agent.service.react.orchestration.OrchestrationPersistenceService;
import com.medical.agent.service.react.orchestration.OrchestrationTrace;
import com.medical.agent.service.react.orchestration.TaskEvent;
import com.medical.agent.service.react.orchestration.TaskEventType;
import com.medical.agent.service.react.orchestration.TaskStateMachine;
import com.medical.agent.service.react.orchestration.TaskScheduler;
import com.medical.agent.service.react.orchestration.TaskStatus;
import com.medical.agent.service.react.orchestration.SupervisorLoop;
import com.medical.agent.service.react.tool.AgentTool;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChatSupervisorServiceTest {

    @Test
    void shouldDelegateNormalMessageToReactExecutor() {
        ReActAgentExecutor executor = mock(ReActAgentExecutor.class);
        LlmService llmService = mock(LlmService.class);
        TaskScheduler taskScheduler = mock(TaskScheduler.class);
        OrchestrationPersistenceService persistenceService = mock(OrchestrationPersistenceService.class);
        ChatSupervisorService service = new ChatSupervisorService(executor, llmService, new ReActPromptBuilder(), taskScheduler, persistenceService);

        AgentState state = new AgentState();
        state.setOwnerRole("patient");
        state.setUserMessage("我最近血压偏高，饮食和复查上需要注意什么？");

        ReActResult expected = new ReActResult(
                "请继续监测血压并规律复查。",
                "先补充上下文，再给建议。",
                "处理目标：补充上下文后生成建议。",
                "帮助患者形成可执行的监测与复查建议。",
                "已完成上下文规划。",
                "输出监测和复查建议。",
                List.of(),
                null,
                List.of(),
                null,
                List.of(),
                List.of("patient_context", "risk_assessment"),
                List.of(),
                List.of(),
                Set.of(),
                null
        );
        AutonomousTask task = new AutonomousTask("task-1", state.getUserMessage(), "ChatSupervisorService", 100);
        OrchestrationTrace trace = new OrchestrationTrace(
                "task-1",
                state.getUserMessage(),
                TaskStatus.COMPLETED,
                "patient_profile_agent",
                List.of(),
                List.of(),
                List.of(new TaskEvent(TaskEventType.COMPLETED, LocalDateTime.now(), "已完成。", Map.of())),
                null
        );
        when(taskScheduler.createRootTask(state)).thenReturn(task);
        when(taskScheduler.snapshot(task)).thenReturn(trace);
        when(executor.execute(state)).thenReturn(expected);

        ReActResult result = service.run(state);

        assertNotNull(result);
        assertEquals(expected.reply(), result.reply());
        assertEquals(expected.answerPlan(), result.answerPlan());
        assertNotNull(result.orchestrationTrace());
        assertEquals("task-1", result.orchestrationTrace().taskId());
        verify(executor).execute(state);
        verify(llmService, never()).chat(any(), any());
    }

    @Test
    void shouldSkipExecutorForLowSignalMessage() {
        ReActAgentExecutor executor = mock(ReActAgentExecutor.class);
        LlmService llmService = mock(LlmService.class);
        when(llmService.isAvailable()).thenReturn(false);

        ChatSupervisorService service = new ChatSupervisorService(
                executor,
                llmService,
                new ReActPromptBuilder(),
                mock(TaskScheduler.class),
                mock(OrchestrationPersistenceService.class)
        );
        AgentState state = new AgentState();
        state.setUserMessage("你好");

        ReActResult result = service.run(state);

        assertNotNull(result);
        assertTrue(result.steps().isEmpty());
        assertTrue(result.usedTools().isEmpty());
        assertFalse(result.reply().isBlank());
        verify(executor, never()).execute(any());
    }

    @Test
    void shouldRunOrchestrationLoopAndReenterExecutor() {
        ReActAgentExecutor executor = mock(ReActAgentExecutor.class);
        LlmService llmService = mock(LlmService.class);
        TaskScheduler taskScheduler = new TaskScheduler(
                new TaskStateMachine(),
                new SubAgentRegistry(List.of(orchestrationAgent("risk_assessment_agent"))),
                new SupervisorLoop()
        );
        ChatSupervisorService service = new ChatSupervisorService(
                executor,
                llmService,
                new ReActPromptBuilder(),
                taskScheduler,
                mock(OrchestrationPersistenceService.class)
        );

        AgentState state = new AgentState();
        state.setOwnerRole("doctor");
        state.setPatientId(12L);
        state.setUserMessage("请分析这个患者。");

        ReActResult first = new ReActResult(
                "阶段性答复。",
                "先画像后评估。",
                null,
                state.getUserMessage(),
                "已完成初步分析。",
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
        );
        ReActResult second = new ReActResult(
                "最终答复。",
                "已结合风险评估给出建议。",
                null,
                state.getUserMessage(),
                "已完成风险评估。",
                "直接输出结论。",
                List.of(),
                null,
                List.of(),
                null,
                List.of(),
                List.of("risk_assessment"),
                List.of(),
                List.of(),
                Set.of(),
                null
        );
        when(executor.execute(state)).thenReturn(first, second);

        ReActResult result = service.run(state);

        assertNotNull(result);
        assertEquals("最终答复。", result.reply());
        assertTrue(result.steps().stream().anyMatch(step -> "risk_assessment_agent".equals(step.actionName())));
        assertNotNull(result.orchestrationTrace());
        assertTrue(result.orchestrationTrace().events().stream().anyMatch(event -> event.type() == TaskEventType.COMPLETED));
        verify(executor, times(2)).execute(state);
    }

    @Test
    void shouldDispatchIndependentSubTasksInSingleRound() {
        ReActAgentExecutor executor = mock(ReActAgentExecutor.class);
        LlmService llmService = mock(LlmService.class);
        TaskScheduler taskScheduler = new TaskScheduler(
                new TaskStateMachine(),
                new SubAgentRegistry(List.of(
                        orchestrationAgent("risk_assessment_agent", List.of("risk_assessment")),
                        orchestrationAgent("evidence_support_agent", List.of("medical_evidence_search"))
                )),
                new SupervisorLoop()
        );
        ChatSupervisorService service = new ChatSupervisorService(
                executor,
                llmService,
                new ReActPromptBuilder(),
                taskScheduler,
                mock(OrchestrationPersistenceService.class)
        );

        AgentState state = new AgentState();
        state.setOwnerRole("doctor");
        state.setPatientId(12L);
        state.setUserMessage("请综合评估并补充证据。");

        ReActResult first = new ReActResult(
                "阶段性答复。",
                "先评估风险，再补证据。",
                null,
                state.getUserMessage(),
                "已完成初步分析。",
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
        );
        ReActResult second = new ReActResult(
                "最终答复。",
                "已结合风险评估与证据。",
                null,
                state.getUserMessage(),
                "已完成风险与证据整合。",
                "直接输出结论。",
                List.of(),
                null,
                List.of(),
                null,
                List.of(),
                List.of("risk_assessment", "medical_evidence_search"),
                List.of(),
                List.of(),
                Set.of(),
                null
        );
        when(executor.execute(state)).thenReturn(first, second);

        ReActResult result = service.run(state);

        assertNotNull(result);
        assertEquals("最终答复。", result.reply());
        assertTrue(result.steps().stream().anyMatch(step -> "risk_assessment_agent".equals(step.actionName())));
        assertTrue(result.steps().stream().anyMatch(step -> "evidence_support_agent".equals(step.actionName())));
        verify(executor, times(2)).execute(state);
    }

    private ChatSubAgent orchestrationAgent(String name) {
        return orchestrationAgent(name, List.of("risk_assessment"));
    }

    private ChatSubAgent orchestrationAgent(String name, List<String> usedTools) {
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
                return new SubAgentResult(name, true, true, "已执行编排子任务。", usedTools);
            }
        };
    }
}
