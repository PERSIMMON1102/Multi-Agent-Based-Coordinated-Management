package com.medical.agent.service.chat.agent;

import com.medical.agent.common.dto.RagEvidenceDTO;
import com.medical.agent.common.dto.RagQueryContextDTO;
import com.medical.agent.service.LlmService;
import com.medical.agent.service.react.ReActAgentExecutor;
import com.medical.agent.service.react.ReActPromptBuilder;
import com.medical.agent.service.react.agent.SubAgentResult;
import com.medical.agent.service.react.model.AgentState;
import com.medical.agent.service.react.model.AgentStep;
import com.medical.agent.service.react.model.ReActResult;
import com.medical.agent.service.react.orchestration.AutonomousTask;
import com.medical.agent.service.react.orchestration.OrchestrationPersistenceService;
import com.medical.agent.service.react.orchestration.SubTaskExecutionResult;
import com.medical.agent.service.react.orchestration.TaskStatus;
import com.medical.agent.service.react.orchestration.TaskScheduler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

@Service
public class ChatSupervisorService {

    private static final Set<String> LOW_SIGNAL_MESSAGES = Set.of(
            "谢谢", "谢谢你", "好的", "好", "收到", "嗯", "嗯嗯", "在吗", "你好", "您好"
    );
    private static final int MAX_ORCHESTRATION_ROUNDS = 3;

    private final ReActAgentExecutor reactAgentExecutor;
    private final LlmService llmService;
    private final ReActPromptBuilder promptBuilder;
    private final TaskScheduler taskScheduler;
    private final OrchestrationPersistenceService orchestrationPersistenceService;

    @Autowired
    public ChatSupervisorService(ReActAgentExecutor reactAgentExecutor,
                                 LlmService llmService,
                                 ReActPromptBuilder promptBuilder,
                                 TaskScheduler taskScheduler,
                                 OrchestrationPersistenceService orchestrationPersistenceService) {
        this.reactAgentExecutor = reactAgentExecutor;
        this.llmService = llmService;
        this.promptBuilder = promptBuilder;
        this.taskScheduler = taskScheduler;
        this.orchestrationPersistenceService = orchestrationPersistenceService;
    }

    public ReActResult run(AgentState state) {
        ChatRunSession runSession = start(state);
        return finish(runSession);
    }

    public ChatRunSession start(AgentState state) {
        if (state == null) {
            return new ChatRunSession(null, null, emptyResult(null));
        }

        if (isLowSignalMessage(state.getUserMessage())) {
            return new ChatRunSession(state, null, new ReActResult(
                    generateLowSignalReply(state),
                    null,
                    null,
                    null,
                    null,
                    null,
                    List.of(),
                    (RagQueryContextDTO) null,
                    List.<RagEvidenceDTO>of(),
                    null,
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    Set.of(),
                    null
            ));
        }

        hydrateStateFromActiveTask(state);
        AutonomousTask task = taskScheduler.createRootTask(state);
        taskScheduler.markPlanning(task);
        ReActResult result = reactAgentExecutor.execute(state);
        taskScheduler.reconcile(task, result);
        return new ChatRunSession(state, task, result);
    }

    public ReActResult finish(ChatRunSession runSession) {
        if (runSession == null) {
            return emptyResult(null);
        }
        AgentState state = runSession.state();
        AutonomousTask task = runSession.task();
        ReActResult result = runSession.result();
        if (state == null) {
            return emptyResult(null);
        }
        if (task == null) {
            return result == null ? emptyResult(null) : result;
        }
        for (int round = 0; round < MAX_ORCHESTRATION_ROUNDS; round++) {
            if (!shouldContinueOrchestration(task)) {
                break;
            }
            List<SubTaskExecutionResult> executionResults = taskScheduler.dispatchReadyBatch(task, state);
            if (executionResults.isEmpty()) {
                break;
            }
            executionResults.forEach(executionResult -> {
                mergeSubTaskExecution(state, executionResult);
                mergeSubTaskState(state, executionResult.stateSnapshot());
            });
            result = reactAgentExecutor.execute(state);
            taskScheduler.reconcile(task, result);
        }
        if (shouldContinueOrchestration(task)) {
            state.addSafetyFlag("orchestration_round_limit_reached");
            state.addBlocker("编排轮次达到上限，剩余子任务待后续继续推进。");
            if (state.getNextAction() == null || state.getNextAction().isBlank()) {
                state.setNextAction("后续继续推进剩余子任务，或由人工接管。");
            }
        }
        ReActResult mergedResult = rebuildResultWithState(result, state);
        ReActResult finalResult = rebuildResultWithTrace(mergedResult, task);
        orchestrationPersistenceService.save(state, finalResult.orchestrationTrace());
        return finalResult;
    }

    public record ChatRunSession(AgentState state, AutonomousTask task, ReActResult result) {
    }

    private void hydrateStateFromActiveTask(AgentState state) {
        if (state == null || state.getSessionId() == null || state.getSessionId().isBlank()) {
            return;
        }
        orchestrationPersistenceService.findLatestActiveBySessionId(state.getSessionId()).ifPresent(snapshot -> {
            String blocker = "检测到上一次未完成任务: " + defaultText(snapshot.getGoal(), "待续跑任务");
            state.addBlocker(blocker);
            if (state.getProgressSummary() == null || state.getProgressSummary().isBlank()) {
                state.setProgressSummary("系统检测到同一会话仍有未完成的编排任务，已把该状态纳入本轮决策。");
            }
            Object nextTarget = snapshot.getNextDecision() == null ? null : snapshot.getNextDecision().get("targetAgent");
            if (nextTarget instanceof String targetAgent && !targetAgent.isBlank()) {
                state.setNextAction("优先续跑上一轮未完成子任务：" + targetAgent);
            }
            state.addStep(new AgentStep(
                    state.getSteps().size() + 1,
                    "恢复上一轮未完成编排任务。",
                    "orchestration_resume",
                    java.util.Map.of(
                            "taskId", defaultText(snapshot.getTaskId(), ""),
                            "status", defaultText(snapshot.getStatus(), "")
                    ),
                    blocker
            ));
        });
    }

    private boolean shouldContinueOrchestration(AutonomousTask task) {
        if (task == null || task.getStatus() == null) {
            return false;
        }
        if (task.getStatus() == TaskStatus.COMPLETED || task.getStatus() == TaskStatus.FAILED) {
            return false;
        }
        return task.getSubTasks().stream().anyMatch(subTask ->
                subTask.getStatus() == com.medical.agent.service.react.orchestration.SubTaskStatus.PENDING
                        || subTask.getStatus() == com.medical.agent.service.react.orchestration.SubTaskStatus.READY);
    }

    private ReActResult rebuildResultWithState(ReActResult result, AgentState state) {
        if (result == null) {
            return emptyResult(null);
        }
        return new ReActResult(
                result.reply(),
                result.answerPlan(),
                result.thoughtSummary(),
                state.getGoal() == null ? result.goal() : state.getGoal(),
                state.getProgressSummary() == null ? result.progressSummary() : state.getProgressSummary(),
                state.getNextAction() == null ? result.nextAction() : state.getNextAction(),
                state.getBlockers().isEmpty() ? result.blockers() : List.copyOf(state.getBlockers()),
                result.evidenceContext(),
                result.evidenceCandidates(),
                result.knowledgeGraphSummary(),
                result.knowledgeGraphItems(),
                state.getUsedTools().stream().toList(),
                state.getSteps(),
                state.getDegradationNotes(),
                state.getSafetyFlags(),
                result.orchestrationTrace()
        );
    }

    private ReActResult rebuildResultWithTrace(ReActResult result, AutonomousTask task) {
        if (result == null) {
            return emptyResult(null);
        }
        return new ReActResult(
                result.reply(),
                result.answerPlan(),
                result.thoughtSummary(),
                result.goal(),
                result.progressSummary(),
                result.nextAction(),
                result.blockers(),
                result.evidenceContext(),
                result.evidenceCandidates(),
                result.knowledgeGraphSummary(),
                result.knowledgeGraphItems(),
                result.usedTools(),
                result.steps(),
                result.degradationNotes(),
                result.safetyFlags(),
                taskScheduler.snapshot(task)
        );
    }

    private void mergeSubTaskExecution(AgentState state, SubTaskExecutionResult executionResult) {
        if (state == null || executionResult == null) {
            return;
        }
        String reasoning = executionResult.decision() == null
                ? "Supervisor 触发了一个编排子任务。"
                : executionResult.decision().summary();
        String actionName = executionResult.decision() == null
                ? "orchestrated_subtask"
                : executionResult.decision().targetAgent();
        String observation = executionResult.summary();
        state.addStep(new AgentStep(
                state.getSteps().size() + 1,
                reasoning,
                actionName,
                java.util.Map.of(
                        "subTaskId", executionResult.subTask() == null ? "" : executionResult.subTask().getSubTaskId(),
                        "goal", executionResult.subTask() == null ? "" : executionResult.subTask().getGoal()
                ),
                observation
        ));
        SubAgentResult subAgentResult = executionResult.subAgentResult();
        if (subAgentResult == null) {
            state.addBlocker(observation);
            return;
        }
        if (subAgentResult.usedTools() != null) {
            subAgentResult.usedTools().forEach(state::addUsedTool);
        }
        if (subAgentResult.blockers() != null) {
            subAgentResult.blockers().forEach(state::addBlocker);
        }
        if (subAgentResult.suggestedNextAction() != null && !subAgentResult.suggestedNextAction().isBlank()) {
            state.setNextAction(subAgentResult.suggestedNextAction());
        }
        if (!subAgentResult.completed()) {
            state.addBlocker(subAgentResult.agentName() + " 编排子任务尚未完成。");
        }
    }

    private void mergeSubTaskState(AgentState state, AgentState subTaskState) {
        if (state == null || subTaskState == null) {
            return;
        }
        if (subTaskState.getPatientContext() != null) {
            state.setPatientContext(subTaskState.getPatientContext());
        }
        if (subTaskState.getAssessmentResults() != null && !subTaskState.getAssessmentResults().isEmpty()) {
            state.setAssessmentResults(subTaskState.getAssessmentResults());
        }
        if (subTaskState.getEvidenceContext() != null) {
            state.setEvidenceContext(subTaskState.getEvidenceContext());
        }
        if (subTaskState.getEvidenceCandidates() != null && !subTaskState.getEvidenceCandidates().isEmpty()) {
            state.setEvidenceCandidates(subTaskState.getEvidenceCandidates());
        }
        if (subTaskState.getKnowledgeGraphSummary() != null && !subTaskState.getKnowledgeGraphSummary().isBlank()) {
            state.setKnowledgeGraphSummary(subTaskState.getKnowledgeGraphSummary());
        }
        if (subTaskState.getKnowledgeGraphItems() != null && !subTaskState.getKnowledgeGraphItems().isEmpty()) {
            state.setKnowledgeGraphItems(subTaskState.getKnowledgeGraphItems());
        }
        if (subTaskState.getUsedTools() != null) {
            subTaskState.getUsedTools().forEach(state::addUsedTool);
        }
        if (subTaskState.getSafetyFlags() != null) {
            subTaskState.getSafetyFlags().forEach(state::addSafetyFlag);
        }
        if (subTaskState.getUnavailableExternalSources() != null) {
            subTaskState.getUnavailableExternalSources().forEach(state::addUnavailableExternalSource);
        }
        if (subTaskState.getDegradationNotes() != null) {
            subTaskState.getDegradationNotes().forEach(state::addDegradationNote);
        }
        if (subTaskState.getBlockers() != null) {
            subTaskState.getBlockers().forEach(state::addBlocker);
        }
    }

    private boolean isLowSignalMessage(String userMessage) {
        if (userMessage == null || userMessage.isBlank()) {
            return true;
        }
        String normalized = userMessage.trim();
        return normalized.length() <= 2 || LOW_SIGNAL_MESSAGES.contains(normalized);
    }

    private String generateLowSignalReply(AgentState state) {
        if (llmService.isAvailable()) {
            String systemPrompt = promptBuilder.buildFinalAnswerSystemPrompt(state.getConversationPerspective());
            String userPrompt = "【用户问题】\n" + state.getUserMessage() + "\n\n"
                    + "请用一句简短自然的话回应，并引导对方补充具体的慢病管理问题，不要编造患者信息。";
            String reply = llmService.chat(systemPrompt, userPrompt);
            if (reply != null && !reply.isBlank() && !isRefusalLike(reply)) {
                return reply;
            }
        }
        return "您好，请直接告诉我您想咨询的慢病管理问题，或说明想结合哪位患者的资料分析。";
    }

    private boolean isRefusalLike(String reply) {
        String normalized = reply == null ? "" : reply.toLowerCase();
        return normalized.contains("敏感信息")
                || normalized.contains("换个话题")
                || normalized.contains("不能继续这个话题")
                || normalized.contains("无法协助处理该请求");
    }

    private ReActResult emptyResult(String reply) {
        return new ReActResult(
                reply,
                null,
                null,
                null,
                null,
                null,
                List.of(),
                (RagQueryContextDTO) null,
                List.<RagEvidenceDTO>of(),
                null,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                Set.of(),
                null
        );
    }

    private String defaultText(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
