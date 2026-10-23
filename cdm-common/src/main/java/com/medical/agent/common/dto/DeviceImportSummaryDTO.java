package com.medical.agent.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeviceImportSummaryDTO {
    private Long patientId;
    private String vendor;
    private String vendorLabel;
    private String deviceType;
    private String deviceTypeLabel;
    private String sourceDeviceId;
    @Builder.Default
    private Integer totalRecords = 0;
    @Builder.Default
    private Integer importedRecords = 0;
    @Builder.Default
    private List<HealthRecordDTO> records = new ArrayList<>();
    @Builder.Default
    private List<String> warnings = new ArrayList<>();
    private String medplumSyncStatus;
    private String medplumSyncMessage;
    private ClinicalDataJobDTO job;
}
