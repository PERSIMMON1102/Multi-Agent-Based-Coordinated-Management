package com.medical.agent.service.clinicaljob;

import com.medical.agent.common.dto.ClinicalDataJobDTO;
import com.medical.agent.common.dto.NotificationDTO;
import com.medical.agent.common.exception.BusinessException;
import com.medical.agent.model.entity.ClinicalDataJob;
import com.medical.agent.model.entity.Patient;
import com.medical.agent.model.repository.ClinicalDataJobRepository;
import com.medical.agent.model.repository.PatientRepository;
import com.medical.agent.service.ClinicalNotificationGateway;
import com.medical.agent.service.PlatformMetricsService;
import com.medical.agent.service.testsupport.RepositoryProxyFactory;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.ArrayList;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ClinicalDataJobServiceTest {

    @Test
    void shouldScheduleRetryBeforeDeadLetter() {
        Map<Long, ClinicalDataJob> jobs = new LinkedHashMap<>();
        ClinicalDataJob job = ClinicalDataJob.builder()
                .id(1L)
                .jobType(ClinicalDataJobService.JOB_TYPE_PAPER_CASE)
                .jobStatus(ClinicalDataJobService.STATUS_RECEIVED)
                .patientId(10L)
                .retryCount(0)
                .maxRetries(3)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        jobs.put(job.getId(), job);

        ClinicalDataJobRepository repository = repository(jobs);
        ClinicalDataJobService service = new ClinicalDataJobService(repository, metrics());

        ClinicalDataJob failed = service.markFailure(1L, "medplum_sync_failed", "network");

        assertEquals(ClinicalDataJobService.STATUS_FAILED, failed.getJobStatus());
        assertEquals(1, failed.getRetryCount());
        assertNotNull(failed.getNextRetryAt());
        assertNotNull(failed.getFirstFailedAt());
        assertTrue(failed.getNextRetryAt().isAfter(LocalDateTime.now().minusSeconds(5)));
    }

    @Test
    void shouldMoveToDeadLetterAfterMaxRetries() {
        Map<Long, ClinicalDataJob> jobs = new LinkedHashMap<>();
        ClinicalDataJob job = ClinicalDataJob.builder()
                .id(2L)
                .jobType(ClinicalDataJobService.JOB_TYPE_HUAWEI_IMPORT)
                .jobStatus(ClinicalDataJobService.STATUS_FAILED)
                .patientId(20L)
                .retryCount(2)
                .maxRetries(3)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        jobs.put(job.getId(), job);

        ClinicalDataJobRepository repository = repository(jobs);
        ClinicalDataJobService service = new ClinicalDataJobService(repository, metrics());

        ClinicalDataJob dead = service.markFailure(2L, "mapping_failed", "no metrics");

        assertEquals(ClinicalDataJobService.STATUS_DEAD_LETTER, dead.getJobStatus());
        assertEquals(3, dead.getRetryCount());
        assertNull(dead.getNextRetryAt());
    }

    @Test
    void shouldEmitDeadLetterAlertOnlyWhenJobFirstEntersDeadLetter() {
        Map<Long, ClinicalDataJob> jobs = new LinkedHashMap<>();
        ClinicalDataJob job = ClinicalDataJob.builder()
                .id(3L)
                .jobType(ClinicalDataJobService.JOB_TYPE_PAPER_CASE)
                .jobStatus(ClinicalDataJobService.STATUS_FAILED)
                .patientId(20L)
                .retryCount(2)
                .maxRetries(3)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        jobs.put(job.getId(), job);

        List<NotificationDTO> notifications = new ArrayList<>();
        ClinicalDataJobAlertService alertService = new ClinicalDataJobAlertService(
                new CapturingClinicalNotificationGateway(notifications),
                patientRepository("李四")
        );
        ClinicalDataJobService service = new ClinicalDataJobService(repository(jobs), metrics(), alertService);

        service.markFailure(3L, "medplum_sync_failed", "network timeout");
        service.markFailure(3L, "medplum_sync_failed", "network timeout again");

        assertEquals(1, notifications.size());
        assertEquals("CLINICAL_JOB_DEAD_LETTER", notifications.get(0).getType());
    }

    @Test
    void shouldReturnRetryableJobsDue() {
        LocalDateTime now = LocalDateTime.now();
        Map<Long, ClinicalDataJob> jobs = new LinkedHashMap<>();
        jobs.put(1L, ClinicalDataJob.builder()
                .id(1L)
                .jobStatus(ClinicalDataJobService.STATUS_FAILED)
                .patientId(10L)
                .nextRetryAt(now.minusMinutes(1))
                .build());
        jobs.put(2L, ClinicalDataJob.builder()
                .id(2L)
                .jobStatus(ClinicalDataJobService.STATUS_FAILED)
                .patientId(11L)
                .nextRetryAt(now.plusMinutes(10))
                .build());

        ClinicalDataJobService service = new ClinicalDataJobService(repository(jobs), metrics());
        List<ClinicalDataJob> due = service.getRetryableJobsDue(now);

        assertEquals(1, due.size());
        assertEquals(1L, due.get(0).getId());
    }

    @Test
    void shouldPersistAndQueryCorrelationId() {
        Map<Long, ClinicalDataJob> jobs = new LinkedHashMap<>();
        ClinicalDataJobRepository repository = repository(jobs);
        ClinicalDataJobService service = new ClinicalDataJobService(repository, metrics());

        ClinicalDataJob created = service.createJob(
                ClinicalDataJobService.JOB_TYPE_PAPER_CASE,
                33L,
                "paper-case-33-abc",
                "idem-paper-case-33-abc",
                ClinicalDataJobService.SOURCE_KIND_PAPER_CASE_RECORD,
                9L,
                "case.pdf",
                "/api/paper-cases/9/file",
                null,
                Map.of("foo", "bar")
        );

        assertEquals("paper-case-33-abc", created.getCorrelationId());
        ClinicalDataJobDTO dto = service.toDTO(created);
        assertEquals(33L, dto.getPatientId());
        assertEquals("paper-case-33-abc", dto.getCorrelationId());
        List<ClinicalDataJobDTO> matches = service.getByCorrelationId("paper-case-33-abc");
        assertEquals(1, matches.size());
        assertEquals(created.getId(), matches.get(0).getId());
        assertEquals(33L, matches.get(0).getPatientId());
        assertEquals("idem-paper-case-33-abc", created.getIdempotencyKey());
        assertEquals("idem-paper-case-33-abc", service.findLatestByIdempotencyKey("idem-paper-case-33-abc").getIdempotencyKey());
    }

    @Test
    void shouldReuseExistingJobForSameIdempotencyKey() {
        Map<Long, ClinicalDataJob> jobs = new LinkedHashMap<>();
        ClinicalDataJobService service = new ClinicalDataJobService(repository(jobs), metrics());

        ClinicalDataJobService.JobCreationResult first = service.createOrReuseJob(
                ClinicalDataJobService.JOB_TYPE_HUAWEI_IMPORT,
                88L,
                "device-import-88-first",
                "idem-device-88",
                ClinicalDataJobService.SOURCE_KIND_DEVICE_IMPORT,
                null,
                "huawei.json",
                null,
                "huawei",
                Map.of("payload", "{}")
        );
        ClinicalDataJobService.JobCreationResult second = service.createOrReuseJob(
                ClinicalDataJobService.JOB_TYPE_HUAWEI_IMPORT,
                88L,
                "device-import-88-second",
                "idem-device-88",
                ClinicalDataJobService.SOURCE_KIND_DEVICE_IMPORT,
                null,
                "huawei.json",
                null,
                "huawei",
                Map.of("payload", "{}")
        );

        assertTrue(first.created());
        assertEquals(false, second.created());
        assertEquals(first.job().getId(), second.job().getId());
        assertEquals(1, jobs.size());
    }

    @Test
    void shouldReuseExistingJobWhenConcurrentSaveHitsUniqueConstraint() {
        Map<Long, ClinicalDataJob> jobs = new LinkedHashMap<>();
        AtomicBoolean firstLookup = new AtomicBoolean(true);
        ClinicalDataJob existing = ClinicalDataJob.builder()
                .id(91L)
                .jobType(ClinicalDataJobService.JOB_TYPE_PAPER_CASE)
                .jobStatus(ClinicalDataJobService.STATUS_RECEIVED)
                .patientId(66L)
                .correlationId("paper-case-66-existing")
                .idempotencyKey("idem-paper-case-race")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        ClinicalDataJobRepository repository = RepositoryProxyFactory.create(ClinicalDataJobRepository.class, Map.of(
                "findFirstByIdempotencyKeyOrderByCreatedAtDesc", args -> {
                    if (firstLookup.getAndSet(false)) {
                        return Optional.empty();
                    }
                    return Optional.of(existing);
                },
                "save", args -> {
                    throw new DataIntegrityViolationException("duplicate idempotency key");
                },
                "countByJobStatus", args -> 0L
        ));
        ClinicalDataJobService service = new ClinicalDataJobService(repository, metrics());

        ClinicalDataJobService.JobCreationResult result = service.createOrReuseJob(
                ClinicalDataJobService.JOB_TYPE_PAPER_CASE,
                66L,
                "paper-case-66-race",
                "idem-paper-case-race",
                ClinicalDataJobService.SOURCE_KIND_PAPER_CASE_RECORD,
                9L,
                "case.pdf",
                "/api/paper-cases/9/file",
                null,
                Map.of("foo", "bar")
        );

        assertEquals(false, result.created());
        assertEquals(91L, result.job().getId());
    }

    @Test
    void shouldClaimJobForExecutionAndPreventDuplicateRunningClaim() {
        Map<Long, ClinicalDataJob> jobs = new LinkedHashMap<>();
        ClinicalDataJob job = ClinicalDataJob.builder()
                .id(9L)
                .jobType(ClinicalDataJobService.JOB_TYPE_PAPER_CASE)
                .jobStatus(ClinicalDataJobService.STATUS_RECEIVED)
                .patientId(100L)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        jobs.put(job.getId(), job);

        ClinicalDataJobService service = new ClinicalDataJobService(repository(jobs), metrics());

        ClinicalDataJob running = service.claimForExecution(9L, "paper_case_async_dispatch");

        assertEquals(ClinicalDataJobService.STATUS_RUNNING, running.getJobStatus());
        assertEquals("paper_case_async_dispatch", running.getLastStage());
        assertNotNull(running.getStartedAt());
        assertTrue(running.getFinishedAt() == null);
    }

    @Test
    void shouldCancelJobAndBlockFurtherRetryScheduling() {
        Map<Long, ClinicalDataJob> jobs = new LinkedHashMap<>();
        ClinicalDataJob job = ClinicalDataJob.builder()
                .id(10L)
                .jobType(ClinicalDataJobService.JOB_TYPE_HUAWEI_IMPORT)
                .jobStatus(ClinicalDataJobService.STATUS_FAILED)
                .patientId(100L)
                .retryCount(1)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        jobs.put(job.getId(), job);

        ClinicalDataJobService service = new ClinicalDataJobService(repository(jobs), metrics());

        ClinicalDataJob cancelled = service.cancelJob(10L, "manual_cancel", "operator requested stop");
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.markRetryScheduled(10L, "retry_scheduled"));

        assertEquals(ClinicalDataJobService.STATUS_CANCELLED, cancelled.getJobStatus());
        assertEquals("manual_cancel", cancelled.getLastStage());
        assertEquals("operator requested stop", cancelled.getLastError());
        assertNotNull(cancelled.getFinishedAt());
        assertEquals(409, ex.getCode());
    }

    @Test
    void shouldRejectManualRequeueForNonRetryableStatus() {
        Map<Long, ClinicalDataJob> jobs = new LinkedHashMap<>();
        jobs.put(22L, ClinicalDataJob.builder()
                .id(22L)
                .jobType(ClinicalDataJobService.JOB_TYPE_PAPER_CASE)
                .jobStatus(ClinicalDataJobService.STATUS_REVIEW_PENDING)
                .patientId(7L)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build());

        ClinicalDataJobService service = new ClinicalDataJobService(repository(jobs), metrics());
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.markRetryScheduled(22L, "manual_requeue"));

        assertEquals(409, ex.getCode());
    }

    @Test
    void shouldRejectCancelForTerminalStatus() {
        Map<Long, ClinicalDataJob> jobs = new LinkedHashMap<>();
        jobs.put(23L, ClinicalDataJob.builder()
                .id(23L)
                .jobType(ClinicalDataJobService.JOB_TYPE_HUAWEI_IMPORT)
                .jobStatus(ClinicalDataJobService.STATUS_SYNCED)
                .patientId(8L)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build());

        ClinicalDataJobService service = new ClinicalDataJobService(repository(jobs), metrics());
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.cancelJob(23L, "manual_cancel", "late stop"));

        assertEquals(409, ex.getCode());
    }

    @Test
    void shouldExposeFirstFailedAtAfterRecoveredSync() {
        Map<Long, ClinicalDataJob> jobs = new LinkedHashMap<>();
        ClinicalDataJob job = ClinicalDataJob.builder()
                .id(24L)
                .jobType(ClinicalDataJobService.JOB_TYPE_PAPER_CASE)
                .jobStatus(ClinicalDataJobService.STATUS_FAILED)
                .patientId(9L)
                .runAttempt(1L)
                .firstFailedAt(LocalDateTime.now().minusMinutes(2))
                .createdAt(LocalDateTime.now().minusMinutes(5))
                .updatedAt(LocalDateTime.now())
                .build();
        jobs.put(24L, job);

        ClinicalDataJobService service = new ClinicalDataJobService(repository(jobs), metrics());
        ClinicalDataJob synced = service.markStage(24L, ClinicalDataJobService.STATUS_SYNCED, "medplum_synced", Map.of("ok", true));
        ClinicalDataJobDTO dto = service.toDTO(synced);

        assertEquals(ClinicalDataJobService.STATUS_SYNCED, synced.getJobStatus());
        assertNotNull(dto.getFirstFailedAt());
        assertNotNull(dto.getFinishedAt());
    }

    @Test
    void shouldRejectStaleAttemptStageUpdateAfterJobWasRequeued() {
        Map<Long, ClinicalDataJob> jobs = new LinkedHashMap<>();
        jobs.put(30L, ClinicalDataJob.builder()
                .id(30L)
                .jobType(ClinicalDataJobService.JOB_TYPE_PAPER_CASE)
                .jobStatus(ClinicalDataJobService.STATUS_RECEIVED)
                .patientId(9L)
                .runAttempt(1L)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build());

        ClinicalDataJobService service = new ClinicalDataJobService(repository(jobs), metrics());

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> service.markStage(30L, 1L, ClinicalDataJobService.STATUS_SYNCED, "late_old_worker", Map.of("ok", true)));

        assertTrue(ex.getMessage().contains("no longer active"));
        assertEquals(ClinicalDataJobService.STATUS_RECEIVED, jobs.get(30L).getJobStatus());
    }

    private ClinicalDataJobRepository repository(Map<Long, ClinicalDataJob> jobs) {
        return RepositoryProxyFactory.create(ClinicalDataJobRepository.class, Map.of(
                "findById", args -> Optional.ofNullable(jobs.get((Long) args[0])),
                "save", args -> {
                    ClinicalDataJob saved = (ClinicalDataJob) args[0];
                    if (saved.getId() == null) {
                        saved.setId((long) (jobs.size() + 1));
                    }
                    jobs.put(saved.getId(), saved);
                    return saved;
                },
                "findByCorrelationIdOrderByCreatedAtDesc", args -> jobs.values().stream()
                        .filter(job -> String.valueOf(args[0]).equals(job.getCorrelationId()))
                        .sorted((left, right) -> {
                            LocalDateTime leftTime = left.getCreatedAt() == null ? LocalDateTime.MIN : left.getCreatedAt();
                            LocalDateTime rightTime = right.getCreatedAt() == null ? LocalDateTime.MIN : right.getCreatedAt();
                            return rightTime.compareTo(leftTime);
                        })
                        .toList(),
                "findFirstByIdempotencyKeyOrderByCreatedAtDesc", args -> jobs.values().stream()
                        .filter(job -> String.valueOf(args[0]).equals(job.getIdempotencyKey()))
                        .sorted((left, right) -> {
                            LocalDateTime leftTime = left.getCreatedAt() == null ? LocalDateTime.MIN : left.getCreatedAt();
                            LocalDateTime rightTime = right.getCreatedAt() == null ? LocalDateTime.MIN : right.getCreatedAt();
                            return rightTime.compareTo(leftTime);
                        })
                        .findFirst(),
                "findByJobStatusAndNextRetryAtLessThanEqualOrderByNextRetryAtAsc", args -> jobs.values().stream()
                        .filter(job -> ClinicalDataJobService.STATUS_FAILED.equals(job.getJobStatus()))
                        .filter(job -> job.getNextRetryAt() != null && !job.getNextRetryAt().isAfter((LocalDateTime) args[1]))
                        .sorted((left, right) -> left.getNextRetryAt().compareTo(right.getNextRetryAt()))
                        .toList(),
                "countByJobStatus", args -> jobs.values().stream()
                        .filter(job -> String.valueOf(args[0]).equals(job.getJobStatus()))
                        .count()
        ));
    }

    private PlatformMetricsService metrics() {
        ClinicalDataJobRepository repository = RepositoryProxyFactory.create(ClinicalDataJobRepository.class, Map.of(
                "countByJobStatus", args -> 0L
        ));
        return new PlatformMetricsService(new SimpleMeterRegistry(), repository, null);
    }

    private PatientRepository patientRepository(String patientName) {
        return RepositoryProxyFactory.create(PatientRepository.class, Map.of(
                "findById", args -> Optional.of(Patient.builder()
                        .id((Long) args[0])
                        .name(patientName)
                        .build())
        ));
    }

    private static class CapturingClinicalNotificationGateway implements ClinicalNotificationGateway {

        private final List<NotificationDTO> notifications;

        private CapturingClinicalNotificationGateway(List<NotificationDTO> notifications) {
            this.notifications = notifications;
        }

        @Override
        public void setNotificationConsumer(java.util.function.Consumer<NotificationDTO> consumer) {
        }

        @Override
        public void sendNotification(NotificationDTO notification) {
            notifications.add(notification);
        }
    }
}
