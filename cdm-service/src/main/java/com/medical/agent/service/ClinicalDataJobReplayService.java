package com.medical.agent.service;

import com.medical.agent.common.dto.ClinicalDataJobDTO;
import com.medical.agent.common.exception.BusinessException;
import com.medical.agent.model.entity.ClinicalDataJob;
import com.medical.agent.model.entity.Patient;
import com.medical.agent.model.entity.PaperCaseRecord;
import com.medical.agent.model.repository.PatientRepository;
import com.medical.agent.model.repository.PaperCaseRecordRepository;
import com.medical.agent.service.clinicaljob.ClinicalDataJobService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Comparator;
import java.util.Set;
import java.util.stream.Stream;
import java.util.stream.Collectors;

@Service
public class ClinicalDataJobReplayService {

    private final ClinicalDataJobService clinicalDataJobService;
    private final MedplumAutoSyncService medplumAutoSyncService;
    private final PaperCaseRecordRepository paperCaseRecordRepository;
    private final PatientRepository patientRepository;

    public ClinicalDataJobReplayService(ClinicalDataJobService clinicalDataJobService,
                                        MedplumAutoSyncService medplumAutoSyncService,
                                        PaperCaseRecordRepository paperCaseRecordRepository) {
        this(clinicalDataJobService, medplumAutoSyncService, paperCaseRecordRepository, null);
    }

    @Autowired
    public ClinicalDataJobReplayService(ClinicalDataJobService clinicalDataJobService,
                                        MedplumAutoSyncService medplumAutoSyncService,
                                        PaperCaseRecordRepository paperCaseRecordRepository,
                                        PatientRepository patientRepository) {
        this.clinicalDataJobService = clinicalDataJobService;
        this.medplumAutoSyncService = medplumAutoSyncService;
        this.paperCaseRecordRepository = paperCaseRecordRepository;
        this.patientRepository = patientRepository;
    }

    public List<ClinicalDataJobDTO> listFailedJobs() {
        return clinicalDataJobService.getByStatus(ClinicalDataJobService.STATUS_FAILED);
    }

    public List<ClinicalDataJobDTO> listReplayableJobs() {
        return Stream.concat(
                        clinicalDataJobService.getByStatus(ClinicalDataJobService.STATUS_FAILED).stream(),
                        clinicalDataJobService.getByStatus(ClinicalDataJobService.STATUS_DEAD_LETTER).stream()
                )
                .filter(job -> isReplayableSource(job.getSourceKind()))
                .sorted(Comparator.comparing(ClinicalDataJobDTO::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(ClinicalDataJobDTO::getId, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    public List<ClinicalDataJobDTO> listFailedJobsForDoctor(Long doctorId) {
        return clinicalDataJobService.getByPatientIdsAndStatus(loadPatientIds(doctorId), ClinicalDataJobService.STATUS_FAILED).stream()
                .filter(job -> isReplayableSource(job.getSourceKind()))
                .sorted(Comparator.comparing(ClinicalDataJobDTO::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(ClinicalDataJobDTO::getId, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    public List<ClinicalDataJobDTO> listReplayableJobsForDoctor(Long doctorId) {
        List<Long> patientIds = loadPatientIds(doctorId);
        return Stream.concat(
                        clinicalDataJobService.getByPatientIdsAndStatus(patientIds, ClinicalDataJobService.STATUS_FAILED).stream(),
                        clinicalDataJobService.getByPatientIdsAndStatus(patientIds, ClinicalDataJobService.STATUS_DEAD_LETTER).stream()
                )
                .filter(job -> isReplayableSource(job.getSourceKind()))
                .sorted(Comparator.comparing(ClinicalDataJobDTO::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(ClinicalDataJobDTO::getId, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    @Transactional
    public Map<String, Object> replayMedplumSync(Long jobId) {
        ClinicalDataJob job = clinicalDataJobService.getRequired(jobId);
        if (job.getPatientId() == null) {
            throw new BusinessException(400, "作业缺少 patientId，无法重放 Medplum 同步。");
        }
        if (!isReplayableSource(job.getSourceKind())) {
            throw new BusinessException(400, "当前作业来源暂不支持 Medplum 重放: " + job.getSourceKind());
        }
        if (!isReplayableStatus(job.getJobStatus())) {
            throw new BusinessException(409, "当前作业状态不允许执行 Medplum 重放: " + job.getJobStatus());
        }

        try {
            job = clinicalDataJobService.claimForExecution(jobId, "medplum_replay_dispatch");
        } catch (IllegalStateException ex) {
            throw new BusinessException(409, ex.getMessage());
        }
        Long runAttempt = job.getRunAttempt();
        clinicalDataJobService.markStarted(jobId, runAttempt, "medplum_replay_requested");
        MedplumAutoSyncService.MedplumSyncOutcome outcome;
        if (ClinicalDataJobService.SOURCE_KIND_PAPER_CASE_RECORD.equals(job.getSourceKind())) {
            outcome = replayPaperCase(job);
        } else {
            outcome = medplumAutoSyncService.replayPatientSync(job.getPatientId(), true);
        }

        if (outcome.successful()) {
            clinicalDataJobService.markStage(jobId, runAttempt, ClinicalDataJobService.STATUS_SYNCED,
                    outcome.skipped() ? "medplum_replay_skipped" : "medplum_replayed", Map.of(
                    "patientId", job.getPatientId(),
                    "sourceKind", job.getSourceKind(),
                    "medplumSyncStatus", outcome.status()
            ));
        } else {
            clinicalDataJobService.markFailure(jobId, runAttempt, "medplum_replay_failed", outcome.message());
        }

        ClinicalDataJob refreshed = clinicalDataJobService.getRequired(jobId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", outcome.successful());
        result.put("job", clinicalDataJobService.toDTO(refreshed));
        result.put("medplumSyncStatus", outcome.status());
        result.put("message", outcome.message());
        return result;
    }

    @Transactional
    public Map<String, Object> replayMedplumSyncForDoctor(Long doctorId, Long jobId) {
        assertDoctorOwnsJob(doctorId, jobId);
        return replayMedplumSync(jobId);
    }

    public Map<String, Object> replayReplayableJobs(Integer limit) {
        List<ClinicalDataJobDTO> replayableJobs = listReplayableJobs();
        return replayReplayableJobs(limit, replayableJobs, this::replayMedplumSync);
    }

    public Map<String, Object> replayReplayableJobsForDoctor(Long doctorId, Integer limit) {
        List<ClinicalDataJobDTO> replayableJobs = listReplayableJobsForDoctor(doctorId);
        return replayReplayableJobs(limit, replayableJobs, jobId -> replayMedplumSyncForDoctor(doctorId, jobId));
    }

    private Map<String, Object> replayReplayableJobs(Integer limit,
                                                     List<ClinicalDataJobDTO> replayableJobs,
                                                     java.util.function.Function<Long, Map<String, Object>> replayAction) {
        int effectiveLimit = limit == null || limit <= 0
                ? replayableJobs.size()
                : Math.min(limit, replayableJobs.size());
        List<ClinicalDataJobDTO> selectedJobs = replayableJobs.stream()
                .limit(effectiveLimit)
                .toList();

        int replayedJobs = 0;
        int failedJobs = 0;
        List<Map<String, Object>> results = new java.util.ArrayList<>();
        for (ClinicalDataJobDTO job : selectedJobs) {
            try {
                Map<String, Object> replayResult = replayAction.apply(job.getId());
                results.add(replayResult);
                if (Boolean.TRUE.equals(replayResult.get("success"))) {
                    replayedJobs++;
                } else {
                    failedJobs++;
                }
            } catch (RuntimeException ex) {
                failedJobs++;
                Map<String, Object> failedResult = new LinkedHashMap<>();
                failedResult.put("success", false);
                failedResult.put("jobId", job.getId());
                failedResult.put("medplumSyncStatus", "failed");
                failedResult.put("message", ex.getMessage() == null ? "批量 Medplum 重放失败" : ex.getMessage());
                results.add(failedResult);
            }
        }

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("success", failedJobs == 0);
        summary.put("requestedJobs", selectedJobs.size());
        summary.put("replayedJobs", replayedJobs);
        summary.put("failedJobs", failedJobs);
        summary.put("message", selectedJobs.isEmpty()
                ? "当前没有可重放的 Medplum 同步作业"
                : failedJobs == 0
                ? "已批量完成 Medplum 重放"
                : "批量 Medplum 重放已执行，部分作业失败");
        summary.put("results", results);
        return summary;
    }

    private void assertDoctorOwnsJob(Long doctorId, Long jobId) {
        if (doctorId == null || jobId == null) {
            throw new BusinessException(403, "无权操作非本人负责患者的 Medplum 恢复作业");
        }
        Set<Long> ownedPatientIds = loadPatientIds(doctorId).stream()
                .collect(Collectors.toSet());
        if (ownedPatientIds.isEmpty()) {
            throw new BusinessException(403, "无权操作非本人负责患者的 Medplum 恢复作业");
        }
        ClinicalDataJob job = clinicalDataJobService.getRequired(jobId);
        if (job.getPatientId() == null || !ownedPatientIds.contains(job.getPatientId())) {
            throw new BusinessException(403, "无权操作非本人负责患者的 Medplum 恢复作业");
        }
    }

    private MedplumAutoSyncService.MedplumSyncOutcome replayPaperCase(ClinicalDataJob job) {
        if (job.getSourceRefId() == null) {
            throw new BusinessException(400, "纸质病历作业缺少 sourceRefId，无法重放。");
        }
        PaperCaseRecord record = paperCaseRecordRepository.findById(job.getSourceRefId())
                .orElseThrow(() -> new BusinessException(404, "纸质病历记录不存在: " + job.getSourceRefId()));
        PaperCaseRecord synced = medplumAutoSyncService.syncPaperCaseRecord(record);
        return new MedplumAutoSyncService.MedplumSyncOutcome(
                firstNonBlank(synced.getMedplumSyncStatus(), "failed"),
                firstNonBlank(synced.getMedplumSyncMessage(), "Medplum 自动同步失败"),
                synced.getMedplumSyncedAt()
        );
    }

    private boolean isReplayableSource(String sourceKind) {
        return ClinicalDataJobService.SOURCE_KIND_PAPER_CASE_RECORD.equals(sourceKind)
                || ClinicalDataJobService.SOURCE_KIND_DEVICE_IMPORT.equals(sourceKind);
    }

    private boolean isReplayableStatus(String status) {
        return ClinicalDataJobService.STATUS_FAILED.equals(status)
                || ClinicalDataJobService.STATUS_DEAD_LETTER.equals(status);
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    private List<Long> loadPatientIds(Long doctorId) {
        if (doctorId == null || patientRepository == null) {
            return List.of();
        }
        return patientRepository.findByAssignedDoctorId(doctorId).stream()
                .map(Patient::getId)
                .filter(id -> id != null)
                .toList();
    }
}
