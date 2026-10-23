package com.medical.agent.service;

import com.medical.agent.common.dto.ClinicalDataJobDTO;
import com.medical.agent.common.exception.BusinessException;
import com.medical.agent.model.entity.ClinicalDataJob;
import com.medical.agent.model.entity.Patient;
import com.medical.agent.model.repository.PatientRepository;
import com.medical.agent.model.entity.PaperCaseRecord;
import com.medical.agent.model.repository.PaperCaseRecordRepository;
import com.medical.agent.service.clinicaljob.ClinicalDataJobService;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClinicalDataJobReplayServiceTest {

    @Test
    void shouldReplayPaperCaseMedplumSyncAndMarkJobSynced() {
        AtomicReference<ClinicalDataJob> jobRef = new AtomicReference<>(ClinicalDataJob.builder()
                .id(21L)
                .jobType(ClinicalDataJobService.JOB_TYPE_PAPER_CASE)
                .jobStatus(ClinicalDataJobService.STATUS_FAILED)
                .patientId(12L)
                .sourceKind(ClinicalDataJobService.SOURCE_KIND_PAPER_CASE_RECORD)
                .sourceRefId(66L)
                .lastStage("medplum_sync_failed")
                .build());
        AtomicReference<PaperCaseRecord> recordRef = new AtomicReference<>(PaperCaseRecord.builder()
                .id(66L)
                .patientId(12L)
                .medplumSyncStatus("failed")
                .medplumSyncMessage("sync failed")
                .build());

        ClinicalDataJobReplayService service = new ClinicalDataJobReplayService(
                new ReplayStubClinicalDataJobService(jobRef),
                new ReplayStubMedplumAutoSyncService(recordRef),
                createProxy(PaperCaseRecordRepository.class, Map.of(
                        "findById", args -> Optional.ofNullable(recordRef.get())
                ))
        );

        Map<String, Object> result = service.replayMedplumSync(21L);
        ClinicalDataJobDTO job = (ClinicalDataJobDTO) result.get("job");

        assertTrue((Boolean) result.get("success"));
        assertEquals("synced", result.get("medplumSyncStatus"));
        assertEquals(ClinicalDataJobService.STATUS_SYNCED, job.getJobStatus());
        assertEquals("medplum_replayed", job.getLastStage());
        assertEquals(1L, job.getRunAttempt());
    }

    @Test
    void shouldListFailedJobs() {
        AtomicReference<ClinicalDataJob> jobRef = new AtomicReference<>(ClinicalDataJob.builder()
                .id(21L)
                .jobType(ClinicalDataJobService.JOB_TYPE_PAPER_CASE)
                .jobStatus(ClinicalDataJobService.STATUS_FAILED)
                .patientId(12L)
                .sourceKind(ClinicalDataJobService.SOURCE_KIND_PAPER_CASE_RECORD)
                .sourceRefId(66L)
                .build());

        ClinicalDataJobReplayService service = new ClinicalDataJobReplayService(
                new ReplayStubClinicalDataJobService(jobRef),
                new ReplayStubMedplumAutoSyncService(new AtomicReference<>()),
                createProxy(PaperCaseRecordRepository.class, Map.of(
                        "findById", args -> Optional.empty()
                ))
        );

        List<ClinicalDataJobDTO> jobs = service.listFailedJobs();

        assertEquals(1, jobs.size());
        assertEquals(21L, jobs.get(0).getId());
        assertEquals(ClinicalDataJobService.STATUS_FAILED, jobs.get(0).getJobStatus());
    }

    @Test
    void shouldReplayMedplumSyncAndMarkJobSyncedWhenSkipped() {
        AtomicReference<ClinicalDataJob> jobRef = new AtomicReference<>(ClinicalDataJob.builder()
                .id(22L)
                .jobType(ClinicalDataJobService.JOB_TYPE_HUAWEI_IMPORT)
                .jobStatus(ClinicalDataJobService.STATUS_FAILED)
                .patientId(18L)
                .sourceKind(ClinicalDataJobService.SOURCE_KIND_DEVICE_IMPORT)
                .lastStage("medplum_sync_failed")
                .build());

        ClinicalDataJobReplayService service = new ClinicalDataJobReplayService(
                new ReplayStubClinicalDataJobService(jobRef),
                new ReplaySkippingMedplumAutoSyncService(),
                createProxy(PaperCaseRecordRepository.class, Map.of(
                        "findById", args -> Optional.empty()
                ))
        );

        Map<String, Object> result = service.replayMedplumSync(22L);
        ClinicalDataJobDTO job = (ClinicalDataJobDTO) result.get("job");

        assertTrue((Boolean) result.get("success"));
        assertEquals("skipped", result.get("medplumSyncStatus"));
        assertEquals(ClinicalDataJobService.STATUS_SYNCED, job.getJobStatus());
        assertEquals("medplum_replay_skipped", job.getLastStage());
    }

    @Test
    void shouldRejectReplayWhenJobStatusIsNotFailedOrDeadLetter() {
        AtomicReference<ClinicalDataJob> jobRef = new AtomicReference<>(ClinicalDataJob.builder()
                .id(31L)
                .jobType(ClinicalDataJobService.JOB_TYPE_PAPER_CASE)
                .jobStatus(ClinicalDataJobService.STATUS_RUNNING)
                .patientId(12L)
                .sourceKind(ClinicalDataJobService.SOURCE_KIND_PAPER_CASE_RECORD)
                .sourceRefId(66L)
                .build());

        ClinicalDataJobReplayService service = new ClinicalDataJobReplayService(
                new ReplayStubClinicalDataJobService(jobRef),
                new ReplayStubMedplumAutoSyncService(new AtomicReference<>()),
                createProxy(PaperCaseRecordRepository.class, Map.of(
                        "findById", args -> Optional.empty()
                ))
        );

        BusinessException ex = assertThrows(BusinessException.class, () -> service.replayMedplumSync(31L));

        assertEquals(409, ex.getCode());
    }

    @Test
    void shouldListReplayableJobsFromFailedAndDeadLetterStatuses() {
        ReplayListingClinicalDataJobService jobService = new ReplayListingClinicalDataJobService(
                List.of(
                        ClinicalDataJobDTO.builder().id(41L).jobStatus(ClinicalDataJobService.STATUS_FAILED).sourceKind(ClinicalDataJobService.SOURCE_KIND_PAPER_CASE_RECORD).createdAt("2026-05-29T20:00:00").build(),
                        ClinicalDataJobDTO.builder().id(42L).jobStatus(ClinicalDataJobService.STATUS_FAILED).sourceKind("manual_upload").createdAt("2026-05-29T19:00:00").build()
                ),
                List.of(
                        ClinicalDataJobDTO.builder().id(43L).jobStatus(ClinicalDataJobService.STATUS_DEAD_LETTER).sourceKind(ClinicalDataJobService.SOURCE_KIND_DEVICE_IMPORT).createdAt("2026-05-29T21:00:00").build()
                )
        );

        ClinicalDataJobReplayService service = new ClinicalDataJobReplayService(
                jobService,
                new ReplayStubMedplumAutoSyncService(new AtomicReference<>()),
                createProxy(PaperCaseRecordRepository.class, Map.of(
                        "findById", args -> Optional.empty()
                ))
        );

        List<ClinicalDataJobDTO> jobs = service.listReplayableJobs();

        assertEquals(List.of(43L, 41L), jobs.stream().map(ClinicalDataJobDTO::getId).toList());
    }

    @Test
    void shouldReplayReplayableJobsInBatch() {
        ReplayBatchClinicalDataJobReplayService service = new ReplayBatchClinicalDataJobReplayService();

        Map<String, Object> result = service.replayReplayableJobs(10);

        assertEquals(2, result.get("requestedJobs"));
        assertEquals(1, result.get("replayedJobs"));
        assertEquals(1, result.get("failedJobs"));
    }

    @Test
    void shouldListDoctorScopedReplayableJobs() {
        ReplayListingClinicalDataJobService jobService = new ReplayListingClinicalDataJobService(
                List.of(
                        ClinicalDataJobDTO.builder().id(41L).patientId(12L).jobStatus(ClinicalDataJobService.STATUS_FAILED).sourceKind(ClinicalDataJobService.SOURCE_KIND_PAPER_CASE_RECORD).createdAt("2026-05-29T20:00:00").build(),
                        ClinicalDataJobDTO.builder().id(42L).patientId(88L).jobStatus(ClinicalDataJobService.STATUS_FAILED).sourceKind(ClinicalDataJobService.SOURCE_KIND_PAPER_CASE_RECORD).createdAt("2026-05-29T19:00:00").build()
                ),
                List.of(
                        ClinicalDataJobDTO.builder().id(43L).patientId(12L).jobStatus(ClinicalDataJobService.STATUS_DEAD_LETTER).sourceKind(ClinicalDataJobService.SOURCE_KIND_DEVICE_IMPORT).createdAt("2026-05-29T21:00:00").build()
                )
        );

        ClinicalDataJobReplayService service = new ClinicalDataJobReplayService(
                jobService,
                new ReplayStubMedplumAutoSyncService(new AtomicReference<>()),
                createProxy(PaperCaseRecordRepository.class, Map.of(
                        "findById", args -> Optional.empty()
                )),
                patientRepositoryFor(Map.of(7L, List.of(12L)))
        );

        List<ClinicalDataJobDTO> jobs = service.listReplayableJobsForDoctor(7L);

        assertEquals(List.of(43L, 41L), jobs.stream().map(ClinicalDataJobDTO::getId).toList());
    }

    @Test
    void shouldRejectDoctorReplayForUnownedJob() {
        AtomicReference<ClinicalDataJob> jobRef = new AtomicReference<>(ClinicalDataJob.builder()
                .id(91L)
                .jobType(ClinicalDataJobService.JOB_TYPE_PAPER_CASE)
                .jobStatus(ClinicalDataJobService.STATUS_FAILED)
                .patientId(88L)
                .sourceKind(ClinicalDataJobService.SOURCE_KIND_PAPER_CASE_RECORD)
                .sourceRefId(166L)
                .build());

        ClinicalDataJobReplayService service = new ClinicalDataJobReplayService(
                new ReplayStubClinicalDataJobService(jobRef),
                new ReplayStubMedplumAutoSyncService(new AtomicReference<>()),
                createProxy(PaperCaseRecordRepository.class, Map.of(
                        "findById", args -> Optional.empty()
                )),
                patientRepositoryFor(Map.of(7L, List.of(12L)))
        );

        BusinessException ex = assertThrows(BusinessException.class, () -> service.replayMedplumSyncForDoctor(7L, 91L));

        assertEquals(403, ex.getCode());
    }

    private static final class ReplayStubClinicalDataJobService extends ClinicalDataJobService {
        private final AtomicReference<ClinicalDataJob> jobRef;

        private ReplayStubClinicalDataJobService(AtomicReference<ClinicalDataJob> jobRef) {
            super(null);
            this.jobRef = jobRef;
        }

        @Override
        public ClinicalDataJob getRequired(Long jobId) {
            return jobRef.get();
        }

        @Override
        public ClinicalDataJob claimForExecution(Long jobId, String stage) {
            ClinicalDataJob job = jobRef.get();
            job.setJobStatus(STATUS_RUNNING);
            job.setRunAttempt((job.getRunAttempt() == null ? 0L : job.getRunAttempt()) + 1L);
            job.setLastStage(stage);
            job.setStartedAt(LocalDateTime.now());
            return job;
        }

        @Override
        public ClinicalDataJob markStarted(Long jobId, Long runAttempt, String stage) {
            ClinicalDataJob job = jobRef.get();
            job.setRunAttempt(runAttempt);
            job.setStartedAt(LocalDateTime.now());
            job.setLastStage(stage);
            return job;
        }

        @Override
        public ClinicalDataJob markStage(Long jobId, Long runAttempt, String status, String stage, Map<String, Object> result) {
            ClinicalDataJob job = jobRef.get();
            job.setRunAttempt(runAttempt);
            job.setJobStatus(status);
            job.setLastStage(stage);
            job.setUpdatedAt(LocalDateTime.now());
            return job;
        }

        @Override
        public ClinicalDataJob markFailure(Long jobId, Long runAttempt, String stage, String message) {
            ClinicalDataJob job = jobRef.get();
            job.setRunAttempt(runAttempt);
            job.setJobStatus(STATUS_FAILED);
            job.setLastStage(stage);
            job.setLastError(message);
            return job;
        }

        @Override
        public List<ClinicalDataJobDTO> getByStatus(String jobStatus) {
            ClinicalDataJob job = jobRef.get();
            if (job == null || !jobStatus.equals(job.getJobStatus())) {
                return List.of();
            }
            return List.of(toDTO(job));
        }

        @Override
        public ClinicalDataJobDTO toDTO(ClinicalDataJob job) {
            return ClinicalDataJobDTO.builder()
                    .id(job.getId())
                    .jobType(job.getJobType())
                    .jobStatus(job.getJobStatus())
                    .runAttempt(job.getRunAttempt())
                    .sourceKind(job.getSourceKind())
                    .sourceRefId(job.getSourceRefId())
                    .lastStage(job.getLastStage())
                    .lastError(job.getLastError())
                    .build();
        }
    }

    private static final class ReplayStubMedplumAutoSyncService extends MedplumAutoSyncService {
        private final AtomicReference<PaperCaseRecord> recordRef;

        private ReplayStubMedplumAutoSyncService(AtomicReference<PaperCaseRecord> recordRef) {
            super(null, null);
            this.recordRef = recordRef;
        }

        @Override
        public PaperCaseRecord syncPaperCaseRecord(PaperCaseRecord record) {
            record.setMedplumSyncStatus("synced");
            record.setMedplumSyncMessage("replayed");
            record.setMedplumSyncedAt(LocalDateTime.now());
            recordRef.set(record);
            return record;
        }

        @Override
        public MedplumSyncOutcome replayPatientSync(Long patientId, boolean includeObservations) {
            return new MedplumSyncOutcome("synced", "replayed", LocalDateTime.now());
        }
    }

    private static final class ReplaySkippingMedplumAutoSyncService extends MedplumAutoSyncService {
        private ReplaySkippingMedplumAutoSyncService() {
            super(null, null);
        }

        @Override
        public MedplumSyncOutcome replayPatientSync(Long patientId, boolean includeObservations) {
            return new MedplumSyncOutcome("skipped", "disabled", null);
        }
    }

    private static final class ReplayListingClinicalDataJobService extends ClinicalDataJobService {
        private final List<ClinicalDataJobDTO> failedJobs;
        private final List<ClinicalDataJobDTO> deadLetterJobs;

        private ReplayListingClinicalDataJobService(List<ClinicalDataJobDTO> failedJobs,
                                                    List<ClinicalDataJobDTO> deadLetterJobs) {
            super(null);
            this.failedJobs = failedJobs;
            this.deadLetterJobs = deadLetterJobs;
        }

        @Override
        public List<ClinicalDataJobDTO> getByStatus(String jobStatus) {
            if (STATUS_FAILED.equals(jobStatus)) {
                return failedJobs;
            }
            if (STATUS_DEAD_LETTER.equals(jobStatus)) {
                return deadLetterJobs;
            }
            return List.of();
        }

        @Override
        public List<ClinicalDataJobDTO> getByPatientIdsAndStatus(List<Long> patientIds, String jobStatus) {
            if (patientIds == null || patientIds.isEmpty()) {
                return List.of();
            }
            return getByStatus(jobStatus).stream()
                    .filter(job -> job.getPatientId() != null && patientIds.contains(job.getPatientId()))
                    .toList();
        }
    }

    private static final class ReplayBatchClinicalDataJobReplayService extends ClinicalDataJobReplayService {
        private ReplayBatchClinicalDataJobReplayService() {
            super(null, null, null);
        }

        @Override
        public List<ClinicalDataJobDTO> listReplayableJobs() {
            return List.of(
                    ClinicalDataJobDTO.builder().id(51L).build(),
                    ClinicalDataJobDTO.builder().id(52L).build()
            );
        }

        @Override
        public Map<String, Object> replayMedplumSync(Long jobId) {
            if (jobId == 51L) {
                return new LinkedHashMap<>(Map.of(
                        "success", true,
                        "jobId", jobId,
                        "medplumSyncStatus", "synced",
                        "message", "ok"
                ));
            }
            throw new BusinessException(409, "conflict");
        }
    }

    private PatientRepository patientRepositoryFor(Map<Long, List<Long>> assignments) {
        return createProxy(PatientRepository.class, Map.of(
                "findByAssignedDoctorId", args -> assignments.getOrDefault((Long) args[0], List.of()).stream()
                        .map(patientId -> Patient.builder().id(patientId).assignedDoctorId((Long) args[0]).build())
                        .toList()
        ));
    }

    private <T> T createProxy(Class<T> type, Map<String, Function<Object[], Object>> handlers) {
        Object proxy = java.lang.reflect.Proxy.newProxyInstance(
                type.getClassLoader(),
                new Class<?>[]{type},
                (ignored, method, args) -> {
                    Function<Object[], Object> handler = handlers.get(method.getName());
                    if (handler != null) {
                        return handler.apply(args == null ? new Object[0] : args);
                    }
                    if ("toString".equals(method.getName())) {
                        return type.getSimpleName() + "Proxy";
                    }
                    if ("hashCode".equals(method.getName())) {
                        return System.identityHashCode(this);
                    }
                    if ("equals".equals(method.getName())) {
                        return false;
                    }
                    return null;
                }
        );
        return type.cast(proxy);
    }
}
