package com.medical.agent.service.clinicaljob;

import com.medical.agent.common.exception.BusinessException;
import com.medical.agent.common.dto.ClinicalDataJobDTO;
import com.medical.agent.common.dto.ClinicalDataJobSummaryDTO;
import com.medical.agent.model.entity.ClinicalDataJob;
import com.medical.agent.model.entity.Patient;
import com.medical.agent.model.repository.PatientRepository;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class ClinicalDataJobDoctorViewService {

    private final PatientRepository patientRepository;
    private final ClinicalDataJobService clinicalDataJobService;
    private final ClinicalDataJobAdminService clinicalDataJobAdminService;

    public ClinicalDataJobDoctorViewService(PatientRepository patientRepository,
                                            ClinicalDataJobService clinicalDataJobService,
                                            ClinicalDataJobAdminService clinicalDataJobAdminService) {
        this.patientRepository = patientRepository;
        this.clinicalDataJobService = clinicalDataJobService;
        this.clinicalDataJobAdminService = clinicalDataJobAdminService;
    }

    public List<ClinicalDataJobDTO> listByStatus(Long doctorId, String status) {
        return clinicalDataJobService.getByPatientIdsAndStatus(loadPatientIds(doctorId), status);
    }

    public List<ClinicalDataJobDTO> listByPatient(Long doctorId, Long patientId, String status) {
        Long ownedPatientId = assertDoctorOwnsPatient(doctorId, patientId);
        return clinicalDataJobService.getByPatientIdAndStatus(ownedPatientId, status);
    }

    public List<ClinicalDataJobDTO> listByCorrelationId(Long doctorId, String correlationId) {
        return clinicalDataJobService.getByPatientIdsAndCorrelationId(loadPatientIds(doctorId), correlationId);
    }

    public List<ClinicalDataJobDTO> listRetryableDue(Long doctorId) {
        return clinicalDataJobService.getRetryableJobsDueByPatientIds(loadPatientIds(doctorId), java.time.LocalDateTime.now()).stream()
                .map(clinicalDataJobService::toDTO)
                .toList();
    }

    public List<ClinicalDataJobDTO> listRetryableByPatient(Long doctorId, Long patientId) {
        Long ownedPatientId = assertDoctorOwnsPatient(doctorId, patientId);
        return clinicalDataJobService.getRetryableJobsDueByPatientIds(List.of(ownedPatientId), java.time.LocalDateTime.now()).stream()
                .map(clinicalDataJobService::toDTO)
                .toList();
    }

    public Map<String, Object> requeueJob(Long doctorId, Long jobId) {
        assertDoctorOwnsJob(doctorId, jobId);
        return clinicalDataJobAdminService.requeueJob(jobId);
    }

    public Map<String, Object> cancelJob(Long doctorId, Long jobId, String reason) {
        assertDoctorOwnsJob(doctorId, jobId);
        return clinicalDataJobAdminService.cancelJob(jobId, reason);
    }

    public Map<String, Object> requeueAllDue(Long doctorId) {
        List<Long> patientIds = loadPatientIds(doctorId);
        if (patientIds.isEmpty()) {
            return Map.of(
                    "success", true,
                    "requeuedJobs", 0,
                    "message", "没有到期可重试的作业"
            );
        }

        List<ClinicalDataJob> dueJobs = clinicalDataJobService.getRetryableJobsDueByPatientIds(patientIds, java.time.LocalDateTime.now());
        int requeued = 0;
        for (ClinicalDataJob job : dueJobs) {
            clinicalDataJobAdminService.requeueJob(job.getId());
            requeued++;
        }
        return Map.of(
                "success", true,
                "requeuedJobs", requeued,
                "message", requeued == 0 ? "没有到期可重试的作业" : "已批量重新入队"
        );
    }

    public ClinicalDataJobSummaryDTO getSummary(Long doctorId) {
        List<Long> patientIds = loadPatientIds(doctorId);
        if (patientIds.isEmpty()) {
            return ClinicalDataJobSummaryDTO.builder()
                    .statusCounts(defaultStatusCounts())
                    .recentAlertCounts(Map.of())
                    .build();
        }

        List<ClinicalDataJobDTO> allJobs = clinicalDataJobService.getByPatientIds(patientIds);
        List<ClinicalDataJobDTO> retryableJobs = listRetryableDue(doctorId);
        List<ClinicalDataJobDTO> failedJobs = filterByStatus(allJobs, ClinicalDataJobService.STATUS_FAILED);
        List<ClinicalDataJobDTO> deadLetterJobs = filterByStatus(allJobs, ClinicalDataJobService.STATUS_DEAD_LETTER);
        List<ClinicalDataJobDTO> reviewPendingJobs = filterByStatus(allJobs, ClinicalDataJobService.STATUS_REVIEW_PENDING);
        List<ClinicalDataJobDTO> runningTimeoutJobs = allJobs.stream()
                .filter(job -> "running_timeout".equalsIgnoreCase(job.getLastStage()))
                .toList();
        List<ClinicalDataJobDTO> syncedRecoveredJobs = allJobs.stream()
                .filter(job -> ClinicalDataJobService.STATUS_SYNCED.equalsIgnoreCase(job.getJobStatus()))
                .filter(clinicalDataJobAdminService::hasRecoveryTimelineForView)
                .toList();

        return ClinicalDataJobSummaryDTO.builder()
                .totalJobs((long) allJobs.size())
                .retryableCount((long) retryableJobs.size())
                .runningTimeoutCount((long) runningTimeoutJobs.size())
                .recoveredCount((long) syncedRecoveredJobs.size())
                .averageRecoveryDurationMinutes(clinicalDataJobAdminService.averageRecoveryMinutesForView(syncedRecoveredJobs))
                .statusCounts(buildStatusCounts(allJobs))
                .recentAlertCounts(Map.of())
                .deadLetterByJobType(clinicalDataJobAdminService.topBucketsForView(deadLetterJobs, ClinicalDataJobDTO::getJobType, ClinicalDataJobService::jobTypeLabel, 5))
                .deadLetterByStage(clinicalDataJobAdminService.topBucketsForView(deadLetterJobs, ClinicalDataJobDTO::getLastStage, 5))
                .deadLetterByVendor(clinicalDataJobAdminService.topBucketsForView(deadLetterJobs, ClinicalDataJobDTO::getVendor, ClinicalDataJobService::vendorLabel, 5))
                .runningTimeoutByJobType(clinicalDataJobAdminService.topBucketsForView(runningTimeoutJobs, ClinicalDataJobDTO::getJobType, ClinicalDataJobService::jobTypeLabel, 5))
                .runningTimeoutByVendor(clinicalDataJobAdminService.topBucketsForView(runningTimeoutJobs, ClinicalDataJobDTO::getVendor, ClinicalDataJobService::vendorLabel, 5))
                .topDeadLetterErrors(clinicalDataJobAdminService.topErrorBucketsForView(deadLetterJobs, 5))
                .recentDeadLetters(deadLetterJobs.stream().limit(5).toList())
                .recentRunningTimeouts(runningTimeoutJobs.stream().limit(5).toList())
                .recentRecoveredJobs(syncedRecoveredJobs.stream().limit(5).toList())
                .recentAlerts(List.of())
                .jobTypeSummaries(clinicalDataJobAdminService.buildJobTypeSummariesForView(
                        allJobs,
                        syncedRecoveredJobs,
                        runningTimeoutJobs,
                        retryableJobs
                ))
                .build();
    }

    private List<ClinicalDataJobDTO> filterByStatus(List<ClinicalDataJobDTO> jobs, String status) {
        if (jobs == null || jobs.isEmpty()) {
            return List.of();
        }
        return jobs.stream()
                .filter(job -> status.equalsIgnoreCase(job.getJobStatus()))
                .toList();
    }

    private LinkedHashMap<String, Long> buildStatusCounts(List<ClinicalDataJobDTO> jobs) {
        LinkedHashMap<String, Long> result = defaultStatusCounts();
        for (ClinicalDataJobDTO job : jobs) {
            if (job == null || job.getJobStatus() == null || job.getJobStatus().isBlank()) {
                continue;
            }
            result.computeIfPresent(job.getJobStatus(), (ignored, count) -> count + 1L);
        }
        return result;
    }

    private LinkedHashMap<String, Long> defaultStatusCounts() {
        LinkedHashMap<String, Long> result = new LinkedHashMap<>();
        result.put(ClinicalDataJobService.STATUS_RECEIVED, 0L);
        result.put(ClinicalDataJobService.STATUS_PARSED, 0L);
        result.put(ClinicalDataJobService.STATUS_MAPPED, 0L);
        result.put(ClinicalDataJobService.STATUS_REVIEW_PENDING, 0L);
        result.put(ClinicalDataJobService.STATUS_RUNNING, 0L);
        result.put(ClinicalDataJobService.STATUS_SYNCED, 0L);
        result.put(ClinicalDataJobService.STATUS_FAILED, 0L);
        result.put(ClinicalDataJobService.STATUS_DEAD_LETTER, 0L);
        result.put(ClinicalDataJobService.STATUS_CANCELLED, 0L);
        return result;
    }

    private void assertDoctorOwnsJob(Long doctorId, Long jobId) {
        if (doctorId == null || jobId == null) {
            throw new BusinessException(403, "无权操作非本人负责患者的临床作业");
        }
        Set<Long> ownedPatientIds = loadPatientIds(doctorId).stream()
                .collect(Collectors.toSet());
        if (ownedPatientIds.isEmpty()) {
            throw new BusinessException(403, "无权操作非本人负责患者的临床作业");
        }
        ClinicalDataJob job = clinicalDataJobService.getRequired(jobId);
        if (job.getPatientId() == null || !ownedPatientIds.contains(job.getPatientId())) {
            throw new BusinessException(403, "无权操作非本人负责患者的临床作业");
        }
    }

    private Long assertDoctorOwnsPatient(Long doctorId, Long patientId) {
        if (doctorId == null || patientId == null) {
            throw new BusinessException(403, "无权查看非本人负责患者的临床作业");
        }
        boolean ownsPatient = loadPatientIds(doctorId).stream()
                .anyMatch(patientId::equals);
        if (!ownsPatient) {
            throw new BusinessException(403, "无权查看非本人负责患者的临床作业");
        }
        return patientId;
    }

    private List<Long> loadPatientIds(Long doctorId) {
        if (doctorId == null) {
            return List.of();
        }
        return patientRepository.findByAssignedDoctorId(doctorId).stream()
                .map(Patient::getId)
                .filter(id -> id != null)
                .toList();
    }
}
