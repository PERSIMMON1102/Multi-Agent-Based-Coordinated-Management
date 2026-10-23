package com.medical.agent.service.device;

import com.medical.agent.common.dto.DeviceImportSummaryDTO;
import com.medical.agent.service.clinicaljob.ClinicalDataJobService;
import org.springframework.stereotype.Service;

@Service
public class HuaweiDeviceJsonImportService {

    private final DeviceJsonImportService deviceJsonImportService;

    public HuaweiDeviceJsonImportService(DeviceJsonImportService deviceJsonImportService) {
        this.deviceJsonImportService = deviceJsonImportService;
    }

    public DeviceImportSummaryDTO importJson(Long patientId, byte[] payload) {
        return deviceJsonImportService.importJson(
                patientId,
                payload,
                "huawei",
                ClinicalDataJobService.JOB_TYPE_HUAWEI_IMPORT
        );
    }

    public DeviceImportSummaryDTO processImportJob(Long jobId, Long patientId, String rawJson) {
        return deviceJsonImportService.processImportJob(jobId, patientId, rawJson, "huawei");
    }
}
