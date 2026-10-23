package com.medical.agent.service.device;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

public interface DeviceImportAdapter {

    String vendor();

    boolean supports(JsonNode root);

    DeviceImportEnvelope parse(JsonNode root);

    record DeviceImportEnvelope(String vendor,
                                String deviceType,
                                String sourceDeviceId,
                                List<DeviceMeasurement> measurements) {
    }
}
