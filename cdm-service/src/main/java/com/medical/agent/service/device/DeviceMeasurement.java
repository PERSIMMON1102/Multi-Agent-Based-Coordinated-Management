package com.medical.agent.service.device;

import com.fasterxml.jackson.databind.JsonNode;

public record DeviceMeasurement(String vendor,
                                String deviceType,
                                String deviceId,
                                String eventTime,
                                JsonNode properties) {
}
