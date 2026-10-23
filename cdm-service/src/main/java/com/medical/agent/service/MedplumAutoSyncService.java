package com.medical.agent.service;

import com.medical.agent.common.dto.DeviceImportSummaryDTO;
import com.medical.agent.model.entity.PaperCaseRecord;
import com.medical.agent.model.repository.PaperCaseRecordRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;

@Service
public class MedplumAutoSyncService {

    private static final Logger log = LoggerFactory.getLogger(MedplumAutoSyncService.class);

    private final MedplumService medplumService;
    private final PaperCaseRecordRepository paperCaseRecordRepository;
    private final PlatformMetricsService platformMetricsService;

    @Autowired
    public MedplumAutoSyncService(MedplumService medplumService,
                                  PaperCaseRecordRepository paperCaseRecordRepository,
                                  PlatformMetricsService platformMetricsService) {
        this.medplumService = medplumService;
        this.paperCaseRecordRepository = paperCaseRecordRepository;
        this.platformMetricsService = platformMetricsService;
    }

    public MedplumAutoSyncService(MedplumService medplumService,
                                  PaperCaseRecordRepository paperCaseRecordRepository) {
        this(medplumService, paperCaseRecordRepository, null);
    }

    public DeviceImportSummaryDTO syncImportedHealthData(DeviceImportSummaryDTO summary, Long patientId) {
        if (summary == null || patientId == null) {
            return summary;
        }
        MedplumSyncOutcome outcome = syncPatientSafely(patientId, true, "device_import");
        summary.setMedplumSyncStatus(outcome.status());
        summary.setMedplumSyncMessage(outcome.message());
        return summary;
    }

    public PaperCaseRecord syncPaperCaseRecord(PaperCaseRecord record) {
        if (record == null || record.getPatientId() == null) {
            return record;
        }
        MedplumSyncOutcome outcome = syncPatientSafely(record.getPatientId(), true, "paper_case_record");
        record.setMedplumSyncStatus(outcome.status());
        record.setMedplumSyncMessage(outcome.message());
        record.setMedplumSyncedAt(outcome.syncedAt());
        return paperCaseRecordRepository.save(record);
    }

    public MedplumSyncOutcome replayPatientSync(Long patientId, boolean includeObservations) {
        return syncPatientSafely(patientId, includeObservations, "manual_replay");
    }

    private MedplumSyncOutcome syncPatientSafely(Long patientId, boolean includeObservations, String sourceKind) {
        long startedAt = System.nanoTime();
        if (!medplumService.isAutoSyncReady()) {
            MedplumSyncOutcome outcome = new MedplumSyncOutcome("skipped", "Medplum 未启用或未完成配置，已跳过自动同步。", null);
            recordMetric(sourceKind, outcome, startedAt);
            return outcome;
        }
        try {
            Map<String, Object> result = medplumService.syncPatient(patientId, includeObservations);
            String message = String.valueOf(result.getOrDefault("message", "已自动同步到 Medplum。"));
            MedplumSyncOutcome outcome = new MedplumSyncOutcome("synced", message, LocalDateTime.now());
            recordMetric(sourceKind, outcome, startedAt);
            return outcome;
        } catch (RuntimeException ex) {
            log.warn("[MedplumAutoSyncService] auto sync failed for patient {}: {}", patientId, ex.getMessage());
            MedplumSyncOutcome outcome = new MedplumSyncOutcome("failed", ex.getMessage(), null);
            recordMetric(sourceKind, outcome, startedAt);
            return outcome;
        }
    }

    private void recordMetric(String sourceKind, MedplumSyncOutcome outcome, long startedAt) {
        if (platformMetricsService == null) {
            return;
        }
        long elapsedMillis = Math.max(0L, (System.nanoTime() - startedAt) / 1_000_000L);
        platformMetricsService.recordMedplumAutoSync(sourceKind, outcome.status(), elapsedMillis);
    }

    public static boolean isSuccessfulStatus(String status) {
        return isSyncedStatus(status) || isSkippedStatus(status);
    }

    public static boolean isSyncedStatus(String status) {
        return "synced".equalsIgnoreCase(status);
    }

    public static boolean isSkippedStatus(String status) {
        return "skipped".equalsIgnoreCase(status);
    }

    public record MedplumSyncOutcome(String status,
                                     String message,
                                     LocalDateTime syncedAt) {
        public boolean successful() {
            return isSuccessfulStatus(status);
        }

        public boolean synced() {
            return isSyncedStatus(status);
        }

        public boolean skipped() {
            return isSkippedStatus(status);
        }
    }
}
