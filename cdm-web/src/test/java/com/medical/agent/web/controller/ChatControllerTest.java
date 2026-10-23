package com.medical.agent.web.controller;

import com.medical.agent.service.ChatService;
import com.medical.agent.service.LlmService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatControllerTest {

    @Test
    void shouldReturnBadRequestWhenMessageIsBlank() {
        ChatController controller = new ChatController(new StubChatService(), new StubLlmService());

        ResponseEntity<Map<String, Object>> response = controller.sendMessage(Map.of("message", " "));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("消息不能为空", response.getBody().get("error"));
    }

    @Test
    void shouldMapChatServiceResponseToHttpBody() {
        ChatController controller = new ChatController(new StubChatService(), new StubLlmService());

        ResponseEntity<Map<String, Object>> response = controller.sendMessage(Map.of(
                "sessionId", "",
                "ownerRole", "doctor",
                "ownerUserId", 7,
                "patientId", 11,
                "message", "请结合患者资料分析风险"
        ));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        Map<String, Object> body = response.getBody();
        assertNotNull(body.get("sessionId"));
        assertFalse(String.valueOf(body.get("sessionId")).isBlank());
        assertEquals("多智能体回复", body.get("reply"));
        assertEquals(List.of("patient_context_agent"), body.get("usedTools"));
        assertEquals("图谱摘要", body.get("knowledgeGraphSummary"));
        assertEquals(true, body.get("usedCloudPatientData"));
        assertTrue(body.containsKey("timestamp"));
    }

    @Test
    void shouldExposeLlmStatus() {
        ChatController controller = new ChatController(new StubChatService(), new StubLlmService());

        ResponseEntity<Map<String, Object>> response = controller.getStatus();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(true, response.getBody().get("available"));
    }

    private static class StubChatService extends ChatService {
        StubChatService() {
            super(null, null, null, null, null, null, null, null, null, null, null, null, null, new ObjectMapper());
        }

        @Override
        public Map<String, Object> chat(String sessionId, String ownerRole, Long ownerUserId, Long patientId, String userMessage) {
            return Map.of(
                    "sessionId", sessionId,
                    "reply", "多智能体回复",
                    "references", List.of(Map.of("title", "指南A")),
                    "usedTools", List.of("patient_context_agent"),
                    "steps", List.of(Map.of("agentName", "patient_context_agent")),
                    "safetyFlags", List.of("high_risk_detected"),
                    "knowledgeGraphSummary", "图谱摘要",
                    "knowledgeGraphItems", List.of(Map.of("node", "高血压")),
                    "usedCloudPatientData", true
            );
        }

        @Override
        public List<Map<String, Object>> listSessions(String ownerRole, Long ownerUserId, Long patientId) {
            return List.of(Map.of("sessionId", "s-1"));
        }

        @Override
        public List<Map<String, Object>> getHistory(String sessionId, String ownerRole, Long ownerUserId) {
            return List.of(Map.of("role", "assistant", "content", "历史消息"));
        }

        @Override
        public void clearSession(String sessionId, String ownerRole, Long ownerUserId) {
        }
    }

    private static class StubLlmService extends LlmService {
        @Override
        public Map<String, Object> getStatus() {
            return Map.of("available", true, "model", "test-model");
        }
    }
}
