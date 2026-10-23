package com.medical.agent.service.clinicaljob;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.medical.agent.model.entity.ClinicalDataJob;
import com.medical.agent.model.entity.PaperCaseRecord;
import com.medical.agent.model.entity.Patient;
import com.medical.agent.model.repository.PaperCaseRecordRepository;
import com.medical.agent.model.repository.PatientRepository;
import com.medical.agent.service.PaperCaseService;
import com.medical.agent.service.device.DeviceJsonImportService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class ClinicalDataJobRetryScheduler {

    private static final Logger log = LoggerFactory.getLogger(ClinicalDataJobRetryScheduler.class);

    private final ObjectMapper objectMapper = JsonMapper.builder().findAndAddModules().build();
    private final ClinicalDataJobService clinicalDataJobService;
    private final ClinicalDataJobOrchestrator clinicalDataJobOrchestrator;
    private final PaperCaseService paperCaseService;
    private final DeviceJsonImportService deviceJsonImportService;
    private final PatientRepository patientRepository;
    private final PaperCaseRecordRepository paperCaseRecordRepository;

    @Value("${clinical-data-job.running-timeout-ms:1800000}")
    private long runningTimeoutMs;

    public ClinicalDataJobRetryScheduler(ClinicalDataJobService clinicalDataJobService,
                                         ClinicalDataJobOrchestrator clinicalDataJobOrchestrator,
                                         PaperCaseService paperCaseService,
                                         DeviceJsonImportService deviceJsonImportService,
                                         PatientRepository patientRepository,
                                         PaperCaseRecordRepository paperCaseRecordRepository) {
        this.clinicalDataJobService = clinicalDataJobService;
        this.clinicalDataJobOrchestrator = clinicalDataJobOrchestrator;
        this.paperCaseService = paperCaseService;
        this.deviceJsonImportService = deviceJsonImportService;
        this.patientRepository = patientRepository;
        this.paperCaseRecordRepository = paperCaseRecordRepository;
    }

    @Scheduled(fixedDelayString = "${clinical-data-job.retry.fixed-delay-ms:60000}")
    public void scheduleRetries() {
        recoverTimedOutRunningJobs();
        List<ClinicalDataJob> dueJobs = clinicalDataJobService.getRetryableJobsDue(LocalDateTime.now());
        for (ClinicalDataJob job : dueJobs) {
            try {
                clinicalDataJobService.markRetryScheduled(job.getId(), "retry_scheduled");
                resubmit(job);
            } catch (RuntimeException ex) {
                log.warn("[ClinicalDataJobRetryScheduler] failed to resubmit job {}: {}", job.getId(), ex.getMessage());
            }
        }
    }

    public void recoverTimedOutRunningJobs() {
        long effectiveTimeoutMs = runningTimeoutMs > 0 ? runningTimeoutMs : 1800000L;
        LocalDateTime cutoff = LocalDateTime.now().minusNanos(effectiveTimeoutMs * 1_000_000L);
        List<ClinicalDataJob> timedOutJobs = clinicalDataJobService.getRunningJobsTimedOut(cutoff);
        for (ClinicalDataJob job : timedOutJobs) {
            try {
                clinicalDataJobService.markFailure(
                        job.getId(),
                        "running_timeout",
                        "clinical data job execution timed out after " + effectiveTimeoutMs + " ms"
                );
            } catch (RuntimeException ex) {
                log.warn("[ClinicalDataJobRetryScheduler] failed to recover timed-out running job {}: {}",
                        job.getId(), ex.getMessage());
            }
        }
    }

    public void resubmit(ClinicalDataJob job) {
        if (job == null || ClinicalDataJobService.STATUS_CANCELLED.equals(job.getJobStatus())) {
            return;
        }
        clinicalDataJobOrchestrator.submit(job.getId(), "retry_replay_dispatch", runAttempt -> replay(job, runAttempt));
    }

    private void replay(ClinicalDataJob job, Long runAttempt) {
        if (ClinicalDataJobService.JOB_TYPE_PAPER_CASE.equals(job.getJobType())) {
            replayPaperCase(job, runAttempt);
            return;
        }
        if (ClinicalDataJobService.JOB_TYPE_HUAWEI_IMPORT.equals(job.getJobType())) {
            replayDeviceImport(job, runAttempt);
            return;
        }
        throw new IllegalArgumentException("unsupported clinical data job type for retry: " + job.getJobType());
    }

    private void replayPaperCase(ClinicalDataJob job, Long runAttempt) {
        JsonNode payload = readPayload(job);
        String sourcePath = payload.path("sourcePath").asText(null);
        String contentType = payload.path("contentType").asText(null);
        if (sourcePath == null || contentType == null) {
            throw new IllegalArgumentException("paper case retry payload incomplete");
        }
        Patient patient = patientRepository.findById(job.getPatientId())
                .orElseThrow(() -> new IllegalArgumentException("patient not found for retry: " + job.getPatientId()));
        Long recordId = job.getSourceRefId();
        if (recordId == null) {
            throw new IllegalArgumentException("paper case retry missing source ref id");
        }
        PaperCaseRecord record = paperCaseRecordRepository.findById(recordId)
                .orElseThrow(() -> new IllegalArgumentException("paper case record not found for retry: " + recordId));
        paperCaseService.processPaperCaseJob(job.getId(), runAttempt, patient, record.getId(), Path.of(sourcePath), contentType);
    }

    private void replayDeviceImport(ClinicalDataJob job, Long runAttempt) {
        JsonNode payload = readPayload(job);
        String rawJson = payload.path("payload").asText(null);
        String vendor = payload.path("vendor").asText(null);
        if (rawJson == null) {
            throw new IllegalArgumentException("device import retry payload missing raw json");
        }
        deviceJsonImportService.processImportJob(job.getId(), runAttempt, job.getPatientId(), rawJson, vendor);
    }

    private JsonNode readPayload(ClinicalDataJob job) {
        try {
            return objectMapper.readTree(job.getPayloadJson());
        } catch (Exception ex) {
            throw new IllegalArgumentException("failed to read clinical data job payload", ex);
        }
    }
}
