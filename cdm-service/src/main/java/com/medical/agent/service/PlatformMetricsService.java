package com.medical.agent.service;

import com.medical.agent.model.entity.ClinicalDataJob;
import com.medical.agent.model.mongo.repository.OrchestrationTaskSnapshotRepository;
import com.medical.agent.model.repository.ClinicalDataJobRepository;
import com.medical.agent.service.clinicaljob.ClinicalDataJobService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class PlatformMetricsService {

    private final MeterRegistry meterRegistry;
    private final Map<String, Counter> counters = new ConcurrentHashMap<>();
    private final Map<String, Timer> timers = new ConcurrentHashMap<>();

    public PlatformMetricsService(MeterRegistry meterRegistry,
                                  ClinicalDataJobRepository clinicalDataJobRepository,
                                  OrchestrationTaskSnapshotRepository orchestrationTaskSnapshotRepository) {
        this.meterRegistry = meterRegistry;
        if (clinicalDataJobRepository != null) {
            registerClinicalJobGauges(clinicalDataJobRepository);
        }
        if (orchestrationTaskSnapshotRepository != null) {
            registerOrchestrationGauges(orchestrationTaskSnapshotRepository);
        }
    }

    public void recordClinicalJobStage(String jobType, String status, String stage) {
        counter(
                "cdm.clinical.job.stage.transitions",
                Tags.of(
                        "job_type", normalize(jobType),
                        "status", normalize(status),
                        "stage", normalize(stage)
                )
        ).increment();
    }

    public void recordClinicalJobLifecycle(ClinicalDataJob job) {
        if (job == null || job.getCreatedAt() == null || job.getFinishedAt() == null) {
            return;
        }
        long millis = Duration.between(job.getCreatedAt(), job.getFinishedAt()).toMillis();
        if (millis < 0) {
            return;
        }
        timer(
                "cdm.clinical.job.lifecycle",
                Tags.of(
                        "job_type", normalize(job.getJobType()),
                        "status", normalize(job.getJobStatus()),
                        "last_stage", normalize(job.getLastStage())
                )
        ).record(Duration.ofMillis(millis));
    }

    public void recordClinicalJobRecovery(ClinicalDataJob job) {
        if (job == null || job.getFirstFailedAt() == null || job.getFinishedAt() == null) {
            return;
        }
        long millis = Duration.between(job.getFirstFailedAt(), job.getFinishedAt()).toMillis();
        if (millis < 0) {
            return;
        }
        counter(
                "cdm.clinical.job.recovery.completed",
                Tags.of(
                        "job_type", normalize(job.getJobType()),
                        "status", normalize(job.getJobStatus())
                )
        ).increment();
        timer(
                "cdm.clinical.job.recovery.latency",
                Tags.of(
                        "job_type", normalize(job.getJobType()),
                        "status", normalize(job.getJobStatus())
                )
        ).record(Duration.ofMillis(millis));
    }

    public void recordMedplumAutoSync(String sourceKind, String status, long elapsedMillis) {
        counter(
                "cdm.medplum.auto_sync.outcomes",
                Tags.of(
                        "source_kind", normalize(sourceKind),
                        "status", normalize(status)
                )
        ).increment();
        if (elapsedMillis >= 0) {
            timer(
                    "cdm.medplum.auto_sync.latency",
                    Tags.of(
                            "source_kind", normalize(sourceKind),
                            "status", normalize(status)
                    )
            ).record(Duration.ofMillis(elapsedMillis));
        }
    }

    public void recordWatchdogAdvance(String outcome, long elapsedMillis) {
        counter(
                "cdm.orchestration.watchdog.advance.outcomes",
                Tags.of("outcome", normalize(outcome))
        ).increment();
        if (elapsedMillis >= 0) {
            timer(
                    "cdm.orchestration.watchdog.advance.latency",
                    Tags.of("outcome", normalize(outcome))
            ).record(Duration.ofMillis(elapsedMillis));
        }
    }

    public void recordSubTaskDispatch(String targetAgent, String outcome) {
        recordSubTaskDispatch(targetAgent, null, null, outcome);
    }

    public void recordSubTaskDispatch(String targetAgent,
                                      String resolvedAgentName,
                                      String resolvedAgentProvider,
                                      String outcome) {
        counter(
                "cdm.orchestration.subtask.dispatch.outcomes",
                Tags.of(
                        "target_agent", normalize(targetAgent),
                        "resolved_agent", normalize(resolvedAgentName),
                        "resolved_provider", normalize(resolvedAgentProvider),
                        "outcome", normalize(outcome)
                )
        ).increment();
    }

    public void recordClinicalJobOperatorAction(String action, ClinicalDataJob job) {
        if (job == null) {
            return;
        }
        counter(
                "cdm.clinical.job.operator.actions",
                Tags.of(
                        "action", normalize(action),
                        "job_type", normalize(job.getJobType()),
                        "status", normalize(job.getJobStatus()),
                        "stage", normalize(job.getLastStage())
                )
        ).increment();
    }

    public void recordClinicalJobAlert(String type, String outcome, ClinicalDataJob job) {
        counter(
                "cdm.clinical.job.alerts",
                Tags.of(
                        "type", normalize(type),
                        "outcome", normalize(outcome),
                        "job_type", job == null ? "unknown" : normalize(job.getJobType()),
                        "stage", job == null ? "unknown" : normalize(job.getLastStage())
                )
        ).increment();
    }

    private void registerClinicalJobGauges(ClinicalDataJobRepository repository) {
        Set<String> jobTypes = new LinkedHashSet<>(List.of(
                ClinicalDataJobService.JOB_TYPE_PAPER_CASE,
                ClinicalDataJobService.JOB_TYPE_HUAWEI_IMPORT
        ));
        try {
            jobTypes.addAll(repository.findDistinctJobTypes());
        } catch (Exception ignored) {
        }
        for (String status : new String[] {
                ClinicalDataJobService.STATUS_RECEIVED,
                ClinicalDataJobService.STATUS_PARSED,
                ClinicalDataJobService.STATUS_MAPPED,
                ClinicalDataJobService.STATUS_REVIEW_PENDING,
                ClinicalDataJobService.STATUS_RUNNING,
                ClinicalDataJobService.STATUS_SYNCED,
                ClinicalDataJobService.STATUS_FAILED,
                ClinicalDataJobService.STATUS_DEAD_LETTER,
                ClinicalDataJobService.STATUS_CANCELLED
        }) {
            Gauge.builder("cdm.clinical.job.queue.size", repository, source -> source.countByJobStatus(status))
                    .description("Count of clinical data jobs by status")
                    .tag("status", status)
                    .register(meterRegistry);
            for (String jobType : jobTypes) {
                Gauge.builder("cdm.clinical.job.queue.size.by_type", repository,
                                source -> source.countByJobTypeAndJobStatus(jobType, status))
                        .description("Count of clinical data jobs by status and job type")
                        .tag("status", status)
                        .tag("job_type", jobType)
                        .register(meterRegistry);
            }
        }
    }

    private void registerOrchestrationGauges(OrchestrationTaskSnapshotRepository repository) {
        Gauge.builder("cdm.orchestration.active.snapshot.count", repository, source -> source.findTop200ByOrderByUpdatedAtDesc().size())
                .description("Count of orchestration snapshots sampled for observability")
                .register(meterRegistry);
    }

    private Counter counter(String name, Tags tags) {
        String key = name + "|" + tags;
        return counters.computeIfAbsent(key, ignored -> Counter.builder(name).tags(tags).register(meterRegistry));
    }

    private Timer timer(String name, Tags tags) {
        String key = name + "|" + tags;
        return timers.computeIfAbsent(key, ignored -> Timer.builder(name).tags(tags).register(meterRegistry));
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? "unknown" : value.trim();
    }
}
