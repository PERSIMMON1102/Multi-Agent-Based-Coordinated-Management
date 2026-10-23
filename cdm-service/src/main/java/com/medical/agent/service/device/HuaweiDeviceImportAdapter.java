package com.medical.agent.service.device;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class HuaweiDeviceImportAdapter implements DeviceImportAdapter {

    private final DeviceImportNormalizer normalizer;

    public HuaweiDeviceImportAdapter(DeviceImportNormalizer normalizer) {
        this.normalizer = normalizer;
    }

    @Override
    public String vendor() {
        return "huawei";
    }

    @Override
    public boolean supports(JsonNode root) {
        if (root == null || root.isNull()) {
            return false;
        }
        if (root.isArray()) {
            return root.size() > 0 && supports(root.get(0));
        }
        return root.has("services") || root.has("shadow") || root.has("properties");
    }

    @Override
    public DeviceImportEnvelope parse(JsonNode root) {
        List<DeviceMeasurement> measurements = extractMeasurements(root);
        if (measurements.isEmpty()) {
            return new DeviceImportEnvelope(vendor(), null, null, List.of());
        }
        DeviceMeasurement first = measurements.get(0);
        return new DeviceImportEnvelope(vendor(), first.deviceType(), first.deviceId(), measurements);
    }

    private List<DeviceMeasurement> extractMeasurements(JsonNode root) {
        List<DeviceMeasurement> measurements = new ArrayList<>();
        if (root == null || root.isNull()) {
            return measurements;
        }
        if (root.isArray()) {
            for (JsonNode item : root) {
                measurements.addAll(extractMeasurements(item));
            }
            return measurements;
        }

        if (root.has("services") && root.get("services").isArray()) {
            String deviceId = normalizer.textOf(root, "device_id", "deviceId");
            String deviceType = normalizer.textOf(root, "device_type", "deviceType");
            for (JsonNode service : root.get("services")) {
                JsonNode properties = service.get("properties");
                if (properties == null || !properties.isObject()) {
                    continue;
                }
                measurements.add(new DeviceMeasurement(
                        vendor(),
                        resolveDeviceType(deviceType, service),
                        normalizer.firstNonBlank(deviceId, normalizer.textOf(service, "device_id", "deviceId")),
                        normalizer.textOf(service, "event_time", "eventTime", "report_time", "timestamp"),
                        properties
                ));
            }
        }

        if (root.has("shadow") && root.get("shadow").isArray()) {
            String deviceId = normalizer.textOf(root, "device_id", "deviceId");
            String deviceType = normalizer.textOf(root, "device_type", "deviceType");
            for (JsonNode shadow : root.get("shadow")) {
                JsonNode reported = shadow.path("reported");
                JsonNode properties = reported.path("properties");
                if (!properties.isObject()) {
                    continue;
                }
                measurements.add(new DeviceMeasurement(
                        vendor(),
                        resolveDeviceType(deviceType, shadow),
                        normalizer.firstNonBlank(deviceId, normalizer.textOf(shadow, "device_id", "deviceId")),
                        normalizer.textOf(reported, "event_time", "eventTime", "report_time", "timestamp"),
                        properties
                ));
            }
        }

        if (measurements.isEmpty() && root.isObject() && root.has("properties")) {
            measurements.add(new DeviceMeasurement(
                    vendor(),
                    normalizer.inferDeviceType(root.path("properties")),
                    normalizer.textOf(root, "device_id", "deviceId"),
                    normalizer.textOf(root, "event_time", "eventTime", "report_time", "timestamp"),
                    root.path("properties")
            ));
        }

        return measurements;
    }

    private String resolveDeviceType(String rootType, JsonNode node) {
        return normalizer.firstNonBlank(
                rootType,
                normalizer.textOf(node, "device_type", "deviceType", "service_type", "serviceType"),
                normalizer.inferDeviceType(node.path("properties"))
        );
    }
}
