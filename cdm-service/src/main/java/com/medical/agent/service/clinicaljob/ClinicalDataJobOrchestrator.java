package com.medical.agent.service.clinicaljob;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;

import java.util.concurrent.Executor;

@Service
public class ClinicalDataJobOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(ClinicalDataJobOrchestrator.class);

    private final Executor clinicalDataJobExecutor;
    private final ClinicalDataJobService clinicalDataJobService;

    @Autowired
    public ClinicalDataJobOrchestrator(@Qualifier("clinicalDataJobExecutor") Executor clinicalDataJobExecutor,
                                       ClinicalDataJobService clinicalDataJobService) {
        this.clinicalDataJobExecutor = clinicalDataJobExecutor;
        this.clinicalDataJobService = clinicalDataJobService;
    }

    protected ClinicalDataJobOrchestrator(Executor clinicalDataJobExecutor) {
        this.clinicalDataJobExecutor = clinicalDataJobExecutor;
        this.clinicalDataJobService = null;
    }

    public static ClinicalDataJobOrchestrator withoutClaim(Executor clinicalDataJobExecutor) {
        return new ClinicalDataJobOrchestrator(clinicalDataJobExecutor);
    }

    public void submit(Long jobId, String stage, Runnable runnable) {
        submit(jobId, stage, ignoredAttempt -> runnable.run());
    }

    public void submit(Long jobId, String stage, AttemptRunnable runnable) {
        clinicalDataJobExecutor.execute(() -> {
            try {
                long runAttempt = 0L;
                if (clinicalDataJobService != null) {
                    runAttempt = clinicalDataJobService.claimForExecution(jobId, stage).getRunAttempt();
                }
                runnable.run(runAttempt);
            } catch (OptimisticLockingFailureException ex) {
                log.info("[ClinicalDataJobOrchestrator] skipped job {} due to concurrent claim: {}", jobId, ex.getMessage());
            } catch (IllegalStateException ex) {
                log.info("[ClinicalDataJobOrchestrator] skipped job {}: {}", jobId, ex.getMessage());
            } catch (RuntimeException ex) {
                log.warn("[ClinicalDataJobOrchestrator] job {} failed: {}", jobId, ex.getMessage());
                throw ex;
            }
        });
    }

    @FunctionalInterface
    public interface AttemptRunnable {
        void run(long runAttempt);
    }
}
