package com.medical.agent.service.clinicaljob;

import com.medical.agent.common.dto.ClinicalDataJobDTO;
import com.medical.agent.common.dto.ClinicalDataJobSummaryDTO;
import com.medical.agent.model.entity.Notification;
import com.medical.agent.model.repository.NotificationRepository;
import com.medical.agent.model.entity.ClinicalDataJob;
import com.medical.agent.service.testsupport.RepositoryProxyFactory;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClinicalDataJobAdminServiceTest {

    @Test
    void shouldRequeueSingleJob() {
        AtomicReference<ClinicalDataJob> resubmitted = new AtomicReference<>();
        StubClinicalDataJobService jobService = new StubClinicalDataJobService();
        ClinicalDataJobRetryScheduler scheduler = new StubClinicalDataJobRetryScheduler(resubmitted);
        ClinicalDataJobAdminService service = new ClinicalDataJobAdminService(jobService, scheduler);

        Map<String, Object> result = service.requeueJob(42L);

        assertEquals(true, result.get("success"));
        assertEquals("作业已重新入队", result.get("message"));
        assertEquals("received", ((ClinicalDataJobDTO) result.get("job")).getJobStatus());
        assertEquals(42L, resubmitted.get().getId());
    }

    @Test
    void shouldRequeueAllDueJobs() {
        AtomicReference<Integer> count = new AtomicReference<>(0);
        ClinicalDataJobService jobService = new StubClinicalDataJobService() {
            @Override
            public List<ClinicalDataJob> getRetryableJobsDue(LocalDateTime now) {
                return List.of(
                        ClinicalDataJob.builder().id(1L).jobStatus(STATUS_FAILED).build(),
                        ClinicalDataJob.builder().id(2L).jobStatus(STATUS_FAILED).build()
                );
            }
        };
        ClinicalDataJobRetryScheduler scheduler = new ClinicalDataJobRetryScheduler(
                null,
                ClinicalDataJobOrchestrator.withoutClaim(Runnable::run),
                null,
                null,
                null,
                null
        ) {
            @Override
            public void resubmit(ClinicalDataJob job) {
                count.set(count.get() + 1);
            }
        };
        ClinicalDataJobAdminService service = new ClinicalDataJobAdminService(jobService, scheduler);

        Map<String, Object> result = service.requeueAllDueJobs();

        assertEquals(true, result.get("success"));
        assertEquals(2, result.get("requeuedJobs"));
        assertEquals(2, count.get());
    }

    @Test
    void shouldListJobsByCorrelationId() {
        ClinicalDataJobAdminService service = new ClinicalDataJobAdminService(new StubClinicalDataJobService(), null);

        List<ClinicalDataJobDTO> jobs = service.listByCorrelationId("corr-1");

        assertEquals(1, jobs.size());
        assertEquals("corr-1", jobs.get(0).getCorrelationId());
    }

    @Test
    void shouldCancelSingleJob() {
        ClinicalDataJobAdminService service = new ClinicalDataJobAdminService(new StubClinicalDataJobService(), null);

        Map<String, Object> result = service.cancelJob(99L, "manual stop");

        assertEquals(true, result.get("success"));
        assertEquals("作业已取消", result.get("message"));
        assertEquals("cancelled", ((ClinicalDataJobDTO) result.get("job")).getJobStatus());
    }

    @Test
    void shouldBuildClinicalJobSummary() {
        NotificationRepository notificationRepository = RepositoryProxyFactory.create(NotificationRepository.class, Map.of(
                "findTop50ByTypeInOrderByCreatedAtDesc", args -> List.of(
                        notification(1L, "CLINICAL_JOB_DEAD_LETTER", "死信告警"),
                        notification(2L, "CLINICAL_JOB_RUNNING_TIMEOUT", "超时告警"),
                        notification(3L, "CLINICAL_JOB_RUNNING_TIMEOUT", "超时告警")
                )
        ));
        ClinicalDataJobAdminService service = new ClinicalDataJobAdminService(new StubClinicalDataJobService(), null, notificationRepository);

        ClinicalDataJobSummaryDTO summary = service.getSummary();

        assertEquals(5L, summary.getTotalJobs());
        assertEquals(1L, summary.getRetryableCount());
        assertEquals(1L, summary.getRunningTimeoutCount());
        assertEquals(1L, summary.getRecoveredCount());
        assertEquals(45L, summary.getAverageRecoveryDurationMinutes());
        assertEquals(2L, summary.getStatusCounts().get(ClinicalDataJobService.STATUS_DEAD_LETTER));
        assertEquals(2, summary.getDeadLetterByJobType().size());
        assertEquals(2, summary.getRecentDeadLetters().size());
        assertEquals("mapping_failed", summary.getDeadLetterByStage().get(0).getKey());
        assertEquals(2L, summary.getRecentAlertCounts().get("CLINICAL_JOB_RUNNING_TIMEOUT"));
        assertEquals(1L, summary.getRecentAlertCounts().get("CLINICAL_JOB_DEAD_LETTER"));
        assertEquals(3, summary.getRecentAlerts().size());
        assertEquals("CLINICAL_JOB_DEAD_LETTER", summary.getRecentAlerts().get(0).getType());
        assertEquals(1, summary.getRunningTimeoutByJobType().size());
        assertEquals(1, summary.getRunningTimeoutByVendor().size());
        assertEquals(1, summary.getRecentRunningTimeouts().size());
        assertEquals(2, summary.getJobTypeSummaries().size());
        assertEquals(3L, summary.getJobTypeSummaries().get(0).getTotalCount());
        assertEquals(1L, summary.getJobTypeSummaries().get(0).getDeadLetterCount());
        assertEquals(45L, summary.getJobTypeSummaries().get(0).getAverageRecoveryDurationMinutes());
    }

    private static Notification notification(Long id, String type, String title) {
        return Notification.builder()
                .id(id)
                .type(type)
                .title(title)
                .message(title + " message")
                .read(false)
                .createdAt(LocalDateTime.of(2026, 5, 29, 12, 0, 0))
                .build();
    }

    private static class StubClinicalDataJobService extends ClinicalDataJobService {
        private StubClinicalDataJobService() {
            super(null);
        }

        @Override
        public ClinicalDataJob markRetryScheduled(Long jobId, String stage) {
            return ClinicalDataJob.builder()
                    .id(jobId)
                    .jobStatus(STATUS_RECEIVED)
                    .lastStage(stage)
                    .build();
        }

        @Override
        public ClinicalDataJobDTO toDTO(ClinicalDataJob job) {
            return ClinicalDataJobDTO.builder()
                    .id(job.getId())
                    .jobStatus(job.getJobStatus())
                    .lastStage(job.getLastStage())
                    .correlationId(job.getCorrelationId())
                    .build();
        }

        @Override
        public List<ClinicalDataJobDTO> getByCorrelationId(String correlationId) {
            return List.of(ClinicalDataJobDTO.builder()
                    .id(7L)
                    .jobStatus(STATUS_FAILED)
                    .correlationId(correlationId)
                    .build());
        }

        @Override
        public ClinicalDataJob cancelJob(Long jobId, String stage, String reason) {
            return ClinicalDataJob.builder()
                    .id(jobId)
                    .jobStatus(STATUS_CANCELLED)
                    .lastStage(stage)
                    .lastError(reason)
                    .build();
        }

        @Override
        public long countByStatus(String status) {
            return switch (status) {
                case STATUS_FAILED -> 1L;
                case STATUS_DEAD_LETTER -> 2L;
                case STATUS_REVIEW_PENDING -> 1L;
                case STATUS_SYNCED -> 1L;
                default -> 0L;
            };
        }

        @Override
        public long countAll() {
            return 5L;
        }

        @Override
        public long countByJobType(String jobType) {
            return switch (jobType) {
                case JOB_TYPE_PAPER_CASE -> 3L;
                case JOB_TYPE_HUAWEI_IMPORT -> 2L;
                default -> 0L;
            };
        }

        @Override
        public long countByJobTypeAndStatus(String jobType, String status) {
            if (JOB_TYPE_PAPER_CASE.equals(jobType)) {
                return switch (status) {
                    case STATUS_FAILED -> 1L;
                    case STATUS_DEAD_LETTER -> 1L;
                    case STATUS_REVIEW_PENDING -> 1L;
                    case STATUS_SYNCED -> 1L;
                    default -> 0L;
                };
            }
            if (JOB_TYPE_HUAWEI_IMPORT.equals(jobType)) {
                return switch (status) {
                    case STATUS_DEAD_LETTER -> 1L;
                    default -> 0L;
                };
            }
            return 0L;
        }

        @Override
        public List<String> getDistinctJobTypes() {
            return List.of(JOB_TYPE_PAPER_CASE, JOB_TYPE_HUAWEI_IMPORT);
        }

        @Override
        public List<ClinicalDataJobDTO> getByStatus(String status) {
            if (STATUS_SYNCED.equals(status)) {
                return List.of(
                        ClinicalDataJobDTO.builder()
                                .id(30L)
                                .jobStatus(STATUS_SYNCED)
                                .jobType(JOB_TYPE_PAPER_CASE)
                                .vendor("baidu")
                                .firstFailedAt("2026-05-29T10:00:00")
                                .finishedAt("2026-05-29T10:45:00")
                                .createdAt("2026-05-29T09:00:00")
                                .build()
                );
            }
            if (STATUS_FAILED.equals(status)) {
                return List.of(
                        ClinicalDataJobDTO.builder()
                                .id(20L)
                                .jobStatus(STATUS_FAILED)
                                .jobType(JOB_TYPE_PAPER_CASE)
                                .vendor("baidu")
                                .lastStage("running_timeout")
                                .lastError("timeout")
                                .createdAt("2026-05-29T14:00:00")
                                .build()
                );
            }
            if (!STATUS_DEAD_LETTER.equals(status)) {
                return List.of();
            }
            return List.of(
                    ClinicalDataJobDTO.builder()
                            .id(21L)
                            .jobStatus(STATUS_DEAD_LETTER)
                            .jobType(JOB_TYPE_PAPER_CASE)
                            .vendor("baidu")
                            .lastStage("mapping_failed")
                            .lastError("mapping overflow")
                            .createdAt("2026-05-29T12:00:00")
                            .build(),
                    ClinicalDataJobDTO.builder()
                            .id(22L)
                            .jobStatus(STATUS_DEAD_LETTER)
                            .jobType(JOB_TYPE_HUAWEI_IMPORT)
                            .vendor("huawei")
                            .lastStage("mapping_failed")
                            .lastError("mapping overflow")
                            .createdAt("2026-05-29T13:00:00")
                            .build()
            );
        }

        @Override
        public List<ClinicalDataJob> getRetryableJobsDue(LocalDateTime now) {
            return List.of(ClinicalDataJob.builder()
                    .id(31L)
                    .jobStatus(STATUS_FAILED)
                    .build());
        }
    }

    private static final class StubClinicalDataJobRetryScheduler extends ClinicalDataJobRetryScheduler {
        private final AtomicReference<ClinicalDataJob> resubmitted;

        private StubClinicalDataJobRetryScheduler(AtomicReference<ClinicalDataJob> resubmitted) {
            super(null, ClinicalDataJobOrchestrator.withoutClaim(Runnable::run), null, null, null, null);
            this.resubmitted = resubmitted;
        }

        @Override
        public void resubmit(ClinicalDataJob job) {
            resubmitted.set(job);
        }
    }
}
