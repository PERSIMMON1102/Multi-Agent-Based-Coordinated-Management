package com.medical.agent.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.medical.agent.common.dto.DeviceImportSummaryDTO;
import com.medical.agent.common.dto.HealthRecordDTO;
import com.medical.agent.common.dto.PaperCaseIcdCandidateDTO;
import com.medical.agent.common.dto.PaperCaseRecordDTO;
import com.medical.agent.model.entity.ClinicalAttachment;
import com.medical.agent.model.entity.ClinicalDataJob;
import com.medical.agent.model.entity.ClinicalEncounter;
import com.medical.agent.model.entity.EncounterDiagnosis;
import com.medical.agent.model.entity.HealthRecord;
import com.medical.agent.model.entity.PaperCaseRecord;
import com.medical.agent.model.entity.Patient;
import com.medical.agent.model.entity.PatientMedicalHistory;
import com.medical.agent.model.repository.ClinicalAttachmentRepository;
import com.medical.agent.model.repository.ClinicalEncounterRepository;
import com.medical.agent.model.repository.EncounterDiagnosisRepository;
import com.medical.agent.model.repository.HealthRecordRepository;
import com.medical.agent.model.repository.PaperCaseRecordRepository;
import com.medical.agent.model.repository.PatientMedicalHistoryRepository;
import com.medical.agent.model.repository.PatientRepository;
import com.medical.agent.service.cache.AiChatCacheInvalidationService;
import com.medical.agent.service.clinicaljob.ClinicalDataJobOrchestrator;
import com.medical.agent.service.clinicaljob.ClinicalDataJobService;
import com.medical.agent.service.device.DeviceImportAdapterRegistry;
import com.medical.agent.service.device.DeviceImportNormalizer;
import com.medical.agent.service.device.DeviceJsonImportService;
import com.medical.agent.service.device.DeviceMeasurementDedupStrategy;
import com.medical.agent.service.device.DeviceMeasurementMapperRegistry;
import com.medical.agent.service.device.HuaweiDeviceImportAdapter;
import com.medical.agent.service.device.HuaweiDeviceJsonImportService;
import com.medical.agent.service.device.HuaweiMeasurementMapper;
import com.medical.agent.service.fhir.FhirCodingRegistry;
import com.medical.agent.service.papercase.PaperCaseExtractionResult;
import com.medical.agent.service.papercase.PaperCaseIcd11Service;
import com.medical.agent.service.papercase.PaperCaseIcdMatchResult;
import com.medical.agent.service.papercase.PaperCaseVisionOcrService;
import com.medical.agent.service.security.PaperCaseUploadSecurityService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.cache.support.NoOpCacheManager;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ClinicalRegressionSampleSetTest {

    private final ObjectMapper objectMapper = JsonMapper.builder().findAndAddModules().build();

    @TempDir
    Path tempDir;

    @Test
    void shouldKeepPaperCaseRegressionFixtureStable() throws Exception {
        String previousUserDir = System.getProperty("user.dir");
        System.setProperty("user.dir", tempDir.toString());
        try {
            PaperCaseExtractionResult extraction = readPaperCaseSample();
            AtomicReference<PaperCaseRecord> savedRecord = new AtomicReference<>();
            AtomicReference<PatientMedicalHistory> savedHistory = new AtomicReference<>();
            AtomicReference<HealthRecordDTO> createdMetrics = new AtomicReference<>();

            PatientRepository patientRepository = mock(PatientRepository.class);
            when(patientRepository.findById(31L)).thenReturn(Optional.of(Patient.builder()
                    .id(31L)
                    .name("张三")
                    .gender("男")
                    .assignedDoctorId(501L)
                    .build()));

            PaperCaseRecordRepository recordRepository = mock(PaperCaseRecordRepository.class);
            when(recordRepository.save(any(PaperCaseRecord.class))).thenAnswer(invocation -> {
                PaperCaseRecord record = invocation.getArgument(0);
                if (record.getId() == null) {
                    record.setId(901L);
                }
                savedRecord.set(record);
                return record;
            });
            when(recordRepository.findById(anyLong())).thenAnswer(invocation -> Optional.ofNullable(savedRecord.get()));
            when(recordRepository.findByPatientIdOrderByCreatedAtDesc(31L)).thenReturn(List.of());

            PatientMedicalHistoryRepository historyRepository = mock(PatientMedicalHistoryRepository.class);
            when(historyRepository.findByPatientId(31L)).thenReturn(Optional.empty());
            when(historyRepository.save(any(PatientMedicalHistory.class))).thenAnswer(invocation -> {
                PatientMedicalHistory history = invocation.getArgument(0);
                savedHistory.set(history);
                return history;
            });

            PaperCaseService service = new PaperCaseService(
                    recordRepository,
                    patientRepository,
                    historyRepository,
                    new StubPaperCaseVisionOcrService(extraction),
                    new StubPaperCaseIcd11Service(PaperCaseIcdMatchResult.builder()
                            .code("DA22")
                            .title("Gastro-oesophageal reflux disease")
                            .confidence(0.95)
                            .codingStatus("auto_coded")
                            .codingSource("fixture")
                            .candidates(List.of(
                                    PaperCaseIcdCandidateDTO.builder()
                                            .code("DA22")
                                            .title("Gastro-oesophageal reflux disease")
                                            .confidence(0.95)
                                            .source("fixture")
                                            .build()
                            ))
                            .build()),
                    new StubHealthRecordService(createdMetrics),
                    new StubAiChatCacheInvalidationService(),
                    new StubPaperCaseMedplumAutoSyncService(recordRepository),
                    new StubClinicalDataJobService(),
                    new StubClinicalDataJobOrchestrator(),
                    new StubClinicalDataModelService(901L),
                    new PaperCaseUploadSecurityService()
            );

            MockMultipartFile file = new MockMultipartFile(
                    "file",
                    "fixture-case.jpg",
                    "image/jpeg",
                    new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00, 0x01}
            );

            PaperCaseRecordDTO result = service.processUpload(31L, file, LocalDate.of(2026, 5, 28));

            assertEquals("张三", result.getPatientInfo().getName());
            assertEquals("胃食管反流病", result.getPreliminaryDiagnosis());
            assertEquals("DA22", result.getIcdCode());
            assertEquals("synced", result.getJob().getJobStatus());
            assertEquals("medplum_synced", result.getJob().getLastStage());
            assertEquals(148, result.getStructuredMetrics().getSystolicBp());
            assertEquals(901L, result.getStructuredMetrics().getEncounterId());
            assertEquals("/api/paper-cases/901/file", result.getSourceFileUrl());
            assertNotNull(savedHistory.get());
            assertTrue(savedHistory.get().getPastDiseases().contains("胃食管反流病"));
            assertTrue(savedHistory.get().getOtherNotes().contains("主诉：胸闷伴反酸1周"));
            assertNotNull(createdMetrics.get());
            assertEquals(6.1, createdMetrics.get().getFastingGlucose());
        } finally {
            System.setProperty("user.dir", previousUserDir);
        }
    }

    @Test
    void shouldKeepHuaweiImportRegressionFixtureStable() throws Exception {
        AtomicInteger importedCount = new AtomicInteger();
        AtomicReference<HealthRecordDTO> lastImported = new AtomicReference<>();

        DeviceImportNormalizer normalizer = new DeviceImportNormalizer();
        DeviceJsonImportService deviceJsonImportService = new DeviceJsonImportService(
                new StubDeviceHealthRecordService(importedCount, lastImported),
                new StubDeviceMedplumAutoSyncService(),
                new StubClinicalDataJobService(),
                new StubClinicalDataJobOrchestrator(),
                new StubClinicalDataModelService(901L),
                new DeviceImportAdapterRegistry(List.of(new HuaweiDeviceImportAdapter(normalizer))),
                new DeviceMeasurementMapperRegistry(List.of(new HuaweiMeasurementMapper(normalizer))),
                new DeviceMeasurementDedupStrategy()
        );
        HuaweiDeviceJsonImportService service = new HuaweiDeviceJsonImportService(deviceJsonImportService);

        byte[] payload = readResourceBytes("clinical-regression/huawei-device-sample.json");
        DeviceImportSummaryDTO result = service.importJson(31L, payload);

        assertEquals(31L, result.getPatientId());
        assertEquals("huawei", result.getVendor());
        assertEquals("blood_pressure_monitor", result.getDeviceType());
        assertEquals("HW-BP-REG-0001", result.getSourceDeviceId());
        assertEquals(2, result.getTotalRecords());
        assertEquals(2, result.getImportedRecords());
        assertEquals("synced", result.getMedplumSyncStatus());
        assertEquals("synced", result.getJob().getJobStatus());
        assertEquals("medplum_synced", result.getJob().getLastStage());
        assertEquals(2, importedCount.get());
        assertNotNull(lastImported.get());
        assertEquals(134, lastImported.get().getSystolicBp());
        assertEquals(86, lastImported.get().getDiastolicBp());
        assertEquals("huawei", lastImported.get().getSourceVendor());
    }

    @Test
    void shouldExportRegressionFixtureToFhirBundles() throws Exception {
        PaperCaseExtractionResult extraction = readPaperCaseSample();

        PatientRepository patientRepository = mock(PatientRepository.class);
        when(patientRepository.findById(31L)).thenReturn(Optional.of(Patient.builder()
                .id(31L)
                .name("张三")
                .gender("男")
                .build()));

        HealthRecordRepository healthRecordRepository = mock(HealthRecordRepository.class);
        when(healthRecordRepository.findByPatientIdOrderByRecordDateDesc(31L)).thenReturn(List.of(
                HealthRecord.builder()
                        .id(7001L)
                        .patientId(31L)
                        .encounterId(901L)
                        .recordDate(LocalDate.of(2026, 5, 28))
                        .recordedAt(LocalDateTime.of(2026, 5, 28, 9, 30))
                        .height(extraction.getStructuredMetrics().getHeight())
                        .weight(extraction.getStructuredMetrics().getWeight())
                        .systolicBp(extraction.getStructuredMetrics().getSystolicBp())
                        .diastolicBp(extraction.getStructuredMetrics().getDiastolicBp())
                        .restingHeartRate(extraction.getStructuredMetrics().getRestingHeartRate())
                        .fastingGlucose(extraction.getStructuredMetrics().getFastingGlucose())
                        .build()
        ));

        PatientMedicalHistoryRepository historyRepository = mock(PatientMedicalHistoryRepository.class);
        when(historyRepository.findByPatientId(31L)).thenReturn(Optional.empty());

        PaperCaseRecordRepository paperCaseRecordRepository = mock(PaperCaseRecordRepository.class);
        ClinicalEncounterRepository encounterRepository = mock(ClinicalEncounterRepository.class);
        when(encounterRepository.findByPatientIdOrderByEncounterDateDescCreatedAtDesc(31L)).thenReturn(List.of(
                ClinicalEncounter.builder()
                        .id(901L)
                        .patientId(31L)
                        .encounterType("paper_case")
                        .encounterDate(LocalDate.of(2026, 5, 28))
                        .chiefComplaint(extraction.getChiefComplaint())
                        .presentIllness(extraction.getPresentIllness())
                        .physicalExam(extraction.getPhysicalExam())
                        .sourceKind("paper_case_record")
                        .sourceRefId(901L)
                        .sourceFileUrl("/api/paper-cases/901/file")
                        .build()
        ));

        ClinicalAttachmentRepository attachmentRepository = mock(ClinicalAttachmentRepository.class);
        when(attachmentRepository.findByEncounterIdOrderByIdAsc(901L)).thenReturn(List.of(
                ClinicalAttachment.builder()
                        .id(901L)
                        .encounterId(901L)
                        .attachmentType("paper_case")
                        .contentType("image/jpeg")
                        .fileName("fixture-case.jpg")
                        .fileUrl("/api/paper-cases/901/file")
                        .documentNarrative(extraction.getRawText())
                        .sourceKind("paper_case_record")
                        .sourceRefId(901L)
                        .build()
        ));

        EncounterDiagnosisRepository diagnosisRepository = mock(EncounterDiagnosisRepository.class);
        when(diagnosisRepository.findByEncounterIdOrderByIdAsc(901L)).thenReturn(List.of(
                EncounterDiagnosis.builder()
                        .id(901L)
                        .encounterId(901L)
                        .diagnosisText(extraction.getPreliminaryDiagnosis())
                        .primaryDiagnosis(Boolean.TRUE)
                        .icd11Code("DA22")
                        .icd11Title("Gastro-oesophageal reflux disease")
                        .codingStatus("auto_coded")
                        .codingSource("fixture")
                        .codingConfidence(0.95)
                        .build()
        ));

        FhirService service = new FhirService(
                patientRepository,
                healthRecordRepository,
                historyRepository,
                paperCaseRecordRepository,
                encounterRepository,
                diagnosisRepository,
                attachmentRepository,
                new FhirCodingRegistry()
        );

        JsonNode observationBundle = objectMapper.readTree(service.toFhirObservationsJson(31L));
        JsonNode paperCaseBundle = objectMapper.readTree(service.toFhirPaperCasesJson(31L));

        assertEquals("Encounter/paper-case-encounter-901",
                observationBundle.path("entry").get(0).path("resource").path("encounter").path("reference").asText());
        assertEquals("Patient/patient-31",
                paperCaseBundle.path("entry").get(0).path("resource").path("subject").path("reference").asText());
        assertEquals("DA22",
                paperCaseBundle.path("entry").get(2).path("resource").path("code").path("coding").get(0).path("code").asText());
        assertEquals("/api/paper-cases/901/file",
                paperCaseBundle.path("entry").get(1).path("resource").path("content").get(0).path("attachment").path("url").asText());
    }

    private PaperCaseExtractionResult readPaperCaseSample() throws IOException {
        try (InputStream inputStream = new ClassPathResource("clinical-regression/paper-case-ocr-sample.json").getInputStream()) {
            return objectMapper.readValue(inputStream, PaperCaseExtractionResult.class);
        }
    }

    private byte[] readResourceBytes(String path) throws IOException {
        try (InputStream inputStream = new ClassPathResource(path).getInputStream()) {
            return inputStream.readAllBytes();
        }
    }

    private static final class StubPaperCaseVisionOcrService extends PaperCaseVisionOcrService {
        private final PaperCaseExtractionResult result;

        private StubPaperCaseVisionOcrService(PaperCaseExtractionResult result) {
            this.result = result;
        }

        @Override
        public PaperCaseExtractionResult extract(Path filePath, String fileType) {
            return result;
        }
    }

    private static final class StubPaperCaseIcd11Service extends PaperCaseIcd11Service {
        private final PaperCaseIcdMatchResult result;

        private StubPaperCaseIcd11Service(PaperCaseIcdMatchResult result) {
            this.result = result;
        }

        @Override
        public PaperCaseIcdMatchResult matchDiagnosis(String diagnosis) {
            return result;
        }
    }

    private static final class StubHealthRecordService extends HealthRecordService {
        private final AtomicReference<HealthRecordDTO> captured;

        private StubHealthRecordService(AtomicReference<HealthRecordDTO> captured) {
            super(null, null, null);
            this.captured = captured;
        }

        @Override
        public HealthRecordDTO create(HealthRecordDTO dto,
                                      com.medical.agent.common.enums.DiseaseType diseaseType,
                                      boolean triggerAssessment,
                                      Map<String, Object> assessmentOptions) {
            dto.setId(7001L);
            captured.set(dto);
            return dto;
        }
    }

    private static final class StubDeviceHealthRecordService extends HealthRecordService {
        private final AtomicInteger importedCount;
        private final AtomicReference<HealthRecordDTO> lastImported;

        private StubDeviceHealthRecordService(AtomicInteger importedCount,
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
            dto.setId((long) importedCount.get());
            lastImported.set(dto);
            return dto;
        }
    }

    private static final class StubAiChatCacheInvalidationService extends AiChatCacheInvalidationService {
        private StubAiChatCacheInvalidationService() {
            super(new NoOpCacheManager(), new com.medical.agent.service.cache.AiChatCacheVersionService());
        }

        @Override
        public void invalidatePatient(Long patientId) {
        }
    }

    private static final class StubPaperCaseMedplumAutoSyncService extends MedplumAutoSyncService {
        private final PaperCaseRecordRepository recordRepository;

        private StubPaperCaseMedplumAutoSyncService(PaperCaseRecordRepository recordRepository) {
            super(null, null);
            this.recordRepository = recordRepository;
        }

        @Override
        public PaperCaseRecord syncPaperCaseRecord(PaperCaseRecord record) {
            record.setMedplumSyncStatus("synced");
            record.setMedplumSyncMessage("fixture synced");
            return recordRepository.save(record);
        }
    }

    private static final class StubDeviceMedplumAutoSyncService extends MedplumAutoSyncService {
        private StubDeviceMedplumAutoSyncService() {
            super(null, null);
        }

        @Override
        public DeviceImportSummaryDTO syncImportedHealthData(DeviceImportSummaryDTO summary, Long patientId) {
            summary.setMedplumSyncStatus("synced");
            summary.setMedplumSyncMessage("fixture synced");
            return summary;
        }
    }

    private static final class StubClinicalDataJobService extends ClinicalDataJobService {
        private ClinicalDataJob job;

        private StubClinicalDataJobService() {
            super(null);
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
                    .runAttempt(0L)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();
            return new JobCreationResult(job, true);
        }

        @Override
        public ClinicalDataJob findLatestBySource(String sourceKind, Long sourceRefId) {
            return job;
        }

        @Override
        public com.medical.agent.common.dto.ClinicalDataJobDTO findLatestByIdempotencyKey(String idempotencyKey) {
            return null;
        }

        @Override
        public ClinicalDataJob markStarted(Long jobId, Long runAttempt, String stage) {
            job.setRunAttempt(runAttempt);
            job.setLastStage(stage);
            return job;
        }

        @Override
        public ClinicalDataJob markStage(Long jobId, Long runAttempt, String status, String stage, Map<String, Object> result) {
            job.setRunAttempt(runAttempt);
            job.setJobStatus(status);
            job.setLastStage(stage);
            return job;
        }

        @Override
        public ClinicalDataJob markFailure(Long jobId, Long runAttempt, String stage, String message) {
            job.setRunAttempt(runAttempt);
            job.setJobStatus(STATUS_FAILED);
            job.setLastStage(stage);
            job.setLastError(message);
            return job;
        }

        @Override
        public ClinicalDataJob claimForExecution(Long jobId, String stage) {
            job.setJobStatus(STATUS_RUNNING);
            job.setLastStage(stage);
            job.setRunAttempt((job.getRunAttempt() == null ? 0L : job.getRunAttempt()) + 1L);
            return job;
        }

        @Override
        public ClinicalDataJob assertExecutable(Long jobId, Long runAttempt, String stage) {
            return job;
        }

        @Override
        public ClinicalDataJob getRequired(Long jobId) {
            return job;
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
                    .runAttempt(job.getRunAttempt())
                    .build();
        }
    }

    private static final class StubClinicalDataJobOrchestrator extends ClinicalDataJobOrchestrator {
        private StubClinicalDataJobOrchestrator() {
            super(Runnable::run);
        }
    }

    private static final class StubClinicalDataModelService extends ClinicalDataModelService {
        private final Long encounterId;

        private StubClinicalDataModelService(Long encounterId) {
            super(null, null, null, null, null);
            this.encounterId = encounterId;
        }

        @Override
        public ClinicalEncounter upsertPaperCaseEncounter(PaperCaseRecord record,
                                                          PaperCaseExtractionResult extraction,
                                                          PaperCaseIcdMatchResult icdMatch) {
            return ClinicalEncounter.builder()
                    .id(encounterId)
                    .patientId(record.getPatientId())
                    .encounterType("paper_case")
                    .encounterDate(record.getRecordDate())
                    .chiefComplaint(extraction.getChiefComplaint())
                    .presentIllness(extraction.getPresentIllness())
                    .physicalExam(extraction.getPhysicalExam())
                    .sourceKind("paper_case_record")
                    .sourceRefId(record.getId())
                    .build();
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
