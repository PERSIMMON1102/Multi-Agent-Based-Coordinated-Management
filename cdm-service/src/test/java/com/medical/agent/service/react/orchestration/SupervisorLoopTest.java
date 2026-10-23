package com.medical.agent.service.react.orchestration;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SupervisorLoopTest {

    @Test
    void shouldPreferContextRecoveryTaskWhenLatestBlockerSignalsContextGap() {
        SupervisorLoop loop = new SupervisorLoop();
        AutonomousTask task = new AutonomousTask("task-1", "请完成风险评估并补全患者资料", "ChatSupervisorService", 100);
        task.setStatus(TaskStatus.WAITING_SUPPORT);
        task.addEvent(new TaskEvent(
                TaskEventType.BLOCKED,
                LocalDateTime.now().minusMinutes(5),
                "子任务未完成，进入阻塞。",
                Map.of(
                        "subTaskId", "sub-risk",
                        "targetAgent", "risk_assessment_agent",
                        "failureCategory", "context_gap",
                        "missingCapabilities", List.of("patient_context")
                )
        ));
        task.addEvent(new TaskEvent(
                TaskEventType.SUBTASK_HANDOFF,
                LocalDateTime.now().minusMinutes(2),
                "管理员转派了子任务。",
                Map.of(
                        "subTaskId", "sub-profile",
                        "targetAgent", "patient_profile_agent"
                )
        ));

        SubTask risk = new SubTask("sub-risk", "完成风险评估，但当前缺患者上下文", "risk_assessment_agent", 100);
        risk.setStatus(SubTaskStatus.READY);
        SubTask profile = new SubTask("sub-profile", "补充患者画像与上下文，解除当前 context_gap", "patient_profile_agent", 85);
        profile.setStatus(SubTaskStatus.READY);
        task.addSubTask(risk);
        task.addSubTask(profile);

        SupervisorDecision decision = loop.pickNext(task);

        assertNotNull(decision);
        assertEquals("sub-profile", decision.subTaskId());
        assertEquals("patient_profile_agent", decision.targetAgent());
        assertTrue(decision.score() > loop.score(task, risk));
    }

    @Test
    void shouldPreferDependencySatisfiedTask() {
        SupervisorLoop loop = new SupervisorLoop();
        AutonomousTask task = new AutonomousTask("task-2", "先画像再评估风险", "ChatSupervisorService", 100);
        task.setStatus(TaskStatus.RUNNING);

        SubTask profile = new SubTask("sub-profile", "补全患者画像", "patient_profile_agent", 70);
        profile.setStatus(SubTaskStatus.COMPLETED);
        SubTask risk = new SubTask("sub-risk", "进行风险评估", "risk_assessment_agent", 90);
        risk.setStatus(SubTaskStatus.READY);
        risk.getDependencies().add("sub-profile");
        SubTask evidence = new SubTask("sub-evidence", "补文献证据", "evidence_support_agent", 95);
        evidence.setStatus(SubTaskStatus.PENDING);
        evidence.getDependencies().add("sub-missing");
        task.addSubTask(profile);
        task.addSubTask(risk);
        task.addSubTask(evidence);

        List<SubTask> ranked = loop.rankCandidates(task, 5);

        assertEquals("sub-risk", ranked.get(0).getSubTaskId());
        assertTrue(loop.score(task, risk) > loop.score(task, evidence));
    }
}
