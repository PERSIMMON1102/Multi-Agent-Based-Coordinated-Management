package com.medical.agent.service.clinicaljob;

import com.medical.agent.common.dto.ClinicalDataJobDTO;
import com.medical.agent.common.dto.ClinicalDataJobSummaryDTO;
import com.medical.agent.common.exception.BusinessException;
import com.medical.agent.model.entity.ClinicalDataJob;
import com.medical.agent.model.entity.Patient;
import com.medical.agent.model.repository.PatientRepository;
import com.medical.agent.service.testsupport.RepositoryProxyFactory;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ClinicalDataJobDoctorViewServiceTest {

    @Test
    void shouldOnlyListDoctorOwnedJobs() {
        ClinicalDataJobDoctorViewService service = new ClinicalDataJobDoctorViewService(
                patientRepository(),
                stubJobService(),
                new StubClinicalDataJobAdminService()
        );

        List<ClinicalDataJobDTO> jobs = service.listByStatus(9L, ClinicalDataJobService.STATUS_FAILED);

        assertEquals(1, jobs.size());
        assertEquals(101L, jobs.get(0).getPatientId());
    }

    @Test
    void shouldListJobsForOwnedPatientOnly() {
        ClinicalDataJobDoctorViewService service = new ClinicalDataJobDoctorViewService(
                patientRepository(),
                stubJobService(),
                new StubClinicalDataJobAdminService()
        );

        List<ClinicalDataJobDTO> jobs = service.listByPatient(9L, 101L, null);

        assertEquals(2, jobs.size());
        assertEquals(List.of(101L, 101L), jobs.stream().map(ClinicalDataJobDTO::getPatientId).toList());
    }

    @Test
    void shouldListRetryableJobsForOwnedPatientOnly() {
        ClinicalDataJobDoctorViewService service = new ClinicalDataJobDoctorViewService(
                patientRepository(),
                stubJobService(),
                new StubClinicalDataJobAdminService()
        );

        List<ClinicalDataJobDTO> jobs = service.listRetryableByPatient(9L, 102L);

        assertEquals(1, jobs.size());
        assertEquals(102L, jobs.get(0).getPatientId());
    }

    @Test
    void shouldBuildSummaryFromDoctorOwnedJobsOnly() {
        ClinicalDataJobDoctorViewService service = new ClinicalDataJobDoctorViewService(
                patientRepository(),
                stubJobService(),
                new StubClinicalDataJobAdminService()
        );

        ClinicalDataJobSummaryDTO summary = service.getSummary(9L);

        assertEquals(4L, summary.getTotalJobs());
        assertEquals(1L, summary.getStatusCounts().get(ClinicalDataJobService.STATUS_FAILED));
        assertEquals(1L, summary.getStatusCounts().get(ClinicalDataJobService.STATUS_DEAD_LETTER));
        assertEquals(1L, summary.getStatusCounts().get(ClinicalDataJobService.STATUS_REVIEW_PENDING));
        assertEquals(1L, summary.getStatusCounts().get(ClinicalDataJobService.STATUS_SYNCED));
        assertEquals(1L, summary.getRetryableCount());
        assertEquals(1L, summary.getRunningTimeoutCount());
        assertEquals(1L, summary.getRecoveredCount());
        assertEquals(45L, summary.getAverageRecoveryDurationMinutes());
        assertEquals(2, summary.getJobTypeSummaries().size());
        assertEquals(1, summary.getRecentDeadLetters().size());
    }

    @Test
    void shouldOnlyReturnCorrelationMatchesForOwnedPatients() {
        ClinicalDataJobDoctorViewService service = new ClinicalDataJobDoctorViewService(
                patientRepository(),
                stubJobService(),
                new StubClinicalDataJobAdminService()
        );

        List<ClinicalDataJobDTO> jobs = service.listByCorrelationId(9L, "corr-owned");

        assertEquals(2, jobs.size());
        assertEquals(101L, jobs.get(0).getPatientId());
        assertEquals(102L, jobs.get(1).getPatientId());
    }

    @Test
    void shouldRejectRequeueForUnownedJob() {
        ClinicalDataJobDoctorViewService service = new ClinicalDataJobDoctorViewService(
                patientRepository(),
                stubJobService(),
                new StubClinicalDataJobAdminService()
        );

        BusinessException exception = assertThrows(BusinessException.class, () -> service.requeueJob(9L, 999L));

        assertEquals(403, exception.getCode());
    }

    @Test
    void shouldRejectPatientScopedLookupForUnownedPatient() {
        ClinicalDataJobDoctorViewService service = new ClinicalDataJobDoctorViewService(
                patientRepository(),
                stubJobService(),
                new StubClinicalDataJobAdminService()
        );

        BusinessException exception = assertThrows(BusinessException.class, () -> service.listByPatient(9L, 999L, null));

        assertEquals(403, exception.getCode());
    }

    @Test
    void shouldAllowOwnedJobMutationsAndDoctorScopedBulkRequeue() {
        StubClinicalDataJobAdminService adminService = new StubClinicalDataJobAdminService();
        ClinicalDataJobDoctorViewService service = new ClinicalDataJobDoctorViewService(
                patientRepository(),
                stubJobService(),
                adminService
        );

        Map<String, Object> requeueResult = service.requeueJob(9L, 1L);
        Map<String, Object> cancelResult = service.cancelJob(9L, 2L, "manual stop");
        Map<String, Object> bulkResult = service.requeueAllDue(9L);

        assertEquals(true, requeueResult.get("success"));
        assertEquals(true, cancelResult.get("success"));
        assertEquals(1, bulkResult.get("requeuedJobs"));
        assertEquals(List.of(1L, 6L), adminService.requeuedJobIds);
        assertEquals(List.of(2L), adminService.cancelledJobIds);
    }

    private static PatientRepository patientRepository() {
        return RepositoryProxyFactory.create(PatientRepository.class, Map.of(
                "findByAssignedDoctorId", args -> {
                    Long doctorId = (Long) args[0];
                    if (Long.valueOf(9L).equals(doctorId)) {
                        return List.of(
                                Patient.builder().id(101L).assignedDoctorId(9L).build(),
                                Patient.builder().id(102L).assignedDoctorId(9L).build()
                        );
                    }
                    return List.of();
                }
        ));
    }

    private static ClinicalDataJobService stubJobService() {
        return new ClinicalDataJobService(null) {
            @Override
            public List<ClinicalDataJobDTO> getByPatientIdsAndStatus(List<Long> patientIds, String jobStatus) {
                return ownedJobs().stream()
                        .filter(job -> patientIds.contains(job.getPatientId()))
                        .filter(job -> jobStatus.equals(job.getJobStatus()))
                        .toList();
            }

            @Override
            public List<ClinicalDataJobDTO> getByPatientIdsAndCorrelationId(List<Long> patientIds, String correlationId) {
                return ownedJobs().stream()
                        .filter(job -> patientIds.contains(job.getPatientId()))
                        .filter(job -> correlationId.equals(job.getCorrelationId()))
                        .toList();
            }

            @Override
            public List<ClinicalDataJobDTO> getByPatientId(Long patientId) {
                return ownedJobs().stream()
                        .filter(job -> patientId.equals(job.getPatientId()))
                        .toList();
            }

            @Override
            public List<ClinicalDataJobDTO> getByPatientIdAndStatus(Long patientId, String jobStatus) {
                return ownedJobs().stream()
                        .filter(job -> patientId.equals(job.getPatientId()))
                        .filter(job -> jobStatus == null || jobStatus.equals(job.getJobStatus()))
                        .toList();
            }

            @Override
            public List<ClinicalDataJob> getRetryableJobsDueByPatientIds(List<Long> patientIds, LocalDateTime now) {
                return List.of(
                        ClinicalDataJob.builder().id(6L).patientId(102L).jobStatus(STATUS_FAILED).build()
                ).stream().filter(job -> patientIds.contains(job.getPatientId())).toList();
            }

            @Override
            public List<ClinicalDataJobDTO> getByPatientIds(List<Long> patientIds) {
                return ownedJobs().stream()
                        .filter(job -> patientIds.contains(job.getPatientId()))
                        .toList();
            }

            @Override
            public ClinicalDataJobDTO toDTO(ClinicalDataJob job) {
                return ClinicalDataJobDTO.builder()
                        .id(job.getId())
                        .patientId(job.getPatientId())
                        .jobStatus(job.getJobStatus())
                        .build();
            }

            @Override
            public ClinicalDataJob getRequired(Long jobId) {
                return switch (jobId.intValue()) {
                    case 1 -> ClinicalDataJob.builder().id(1L).patientId(101L).jobStatus(STATUS_FAILED).build();
                    case 2 -> ClinicalDataJob.builder().id(2L).patientId(102L).jobStatus(STATUS_REVIEW_PENDING).build();
                    case 3 -> ClinicalDataJob.builder().id(3L).patientId(102L).jobStatus(STATUS_FAILED).build();
                    default -> ClinicalDataJob.builder().id(jobId).patientId(999L).jobStatus(STATUS_FAILED).build();
                };
            }
        };
    }

    private static List<ClinicalDataJobDTO> ownedJobs() {
        return List.of(
                ClinicalDataJobDTO.builder()
                        .id(1L)
                        .patientId(101L)
                        .jobStatus(ClinicalDataJobService.STATUS_FAILED)
                        .jobType(ClinicalDataJobService.JOB_TYPE_PAPER_CASE)
                        .correlationId("corr-owned")
                        .vendor("baidu")
                        .lastStage("running_timeout")
                        .lastError("timeout")
                        .createdAt("2026-05-29T14:00:00")
                        .build(),
                ClinicalDataJobDTO.builder()
                        .id(2L)
                        .patientId(102L)
                        .jobStatus(ClinicalDataJobService.STATUS_DEAD_LETTER)
                        .jobType(ClinicalDataJobService.JOB_TYPE_HUAWEI_IMPORT)
                        .correlationId("corr-owned")
                        .vendor("huawei")
                        .lastStage("mapping_failed")
                        .lastError("mapping overflow")
                        .createdAt("2026-05-29T13:00:00")
                        .build(),
                ClinicalDataJobDTO.builder()
                        .id(3L)
                        .patientId(101L)
                        .jobStatus(ClinicalDataJobService.STATUS_REVIEW_PENDING)
                        .jobType(ClinicalDataJobService.JOB_TYPE_PAPER_CASE)
                        .correlationId("corr-review")
                        .vendor("baidu")
                        .lastStage("review_pending")
                        .createdAt("2026-05-29T12:00:00")
                        .build(),
                ClinicalDataJobDTO.builder()
                        .id(4L)
                        .patientId(102L)
                        .jobStatus(ClinicalDataJobService.STATUS_SYNCED)
                        .jobType(ClinicalDataJobService.JOB_TYPE_PAPER_CASE)
                        .correlationId("corr-synced")
                        .vendor("baidu")
                        .firstFailedAt("2026-05-29T10:00:00")
                        .finishedAt("2026-05-29T10:45:00")
                        .createdAt("2026-05-29T09:00:00")
                        .build(),
                ClinicalDataJobDTO.builder()
                        .id(5L)
                        .patientId(999L)
                        .jobStatus(ClinicalDataJobService.STATUS_FAILED)
                        .jobType(ClinicalDataJobService.JOB_TYPE_PAPER_CASE)
                        .correlationId("corr-other")
                        .vendor("other")
                        .lastStage("mapping_failed")
                        .createdAt("2026-05-29T08:00:00")
                        .build()
        );
    }

    private static final class StubClinicalDataJobAdminService extends ClinicalDataJobAdminService {
        private final java.util.ArrayList<Long> requeuedJobIds = new java.util.ArrayList<>();
        private final java.util.ArrayList<Long> cancelledJobIds = new java.util.ArrayList<>();

        private StubClinicalDataJobAdminService() {
            super(null, null);
        }

        @Override
        public Map<String, Object> requeueJob(Long jobId) {
            requeuedJobIds.add(jobId);
            return Map.of("success", true, "jobId", jobId);
        }

        @Override
        public Map<String, Object> cancelJob(Long jobId, String reason) {
            cancelledJobIds.add(jobId);
            return Map.of("success", true, "jobId", jobId, "reason", reason);
        }
    }
}
