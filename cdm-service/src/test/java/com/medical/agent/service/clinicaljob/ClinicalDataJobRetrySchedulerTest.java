package com.medical.agent.service.clinicaljob;

import com.medical.agent.common.dto.DeviceImportSummaryDTO;
import com.medical.agent.model.entity.ClinicalDataJob;
import com.medical.agent.model.entity.PaperCaseRecord;
import com.medical.agent.model.entity.Patient;
import com.medical.agent.model.repository.PaperCaseRecordRepository;
import com.medical.agent.model.repository.PatientRepository;
import com.medical.agent.service.PaperCaseService;
import com.medical.agent.service.device.DeviceJsonImportService;
import com.medical.agent.service.testsupport.RepositoryProxyFactory;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ClinicalDataJobRetrySchedulerTest {

    @Test
    void shouldResubmitPaperCaseAndDeviceJobsWhenRetryIsDue() {
        Map<Long, ClinicalDataJob> jobs = new LinkedHashMap<>();
        jobs.put(1L, ClinicalDataJob.builder()
                .id(1L)
                .jobType(ClinicalDataJobService.JOB_TYPE_PAPER_CASE)
                .jobStatus(ClinicalDataJobService.STATUS_FAILED)
                .patientId(10L)
                .sourceRefId(100L)
                .payloadJson("{\"sourcePath\":\"C:/tmp/case.jpg\",\"contentType\":\"image/jpeg\"}")
                .nextRetryAt(LocalDateTime.now().minusMinutes(1))
                .retryCount(1)
                .maxRetries(3)
                .build());
        jobs.put(2L, ClinicalDataJob.builder()
                .id(2L)
                .jobType(ClinicalDataJobService.JOB_TYPE_HUAWEI_IMPORT)
                .jobStatus(ClinicalDataJobService.STATUS_FAILED)
                .patientId(20L)
                .payloadJson("{\"payload\":\"{\\\"services\\\":[]}\",\"vendor\":\"huawei\"}")
                .nextRetryAt(LocalDateTime.now().minusMinutes(1))
                .retryCount(1)
                .maxRetries(3)
                .build());

        AtomicReference<Long> paperCaseJob = new AtomicReference<>();
        AtomicReference<Path> paperCasePath = new AtomicReference<>();
        AtomicReference<String> paperCaseContentType = new AtomicReference<>();
        AtomicReference<Long> deviceJob = new AtomicReference<>();
        AtomicReference<String> devicePayload = new AtomicReference<>();
        AtomicReference<String> deviceVendor = new AtomicReference<>();

        ClinicalDataJobService service = new StubClinicalDataJobService(jobs);
        ClinicalDataJobOrchestrator orchestrator = ClinicalDataJobOrchestrator.withoutClaim(Runnable::run);
        PaperCaseService paperCaseService = new StubPaperCaseService(paperCaseJob, paperCasePath, paperCaseContentType);
        DeviceJsonImportService deviceJsonImportService = new StubDeviceJsonImportService(deviceJob, devicePayload, deviceVendor);
        PatientRepository patientRepository = RepositoryProxyFactory.create(PatientRepository.class, Map.of(
                "findById", args -> Optional.of(Patient.builder().id(10L).name("李某").build())
        ));
        PaperCaseRecordRepository recordRepository = RepositoryProxyFactory.create(PaperCaseRecordRepository.class, Map.of(
                "findById", args -> Optional.of(PaperCaseRecord.builder().id(100L).patientId(10L).build())
        ));

        ClinicalDataJobRetryScheduler scheduler = new ClinicalDataJobRetryScheduler(
                service,
                orchestrator,
                paperCaseService,
                deviceJsonImportService,
                patientRepository,
                recordRepository
        );

        scheduler.scheduleRetries();

        assertEquals(ClinicalDataJobService.STATUS_RECEIVED, jobs.get(1L).getJobStatus());
        assertEquals("retry_scheduled", jobs.get(1L).getLastStage());
        assertNotNull(paperCaseJob.get());
        assertEquals(1L, paperCaseJob.get());
        assertEquals("image/jpeg", paperCaseContentType.get());
        assertEquals(Path.of("C:/tmp/case.jpg"), paperCasePath.get());

        assertEquals(ClinicalDataJobService.STATUS_RECEIVED, jobs.get(2L).getJobStatus());
        assertEquals("retry_scheduled", jobs.get(2L).getLastStage());
        assertEquals(2L, deviceJob.get());
        assertEquals("{\"services\":[]}", devicePayload.get());
        assertEquals("huawei", deviceVendor.get());
    }

    @Test
    void shouldSkipCancelledJobDuringManualResubmit() {
        ClinicalDataJob cancelled = ClinicalDataJob.builder()
                .id(88L)
                .jobStatus(ClinicalDataJobService.STATUS_CANCELLED)
                .build();

        ClinicalDataJobRetryScheduler scheduler = new ClinicalDataJobRetryScheduler(
                new StubClinicalDataJobService(new LinkedHashMap<>()),
                ClinicalDataJobOrchestrator.withoutClaim(Runnable::run),
                null,
                null,
                null,
                null
        ) {
            @Override
            public void resubmit(ClinicalDataJob job) {
                super.resubmit(job);
            }
        };

        scheduler.resubmit(cancelled);

        assertEquals(ClinicalDataJobService.STATUS_CANCELLED, cancelled.getJobStatus());
    }

    @Test
    void shouldRecoverTimedOutRunningJobBeforeRetryDispatch() {
        ClinicalDataJob runningJob = ClinicalDataJob.builder()
                .id(3L)
                .jobType(ClinicalDataJobService.JOB_TYPE_PAPER_CASE)
                .jobStatus(ClinicalDataJobService.STATUS_RUNNING)
                .patientId(30L)
                .sourceRefId(300L)
                .payloadJson("{\"sourcePath\":\"C:/tmp/case-timeout.jpg\",\"contentType\":\"image/jpeg\"}")
                .startedAt(LocalDateTime.now().minusMinutes(10))
                .retryCount(0)
                .maxRetries(3)
                .build();

        Map<Long, ClinicalDataJob> jobs = new LinkedHashMap<>();
        jobs.put(3L, runningJob);

        ClinicalDataJobService service = new StubClinicalDataJobService(jobs);
        ClinicalDataJobOrchestrator orchestrator = ClinicalDataJobOrchestrator.withoutClaim(Runnable::run);
        PaperCaseService paperCaseService = new StubPaperCaseService(new AtomicReference<>(), new AtomicReference<>(), new AtomicReference<>());
        DeviceJsonImportService deviceJsonImportService = new StubDeviceJsonImportService(new AtomicReference<>(), new AtomicReference<>(), new AtomicReference<>());

        ClinicalDataJobRetryScheduler scheduler = new ClinicalDataJobRetryScheduler(
                service,
                orchestrator,
                paperCaseService,
                deviceJsonImportService,
                null,
                null
        );
        ReflectionTestUtils.setField(scheduler, "runningTimeoutMs", 60_000L);

        scheduler.recoverTimedOutRunningJobs();

        assertEquals(ClinicalDataJobService.STATUS_FAILED, runningJob.getJobStatus());
        assertEquals("running_timeout", runningJob.getLastStage());
        assertEquals(1, runningJob.getRetryCount());
        assertNotNull(runningJob.getNextRetryAt());
    }

    private static final class StubClinicalDataJobService extends ClinicalDataJobService {
        private final Map<Long, ClinicalDataJob> jobs;

        private StubClinicalDataJobService(Map<Long, ClinicalDataJob> jobs) {
            super(null);
            this.jobs = jobs;
        }

        @Override
        public List<ClinicalDataJob> getRetryableJobsDue(LocalDateTime now) {
            return jobs.values().stream()
                    .filter(job -> ClinicalDataJobService.STATUS_FAILED.equals(job.getJobStatus()))
                    .filter(job -> job.getNextRetryAt() != null && !job.getNextRetryAt().isAfter(now))
                    .toList();
        }

        @Override
        public List<ClinicalDataJob> getRunningJobsTimedOut(LocalDateTime startedBefore) {
            return jobs.values().stream()
                    .filter(job -> ClinicalDataJobService.STATUS_RUNNING.equals(job.getJobStatus()))
                    .filter(job -> job.getStartedAt() != null && !job.getStartedAt().isAfter(startedBefore))
                    .toList();
        }

        @Override
        public ClinicalDataJob markRetryScheduled(Long jobId, String stage) {
            ClinicalDataJob job = jobs.get(jobId);
            job.setJobStatus(STATUS_RECEIVED);
            job.setLastStage(stage);
            job.setNextRetryAt(null);
            job.setLastError(null);
            return job;
        }

        @Override
        public ClinicalDataJob markFailure(Long jobId, String stage, String message) {
            ClinicalDataJob job = jobs.get(jobId);
            job.setJobStatus(STATUS_FAILED);
            job.setLastStage(stage);
            job.setLastError(message);
            job.setRetryCount((job.getRetryCount() == null ? 0 : job.getRetryCount()) + 1);
            job.setNextRetryAt(LocalDateTime.now());
            return job;
        }
    }

    private static final class StubPaperCaseService extends PaperCaseService {
        private final AtomicReference<Long> jobRef;
        private final AtomicReference<Path> pathRef;
        private final AtomicReference<String> contentTypeRef;

        private StubPaperCaseService(AtomicReference<Long> jobRef,
                                     AtomicReference<Path> pathRef,
                                     AtomicReference<String> contentTypeRef) {
            super(null, null, null, null, null, null, null, null, null, null, null);
            this.jobRef = jobRef;
            this.pathRef = pathRef;
            this.contentTypeRef = contentTypeRef;
        }

        @Override
        public void processPaperCaseJob(Long jobId, Long runAttempt, Patient patient, Long paperCaseRecordId, Path target, String contentType) {
            jobRef.set(jobId);
            pathRef.set(target);
            contentTypeRef.set(contentType);
        }
    }

    private static final class StubDeviceJsonImportService extends DeviceJsonImportService {
        private final AtomicReference<Long> jobRef;
        private final AtomicReference<String> payloadRef;
        private final AtomicReference<String> vendorRef;

        private StubDeviceJsonImportService(AtomicReference<Long> jobRef,
                                            AtomicReference<String> payloadRef,
                                            AtomicReference<String> vendorRef) {
            super(null, null, null, null, null, null, null, null);
            this.jobRef = jobRef;
            this.payloadRef = payloadRef;
            this.vendorRef = vendorRef;
        }

        @Override
        public DeviceImportSummaryDTO processImportJob(Long jobId, Long runAttempt, Long patientId, String rawJson, String preferredVendor) {
            jobRef.set(jobId);
            payloadRef.set(rawJson);
            vendorRef.set(preferredVendor);
            return DeviceImportSummaryDTO.builder().patientId(patientId).vendor(preferredVendor).build();
        }
    }
}
