package com.medical.agent.service.device;

import com.medical.agent.common.exception.BusinessException;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class DeviceMeasurementMapperRegistry {

    private final List<DeviceMeasurementMapper> mappers;

    public DeviceMeasurementMapperRegistry(List<DeviceMeasurementMapper> mappers) {
        this.mappers = mappers;
    }

    public DeviceMeasurementMapper require(String vendor) {
        return mappers.stream()
                .filter(mapper -> mapper.supports(vendor))
                .findFirst()
                .orElseThrow(() -> new BusinessException(400, "未找到设备指标映射器: " + vendor));
    }
}
