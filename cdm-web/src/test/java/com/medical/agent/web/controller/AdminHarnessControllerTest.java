package com.medical.agent.web.controller;

import com.medical.agent.service.HarnessObservabilityService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class AdminHarnessControllerTest {

    @Test
    void shouldReturnHarnessOverviewWithRequestParams() {
        StubHarnessObservabilityService service = new StubHarnessObservabilityService();
        AdminHarnessController controller = new AdminHarnessController(service);

        ResponseEntity<Map<String, Object>> response = controller.getOverview(14, 8, 9, 10);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(14, service.lastDays);
        assertEquals(8, service.lastTraceLimit);
        assertEquals(9, service.lastRuleLimit);
        assertEquals(10, service.lastMessageLimit);

        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        assertEquals(14, ((Map<?, ?>) body.get("summary")).get("recentDays"));
        assertEquals("良好", ((Map<?, ?>) body.get("evaluation")).get("overallStatus"));
        assertEquals(1, ((List<?>) body.get("recentTraces")).size());
    }

    private static class StubHarnessObservabilityService extends HarnessObservabilityService {
        private Integer lastDays;
        private Integer lastTraceLimit;
        private Integer lastRuleLimit;
        private Integer lastMessageLimit;

        StubHarnessObservabilityService() {
            super(null, null, null, null, null);
        }

        @Override
        public Map<String, Object> getOverview(Integer days,
                                               Integer traceLimit,
                                               Integer ruleLimit,
                                               Integer messageLimit) {
            this.lastDays = days;
            this.lastTraceLimit = traceLimit;
            this.lastRuleLimit = ruleLimit;
            this.lastMessageLimit = messageLimit;

            return Map.of(
                    "summary", Map.of(
                            "recentDays", days == null ? 30 : days,
                            "traceCoverageRate", 80.0
                    ),
                    "evaluation", Map.of(
                            "overallScore", 78,
                            "overallStatus", "良好",
                            "scorecards", List.of(Map.of("key", "observability", "score", 78, "status", "良好"))
                    ),
                    "recentTraces", List.of(Map.of("messageId", 1, "replyPreview", "test"))
            );
        }
    }
}
