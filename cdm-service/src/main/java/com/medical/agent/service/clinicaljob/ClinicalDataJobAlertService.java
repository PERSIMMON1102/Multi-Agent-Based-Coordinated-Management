package com.medical.agent.service.clinicaljob;

import com.medical.agent.common.dto.NotificationDTO;
import com.medical.agent.model.entity.ClinicalDataJob;
import com.medical.agent.model.entity.Patient;
import com.medical.agent.model.repository.PatientRepository;
import com.medical.agent.service.ClinicalNotificationGateway;
import com.medical.agent.service.PlatformMetricsService;
import com.medical.agent.service.security.PrivacyLogSanitizer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.LongSupplier;

@Service
public class ClinicalDataJobAlertService {
    static final String ALERT_TYPE_DEAD_LETTER = "CLINICAL_JOB_DEAD_LETTER";
    static final String ALERT_TYPE_RUNNING_TIMEOUT = "CLINICAL_JOB_RUNNING_TIMEOUT";
    private static final long DEFAULT_ALERT_DEDUPE_WINDOW_MS = 5 * 60 * 1000L;

    private final ClinicalNotificationGateway clinicalNotificationGateway;
    private final PatientRepository patientRepository;
    private final PlatformMetricsService platformMetricsService;
    private final LongSupplier timeSupplier;
    private final long alertDedupeWindowMs;
    private final PrivacyLogSanitizer privacyLogSanitizer = new PrivacyLogSanitizer();
    private final ConcurrentMap<String, Long> recentAlerts = new ConcurrentHashMap<>();

    @Autowired
    public ClinicalDataJobAlertService(ClinicalNotificationGateway clinicalNotificationGateway,
                                       PatientRepository patientRepository,
                                       PlatformMetricsService platformMetricsService,
                                       @Value("${clinical-data-job.alert-dedupe-window-ms:300000}") long alertDedupeWindowMs) {
        this(clinicalNotificationGateway, patientRepository, platformMetricsService, System::currentTimeMillis, alertDedupeWindowMs);
    }

    ClinicalDataJobAlertService(ClinicalNotificationGateway clinicalNotificationGateway,
                                PatientRepository patientRepository) {
        this(clinicalNotificationGateway, patientRepository, null, System::currentTimeMillis, DEFAULT_ALERT_DEDUPE_WINDOW_MS);
    }

    ClinicalDataJobAlertService(ClinicalNotificationGateway clinicalNotificationGateway,
                                PatientRepository patientRepository,
                                PlatformMetricsService platformMetricsService,
                                LongSupplier timeSupplier,
                                long alertDedupeWindowMs) {
        this.clinicalNotificationGateway = clinicalNotificationGateway;
        this.patientRepository = patientRepository;
        this.platformMetricsService = platformMetricsService;
        this.timeSupplier = timeSupplier == null ? System::currentTimeMillis : timeSupplier;
        this.alertDedupeWindowMs = alertDedupeWindowMs > 0 ? alertDedupeWindowMs : DEFAULT_ALERT_DEDUPE_WINDOW_MS;
    }

    public void notifyDeadLetter(ClinicalDataJob job) {
        if (job == null || job.getId() == null) {
            return;
        }
        if (!shouldEmitAlert(ALERT_TYPE_DEAD_LETTER, job)) {
            recordAlertMetric(ALERT_TYPE_DEAD_LETTER, "suppressed", job);
            return;
        }
        String patientName = resolvePatientName(job);
        String stage = resolveStage(job);
        String jobType = resolveJobType(job);
        String errorSummary = summarizeError(job);

        StringBuilder message = new StringBuilder()
                .append("患者")
                .append(patientName)
                .append("(ID:")
                .append(job.getPatientId())
                .append(")")
                .append(" 的临床数据作业 ")
                .append(jobType)
                .append(" 在阶段 ")
                .append(stage)
                .append(" 达到最大重试次数，已进入死信队列。");
        if (!errorSummary.isBlank()) {
            message.append(" 错误摘要: ").append(errorSummary);
        }

        clinicalNotificationGateway.sendNotification(NotificationDTO.builder()
                .type(ALERT_TYPE_DEAD_LETTER)
                .patientId(null)
                .title("临床数据作业进入死信队列")
                .message(message.toString())
                .payload(buildPayload(job, patientName, errorSummary))
                .timestamp(System.currentTimeMillis())
                .build());
        recordAlertMetric(ALERT_TYPE_DEAD_LETTER, "sent", job);
    }

    public void notifyRunningTimeout(ClinicalDataJob job) {
        if (job == null || job.getId() == null) {
            return;
        }
        if (!shouldEmitAlert(ALERT_TYPE_RUNNING_TIMEOUT, job)) {
            recordAlertMetric(ALERT_TYPE_RUNNING_TIMEOUT, "suppressed", job);
            return;
        }
        String patientName = resolvePatientName(job);
        String stage = resolveStage(job);
        String jobType = resolveJobType(job);
        String errorSummary = summarizeError(job);

        StringBuilder message = new StringBuilder()
                .append("患者")
                .append(patientName)
                .append("(ID:")
                .append(job.getPatientId())
                .append(")")
                .append(" 的临床数据作业 ")
                .append(jobType)
                .append(" 在阶段 ")
                .append(stage)
                .append(" 因执行超时被回收为失败状态，等待后续自动重试。");
        if (!errorSummary.isBlank()) {
            message.append(" 错误摘要: ").append(errorSummary);
        }

        clinicalNotificationGateway.sendNotification(NotificationDTO.builder()
                .type(ALERT_TYPE_RUNNING_TIMEOUT)
                .patientId(null)
                .title("临床数据作业执行超时")
                .message(message.toString())
                .payload(buildPayload(job, patientName, errorSummary))
                .timestamp(System.currentTimeMillis())
                .build());
        recordAlertMetric(ALERT_TYPE_RUNNING_TIMEOUT, "sent", job);
    }

    private Map<String, Object> buildPayload(ClinicalDataJob job, String patientName, String errorSummary) {
        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        payload.put("jobId", job.getId());
        payload.put("jobType", job.getJobType());
        payload.put("patientId", job.getPatientId());
        payload.put("patientName", patientName);
        payload.put("sourceKind", job.getSourceKind());
        payload.put("sourceRefId", job.getSourceRefId());
        payload.put("correlationId", job.getCorrelationId());
        payload.put("idempotencyKey", job.getIdempotencyKey());
        payload.put("lastStage", job.getLastStage());
        payload.put("vendor", job.getVendor());
        payload.put("retryCount", job.getRetryCount());
        payload.put("maxRetries", job.getMaxRetries());
        if (job.getSourceFileName() != null && !job.getSourceFileName().isBlank()) {
            payload.put("sourceFileName", privacyLogSanitizer.summarizeText(job.getSourceFileName()));
        }
        if (!errorSummary.isBlank()) {
            payload.put("lastErrorSummary", errorSummary);
        }
        return payload;
    }

    private String blankAsFallback(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private String resolvePatientName(ClinicalDataJob job) {
        return patientRepository.findById(job.getPatientId())
                .map(Patient::getName)
                .filter(name -> name != null && !name.isBlank())
                .orElse("未知患者");
    }

    private String resolveStage(ClinicalDataJob job) {
        return blankAsFallback(job.getLastStage(), "unknown");
    }

    private String resolveJobType(ClinicalDataJob job) {
        return blankAsFallback(job.getJobType(), "unknown");
    }

    private String summarizeError(ClinicalDataJob job) {
        return privacyLogSanitizer.summarizeText(job.getLastError());
    }

    private boolean shouldEmitAlert(String alertType, ClinicalDataJob job) {
        long now = timeSupplier.getAsLong();
        String key = buildAlertKey(alertType, job);
        Long previous = recentAlerts.putIfAbsent(key, now);
        if (previous == null) {
            return true;
        }
        if (now - previous < alertDedupeWindowMs) {
            return false;
        }
        recentAlerts.put(key, now);
        return true;
    }

    private String buildAlertKey(String alertType, ClinicalDataJob job) {
        String correlationIdentity = firstNonBlank(
                job.getCorrelationId(),
                job.getIdempotencyKey(),
                job.getSourceKind() + ":" + job.getSourceRefId(),
                String.valueOf(job.getId())
        );
        return alertType
                + "|jobType=" + resolveJobType(job)
                + "|stage=" + resolveStage(job)
                + "|identity=" + correlationIdentity;
    }

    private void recordAlertMetric(String type, String outcome, ClinicalDataJob job) {
        if (platformMetricsService != null) {
            platformMetricsService.recordClinicalJobAlert(type, outcome, job);
        }
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return "unknown";
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return "unknown";
    }
}
