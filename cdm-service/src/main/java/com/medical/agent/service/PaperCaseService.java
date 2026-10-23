package com.medical.agent.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.medical.agent.common.dto.HealthRecordDTO;
import com.medical.agent.common.dto.PaperCaseIcdConfirmDTO;
import com.medical.agent.common.dto.PaperCaseIcdCandidateDTO;
import com.medical.agent.common.dto.PaperCasePatientInfoDTO;
import com.medical.agent.common.dto.PaperCasePatientSummaryDTO;
import com.medical.agent.common.dto.PaperCaseRecordDTO;
import com.medical.agent.common.exception.BusinessException;
import com.medical.agent.model.entity.PaperCaseRecord;
import com.medical.agent.model.entity.Patient;
import com.medical.agent.model.entity.PatientMedicalHistory;
import com.medical.agent.model.repository.PaperCaseRecordRepository;
import com.medical.agent.model.repository.PatientMedicalHistoryRepository;
import com.medical.agent.model.repository.PatientRepository;
import com.medical.agent.service.cache.AiChatCacheInvalidationService;
import com.medical.agent.service.clinicaljob.ClinicalDataJobOrchestrator;
import com.medical.agent.service.clinicaljob.ClinicalDataJobService;
import com.medical.agent.service.papercase.PaperCaseExtractionResult;
import com.medical.agent.service.papercase.PaperCaseIcd11Service;
import com.medical.agent.service.papercase.PaperCaseIcdMatchResult;
import com.medical.agent.service.papercase.PaperCaseVisionOcrService;
import com.medical.agent.service.security.PaperCaseFileAccess;
import com.medical.agent.service.security.PaperCaseUploadSecurityService;
import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class PaperCaseService {

    private static final Logger log = LoggerFactory.getLogger(PaperCaseService.class);
    private static final List<String> SUPPORTED_EXTENSIONS = List.of(".png", ".jpg", ".jpeg", ".pdf");

    private final ObjectMapper objectMapper = JsonMapper.builder()
            .findAndAddModules()
            .build();
    private final PaperCaseRecordRepository paperCaseRecordRepository;
    private final PatientRepository patientRepository;
    private final PatientMedicalHistoryRepository patientMedicalHistoryRepository;
    private final PaperCaseVisionOcrService paperCaseVisionOcrService;
    private final PaperCaseIcd11Service paperCaseIcd11Service;
    private final HealthRecordService healthRecordService;
    private final AiChatCacheInvalidationService aiChatCacheInvalidationService;
    private final MedplumAutoSyncService medplumAutoSyncService;
    private final ClinicalDataJobService clinicalDataJobService;
    private final ClinicalDataJobOrchestrator clinicalDataJobOrchestrator;
    private final ClinicalDataModelService clinicalDataModelService;
    private final PaperCaseUploadSecurityService paperCaseUploadSecurityService;

    @Autowired
    public PaperCaseService(PaperCaseRecordRepository paperCaseRecordRepository,
                            PatientRepository patientRepository,
                            PatientMedicalHistoryRepository patientMedicalHistoryRepository,
                            PaperCaseVisionOcrService paperCaseVisionOcrService,
                            PaperCaseIcd11Service paperCaseIcd11Service,
                            HealthRecordService healthRecordService,
                            AiChatCacheInvalidationService aiChatCacheInvalidationService,
                            MedplumAutoSyncService medplumAutoSyncService,
                            ClinicalDataJobService clinicalDataJobService,
                            ClinicalDataJobOrchestrator clinicalDataJobOrchestrator,
                            ClinicalDataModelService clinicalDataModelService,
                            PaperCaseUploadSecurityService paperCaseUploadSecurityService) {
        this.paperCaseRecordRepository = paperCaseRecordRepository;
        this.patientRepository = patientRepository;
        this.patientMedicalHistoryRepository = patientMedicalHistoryRepository;
        this.paperCaseVisionOcrService = paperCaseVisionOcrService;
        this.paperCaseIcd11Service = paperCaseIcd11Service;
        this.healthRecordService = healthRecordService;
        this.aiChatCacheInvalidationService = aiChatCacheInvalidationService;
        this.medplumAutoSyncService = medplumAutoSyncService;
        this.clinicalDataJobService = clinicalDataJobService;
        this.clinicalDataJobOrchestrator = clinicalDataJobOrchestrator;
        this.clinicalDataModelService = clinicalDataModelService;
        this.paperCaseUploadSecurityService = paperCaseUploadSecurityService;
    }

    public PaperCaseService(PaperCaseRecordRepository paperCaseRecordRepository,
                            PatientRepository patientRepository,
                            PatientMedicalHistoryRepository patientMedicalHistoryRepository,
                            PaperCaseVisionOcrService paperCaseVisionOcrService,
                            PaperCaseIcd11Service paperCaseIcd11Service,
                            HealthRecordService healthRecordService,
                            AiChatCacheInvalidationService aiChatCacheInvalidationService,
                            MedplumAutoSyncService medplumAutoSyncService,
                            ClinicalDataJobService clinicalDataJobService,
                            ClinicalDataJobOrchestrator clinicalDataJobOrchestrator,
                            ClinicalDataModelService clinicalDataModelService) {
        this(paperCaseRecordRepository,
                patientRepository,
                patientMedicalHistoryRepository,
                paperCaseVisionOcrService,
                paperCaseIcd11Service,
                healthRecordService,
                aiChatCacheInvalidationService,
                medplumAutoSyncService,
                clinicalDataJobService,
                clinicalDataJobOrchestrator,
                clinicalDataModelService,
                new PaperCaseUploadSecurityService());
    }

    public List<PaperCaseRecordDTO> getByPatientId(Long patientId) {
        return paperCaseRecordRepository.findByPatientIdOrderByCreatedAtDesc(patientId)
                .stream()
                .map(this::toDTO)
                .toList();
    }

    public PaperCasePatientSummaryDTO getPatientSummary(Long patientId) {
        if (patientId == null) {
            throw new BusinessException(400, "patientId 不能为空");
        }
        List<PaperCaseRecordDTO> allRecords = getByPatientId(patientId);
        List<PaperCaseRecordDTO> pendingReviewRecords = allRecords.stream()
                .filter(item -> "pending_review".equalsIgnoreCase(item.getIcdCodingStatus()))
                .toList();
        List<PaperCaseRecordDTO> failedSyncRecords = allRecords.stream()
                .filter(item -> "failed".equalsIgnoreCase(item.getMedplumSyncStatus()))
                .toList();
        List<PaperCaseRecordDTO> attentionRecords = allRecords.stream()
                .filter(item -> "pending_review".equalsIgnoreCase(item.getIcdCodingStatus())
                        || "failed".equalsIgnoreCase(item.getMedplumSyncStatus())
                        || item.getJob() != null && (
                        ClinicalDataJobService.STATUS_REVIEW_PENDING.equalsIgnoreCase(item.getJob().getJobStatus())
                                || ClinicalDataJobService.STATUS_FAILED.equalsIgnoreCase(item.getJob().getJobStatus())
                                || ClinicalDataJobService.STATUS_DEAD_LETTER.equalsIgnoreCase(item.getJob().getJobStatus())))
                .toList();
        PaperCasePatientInfoDTO patientInfo = allRecords.isEmpty() ? null : allRecords.get(0).getPatientInfo();
        long failedJobCount = allRecords.stream()
                .filter(item -> item.getJob() != null
                        && (ClinicalDataJobService.STATUS_FAILED.equalsIgnoreCase(item.getJob().getJobStatus())
                        || ClinicalDataJobService.STATUS_DEAD_LETTER.equalsIgnoreCase(item.getJob().getJobStatus())))
                .count();
        return PaperCasePatientSummaryDTO.builder()
                .patientId(patientId)
                .patientInfo(patientInfo)
                .totalRecords((long) allRecords.size())
                .pendingReviewCount((long) pendingReviewRecords.size())
                .failedSyncCount((long) failedSyncRecords.size())
                .failedJobCount(failedJobCount)
                .attentionRecords(new ArrayList<>(attentionRecords))
                .pendingReviewRecords(new ArrayList<>(pendingReviewRecords))
                .failedSyncRecords(new ArrayList<>(failedSyncRecords))
                .allRecords(new ArrayList<>(allRecords))
                .build();
    }

    public List<PaperCaseRecordDTO> getPendingReviewRecords() {
        return paperCaseRecordRepository.findByIcdCodingStatusOrderByCreatedAtDesc("pending_review")
                .stream()
                .map(this::toDTO)
                .toList();
    }

    public List<PaperCaseRecordDTO> getPendingReviewRecordsForDoctor(Long doctorId) {
        List<Long> patientIds = loadPatientIds(doctorId);
        if (patientIds.isEmpty()) {
            return List.of();
        }
        return paperCaseRecordRepository.findByPatientIdInAndIcdCodingStatusOrderByCreatedAtDesc(patientIds, "pending_review")
                .stream()
                .map(this::toDTO)
                .toList();
    }

    public List<PaperCaseRecordDTO> getFailedSyncRecords() {
        return paperCaseRecordRepository.findByMedplumSyncStatusOrderByUpdatedAtDesc("failed")
                .stream()
                .map(this::toDTO)
                .toList();
    }

    public List<PaperCaseRecordDTO> getFailedSyncRecordsForDoctor(Long doctorId) {
        List<Long> patientIds = loadPatientIds(doctorId);
        if (patientIds.isEmpty()) {
            return List.of();
        }
        return paperCaseRecordRepository.findByPatientIdInAndMedplumSyncStatusOrderByUpdatedAtDesc(patientIds, "failed")
                .stream()
                .map(this::toDTO)
                .toList();
    }

    public Long getPatientIdByPaperCaseId(Long paperCaseId) {
        if (paperCaseId == null) {
            throw new BusinessException(400, "paperCaseId 不能为空");
        }
        return paperCaseRecordRepository.findById(paperCaseId)
                .map(PaperCaseRecord::getPatientId)
                .orElseThrow(() -> new BusinessException(404, "纸质病历记录不存在"));
    }

    public PaperCaseFileAccess resolveFileAccess(Long paperCaseId) {
        if (paperCaseId == null) {
            throw new BusinessException(400, "paperCaseId 不能为空");
        }
        PaperCaseRecord record = paperCaseRecordRepository.findById(paperCaseId)
                .orElseThrow(() -> new BusinessException(404, "纸质病历记录不存在"));
        if (!hasText(record.getSourceObjectKey())) {
            throw new BusinessException(404, "病历原件不存在");
        }
        Path filePath = Path.of(System.getProperty("user.dir"), "uploads", record.getSourceObjectKey())
                .toAbsolutePath()
                .normalize();
        if (!Files.exists(filePath)) {
            throw new BusinessException(404, "病历原件不存在");
        }
        return PaperCaseFileAccess.builder()
                .paperCaseId(record.getId())
                .patientId(record.getPatientId())
                .filePath(filePath)
                .mediaType(firstNonBlank(record.getSourceFileType(), "application/octet-stream"))
                .downloadFileName(firstNonBlank(record.getOriginalFileName(), filePath.getFileName().toString()))
                .build();
    }

    @Transactional
    public PaperCaseRecordDTO confirmIcd(Long paperCaseId, PaperCaseIcdConfirmDTO request, Long reviewerUserId) {
        if (paperCaseId == null) {
            throw new BusinessException(400, "paperCaseId 不能为空");
        }
        if (request == null || !hasText(request.getCode())) {
            throw new BusinessException(400, "ICD-11 编码不能为空");
        }
        PaperCaseRecord record = paperCaseRecordRepository.findById(paperCaseId)
                .orElseThrow(() -> new BusinessException(404, "纸质病历记录不存在"));
        record.setIcdCode(request.getCode().trim());
        record.setIcdTitle(hasText(request.getTitle()) ? request.getTitle().trim() : null);
        record.setIcdConfidence(request.getConfidence());
        record.setIcdCodingStatus("manually_confirmed");
        record.setIcdCodingSource("manual");
        record.setIcdReviewedBy(reviewerUserId);
        record.setIcdReviewedAt(LocalDateTime.now());
        record.setMedplumSyncStatus(ClinicalDataJobService.STATUS_REVIEW_PENDING);
        record.setMedplumSyncMessage("ICD 已人工确认，等待 Medplum 同步");
        PaperCaseRecord saved = paperCaseRecordRepository.save(record);
        clinicalDataModelService.upsertPaperCaseEncounter(saved);
        saved = medplumAutoSyncService.syncPaperCaseRecord(saved);
        syncConfirmedPaperCaseJob(saved);
        aiChatCacheInvalidationService.invalidatePatient(saved.getPatientId());
        return toDTO(saved);
    }

    @Transactional
    public PaperCaseRecordDTO processUpload(Long patientId, MultipartFile file, LocalDate recordDate) {
        if (patientId == null) {
            throw new BusinessException(400, "patientId 不能为空");
        }
        if (file == null || file.isEmpty()) {
            throw new BusinessException(400, "上传文件不能为空");
        }
        Patient patient = patientRepository.findById(patientId)
                .orElseThrow(() -> new BusinessException(404, "患者不存在"));

        String originalName = file.getOriginalFilename();
        String ext = resolveExtension(originalName);
        ensureSupported(ext);
        paperCaseUploadSecurityService.validatePaperCaseFile(file, ext);
        byte[] fileFingerprintBytes;
        try {
            fileFingerprintBytes = file.getBytes();
        } catch (IOException ex) {
            throw new BusinessException(500, "读取病历上传文件失败: " + ex.getMessage());
        }
        String idempotencyKey = buildIdempotencyKey(patientId, ext, fileFingerprintBytes, recordDate);
        var existingJob = clinicalDataJobService.findLatestByIdempotencyKey(idempotencyKey);
        if (existingJob != null) {
            if (shouldCreateRetryForExistingJob(existingJob)) {
                idempotencyKey = idempotencyKey + ":retry:" + System.currentTimeMillis();
            } else {
                PaperCaseRecordDTO duplicate = null;
                if (existingJob.getSourceRefId() != null) {
                    duplicate = paperCaseRecordRepository.findById(existingJob.getSourceRefId())
                            .map(this::toDTO)
                            .orElse(null);
                }
                if (duplicate != null) {
                    duplicate.setJob(existingJob);
                    duplicate.setMedplumSyncStatus("duplicate");
                    duplicate.setMedplumSyncMessage("重复病历上传已被拦截，可查看既有作业链");
                    return duplicate;
                }
                throw new BusinessException(409, "检测到重复病历上传请求，请稍后刷新作业队列");
            }
        }

        Path uploadRoot = Path.of(System.getProperty("user.dir"), "uploads", "paper-cases", String.valueOf(patientId));
        try {
            Files.createDirectories(uploadRoot);
        } catch (IOException ex) {
            throw new BusinessException(500, "创建病历上传目录失败: " + ex.getMessage());
        }

        String fileName = UUID.randomUUID() + ext;
        Path target = uploadRoot.resolve(fileName).normalize();
        if (!target.startsWith(uploadRoot.toAbsolutePath().normalize())) {
            throw new BusinessException(500, "病历文件保存路径非法");
        }
        try (InputStream inputStream = file.getInputStream()) {
            Files.copy(inputStream, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            throw new BusinessException(500, "保存病历原文件失败: " + ex.getMessage());
        }

        String objectKey = "paper-cases/" + patientId + "/" + fileName;
        String contentType = detectContentType(file.getContentType(), ext);
        try {
            PaperCaseRecord record = PaperCaseRecord.builder()
                    .patientId(patientId)
                    .recordDate(recordDate)
                    .sourceFileUrl(objectKey)
                    .sourceObjectKey(objectKey)
                    .sourceFileType(contentType)
                    .originalFileName(originalName)
                    .medplumSyncStatus("pending")
                    .medplumSyncMessage("等待异步作业处理")
                    .build();
            record = paperCaseRecordRepository.save(record);
            record.setSourceFileUrl(buildSecureDownloadUrl(record.getId()));
            record = paperCaseRecordRepository.save(record);

            ClinicalDataJobService.JobCreationResult jobCreation = clinicalDataJobService.createOrReuseJob(
                    ClinicalDataJobService.JOB_TYPE_PAPER_CASE,
                    patientId,
                    buildCorrelationId("paper-case", patientId),
                    idempotencyKey,
                    ClinicalDataJobService.SOURCE_KIND_PAPER_CASE_RECORD,
                    record.getId(),
                    originalName,
                    record.getSourceFileUrl(),
                    null,
                    Map.of(
                            "recordDate", recordDate == null ? "" : recordDate.toString(),
                            "contentType", contentType,
                            "sourcePath", target.toString()
                    )
            );
            var job = jobCreation.job();
            if (!jobCreation.created()) {
                cleanupFailedUpload(target);
                deletePaperCaseRecord(record.getId());
                PaperCaseRecord existingRecord = job.getSourceRefId() == null ? null
                        : paperCaseRecordRepository.findById(job.getSourceRefId()).orElse(null);
                if (existingRecord != null) {
                    PaperCaseRecordDTO duplicate = toDTO(existingRecord);
                    duplicate.setJob(clinicalDataJobService.toDTO(job));
                    duplicate.setMedplumSyncStatus("duplicate");
                    duplicate.setMedplumSyncMessage("重复病历上传已被拦截，可查看既有作业链");
                    return duplicate;
                }
                throw new BusinessException(409, "检测到重复病历上传请求，请稍后刷新作业队列");
            }
            Long jobId = job.getId();
            Long recordId = record.getId();
            Runnable task = () -> clinicalDataJobOrchestrator.submit(jobId, "paper_case_async_dispatch",
                    runAttempt -> processPaperCaseJob(jobId, runAttempt, patient, recordId, target, contentType));
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        task.run();
                    }
                });
            } else {
                task.run();
            }
            PaperCaseRecord refreshed = paperCaseRecordRepository.findById(record.getId()).orElse(record);
            PaperCaseRecordDTO dto = toDTO(refreshed);
            dto.setJob(clinicalDataJobService.toDTO(job));
            return dto;
        } catch (RuntimeException ex) {
            cleanupFailedUpload(target);
            throw ex;
        }
    }

    @Transactional
    public void processPaperCaseJob(Long jobId,
                                    Long runAttempt,
                                    Patient patient,
                                    Long paperCaseRecordId,
                                    Path target,
                                    String contentType) {
        clinicalDataJobService.markStarted(jobId, runAttempt, "received");
        try {
            clinicalDataJobService.assertExecutable(jobId, runAttempt, "ocr_extract");
            PaperCaseExtractionResult extraction = paperCaseVisionOcrService.extract(target, contentType);
            clinicalDataJobService.markStage(jobId, runAttempt, ClinicalDataJobService.STATUS_PARSED, "ocr_parsed", Map.of(
                    "diagnosis", firstNonBlank(extraction.getPreliminaryDiagnosis(), "")
            ));
            clinicalDataJobService.assertExecutable(jobId, runAttempt, "icd_mapping");

            PaperCaseIcdMatchResult icdMatch = paperCaseIcd11Service.matchDiagnosis(extraction.getPreliminaryDiagnosis());
            boolean historySynced = synchronizeMedicalHistory(patient, extraction, icdMatch);

            PaperCaseRecord record = paperCaseRecordRepository.findById(paperCaseRecordId)
                    .orElseThrow(() -> new BusinessException(404, "纸质病历记录不存在"));
            record.setOcrText(extraction.getRawText());
            record.setPatientName(firstNonBlank(extraction.getPatientName(), patient.getName()));
            record.setGender(firstNonBlank(extraction.getGender(), patient.getGender()));
            record.setAge(extraction.getAge());
            record.setChiefComplaint(extraction.getChiefComplaint());
            record.setPresentIllness(extraction.getPresentIllness());
            record.setPhysicalExam(extraction.getPhysicalExam());
            record.setPreliminaryDiagnosis(extraction.getPreliminaryDiagnosis());
            record.setIcdCode(icdMatch.getCode());
            record.setIcdTitle(icdMatch.getTitle());
            record.setIcdConfidence(icdMatch.getConfidence());
            record.setIcdCodingStatus(firstNonBlank(icdMatch.getCodingStatus(), "not_coded"));
            record.setIcdCodingSource(firstNonBlank(icdMatch.getCodingSource(), "unknown"));
            record.setIcdCandidatesJson(writeJsonSafely(icdMatch.getCandidates()));
            record.setRawModelJson(extraction.getRawJson());
            record.setMedicalHistorySynced(historySynced);
            boolean requiresManualReview = requiresManualCodingReview(icdMatch);
            record.setMedplumSyncStatus(requiresManualReview
                    ? ClinicalDataJobService.STATUS_REVIEW_PENDING
                    : "pending_sync");
            record.setMedplumSyncMessage(requiresManualReview
                    ? "病历解析完成，等待 ICD 人工确认后同步 Medplum"
                    : "病历解析完成，准备同步 Medplum");
            record = paperCaseRecordRepository.save(record);

            var encounter = clinicalDataModelService.upsertPaperCaseEncounter(record, extraction, icdMatch);
            HealthRecordDTO synchronizedMetrics = synchronizeStructuredMetrics(
                    patient.getId(),
                    record.getRecordDate(),
                    extraction.getStructuredMetrics(),
                    encounter.getId()
            );
            if (synchronizedMetrics != null) {
                record.setStructuredMetricsJson(writeJsonSafely(synchronizedMetrics));
                record = paperCaseRecordRepository.save(record);
            }
            if (synchronizedMetrics != null && synchronizedMetrics.getId() != null) {
                clinicalDataModelService.recordMeasurementSource(
                        synchronizedMetrics,
                        encounter.getId(),
                        ClinicalDataJobService.SOURCE_KIND_PAPER_CASE_RECORD,
                        record.getId(),
                        "paper_case",
                        "paper_case_document",
                        record.getSourceObjectKey(),
                        Map.of("paperCaseRecordId", record.getId()),
                        Map.of("structuredMetricsJson", firstNonBlank(record.getStructuredMetricsJson(), ""))
                );
            }

            clinicalDataJobService.markStage(jobId, runAttempt, ClinicalDataJobService.STATUS_MAPPED, "structured_mapped", Map.of(
                    "paperCaseRecordId", record.getId(),
                    "medicalHistorySynced", historySynced,
                    "encounterId", encounter.getId(),
                    "requiresManualReview", requiresManualReview
            ));
            clinicalDataJobService.assertExecutable(jobId, runAttempt, "medplum_sync");

            if (requiresManualReview) {
                clinicalDataJobService.markStage(jobId, runAttempt, ClinicalDataJobService.STATUS_REVIEW_PENDING, "awaiting_manual_icd_review", Map.of(
                        "paperCaseRecordId", record.getId(),
                        "icdCodingStatus", firstNonBlank(record.getIcdCodingStatus(), "unknown")
                ));
            } else {
                record = medplumAutoSyncService.syncPaperCaseRecord(record);
                if (MedplumAutoSyncService.isSuccessfulStatus(record.getMedplumSyncStatus())) {
                    clinicalDataJobService.markStage(jobId, runAttempt, ClinicalDataJobService.STATUS_SYNCED,
                            MedplumAutoSyncService.isSkippedStatus(record.getMedplumSyncStatus())
                                    ? "medplum_sync_skipped"
                                    : "medplum_synced", Map.of(
                            "paperCaseRecordId", record.getId(),
                            "medplumSyncStatus", firstNonBlank(record.getMedplumSyncStatus(), "unknown")
                    ));
                } else {
                    clinicalDataJobService.markFailure(jobId, runAttempt, "medplum_sync_failed", firstNonBlank(
                            record.getMedplumSyncMessage(),
                            "Medplum 自动同步失败"
                    ));
                }
            }
            aiChatCacheInvalidationService.invalidatePatient(patient.getId());
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            clinicalDataJobService.markFailure(jobId, runAttempt, "failed", ex.getMessage());
            cleanupFailedUpload(target);
            throw ex;
        }
    }

    private HealthRecordDTO synchronizeStructuredMetrics(Long patientId, LocalDate recordDate, HealthRecordDTO structuredMetrics) {
        return synchronizeStructuredMetrics(patientId, recordDate, structuredMetrics, null);
    }

    private HealthRecordDTO synchronizeStructuredMetrics(Long patientId,
                                                         LocalDate recordDate,
                                                         HealthRecordDTO structuredMetrics,
                                                         Long encounterId) {
        if (structuredMetrics == null || !hasMeaningfulMetric(structuredMetrics)) {
            return structuredMetrics;
        }
        HealthRecordDTO payload = HealthRecordDTO.builder()
                .patientId(patientId)
                .encounterId(encounterId)
                .recordDate(recordDate)
                .height(structuredMetrics.getHeight())
                .weight(structuredMetrics.getWeight())
                .systolicBp(structuredMetrics.getSystolicBp())
                .diastolicBp(structuredMetrics.getDiastolicBp())
                .restingHeartRate(structuredMetrics.getRestingHeartRate())
                .fastingGlucose(structuredMetrics.getFastingGlucose())
                .postprandialGlucose(structuredMetrics.getPostprandialGlucose())
                .hba1c(structuredMetrics.getHba1c())
                .bmi(structuredMetrics.getBmi())
                .totalCholesterol(structuredMetrics.getTotalCholesterol())
                .triglycerides(structuredMetrics.getTriglycerides())
                .ldlCholesterol(structuredMetrics.getLdlCholesterol())
                .hdlCholesterol(structuredMetrics.getHdlCholesterol())
                .egfr(structuredMetrics.getEgfr())
                .uacr(structuredMetrics.getUacr())
                .serumCreatinine(structuredMetrics.getSerumCreatinine())
                .hemoglobin(structuredMetrics.getHemoglobin())
                .wbc(structuredMetrics.getWbc())
                .platelet(structuredMetrics.getPlatelet())
                .neutrophilPct(structuredMetrics.getNeutrophilPct())
                .alt(structuredMetrics.getAlt())
                .ast(structuredMetrics.getAst())
                .uricAcid(structuredMetrics.getUricAcid())
                .ck(structuredMetrics.getCk())
                .potassium(structuredMetrics.getPotassium())
                .urineProtein(structuredMetrics.getUrineProtein())
                .urineGlucose(structuredMetrics.getUrineGlucose())
                .urineBlood(structuredMetrics.getUrineBlood())
                .build();
        return healthRecordService.create(payload, null, true, Map.of(
                "source", "paper_case",
                "_bestEffortAssessment", true,
                "_deferAssessment", true
        ));
    }

    private boolean synchronizeMedicalHistory(Patient patient,
                                              PaperCaseExtractionResult extraction,
                                              PaperCaseIcdMatchResult icdMatch) {
        List<String> notes = new ArrayList<>();
        appendIfPresent(notes, "主诉", extraction.getChiefComplaint());
        appendIfPresent(notes, "现病史", extraction.getPresentIllness());
        appendIfPresent(notes, "体格检查", extraction.getPhysicalExam());
        appendIfPresent(notes, "初步诊断", extraction.getPreliminaryDiagnosis());
        if (icdMatch != null && icdMatch.getCode() != null) {
            String label = "manually_confirmed".equalsIgnoreCase(icdMatch.getCodingStatus())
                    || "auto_coded".equalsIgnoreCase(icdMatch.getCodingStatus())
                    ? "ICD-11 编码"
                    : "ICD-11 候选";
            notes.add(label + "：" + icdMatch.getCode() + " " + firstNonBlank(icdMatch.getTitle(), ""));
        }
        if (notes.isEmpty()) {
            return false;
        }

        PatientMedicalHistory history = patientMedicalHistoryRepository.findByPatientId(patient.getId())
                .orElseGet(() -> PatientMedicalHistory.builder().patientId(patient.getId()).build());

        String diagnosisText = firstNonBlank(extraction.getPreliminaryDiagnosis(), icdMatch != null ? icdMatch.getTitle() : null);
        history.setPastDiseases(mergeText(history.getPastDiseases(), diagnosisText));

        String summary = String.join("\n", notes);
        history.setOtherNotes(mergeText(history.getOtherNotes(), summary));
        patientMedicalHistoryRepository.save(history);
        return true;
    }

    private PaperCaseRecordDTO toDTO(PaperCaseRecord record) {
        List<PaperCaseIcdCandidateDTO> candidates = readIcdCandidates(record.getIcdCandidatesJson());
        HealthRecordDTO metrics = readMetrics(record.getStructuredMetricsJson());
        return PaperCaseRecordDTO.builder()
                .id(record.getId())
                .patientId(record.getPatientId())
                .recordDate(record.getRecordDate())
                .sourceFileUrl(buildSecureDownloadUrl(record.getId()))
                .sourceFileType(record.getSourceFileType())
                .originalFileName(record.getOriginalFileName())
                .ocrText(record.getOcrText())
                .patientInfo(PaperCasePatientInfoDTO.builder()
                        .name(record.getPatientName())
                        .gender(record.getGender())
                        .age(record.getAge())
                        .build())
                .chiefComplaint(record.getChiefComplaint())
                .presentIllness(record.getPresentIllness())
                .physicalExam(record.getPhysicalExam())
                .preliminaryDiagnosis(record.getPreliminaryDiagnosis())
                .icdCode(record.getIcdCode())
                .icdTitle(record.getIcdTitle())
                .icdConfidence(record.getIcdConfidence())
                .icdCodingStatus(record.getIcdCodingStatus())
                .icdCodingSource(record.getIcdCodingSource())
                .icdReviewedBy(record.getIcdReviewedBy())
                .icdReviewedAt(record.getIcdReviewedAt() == null ? null : record.getIcdReviewedAt().toString())
                .icdCandidates(candidates)
                .structuredMetrics(metrics)
                .medicalHistorySynced(record.getMedicalHistorySynced())
                .medplumSyncStatus(record.getMedplumSyncStatus())
                .medplumSyncMessage(record.getMedplumSyncMessage())
                .medplumSyncedAt(record.getMedplumSyncedAt() == null ? null : record.getMedplumSyncedAt().toString())
                .job(resolveLatestJob(record.getPatientId(), record.getId()))
                .createdAt(record.getCreatedAt() == null ? null : record.getCreatedAt().toString())
                .updatedAt(record.getUpdatedAt() == null ? null : record.getUpdatedAt().toString())
                .build();
    }

    private String buildSecureDownloadUrl(Long paperCaseId) {
        return paperCaseId == null ? null : "/api/paper-cases/" + paperCaseId + "/file";
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

    private String buildIdempotencyKey(Long patientId, String ext, byte[] fileBytes, LocalDate recordDate) {
        return "paper-case:" + patientId + ":" + firstNonBlank(ext, "unknown") + ":" +
                (recordDate == null ? "no-date" : recordDate.toString()) + ":" + sha256(fileBytes);
    }

    private boolean shouldCreateRetryForExistingJob(com.medical.agent.common.dto.ClinicalDataJobDTO existingJob) {
        if (existingJob == null) {
            return false;
        }
        String status = firstNonBlank(existingJob.getJobStatus(), "");
        if (ClinicalDataJobService.STATUS_FAILED.equalsIgnoreCase(status)
                || ClinicalDataJobService.STATUS_DEAD_LETTER.equalsIgnoreCase(status)
                || ClinicalDataJobService.STATUS_CANCELLED.equalsIgnoreCase(status)) {
            return true;
        }
        if (!ClinicalDataJobService.STATUS_RUNNING.equalsIgnoreCase(status)) {
            return false;
        }
        LocalDateTime referenceTime = parseDateTime(existingJob.getUpdatedAt());
        if (referenceTime == null) {
            referenceTime = parseDateTime(existingJob.getStartedAt());
        }
        return referenceTime != null && referenceTime.isBefore(LocalDateTime.now().minusMinutes(2));
    }

    private com.medical.agent.common.dto.ClinicalDataJobDTO resolveLatestJob(Long patientId, Long sourceRefId) {
        if (patientId == null || sourceRefId == null) {
            return null;
        }
        return clinicalDataJobService.toDTO(clinicalDataJobService.findLatestBySource(
                ClinicalDataJobService.SOURCE_KIND_PAPER_CASE_RECORD,
                sourceRefId
        ));
    }

    private void syncConfirmedPaperCaseJob(PaperCaseRecord record) {
        if (record == null || record.getId() == null) {
            return;
        }
        var job = clinicalDataJobService.findLatestBySource(
                ClinicalDataJobService.SOURCE_KIND_PAPER_CASE_RECORD,
                record.getId()
        );
        if (job == null || ClinicalDataJobService.STATUS_CANCELLED.equals(job.getJobStatus())) {
            return;
        }
        if (MedplumAutoSyncService.isSuccessfulStatus(record.getMedplumSyncStatus())) {
            clinicalDataJobService.markStage(
                    job.getId(),
                    ClinicalDataJobService.STATUS_SYNCED,
                    MedplumAutoSyncService.isSkippedStatus(record.getMedplumSyncStatus())
                            ? "manual_icd_confirmed_medplum_sync_skipped"
                            : "manual_icd_confirmed_medplum_synced",
                    Map.of(
                            "paperCaseRecordId", record.getId(),
                            "medplumSyncStatus", firstNonBlank(record.getMedplumSyncStatus(), "unknown"),
                            "icdCodingStatus", firstNonBlank(record.getIcdCodingStatus(), "unknown")
                    )
            );
        } else {
            clinicalDataJobService.markFailure(
                    job.getId(),
                    "manual_icd_confirmed_medplum_sync_failed",
                    firstNonBlank(record.getMedplumSyncMessage(), "人工确认 ICD 后 Medplum 同步失败")
            );
        }
    }

    private List<PaperCaseIcdCandidateDTO> readIcdCandidates(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readerForListOf(PaperCaseIcdCandidateDTO.class).readValue(json);
        } catch (IOException ex) {
            log.warn("[PaperCaseService] failed to parse icd candidates json: {}", ex.getMessage());
            return List.of();
        }
    }

    private HealthRecordDTO readMetrics(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, HealthRecordDTO.class);
        } catch (IOException ex) {
            log.warn("[PaperCaseService] failed to parse metrics json: {}", ex.getMessage());
            return null;
        }
    }

    private String writeJsonSafely(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new BusinessException(500, "序列化病历识别结果失败: " + ex.getMessage());
        }
    }

    private String sha256(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(bytes == null ? new byte[0] : bytes);
            StringBuilder builder = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                builder.append(String.format("%02x", b));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 算法不可用", ex);
        }
    }

    private String resolveExtension(String fileName) {
        if (fileName == null || !fileName.contains(".")) {
            throw new BusinessException(400, "文件缺少扩展名");
        }
        return fileName.substring(fileName.lastIndexOf('.')).toLowerCase();
    }

    private void ensureSupported(String ext) {
        if (!SUPPORTED_EXTENSIONS.contains(ext)) {
            throw new BusinessException(400, "仅支持 png/jpg/jpeg/pdf 病历文件");
        }
    }

    private String detectContentType(String contentType, String ext) {
        if (contentType != null && !contentType.isBlank()) {
            return contentType;
        }
        return switch (ext) {
            case ".pdf" -> "application/pdf";
            case ".png" -> "image/png";
            default -> "image/jpeg";
        };
    }

    private LocalDateTime parseDateTime(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(value);
        } catch (Exception ex) {
            return null;
        }
    }

    private void cleanupFailedUpload(Path target) {
        if (target == null) {
            return;
        }
        try {
            Files.deleteIfExists(target);
        } catch (IOException cleanupError) {
            log.warn("[PaperCaseService] failed to cleanup upload {} after processing error: {}",
                    target, cleanupError.getMessage());
        }
    }

    private void deletePaperCaseRecord(Long paperCaseRecordId) {
        if (paperCaseRecordId == null) {
            return;
        }
        try {
            paperCaseRecordRepository.deleteById(paperCaseRecordId);
        } catch (RuntimeException ex) {
            log.warn("[PaperCaseService] failed to delete duplicate paper case record {}: {}",
                    paperCaseRecordId, ex.getMessage());
        }
    }

    private boolean hasMeaningfulMetric(HealthRecordDTO dto) {
        return dto.getHeight() != null
                || dto.getWeight() != null
                || dto.getSystolicBp() != null
                || dto.getDiastolicBp() != null
                || dto.getRestingHeartRate() != null
                || dto.getFastingGlucose() != null
                || dto.getPostprandialGlucose() != null
                || dto.getHba1c() != null
                || dto.getBmi() != null
                || dto.getTotalCholesterol() != null
                || dto.getTriglycerides() != null
                || dto.getLdlCholesterol() != null
                || dto.getHdlCholesterol() != null
                || dto.getEgfr() != null
                || dto.getUacr() != null
                || dto.getSerumCreatinine() != null
                || dto.getHemoglobin() != null
                || dto.getWbc() != null
                || dto.getPlatelet() != null
                || dto.getNeutrophilPct() != null
                || dto.getAlt() != null
                || dto.getAst() != null
                || dto.getUricAcid() != null
                || dto.getCk() != null
                || dto.getPotassium() != null
                || hasText(dto.getUrineProtein())
                || hasText(dto.getUrineGlucose())
                || hasText(dto.getUrineBlood());
    }

    private boolean requiresManualCodingReview(PaperCaseIcdMatchResult icdMatch) {
        return !"auto_coded".equalsIgnoreCase(firstNonBlank(icdMatch == null ? null : icdMatch.getCodingStatus(), ""));
    }

    private void appendIfPresent(List<String> target, String label, String value) {
        if (hasText(value)) {
            target.add(label + "：" + value.trim());
        }
    }

    private String mergeText(String original, String appended) {
        if (!hasText(appended)) {
            return original;
        }
        if (!hasText(original)) {
            return appended.trim();
        }
        if (original.contains(appended.trim())) {
            return original;
        }
        return original.trim() + "\n" + appended.trim();
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private String firstNonBlank(String a, String b) {
        if (hasText(a)) {
            return a.trim();
        }
        return hasText(b) ? b.trim() : null;
    }

    private String buildCorrelationId(String prefix, Long patientId) {
        return prefix + "-" + patientId + "-" + UUID.randomUUID();
    }
}
