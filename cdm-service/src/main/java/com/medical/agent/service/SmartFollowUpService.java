package com.medical.agent.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.medical.agent.common.dto.NotificationDTO;
import com.medical.agent.model.entity.Patient;
import com.medical.agent.model.entity.SmartFollowUpMessage;
import com.medical.agent.model.repository.PatientRepository;
import com.medical.agent.model.repository.SmartFollowUpMessageRepository;
import com.medical.agent.service.followup.agent.SmartFollowUpSupervisorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * AI智能随访服务 - 自动生成个性化随访消息
 */
@Service
public class SmartFollowUpService {

    private static final Logger log = LoggerFactory.getLogger(SmartFollowUpService.class);
    private static final int DEFAULT_HISTORY_LIMIT = 30;
    private static final int MAX_HISTORY_QUERY_LIMIT = 100;
    private static final int MAX_HISTORY_PER_PATIENT = 30;

    private final PatientRepository patientRepository;
    private final ClinicalNotificationGateway clinicalNotificationGateway;
    private final SmartFollowUpSupervisorService smartFollowUpSupervisorService;
    private final SmartFollowUpMessageRepository smartFollowUpMessageRepository;
    private final ObjectMapper objectMapper;

    @Autowired
    public SmartFollowUpService(PatientRepository patientRepository,
                                ClinicalNotificationGateway clinicalNotificationGateway,
                                SmartFollowUpSupervisorService smartFollowUpSupervisorService,
                                SmartFollowUpMessageRepository smartFollowUpMessageRepository,
                                ObjectMapper objectMapper) {
        this.patientRepository = patientRepository;
        this.clinicalNotificationGateway = clinicalNotificationGateway;
        this.smartFollowUpSupervisorService = smartFollowUpSupervisorService;
        this.smartFollowUpMessageRepository = smartFollowUpMessageRepository;
        this.objectMapper = objectMapper;
    }

    protected SmartFollowUpService(PatientRepository patientRepository,
                                   ClinicalNotificationGateway clinicalNotificationGateway,
                                   SmartFollowUpSupervisorService smartFollowUpSupervisorService) {
        this(patientRepository, clinicalNotificationGateway, smartFollowUpSupervisorService, null, new ObjectMapper());
    }

    /**
     * 为指定患者生成AI随访消息
     */
    public Map<String, Object> generateFollowUpMessage(Long patientId) {
        return smartFollowUpSupervisorService.generate(patientId);
    }

    /**
     * 发送AI随访消息（通过WebSocket推送给患者）
     */
    @Transactional
    public Map<String, Object> sendFollowUpMessage(Long patientId, String message) {
        return sendFollowUpMessage(patientId, message, List.of());
    }

    @Transactional
    public Map<String, Object> sendFollowUpMessage(Long patientId, String message, List<Map<String, Object>> agentSteps) {
        return sendFollowUpMessage(patientId, message, agentSteps, List.of());
    }

    @Transactional
    public Map<String, Object> sendFollowUpMessage(Long patientId,
                                                   String message,
                                                   List<Map<String, Object>> agentSteps,
                                                   List<String> historyAlerts) {
        Patient patient = patientRepository.findById(patientId)
                .orElseThrow(() -> new RuntimeException("患者不存在: " + patientId));
        List<String> normalizedHistoryAlerts = normalizeHistoryAlerts(historyAlerts);

        NotificationDTO notification = NotificationDTO.builder()
                .type("AI_FOLLOW_UP")
                .patientId(patientId)
                .title("AI随访提醒")
                .message(buildNotificationMessage(message, normalizedHistoryAlerts))
                .historyAlerts(normalizedHistoryAlerts)
                .timestamp(System.currentTimeMillis())
                .build();
        clinicalNotificationGateway.sendNotification(notification);

        SmartFollowUpMessage savedMessage = smartFollowUpMessageRepository.save(SmartFollowUpMessage.builder()
                .patientId(patientId)
                .patientName(patient.getName())
                .message(message)
                .channel("系统通知")
                .status("sent")
                .agentTraceJson(toJson(agentSteps == null ? List.of() : agentSteps))
                .historyAlertJson(toJson(normalizedHistoryAlerts))
                .build());
        trimHistory(patientId);

        log.info("AI follow-up message sent to patient {}: {}", patient.getName(),
                message.substring(0, Math.min(50, message.length())));
        return toMap(savedMessage);
    }

    /**
     * 获取已发送的随访消息历史
     */
    public List<Map<String, Object>> getMessageHistory() {
        return smartFollowUpMessageRepository.findAllByOrderByCreatedAtDesc()
                .stream()
                .map(this::toMap)
                .toList();
    }

    public List<Map<String, Object>> getMessageHistory(Long patientId, Integer limit) {
        int queryLimit = normalizeLimit(limit);
        List<SmartFollowUpMessage> messages = patientId == null
                ? smartFollowUpMessageRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(0, queryLimit))
                : smartFollowUpMessageRepository.findByPatientIdOrderByCreatedAtDesc(patientId, PageRequest.of(0, queryLimit));
        return messages.stream()
                .map(this::toMap)
                .toList();
    }

    /**
     * 记录患者反馈
     */
    @Transactional
    public Map<String, Object> recordFeedback(Long messageId, String feedback) {
        LocalDateTime receivedAt = LocalDateTime.now();
        smartFollowUpMessageRepository.findById(messageId)
                .ifPresent(message -> {
                    message.setFeedback(feedback);
                    message.setStatus("replied");
                    message.setFeedbackReceivedAt(receivedAt);
                    smartFollowUpMessageRepository.save(message);
                });

        Map<String, Object> record = new LinkedHashMap<>();
        record.put("messageId", messageId);
        record.put("feedback", feedback);
        record.put("receivedAt", receivedAt.toLocalDate().toString());
        record.put("status", "ok");
        return record;
    }

    private void trimHistory(Long patientId) {
        List<SmartFollowUpMessage> history = smartFollowUpMessageRepository.findByPatientIdOrderByCreatedAtDesc(patientId);
        if (history.size() <= MAX_HISTORY_PER_PATIENT) {
            return;
        }
        smartFollowUpMessageRepository.deleteAll(history.subList(MAX_HISTORY_PER_PATIENT, history.size()));
    }

    private int normalizeLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return DEFAULT_HISTORY_LIMIT;
        }
        return Math.min(limit, MAX_HISTORY_QUERY_LIMIT);
    }

    private Map<String, Object> toMap(SmartFollowUpMessage message) {
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("id", message.getId());
        record.put("patientId", message.getPatientId());
        record.put("patientName", message.getPatientName());
        record.put("message", message.getMessage());
        record.put("channel", message.getChannel());
        record.put("status", message.getStatus());
        record.put("feedback", message.getFeedback());
        record.put("createdAt", message.getCreatedAt() != null
                ? message.getCreatedAt().toLocalDate().toString()
                : LocalDate.now().toString());
        record.put("historyAlerts", readStringList(message.getHistoryAlertJson()));
        record.put("agentSteps", readMapList(message.getAgentTraceJson()));
        return record;
    }

    private List<String> normalizeHistoryAlerts(List<String> historyAlerts) {
        if (historyAlerts == null || historyAlerts.isEmpty()) {
            return List.of();
        }
        List<String> normalized = new ArrayList<>();
        for (String historyAlert : historyAlerts) {
            if (historyAlert == null) {
                continue;
            }
            String item = historyAlert.trim();
            if (!item.isBlank()) {
                normalized.add(item);
            }
        }
        return normalized.isEmpty() ? List.of() : List.copyOf(normalized);
    }

    private String buildNotificationMessage(String message, List<String> historyAlerts) {
        String base = message == null ? "" : message.trim();
        if (historyAlerts == null || historyAlerts.isEmpty()) {
            return base;
        }
        String alertSummary = historyAlerts.stream()
                .limit(2)
                .reduce((left, right) -> left + "；" + right)
                .orElse("");
        if (alertSummary.isBlank()) {
            return base;
        }
        if (base.isBlank()) {
            return "病史提醒：" + alertSummary;
        }
        return base + "\n病史提醒：" + alertSummary;
    }

    private String toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return null;
        }
    }

    private List<Map<String, Object>> readMapList(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<Map<String, Object>>>() {});
        } catch (Exception e) {
            return List.of();
        }
    }

    private List<String> readStringList(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            return List.of();
        }
    }
}
