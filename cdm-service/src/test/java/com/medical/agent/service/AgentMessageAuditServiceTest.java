package com.medical.agent.service;

import com.medical.agent.model.mongo.AgentMessageLog;
import com.medical.agent.model.mongo.repository.AgentMessageLogRepository;
import com.medical.agent.service.testsupport.RepositoryProxyFactory;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentMessageAuditServiceTest {

    @Test
    void shouldPersistSanitizedMessageContentAndMetadata() {
        AtomicReference<AgentMessageLog> saved = new AtomicReference<>();
        AgentMessageLogRepository repository = RepositoryProxyFactory.create(AgentMessageLogRepository.class, Map.of(
                "save", args -> {
                    saved.set((AgentMessageLog) args[0]);
                    return args[0];
                }
        ));
        AgentMessageAuditService service = new AgentMessageAuditService(repository);

        String rawContent = "患者姓名张三，主诉上腹痛伴反酸2周，诊断胃食管反流病。";
        service.log("risk_assessment_agent", "supervisor", "INFORM", rawContent, Map.of(
                "rawText", "患者李某，男，45岁，主诉腹痛，诊断胃食管反流病",
                "tool", "patient_context"
        ));

        AgentMessageLog log = saved.get();
        assertNotNull(log);
        assertTrue(log.getContent().contains("sha256="));
        assertTrue(log.getContent().contains("message(len="));
        assertFalse(log.getContent().contains("张三"));
        assertFalse(String.valueOf(log.getMetadata().get("rawText")).contains("李某"));
        assertEqualsSafe("patient_context", String.valueOf(log.getMetadata().get("tool")));
    }

    private void assertEqualsSafe(String expected, String actual) {
        if (!expected.equals(actual)) {
            throw new AssertionError("expected=" + expected + ", actual=" + actual);
        }
    }
}
