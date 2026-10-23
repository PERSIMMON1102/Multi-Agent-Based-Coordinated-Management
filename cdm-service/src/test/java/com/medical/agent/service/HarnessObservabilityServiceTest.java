package com.medical.agent.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.medical.agent.model.entity.AiChatMessage;
import com.medical.agent.model.entity.AiChatSession;
import com.medical.agent.model.mongo.AgentMessageLog;
import com.medical.agent.model.mongo.RuleExecutionLog;
import com.medical.agent.model.mongo.repository.AgentMessageLogRepository;
import com.medical.agent.model.mongo.repository.RuleExecutionLogRepository;
import com.medical.agent.model.repository.AiChatMessageRepository;
import com.medical.agent.model.repository.AiChatSessionRepository;
import com.medical.agent.service.testsupport.RepositoryProxyFactory;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HarnessObservabilityServiceTest {

    @Test
    void shouldAggregateTraceAndLogMetrics() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        LocalDateTime now = LocalDateTime.now();

        AiChatSessionRepository sessionRepository = RepositoryProxyFactory.create(AiChatSessionRepository.class, Map.of(
                "findAll", args -> List.of(
                        session("s-1", "doctor", 11L, 1001L, "患者血压咨询", now.minusHours(2)),
                        session("s-2", "patient", 22L, 22L, "我的报告解读", now.minusHours(1))
                )
        ));
        AiChatMessageRepository messageRepository = RepositoryProxyFactory.create(AiChatMessageRepository.class, Map.of(
                "findAll", args -> List.of(
                        assistantMessage(1L, "s-1", now.minusHours(2), writeJson(objectMapper, Map.of(
                                "usedTools", List.of("patient_context", "risk_assessment"),
                                "steps", List.of(
                                        Map.of("index", 1, "actionName", "patient_context", "reasoningSummary", "读取患者上下文"),
                                        Map.of("index", 2, "actionName", "risk_assessment", "observationSummary", "命中高风险")
                                ),
                                "safetyFlags", List.of("high_risk_detected"),
                                "knowledgeGraphSummary", "命中高血压管理图谱",
                                "knowledgeGraphItems", List.of(Map.of("title", "高血压规范管理"))
                        ))),
                        assistantMessage(2L, "s-2", now.minusHours(1), writeJson(objectMapper, Map.of(
                                "usedTools", List.of("medical_evidence_agent"),
                                "steps", List.of(
                                        Map.of("index", 1, "actionName", "agent_1"),
                                        Map.of("index", 2, "actionName", "agent_2"),
                                        Map.of("index", 3, "actionName", "agent_3"),
                                        Map.of("index", 4, "actionName", "agent_4"),
                                        Map.of("index", 6, "actionName", "agent_5")
                                ),
                                "safetyFlags", List.of(),
                                "knowledgeGraphSummary", "",
                                "knowledgeGraphItems", List.of()
                        )))
                )
        ));
        RuleExecutionLogRepository ruleExecutionLogRepository = RepositoryProxyFactory.create(RuleExecutionLogRepository.class, Map.of(
                "findAll", args -> List.of(
                        ruleLog("r-1", "AssessmentAgent", "hypertension", "bp-high", 1001L, 12L, now.minusMinutes(30)),
                        ruleLog("r-2", "AssessmentAgent", "diabetes", "glucose-high", 1001L, 18L, now.minusMinutes(10))
                )
        ));
        AgentMessageLogRepository agentMessageLogRepository = RepositoryProxyFactory.create(AgentMessageLogRepository.class, Map.of(
                "findAll", args -> List.of(
                        agentLog("m-1", "Spring", "AssessmentAgent", "REQUEST", now.minusMinutes(25)),
                        agentLog("m-2", "AssessmentAgent", "Spring", "INFORM", now.minusMinutes(5))
                )
        ));

        HarnessObservabilityService service = new HarnessObservabilityService(
                sessionRepository,
                messageRepository,
                agentMessageLogRepository,
                ruleExecutionLogRepository,
                objectMapper
        );

        Map<String, Object> overview = service.getOverview(30, 10, 10, 10);

        @SuppressWarnings("unchecked")
        Map<String, Object> summary = (Map<String, Object>) overview.get("summary");
        assertEquals(2, summary.get("sessionCount"));
        assertEquals(2, summary.get("traceMessageCount"));
        assertEquals(1L, summary.get("boundedViolationCount"));
        assertEquals(1L, summary.get("safetyTraceCount"));
        assertEquals(1L, summary.get("highRiskTraceCount"));
        assertEquals(1L, summary.get("knowledgeGraphTraceCount"));
        assertEquals(3, summary.get("uniqueToolCount"));
        assertEquals(5, summary.get("maxObservedSteps"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> guardrails = (List<Map<String, Object>>) overview.get("guardrails");
        assertFalse((Boolean) guardrails.get(0).get("healthy"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> traces = (List<Map<String, Object>>) overview.get("recentTraces");
        assertEquals(2, traces.size());
        assertEquals(2L, traces.get(0).get("messageId"));
        assertFalse((Boolean) traces.get(0).get("boundedExecution"));
        assertEquals("关注", traces.get(0).get("qualityLabel"));
        assertTrue((Boolean) traces.get(1).get("hasKnowledgeGraph"));
        assertTrue((Boolean) traces.get(1).get("hasEvidence"));

        @SuppressWarnings("unchecked")
        Map<String, Object> evaluation = (Map<String, Object>) overview.get("evaluation");
        assertEquals("良好", evaluation.get("overallStatus"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> scorecards = (List<Map<String, Object>>) evaluation.get("scorecards");
        assertEquals("边界约束", scorecards.get(1).get("title"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> recommendedCases = (List<Map<String, Object>>) overview.get("recommendedCases");
        assertFalse(recommendedCases.isEmpty());
        assertEquals("高风险拦截演示", recommendedCases.get(0).get("title"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> ruleRows = (List<Map<String, Object>>) overview.get("recentRuleExecutions");
        assertEquals("diabetes", ruleRows.get(0).get("ruleGroup"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> toolStats = (List<Map<String, Object>>) overview.get("toolStats");
        assertEquals("medical_evidence_agent", toolStats.get(0).get("name"));
        assertEquals(1L, toolStats.get(0).get("count"));
    }

    private AiChatSession session(String id,
                                  String ownerRole,
                                  Long ownerUserId,
                                  Long patientId,
                                  String title,
                                  LocalDateTime lastMessageAt) {
        return AiChatSession.builder()
                .id(id)
                .ownerRole(ownerRole)
                .ownerUserId(ownerUserId)
                .patientId(patientId)
                .title(title)
                .lastMessageAt(lastMessageAt)
                .updatedAt(lastMessageAt)
                .build();
    }

    private AiChatMessage assistantMessage(Long id, String sessionId, LocalDateTime createdAt, String traceJson) {
        return AiChatMessage.builder()
                .id(id)
                .sessionId(sessionId)
                .role("assistant")
                .content("测试回复内容")
                .agentTraceJson(traceJson)
                .createdAt(createdAt)
                .build();
    }

    private RuleExecutionLog ruleLog(String id,
                                     String agentId,
                                     String ruleGroup,
                                     String ruleName,
                                     Long patientId,
                                     long executionTimeMs,
                                     LocalDateTime timestamp) {
        return RuleExecutionLog.builder()
                .id(id)
                .agentId(agentId)
                .ruleGroup(ruleGroup)
                .ruleName(ruleName)
                .patientId(patientId)
                .executionTimeMs(executionTimeMs)
                .timestamp(timestamp)
                .build();
    }

    private AgentMessageLog agentLog(String id,
                                     String senderAgent,
                                     String receiverAgent,
                                     String performative,
                                     LocalDateTime timestamp) {
        return AgentMessageLog.builder()
                .id(id)
                .senderAgent(senderAgent)
                .receiverAgent(receiverAgent)
                .performative(performative)
                .content("ASSESS_RESULT: test")
                .timestamp(timestamp)
                .build();
    }

    private String writeJson(ObjectMapper objectMapper, Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("测试数据序列化失败", exception);
        }
    }
}
