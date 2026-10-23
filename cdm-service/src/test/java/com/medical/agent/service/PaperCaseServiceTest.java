package com.medical.agent.service;

import com.medical.agent.common.dto.HealthRecordDTO;
import com.medical.agent.common.dto.PaperCaseIcdConfirmDTO;
import com.medical.agent.common.dto.PaperCaseIcdCandidateDTO;
import com.medical.agent.common.dto.PaperCasePatientSummaryDTO;
import com.medical.agent.common.dto.PaperCaseRecordDTO;
import com.medical.agent.common.exception.BusinessException;
import com.medical.agent.model.entity.ClinicalDataJob;
import com.medical.agent.model.entity.PaperCaseRecord;
import com.medical.agent.model.entity.Patient;
import com.medical.agent.model.entity.PatientMedicalHistory;
import com.medical.agent.model.repository.PaperCaseRecordRepository;
import com.medical.agent.model.repository.PatientMedicalHistoryRepository;
import com.medical.agent.model.repository.PatientRepository;
import com.medical.agent.service.cache.AiChatCacheInvalidationService;
import com.medical.agent.service.clinicaljob.ClinicalDataJobOrchestrator;
import com.medical.agent.service.clinicaljob.ClinicalDataJobService;
import com.medical.agent.service.papercase.PaperCaseExtractionResult;
import com.medical.agent.service.papercase.PaperCaseIcd11Service;
import com.medical.agent.service.papercase.PaperCaseIcdMatchResult;
import com.medical.agent.service.papercase.PaperCaseVisionOcrService;
import com.medical.agent.service.security.PaperCaseUploadSecurityService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.cache.support.NoOpCacheManager;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PaperCaseServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldPersistPaperCaseAndSyncHistoryAndMetrics() throws Exception {
        String previousUserDir = System.getProperty("user.dir");
        System.setProperty("user.dir", tempDir.toString());
        try {
            AtomicReference<PaperCaseRecord> savedRecord = new AtomicReference<>();
            AtomicReference<PatientMedicalHistory> savedHistory = new AtomicReference<>();
            AtomicReference<HealthRecordDTO> syncedMetrics = new AtomicReference<>();
            AtomicReference<Long> invalidatedPatientId = new AtomicReference<>();

            PatientRepository patientRepository = createProxy(PatientRepository.class, Map.of(
                    "findById", args -> Optional.of(Patient.builder()
                            .id(12L)
                            .name("李某")
                            .gender("男")
                            .assignedDoctorId(88L)
                            .build())
            ));
            PaperCaseRecordRepository recordRepository = createProxy(PaperCaseRecordRepository.class, Map.of(
                    "save", args -> {
                        PaperCaseRecord record = (PaperCaseRecord) args[0];
                        record.setId(300L);
                        savedRecord.set(record);
                        return record;
                    },
                    "findById", args -> Optional.ofNullable(savedRecord.get()),
                    "findByPatientIdOrderByCreatedAtDesc", args -> List.of()
            ));
            PatientMedicalHistoryRepository historyRepository = createProxy(PatientMedicalHistoryRepository.class, Map.of(
                    "findByPatientId", args -> Optional.of(PatientMedicalHistory.builder()
                            .patientId(12L)
                            .pastDiseases("高血压")
                            .otherNotes("既往记录")
                            .build()),
                    "save", args -> {
                        PatientMedicalHistory history = (PatientMedicalHistory) args[0];
                        savedHistory.set(history);
                        return history;
                    }
            ));

            StubPaperCaseVisionOcrService ocrService = new StubPaperCaseVisionOcrService(PaperCaseExtractionResult.builder()
                    .rawText("患者李某，男，45岁。主诉：上腹痛伴反酸2周。诊断：胃食管反流病。")
                    .patientName("李某")
                    .gender("男")
                    .age(45)
                    .chiefComplaint("上腹痛伴反酸2周")
                    .presentIllness("2周前起反复上腹痛伴反酸")
                    .physicalExam("上腹部轻压痛")
                    .preliminaryDiagnosis("胃食管反流病")
                    .structuredMetrics(HealthRecordDTO.builder()
                            .height(170.0)
                            .weight(68.0)
                            .systolicBp(132)
                            .diastolicBp(86)
                            .fastingGlucose(5.4)
                            .build())
                    .rawJson("{\"ok\":true}")
                    .build());
            StubPaperCaseIcd11Service icd11Service = new StubPaperCaseIcd11Service(PaperCaseIcdMatchResult.builder()
                    .code("DA22")
                    .title("Gastro-oesophageal reflux disease")
                    .confidence(0.93)
                    .candidates(List.of(
                            PaperCaseIcdCandidateDTO.builder().code("DA22").title("Gastro-oesophageal reflux disease").confidence(0.93).source("autocode").build(),
                            PaperCaseIcdCandidateDTO.builder().code("DD90").title("Gastritis").confidence(0.42).source("search").build()
                    ))
                    .build());
            StubHealthRecordService healthRecordService = new StubHealthRecordService(syncedMetrics);
            StubAiChatCacheInvalidationService cacheInvalidationService = new StubAiChatCacheInvalidationService(invalidatedPatientId);

            PaperCaseService service = new PaperCaseService(
                    recordRepository,
                    patientRepository,
                    historyRepository,
                    ocrService,
                    icd11Service,
                    healthRecordService,
                    cacheInvalidationService,
                    new StubMedplumAutoSyncService(recordRepository),
                    new StubClinicalDataJobService(),
                    new StubClinicalDataJobOrchestrator(),
                    new StubClinicalDataModelService(),
                    new PaperCaseUploadSecurityService()
            );

            MockMultipartFile file = new MockMultipartFile(
                    "file",
                    "case.jpg",
                    "image/jpeg",
                    new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00, 0x01}
            );

            var result = service.processUpload(12L, file, LocalDate.of(2026, 5, 28));

            assertNotNull(result);
            assertEquals(300L, result.getId());
            assertEquals("李某", result.getPatientInfo().getName());
            assertEquals("胃食管反流病", result.getPreliminaryDiagnosis());
            assertEquals("DA22", result.getIcdCode());
            assertEquals(2, result.getIcdCandidates().size());
            assertEquals(12L, invalidatedPatientId.get());
            assertEquals("review_pending", result.getMedplumSyncStatus());

            assertNotNull(savedRecord.get());
            assertEquals("/api/paper-cases/300/file", result.getSourceFileUrl());
            assertEquals("paper-cases/12/", savedRecord.get().getSourceObjectKey().substring(0, "paper-cases/12/".length()));
            assertTrue(savedRecord.get().getStructuredMetricsJson().contains("\"systolicBp\":132"));
            assertTrue(savedRecord.get().getIcdCandidatesJson().contains("\"code\":\"DA22\""));
            assertEquals("胃食管反流病", savedRecord.get().getPreliminaryDiagnosis());

            assertNotNull(savedHistory.get());
            assertTrue(savedHistory.get().getPastDiseases().contains("高血压"));
            assertTrue(savedHistory.get().getPastDiseases().contains("胃食管反流病"));
            assertTrue(savedHistory.get().getOtherNotes().contains("主诉：上腹痛伴反酸2周"));
            assertTrue(savedHistory.get().getOtherNotes().contains("ICD-11"));
            assertTrue(savedHistory.get().getOtherNotes().contains("DA22"));

            assertNotNull(syncedMetrics.get());
            assertEquals(12L, syncedMetrics.get().getPatientId());
            assertEquals(300L, syncedMetrics.get().getEncounterId());
            assertEquals(132, syncedMetrics.get().getSystolicBp());
            assertEquals(86, syncedMetrics.get().getDiastolicBp());
            assertEquals(5.4, syncedMetrics.get().getFastingGlucose());

            Path uploadDir = tempDir.resolve("uploads").resolve("paper-cases").resolve("12");
            assertTrue(Files.exists(uploadDir));
            assertEquals(1L, Files.list(uploadDir).count());
        } finally {
            System.setProperty("user.dir", previousUserDir);
        }
    }

    @Test
    void shouldNotSyncHealthRecordWhenNoStructuredMetrics() throws Exception {
        String previousUserDir = System.getProperty("user.dir");
        System.setProperty("user.dir", tempDir.toString());
        try {
            AtomicBoolean createCalled = new AtomicBoolean(false);

            PatientRepository patientRepository = createProxy(PatientRepository.class, Map.of(
                    "findById", args -> Optional.of(Patient.builder().id(9L).name("王某").build())
            ));
            PaperCaseRecordRepository recordRepository = createProxy(PaperCaseRecordRepository.class, Map.of(
                    "save", args -> {
                        PaperCaseRecord record = (PaperCaseRecord) args[0];
                        record.setId(9L);
                        return record;
                    },
                    "findById", args -> Optional.of(PaperCaseRecord.builder().id(9L).patientId(9L).build()),
                    "findByPatientIdOrderByCreatedAtDesc", args -> List.of()
            ));
            PatientMedicalHistoryRepository historyRepository = createProxy(PatientMedicalHistoryRepository.class, Map.of(
                    "findByPatientId", args -> Optional.empty(),
                    "save", args -> args[0]
            ));

            PaperCaseService service = new PaperCaseService(
                    recordRepository,
                    patientRepository,
                    historyRepository,
                    new StubPaperCaseVisionOcrService(PaperCaseExtractionResult.builder()
                            .rawText("患者王某，诊断：慢性胃炎")
                            .preliminaryDiagnosis("慢性胃炎")
                            .build()),
                    new StubPaperCaseIcd11Service(PaperCaseIcdMatchResult.builder().candidates(List.of()).build()),
                    new StubHealthRecordService(createCalled),
                    new StubAiChatCacheInvalidationService(new AtomicReference<>()),
                    new StubMedplumAutoSyncService(recordRepository),
                    new StubClinicalDataJobService(),
                    new StubClinicalDataJobOrchestrator(),
                    new StubClinicalDataModelService(),
                    new PaperCaseUploadSecurityService()
            );

            MockMultipartFile file = new MockMultipartFile("file", "case.png", "image/png", new byte[]{
                    (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
            });
            var result = service.processUpload(9L, file, LocalDate.of(2026, 5, 28));

            assertEquals(9L, result.getId());
            assertFalse(createCalled.get());
            assertNull(result.getStructuredMetrics());
        } finally {
            System.setProperty("user.dir", previousUserDir);
        }
    }

    @Test
    void shouldPersistSecureSourceFileUrlBeforeAsyncProcessing() throws Exception {
        String previousUserDir = System.getProperty("user.dir");
        System.setProperty("user.dir", tempDir.toString());
        try {
            AtomicReference<PaperCaseRecord> savedRecord = new AtomicReference<>();
            List<String> savedSourceFileUrls = new java.util.ArrayList<>();

            PatientRepository patientRepository = createProxy(PatientRepository.class, Map.of(
                    "findById", args -> Optional.of(Patient.builder().id(15L).name("周某").build())
            ));
            PaperCaseRecordRepository recordRepository = createProxy(PaperCaseRecordRepository.class, Map.of(
                    "save", args -> {
                        PaperCaseRecord record = (PaperCaseRecord) args[0];
                        if (record.getSourceFileUrl() == null) {
                            throw new IllegalStateException("sourceFileUrl must not be null");
                        }
                        if (record.getId() == null) {
                            record.setId(15L);
                        }
                        savedSourceFileUrls.add(record.getSourceFileUrl());
                        savedRecord.set(record);
                        return record;
                    },
                    "findById", args -> Optional.ofNullable(savedRecord.get()),
                    "findByPatientIdOrderByCreatedAtDesc", args -> savedRecord.get() == null ? List.of() : List.of(savedRecord.get())
            ));
            PatientMedicalHistoryRepository historyRepository = createProxy(PatientMedicalHistoryRepository.class, Map.of(
                    "findByPatientId", args -> Optional.empty(),
                    "save", args -> args[0]
            ));

            PaperCaseService service = new PaperCaseService(
                    recordRepository,
                    patientRepository,
                    historyRepository,
                    new StubPaperCaseVisionOcrService(PaperCaseExtractionResult.builder()
                            .rawText("患者周某，诊断：胃炎")
                            .preliminaryDiagnosis("胃炎")
                            .build()),
                    new StubPaperCaseIcd11Service(PaperCaseIcdMatchResult.builder().candidates(List.of()).build()),
                    new StubHealthRecordService(new AtomicBoolean(false)),
                    new StubAiChatCacheInvalidationService(new AtomicReference<>()),
                    new StubMedplumAutoSyncService(recordRepository),
                    new StubClinicalDataJobService(),
                    new StubClinicalDataJobOrchestrator(),
                    new StubClinicalDataModelService(),
                    new PaperCaseUploadSecurityService()
            );

            MockMultipartFile file = new MockMultipartFile("file", "case.jpg", "image/jpeg", new byte[]{
                    (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00, 0x01
            });

            PaperCaseRecordDTO result = service.processUpload(15L, file, LocalDate.of(2026, 5, 28));

            assertTrue(savedSourceFileUrls.size() >= 2);
            assertTrue(savedSourceFileUrls.get(0).startsWith("paper-cases/15/"));
            assertEquals("/api/paper-cases/15/file", savedSourceFileUrls.get(1));
            assertEquals("/api/paper-cases/15/file", result.getSourceFileUrl());
            assertEquals("/api/paper-cases/15/file", savedRecord.get().getSourceFileUrl());
        } finally {
            System.setProperty("user.dir", previousUserDir);
        }
    }

    @Test
    void shouldDeleteUploadedFileWhenOcrFails() throws Exception {
        String previousUserDir = System.getProperty("user.dir");
        System.setProperty("user.dir", tempDir.toString());
        try {
            PatientRepository patientRepository = createProxy(PatientRepository.class, Map.of(
                    "findById", args -> Optional.of(Patient.builder().id(20L).name("赵某").build())
            ));
            PaperCaseRecordRepository recordRepository = createProxy(PaperCaseRecordRepository.class, Map.of(
                    "save", args -> args[0],
                    "findById", args -> Optional.of(PaperCaseRecord.builder().id(20L).patientId(20L).build()),
                    "findByPatientIdOrderByCreatedAtDesc", args -> List.of()
            ));
            PatientMedicalHistoryRepository historyRepository = createProxy(PatientMedicalHistoryRepository.class, Map.of(
                    "findByPatientId", args -> Optional.empty(),
                    "save", args -> args[0]
            ));

            PaperCaseService service = new PaperCaseService(
                    recordRepository,
                    patientRepository,
                    historyRepository,
                    new StubPaperCaseVisionOcrService(new BusinessException(422, "OCR 未识别出有效文本")),
                    new StubPaperCaseIcd11Service(PaperCaseIcdMatchResult.builder().candidates(List.of()).build()),
                    new StubHealthRecordService(new AtomicBoolean(false)),
                    new StubAiChatCacheInvalidationService(new AtomicReference<>()),
                    new StubMedplumAutoSyncService(recordRepository),
                    new StubClinicalDataJobService(),
                    new StubClinicalDataJobOrchestrator(),
                    new StubClinicalDataModelService(),
                    new PaperCaseUploadSecurityService()
            );

            MockMultipartFile file = new MockMultipartFile("file", "case.jpg", "image/jpeg", new byte[]{
                    (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00
            });

            BusinessException ex = null;
            try {
                service.processUpload(20L, file, LocalDate.of(2026, 5, 28));
            } catch (BusinessException error) {
                ex = error;
            }

            assertNotNull(ex);
            assertEquals(422, ex.getCode());
            Path uploadDir = tempDir.resolve("uploads").resolve("paper-cases").resolve("20");
            if (Files.exists(uploadDir)) {
                assertEquals(0L, Files.list(uploadDir).count());
            }
        } finally {
            System.setProperty("user.dir", previousUserDir);
        }
    }

    @Test
    void shouldConfirmIcdAndAdvanceReviewPendingJob() {
        AtomicReference<PaperCaseRecord> savedRecord = new AtomicReference<>();

        PatientRepository patientRepository = createProxy(PatientRepository.class, Map.of(
                "findById", args -> Optional.empty()
        ));
        PaperCaseRecordRepository recordRepository = createProxy(PaperCaseRecordRepository.class, Map.of(
                "findById", args -> Optional.of(PaperCaseRecord.builder()
                        .id(55L)
                        .patientId(12L)
                        .preliminaryDiagnosis("胃食管反流病")
                        .icdCode("DA22")
                        .icdTitle("胃食管反流病")
                        .icdCodingStatus("pending_review")
                        .icdCodingSource("local-mapping")
                        .build()),
                "save", args -> {
                    PaperCaseRecord record = (PaperCaseRecord) args[0];
                    savedRecord.set(record);
                    return record;
                },
                "findByPatientIdOrderByCreatedAtDesc", args -> List.of()
        ));
        PatientMedicalHistoryRepository historyRepository = createProxy(PatientMedicalHistoryRepository.class, Map.of(
                "findByPatientId", args -> Optional.empty(),
                "save", args -> args[0]
        ));

        AtomicReference<Long> invalidatedPatientId = new AtomicReference<>();
        StubClinicalDataJobService jobService = new StubClinicalDataJobService();
        jobService.seedJob(ClinicalDataJob.builder()
                .id(91L)
                .jobType(ClinicalDataJobService.JOB_TYPE_PAPER_CASE)
                .jobStatus(ClinicalDataJobService.STATUS_REVIEW_PENDING)
                .patientId(12L)
                .sourceKind(ClinicalDataJobService.SOURCE_KIND_PAPER_CASE_RECORD)
                .sourceRefId(55L)
                .lastStage("awaiting_manual_icd_review")
                .build());
        PaperCaseService service = new PaperCaseService(
                recordRepository,
                patientRepository,
                historyRepository,
                new StubPaperCaseVisionOcrService(PaperCaseExtractionResult.builder().build()),
                new StubPaperCaseIcd11Service(PaperCaseIcdMatchResult.builder().candidates(List.of()).build()),
                new StubHealthRecordService(new AtomicBoolean(false)),
                new StubAiChatCacheInvalidationService(invalidatedPatientId),
                new StubMedplumAutoSyncService(recordRepository),
                jobService,
                new StubClinicalDataJobOrchestrator(),
                new StubClinicalDataModelService(),
                new PaperCaseUploadSecurityService()
        );

        PaperCaseRecordDTO result = service.confirmIcd(55L, PaperCaseIcdConfirmDTO.builder()
                .code("DA22")
                .title("胃食管反流病")
                .confidence(0.94)
                .build(), 88L);

        assertNotNull(result);
        assertEquals("DA22", result.getIcdCode());
        assertEquals("manually_confirmed", result.getIcdCodingStatus());
        assertEquals("manual", result.getIcdCodingSource());
        assertEquals(88L, result.getIcdReviewedBy());
        assertEquals(12L, invalidatedPatientId.get());
        assertEquals("synced", result.getMedplumSyncStatus());
        assertNotNull(result.getJob());
        assertEquals(ClinicalDataJobService.STATUS_SYNCED, result.getJob().getJobStatus());
        assertEquals("manual_icd_confirmed_medplum_synced", result.getJob().getLastStage());
        assertNotNull(savedRecord.get());
        assertEquals("manual", savedRecord.get().getIcdCodingSource());
        assertNotNull(savedRecord.get().getIcdReviewedAt());
    }

    @Test
    void shouldMarkReviewPendingJobFailedWhenManualIcdSyncFails() {
        AtomicReference<PaperCaseRecord> savedRecord = new AtomicReference<>();

        PatientRepository patientRepository = createProxy(PatientRepository.class, Map.of(
                "findById", args -> Optional.empty()
        ));
        PaperCaseRecordRepository recordRepository = createProxy(PaperCaseRecordRepository.class, Map.of(
                "findById", args -> Optional.of(PaperCaseRecord.builder()
                        .id(56L)
                        .patientId(13L)
                        .preliminaryDiagnosis("胃炎")
                        .icdCode("DD90")
                        .icdTitle("胃炎")
                        .icdCodingStatus("pending_review")
                        .icdCodingSource("local-mapping")
                        .build()),
                "save", args -> {
                    PaperCaseRecord record = (PaperCaseRecord) args[0];
                    savedRecord.set(record);
                    return record;
                },
                "findByPatientIdOrderByCreatedAtDesc", args -> List.of()
        ));
        PatientMedicalHistoryRepository historyRepository = createProxy(PatientMedicalHistoryRepository.class, Map.of(
                "findByPatientId", args -> Optional.empty(),
                "save", args -> args[0]
        ));

        StubClinicalDataJobService jobService = new StubClinicalDataJobService();
        jobService.seedJob(ClinicalDataJob.builder()
                .id(92L)
                .jobType(ClinicalDataJobService.JOB_TYPE_PAPER_CASE)
                .jobStatus(ClinicalDataJobService.STATUS_REVIEW_PENDING)
                .patientId(13L)
                .sourceKind(ClinicalDataJobService.SOURCE_KIND_PAPER_CASE_RECORD)
                .sourceRefId(56L)
                .lastStage("awaiting_manual_icd_review")
                .build());

        PaperCaseService service = new PaperCaseService(
                recordRepository,
                patientRepository,
                historyRepository,
                new StubPaperCaseVisionOcrService(PaperCaseExtractionResult.builder().build()),
                new StubPaperCaseIcd11Service(PaperCaseIcdMatchResult.builder().candidates(List.of()).build()),
                new StubHealthRecordService(new AtomicBoolean(false)),
                new StubAiChatCacheInvalidationService(new AtomicReference<>()),
                new FailingStubMedplumAutoSyncService(recordRepository),
                jobService,
                new StubClinicalDataJobOrchestrator(),
                new StubClinicalDataModelService(),
                new PaperCaseUploadSecurityService()
        );

        PaperCaseRecordDTO result = service.confirmIcd(56L, PaperCaseIcdConfirmDTO.builder()
                .code("DD90")
                .title("胃炎")
                .confidence(0.86)
                .build(), 89L);

        assertEquals("failed", result.getMedplumSyncStatus());
        assertNotNull(result.getJob());
        assertEquals(ClinicalDataJobService.STATUS_FAILED, result.getJob().getJobStatus());
        assertEquals("manual_icd_confirmed_medplum_sync_failed", result.getJob().getLastStage());
        assertEquals("stub sync failed", result.getJob().getLastError());
    }

    @Test
    void shouldAdvanceReviewPendingJobWhenManualIcdSyncIsSkipped() {
        AtomicReference<PaperCaseRecord> savedRecord = new AtomicReference<>();

        PatientRepository patientRepository = createProxy(PatientRepository.class, Map.of(
                "findById", args -> Optional.empty()
        ));
        PaperCaseRecordRepository recordRepository = createProxy(PaperCaseRecordRepository.class, Map.of(
                "findById", args -> Optional.of(PaperCaseRecord.builder()
                        .id(57L)
                        .patientId(14L)
                        .preliminaryDiagnosis("胃炎")
                        .icdCode("DD90")
                        .icdTitle("胃炎")
                        .icdCodingStatus("pending_review")
                        .icdCodingSource("local-mapping")
                        .build()),
                "save", args -> {
                    PaperCaseRecord record = (PaperCaseRecord) args[0];
                    savedRecord.set(record);
                    return record;
                },
                "findByPatientIdOrderByCreatedAtDesc", args -> List.of()
        ));
        PatientMedicalHistoryRepository historyRepository = createProxy(PatientMedicalHistoryRepository.class, Map.of(
                "findByPatientId", args -> Optional.empty(),
                "save", args -> args[0]
        ));

        AtomicReference<Long> invalidatedPatientId = new AtomicReference<>();
        StubClinicalDataJobService jobService = new StubClinicalDataJobService();
        jobService.seedJob(ClinicalDataJob.builder()
                .id(92L)
                .jobType(ClinicalDataJobService.JOB_TYPE_PAPER_CASE)
                .jobStatus(ClinicalDataJobService.STATUS_REVIEW_PENDING)
                .patientId(14L)
                .sourceKind(ClinicalDataJobService.SOURCE_KIND_PAPER_CASE_RECORD)
                .sourceRefId(57L)
                .lastStage("awaiting_manual_icd_review")
                .build());

        PaperCaseService service = new PaperCaseService(
                recordRepository,
                patientRepository,
                historyRepository,
                new StubPaperCaseVisionOcrService(PaperCaseExtractionResult.builder().build()),
                new StubPaperCaseIcd11Service(PaperCaseIcdMatchResult.builder().candidates(List.of()).build()),
                new StubHealthRecordService(new AtomicBoolean(false)),
                new StubAiChatCacheInvalidationService(invalidatedPatientId),
                new SkippingStubMedplumAutoSyncService(recordRepository),
                jobService,
                new StubClinicalDataJobOrchestrator(),
                new StubClinicalDataModelService(),
                new PaperCaseUploadSecurityService()
        );

        PaperCaseRecordDTO result = service.confirmIcd(57L, PaperCaseIcdConfirmDTO.builder()
                .code("DD90")
                .title("胃炎")
                .confidence(0.86)
                .build(), 89L);

        assertEquals("skipped", result.getMedplumSyncStatus());
        assertNotNull(result.getJob());
        assertEquals(ClinicalDataJobService.STATUS_SYNCED, result.getJob().getJobStatus());
        assertEquals("manual_icd_confirmed_medplum_sync_skipped", result.getJob().getLastStage());
        assertEquals(14L, invalidatedPatientId.get());
        assertNotNull(savedRecord.get());
    }

    @Test
    void shouldListFailedSyncPaperCaseRecords() {
        PaperCaseRecord failedRecord = PaperCaseRecord.builder()
                .id(301L)
                .patientId(44L)
                .patientName("钱某")
                .preliminaryDiagnosis("慢性胃炎")
                .medplumSyncStatus("failed")
                .medplumSyncMessage("stub sync failed")
                .build();

        PatientRepository patientRepository = createProxy(PatientRepository.class, Map.of(
                "findById", args -> Optional.empty()
        ));
        PaperCaseRecordRepository recordRepository = createProxy(PaperCaseRecordRepository.class, Map.of(
                "findByMedplumSyncStatusOrderByUpdatedAtDesc", args -> List.of(failedRecord),
                "findByPatientIdOrderByCreatedAtDesc", args -> List.of()
        ));
        PatientMedicalHistoryRepository historyRepository = createProxy(PatientMedicalHistoryRepository.class, Map.of(
                "findByPatientId", args -> Optional.empty(),
                "save", args -> args[0]
        ));

        PaperCaseService service = new PaperCaseService(
                recordRepository,
                patientRepository,
                historyRepository,
                new StubPaperCaseVisionOcrService(PaperCaseExtractionResult.builder().build()),
                new StubPaperCaseIcd11Service(PaperCaseIcdMatchResult.builder().candidates(List.of()).build()),
                new StubHealthRecordService(new AtomicBoolean(false)),
                new StubAiChatCacheInvalidationService(new AtomicReference<>()),
                new StubMedplumAutoSyncService(recordRepository),
                new StubClinicalDataJobService(),
                new StubClinicalDataJobOrchestrator(),
                new StubClinicalDataModelService(),
                new PaperCaseUploadSecurityService()
        );

        List<PaperCaseRecordDTO> records = service.getFailedSyncRecords();

        assertEquals(1, records.size());
        assertEquals(301L, records.get(0).getId());
        assertEquals("failed", records.get(0).getMedplumSyncStatus());
        assertEquals("stub sync failed", records.get(0).getMedplumSyncMessage());
    }

    @Test
    void shouldListPendingReviewPaperCaseRecordsForDoctorScope() {
        PaperCaseRecord ownedRecord = PaperCaseRecord.builder()
                .id(311L)
                .patientId(44L)
                .patientName("钱某")
                .preliminaryDiagnosis("慢性胃炎")
                .icdCodingStatus("pending_review")
                .build();
        PaperCaseRecord otherRecord = PaperCaseRecord.builder()
                .id(312L)
                .patientId(45L)
                .patientName("孙某")
                .preliminaryDiagnosis("反流性食管炎")
                .icdCodingStatus("pending_review")
                .build();

        PatientRepository patientRepository = createProxy(PatientRepository.class, Map.of(
                "findByAssignedDoctorId", args -> List.of(
                        Patient.builder().id(44L).assignedDoctorId(9L).build()
                ),
                "findById", args -> Optional.empty()
        ));
        PaperCaseRecordRepository recordRepository = createProxy(PaperCaseRecordRepository.class, Map.of(
                "findByPatientIdInAndIcdCodingStatusOrderByCreatedAtDesc", args -> {
                    List<Long> patientIds = (List<Long>) args[0];
                    String status = (String) args[1];
                    return "pending_review".equals(status) && patientIds.contains(44L) && !patientIds.contains(45L)
                            ? List.of(ownedRecord)
                            : List.of(otherRecord);
                },
                "findByPatientIdOrderByCreatedAtDesc", args -> List.of()
        ));
        PatientMedicalHistoryRepository historyRepository = createProxy(PatientMedicalHistoryRepository.class, Map.of(
                "findByPatientId", args -> Optional.empty(),
                "save", args -> args[0]
        ));

        PaperCaseService service = new PaperCaseService(
                recordRepository,
                patientRepository,
                historyRepository,
                new StubPaperCaseVisionOcrService(PaperCaseExtractionResult.builder().build()),
                new StubPaperCaseIcd11Service(PaperCaseIcdMatchResult.builder().candidates(List.of()).build()),
                new StubHealthRecordService(new AtomicBoolean(false)),
                new StubAiChatCacheInvalidationService(new AtomicReference<>()),
                new StubMedplumAutoSyncService(recordRepository),
                new StubClinicalDataJobService(),
                new StubClinicalDataJobOrchestrator(),
                new StubClinicalDataModelService(),
                new PaperCaseUploadSecurityService()
        );

        List<PaperCaseRecordDTO> records = service.getPendingReviewRecordsForDoctor(9L);

        assertEquals(1, records.size());
        assertEquals(311L, records.get(0).getId());
        assertEquals(44L, records.get(0).getPatientId());
        assertEquals("pending_review", records.get(0).getIcdCodingStatus());
    }

    @Test
    void shouldListFailedSyncPaperCaseRecordsForDoctorScope() {
        PaperCaseRecord ownedRecord = PaperCaseRecord.builder()
                .id(321L)
                .patientId(44L)
                .patientName("钱某")
                .preliminaryDiagnosis("慢性胃炎")
                .medplumSyncStatus("failed")
                .medplumSyncMessage("owned failed sync")
                .build();
        PaperCaseRecord otherRecord = PaperCaseRecord.builder()
                .id(322L)
                .patientId(45L)
                .patientName("孙某")
                .preliminaryDiagnosis("反流性食管炎")
                .medplumSyncStatus("failed")
                .medplumSyncMessage("other failed sync")
                .build();

        PatientRepository patientRepository = createProxy(PatientRepository.class, Map.of(
                "findByAssignedDoctorId", args -> List.of(
                        Patient.builder().id(44L).assignedDoctorId(9L).build()
                ),
                "findById", args -> Optional.empty()
        ));
        PaperCaseRecordRepository recordRepository = createProxy(PaperCaseRecordRepository.class, Map.of(
                "findByPatientIdInAndMedplumSyncStatusOrderByUpdatedAtDesc", args -> {
                    List<Long> patientIds = (List<Long>) args[0];
                    String status = (String) args[1];
                    return "failed".equals(status) && patientIds.contains(44L) && !patientIds.contains(45L)
                            ? List.of(ownedRecord)
                            : List.of(otherRecord);
                },
                "findByPatientIdOrderByCreatedAtDesc", args -> List.of()
        ));
        PatientMedicalHistoryRepository historyRepository = createProxy(PatientMedicalHistoryRepository.class, Map.of(
                "findByPatientId", args -> Optional.empty(),
                "save", args -> args[0]
        ));

        PaperCaseService service = new PaperCaseService(
                recordRepository,
                patientRepository,
                historyRepository,
                new StubPaperCaseVisionOcrService(PaperCaseExtractionResult.builder().build()),
                new StubPaperCaseIcd11Service(PaperCaseIcdMatchResult.builder().candidates(List.of()).build()),
                new StubHealthRecordService(new AtomicBoolean(false)),
                new StubAiChatCacheInvalidationService(new AtomicReference<>()),
                new StubMedplumAutoSyncService(recordRepository),
                new StubClinicalDataJobService(),
                new StubClinicalDataJobOrchestrator(),
                new StubClinicalDataModelService(),
                new PaperCaseUploadSecurityService()
        );

        List<PaperCaseRecordDTO> records = service.getFailedSyncRecordsForDoctor(9L);

        assertEquals(1, records.size());
        assertEquals(321L, records.get(0).getId());
        assertEquals(44L, records.get(0).getPatientId());
        assertEquals("failed", records.get(0).getMedplumSyncStatus());
        assertEquals("owned failed sync", records.get(0).getMedplumSyncMessage());
    }

    @Test
    void shouldBuildPatientScopedPaperCaseSummary() {
        PaperCaseRecord pendingRecord = PaperCaseRecord.builder()
                .id(401L)
                .patientId(77L)
                .patientName("刘某")
                .gender("男")
                .age(52)
                .preliminaryDiagnosis("胃食管反流病")
                .icdCodingStatus("pending_review")
                .medplumSyncStatus("review_pending")
                .build();
        PaperCaseRecord failedRecord = PaperCaseRecord.builder()
                .id(402L)
                .patientId(77L)
                .patientName("刘某")
                .gender("男")
                .age(52)
                .preliminaryDiagnosis("慢性胃炎")
                .icdCodingStatus("manually_confirmed")
                .medplumSyncStatus("failed")
                .build();

        PatientRepository patientRepository = createProxy(PatientRepository.class, Map.of(
                "findById", args -> Optional.empty()
        ));
        PaperCaseRecordRepository recordRepository = createProxy(PaperCaseRecordRepository.class, Map.of(
                "findByPatientIdOrderByCreatedAtDesc", args -> List.of(pendingRecord, failedRecord)
        ));
        PatientMedicalHistoryRepository historyRepository = createProxy(PatientMedicalHistoryRepository.class, Map.of(
                "findByPatientId", args -> Optional.empty(),
                "save", args -> args[0]
        ));

        StubClinicalDataJobService jobService = new StubClinicalDataJobService();
        jobService.seedJob(ClinicalDataJob.builder()
                .id(501L)
                .jobType(ClinicalDataJobService.JOB_TYPE_PAPER_CASE)
                .jobStatus(ClinicalDataJobService.STATUS_REVIEW_PENDING)
                .patientId(77L)
                .sourceKind(ClinicalDataJobService.SOURCE_KIND_PAPER_CASE_RECORD)
                .sourceRefId(401L)
                .lastStage("awaiting_manual_icd_review")
                .build());
        jobService.seedJob(ClinicalDataJob.builder()
                .id(502L)
                .jobType(ClinicalDataJobService.JOB_TYPE_PAPER_CASE)
                .jobStatus(ClinicalDataJobService.STATUS_FAILED)
                .patientId(77L)
                .sourceKind(ClinicalDataJobService.SOURCE_KIND_PAPER_CASE_RECORD)
                .sourceRefId(402L)
                .lastStage("manual_icd_confirmed_medplum_sync_failed")
                .lastError("stub sync failed")
                .build());

        PaperCaseService service = new PaperCaseService(
                recordRepository,
                patientRepository,
                historyRepository,
                new StubPaperCaseVisionOcrService(PaperCaseExtractionResult.builder().build()),
                new StubPaperCaseIcd11Service(PaperCaseIcdMatchResult.builder().candidates(List.of()).build()),
                new StubHealthRecordService(new AtomicBoolean(false)),
                new StubAiChatCacheInvalidationService(new AtomicReference<>()),
                new StubMedplumAutoSyncService(recordRepository),
                jobService,
                new StubClinicalDataJobOrchestrator(),
                new StubClinicalDataModelService(),
                new PaperCaseUploadSecurityService()
        );

        PaperCasePatientSummaryDTO summary = service.getPatientSummary(77L);

        assertEquals(77L, summary.getPatientId());
        assertEquals(2L, summary.getTotalRecords());
        assertEquals(1L, summary.getPendingReviewCount());
        assertEquals(1L, summary.getFailedSyncCount());
        assertEquals(1L, summary.getFailedJobCount());
        assertEquals(2, summary.getAttentionRecords().size());
        assertEquals(1, summary.getPendingReviewRecords().size());
        assertEquals(1, summary.getFailedSyncRecords().size());
        assertEquals("刘某", summary.getPatientInfo().getName());
    }

    @Test
    void shouldMarkJobFailedWhenMedplumAutoSyncFails() throws Exception {
        String previousUserDir = System.getProperty("user.dir");
        System.setProperty("user.dir", tempDir.toString());
        try {
            AtomicReference<PaperCaseRecord> savedRecord = new AtomicReference<>();
            StubClinicalDataJobService jobService = new StubClinicalDataJobService();

            PatientRepository patientRepository = createProxy(PatientRepository.class, Map.of(
                    "findById", args -> Optional.of(Patient.builder()
                            .id(15L)
                            .name("周某")
                            .gender("男")
                            .build())
            ));
            PaperCaseRecordRepository recordRepository = createProxy(PaperCaseRecordRepository.class, Map.of(
                    "save", args -> {
                        PaperCaseRecord record = (PaperCaseRecord) args[0];
                        record.setId(15L);
                        savedRecord.set(record);
                        return record;
                    },
                    "findById", args -> Optional.ofNullable(savedRecord.get()),
                    "findByPatientIdOrderByCreatedAtDesc", args -> List.of()
            ));
            PatientMedicalHistoryRepository historyRepository = createProxy(PatientMedicalHistoryRepository.class, Map.of(
                    "findByPatientId", args -> Optional.empty(),
                    "save", args -> args[0]
            ));

            PaperCaseService service = new PaperCaseService(
                    recordRepository,
                    patientRepository,
                    historyRepository,
                    new StubPaperCaseVisionOcrService(PaperCaseExtractionResult.builder()
                            .rawText("患者周某，诊断：胃食管反流病")
                            .preliminaryDiagnosis("胃食管反流病")
                            .build()),
                    new StubPaperCaseIcd11Service(PaperCaseIcdMatchResult.builder()
                            .code("DA22")
                            .title("胃食管反流病")
                            .codingStatus("auto_coded")
                            .codingSource("local-mapping")
                            .candidates(List.of())
                            .build()),
                    new StubHealthRecordService(new AtomicBoolean(false)),
                    new StubAiChatCacheInvalidationService(new AtomicReference<>()),
                    new FailingStubMedplumAutoSyncService(recordRepository),
                    jobService,
                    new StubClinicalDataJobOrchestrator(),
                    new StubClinicalDataModelService(),
                    new PaperCaseUploadSecurityService()
            );

            MockMultipartFile file = new MockMultipartFile("file", "case.jpg", "image/jpeg", new byte[]{
                    (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00
            });
            PaperCaseRecordDTO result = service.processUpload(15L, file, LocalDate.of(2026, 5, 28));

            assertEquals("failed", result.getJob().getJobStatus());
            assertEquals("medplum_sync_failed", result.getJob().getLastStage());
            assertEquals("failed", savedRecord.get().getMedplumSyncStatus());
            assertEquals("stub sync failed", savedRecord.get().getMedplumSyncMessage());
        } finally {
            System.setProperty("user.dir", previousUserDir);
        }
    }

    @Test
    void shouldMarkJobSyncedWhenMedplumAutoSyncIsSkipped() throws Exception {
        String previousUserDir = System.getProperty("user.dir");
        System.setProperty("user.dir", tempDir.toString());
        try {
            AtomicReference<PaperCaseRecord> savedRecord = new AtomicReference<>();
            StubClinicalDataJobService jobService = new StubClinicalDataJobService();

            PatientRepository patientRepository = createProxy(PatientRepository.class, Map.of(
                    "findById", args -> Optional.of(Patient.builder()
                            .id(16L)
                            .name("郑某")
                            .gender("女")
                            .build())
            ));
            PaperCaseRecordRepository recordRepository = createProxy(PaperCaseRecordRepository.class, Map.of(
                    "save", args -> {
                        PaperCaseRecord record = (PaperCaseRecord) args[0];
                        record.setId(16L);
                        savedRecord.set(record);
                        return record;
                    },
                    "findById", args -> Optional.ofNullable(savedRecord.get()),
                    "findByPatientIdOrderByCreatedAtDesc", args -> List.of()
            ));
            PatientMedicalHistoryRepository historyRepository = createProxy(PatientMedicalHistoryRepository.class, Map.of(
                    "findByPatientId", args -> Optional.empty(),
                    "save", args -> args[0]
            ));

            PaperCaseService service = new PaperCaseService(
                    recordRepository,
                    patientRepository,
                    historyRepository,
                    new StubPaperCaseVisionOcrService(PaperCaseExtractionResult.builder()
                            .rawText("患者郑某，诊断：胃食管反流病")
                            .preliminaryDiagnosis("胃食管反流病")
                            .build()),
                    new StubPaperCaseIcd11Service(PaperCaseIcdMatchResult.builder()
                            .code("DA22")
                            .title("胃食管反流病")
                            .codingStatus("auto_coded")
                            .codingSource("local-mapping")
                            .candidates(List.of())
                            .build()),
                    new StubHealthRecordService(new AtomicBoolean(false)),
                    new StubAiChatCacheInvalidationService(new AtomicReference<>()),
                    new SkippingStubMedplumAutoSyncService(recordRepository),
                    jobService,
                    new StubClinicalDataJobOrchestrator(),
                    new StubClinicalDataModelService(),
                    new PaperCaseUploadSecurityService()
            );

            MockMultipartFile file = new MockMultipartFile("file", "case.jpg", "image/jpeg", new byte[]{
                    (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00
            });
            PaperCaseRecordDTO result = service.processUpload(16L, file, LocalDate.of(2026, 5, 28));

            assertEquals(ClinicalDataJobService.STATUS_SYNCED, result.getJob().getJobStatus());
            assertEquals("medplum_sync_skipped", result.getJob().getLastStage());
            assertEquals("skipped", savedRecord.get().getMedplumSyncStatus());
            assertEquals("stub skipped", savedRecord.get().getMedplumSyncMessage());
        } finally {
            System.setProperty("user.dir", previousUserDir);
        }
    }

    @Test
    void shouldStopPaperCaseJobWhenCancelledAfterParse() throws Exception {
        String previousUserDir = System.getProperty("user.dir");
        System.setProperty("user.dir", tempDir.toString());
        try {
            AtomicBoolean createCalled = new AtomicBoolean(false);
            AtomicBoolean medplumCalled = new AtomicBoolean(false);
            StubClinicalDataJobService jobService = new StubClinicalDataJobService();
            jobService.cancelAfterStage = "ocr_parsed";

            PatientRepository patientRepository = createProxy(PatientRepository.class, Map.of(
                    "findById", args -> Optional.of(Patient.builder().id(25L).name("吴某").gender("女").build())
            ));
            PaperCaseRecordRepository recordRepository = createProxy(PaperCaseRecordRepository.class, Map.of(
                    "save", args -> {
                        PaperCaseRecord record = (PaperCaseRecord) args[0];
                        record.setId(25L);
                        return record;
                    },
                    "findById", args -> Optional.of(PaperCaseRecord.builder().id(25L).patientId(25L).build()),
                    "findByPatientIdOrderByCreatedAtDesc", args -> List.of()
            ));
            PatientMedicalHistoryRepository historyRepository = createProxy(PatientMedicalHistoryRepository.class, Map.of(
                    "findByPatientId", args -> Optional.empty(),
                    "save", args -> args[0]
            ));

            PaperCaseService service = new PaperCaseService(
                    recordRepository,
                    patientRepository,
                    historyRepository,
                    new StubPaperCaseVisionOcrService(PaperCaseExtractionResult.builder()
                            .rawText("患者吴某，诊断：胃食管反流病")
                            .preliminaryDiagnosis("胃食管反流病")
                            .build()),
                    new StubPaperCaseIcd11Service(PaperCaseIcdMatchResult.builder()
                            .code("DA22")
                            .title("胃食管反流病")
                            .codingStatus("auto_coded")
                            .codingSource("local-mapping")
                            .candidates(List.of())
                            .build()),
                    new StubHealthRecordService(createCalled),
                    new StubAiChatCacheInvalidationService(new AtomicReference<>()),
                    new RecordingStubMedplumAutoSyncService(recordRepository, medplumCalled),
                    jobService,
                    new StubClinicalDataJobOrchestrator(),
                    new StubClinicalDataModelService(),
                    new PaperCaseUploadSecurityService()
            );

            MockMultipartFile file = new MockMultipartFile("file", "case.jpg", "image/jpeg", new byte[]{
                    (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00
            });

            PaperCaseRecordDTO result = service.processUpload(25L, file, LocalDate.of(2026, 5, 28));

            assertEquals("cancelled", result.getJob().getJobStatus());
            assertEquals("manual_cancel", result.getJob().getLastStage());
            assertFalse(createCalled.get());
            assertFalse(medplumCalled.get());
        } finally {
            System.setProperty("user.dir", previousUserDir);
        }
    }

    @Test
    void shouldRejectSpoofedFileContent() {
        PatientRepository patientRepository = createProxy(PatientRepository.class, Map.of(
                "findById", args -> Optional.of(Patient.builder().id(30L).name("孙某").build())
        ));
        PaperCaseRecordRepository recordRepository = createProxy(PaperCaseRecordRepository.class, Map.of(
                "save", args -> args[0],
                "findByPatientIdOrderByCreatedAtDesc", args -> List.of()
        ));
        PatientMedicalHistoryRepository historyRepository = createProxy(PatientMedicalHistoryRepository.class, Map.of(
                "findByPatientId", args -> Optional.empty(),
                "save", args -> args[0]
        ));

        PaperCaseService service = new PaperCaseService(
                recordRepository,
                patientRepository,
                historyRepository,
                new StubPaperCaseVisionOcrService(PaperCaseExtractionResult.builder().build()),
                new StubPaperCaseIcd11Service(PaperCaseIcdMatchResult.builder().candidates(List.of()).build()),
                new StubHealthRecordService(new AtomicBoolean(false)),
                new StubAiChatCacheInvalidationService(new AtomicReference<>()),
                new StubMedplumAutoSyncService(recordRepository),
                new StubClinicalDataJobService(),
                new StubClinicalDataJobOrchestrator(),
                new StubClinicalDataModelService(),
                new PaperCaseUploadSecurityService()
        );

        MockMultipartFile file = new MockMultipartFile("file", "case.jpg", "image/jpeg", "not-a-jpeg".getBytes());

        BusinessException ex = null;
        try {
            service.processUpload(30L, file, LocalDate.of(2026, 5, 28));
        } catch (BusinessException error) {
            ex = error;
        }

        assertNotNull(ex);
        assertEquals(400, ex.getCode());
        assertEquals("病历文件内容与扩展名不匹配", ex.getMessage());
    }

    @Test
    void shouldReturnExistingPaperCaseWhenDuplicateUploadSubmitted() {
        PatientRepository patientRepository = createProxy(PatientRepository.class, Map.of(
                "findById", args -> Optional.of(Patient.builder().id(66L).name("钱某").gender("男").build())
        ));
        PaperCaseRecordRepository recordRepository = createProxy(PaperCaseRecordRepository.class, Map.of(
                "findById", args -> Optional.of(PaperCaseRecord.builder()
                        .id(66L)
                        .patientId(66L)
                        .preliminaryDiagnosis("胃食管反流病")
                        .sourceObjectKey("paper-cases/66/existing.jpg")
                        .sourceFileType("image/jpeg")
                        .originalFileName("existing.jpg")
                        .build()),
                "findByPatientIdOrderByCreatedAtDesc", args -> List.of()
        ));
        PatientMedicalHistoryRepository historyRepository = createProxy(PatientMedicalHistoryRepository.class, Map.of(
                "findByPatientId", args -> Optional.empty(),
                "save", args -> args[0]
        ));
        StubClinicalDataJobService jobService = new StubClinicalDataJobService();
        jobService.duplicateJobDto = com.medical.agent.common.dto.ClinicalDataJobDTO.builder()
                .id(101L)
                .jobType(ClinicalDataJobService.JOB_TYPE_PAPER_CASE)
                .jobStatus(ClinicalDataJobService.STATUS_RECEIVED)
                .correlationId("paper-case-66-existing")
                .idempotencyKey("paper-case:66:duplicate")
                .sourceRefId(66L)
                .build();

        PaperCaseService service = new PaperCaseService(
                recordRepository,
                patientRepository,
                historyRepository,
                new StubPaperCaseVisionOcrService(PaperCaseExtractionResult.builder().build()),
                new StubPaperCaseIcd11Service(PaperCaseIcdMatchResult.builder().candidates(List.of()).build()),
                new StubHealthRecordService(new AtomicBoolean(false)),
                new StubAiChatCacheInvalidationService(new AtomicReference<>()),
                new StubMedplumAutoSyncService(recordRepository),
                jobService,
                new StubClinicalDataJobOrchestrator(),
                new StubClinicalDataModelService(),
                new PaperCaseUploadSecurityService()
        );

        MockMultipartFile file = new MockMultipartFile("file", "case.jpg", "image/jpeg", new byte[]{
                (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00, 0x01
        });

        PaperCaseRecordDTO result = service.processUpload(66L, file, LocalDate.of(2026, 5, 28));

        assertEquals(66L, result.getId());
        assertEquals("duplicate", result.getMedplumSyncStatus());
        assertEquals(101L, result.getJob().getId());
        assertEquals("重复病历上传已被拦截，可查看既有作业链", result.getMedplumSyncMessage());
    }

    @Test
    void shouldUsePaperCaseSourceKindConstantForJobLookupAndCreation() {
        AtomicReference<PaperCaseRecord> savedRecord = new AtomicReference<>();

        PatientRepository patientRepository = createProxy(PatientRepository.class, Map.of(
                "findById", args -> Optional.of(Patient.builder().id(77L).name("孙某").gender("女").build())
        ));
        PaperCaseRecordRepository recordRepository = createProxy(PaperCaseRecordRepository.class, Map.of(
                "save", args -> {
                    PaperCaseRecord record = (PaperCaseRecord) args[0];
                    if (record.getId() == null) {
                        record.setId(77L);
                    }
                    savedRecord.set(record);
                    return record;
                },
                "findById", args -> Optional.ofNullable(savedRecord.get()),
                "findByPatientIdOrderByCreatedAtDesc", args -> savedRecord.get() == null ? List.of() : List.of(savedRecord.get())
        ));
        PatientMedicalHistoryRepository historyRepository = createProxy(PatientMedicalHistoryRepository.class, Map.of(
                "findByPatientId", args -> Optional.empty(),
                "save", args -> args[0]
        ));
        StubClinicalDataJobService jobService = new StubClinicalDataJobService();

        PaperCaseService service = new PaperCaseService(
                recordRepository,
                patientRepository,
                historyRepository,
                new StubPaperCaseVisionOcrService(PaperCaseExtractionResult.builder()
                        .rawText("患者孙某，诊断：胃炎")
                        .preliminaryDiagnosis("胃炎")
                        .build()),
                new StubPaperCaseIcd11Service(PaperCaseIcdMatchResult.builder().candidates(List.of()).build()),
                new StubHealthRecordService(new AtomicBoolean(false)),
                new StubAiChatCacheInvalidationService(new AtomicReference<>()),
                new StubMedplumAutoSyncService(recordRepository),
                jobService,
                new StubClinicalDataJobOrchestrator(),
                new StubClinicalDataModelService(),
                new PaperCaseUploadSecurityService()
        );

        MockMultipartFile file = new MockMultipartFile("file", "case.jpg", "image/jpeg", new byte[]{
                (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00, 0x01
        });

        PaperCaseRecordDTO uploaded = service.processUpload(77L, file, LocalDate.of(2026, 5, 29));
        assertNotNull(uploaded.getJob());
        assertEquals(ClinicalDataJobService.SOURCE_KIND_PAPER_CASE_RECORD, jobService.lastCreatedSourceKind);
        assertEquals(ClinicalDataJobService.SOURCE_KIND_PAPER_CASE_RECORD, uploaded.getJob().getSourceKind());

        List<PaperCaseRecordDTO> records = service.getByPatientId(77L);
        assertEquals(1, records.size());
        assertNotNull(records.get(0).getJob());
        assertEquals(ClinicalDataJobService.SOURCE_KIND_PAPER_CASE_RECORD, jobService.lastLookupSourceKind);
    }

    private static final class StubMedplumAutoSyncService extends MedplumAutoSyncService {
        private StubMedplumAutoSyncService(PaperCaseRecordRepository repository) {
            super(null, repository);
        }

        @Override
        public PaperCaseRecord syncPaperCaseRecord(PaperCaseRecord record) {
            record.setMedplumSyncStatus("synced");
            record.setMedplumSyncMessage("stub synced");
            return record;
        }
    }

    private static final class RecordingStubMedplumAutoSyncService extends MedplumAutoSyncService {
        private final AtomicBoolean called;

        private RecordingStubMedplumAutoSyncService(PaperCaseRecordRepository repository, AtomicBoolean called) {
            super(null, repository);
            this.called = called;
        }

        @Override
        public PaperCaseRecord syncPaperCaseRecord(PaperCaseRecord record) {
            called.set(true);
            record.setMedplumSyncStatus("synced");
            record.setMedplumSyncMessage("stub synced");
            return record;
        }
    }

    private static final class FailingStubMedplumAutoSyncService extends MedplumAutoSyncService {
        private FailingStubMedplumAutoSyncService(PaperCaseRecordRepository repository) {
            super(null, repository);
        }

        @Override
        public PaperCaseRecord syncPaperCaseRecord(PaperCaseRecord record) {
            record.setMedplumSyncStatus("failed");
            record.setMedplumSyncMessage("stub sync failed");
            record.setMedplumSyncedAt(null);
            return record;
        }
    }

    private static final class SkippingStubMedplumAutoSyncService extends MedplumAutoSyncService {
        private SkippingStubMedplumAutoSyncService(PaperCaseRecordRepository repository) {
            super(null, repository);
        }

        @Override
        public PaperCaseRecord syncPaperCaseRecord(PaperCaseRecord record) {
            record.setMedplumSyncStatus("skipped");
            record.setMedplumSyncMessage("stub skipped");
            record.setMedplumSyncedAt(null);
            return record;
        }
    }

    private static final class StubClinicalDataJobService extends ClinicalDataJobService {
        private ClinicalDataJob job;
        private com.medical.agent.common.dto.ClinicalDataJobDTO duplicateJobDto;
        private String cancelAfterStage;
        private String lastCreatedSourceKind;
        private String lastLookupSourceKind;
        private final Map<Long, ClinicalDataJob> jobsBySourceRefId = new LinkedHashMap<>();

        private StubClinicalDataJobService() {
            super(null);
        }

        private void seedJob(ClinicalDataJob seededJob) {
            this.job = seededJob;
            if (seededJob != null && seededJob.getSourceRefId() != null) {
                jobsBySourceRefId.put(seededJob.getSourceRefId(), seededJob);
            }
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
            lastCreatedSourceKind = sourceKind;
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
                    .createdAt(java.time.LocalDateTime.now())
                    .updatedAt(java.time.LocalDateTime.now())
                    .build();
            if (sourceRefId != null) {
                jobsBySourceRefId.put(sourceRefId, job);
            }
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
            if (duplicateJobDto != null) {
                job = ClinicalDataJob.builder()
                        .id(duplicateJobDto.getId())
                        .jobType(duplicateJobDto.getJobType())
                        .jobStatus(duplicateJobDto.getJobStatus())
                        .patientId(patientId)
                        .correlationId(duplicateJobDto.getCorrelationId())
                        .idempotencyKey(duplicateJobDto.getIdempotencyKey())
                        .sourceRefId(duplicateJobDto.getSourceRefId())
                        .build();
                if (job.getSourceRefId() != null) {
                    jobsBySourceRefId.put(job.getSourceRefId(), job);
                }
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
            job.setStartedAt(java.time.LocalDateTime.now());
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
            job.setUpdatedAt(java.time.LocalDateTime.now());
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
        public com.medical.agent.common.dto.ClinicalDataJobDTO findLatestByIdempotencyKey(String idempotencyKey) {
            return duplicateJobDto;
        }

        @Override
        public ClinicalDataJob findLatestBySource(String sourceKind, Long sourceRefId) {
            lastLookupSourceKind = sourceKind;
            if (sourceRefId != null && jobsBySourceRefId.containsKey(sourceRefId)) {
                return jobsBySourceRefId.get(sourceRefId);
            }
            return job;
        }

        @Override
        public com.medical.agent.common.dto.ClinicalDataJobDTO toDTO(ClinicalDataJob job) {
            return job == null ? null : com.medical.agent.common.dto.ClinicalDataJobDTO.builder()
                    .id(job.getId())
                    .jobType(job.getJobType())
                    .jobStatus(job.getJobStatus())
                    .patientId(job.getPatientId())
                    .correlationId(job.getCorrelationId())
                    .idempotencyKey(job.getIdempotencyKey())
                    .sourceKind(job.getSourceKind())
                    .sourceRefId(job.getSourceRefId())
                    .sourceFileName(job.getSourceFileName())
                    .sourceFileUrl(job.getSourceFileUrl())
                    .vendor(job.getVendor())
                    .lastStage(job.getLastStage())
                    .lastError(job.getLastError())
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
        public com.medical.agent.model.entity.ClinicalEncounter upsertPaperCaseEncounter(PaperCaseRecord record,
                                                                                          com.medical.agent.service.papercase.PaperCaseExtractionResult extraction,
                                                                                          com.medical.agent.service.papercase.PaperCaseIcdMatchResult icdMatch) {
            return com.medical.agent.model.entity.ClinicalEncounter.builder()
                    .id(record.getId())
                    .patientId(record.getPatientId())
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

    private <T> T createProxy(Class<T> type, Map<String, Function<Object[], Object>> handlers) {
        InvocationHandler invocationHandler = (proxy, method, args) -> {
            String methodName = method.getName();
            if ("toString".equals(methodName)) {
                return type.getSimpleName() + "Proxy";
            }
            if ("hashCode".equals(methodName)) {
                return System.identityHashCode(proxy);
            }
            if ("equals".equals(methodName)) {
                return proxy == args[0];
            }

            Function<Object[], Object> handler = handlers.get(methodName);
            if (handler != null) {
                return handler.apply(args == null ? new Object[0] : args);
            }
            return defaultValue(method);
        };
        Object proxy = Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, invocationHandler);
        return type.cast(proxy);
    }

    private Object defaultValue(Method method) {
        Class<?> returnType = method.getReturnType();
        if (!returnType.isPrimitive()) {
            if (Optional.class.isAssignableFrom(returnType)) {
                return Optional.empty();
            }
            if (List.class.isAssignableFrom(returnType)) {
                return List.of();
            }
            if (Set.class.isAssignableFrom(returnType)) {
                return Set.of();
            }
            if (Map.class.isAssignableFrom(returnType)) {
                return Map.of();
            }
            return null;
        }
        if (boolean.class.equals(returnType)) {
            return false;
        }
        if (byte.class.equals(returnType)) {
            return (byte) 0;
        }
        if (short.class.equals(returnType)) {
            return (short) 0;
        }
        if (int.class.equals(returnType)) {
            return 0;
        }
        if (long.class.equals(returnType)) {
            return 0L;
        }
        if (float.class.equals(returnType)) {
            return 0f;
        }
        if (double.class.equals(returnType)) {
            return 0d;
        }
        if (char.class.equals(returnType)) {
            return '\0';
        }
        return null;
    }

    private static final class StubPaperCaseVisionOcrService extends PaperCaseVisionOcrService {
        private final PaperCaseExtractionResult result;
        private final RuntimeException error;

        private StubPaperCaseVisionOcrService(PaperCaseExtractionResult result) {
            this.result = result;
            this.error = null;
        }

        private StubPaperCaseVisionOcrService(RuntimeException error) {
            this.result = null;
            this.error = error;
        }

        @Override
        public PaperCaseExtractionResult extract(Path filePath, String fileType) {
            if (error != null) {
                throw error;
            }
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
        private final AtomicBoolean called;

        private StubHealthRecordService(AtomicReference<HealthRecordDTO> captured) {
            super(null, null, null);
            this.captured = captured;
            this.called = null;
        }

        private StubHealthRecordService(AtomicBoolean called) {
            super(null, null, null);
            this.captured = null;
            this.called = called;
        }

        @Override
        public HealthRecordDTO create(HealthRecordDTO dto,
                                      com.medical.agent.common.enums.DiseaseType diseaseType,
                                      boolean triggerAssessment,
                                      Map<String, Object> assessmentOptions) {
            if (captured != null) {
                captured.set(dto);
            }
            if (called != null) {
                called.set(true);
            }
            return dto;
        }
    }

    private static final class StubAiChatCacheInvalidationService extends AiChatCacheInvalidationService {
        private final AtomicReference<Long> invalidatedPatientId;

        private StubAiChatCacheInvalidationService(AtomicReference<Long> invalidatedPatientId) {
            super(new NoOpCacheManager(), new com.medical.agent.service.cache.AiChatCacheVersionService());
            this.invalidatedPatientId = invalidatedPatientId;
        }

        @Override
        public void invalidatePatient(Long patientId) {
            invalidatedPatientId.set(patientId);
        }
    }
}
