package com.medical.agent.web.controller;

import com.medical.agent.service.react.orchestration.OrchestrationWatchdogService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class AdminOrchestrationControllerTest {

    @Test
    void shouldReturnLatestTaskBySession() {
        StubOrchestrationWatchdogService service = new StubOrchestrationWatchdogService();
        AdminOrchestrationController controller = new AdminOrchestrationController(service);

        ResponseEntity<Map<String, Object>> response = controller.getLatestSessionTask("session-1");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("session-1", service.lastSessionId);
        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        assertEquals("task-session-1", body.get("taskId"));
        assertEquals("session-1", body.get("sessionId"));
    }

    @Test
    void shouldAdvanceTaskNow() {
        StubOrchestrationWatchdogService service = new StubOrchestrationWatchdogService();
        AdminOrchestrationController controller = new AdminOrchestrationController(service);

        ResponseEntity<Map<String, Object>> response = controller.advanceTaskNow("task-advance");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("task-advance", service.lastAdvanceTaskId);
        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        assertEquals("task-advance", body.get("taskId"));
        assertEquals("WAITING_SUPPORT", body.get("status"));
    }

    @Test
    void shouldPassContractFiltersToTaskCards() {
        StubOrchestrationWatchdogService service = new StubOrchestrationWatchdogService();
        AdminOrchestrationController controller = new AdminOrchestrationController(service);

        ResponseEntity<java.util.List<Map<String, Object>>> response = controller.getTaskCards(
                "ACTIVE",
                "low_confidence_score_gap",
                "risk_triage",
                Boolean.TRUE,
                "schema_only",
                Boolean.FALSE,
                Boolean.TRUE
        );

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("ACTIVE", service.lastControlStatus);
        assertEquals("low_confidence_score_gap", service.lastReviewTrigger);
        assertEquals("risk_triage", service.lastTaskType);
        assertEquals(Boolean.TRUE, service.lastCircuitBreakerOnly);
        assertEquals("schema_only", service.lastContractCompatibility);
        assertEquals(Boolean.FALSE, service.lastContractValidated);
        assertEquals(Boolean.TRUE, service.lastContractErrorsOnly);
        assertNotNull(response.getBody());
        assertEquals("task-card-filtered", response.getBody().get(0).get("taskId"));
    }

    private static final class StubOrchestrationWatchdogService extends OrchestrationWatchdogService {
        private String lastSessionId;
        private String lastAdvanceTaskId;
        private String lastControlStatus;
        private String lastReviewTrigger;
        private String lastTaskType;
        private Boolean lastCircuitBreakerOnly;
        private String lastContractCompatibility;
        private Boolean lastContractValidated;
        private Boolean lastContractErrorsOnly;

        private StubOrchestrationWatchdogService() {
            super(null, null, null, null, null, null);
        }

        @Override
        public Optional<Map<String, Object>> getLatestSessionTask(String sessionId) {
            this.lastSessionId = sessionId;
            return Optional.of(Map.of(
                    "taskId", "task-" + sessionId,
                    "sessionId", sessionId,
                    "status", "RUNNING",
                    "controlStatus", "ACTIVE"
            ));
        }

        @Override
        public Optional<Map<String, Object>> advanceTaskNow(String taskId) {
            this.lastAdvanceTaskId = taskId;
            return Optional.of(Map.of(
                    "taskId", taskId,
                    "sessionId", "session-advance",
                    "status", "WAITING_SUPPORT",
                    "controlStatus", "ACTIVE"
            ));
        }

        @Override
        public java.util.List<Map<String, Object>> listTaskCards(String controlStatus,
                                                                 String reviewTrigger,
                                                                 String taskType,
                                                                 Boolean circuitBreakerOnly,
                                                                 String contractCompatibility,
                                                                 Boolean contractValidated,
                                                                 Boolean contractErrorsOnly) {
            this.lastControlStatus = controlStatus;
            this.lastReviewTrigger = reviewTrigger;
            this.lastTaskType = taskType;
            this.lastCircuitBreakerOnly = circuitBreakerOnly;
            this.lastContractCompatibility = contractCompatibility;
            this.lastContractValidated = contractValidated;
            this.lastContractErrorsOnly = contractErrorsOnly;
            return java.util.List.of(Map.of(
                    "taskId", "task-card-filtered",
                    "controlStatus", controlStatus,
                    "contractCompatibility", contractCompatibility
            ));
        }
    }
}
