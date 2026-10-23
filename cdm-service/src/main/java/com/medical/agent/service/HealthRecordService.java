package com.medical.agent.service;

import com.medical.agent.common.dto.HealthRecordDTO;
import com.medical.agent.common.enums.DiseaseType;
import com.medical.agent.common.exception.BusinessException;
import com.medical.agent.model.entity.ClinicalDataJob;
import com.medical.agent.model.entity.HealthRecord;
import com.medical.agent.model.repository.HealthRecordRepository;
import com.medical.agent.service.cache.AiChatCacheInvalidationService;
import com.medical.agent.service.clinicaljob.ClinicalDataJobService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.Comparator;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Service
public class HealthRecordService {

    private static final Logger log = LoggerFactory.getLogger(HealthRecordService.class);
    private static final String BEST_EFFORT_ASSESSMENT_FLAG = "_bestEffortAssessment";
    private static final String DEFER_ASSESSMENT_FLAG = "_deferAssessment";

    private final HealthRecordRepository healthRecordRepository;
    private final ClinicalAssessmentOrchestrator clinicalAssessmentOrchestrator;
    private final AiChatCacheInvalidationService aiChatCacheInvalidationService;
    private final MedplumAutoSyncService medplumAutoSyncService;
    private final ClinicalDataJobService clinicalDataJobService;

    @Autowired
    public HealthRecordService(HealthRecordRepository healthRecordRepository,
                               ClinicalAssessmentOrchestrator clinicalAssessmentOrchestrator,
                               AiChatCacheInvalidationService aiChatCacheInvalidationService,
                               MedplumAutoSyncService medplumAutoSyncService,
                               ClinicalDataJobService clinicalDataJobService) {
        this.healthRecordRepository = healthRecordRepository;
        this.clinicalAssessmentOrchestrator = clinicalAssessmentOrchestrator;
        this.aiChatCacheInvalidationService = aiChatCacheInvalidationService;
        this.medplumAutoSyncService = medplumAutoSyncService;
        this.clinicalDataJobService = clinicalDataJobService;
    }

    public HealthRecordService(HealthRecordRepository healthRecordRepository,
                               ClinicalAssessmentOrchestrator clinicalAssessmentOrchestrator,
                               AiChatCacheInvalidationService aiChatCacheInvalidationService) {
        this(healthRecordRepository, clinicalAssessmentOrchestrator, aiChatCacheInvalidationService, null, null);
    }

    public List<HealthRecordDTO> getByPatientId(Long patientId) {
        return healthRecordRepository.findByPatientIdOrderByRecordDateDesc(patientId)
                .stream()
                .sorted(Comparator
                        .comparing(HealthRecord::getRecordDate, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(HealthRecord::getRecordedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(HealthRecord::getId, Comparator.nullsLast(Comparator.reverseOrder())))
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    /**
     * 录入健康记录并自动触发智能体评估
     */
    @Transactional
    public HealthRecordDTO create(HealthRecordDTO dto) {
        return create(dto, null, true, Collections.emptyMap());
    }

    @Transactional
    public HealthRecordDTO create(HealthRecordDTO dto,
                                  DiseaseType diseaseType,
                                  boolean triggerAssessment,
                                  Map<String, Object> assessmentOptions) {
        Map<String, Object> resolvedAssessmentOptions = assessmentOptions == null ? Collections.emptyMap() : assessmentOptions;
        ClinicalDataJob recoveryJob = resolveManualRecoveryJob(dto);
        HealthRecord record = toEntity(dto);
        record = healthRecordRepository.save(record);
        aiChatCacheInvalidationService.invalidatePatient(record.getPatientId());

        if (triggerAssessment) {
            Map<String, Object> healthData = toHealthDataMap(record);
            if (isDeferredAssessment(resolvedAssessmentOptions)) {
                Long patientId = record.getPatientId();
                Map<String, Object> deferredHealthData = new HashMap<>(healthData);
                Map<String, Object> deferredOptions = new HashMap<>(resolvedAssessmentOptions);
                CompletableFuture.runAsync(() -> triggerAssessment(
                        patientId,
                        diseaseType,
                        deferredHealthData,
                        deferredOptions
                ));
            } else {
                triggerAssessment(record.getPatientId(), diseaseType, healthData, resolvedAssessmentOptions);
            }
        }

        if (recoveryJob != null) {
            syncRecoveredClinicalJob(record, recoveryJob);
        }

        return toDTO(record);
    }

    private boolean isBestEffortAssessment(Map<String, Object> assessmentOptions) {
        if (assessmentOptions == null || assessmentOptions.isEmpty()) {
            return false;
        }
        Object value = assessmentOptions.get(BEST_EFFORT_ASSESSMENT_FLAG);
        if (value instanceof Boolean flag) {
            return flag;
        }
        return value != null && Boolean.parseBoolean(value.toString());
    }

    private boolean isDeferredAssessment(Map<String, Object> assessmentOptions) {
        if (assessmentOptions == null || assessmentOptions.isEmpty()) {
            return false;
        }
        Object value = assessmentOptions.get(DEFER_ASSESSMENT_FLAG);
        if (value instanceof Boolean flag) {
            return flag;
        }
        return value != null && Boolean.parseBoolean(value.toString());
    }

    private void triggerAssessment(Long patientId,
                                   DiseaseType diseaseType,
                                   Map<String, Object> healthData,
                                   Map<String, Object> assessmentOptions) {
        try {
            clinicalAssessmentOrchestrator.triggerAssessment(
                    patientId,
                    diseaseType,
                    healthData,
                    assessmentOptions
            );
        } catch (RuntimeException ex) {
            if (!isBestEffortAssessment(assessmentOptions)) {
                throw ex;
            }
            log.warn("Assessment degraded for health record patient {}: {}",
                    patientId, ex.getMessage());
        }
    }

    private ClinicalDataJob resolveManualRecoveryJob(HealthRecordDTO dto) {
        if (dto == null || dto.getRecoveryJobId() == null) {
            return null;
        }
        if (clinicalDataJobService == null) {
            throw new IllegalStateException("clinicalDataJobService is required for manual recovery closure");
        }
        ClinicalDataJob job = clinicalDataJobService.getRequired(dto.getRecoveryJobId());
        if (dto.getPatientId() == null || !dto.getPatientId().equals(job.getPatientId())) {
            throw new BusinessException(403, "恢复作业不属于当前患者");
        }
        if (ClinicalDataJobService.JOB_TYPE_PAPER_CASE.equals(job.getJobType())) {
            throw new BusinessException(409, "纸质病历作业请通过病历审核流恢复");
        }
        if (!ClinicalDataJobService.STATUS_FAILED.equals(job.getJobStatus())
                && !ClinicalDataJobService.STATUS_DEAD_LETTER.equals(job.getJobStatus())) {
            throw new BusinessException(409, "当前作业状态不允许通过手工补录恢复: " + job.getJobStatus());
        }
        return job;
    }

    private void syncRecoveredClinicalJob(HealthRecord record, ClinicalDataJob recoveryJob) {
        if (record == null || recoveryJob == null) {
            return;
        }
        if (medplumAutoSyncService == null) {
            throw new IllegalStateException("medplumAutoSyncService is required for manual recovery closure");
        }
        MedplumAutoSyncService.MedplumSyncOutcome outcome = medplumAutoSyncService.replayPatientSync(record.getPatientId(), true);
        if (outcome.successful()) {
            clinicalDataJobService.markStage(
                    recoveryJob.getId(),
                    ClinicalDataJobService.STATUS_SYNCED,
                    outcome.skipped()
                            ? "manual_health_record_medplum_skipped"
                            : "manual_health_record_recovered",
                    Map.of(
                            "recoveryKind", "manual_health_record_entry",
                            "healthRecordId", record.getId(),
                            "patientId", record.getPatientId(),
                            "medplumSyncStatus", outcome.status()
                    )
            );
            return;
        }
        clinicalDataJobService.markFailure(
                recoveryJob.getId(),
                "manual_health_record_sync_failed",
                firstNonBlank(outcome.message(), "手工补录后 Medplum 同步失败")
        );
    }

    private Map<String, Object> toHealthDataMap(HealthRecord r) {
        Map<String, Object> data = new HashMap<>();
        putIfNotNull(data, "systolicBp", r.getSystolicBp());
        putIfNotNull(data, "diastolicBp", r.getDiastolicBp());
        putIfNotNull(data, "restingHeartRate", r.getRestingHeartRate());
        putIfNotNull(data, "fastingGlucose", r.getFastingGlucose());
        putIfNotNull(data, "postprandialGlucose", r.getPostprandialGlucose());
        putIfNotNull(data, "hba1c", r.getHba1c());
        putIfNotNull(data, "bmi", r.getBmi());
        putIfNotNull(data, "weight", r.getWeight());
        putIfNotNull(data, "totalCholesterol", r.getTotalCholesterol());
        putIfNotNull(data, "triglyceride", r.getTriglycerides());
        putIfNotNull(data, "triglycerides", r.getTriglycerides());
        putIfNotNull(data, "ldlCholesterol", r.getLdlCholesterol());
        putIfNotNull(data, "hdlCholesterol", r.getHdlCholesterol());
        putIfNotNull(data, "fev1Percent", r.getFev1Percent());
        putIfNotNull(data, "catScore", r.getCatScore());
        putIfNotNull(data, "mmrcScore", r.getMmrcScore());
        putIfNotNull(data, "acuteExacerbations", r.getAcuteExacerbations());
        putIfNotNull(data, "smokingYears", r.getSmokingYears());
        putIfNotNull(data, "egfr", r.getEgfr());
        putIfNotNull(data, "uacr", r.getUacr());
        putIfNotNull(data, "creatinine", r.getSerumCreatinine());
        putIfNotNull(data, "serumCreatinine", r.getSerumCreatinine());
        putIfNotNull(data, "hemoglobin", r.getHemoglobin());
        putIfNotNull(data, "wbc", r.getWbc());
        putIfNotNull(data, "platelet", r.getPlatelet());
        putIfNotNull(data, "neutrophilPct", r.getNeutrophilPct());
        putIfNotNull(data, "alt", r.getAlt());
        putIfNotNull(data, "ast", r.getAst());
        putIfNotNull(data, "uricAcid", r.getUricAcid());
        putIfNotNull(data, "ck", r.getCk());
        putIfNotNull(data, "potassium", r.getPotassium());
        putIfNotNull(data, "urineProtein", r.getUrineProtein());
        putIfNotNull(data, "urineGlucose", r.getUrineGlucose());
        putIfNotNull(data, "urineBlood", r.getUrineBlood());
        putIfNotNull(data, "chestPainFrequency", r.getChestPainFrequency());
        putIfNotNull(data, "exerciseTolerance", r.getExerciseTolerance());
        putIfNotNull(data, "ejectionFraction", r.getEjectionFraction());
        putIfNotNull(data, "bnp", r.getBnp());
        putIfNotNull(data, "onAntiplatelet", r.getOnAntiplatelet());
        putIfNotNull(data, "hasAtrialFibrillation", r.getHasAtrialFibrillation());
        putIfNotNull(data, "hasDiabetesHistory", r.getHasDiabetesHistory());
        putIfNotNull(data, "isSmoking", r.getIsSmoking());
        putIfNotNull(data, "adlScore", r.getAdlScore());
        putIfNotNull(data, "mmseScore", r.getMmseScore());
        putIfNotNull(data, "carotidStenosis", r.getCarotidStenosis());
        putIfNotNull(data, "medicationMissRate", r.getMedicationMissRate());
        putIfNotNull(data, "onRegularMedication", r.getOnRegularMedication());
        putIfNotNull(data, "saltIntake", r.getSaltIntake());
        putIfNotNull(data, "exerciseFrequency", r.getExerciseFrequency());
        putIfNotNull(data, "alcoholIntake", r.getAlcoholIntake());
        return data;
    }

    private void putIfNotNull(Map<String, Object> map, String key, Object value) {
        if (value != null) map.put(key, value);
    }

    private HealthRecordDTO toDTO(HealthRecord r) {
        return HealthRecordDTO.builder()
                .id(r.getId()).patientId(r.getPatientId()).encounterId(r.getEncounterId()).recordDate(r.getRecordDate())
                .recordedAt(r.getRecordedAt())
                .systolicBp(r.getSystolicBp()).diastolicBp(r.getDiastolicBp()).restingHeartRate(r.getRestingHeartRate())
                .fastingGlucose(r.getFastingGlucose()).postprandialGlucose(r.getPostprandialGlucose()).hba1c(r.getHba1c())
                .bmi(r.getBmi()).weight(r.getWeight()).height(r.getHeight())
                .totalCholesterol(r.getTotalCholesterol()).triglycerides(r.getTriglycerides())
                .ldlCholesterol(r.getLdlCholesterol()).hdlCholesterol(r.getHdlCholesterol())
                .fev1Percent(r.getFev1Percent()).catScore(r.getCatScore()).mmrcScore(r.getMmrcScore())
                .acuteExacerbations(r.getAcuteExacerbations()).smokingYears(r.getSmokingYears())
                .egfr(r.getEgfr()).uacr(r.getUacr()).serumCreatinine(r.getSerumCreatinine()).hemoglobin(r.getHemoglobin())
                .wbc(r.getWbc()).platelet(r.getPlatelet()).neutrophilPct(r.getNeutrophilPct())
                .alt(r.getAlt()).ast(r.getAst()).uricAcid(r.getUricAcid()).ck(r.getCk()).potassium(r.getPotassium())
                .urineProtein(r.getUrineProtein()).urineGlucose(r.getUrineGlucose()).urineBlood(r.getUrineBlood())
                .chestPainFrequency(r.getChestPainFrequency()).exerciseTolerance(r.getExerciseTolerance())
                .ejectionFraction(r.getEjectionFraction()).bnp(r.getBnp()).onAntiplatelet(r.getOnAntiplatelet())
                .hasAtrialFibrillation(r.getHasAtrialFibrillation()).hasDiabetesHistory(r.getHasDiabetesHistory())
                .isSmoking(r.getIsSmoking()).adlScore(r.getAdlScore()).mmseScore(r.getMmseScore())
                .carotidStenosis(r.getCarotidStenosis())
                .medicationMissRate(r.getMedicationMissRate()).onRegularMedication(r.getOnRegularMedication())
                .saltIntake(r.getSaltIntake()).exerciseFrequency(r.getExerciseFrequency()).alcoholIntake(r.getAlcoholIntake())
                .dataSource(r.getDataSource()).sourceVendor(r.getSourceVendor())
                .sourceDeviceType(r.getSourceDeviceType()).sourceDeviceId(r.getSourceDeviceId())
                .recoveryJobId(null)
                .build();
    }

    private HealthRecord toEntity(HealthRecordDTO dto) {
        return HealthRecord.builder()
                .patientId(dto.getPatientId()).encounterId(dto.getEncounterId()).recordDate(dto.getRecordDate())
                .recordedAt(resolveRecordedAt(dto.getRecordedAt(), dto.getRecordDate()))
                .systolicBp(dto.getSystolicBp()).diastolicBp(dto.getDiastolicBp()).restingHeartRate(dto.getRestingHeartRate())
                .fastingGlucose(dto.getFastingGlucose()).postprandialGlucose(dto.getPostprandialGlucose()).hba1c(dto.getHba1c())
                .bmi(dto.getBmi()).weight(dto.getWeight()).height(dto.getHeight())
                .totalCholesterol(dto.getTotalCholesterol()).triglycerides(dto.getTriglycerides())
                .ldlCholesterol(dto.getLdlCholesterol()).hdlCholesterol(dto.getHdlCholesterol())
                .fev1Percent(dto.getFev1Percent()).catScore(dto.getCatScore()).mmrcScore(dto.getMmrcScore())
                .acuteExacerbations(dto.getAcuteExacerbations()).smokingYears(dto.getSmokingYears())
                .egfr(dto.getEgfr()).uacr(dto.getUacr()).serumCreatinine(dto.getSerumCreatinine()).hemoglobin(dto.getHemoglobin())
                .wbc(dto.getWbc()).platelet(dto.getPlatelet()).neutrophilPct(dto.getNeutrophilPct())
                .alt(dto.getAlt()).ast(dto.getAst()).uricAcid(dto.getUricAcid()).ck(dto.getCk()).potassium(dto.getPotassium())
                .urineProtein(dto.getUrineProtein()).urineGlucose(dto.getUrineGlucose()).urineBlood(dto.getUrineBlood())
                .chestPainFrequency(dto.getChestPainFrequency()).exerciseTolerance(dto.getExerciseTolerance())
                .ejectionFraction(dto.getEjectionFraction()).bnp(dto.getBnp()).onAntiplatelet(dto.getOnAntiplatelet())
                .hasAtrialFibrillation(dto.getHasAtrialFibrillation()).hasDiabetesHistory(dto.getHasDiabetesHistory())
                .isSmoking(dto.getIsSmoking()).adlScore(dto.getAdlScore()).mmseScore(dto.getMmseScore())
                .carotidStenosis(dto.getCarotidStenosis())
                .medicationMissRate(dto.getMedicationMissRate()).onRegularMedication(dto.getOnRegularMedication())
                .saltIntake(dto.getSaltIntake()).exerciseFrequency(dto.getExerciseFrequency()).alcoholIntake(dto.getAlcoholIntake())
                .dataSource(dto.getDataSource()).sourceVendor(dto.getSourceVendor())
                .sourceDeviceType(dto.getSourceDeviceType()).sourceDeviceId(dto.getSourceDeviceId())
                .build();
    }

    private LocalDateTime resolveRecordedAt(LocalDateTime recordedAt, LocalDate recordDate) {
        if (recordedAt != null) {
            return recordedAt;
        }
        return recordDate != null ? recordDate.atStartOfDay() : null;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }
}
