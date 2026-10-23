package com.medical.agent.service.chat.agent;

import java.util.Map;

public record ChatSpecialistExecutionResult(boolean success,
                                            String reasoningSummary,
                                            Map<String, Object> actionInput,
                                            String observationSummary) {
}
