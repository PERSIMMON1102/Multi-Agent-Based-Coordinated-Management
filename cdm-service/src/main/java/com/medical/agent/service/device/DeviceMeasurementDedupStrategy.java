package com.medical.agent.service.device;

import com.medical.agent.common.dto.HealthRecordDTO;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class DeviceMeasurementDedupStrategy {

    public List<HealthRecordDTO> dedup(List<HealthRecordDTO> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }
        Map<String, HealthRecordDTO> unique = new LinkedHashMap<>();
        for (HealthRecordDTO candidate : candidates) {
            if (candidate == null) {
                continue;
            }
            unique.put(buildKey(candidate), candidate);
        }
        return List.copyOf(unique.values());
    }

    private String buildKey(HealthRecordDTO dto) {
        return String.join("|",
                String.valueOf(dto.getPatientId()),
                nullSafe(dto.getSourceVendor()),
                nullSafe(dto.getSourceDeviceType()),
                nullSafe(dto.getSourceDeviceId()),
                dto.getRecordedAt() == null ? "" : dto.getRecordedAt().toString(),
                nullSafe(String.valueOf(dto.getSystolicBp())),
                nullSafe(String.valueOf(dto.getDiastolicBp())),
                nullSafe(String.valueOf(dto.getRestingHeartRate())),
                nullSafe(String.valueOf(dto.getFastingGlucose())),
                nullSafe(String.valueOf(dto.getPostprandialGlucose())),
                nullSafe(String.valueOf(dto.getWeight())),
                nullSafe(String.valueOf(dto.getHeight()))
        );
    }

    private String nullSafe(String value) {
        return value == null ? "" : value;
    }
}
