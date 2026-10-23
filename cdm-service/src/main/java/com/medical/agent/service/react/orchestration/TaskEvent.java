package com.medical.agent.service.react.orchestration;

import java.time.LocalDateTime;
import java.util.Map;

public record TaskEvent(TaskEventType type,
                        LocalDateTime occurredAt,
                        String summary,
                        Map<String, Object> metadata) {
}
