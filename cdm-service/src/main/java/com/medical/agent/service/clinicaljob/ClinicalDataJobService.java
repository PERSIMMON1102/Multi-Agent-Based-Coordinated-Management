package com.medical.agent.service.clinicaljob;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.medical.agent.common.dto.ClinicalDataJobDTO;
import com.medical.agent.common.exception.BusinessException;
import com.medical.agent.model.entity.ClinicalDataJob;
import com.medical.agent.model.repository.ClinicalDataJobRepository;
import com.medical.agent.service.PlatformMetricsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.ReentrantLock;

@Service
public class ClinicalDataJobService {

    private static final Logger log = LoggerFactory.getLogger(ClinicalDataJobService.class);

    public static final String JOB_TYPE_PAPER_CASE = "paper_case_ingest";
    public static final String JOB_TYPE_HUAWEI_IMPORT = "huawei_iot_import";
    public static final String SOURCE_KIND_PAPER_CASE_RECORD = "paper_case_record";
    public static final String SOURCE_KIND_DEVICE_IMPORT = "device_import";

    public static final String STATUS_RECEIVED = "received";
    public static final String STATUS_PARSED = "parsed";
    public static final String STATUS_MAPPED = "mapped";
    public static final String STATUS_REVIEW_PENDING = "review_pending";
    public static final String STATUS_RUNNING = "running";
    public static final String STATUS_SYNCED = "synced";
    public static final String STATUS_FAILED = "failed";
    public static final String STATUS_DEAD_LETTER = "dead_letter";
    public static final String STATUS_CANCELLED = "cancelled";

    public static String jobTypeLabel(String jobType) {
        if (jobType == null || jobType.isBlank()) {
            return "未知作业";
        }
        return switch (jobType.trim()) {
            case JOB_TYPE_PAPER_CASE -> "纸质病历导入";
            case JOB_TYPE_HUAWEI_IMPORT -> "设备数据导入";
            default -> jobType.trim();
        };
    }

    public static String vendorLabel(String vendor) {
        if (vendor == null || vendor.isBlank()) {
            return "未标记来源";
        }
        return switch (vendor.trim().toLowerCase()) {
            case "huawei" -> "设备平台";
            default -> vendor.trim();
        };
    }

    private final ObjectMapper objectMapper = JsonMapper.builder().findAndAddModules().build();
    private final ClinicalDataJobRepository clinicalDataJobRepository;
    private final PlatformMetricsService platformMetricsService;
    private final ClinicalDataJobAlertService clinicalDataJobAlertService;
    private final ConcurrentMap<String, ReentrantLock> jobCreationLocks = new ConcurrentHashMap<>();

    @Autowired
    public ClinicalDataJobService(ClinicalDataJobRepository clinicalDataJobRepository,
                                  PlatformMetricsService platformMetricsService,
                                  ClinicalDataJobAlertService clinicalDataJobAlertService) {
        this.clinicalDataJobRepository = clinicalDataJobRepository;
        this.platformMetricsService = platformMetricsService;
        this.clinicalDataJobAlertService = clinicalDataJobAlertService;
    }

    public ClinicalDataJobService(ClinicalDataJobRepository clinicalDataJobRepository) {
        this(clinicalDataJobRepository, null, null);
    }

    public ClinicalDataJobService(ClinicalDataJobRepository clinicalDataJobRepository,
                                  PlatformMetricsService platformMetricsService) {
        this(clinicalDataJobRepository, platformMetricsService, null);
    }

    @Transactional
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
        return createOrReuseJob(
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
        ).job();
    }

    @Transactional
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
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return new JobCreationResult(saveNewJob(
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
        ReentrantLock lock = jobCreationLocks.computeIfAbsent(idempotencyKey, key -> new ReentrantLock());
        lock.lock();
        try {
            ClinicalDataJob existing = clinicalDataJobRepository.findFirstByIdempotencyKeyOrderByCreatedAtDesc(idempotencyKey)
                    .orElse(null);
            if (existing != null) {
                return new JobCreationResult(existing, false);
            }
            try {
                return new JobCreationResult(saveNewJob(
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
            } catch (DataIntegrityViolationException ex) {
                ClinicalDataJob concurrentExisting = clinicalDataJobRepository.findFirstByIdempotencyKeyOrderByCreatedAtDesc(idempotencyKey)
                        .orElse(null);
                if (concurrentExisting != null) {
                    log.info("Reusing concurrently created clinical data job for idempotencyKey={}", idempotencyKey);
                    return new JobCreationResult(concurrentExisting, false);
                }
                throw ex;
            }
        } finally {
            lock.unlock();
            if (!lock.hasQueuedThreads()) {
                jobCreationLocks.remove(idempotencyKey, lock);
            }
        }
    }

    private ClinicalDataJob saveNewJob(String jobType,
                                       Long patientId,
                                       String correlationId,
                                       String idempotencyKey,
                                       String sourceKind,
                                       Long sourceRefId,
                                       String sourceFileName,
                                       String sourceFileUrl,
                                       String vendor,
                                       Map<String, Object> payload) {
        ClinicalDataJob job = ClinicalDataJob.builder()
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
                .payloadJson(writeJson(payload))
                .build();
        ClinicalDataJob saved = clinicalDataJobRepository.saveAndFlush(job);
        if (platformMetricsService != null) {
            platformMetricsService.recordClinicalJobStage(saved.getJobType(), saved.getJobStatus(), "created");
        }
        return saved;
    }

    @Transactional
    public ClinicalDataJob markStarted(Long jobId, String stage) {
        ClinicalDataJob job = getRequired(jobId);
        if (job.getStartedAt() == null) {
            job.setStartedAt(LocalDateTime.now());
        }
        job.setLastStage(stage);
        ClinicalDataJob saved = clinicalDataJobRepository.save(job);
        if (platformMetricsService != null) {
            platformMetricsService.recordClinicalJobStage(saved.getJobType(), saved.getJobStatus(), stage);
        }
        return saved;
    }

    @Transactional
    public ClinicalDataJob markStarted(Long jobId, Long runAttempt, String stage) {
        ClinicalDataJob job = requireActiveAttempt(jobId, runAttempt, stage);
        if (job.getStartedAt() == null) {
            job.setStartedAt(LocalDateTime.now());
        }
        job.setLastStage(stage);
        ClinicalDataJob saved = clinicalDataJobRepository.save(job);
        if (platformMetricsService != null) {
            platformMetricsService.recordClinicalJobStage(saved.getJobType(), saved.getJobStatus(), stage);
        }
        return saved;
    }

    @Transactional
    public ClinicalDataJob claimForExecution(Long jobId, String stage) {
        ClinicalDataJob job = getRequired(jobId);
        if (STATUS_CANCELLED.equals(job.getJobStatus())) {
            throw new IllegalStateException("clinical data job is cancelled: " + jobId);
        }
        if (STATUS_RUNNING.equals(job.getJobStatus())) {
            throw new IllegalStateException("clinical data job already running: " + jobId);
        }
        job.setJobStatus(STATUS_RUNNING);
        job.setLastStage(stage);
        job.setStartedAt(LocalDateTime.now());
        job.setFinishedAt(null);
        job.setNextRetryAt(null);
        job.setRunAttempt((job.getRunAttempt() == null ? 0L : job.getRunAttempt()) + 1L);
        ClinicalDataJob saved = clinicalDataJobRepository.save(job);
        if (platformMetricsService != null) {
            platformMetricsService.recordClinicalJobStage(saved.getJobType(), saved.getJobStatus(), stage);
        }
        return saved;
    }

    @Transactional
    public ClinicalDataJob markStage(Long jobId, String status, String stage, Map<String, Object> result) {
        ClinicalDataJob job = getRequired(jobId);
        String previousStatus = job.getJobStatus();
        if (STATUS_CANCELLED.equals(job.getJobStatus()) && !STATUS_CANCELLED.equals(status)) {
            return job;
        }
        job.setJobStatus(status);
        job.setLastStage(stage);
        if (result != null && !result.isEmpty()) {
            job.setResultJson(writeJson(result));
        }
        if (STATUS_SYNCED.equals(status)
                || STATUS_FAILED.equals(status)
                || STATUS_DEAD_LETTER.equals(status)
                || STATUS_CANCELLED.equals(status)
                || STATUS_REVIEW_PENDING.equals(status)) {
            job.setFinishedAt(LocalDateTime.now());
        }
        ClinicalDataJob saved = clinicalDataJobRepository.save(job);
        if (platformMetricsService != null) {
            platformMetricsService.recordClinicalJobStage(saved.getJobType(), saved.getJobStatus(), stage);
            platformMetricsService.recordClinicalJobLifecycle(saved);
            if (STATUS_SYNCED.equals(saved.getJobStatus())
                    && !STATUS_SYNCED.equals(previousStatus)
                    && saved.getFirstFailedAt() != null) {
                platformMetricsService.recordClinicalJobRecovery(saved);
            }
        }
        return saved;
    }

    @Transactional
    public ClinicalDataJob markStage(Long jobId,
                                     Long runAttempt,
                                     String status,
                                     String stage,
                                     Map<String, Object> result) {
        ClinicalDataJob job = requireActiveAttempt(jobId, runAttempt, stage);
        String previousStatus = job.getJobStatus();
        job.setJobStatus(status);
        job.setLastStage(stage);
        if (result != null && !result.isEmpty()) {
            job.setResultJson(writeJson(result));
        }
        if (STATUS_SYNCED.equals(status)
                || STATUS_FAILED.equals(status)
                || STATUS_DEAD_LETTER.equals(status)
                || STATUS_CANCELLED.equals(status)
                || STATUS_REVIEW_PENDING.equals(status)) {
            job.setFinishedAt(LocalDateTime.now());
        }
        ClinicalDataJob saved = clinicalDataJobRepository.save(job);
        if (platformMetricsService != null) {
            platformMetricsService.recordClinicalJobStage(saved.getJobType(), saved.getJobStatus(), stage);
            platformMetricsService.recordClinicalJobLifecycle(saved);
            if (STATUS_SYNCED.equals(saved.getJobStatus())
                    && !STATUS_SYNCED.equals(previousStatus)
                    && saved.getFirstFailedAt() != null) {
                platformMetricsService.recordClinicalJobRecovery(saved);
            }
        }
        return saved;
    }

    @Transactional
    public ClinicalDataJob markFailure(Long jobId, String stage, String message) {
        ClinicalDataJob job = getRequired(jobId);
        if (STATUS_CANCELLED.equals(job.getJobStatus())) {
            return job;
        }
        String previousStatus = job.getJobStatus();
        int nextRetryCount = (job.getRetryCount() == null ? 0 : job.getRetryCount()) + 1;
        boolean deadLetter = nextRetryCount >= (job.getMaxRetries() == null ? 3 : job.getMaxRetries());
        boolean newlyDeadLetter = deadLetter && !STATUS_DEAD_LETTER.equals(previousStatus);
        job.setJobStatus(deadLetter ? STATUS_DEAD_LETTER : STATUS_FAILED);
        job.setLastStage(stage);
        job.setLastError(message);
        job.setRetryCount(nextRetryCount);
        if (job.getFirstFailedAt() == null) {
            job.setFirstFailedAt(LocalDateTime.now());
        }
        if (deadLetter) {
            job.setNextRetryAt(null);
        } else {
            job.setNextRetryAt(LocalDateTime.now().plusMinutes(Math.max(nextRetryCount, 1L)));
        }
        job.setFinishedAt(LocalDateTime.now());
        ClinicalDataJob saved = clinicalDataJobRepository.save(job);
        if (platformMetricsService != null) {
            platformMetricsService.recordClinicalJobStage(saved.getJobType(), saved.getJobStatus(), stage);
            platformMetricsService.recordClinicalJobLifecycle(saved);
        }
        if ("running_timeout".equals(stage) && clinicalDataJobAlertService != null) {
            try {
                clinicalDataJobAlertService.notifyRunningTimeout(saved);
            } catch (Exception ex) {
                log.warn("Failed to emit clinical data job running-timeout alert: jobId={}, reason={}",
                        saved.getId(), ex.getMessage());
            }
        }
        if (newlyDeadLetter && clinicalDataJobAlertService != null) {
            try {
                clinicalDataJobAlertService.notifyDeadLetter(saved);
            } catch (Exception ex) {
                log.warn("Failed to emit clinical data job dead-letter alert: jobId={}, reason={}",
                        saved.getId(), ex.getMessage());
            }
        }
        return saved;
    }

    @Transactional
    public ClinicalDataJob markFailure(Long jobId, Long runAttempt, String stage, String message) {
        ClinicalDataJob job = requireActiveAttempt(jobId, runAttempt, stage);
        String previousStatus = job.getJobStatus();
        int nextRetryCount = (job.getRetryCount() == null ? 0 : job.getRetryCount()) + 1;
        boolean deadLetter = nextRetryCount >= (job.getMaxRetries() == null ? 3 : job.getMaxRetries());
        boolean newlyDeadLetter = deadLetter && !STATUS_DEAD_LETTER.equals(previousStatus);
        job.setJobStatus(deadLetter ? STATUS_DEAD_LETTER : STATUS_FAILED);
        job.setLastStage(stage);
        job.setLastError(message);
        job.setRetryCount(nextRetryCount);
        if (job.getFirstFailedAt() == null) {
            job.setFirstFailedAt(LocalDateTime.now());
        }
        if (deadLetter) {
            job.setNextRetryAt(null);
        } else {
            job.setNextRetryAt(LocalDateTime.now().plusMinutes(Math.max(nextRetryCount, 1L)));
        }
        job.setFinishedAt(LocalDateTime.now());
        ClinicalDataJob saved = clinicalDataJobRepository.save(job);
        if (platformMetricsService != null) {
            platformMetricsService.recordClinicalJobStage(saved.getJobType(), saved.getJobStatus(), stage);
            platformMetricsService.recordClinicalJobLifecycle(saved);
        }
        if ("running_timeout".equals(stage) && clinicalDataJobAlertService != null) {
            try {
                clinicalDataJobAlertService.notifyRunningTimeout(saved);
            } catch (Exception ex) {
                log.warn("Failed to emit clinical data job running-timeout alert: jobId={}, reason={}",
                        saved.getId(), ex.getMessage());
            }
        }
        if (newlyDeadLetter && clinicalDataJobAlertService != null) {
            try {
                clinicalDataJobAlertService.notifyDeadLetter(saved);
            } catch (Exception ex) {
                log.warn("Failed to emit clinical data job dead-letter alert: jobId={}, reason={}",
                        saved.getId(), ex.getMessage());
            }
        }
        return saved;
    }

    @Transactional
    public ClinicalDataJob markRetryScheduled(Long jobId, String stage) {
        ClinicalDataJob job = getRequired(jobId);
        if (!isRequeueableStatus(job.getJobStatus())) {
            throw new BusinessException(409, "当前作业状态不允许重新入队: " + job.getJobStatus());
        }
        job.setJobStatus(STATUS_RECEIVED);
        job.setLastStage(stage);
        job.setStartedAt(null);
        job.setFinishedAt(null);
        job.setLastError(null);
        job.setNextRetryAt(null);
        ClinicalDataJob saved = clinicalDataJobRepository.save(job);
        if (platformMetricsService != null) {
            platformMetricsService.recordClinicalJobStage(saved.getJobType(), saved.getJobStatus(), stage);
        }
        return saved;
    }

    @Transactional
    public ClinicalDataJob cancelJob(Long jobId, String stage, String reason) {
        ClinicalDataJob job = getRequired(jobId);
        if (!isCancellableStatus(job.getJobStatus())) {
            throw new BusinessException(409, "当前作业状态不允许取消: " + job.getJobStatus());
        }
        job.setJobStatus(STATUS_CANCELLED);
        job.setLastStage(stage);
        job.setLastError(reason);
        job.setNextRetryAt(null);
        job.setFinishedAt(LocalDateTime.now());
        ClinicalDataJob saved = clinicalDataJobRepository.save(job);
        if (platformMetricsService != null) {
            platformMetricsService.recordClinicalJobStage(saved.getJobType(), saved.getJobStatus(), stage);
            platformMetricsService.recordClinicalJobLifecycle(saved);
        }
        return saved;
    }

    public ClinicalDataJob getRequired(Long jobId) {
        return clinicalDataJobRepository.findById(jobId)
                .orElseThrow(() -> new IllegalArgumentException("clinical data job not found: " + jobId));
    }

    public ClinicalDataJobDTO toDTO(ClinicalDataJob job) {
        if (job == null) {
            return null;
        }
        return ClinicalDataJobDTO.builder()
                .id(job.getId())
                .jobType(job.getJobType())
                .jobTypeLabel(jobTypeLabel(job.getJobType()))
                .jobStatus(job.getJobStatus())
                .patientId(job.getPatientId())
                .correlationId(job.getCorrelationId())
                .idempotencyKey(job.getIdempotencyKey())
                .sourceKind(job.getSourceKind())
                .sourceRefId(job.getSourceRefId())
                .sourceFileName(job.getSourceFileName())
                .sourceFileUrl(job.getSourceFileUrl())
                .vendor(job.getVendor())
                .vendorLabel(vendorLabel(job.getVendor()))
                .lastStage(job.getLastStage())
                .runAttempt(job.getRunAttempt())
                .retryCount(job.getRetryCount())
                .maxRetries(job.getMaxRetries())
                .nextRetryAt(asText(job.getNextRetryAt()))
                .firstFailedAt(asText(job.getFirstFailedAt()))
                .lastError(job.getLastError())
                .startedAt(asText(job.getStartedAt()))
                .finishedAt(asText(job.getFinishedAt()))
                .createdAt(asText(job.getCreatedAt()))
                .updatedAt(asText(job.getUpdatedAt()))
                .build();
    }

    public ClinicalDataJob findLatestBySource(String sourceKind, Long sourceRefId) {
        if (sourceKind == null || sourceKind.isBlank() || sourceRefId == null) {
            return null;
        }
        return clinicalDataJobRepository.findFirstBySourceKindAndSourceRefIdOrderByCreatedAtDesc(sourceKind, sourceRefId)
                .orElse(null);
    }

    public List<ClinicalDataJobDTO> getByStatus(String jobStatus) {
        if (jobStatus == null || jobStatus.isBlank()) {
            return List.of();
        }
        return clinicalDataJobRepository.findByJobStatusOrderByCreatedAtAsc(jobStatus).stream()
                .map(this::toDTO)
                .toList();
    }

    public List<ClinicalDataJobDTO> getByPatientId(Long patientId) {
        if (patientId == null) {
            return List.of();
        }
        return clinicalDataJobRepository.findByPatientIdOrderByCreatedAtDesc(patientId).stream()
                .map(this::toDTO)
                .toList();
    }

    public List<ClinicalDataJobDTO> getByPatientIdAndStatus(Long patientId, String jobStatus) {
        if (patientId == null) {
            return List.of();
        }
        List<ClinicalDataJobDTO> jobs = getByPatientId(patientId);
        if (jobStatus == null || jobStatus.isBlank()) {
            return jobs;
        }
        return jobs.stream()
                .filter(job -> jobStatus.equalsIgnoreCase(job.getJobStatus()))
                .toList();
    }

    public List<ClinicalDataJobDTO> getByPatientIdsAndStatus(List<Long> patientIds, String jobStatus) {
        if (patientIds == null || patientIds.isEmpty() || jobStatus == null || jobStatus.isBlank()) {
            return List.of();
        }
        return clinicalDataJobRepository.findByPatientIdInAndJobStatusOrderByCreatedAtAsc(patientIds, jobStatus).stream()
                .map(this::toDTO)
                .toList();
    }

    public List<ClinicalDataJobDTO> getByCorrelationId(String correlationId) {
        if (correlationId == null || correlationId.isBlank()) {
            return List.of();
        }
        return clinicalDataJobRepository.findByCorrelationIdOrderByCreatedAtDesc(correlationId).stream()
                .map(this::toDTO)
                .toList();
    }

    public List<ClinicalDataJobDTO> getByPatientIdsAndCorrelationId(List<Long> patientIds, String correlationId) {
        if (patientIds == null || patientIds.isEmpty() || correlationId == null || correlationId.isBlank()) {
            return List.of();
        }
        return clinicalDataJobRepository.findByPatientIdInAndCorrelationIdOrderByCreatedAtDesc(patientIds, correlationId).stream()
                .map(this::toDTO)
                .toList();
    }

    public ClinicalDataJobDTO findLatestByIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return null;
        }
        return clinicalDataJobRepository.findFirstByIdempotencyKeyOrderByCreatedAtDesc(idempotencyKey)
                .map(this::toDTO)
                .orElse(null);
    }

    public List<ClinicalDataJob> getRetryableJobsDue(LocalDateTime now) {
        LocalDateTime effectiveNow = now == null ? LocalDateTime.now() : now;
        return clinicalDataJobRepository.findByJobStatusAndNextRetryAtLessThanEqualOrderByNextRetryAtAsc(STATUS_FAILED, effectiveNow);
    }

    public List<ClinicalDataJob> getRetryableJobsDueByPatientIds(List<Long> patientIds, LocalDateTime now) {
        if (patientIds == null || patientIds.isEmpty()) {
            return List.of();
        }
        LocalDateTime effectiveNow = now == null ? LocalDateTime.now() : now;
        return clinicalDataJobRepository.findByPatientIdInAndJobStatusAndNextRetryAtLessThanEqualOrderByNextRetryAtAsc(
                patientIds,
                STATUS_FAILED,
                effectiveNow
        );
    }

    public List<ClinicalDataJobDTO> getByPatientIds(List<Long> patientIds) {
        if (patientIds == null || patientIds.isEmpty()) {
            return List.of();
        }
        return clinicalDataJobRepository.findByPatientIdInOrderByCreatedAtDesc(patientIds).stream()
                .map(this::toDTO)
                .toList();
    }

    public List<ClinicalDataJob> getRunningJobsTimedOut(LocalDateTime startedBefore) {
        if (startedBefore == null) {
            return List.of();
        }
        return clinicalDataJobRepository.findByJobStatusAndStartedAtLessThanEqualOrderByStartedAtAsc(STATUS_RUNNING, startedBefore);
    }

    public long countByStatus(String status) {
        if (status == null || status.isBlank()) {
            return 0L;
        }
        return clinicalDataJobRepository.countByJobStatus(status);
    }

    public long countByJobType(String jobType) {
        if (jobType == null || jobType.isBlank()) {
            return 0L;
        }
        return clinicalDataJobRepository.countByJobType(jobType);
    }

    public long countByJobTypeAndStatus(String jobType, String status) {
        if (jobType == null || jobType.isBlank() || status == null || status.isBlank()) {
            return 0L;
        }
        return clinicalDataJobRepository.countByJobTypeAndJobStatus(jobType, status);
    }

    public List<String> getDistinctJobTypes() {
        LinkedHashSet<String> jobTypes = new LinkedHashSet<>();
        jobTypes.add(JOB_TYPE_PAPER_CASE);
        jobTypes.add(JOB_TYPE_HUAWEI_IMPORT);
        if (clinicalDataJobRepository != null) {
            jobTypes.addAll(clinicalDataJobRepository.findDistinctJobTypes());
        }
        return jobTypes.stream()
                .filter(type -> type != null && !type.isBlank())
                .toList();
    }

    public long countAll() {
        return clinicalDataJobRepository.count();
    }

    public ClinicalDataJob assertExecutable(Long jobId, String stage) {
        ClinicalDataJob job = getRequired(jobId);
        if (STATUS_CANCELLED.equals(job.getJobStatus())) {
            throw new IllegalStateException("clinical data job is cancelled before stage " + stage + ": " + jobId);
        }
        return job;
    }

    public ClinicalDataJob assertExecutable(Long jobId, Long runAttempt, String stage) {
        return requireActiveAttempt(jobId, runAttempt, stage);
    }

    private String writeJson(Map<String, Object> value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("failed to serialize clinical data job payload", e);
        }
    }

    private String asText(LocalDateTime value) {
        return value == null ? null : value.toString();
    }

    private boolean isRequeueableStatus(String status) {
        return STATUS_FAILED.equals(status) || STATUS_DEAD_LETTER.equals(status);
    }

    private boolean isCancellableStatus(String status) {
        return STATUS_RECEIVED.equals(status)
                || STATUS_RUNNING.equals(status)
                || STATUS_PARSED.equals(status)
                || STATUS_MAPPED.equals(status)
                || STATUS_FAILED.equals(status)
                || STATUS_REVIEW_PENDING.equals(status);
    }

    private ClinicalDataJob requireActiveAttempt(Long jobId, Long runAttempt, String stage) {
        ClinicalDataJob job = getRequired(jobId);
        long expectedAttempt = runAttempt == null ? -1L : runAttempt;
        long currentAttempt = job.getRunAttempt() == null ? 0L : job.getRunAttempt();
        if (expectedAttempt != currentAttempt) {
            throw new IllegalStateException("stale clinical data job attempt " + expectedAttempt
                    + " for job " + jobId + " at stage " + stage + ", current attempt=" + currentAttempt);
        }
        if (!isAttemptMutableStatus(job.getJobStatus())) {
            throw new IllegalStateException("clinical data job attempt is no longer active for job "
                    + jobId + " at stage " + stage + ", current status=" + job.getJobStatus());
        }
        return job;
    }

    private boolean isAttemptMutableStatus(String status) {
        return STATUS_RUNNING.equals(status)
                || STATUS_PARSED.equals(status)
                || STATUS_MAPPED.equals(status);
    }

    public record JobCreationResult(ClinicalDataJob job, boolean created) {
    }
}
