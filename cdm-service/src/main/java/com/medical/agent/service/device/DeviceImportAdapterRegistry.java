package com.medical.agent.service.device;

import com.fasterxml.jackson.databind.JsonNode;
import com.medical.agent.common.exception.BusinessException;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class DeviceImportAdapterRegistry {

    private final List<DeviceImportAdapter> adapters;

    public DeviceImportAdapterRegistry(List<DeviceImportAdapter> adapters) {
        this.adapters = adapters;
    }

    public DeviceImportAdapter resolve(JsonNode root, String preferredVendor) {
        if (preferredVendor != null && !preferredVendor.isBlank()) {
            for (DeviceImportAdapter adapter : adapters) {
                if (preferredVendor.equalsIgnoreCase(adapter.vendor())) {
                    return adapter;
                }
            }
            throw new BusinessException(400, "未找到可用的设备数据适配器: " + preferredVendor);
        }

        return adapters.stream()
                .filter(adapter -> adapter.supports(root))
                .findFirst()
                .orElseThrow(() -> new BusinessException(422, "未识别到可导入的设备 JSON 格式"));
    }
}
