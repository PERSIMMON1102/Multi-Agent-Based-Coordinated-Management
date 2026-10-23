package com.medical.agent.service.clinicaljob;

import org.junit.jupiter.api.Test;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ClinicalDataJobOrchestratorTest {

    @Test
    void shouldSkipRunnableWhenJobClaimLosesConcurrentRace() {
        AtomicBoolean executed = new AtomicBoolean(false);
        ClinicalDataJobOrchestrator orchestrator = new ClinicalDataJobOrchestrator(Runnable::run, new ClaimConflictClinicalDataJobService());

        orchestrator.submit(7L, "paper_case_async_dispatch", () -> executed.set(true));

        assertFalse(executed.get());
    }

    @Test
    void shouldPassRunAttemptIntoAttemptRunnable() {
        AtomicLong observedAttempt = new AtomicLong(-1L);
        ClinicalDataJobOrchestrator orchestrator = new ClinicalDataJobOrchestrator(
                Runnable::run,
                new RunAttemptClinicalDataJobService(4L)
        );

        orchestrator.submit(9L, "device_import_async_dispatch", observedAttempt::set);

        assertEquals(4L, observedAttempt.get());
    }

    private static final class ClaimConflictClinicalDataJobService extends ClinicalDataJobService {
        private ClaimConflictClinicalDataJobService() {
            super(null);
        }

        @Override
        public com.medical.agent.model.entity.ClinicalDataJob claimForExecution(Long jobId, String stage) {
            throw new ObjectOptimisticLockingFailureException("clinical data job", jobId);
        }
    }

    private static final class RunAttemptClinicalDataJobService extends ClinicalDataJobService {
        private final long runAttempt;

        private RunAttemptClinicalDataJobService(long runAttempt) {
            super(null);
            this.runAttempt = runAttempt;
        }

        @Override
        public com.medical.agent.model.entity.ClinicalDataJob claimForExecution(Long jobId, String stage) {
            return com.medical.agent.model.entity.ClinicalDataJob.builder()
                    .id(jobId)
                    .jobStatus(STATUS_RUNNING)
                    .runAttempt(runAttempt)
                    .build();
        }
    }
}
