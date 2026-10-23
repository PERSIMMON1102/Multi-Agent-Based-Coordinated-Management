package com.medical.agent.service.device;

import com.medical.agent.common.dto.HealthRecordDTO;

public interface DeviceMeasurementMapper {

    boolean supports(String vendor);

    HealthRecordDTO toHealthRecord(Long patientId, DeviceMeasurement measurement);

    boolean hasImportableMetric(HealthRecordDTO dto);
}
