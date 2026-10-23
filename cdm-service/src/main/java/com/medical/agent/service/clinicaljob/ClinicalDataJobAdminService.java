package com.medical.agent.service.clinicaljob;

import com.medical.agent.common.dto.ClinicalDataJobAlertDTO;
import com.medical.agent.common.dto.ClinicalDataJobBucketDTO;
import com.medical.agent.common.dto.ClinicalDataJobDTO;
import com.medical.agent.common.dto.ClinicalDataJobSummaryDTO;
import com.medical.agent.common.dto.ClinicalDataJobTypeSummaryDTO;
import com.medical.agent.model.entity.ClinicalDataJob;
import com.medical.agent.model.entity.Notification;
import com.medical.agent.model.repository.NotificationRepository;
import com.medical.agent.service.PlatformMetricsService;
import com.medical.agent.service.security.PrivacyLogSanitizer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.Duration;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Stream;
import java.util.stream.Collectors;

@Service
public class ClinicalDataJobAdminService {

    private static final String ALERT_TYPE_DEAD_LETTER = "CLINICAL_JOB_DEAD_LETTER";
    private static final String ALERT_TYPE_RUNNING_TIMEOUT = "CLINICAL_JOB_RUNNING_TIMEOUT";
    private static final String RUNNING_TIMEOUT_STAGE = "running_timeout";

    private final ClinicalDataJobService clinicalDataJobService;
    private final ClinicalDataJobRetryScheduler clinicalDataJobRetryScheduler;
    private final PlatformMetricsService platformMetricsService;
    private final NotificationRepository notificationRepository;
    private final PrivacyLogSanitizer privacyLogSanitizer = new PrivacyLogSanitizer();

    @Autowired
    public ClinicalDataJobAdminService(ClinicalDataJobService clinicalDataJobService,
                                       ClinicalDataJobRetryScheduler clinicalDataJobRetryScheduler,
                                       PlatformMetricsService platformMetricsService,
                                       NotificationRepository notificationRepository) {
        this.clinicalDataJobService = clinicalDataJobService;
        this.clinicalDataJobRetryScheduler = clinicalDataJobRetryScheduler;
        this.platformMetricsService = platformMetricsService;
        this.notificationRepository = notificationRepository;
    }

    public ClinicalDataJobAdminService(ClinicalDataJobService clinicalDataJobService,
                                       ClinicalDataJobRetryScheduler clinicalDataJobRetryScheduler) {
        this(clinicalDataJobService, clinicalDataJobRetryScheduler, null, null);
    }

    public ClinicalDataJobAdminService(ClinicalDataJobService clinicalDataJobService,
                                       ClinicalDataJobRetryScheduler clinicalDataJobRetryScheduler,
                                       NotificationRepository notificationRepository) {
        this(clinicalDataJobService, clinicalDataJobRetryScheduler, null, notificationRepository);
    }

    public List<ClinicalDataJobDTO> listByStatus(String status) {
        return clinicalDataJobService.getByStatus(status);
    }

    public List<ClinicalDataJobDTO> listRetryableDue() {
        return clinicalDataJobService.getRetryableJobsDue(LocalDateTime.now()).stream()
                .map(clinicalDataJobService::toDTO)
                .toList();
    }

    public List<ClinicalDataJobDTO> listByCorrelationId(String correlationId) {
        return clinicalDataJobService.getByCorrelationId(correlationId);
    }

    public List<ClinicalDataJobDTO> listByPatient(Long patientId, String status) {
        return clinicalDataJobService.getByPatientIdAndStatus(patientId, status);
    }

    public List<ClinicalDataJobDTO> listRetryableByPatient(Long patientId) {
        if (patientId == null) {
            return List.of();
        }
        return clinicalDataJobService.getRetryableJobsDueByPatientIds(List.of(patientId), LocalDateTime.now()).stream()
                .map(clinicalDataJobService::toDTO)
                .toList();
    }

    public ClinicalDataJobSummaryDTO getSummary() {
        List<String> statuses = List.of(
                ClinicalDataJobService.STATUS_RECEIVED,
                ClinicalDataJobService.STATUS_PARSED,
                ClinicalDataJobService.STATUS_MAPPED,
                ClinicalDataJobService.STATUS_REVIEW_PENDING,
                ClinicalDataJobService.STATUS_RUNNING,
                ClinicalDataJobService.STATUS_SYNCED,
                ClinicalDataJobService.STATUS_FAILED,
                ClinicalDataJobService.STATUS_DEAD_LETTER,
                ClinicalDataJobService.STATUS_CANCELLED
        );
        LinkedHashMap<String, Long> statusCounts = new LinkedHashMap<>();
        for (String status : statuses) {
            statusCounts.put(status, clinicalDataJobService.countByStatus(status));
        }

        List<ClinicalDataJobDTO> failedJobs = clinicalDataJobService.getByStatus(ClinicalDataJobService.STATUS_FAILED);
        List<ClinicalDataJobDTO> deadLetters = clinicalDataJobService.getByStatus(ClinicalDataJobService.STATUS_DEAD_LETTER);
        List<ClinicalDataJobDTO> syncedJobs = clinicalDataJobService.getByStatus(ClinicalDataJobService.STATUS_SYNCED);
        List<ClinicalDataJobDTO> recentDeadLetters = deadLetters.stream()
                .sorted(Comparator.comparing(ClinicalDataJobDTO::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())).reversed())
                .limit(5)
                .toList();
        List<ClinicalDataJobDTO> runningTimeoutJobs = mergeJobs(failedJobs, deadLetters).stream()
                .filter(job -> RUNNING_TIMEOUT_STAGE.equalsIgnoreCase(blankAsUnknown(job.getLastStage())))
                .sorted(Comparator.comparing(ClinicalDataJobDTO::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())).reversed())
                .toList();
        List<Notification> recentAlerts = loadRecentClinicalJobAlerts();
        List<ClinicalDataJobDTO> recoveredJobs = syncedJobs.stream()
                .filter(this::hasRecoveryTimelineForView)
                .sorted(Comparator.comparing(ClinicalDataJobDTO::getFinishedAt, Comparator.nullsLast(Comparator.naturalOrder())).reversed())
                .toList();
        List<ClinicalDataJobDTO> retryableJobs = clinicalDataJobService.getRetryableJobsDue(LocalDateTime.now()).stream()
                .map(clinicalDataJobService::toDTO)
                .toList();
        long averageRecoveryMinutes = averageRecoveryMinutesForView(recoveredJobs);
        List<ClinicalDataJobTypeSummaryDTO> jobTypeSummaries = buildJobTypeSummaries(
                recoveredJobs,
                runningTimeoutJobs,
                retryableJobs
        );

        return ClinicalDataJobSummaryDTO.builder()
                .totalJobs(clinicalDataJobService.countAll())
                .retryableCount((long) retryableJobs.size())
                .runningTimeoutCount((long) runningTimeoutJobs.size())
                .recoveredCount((long) recoveredJobs.size())
                .averageRecoveryDurationMinutes(averageRecoveryMinutes)
                .statusCounts(statusCounts)
                .recentAlertCounts(buildAlertCounts(recentAlerts))
                .deadLetterByJobType(topBucketsForView(deadLetters, ClinicalDataJobDTO::getJobType, ClinicalDataJobService::jobTypeLabel, 5))
                .deadLetterByStage(topBucketsForView(deadLetters, ClinicalDataJobDTO::getLastStage, 5))
                .deadLetterByVendor(topBucketsForView(deadLetters, ClinicalDataJobDTO::getVendor, ClinicalDataJobService::vendorLabel, 5))
                .runningTimeoutByJobType(topBucketsForView(runningTimeoutJobs, ClinicalDataJobDTO::getJobType, ClinicalDataJobService::jobTypeLabel, 5))
                .runningTimeoutByVendor(topBucketsForView(runningTimeoutJobs, ClinicalDataJobDTO::getVendor, ClinicalDataJobService::vendorLabel, 5))
                .topDeadLetterErrors(topErrorBucketsForView(deadLetters, 5))
                .recentDeadLetters(recentDeadLetters)
                .recentRunningTimeouts(runningTimeoutJobs.stream().limit(5).toList())
                .recentRecoveredJobs(recoveredJobs.stream().limit(5).toList())
                .recentAlerts(recentAlerts.stream().limit(5).map(this::toAlertDTO).toList())
                .jobTypeSummaries(jobTypeSummaries)
                .build();
    }

    public Map<String, Object> requeueJob(Long jobId) {
        ClinicalDataJob job = clinicalDataJobService.markRetryScheduled(jobId, "manual_requeue");
        clinicalDataJobRetryScheduler.resubmit(job);
        if (platformMetricsService != null) {
            platformMetricsService.recordClinicalJobOperatorAction("manual_requeue", job);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("message", "作业已重新入队");
        result.put("job", clinicalDataJobService.toDTO(job));
        return result;
    }

    public Map<String, Object> requeueAllDueJobs() {
        List<ClinicalDataJob> jobs = clinicalDataJobService.getRetryableJobsDue(LocalDateTime.now());
        int requeued = 0;
        for (ClinicalDataJob job : jobs) {
            ClinicalDataJob requeuedJob = clinicalDataJobService.markRetryScheduled(job.getId(), "bulk_manual_requeue");
            if (platformMetricsService != null) {
                platformMetricsService.recordClinicalJobOperatorAction("bulk_manual_requeue", requeuedJob);
            }
            clinicalDataJobRetryScheduler.resubmit(requeuedJob);
            requeued++;
        }
        return Map.of(
                "success", true,
                "requeuedJobs", requeued,
                "message", requeued == 0 ? "没有到期可重试的作业" : "已批量重新入队"
        );
    }

    public Map<String, Object> cancelJob(Long jobId, String reason) {
        ClinicalDataJob job = clinicalDataJobService.cancelJob(jobId, "manual_cancel", reason);
        if (platformMetricsService != null) {
            platformMetricsService.recordClinicalJobOperatorAction("manual_cancel", job);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("message", "作业已取消");
        result.put("job", clinicalDataJobService.toDTO(job));
        return result;
    }

    List<ClinicalDataJobBucketDTO> topBucketsForView(List<ClinicalDataJobDTO> jobs,
                                                     Function<ClinicalDataJobDTO, String> classifier,
                                                     int limit) {
        return topBucketsForView(jobs, classifier, Function.identity(), limit);
    }

    List<ClinicalDataJobBucketDTO> topBucketsForView(List<ClinicalDataJobDTO> jobs,
                                                     Function<ClinicalDataJobDTO, String> classifier,
                                                     Function<String, String> labelMapper,
                                                     int limit) {
        if (jobs == null || jobs.isEmpty()) {
            return List.of();
        }
        Map<String, Long> buckets = jobs.stream()
                .map(classifier)
                .map(value -> value == null || value.isBlank() ? "unknown" : value.trim())
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
        return buckets.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed().thenComparing(Map.Entry::getKey))
                .limit(Math.max(limit, 0))
                .map(entry -> ClinicalDataJobBucketDTO.builder()
                        .key(entry.getKey())
                        .label(labelMapper.apply(entry.getKey()))
                        .count(entry.getValue())
                        .build())
                .toList();
    }

    List<ClinicalDataJobBucketDTO> topErrorBucketsForView(List<ClinicalDataJobDTO> jobs, int limit) {
        return topBucketsForView(jobs, dto -> privacyLogSanitizer.summarizeText(dto.getLastError()), limit);
    }

    private List<ClinicalDataJobDTO> mergeJobs(List<ClinicalDataJobDTO> first, List<ClinicalDataJobDTO> second) {
        Map<Long, ClinicalDataJobDTO> merged = new LinkedHashMap<>();
        Stream.concat(first == null ? Stream.<ClinicalDataJobDTO>empty() : first.stream(),
                        second == null ? Stream.<ClinicalDataJobDTO>empty() : second.stream())
                .filter(job -> job != null && job.getId() != null)
                .sorted(Comparator.comparing(ClinicalDataJobDTO::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())).reversed())
                .forEach(job -> merged.putIfAbsent(job.getId(), job));
        return merged.values().stream().toList();
    }

    private List<ClinicalDataJobTypeSummaryDTO> buildJobTypeSummaries(List<ClinicalDataJobDTO> recoveredJobs,
                                                                      List<ClinicalDataJobDTO> runningTimeoutJobs,
                                                                      List<ClinicalDataJobDTO> retryableJobs) {
        return buildJobTypeSummariesForView(
                mergeJobs(
                        mergeJobs(
                                clinicalDataJobService.getByStatus(ClinicalDataJobService.STATUS_RECEIVED),
                                clinicalDataJobService.getByStatus(ClinicalDataJobService.STATUS_PARSED)
                        ),
                        mergeJobs(
                                mergeJobs(
                                        clinicalDataJobService.getByStatus(ClinicalDataJobService.STATUS_MAPPED),
                                        clinicalDataJobService.getByStatus(ClinicalDataJobService.STATUS_REVIEW_PENDING)
                                ),
                                mergeJobs(
                                        mergeJobs(
                                                clinicalDataJobService.getByStatus(ClinicalDataJobService.STATUS_RUNNING),
                                                clinicalDataJobService.getByStatus(ClinicalDataJobService.STATUS_SYNCED)
                                        ),
                                        mergeJobs(
                                                clinicalDataJobService.getByStatus(ClinicalDataJobService.STATUS_FAILED),
                                                clinicalDataJobService.getByStatus(ClinicalDataJobService.STATUS_DEAD_LETTER)
                                        )
                                )
                        )
                ),
                recoveredJobs,
                runningTimeoutJobs,
                retryableJobs
        );
    }

    List<ClinicalDataJobTypeSummaryDTO> buildJobTypeSummariesForView(List<ClinicalDataJobDTO> allJobs,
                                                                     List<ClinicalDataJobDTO> recoveredJobs,
                                                                     List<ClinicalDataJobDTO> runningTimeoutJobs,
                                                                     List<ClinicalDataJobDTO> retryableJobs) {
        return distinctJobTypes(allJobs).stream()
                .map(jobType -> ClinicalDataJobTypeSummaryDTO.builder()
                        .jobType(jobType)
                        .jobTypeLabel(ClinicalDataJobService.jobTypeLabel(jobType))
                        .totalCount(countByJobType(allJobs, jobType))
                        .failedCount(countByJobType(filterByStatus(allJobs, ClinicalDataJobService.STATUS_FAILED), jobType))
                        .deadLetterCount(countByJobType(filterByStatus(allJobs, ClinicalDataJobService.STATUS_DEAD_LETTER), jobType))
                        .reviewPendingCount(countByJobType(filterByStatus(allJobs, ClinicalDataJobService.STATUS_REVIEW_PENDING), jobType))
                        .runningCount(countByJobType(filterByStatus(allJobs, ClinicalDataJobService.STATUS_RUNNING), jobType))
                        .retryableCount(countByJobType(retryableJobs, jobType))
                        .runningTimeoutCount(countByJobType(runningTimeoutJobs, jobType))
                        .recoveredCount(countByJobType(recoveredJobs, jobType))
                        .averageRecoveryDurationMinutes(averageRecoveryMinutesForView(filterByJobType(recoveredJobs, jobType)))
                        .build())
                .filter(summary -> summary.getTotalCount() > 0
                        || summary.getFailedCount() > 0
                        || summary.getDeadLetterCount() > 0
                        || summary.getRunningCount() > 0
                        || summary.getReviewPendingCount() > 0
                        || summary.getRetryableCount() > 0
                        || summary.getRunningTimeoutCount() > 0
                        || summary.getRecoveredCount() > 0)
                .sorted(Comparator.comparing(ClinicalDataJobTypeSummaryDTO::getDeadLetterCount, Comparator.reverseOrder())
                        .thenComparing(ClinicalDataJobTypeSummaryDTO::getFailedCount, Comparator.reverseOrder())
                        .thenComparing(ClinicalDataJobTypeSummaryDTO::getRunningTimeoutCount, Comparator.reverseOrder())
                        .thenComparing(ClinicalDataJobTypeSummaryDTO::getJobType))
                .toList();
    }

    private long countByJobType(List<ClinicalDataJobDTO> jobs, String jobType) {
        return filterByJobType(jobs, jobType).size();
    }

    private List<ClinicalDataJobDTO> filterByJobType(List<ClinicalDataJobDTO> jobs, String jobType) {
        if (jobs == null || jobs.isEmpty() || !hasText(jobType)) {
            return List.of();
        }
        return jobs.stream()
                .filter(job -> job != null && jobType.equals(blankAsUnknown(job.getJobType())))
                .toList();
    }

    private List<Notification> loadRecentClinicalJobAlerts() {
        if (notificationRepository == null) {
            return List.of();
        }
        return notificationRepository.findTop50ByTypeInOrderByCreatedAtDesc(List.of(
                ALERT_TYPE_DEAD_LETTER,
                ALERT_TYPE_RUNNING_TIMEOUT
        ));
    }

    private Map<String, Long> buildAlertCounts(List<Notification> alerts) {
        if (alerts == null || alerts.isEmpty()) {
            return Map.of();
        }
        LinkedHashMap<String, Long> counts = new LinkedHashMap<>();
        counts.put(ALERT_TYPE_DEAD_LETTER, 0L);
        counts.put(ALERT_TYPE_RUNNING_TIMEOUT, 0L);
        alerts.stream()
                .map(Notification::getType)
                .filter(type -> type != null && !type.isBlank())
                .forEach(type -> counts.compute(type, (_type, count) -> count == null ? 1L : count + 1L));
        counts.entrySet().removeIf(entry -> entry.getValue() == 0L);
        return counts;
    }

    private ClinicalDataJobAlertDTO toAlertDTO(Notification notification) {
        return ClinicalDataJobAlertDTO.builder()
                .id(notification.getId())
                .type(blankAsUnknown(notification.getType()))
                .title(privacyLogSanitizer.summarizeText(notification.getTitle()))
                .message(privacyLogSanitizer.summarizeText(notification.getMessage()))
                .read(Boolean.TRUE.equals(notification.getRead()))
                .createdAt(notification.getCreatedAt() == null ? null : notification.getCreatedAt().toString())
                .build();
    }

    boolean hasRecoveryTimelineForView(ClinicalDataJobDTO job) {
        return job != null
                && hasText(job.getFirstFailedAt())
                && hasText(job.getFinishedAt())
                && parseTime(job.getFinishedAt()).isAfter(parseTime(job.getFirstFailedAt()));
    }

    long averageRecoveryMinutesForView(List<ClinicalDataJobDTO> jobs) {
        if (jobs == null || jobs.isEmpty()) {
            return 0L;
        }
        long totalMinutes = 0L;
        long count = 0L;
        for (ClinicalDataJobDTO job : jobs) {
            if (job == null || !hasRecoveryTimelineForView(job)) {
                continue;
            }
            totalMinutes += Duration.between(parseTime(job.getFirstFailedAt()), parseTime(job.getFinishedAt())).toMinutes();
            count++;
        }
        return count == 0L ? 0L : Math.round((double) totalMinutes / count);
    }

    private List<String> distinctJobTypes(List<ClinicalDataJobDTO> jobs) {
        return jobs == null ? List.of() : jobs.stream()
                .map(ClinicalDataJobDTO::getJobType)
                .filter(this::hasText)
                .distinct()
                .toList();
    }

    private List<ClinicalDataJobDTO> filterByStatus(List<ClinicalDataJobDTO> jobs, String status) {
        if (jobs == null || jobs.isEmpty() || !hasText(status)) {
            return List.of();
        }
        return jobs.stream()
                .filter(job -> job != null && status.equals(blankAsUnknown(job.getJobStatus())))
                .toList();
    }

    private LocalDateTime parseTime(String value) {
        return LocalDateTime.parse(value);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String blankAsUnknown(String value) {
        return value == null || value.isBlank() ? "unknown" : value.trim();
    }
}
