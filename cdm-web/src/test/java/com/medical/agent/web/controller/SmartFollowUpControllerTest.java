package com.medical.agent.web.controller;

import com.medical.agent.service.SmartFollowUpService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SmartFollowUpControllerTest {

    @Test
    void shouldPassAgentStepsToSendService() {
        RecordingSmartFollowUpService service = new RecordingSmartFollowUpService();
        SmartFollowUpController controller = new SmartFollowUpController(service);

        ResponseEntity<Map<String, Object>> response = controller.sendMessage(Map.of(
                "patientId", 9,
                "message", "请按时复查",
                "agentSteps", List.of(Map.of("agentName", "followup_drafting_agent")),
                "historyAlerts", List.of("药物过敏：青霉素")
        ));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(9L, service.patientId);
        assertEquals("请按时复查", service.message);
        assertEquals(1, service.agentSteps.size());
        assertEquals("followup_drafting_agent", service.agentSteps.get(0).get("agentName"));
        assertEquals(List.of("药物过敏：青霉素"), service.historyAlerts);
        assertEquals("sent", response.getBody().get("status"));
    }

    @Test
    void shouldReturnHistoryAndFeedbackResponses() {
        SmartFollowUpController controller = new SmartFollowUpController(new RecordingSmartFollowUpService());

        assertEquals(1, controller.getHistory(null, null).getBody().size());
        assertEquals(9L, controller.getHistory(9L, 15).getBody().get(0).get("patientId"));
        assertEquals(15, controller.getHistory(9L, 15).getBody().size());
        assertEquals("ok", controller.recordFeedback(Map.of("messageId", 1, "feedback", "收到")).getBody().get("status"));
    }

    private static class RecordingSmartFollowUpService extends SmartFollowUpService {
        private Long patientId;
        private String message;
        private List<Map<String, Object>> agentSteps = List.of();
        private List<String> historyAlerts = List.of();

        RecordingSmartFollowUpService() {
            super(null, null, null);
        }

        @Override
        public Map<String, Object> generateFollowUpMessage(Long patientId) {
            return Map.of("patientId", patientId, "message", "待发送");
        }

        @Override
        public Map<String, Object> sendFollowUpMessage(Long patientId, String message, List<Map<String, Object>> agentSteps) {
            return sendFollowUpMessage(patientId, message, agentSteps, List.of());
        }

        @Override
        public Map<String, Object> sendFollowUpMessage(Long patientId,
                                                       String message,
                                                       List<Map<String, Object>> agentSteps,
                                                       List<String> historyAlerts) {
            this.patientId = patientId;
            this.message = message;
            this.agentSteps = agentSteps;
            this.historyAlerts = historyAlerts;
            return Map.of("status", "sent", "agentSteps", agentSteps, "historyAlerts", historyAlerts);
        }

        @Override
        public List<Map<String, Object>> getMessageHistory() {
            return List.of(Map.of("id", 1L, "message", "历史记录"));
        }

        @Override
        public List<Map<String, Object>> getMessageHistory(Long patientId, Integer limit) {
            if (patientId == null && limit == null) {
                return getMessageHistory();
            }
            return java.util.stream.IntStream.range(0, limit == null ? 1 : limit)
                    .mapToObj(index -> Map.<String, Object>of("id", (long) index + 1, "patientId", patientId, "message", "历史记录" + index))
                    .toList();
        }

        @Override
        public Map<String, Object> recordFeedback(Long messageId, String feedback) {
            return Map.of("status", "ok", "messageId", messageId, "feedback", feedback);
        }
    }
}
