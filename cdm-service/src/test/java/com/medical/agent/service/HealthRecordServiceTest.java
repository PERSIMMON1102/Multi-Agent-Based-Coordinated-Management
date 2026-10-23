package com.medical.agent.service;

import com.medical.agent.common.dto.HealthRecordDTO;
import com.medical.agent.common.enums.DiseaseType;
import com.medical.agent.common.exception.BusinessException;
import com.medical.agent.model.entity.ClinicalDataJob;
import com.medical.agent.model.entity.HealthRecord;
import com.medical.agent.model.repository.ClinicalDataJobRepository;
import com.medical.agent.model.repository.HealthRecordRepository;
import com.medical.agent.service.cache.AiChatCacheInvalidationService;
import com.medical.agent.service.clinicaljob.ClinicalDataJobService;
import com.medical.agent.service.testsupport.RepositoryProxyFactory;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class HealthRecordServiceTest {

    @Test
    void shouldCloseFailedClinicalJobAfterManualHealthRecordRecovery() {
        Map<Long, HealthRecord> records = new LinkedHashMap<>();
        Map<Long, ClinicalDataJob> jobs = new LinkedHashMap<>();
        ClinicalDataJob failedJob = ClinicalDataJob.builder()
                .id(41L)
                .jobType(ClinicalDataJobService.JOB_TYPE_HUAWEI_IMPORT)
                .jobStatus(ClinicalDataJobService.STATUS_FAILED)
                .patientId(12L)
                .lastStage("medplum_sync_failed")
                .firstFailedAt(LocalDateTime.now().minusMinutes(3))
                .createdAt(LocalDateTime.now().minusMinutes(10))
                .updatedAt(LocalDateTime.now().minusMinutes(1))
                .build();
        jobs.put(failedJob.getId(), failedJob);

        AtomicReference<Long> invalidatedPatientId = new AtomicReference<>();
        AtomicReference<Map<String, Object>> assessmentHealthData = new AtomicReference<>();
        AtomicReference<Map<String, Object>> assessmentOptions = new AtomicReference<>();

        HealthRecordService service = new HealthRecordService(
                healthRecordRepository(records),
                new CapturingAssessmentOrchestrator(assessmentHealthData, assessmentOptions),
                cacheInvalidationService(invalidatedPatientId),
                new SuccessfulMedplumAutoSyncService(),
                clinicalDataJobService(jobs)
        );

        HealthRecordDTO created = service.create(HealthRecordDTO.builder()
                .patientId(12L)
                .recordDate(LocalDate.of(2026, 5, 30))
                .systolicBp(138)
                .diastolicBp(88)
                .fastingGlucose(6.8)
                .recoveryJobId(41L)
                .build(), DiseaseType.DIABETES, true, Map.of("source", "manual_recovery"));

        assertNotNull(created.getId());
        assertEquals(12L, invalidatedPatientId.get());
        assertEquals(138, assessmentHealthData.get().get("systolicBp"));
        assertEquals("manual_recovery", assessmentOptions.get().get("source"));
        assertEquals(ClinicalDataJobService.STATUS_SYNCED, failedJob.getJobStatus());
        assertEquals("manual_health_record_recovered", failedJob.getLastStage());
        assertNotNull(failedJob.getFinishedAt());
        assertEquals(41L, failedJob.getId());
    }

    @Test
    void shouldKeepJobFailedWhenManualRecoveryMedplumSyncFails() {
        Map<Long, HealthRecord> records = new LinkedHashMap<>();
        Map<Long, ClinicalDataJob> jobs = new LinkedHashMap<>();
        ClinicalDataJob failedJob = ClinicalDataJob.builder()
                .id(42L)
                .jobType(ClinicalDataJobService.JOB_TYPE_HUAWEI_IMPORT)
                .jobStatus(ClinicalDataJobService.STATUS_FAILED)
                .patientId(12L)
                .retryCount(0)
                .maxRetries(3)
                .build();
        jobs.put(failedJob.getId(), failedJob);

        HealthRecordService service = new HealthRecordService(
                healthRecordRepository(records),
                new CapturingAssessmentOrchestrator(new AtomicReference<>(), new AtomicReference<>()),
                cacheInvalidationService(new AtomicReference<>()),
                new FailingMedplumAutoSyncService(),
                clinicalDataJobService(jobs)
        );

        service.create(HealthRecordDTO.builder()
                .patientId(12L)
                .recordDate(LocalDate.of(2026, 5, 30))
                .systolicBp(126)
                .recoveryJobId(42L)
                .build());

        assertEquals(ClinicalDataJobService.STATUS_FAILED, failedJob.getJobStatus());
        assertEquals("manual_health_record_sync_failed", failedJob.getLastStage());
        assertEquals("medplum unavailable", failedJob.getLastError());
    }

    @Test
    void shouldCloseFailedClinicalJobWhenManualRecoverySkipsMedplumSync() {
        Map<Long, HealthRecord> records = new LinkedHashMap<>();
        Map<Long, ClinicalDataJob> jobs = new LinkedHashMap<>();
        ClinicalDataJob failedJob = ClinicalDataJob.builder()
                .id(43L)
                .jobType(ClinicalDataJobService.JOB_TYPE_HUAWEI_IMPORT)
                .jobStatus(ClinicalDataJobService.STATUS_FAILED)
                .patientId(12L)
                .lastStage("medplum_sync_failed")
                .build();
        jobs.put(failedJob.getId(), failedJob);

        HealthRecordService service = new HealthRecordService(
                healthRecordRepository(records),
                new CapturingAssessmentOrchestrator(new AtomicReference<>(), new AtomicReference<>()),
                cacheInvalidationService(new AtomicReference<>()),
                new SkippingMedplumAutoSyncService(),
                clinicalDataJobService(jobs)
        );

        service.create(HealthRecordDTO.builder()
                .patientId(12L)
                .recordDate(LocalDate.of(2026, 5, 30))
                .systolicBp(126)
                .recoveryJobId(43L)
                .build());

        assertEquals(ClinicalDataJobService.STATUS_SYNCED, failedJob.getJobStatus());
        assertEquals("manual_health_record_medplum_skipped", failedJob.getLastStage());
    }

    @Test
    void shouldRejectManualRecoveryForPaperCaseJobs() {
        Map<Long, ClinicalDataJob> jobs = new LinkedHashMap<>();
        jobs.put(51L, ClinicalDataJob.builder()
                .id(51L)
                .jobType(ClinicalDataJobService.JOB_TYPE_PAPER_CASE)
                .jobStatus(ClinicalDataJobService.STATUS_FAILED)
                .patientId(12L)
                .build());

        HealthRecordService service = new HealthRecordService(
                healthRecordRepository(new LinkedHashMap<>()),
                new CapturingAssessmentOrchestrator(new AtomicReference<>(), new AtomicReference<>()),
                cacheInvalidationService(new AtomicReference<>()),
                new SuccessfulMedplumAutoSyncService(),
                clinicalDataJobService(jobs)
        );

        BusinessException ex = assertThrows(BusinessException.class, () -> service.create(HealthRecordDTO.builder()
                .patientId(12L)
                .recordDate(LocalDate.of(2026, 5, 30))
                .recoveryJobId(51L)
                .build()));

        assertEquals(409, ex.getCode());
    }

    @Test
    void shouldRejectManualRecoveryForDifferentPatientJob() {
        Map<Long, ClinicalDataJob> jobs = new LinkedHashMap<>();
        jobs.put(61L, ClinicalDataJob.builder()
                .id(61L)
                .jobType(ClinicalDataJobService.JOB_TYPE_HUAWEI_IMPORT)
                .jobStatus(ClinicalDataJobService.STATUS_DEAD_LETTER)
                .patientId(99L)
                .build());

        HealthRecordService service = new HealthRecordService(
                healthRecordRepository(new LinkedHashMap<>()),
                new CapturingAssessmentOrchestrator(new AtomicReference<>(), new AtomicReference<>()),
                cacheInvalidationService(new AtomicReference<>()),
                new SuccessfulMedplumAutoSyncService(),
                clinicalDataJobService(jobs)
        );

        BusinessException ex = assertThrows(BusinessException.class, () -> service.create(HealthRecordDTO.builder()
                .patientId(12L)
                .recordDate(LocalDate.of(2026, 5, 30))
                .recoveryJobId(61L)
                .build()));

        assertEquals(403, ex.getCode());
    }

    @Test
    void shouldContinueWhenBestEffortAssessmentFails() {
        Map<Long, HealthRecord> records = new LinkedHashMap<>();
        AtomicReference<Long> invalidatedPatientId = new AtomicReference<>();

        HealthRecordService service = new HealthRecordService(
                healthRecordRepository(records),
                new ThrowingAssessmentOrchestrator(),
                cacheInvalidationService(invalidatedPatientId),
                null,
                null
        );

        HealthRecordDTO created = service.create(
                HealthRecordDTO.builder()
                        .patientId(15L)
                        .recordDate(LocalDate.of(2026, 5, 30))
                        .systolicBp(132)
                        .build(),
                null,
                true,
                Map.of("_bestEffortAssessment", true, "source", "device_import")
        );

        assertNotNull(created.getId());
        assertEquals(1, records.size());
        assertEquals(15L, invalidatedPatientId.get());
    }

    @Test
    void shouldPropagateAssessmentFailureWhenBestEffortDisabled() {
        Map<Long, HealthRecord> records = new LinkedHashMap<>();

        HealthRecordService service = new HealthRecordService(
                healthRecordRepository(records),
                new ThrowingAssessmentOrchestrator(),
                cacheInvalidationService(new AtomicReference<>()),
                null,
                null
        );

        assertThrows(IllegalStateException.class, () -> service.create(
                HealthRecordDTO.builder()
                        .patientId(16L)
                        .recordDate(LocalDate.of(2026, 5, 30))
                        .systolicBp(140)
                        .build(),
                null,
                true,
                        Map.of("source", "manual_entry")
        ));
    }

    @Test
    void shouldDeferAssessmentWhenFlagEnabled() {
        Map<Long, HealthRecord> records = new LinkedHashMap<>();

        HealthRecordService service = new HealthRecordService(
                healthRecordRepository(records),
                new ThrowingAssessmentOrchestrator(),
                cacheInvalidationService(new AtomicReference<>()),
                null,
                null
        );

        HealthRecordDTO created = service.create(
                HealthRecordDTO.builder()
                        .patientId(17L)
                        .recordDate(LocalDate.of(2026, 5, 30))
                        .systolicBp(128)
                        .build(),
                null,
                true,
                Map.of("_deferAssessment", true, "_bestEffortAssessment", true, "source", "device_import")
        );

        assertNotNull(created.getId());
        assertEquals(1, records.size());
    }

    @Test
    void shouldReturnNewestSameDayRecordFirst() {
        Map<Long, HealthRecord> records = new LinkedHashMap<>();
        records.put(71L, HealthRecord.builder()
                .id(71L)
                .patientId(11L)
                .recordDate(LocalDate.of(2026, 5, 30))
                .recordedAt(LocalDateTime.of(2026, 5, 30, 8, 0))
                .systolicBp(126)
                .build());
        records.put(72L, HealthRecord.builder()
                .id(72L)
                .patientId(11L)
                .recordDate(LocalDate.of(2026, 5, 30))
                .recordedAt(LocalDateTime.of(2026, 5, 30, 9, 0))
                .systolicBp(137)
                .build());

        HealthRecordService service = new HealthRecordService(
                healthRecordRepository(records),
                new CapturingAssessmentOrchestrator(new AtomicReference<>(), new AtomicReference<>()),
                cacheInvalidationService(new AtomicReference<>()),
                null,
                null
        );

        List<HealthRecordDTO> result = service.getByPatientId(11L);

        assertEquals(2, result.size());
        assertEquals(72L, result.get(0).getId());
        assertEquals(71L, result.get(1).getId());
    }

    private HealthRecordRepository healthRecordRepository(Map<Long, HealthRecord> records) {
        AtomicLong idSequence = new AtomicLong(records.size() + 1L);
        return RepositoryProxyFactory.create(HealthRecordRepository.class, Map.of(
                "save", args -> {
                    HealthRecord record = (HealthRecord) args[0];
                    if (record.getId() == null) {
                        record.setId(idSequence.getAndIncrement());
                    }
                    records.put(record.getId(), record);
                    return record;
                },
                "findByPatientIdOrderByRecordDateDesc", args -> records.values().stream()
                        .filter(record -> ((Long) args[0]).equals(record.getPatientId()))
                        .sorted((left, right) -> {
                            LocalDate leftDate = left.getRecordDate() == null ? LocalDate.MIN : left.getRecordDate();
                            LocalDate rightDate = right.getRecordDate() == null ? LocalDate.MIN : right.getRecordDate();
                            return rightDate.compareTo(leftDate);
                        })
                        .toList()
        ));
    }

    private ClinicalDataJobService clinicalDataJobService(Map<Long, ClinicalDataJob> jobs) {
        ClinicalDataJobRepository repository = RepositoryProxyFactory.create(ClinicalDataJobRepository.class, Map.of(
                "findById", args -> Optional.ofNullable(jobs.get((Long) args[0])),
                "save", args -> {
                    ClinicalDataJob job = (ClinicalDataJob) args[0];
                    jobs.put(job.getId(), job);
                    return job;
                },
                "countByJobStatus", args -> 0L
        ));
        return new ClinicalDataJobService(repository, new PlatformMetricsService(new SimpleMeterRegistry(), repository, null));
    }

    private AiChatCacheInvalidationService cacheInvalidationService(AtomicReference<Long> invalidatedPatientId) {
        return new AiChatCacheInvalidationService(null, null) {
            @Override
            public void invalidatePatient(Long patientId) {
                invalidatedPatientId.set(patientId);
            }
        };
    }

    private static final class CapturingAssessmentOrchestrator implements ClinicalAssessmentOrchestrator {
        private final AtomicReference<Map<String, Object>> healthDataRef;
        private final AtomicReference<Map<String, Object>> optionsRef;

        private CapturingAssessmentOrchestrator(AtomicReference<Map<String, Object>> healthDataRef,
                                                AtomicReference<Map<String, Object>> optionsRef) {
            this.healthDataRef = healthDataRef;
            this.optionsRef = optionsRef;
        }

        @Override
        public void triggerAssessment(Long patientId, DiseaseType diseaseType, Map<String, Object> healthData) {
            healthDataRef.set(healthData);
            optionsRef.set(Map.of());
        }

        @Override
        public void triggerAssessment(Long patientId,
                                      DiseaseType diseaseType,
                                      Map<String, Object> healthData,
                                      Map<String, Object> assessmentOptions) {
            healthDataRef.set(healthData);
            optionsRef.set(assessmentOptions);
        }
    }

    private static final class ThrowingAssessmentOrchestrator implements ClinicalAssessmentOrchestrator {
        @Override
        public void triggerAssessment(Long patientId, DiseaseType diseaseType, Map<String, Object> healthData) {
            throw new IllegalStateException("assessment unavailable");
        }

        @Override
        public void triggerAssessment(Long patientId,
                                      DiseaseType diseaseType,
                                      Map<String, Object> healthData,
                                      Map<String, Object> assessmentOptions) {
            throw new IllegalStateException("assessment unavailable");
        }
    }

    private static final class SuccessfulMedplumAutoSyncService extends MedplumAutoSyncService {
        private SuccessfulMedplumAutoSyncService() {
            super(null, null);
        }

        @Override
        public MedplumSyncOutcome replayPatientSync(Long patientId, boolean includeObservations) {
            return new MedplumSyncOutcome("synced", "ok", LocalDateTime.now());
        }
    }

    private static final class FailingMedplumAutoSyncService extends MedplumAutoSyncService {
        private FailingMedplumAutoSyncService() {
            super(null, null);
        }

        @Override
        public MedplumSyncOutcome replayPatientSync(Long patientId, boolean includeObservations) {
            return new MedplumSyncOutcome("failed", "medplum unavailable", null);
        }
    }

    private static final class SkippingMedplumAutoSyncService extends MedplumAutoSyncService {
        private SkippingMedplumAutoSyncService() {
            super(null, null);
        }

        @Override
        public MedplumSyncOutcome replayPatientSync(Long patientId, boolean includeObservations) {
            return new MedplumSyncOutcome("skipped", "medplum disabled", null);
        }
    }
}
