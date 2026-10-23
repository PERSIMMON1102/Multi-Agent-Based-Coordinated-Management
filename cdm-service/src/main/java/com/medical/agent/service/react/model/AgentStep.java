package com.medical.agent.service.react.model;

import java.util.Map;

public record AgentStep(int index,
                        String reasoningSummary,
                        String actionName,
                        Map<String, Object> actionInput,
                        String observationSummary) {
}
