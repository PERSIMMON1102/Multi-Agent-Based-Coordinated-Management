package com.medical.agent.service.device;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.medical.agent.common.dto.DeviceImportSummaryDTO;
import com.medical.agent.common.dto.HealthRecordDTO;
import com.medical.agent.common.exception.BusinessException;
import com.medical.agent.service.ClinicalDataModelService;
import com.medical.agent.service.HealthRecordService;
import com.medical.agent.service.MedplumAutoSyncService;
import com.medical.agent.service.clinicaljob.ClinicalDataJobOrchestrator;
import com.medical.agent.service.clinicaljob.ClinicalDataJobService;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class DeviceJsonImportService {

    private final ObjectMapper objectMapper = JsonMapper.builder().findAndAddModules().build();
    private final HealthRecordService healthRecordService;
    private final MedplumAutoSyncService medplumAutoSyncService;
    private final ClinicalDataJobService clinicalDataJobService;
    private final ClinicalDataModelService clinicalDataModelService;
    private final DeviceImportAdapterRegistry adapterRegistry;
    private final DeviceMeasurementMapperRegistry mapperRegistry;
    private final DeviceMeasurementDedupStrategy dedupStrategy;

    private static String vendorLabel(String vendor) {
        return ClinicalDataJobService.vendorLabel(vendor);
    }

    private static String deviceTypeLabel(String deviceType) {
        if (deviceType == null || deviceType.isBlank()) {
            return "通用设备";
        }
        return switch (deviceType.trim().toLowerCase()) {
            case "wearable" -> "可穿戴设备";
            case "blood_pressure_monitor" -> "血压设备";
            case "glucose_meter" -> "血糖设备";
            default -> deviceType.trim();
        };
    }

    public DeviceJsonImportService(HealthRecordService healthRecordService,
                                   MedplumAutoSyncService medplumAutoSyncService,
                                   ClinicalDataJobService clinicalDataJobService,
                                   ClinicalDataJobOrchestrator clinicalDataJobOrchestrator,
                                   ClinicalDataModelService clinicalDataModelService,
                                   DeviceImportAdapterRegistry adapterRegistry,
                                   DeviceMeasurementMapperRegistry mapperRegistry,
                                   DeviceMeasurementDedupStrategy dedupStrategy) {
        this.healthRecordService = healthRecordService;
        this.medplumAutoSyncService = medplumAutoSyncService;
        this.clinicalDataJobService = clinicalDataJobService;
        this.clinicalDataModelService = clinicalDataModelService;
        this.adapterRegistry = adapterRegistry;
        this.mapperRegistry = mapperRegistry;
        this.dedupStrategy = dedupStrategy;
    }

    public DeviceImportSummaryDTO importJson(Long patientId, byte[] payload, String preferredVendor, String jobType) {
        if (patientId == null) {
            throw new BusinessException(400, "patientId 不能为空");
        }
        if (payload == null || payload.length == 0) {
            throw new BusinessException(400, "导入 JSON 不能为空");
        }

        String rawJson = new String(payload, StandardCharsets.UTF_8);
        String idempotencyKey = buildIdempotencyKey("device-import", patientId, rawJson);
        var existingJob = clinicalDataJobService.findLatestByIdempotencyKey(idempotencyKey);
        if (existingJob != null) {
            return DeviceImportSummaryDTO.builder()
                    .patientId(patientId)
                    .warnings(List.of("检测到重复导入请求，已返回最近一次作业"))
                    .job(existingJob)
                    .medplumSyncStatus("duplicate")
                    .medplumSyncMessage("重复导入已被拦截，可通过 correlationId 查看既有作业链")
                    .build();
        }
        JsonNode root = readRoot(rawJson);
        DeviceImportAdapter adapter = adapterRegistry.resolve(root, preferredVendor);
        DeviceImportAdapter.DeviceImportEnvelope envelope = adapter.parse(root);
        if (envelope.measurements().isEmpty()) {
            throw new BusinessException(422, "未识别到可导入的设备测量数据");
        }

        ClinicalDataJobService.JobCreationResult jobCreation = clinicalDataJobService.createOrReuseJob(
                jobType,
                patientId,
                buildCorrelationId("device-import", patientId),
                idempotencyKey,
                ClinicalDataJobService.SOURCE_KIND_DEVICE_IMPORT,
                null,
                firstNonBlank(envelope.sourceDeviceId(), envelope.vendor()) + ".json",
                null,
                envelope.vendor(),
                Map.of(
                        "vendor", envelope.vendor(),
                        "deviceType", firstNonBlank(envelope.deviceType(), ""),
                        "sourceDeviceId", firstNonBlank(envelope.sourceDeviceId(), ""),
                        "payload", rawJson
                )
        );
        var job = jobCreation.job();
        if (!jobCreation.created()) {
            return DeviceImportSummaryDTO.builder()
                    .patientId(patientId)
                    .warnings(List.of("检测到重复导入请求，已复用既有作业"))
                    .job(clinicalDataJobService.toDTO(job))
                    .medplumSyncStatus("duplicate")
                    .medplumSyncMessage("重复导入已被拦截，可通过 correlationId 查看既有作业链")
                    .build();
        }

        var claimedJob = clinicalDataJobService.claimForExecution(job.getId(), "device_import_sync_dispatch");
        try {
            return processImportJob(
                    claimedJob.getId(),
                    claimedJob.getRunAttempt(),
                    patientId,
                    rawJson,
                    envelope.vendor()
            );
        } catch (IllegalStateException ex) {
            var currentJob = clinicalDataJobService.getRequired(job.getId());
            if (ClinicalDataJobService.STATUS_CANCELLED.equals(currentJob.getJobStatus())) {
                DeviceImportSummaryDTO summary = DeviceImportSummaryDTO.builder()
                        .patientId(patientId)
                        .vendor(envelope.vendor())
                        .vendorLabel(vendorLabel(envelope.vendor()))
                        .deviceType(envelope.deviceType())
                        .deviceTypeLabel(deviceTypeLabel(envelope.deviceType()))
                        .sourceDeviceId(envelope.sourceDeviceId())
                        .totalRecords(envelope.measurements().size())
                        .importedRecords(0)
                        .records(List.of())
                        .warnings(List.of("导入作业已取消"))
                        .job(clinicalDataJobService.toDTO(currentJob))
                        .build();
                summary.setMedplumSyncStatus("cancelled");
                summary.setMedplumSyncMessage("导入作业已取消，未继续执行后续处理");
                return summary;
            }
            throw ex;
        }
    }

    public DeviceImportSummaryDTO processImportJob(Long jobId, Long runAttempt, Long patientId, String rawJson, String preferredVendor) {
        clinicalDataJobService.markStarted(jobId, runAttempt, "received");
        clinicalDataJobService.assertExecutable(jobId, runAttempt, "json_parse");
        JsonNode root = readRootOrFail(jobId, runAttempt, rawJson);
        DeviceImportAdapter adapter = adapterRegistry.resolve(root, preferredVendor);
        DeviceImportAdapter.DeviceImportEnvelope envelope = adapter.parse(root);
        if (envelope.measurements().isEmpty()) {
            clinicalDataJobService.markFailure(jobId, runAttempt, "parse_failed", "未识别到可导入测量数据");
            throw new BusinessException(422, "未识别到可导入的设备测量数据");
        }
        clinicalDataJobService.markStage(jobId, runAttempt, ClinicalDataJobService.STATUS_PARSED, "json_parsed", Map.of(
                "vendor", envelope.vendor(),
                "measurementCount", envelope.measurements().size()
        ));
        clinicalDataJobService.assertExecutable(jobId, runAttempt, "metric_mapping");

        DeviceMeasurementMapper mapper = mapperRegistry.require(envelope.vendor());
        List<HealthRecordDTO> candidates = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        for (DeviceMeasurement measurement : envelope.measurements()) {
            HealthRecordDTO candidate = mapper.toHealthRecord(patientId, measurement);
            if (!mapper.hasImportableMetric(candidate)) {
                warnings.add("已跳过 1 条记录：未包含可映射的健康指标");
                continue;
            }
            candidates.add(candidate);
        }

        List<HealthRecordDTO> normalizedRecords = dedupStrategy.dedup(candidates);
        List<HealthRecordDTO> imported = new ArrayList<>();
        for (HealthRecordDTO candidate : normalizedRecords) {
            HealthRecordDTO created = healthRecordService.create(candidate, null, true, Map.of(
                    "source", candidate.getSourceVendor() + "_iot_import",
                    "vendor", candidate.getSourceVendor(),
                    "_bestEffortAssessment", true,
                    "_deferAssessment", true
            ));
            clinicalDataModelService.recordMeasurementSource(
                    created,
                    null,
                    ClinicalDataJobService.SOURCE_KIND_DEVICE_IMPORT,
                    null,
                    candidate.getSourceVendor(),
                    candidate.getSourceDeviceType(),
                    candidate.getSourceDeviceId(),
                    Map.of("recordedAt", firstNonBlank(candidate.getRecordedAt() == null ? null : candidate.getRecordedAt().toString(), "")),
                    Map.of(
                            "sourceDeviceId", firstNonBlank(candidate.getSourceDeviceId(), ""),
                            "sourceDeviceType", firstNonBlank(candidate.getSourceDeviceType(), "")
                    )
            );
            imported.add(created);
        }

        if (imported.isEmpty()) {
            clinicalDataJobService.markFailure(jobId, runAttempt, "mapping_failed", "设备 JSON 已解析，但未找到可导入的有效健康指标");
            throw new BusinessException(422, "设备 JSON 已解析，但未找到可导入的有效健康指标");
        }

        clinicalDataJobService.markStage(jobId, runAttempt, ClinicalDataJobService.STATUS_MAPPED, "metrics_mapped", Map.of(
                "vendor", envelope.vendor(),
                "importedRecords", imported.size(),
                "warningCount", warnings.size(),
                "deduplicatedRecords", normalizedRecords.size()
        ));
        clinicalDataJobService.assertExecutable(jobId, runAttempt, "medplum_sync");

        DeviceImportSummaryDTO summary = DeviceImportSummaryDTO.builder()
                .patientId(patientId)
                .vendor(envelope.vendor())
                .vendorLabel(vendorLabel(envelope.vendor()))
                .deviceType(envelope.deviceType())
                .deviceTypeLabel(deviceTypeLabel(envelope.deviceType()))
                .sourceDeviceId(envelope.sourceDeviceId())
                .totalRecords(envelope.measurements().size())
                .importedRecords(imported.size())
                .records(imported)
                .warnings(warnings)
                .build();
        DeviceImportSummaryDTO synced = medplumAutoSyncService.syncImportedHealthData(summary, patientId);
        if (MedplumAutoSyncService.isSuccessfulStatus(synced.getMedplumSyncStatus())) {
            clinicalDataJobService.markStage(jobId, runAttempt, ClinicalDataJobService.STATUS_SYNCED,
                    MedplumAutoSyncService.isSkippedStatus(synced.getMedplumSyncStatus())
                            ? "medplum_sync_skipped"
                            : "medplum_synced",
                    Map.of(
                    "vendor", envelope.vendor(),
                    "importedRecords", imported.size(),
                    "medplumSyncStatus", synced.getMedplumSyncStatus()
            ));
        } else {
            clinicalDataJobService.markFailure(jobId, runAttempt, "medplum_sync_failed", firstNonBlank(
                    synced.getMedplumSyncMessage(),
                    "Medplum 自动同步失败"
            ));
        }
        synced.setJob(clinicalDataJobService.toDTO(clinicalDataJobService.getRequired(jobId)));
        return synced;
    }

    public DeviceImportSummaryDTO processImportJob(Long jobId, Long patientId, String rawJson, String preferredVendor) {
        Long runAttempt = clinicalDataJobService.getRequired(jobId).getRunAttempt();
        return processImportJob(jobId, runAttempt, patientId, rawJson, preferredVendor);
    }

    private JsonNode readRoot(String rawJson) {
        try {
            return objectMapper.readTree(rawJson);
        } catch (IOException ex) {
            throw new BusinessException(400, "设备 JSON 解析失败: " + ex.getMessage());
        }
    }

    private JsonNode readRootOrFail(Long jobId, Long runAttempt, String rawJson) {
        try {
            return objectMapper.readTree(rawJson);
        } catch (IOException ex) {
            clinicalDataJobService.markFailure(jobId, runAttempt, "parse_failed", ex.getMessage());
            throw new BusinessException(400, "设备 JSON 解析失败: " + ex.getMessage());
        }
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    private String buildCorrelationId(String prefix, Long patientId) {
        return prefix + "-" + patientId + "-" + UUID.randomUUID();
    }

    private String buildIdempotencyKey(String prefix, Long patientId, String rawPayload) {
        return prefix + ":" + patientId + ":" + sha256(rawPayload == null ? "" : rawPayload.trim());
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                builder.append(String.format("%02x", b));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 算法不可用", ex);
        }
    }
}
