package com.medical.agent.service;

import com.medical.agent.model.mongo.AgentMessageLog;
import com.medical.agent.model.mongo.repository.AgentMessageLogRepository;
import com.medical.agent.service.security.PrivacyLogSanitizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class AgentMessageAuditService {

    private static final Logger log = LoggerFactory.getLogger(AgentMessageAuditService.class);
    private static final java.util.Set<String> SENSITIVE_METADATA_KEYS = java.util.Set.of(
            "rawText",
            "rawModelJson",
            "ocrText",
            "payload",
            "payloadJson",
            "result",
            "resultJson",
            "body",
            "content"
    );

    private final AgentMessageLogRepository messageLogRepository;
    private final PrivacyLogSanitizer sanitizer = new PrivacyLogSanitizer();

    public AgentMessageAuditService(AgentMessageLogRepository messageLogRepository) {
        this.messageLogRepository = messageLogRepository;
    }

    public void log(String sender,
                    String receiver,
                    String performative,
                    String content,
                    Map<String, Object> metadata) {
        try {
            AgentMessageLog logEntry = AgentMessageLog.builder()
                    .senderAgent(normalizeAgentName(sender, "UnknownSender"))
                    .receiverAgent(normalizeAgentName(receiver, "UnknownReceiver"))
                    .performative(normalizeAgentName(performative, "INFORM"))
                    .content(normalizeContent(content))
                    .metadata(normalizeMetadata(metadata))
                    .timestamp(LocalDateTime.now())
                    .build();
            messageLogRepository.save(logEntry);
        } catch (Exception ex) {
            log.warn("Failed to persist agent message log {} -> {} ({})",
                    sender, receiver, performative, ex);
        }
    }

    private String normalizeAgentName(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.trim();
    }

    private String normalizeContent(String content) {
        if (content == null || content.isBlank()) {
            return "无摘要";
        }
        String digest = sanitizer.digest(content);
        String fingerprint = digest.isBlank() ? "" : digest.substring(0, Math.min(12, digest.length()));
        return "message(len=" + content.trim().length() + ", sha256=" + fingerprint + ")";
    }

    private Map<String, Object> normalizeMetadata(Map<String, Object> metadata) {
        LinkedHashMap<String, Object> normalized = new LinkedHashMap<>();
        if (metadata == null || metadata.isEmpty()) {
            return normalized;
        }
        metadata.forEach((key, value) -> {
            if (key != null && !key.isBlank() && value != null) {
                if (SENSITIVE_METADATA_KEYS.contains(key)) {
                    String text = String.valueOf(value);
                    String digest = sanitizer.digest(text);
                    String fingerprint = digest.isBlank() ? "" : digest.substring(0, Math.min(12, digest.length()));
                    normalized.put(key, "sanitized(len=" + text.trim().length() + ", sha256=" + fingerprint + ")");
                } else {
                    normalized.put(key, sanitizer.summarizeMap(Map.of(key, value)).get(key));
                }
            }
        });
        return normalized;
    }
}
