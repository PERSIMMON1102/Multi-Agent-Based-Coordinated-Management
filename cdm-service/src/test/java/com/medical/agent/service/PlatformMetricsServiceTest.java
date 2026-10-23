package com.medical.agent.service;

import com.medical.agent.model.entity.ClinicalDataJob;
import com.medical.agent.model.mongo.repository.OrchestrationTaskSnapshotRepository;
import com.medical.agent.model.repository.ClinicalDataJobRepository;
import com.medical.agent.service.clinicaljob.ClinicalDataJobService;
import com.medical.agent.service.testsupport.RepositoryProxyFactory;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PlatformMetricsServiceTest {

    @Test
    void shouldRegisterJobQueueGaugeAndLifecycleMetrics() {
        ClinicalDataJobRepository jobRepository = RepositoryProxyFactory.create(ClinicalDataJobRepository.class, Map.of(
                "countByJobStatus", args -> ClinicalDataJobService.STATUS_FAILED.equals(args[0]) ? 2L : 0L,
                "countByJobTypeAndJobStatus", args -> {
                    String jobType = (String) args[0];
                    String status = (String) args[1];
                    if (ClinicalDataJobService.JOB_TYPE_PAPER_CASE.equals(jobType)
                            && ClinicalDataJobService.STATUS_FAILED.equals(status)) {
                        return 1L;
                    }
                    if (ClinicalDataJobService.JOB_TYPE_HUAWEI_IMPORT.equals(jobType)
                            && ClinicalDataJobService.STATUS_FAILED.equals(status)) {
                        return 1L;
                    }
                    return 0L;
                },
                "findDistinctJobTypes", args -> java.util.List.of(
                        ClinicalDataJobService.JOB_TYPE_PAPER_CASE,
                        ClinicalDataJobService.JOB_TYPE_HUAWEI_IMPORT
                )
        ));
        OrchestrationTaskSnapshotRepository snapshotRepository = RepositoryProxyFactory.create(OrchestrationTaskSnapshotRepository.class, Map.of(
                "findTop200ByOrderByUpdatedAtDesc", args -> java.util.List.of()
        ));
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        PlatformMetricsService service = new PlatformMetricsService(meterRegistry, jobRepository, snapshotRepository);

        ClinicalDataJob job = ClinicalDataJob.builder()
                .jobType(ClinicalDataJobService.JOB_TYPE_PAPER_CASE)
                .jobStatus(ClinicalDataJobService.STATUS_SYNCED)
                .lastStage("medplum_synced")
                .firstFailedAt(LocalDateTime.now().minusSeconds(3))
                .createdAt(LocalDateTime.now().minusSeconds(5))
                .finishedAt(LocalDateTime.now())
                .build();

        service.recordClinicalJobStage(job.getJobType(), job.getJobStatus(), job.getLastStage());
        service.recordClinicalJobLifecycle(job);
        service.recordClinicalJobRecovery(job);
        service.recordClinicalJobOperatorAction("manual_requeue", job);
        service.recordClinicalJobAlert("CLINICAL_JOB_RUNNING_TIMEOUT", "sent", job);
        service.recordClinicalJobAlert("CLINICAL_JOB_RUNNING_TIMEOUT", "suppressed", job);

        assertEquals(2.0d, meterRegistry.get("cdm.clinical.job.queue.size").tag("status", ClinicalDataJobService.STATUS_FAILED).gauge().value());
        assertEquals(1.0d, meterRegistry.get("cdm.clinical.job.queue.size.by_type")
                .tag("status", ClinicalDataJobService.STATUS_FAILED)
                .tag("job_type", ClinicalDataJobService.JOB_TYPE_PAPER_CASE)
                .gauge().value());
        assertEquals(0.0d, meterRegistry.get("cdm.clinical.job.queue.size").tag("status", ClinicalDataJobService.STATUS_RUNNING).gauge().value());
        assertEquals(0.0d, meterRegistry.get("cdm.clinical.job.queue.size").tag("status", ClinicalDataJobService.STATUS_CANCELLED).gauge().value());
        assertEquals(1L, meterRegistry.get("cdm.clinical.job.stage.transitions").counter().count(), 0.0001d);
        assertEquals(1L, meterRegistry.get("cdm.clinical.job.lifecycle").timer().count());
        assertEquals(1L, meterRegistry.get("cdm.clinical.job.recovery.completed").counter().count(), 0.0001d);
        assertEquals(1L, meterRegistry.get("cdm.clinical.job.recovery.latency").timer().count());
        assertEquals(1L, meterRegistry.get("cdm.clinical.job.operator.actions").counter().count(), 0.0001d);
        assertEquals(1L, meterRegistry.get("cdm.clinical.job.alerts")
                .tag("type", "CLINICAL_JOB_RUNNING_TIMEOUT")
                .tag("outcome", "sent")
                .counter().count(), 0.0001d);
        assertEquals(1L, meterRegistry.get("cdm.clinical.job.alerts")
                .tag("type", "CLINICAL_JOB_RUNNING_TIMEOUT")
                .tag("outcome", "suppressed")
                .counter().count(), 0.0001d);
    }

    @Test
    void shouldRecordMedplumAndWatchdogMetrics() {
        ClinicalDataJobRepository jobRepository = RepositoryProxyFactory.create(ClinicalDataJobRepository.class, Map.of(
                "countByJobStatus", args -> 0L,
                "countByJobTypeAndJobStatus", args -> 0L,
                "findDistinctJobTypes", args -> java.util.List.of()
        ));
        OrchestrationTaskSnapshotRepository snapshotRepository = RepositoryProxyFactory.create(OrchestrationTaskSnapshotRepository.class, Map.of(
                "findTop200ByOrderByUpdatedAtDesc", args -> java.util.List.of()
        ));
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        PlatformMetricsService service = new PlatformMetricsService(meterRegistry, jobRepository, snapshotRepository);

        service.recordMedplumAutoSync("paper_case_record", "synced", 125);
        service.recordWatchdogAdvance("advanced", 80);
        service.recordSubTaskDispatch("risk_assessment_agent", "risk_assessment_agent_primary", "local", "completed");

        assertEquals(1L, meterRegistry.get("cdm.medplum.auto_sync.outcomes").counter().count(), 0.0001d);
        assertEquals(1L, meterRegistry.get("cdm.medplum.auto_sync.latency").timer().count());
        assertEquals(1L, meterRegistry.get("cdm.orchestration.watchdog.advance.outcomes").counter().count(), 0.0001d);
        assertEquals(1L, meterRegistry.get("cdm.orchestration.watchdog.advance.latency").timer().count());
        assertEquals(1L, meterRegistry.get("cdm.orchestration.subtask.dispatch.outcomes")
                .tag("target_agent", "risk_assessment_agent")
                .tag("resolved_agent", "risk_assessment_agent_primary")
                .tag("resolved_provider", "local")
                .tag("outcome", "completed")
                .counter().count(), 0.0001d);
    }
}
