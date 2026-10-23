package com.medical.agent.service.device;

import com.medical.agent.common.dto.DeviceImportSummaryDTO;
import com.medical.agent.common.dto.HealthRecordDTO;
import com.medical.agent.model.entity.ClinicalDataJob;
import com.medical.agent.service.ClinicalDataModelService;
import com.medical.agent.service.HealthRecordService;
import com.medical.agent.service.MedplumAutoSyncService;
import com.medical.agent.service.clinicaljob.ClinicalDataJobOrchestrator;
import com.medical.agent.service.clinicaljob.ClinicalDataJobService;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HuaweiDeviceJsonImportServiceTest {

    @Test
    void shouldImportHuaweiServicesPayloadToHealthRecords() {
        AtomicInteger importedCount = new AtomicInteger();
        AtomicReference<HealthRecordDTO> lastImported = new AtomicReference<>();

        HuaweiDeviceJsonImportService service = createService(importedCount, lastImported, new StubMedplumAutoSyncService(), new StubClinicalDataJobService());
        String json = """
                {
                  "device_id": "HW-BP-0001",
                  "device_type": "blood_pressure_monitor",
                  "services": [
                    {
                      "service_id": "health",
                      "service_type": "blood_pressure_monitor",
                      "event_time": "2026-05-28T08:30:00Z",
                      "properties": {
                        "systolic_bp": 128,
                        "diastolic_bp": 82,
                        "heart_rate": 74,
                        "weight": 67.5,
                        "height": 170
                      }
                    },
                    {
                      "service_id": "health",
                      "service_type": "blood_pressure_monitor",
                      "event_time": "2026-05-28T20:10:00Z",
                      "properties": {
                        "systolic_bp": 135,
                        "diastolic_bp": 86,
                        "heart_rate": 78
                      }
                    }
                  ]
                }
                """;

        DeviceImportSummaryDTO result = service.importJson(12L, json.getBytes(StandardCharsets.UTF_8));

        assertNotNull(result);
        assertEquals(12L, result.getPatientId());
        assertEquals("huawei", result.getVendor());
        assertEquals("blood_pressure_monitor", result.getDeviceType());
        assertEquals("HW-BP-0001", result.getSourceDeviceId());
        assertEquals(2, result.getTotalRecords());
        assertEquals(2, result.getImportedRecords());
        assertEquals(2, importedCount.get());
        assertEquals("synced", result.getMedplumSyncStatus());
        assertNotNull(result.getJob());
        assertEquals(ClinicalDataJobService.STATUS_SYNCED, result.getJob().getJobStatus());
        assertNotNull(lastImported.get());
        assertEquals("iot_import", lastImported.get().getDataSource());
        assertEquals("huawei", lastImported.get().getSourceVendor());
        assertEquals("blood_pressure_monitor", lastImported.get().getSourceDeviceType());
        assertEquals("HW-BP-0001", lastImported.get().getSourceDeviceId());
        assertEquals(LocalDateTime.of(2026, 5, 29, 4, 10), lastImported.get().getRecordedAt());
        assertTrue(lastImported.get().getSystolicBp() >= 128);
    }

    @Test
    void shouldMarkJobFailedWhenMedplumAutoSyncFails() {
        AtomicInteger importedCount = new AtomicInteger();
        AtomicReference<HealthRecordDTO> lastImported = new AtomicReference<>();
        StubClinicalDataJobService jobService = new StubClinicalDataJobService();

        HuaweiDeviceJsonImportService service = createService(importedCount, lastImported, new FailingStubMedplumAutoSyncService(), jobService);
        String json = """
                {
                  "device_id": "HW-BP-0002",
                  "device_type": "blood_pressure_monitor",
                  "services": [
                    {
                      "service_id": "health",
                      "service_type": "blood_pressure_monitor",
                      "event_time": "2026-05-28T08:30:00Z",
                      "properties": {
                        "systolic_bp": 128,
                        "diastolic_bp": 82
                      }
                    }
                  ]
                }
                """;

        DeviceImportSummaryDTO result = service.importJson(18L, json.getBytes(StandardCharsets.UTF_8));

        assertEquals("failed", jobService.getRequired(1L).getJobStatus());
        assertEquals("medplum_sync_failed", jobService.getRequired(1L).getLastStage());
        assertEquals("failed", result.getMedplumSyncStatus());
    }

    @Test
    void shouldMarkJobSyncedWhenMedplumAutoSyncIsSkipped() {
        AtomicInteger importedCount = new AtomicInteger();
        AtomicReference<HealthRecordDTO> lastImported = new AtomicReference<>();
        StubClinicalDataJobService jobService = new StubClinicalDataJobService();

        HuaweiDeviceJsonImportService service = createService(importedCount, lastImported, new SkippingStubMedplumAutoSyncService(), jobService);
        String json = """
                {
                  "device_id": "HW-BP-0005",
                  "device_type": "blood_pressure_monitor",
                  "services": [
                    {
                      "service_id": "health",
                      "service_type": "blood_pressure_monitor",
                      "event_time": "2026-05-28T08:30:00Z",
                      "properties": {
                        "systolic_bp": 128,
                        "diastolic_bp": 82
                      }
                    }
                  ]
                }
                """;

        DeviceImportSummaryDTO result = service.importJson(28L, json.getBytes(StandardCharsets.UTF_8));

        assertEquals(ClinicalDataJobService.STATUS_SYNCED, jobService.getRequired(1L).getJobStatus());
        assertEquals("medplum_sync_skipped", jobService.getRequired(1L).getLastStage());
        assertEquals("skipped", result.getMedplumSyncStatus());
    }

    @Test
    void shouldDeduplicateDuplicateMeasurementsWithinSamePayload() {
        AtomicInteger importedCount = new AtomicInteger();
        AtomicReference<HealthRecordDTO> lastImported = new AtomicReference<>();

        HuaweiDeviceJsonImportService service = createService(
                importedCount,
                lastImported,
                new StubMedplumAutoSyncService(),
                new StubClinicalDataJobService()
        );
        String json = """
                {
                  "device_id": "HW-BP-0003",
                  "device_type": "blood_pressure_monitor",
                  "services": [
                    {
                      "service_id": "health",
                      "service_type": "blood_pressure_monitor",
                      "event_time": "2026-05-28T08:30:00Z",
                      "properties": {
                        "systolic_bp": 128,
                        "diastolic_bp": 82
                      }
                    },
                    {
                      "service_id": "health",
                      "service_type": "blood_pressure_monitor",
                      "event_time": "2026-05-28T08:30:00Z",
                      "properties": {
                        "systolic_bp": 128,
                        "diastolic_bp": 82
                      }
                    }
                  ]
                }
                """;

        DeviceImportSummaryDTO result = service.importJson(22L, json.getBytes(StandardCharsets.UTF_8));

        assertEquals(2, result.getTotalRecords());
        assertEquals(1, result.getImportedRecords());
        assertEquals(1, importedCount.get());
        assertNotNull(lastImported.get());
        assertEquals("HW-BP-0003", lastImported.get().getSourceDeviceId());
    }

    @Test
    void shouldReturnExistingJobWhenDuplicatePayloadSubmitted() {
        AtomicInteger importedCount = new AtomicInteger();
        AtomicReference<HealthRecordDTO> lastImported = new AtomicReference<>();
        StubClinicalDataJobService jobService = new StubClinicalDataJobService();
        jobService.duplicateJob = com.medical.agent.common.dto.ClinicalDataJobDTO.builder()
                .id(77L)
                .jobType(ClinicalDataJobService.JOB_TYPE_HUAWEI_IMPORT)
                .jobStatus(ClinicalDataJobService.STATUS_RECEIVED)
                .correlationId("device-import-12-existing")
                .idempotencyKey("duplicate-key")
                .build();

        HuaweiDeviceJsonImportService service = createService(importedCount, lastImported, new StubMedplumAutoSyncService(), jobService);
        String json = """
                {
                  "device_id": "HW-BP-0001",
                  "device_type": "blood_pressure_monitor",
                  "services": [
                    {
                      "service_id": "health",
                      "service_type": "blood_pressure_monitor",
                      "event_time": "2026-05-28T08:30:00Z",
                      "properties": {
                        "systolic_bp": 128,
                        "diastolic_bp": 82
                      }
                    }
                  ]
                }
                """;

        DeviceImportSummaryDTO result = service.importJson(12L, json.getBytes(StandardCharsets.UTF_8));

        assertEquals("duplicate", result.getMedplumSyncStatus());
        assertEquals(77L, result.getJob().getId());
        assertEquals(0, importedCount.get());
        assertNull(lastImported.get());
    }

    @Test
    void shouldStopDeviceImportWhenCancelledAfterParse() {
        AtomicInteger importedCount = new AtomicInteger();
        AtomicReference<HealthRecordDTO> lastImported = new AtomicReference<>();
        AtomicBoolean medplumCalled = new AtomicBoolean(false);
        StubClinicalDataJobService jobService = new StubClinicalDataJobService();
        jobService.cancelAfterStage = "json_parsed";

        HuaweiDeviceJsonImportService service = createService(
                importedCount,
                lastImported,
                new RecordingStubMedplumAutoSyncService(medplumCalled),
                jobService
        );
        String json = """
                {
                  "device_id": "HW-BP-0004",
                  "device_type": "blood_pressure_monitor",
                  "services": [
                    {
                      "service_id": "health",
                      "service_type": "blood_pressure_monitor",
                      "event_time": "2026-05-28T08:30:00Z",
                      "properties": {
                        "systolic_bp": 128,
                        "diastolic_bp": 82
                      }
                    }
                  ]
                }
                """;

        DeviceImportSummaryDTO result = service.importJson(19L, json.getBytes(StandardCharsets.UTF_8));

        assertEquals("cancelled", result.getMedplumSyncStatus());
        assertEquals("cancelled", result.getJob().getJobStatus());
        assertEquals("manual_cancel", result.getJob().getLastStage());
        assertEquals(0, importedCount.get());
        assertNull(lastImported.get());
        assertFalse(medplumCalled.get());
    }

    private HuaweiDeviceJsonImportService createService(AtomicInteger importedCount,
                                                        AtomicReference<HealthRecordDTO> lastImported,
                                                        MedplumAutoSyncService medplumAutoSyncService,
                                                        StubClinicalDataJobService jobService) {
        DeviceImportNormalizer normalizer = new DeviceImportNormalizer();
        DeviceJsonImportService deviceJsonImportService = new DeviceJsonImportService(
                new StubHealthRecordService(importedCount, lastImported),
                medplumAutoSyncService,
                jobService,
                new StubClinicalDataJobOrchestrator(),
                new StubClinicalDataModelService(),
                new DeviceImportAdapterRegistry(java.util.List.of(new HuaweiDeviceImportAdapter(normalizer))),
                new DeviceMeasurementMapperRegistry(java.util.List.of(new HuaweiMeasurementMapper(normalizer))),
                new DeviceMeasurementDedupStrategy()
        );
        return new HuaweiDeviceJsonImportService(deviceJsonImportService);
    }

    private static final class StubHealthRecordService extends HealthRecordService {
        private final AtomicInteger importedCount;
        private final AtomicReference<HealthRecordDTO> lastImported;

        private StubHealthRecordService(AtomicInteger importedCount,
                                        AtomicReference<HealthRecordDTO> lastImported) {
            super(null, null, null);
            this.importedCount = importedCount;
            this.lastImported = lastImported;
        }

        @Override
        public HealthRecordDTO create(HealthRecordDTO dto,
                                      com.medical.agent.common.enums.DiseaseType diseaseType,
                                      boolean triggerAssessment,
                                      Map<String, Object> assessmentOptions) {
            importedCount.incrementAndGet();
            lastImported.set(dto);
            dto.setId((long) importedCount.get());
            return dto;
        }
    }

    private static final class StubMedplumAutoSyncService extends MedplumAutoSyncService {

        private StubMedplumAutoSyncService() {
            super(null, null);
        }

        @Override
        public DeviceImportSummaryDTO syncImportedHealthData(DeviceImportSummaryDTO summary, Long patientId) {
            summary.setMedplumSyncStatus("synced");
            summary.setMedplumSyncMessage("stub synced");
            return summary;
        }
    }

    private static final class FailingStubMedplumAutoSyncService extends MedplumAutoSyncService {

        private FailingStubMedplumAutoSyncService() {
            super(null, null);
        }

        @Override
        public DeviceImportSummaryDTO syncImportedHealthData(DeviceImportSummaryDTO summary, Long patientId) {
            summary.setMedplumSyncStatus("failed");
            summary.setMedplumSyncMessage("stub sync failed");
            return summary;
        }
    }

    private static final class SkippingStubMedplumAutoSyncService extends MedplumAutoSyncService {

        private SkippingStubMedplumAutoSyncService() {
            super(null, null);
        }

        @Override
        public DeviceImportSummaryDTO syncImportedHealthData(DeviceImportSummaryDTO summary, Long patientId) {
            summary.setMedplumSyncStatus("skipped");
            summary.setMedplumSyncMessage("stub skipped");
            return summary;
        }
    }

    private static final class RecordingStubMedplumAutoSyncService extends MedplumAutoSyncService {
        private final AtomicBoolean called;

        private RecordingStubMedplumAutoSyncService(AtomicBoolean called) {
            super(null, null);
            this.called = called;
        }

        @Override
        public DeviceImportSummaryDTO syncImportedHealthData(DeviceImportSummaryDTO summary, Long patientId) {
            called.set(true);
            summary.setMedplumSyncStatus("synced");
            summary.setMedplumSyncMessage("stub synced");
            return summary;
        }
    }

    private static final class StubClinicalDataJobService extends ClinicalDataJobService {
        private ClinicalDataJob job;
        private com.medical.agent.common.dto.ClinicalDataJobDTO duplicateJob;
        private String cancelAfterStage;

        private StubClinicalDataJobService() {
            super(null);
        }

        @Override
        public ClinicalDataJob createJob(String jobType,
                                         Long patientId,
                                         String correlationId,
                                         String idempotencyKey,
                                         String sourceKind,
                                         Long sourceRefId,
                                         String sourceFileName,
                                         String sourceFileUrl,
                                         String vendor,
                                         Map<String, Object> payload) {
            job = ClinicalDataJob.builder()
                    .id(1L)
                    .jobType(jobType)
                    .jobStatus(STATUS_RECEIVED)
                    .patientId(patientId)
                    .correlationId(correlationId)
                    .idempotencyKey(idempotencyKey)
                    .sourceKind(sourceKind)
                    .sourceRefId(sourceRefId)
                    .sourceFileName(sourceFileName)
                    .sourceFileUrl(sourceFileUrl)
                    .vendor(vendor)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();
            return job;
        }

        @Override
        public JobCreationResult createOrReuseJob(String jobType,
                                                  Long patientId,
                                                  String correlationId,
                                                  String idempotencyKey,
                                                  String sourceKind,
                                                  Long sourceRefId,
                                                  String sourceFileName,
                                                  String sourceFileUrl,
                                                  String vendor,
                                                  Map<String, Object> payload) {
            if (duplicateJob != null) {
                job = ClinicalDataJob.builder()
                        .id(duplicateJob.getId())
                        .jobType(duplicateJob.getJobType())
                        .jobStatus(duplicateJob.getJobStatus())
                        .patientId(patientId)
                        .correlationId(duplicateJob.getCorrelationId())
                        .idempotencyKey(duplicateJob.getIdempotencyKey())
                        .sourceKind(sourceKind)
                        .sourceRefId(sourceRefId)
                        .sourceFileName(sourceFileName)
                        .sourceFileUrl(sourceFileUrl)
                        .vendor(vendor)
                        .build();
                return new JobCreationResult(job, false);
            }
            return new JobCreationResult(createJob(
                    jobType,
                    patientId,
                    correlationId,
                    idempotencyKey,
                    sourceKind,
                    sourceRefId,
                    sourceFileName,
                    sourceFileUrl,
                    vendor,
                    payload
            ), true);
        }

        @Override
        public ClinicalDataJob markStarted(Long jobId, String stage) {
            job.setLastStage(stage);
            job.setStartedAt(LocalDateTime.now());
            return job;
        }

        @Override
        public ClinicalDataJob markStarted(Long jobId, Long runAttempt, String stage) {
            job.setRunAttempt(runAttempt);
            return markStarted(jobId, stage);
        }

        @Override
        public ClinicalDataJob markStage(Long jobId, String status, String stage, Map<String, Object> result) {
            job.setJobStatus(status);
            job.setLastStage(stage);
            job.setUpdatedAt(LocalDateTime.now());
            if (cancelAfterStage != null && cancelAfterStage.equals(stage)) {
                job.setJobStatus(STATUS_CANCELLED);
                job.setLastStage("manual_cancel");
                job.setLastError("cancelled during test");
            }
            return job;
        }

        @Override
        public ClinicalDataJob markStage(Long jobId, Long runAttempt, String status, String stage, Map<String, Object> result) {
            job.setRunAttempt(runAttempt);
            return markStage(jobId, status, stage, result);
        }

        @Override
        public ClinicalDataJob markFailure(Long jobId, String stage, String message) {
            job.setJobStatus(STATUS_FAILED);
            job.setLastStage(stage);
            job.setLastError(message);
            return job;
        }

        @Override
        public ClinicalDataJob markFailure(Long jobId, Long runAttempt, String stage, String message) {
            job.setRunAttempt(runAttempt);
            return markFailure(jobId, stage, message);
        }

        @Override
        public ClinicalDataJob getRequired(Long jobId) {
            return job;
        }

        @Override
        public ClinicalDataJob assertExecutable(Long jobId, Long runAttempt, String stage) {
            if (STATUS_CANCELLED.equals(job.getJobStatus())) {
                throw new IllegalStateException("clinical data job is cancelled before stage " + stage + ": " + jobId);
            }
            return job;
        }

        @Override
        public ClinicalDataJob claimForExecution(Long jobId, String stage) {
            job.setJobStatus(STATUS_RUNNING);
            job.setLastStage(stage);
            job.setStartedAt(LocalDateTime.now());
            job.setRunAttempt((job.getRunAttempt() == null ? 0L : job.getRunAttempt()) + 1L);
            return job;
        }

        @Override
        public com.medical.agent.common.dto.ClinicalDataJobDTO findLatestByIdempotencyKey(String idempotencyKey) {
            return duplicateJob;
        }

        @Override
        public com.medical.agent.common.dto.ClinicalDataJobDTO toDTO(ClinicalDataJob job) {
            return job == null ? null : com.medical.agent.common.dto.ClinicalDataJobDTO.builder()
                    .id(job.getId())
                    .jobType(job.getJobType())
                    .jobStatus(job.getJobStatus())
                    .correlationId(job.getCorrelationId())
                    .idempotencyKey(job.getIdempotencyKey())
                    .sourceKind(job.getSourceKind())
                    .sourceRefId(job.getSourceRefId())
                    .sourceFileName(job.getSourceFileName())
                    .sourceFileUrl(job.getSourceFileUrl())
                    .vendor(job.getVendor())
                    .lastStage(job.getLastStage())
                    .build();
        }
    }

    private static final class StubClinicalDataJobOrchestrator extends ClinicalDataJobOrchestrator {
        private StubClinicalDataJobOrchestrator() {
            super(Runnable::run);
        }
    }

    private static final class StubClinicalDataModelService extends ClinicalDataModelService {
        private StubClinicalDataModelService() {
            super(null, null, null, null, null);
        }

        @Override
        public void recordMeasurementSource(HealthRecordDTO createdRecord,
                                            Long encounterId,
                                            String sourceKind,
                                            Long sourceRefId,
                                            String vendor,
                                            String deviceType,
                                            String deviceId,
                                            Map<String, Object> rawPayload,
                                            Map<String, Object> normalizedPayload) {
        }
    }
}
