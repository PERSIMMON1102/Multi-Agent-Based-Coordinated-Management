package com.medical.agent.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.medical.agent.common.dto.NotificationDTO;
import com.medical.agent.model.entity.Patient;
import com.medical.agent.model.entity.SmartFollowUpMessage;
import com.medical.agent.model.repository.PatientRepository;
import com.medical.agent.model.repository.SmartFollowUpMessageRepository;
import com.medical.agent.service.followup.agent.SmartFollowUpSupervisorService;
import com.medical.agent.service.testsupport.RepositoryProxyFactory;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SmartFollowUpServiceTest {

    @Test
    void shouldPersistMessageHistoryAndUpdateFeedback() {
        RecordingAgentBridgeService bridgeService = new RecordingAgentBridgeService();
        AtomicLong idGenerator = new AtomicLong();
        Map<Long, SmartFollowUpMessage> storedMessages = new LinkedHashMap<>();
        Map<String, java.util.function.Function<Object[], Object>> handlers = new LinkedHashMap<>();
        handlers.put("save", args -> {
            SmartFollowUpMessage message = (SmartFollowUpMessage) args[0];
            if (message.getId() == null) {
                message.setId(idGenerator.incrementAndGet());
            }
            if (message.getCreatedAt() == null) {
                message.setCreatedAt(LocalDateTime.now());
            }
            storedMessages.put(message.getId(), message);
            return message;
        });
        handlers.put("findAllByOrderByCreatedAtDesc", args -> {
            if (args.length == 1 && args[0] instanceof Pageable pageable) {
                return storedMessages.values().stream()
                        .sorted(Comparator.comparing(SmartFollowUpMessage::getCreatedAt).reversed())
                        .limit(pageable.getPageSize())
                        .toList();
            }
            return storedMessages.values().stream()
                    .sorted(Comparator.comparing(SmartFollowUpMessage::getCreatedAt).reversed())
                    .toList();
        });
        handlers.put("findByPatientIdOrderByCreatedAtDesc", args -> storedMessages.values().stream()
                .filter(message -> Objects.equals(message.getPatientId(), args[0]))
                .sorted(Comparator.comparing(SmartFollowUpMessage::getCreatedAt).reversed())
                .limit(args.length > 1 && args[1] instanceof Pageable pageable ? pageable.getPageSize() : Long.MAX_VALUE)
                .toList());
        handlers.put("findById", args -> Optional.ofNullable(storedMessages.get(Long.valueOf(String.valueOf(args[0])))));
        handlers.put("deleteAll", args -> {
            @SuppressWarnings("unchecked")
            Iterable<SmartFollowUpMessage> messages = (Iterable<SmartFollowUpMessage>) args[0];
            for (SmartFollowUpMessage message : messages) {
                storedMessages.remove(message.getId());
            }
            return null;
        });
        SmartFollowUpMessageRepository repository = RepositoryProxyFactory.create(SmartFollowUpMessageRepository.class, handlers);

        SmartFollowUpService service = new SmartFollowUpService(
                RepositoryProxyFactory.create(PatientRepository.class, Map.of(
                        "findById", args -> Optional.of(Patient.builder().id(1L).name("随访患者").build())
                )),
                bridgeService,
                new StubSupervisorService(),
                repository,
                new ObjectMapper()
        );

        Map<String, Object> generated = service.generateFollowUpMessage(1L);
        Map<String, Object> sent = service.sendFollowUpMessage(
                1L,
                "请按时复查",
                List.of(Map.of("agentName", "followup_drafting_agent", "summary", "生成提醒文案")),
                List.of("药物过敏：青霉素")
        );
        assertEquals("sent", sent.get("status"));
        assertEquals(1, ((List<?>) sent.get("agentSteps")).size());
        assertEquals(List.of("药物过敏：青霉素"), sent.get("historyAlerts"));

        Map<String, Object> feedback = service.recordFeedback(Long.valueOf(String.valueOf(sent.get("id"))), "收到");
        List<Map<String, Object>> history = service.getMessageHistory();

        assertEquals("待发送消息", generated.get("message"));
        assertEquals("AI_FOLLOW_UP", bridgeService.lastNotification.get().getType());
        assertEquals(List.of("药物过敏：青霉素"), bridgeService.lastNotification.get().getHistoryAlerts());
        assertTrue(bridgeService.lastNotification.get().getMessage().contains("病史提醒"));
        assertEquals("ok", feedback.get("status"));
        assertEquals(1, history.size());
        assertEquals("replied", history.get(0).get("status"));
        assertEquals("收到", history.get(0).get("feedback"));
        assertEquals(List.of("药物过敏：青霉素"), history.get(0).get("historyAlerts"));
        assertEquals("followup_drafting_agent", ((Map<?, ?>) ((List<?>) history.get(0).get("agentSteps")).get(0)).get("agentName"));
    }

    @Test
    void shouldFilterHistoryByPatientAndLimit() {
        AtomicLong idGenerator = new AtomicLong();
        Map<Long, SmartFollowUpMessage> storedMessages = new LinkedHashMap<>();
        SmartFollowUpMessageRepository repository = RepositoryProxyFactory.create(SmartFollowUpMessageRepository.class, Map.of(
                "findAllByOrderByCreatedAtDesc", args -> {
                    int pageSize = args.length == 1 && args[0] instanceof Pageable pageable ? pageable.getPageSize() : Integer.MAX_VALUE;
                    return storedMessages.values().stream()
                            .sorted(Comparator.comparing(SmartFollowUpMessage::getCreatedAt).reversed())
                            .limit(pageSize)
                            .toList();
                },
                "findByPatientIdOrderByCreatedAtDesc", args -> {
                    int pageSize = args.length > 1 && args[1] instanceof Pageable pageable ? pageable.getPageSize() : Integer.MAX_VALUE;
                    return storedMessages.values().stream()
                            .filter(message -> Objects.equals(message.getPatientId(), args[0]))
                            .sorted(Comparator.comparing(SmartFollowUpMessage::getCreatedAt).reversed())
                            .limit(pageSize)
                            .toList();
                }
        ));

        for (int i = 1; i <= 5; i++) {
            storedMessages.put((long) i, SmartFollowUpMessage.builder()
                    .id(idGenerator.incrementAndGet())
                    .patientId(i <= 3 ? 1L : 2L)
                    .patientName(i <= 3 ? "甲" : "乙")
                    .message("消息" + i)
                    .status("sent")
                    .channel("系统通知")
                    .createdAt(LocalDateTime.of(2026, 4, i, 10, 0))
                    .build());
        }

        SmartFollowUpService service = new SmartFollowUpService(
                null,
                null,
                null,
                repository,
                new ObjectMapper()
        );

        List<Map<String, Object>> history = service.getMessageHistory(1L, 2);
        assertEquals(2, history.size());
        assertEquals(1L, history.get(0).get("patientId"));
        assertEquals("消息3", history.get(0).get("message"));
        assertEquals("消息2", history.get(1).get("message"));
    }

    @Test
    void shouldTrimMessageHistoryBeyondLimit() {
        RecordingAgentBridgeService bridgeService = new RecordingAgentBridgeService();
        AtomicLong idGenerator = new AtomicLong();
        Map<Long, SmartFollowUpMessage> storedMessages = new LinkedHashMap<>();
        SmartFollowUpMessageRepository repository = RepositoryProxyFactory.create(SmartFollowUpMessageRepository.class, Map.of(
                "save", args -> {
                    SmartFollowUpMessage message = (SmartFollowUpMessage) args[0];
                    if (message.getId() == null) {
                        message.setId(idGenerator.incrementAndGet());
                    }
                    if (message.getCreatedAt() == null) {
                        message.setCreatedAt(LocalDateTime.now().plusSeconds(idGenerator.get()));
                    }
                    storedMessages.put(message.getId(), message);
                    return message;
                },
                "findAllByOrderByCreatedAtDesc", args -> storedMessages.values().stream()
                        .sorted(Comparator.comparing(SmartFollowUpMessage::getCreatedAt).reversed())
                        .toList(),
                "findByPatientIdOrderByCreatedAtDesc", args -> storedMessages.values().stream()
                        .filter(message -> Objects.equals(message.getPatientId(), args[0]))
                        .sorted(Comparator.comparing(SmartFollowUpMessage::getCreatedAt).reversed())
                        .toList(),
                "deleteAll", args -> {
                    @SuppressWarnings("unchecked")
                    Iterable<SmartFollowUpMessage> messages = (Iterable<SmartFollowUpMessage>) args[0];
                    for (SmartFollowUpMessage message : messages) {
                        storedMessages.remove(message.getId());
                    }
                    return null;
                }
        ));

        SmartFollowUpService service = new SmartFollowUpService(
                RepositoryProxyFactory.create(PatientRepository.class, Map.of(
                        "findById", args -> Optional.of(Patient.builder().id(1L).name("随访患者").build())
                )),
                bridgeService,
                new StubSupervisorService(),
                repository,
                new ObjectMapper()
        );

        for (int i = 1; i <= 32; i++) {
            service.sendFollowUpMessage(1L, "第" + i + "条提醒", List.of());
        }

        List<Map<String, Object>> history = service.getMessageHistory();
        assertEquals(30, history.size());
        assertEquals("第32条提醒", history.get(0).get("message"));
        assertEquals("第3条提醒", history.get(29).get("message"));
    }

    private static class StubSupervisorService extends SmartFollowUpSupervisorService {
        StubSupervisorService() {
            super(null, null, null, List.of());
        }

        @Override
        public Map<String, Object> generate(Long patientId) {
            return Map.of(
                    "patientId", patientId,
                    "message", "待发送消息",
                    "historyAlerts", List.of("药物过敏：青霉素"),
                    "agentSteps", List.of()
            );
        }
    }

    private static class RecordingAgentBridgeService implements ClinicalNotificationGateway {
        private final AtomicReference<NotificationDTO> lastNotification = new AtomicReference<>();

        @Override
        public void setNotificationConsumer(java.util.function.Consumer<NotificationDTO> consumer) {
        }

        @Override
        public void sendNotification(NotificationDTO notification) {
            lastNotification.set(notification);
        }
    }
}
