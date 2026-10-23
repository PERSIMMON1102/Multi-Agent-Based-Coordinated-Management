package com.medical.agent.service;

import com.medical.agent.common.dto.PaperCaseAccessAuditSummaryDTO;
import com.medical.agent.common.dto.PaperCaseAccessLogDTO;
import com.medical.agent.model.mongo.PaperCaseAccessLog;
import com.medical.agent.model.mongo.repository.PaperCaseAccessLogRepository;
import com.medical.agent.service.security.PrivacyLogSanitizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class PaperCaseAccessAuditService {

    private static final Logger log = LoggerFactory.getLogger(PaperCaseAccessAuditService.class);

    private final PaperCaseAccessLogRepository repository;
    private final PrivacyLogSanitizer sanitizer;

    public PaperCaseAccessAuditService(PaperCaseAccessLogRepository repository) {
        this.repository = repository;
        this.sanitizer = new PrivacyLogSanitizer();
    }

    public void record(Long paperCaseId,
                       Long patientId,
                       Long actorUserId,
                       String actorRole,
                       String action,
                       String outcome,
                       String resourceType,
                       String requestIp,
                       String userAgent,
                       String targetSummary,
                       String detailSummary,
                       Map<String, Object> metadata) {
        try {
            PaperCaseAccessLog entry = PaperCaseAccessLog.builder()
                    .paperCaseId(paperCaseId)
                    .patientId(patientId)
                    .actorUserId(actorUserId)
                    .actorRole(normalize(actorRole))
                    .action(normalize(action))
                    .outcome(normalize(outcome))
                    .resourceType(normalize(resourceType))
                    .requestIp(normalize(requestIp))
                    .userAgent(sanitizer.summarizeText(userAgent))
                    .targetSummary(sanitizer.summarizeText(targetSummary))
                    .detailSummary(sanitizer.summarizeText(detailSummary))
                    .timestamp(LocalDateTime.now())
                    .build();
            repository.save(entry);
            if (metadata != null && !metadata.isEmpty()) {
                log.info("[PaperCaseAccessAudit] action={}, outcome={}, paperCaseId={}, patientId={}, actorRole={}, actorUserId={}, summary={}, metadata={}",
                        entry.getAction(),
                        entry.getOutcome(),
                        entry.getPaperCaseId(),
                        entry.getPatientId(),
                        entry.getActorRole(),
                        entry.getActorUserId(),
                        entry.getTargetSummary(),
                        sanitizer.summarizeMap(metadata));
            } else {
                log.info("[PaperCaseAccessAudit] action={}, outcome={}, paperCaseId={}, patientId={}, actorRole={}, actorUserId={}, summary={}",
                        entry.getAction(),
                        entry.getOutcome(),
                        entry.getPaperCaseId(),
                        entry.getPatientId(),
                        entry.getActorRole(),
                        entry.getActorUserId(),
                        entry.getTargetSummary());
            }
        } catch (Exception ex) {
            log.warn("[PaperCaseAccessAudit] failed to persist paper-case access audit: {}", ex.getMessage());
        }
    }

    public PaperCaseAccessAuditSummaryDTO getAuditSummary(Long patientId, Long paperCaseId, Integer limit) {
        List<PaperCaseAccessLogDTO> entries = loadEntries(patientId, paperCaseId, limit).stream()
                .map(this::toDTO)
                .toList();
        return PaperCaseAccessAuditSummaryDTO.builder()
                .totalEntries((long) entries.size())
                .actionCounts(buildCounts(entries, PaperCaseAccessLogDTO::getAction))
                .outcomeCounts(buildCounts(entries, PaperCaseAccessLogDTO::getOutcome))
                .actorRoleCounts(buildCounts(entries, PaperCaseAccessLogDTO::getActorRole))
                .resourceTypeCounts(buildCounts(entries, PaperCaseAccessLogDTO::getResourceType))
                .entries(entries)
                .build();
    }

    public List<PaperCaseAccessLogDTO> listEntries(Long patientId, Long paperCaseId, Integer limit) {
        return loadEntries(patientId, paperCaseId, limit).stream()
                .map(this::toDTO)
                .toList();
    }

    private List<PaperCaseAccessLog> loadEntries(Long patientId, Long paperCaseId, Integer limit) {
        int normalizedLimit = normalizeLimit(limit);
        PageRequest pageRequest = PageRequest.of(0, normalizedLimit);
        if (repository == null) {
            return List.of();
        }
        if (paperCaseId != null) {
            List<PaperCaseAccessLog> caseLogs = repository.findByPaperCaseIdOrderByTimestampDesc(paperCaseId, pageRequest);
            if (patientId == null) {
                return caseLogs;
            }
            return caseLogs.stream()
                    .filter(entry -> patientId.equals(entry.getPatientId()))
                    .toList();
        }
        if (patientId != null) {
            return repository.findByPatientIdOrderByTimestampDesc(patientId, pageRequest);
        }
        return repository.findAllByOrderByTimestampDesc(pageRequest);
    }

    private PaperCaseAccessLogDTO toDTO(PaperCaseAccessLog entry) {
        return PaperCaseAccessLogDTO.builder()
                .id(entry.getId())
                .paperCaseId(entry.getPaperCaseId())
                .patientId(entry.getPatientId())
                .actorUserId(entry.getActorUserId())
                .actorRole(normalize(entry.getActorRole()))
                .action(normalize(entry.getAction()))
                .outcome(normalize(entry.getOutcome()))
                .resourceType(normalize(entry.getResourceType()))
                .requestIp(normalize(entry.getRequestIp()))
                .userAgent(sanitizer.summarizeText(entry.getUserAgent()))
                .targetSummary(sanitizer.summarizeText(entry.getTargetSummary()))
                .detailSummary(sanitizer.summarizeText(entry.getDetailSummary()))
                .timestamp(entry.getTimestamp() == null ? null : entry.getTimestamp().toString())
                .build();
    }

    private Map<String, Long> buildCounts(List<PaperCaseAccessLogDTO> entries,
                                          Function<PaperCaseAccessLogDTO, String> classifier) {
        if (entries == null || entries.isEmpty()) {
            return Map.of();
        }
        return entries.stream()
                .map(classifier)
                .map(this::blankAsUnknown)
                .collect(Collectors.groupingBy(Function.identity(), LinkedHashMap::new, Collectors.counting()))
                .entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue(Comparator.reverseOrder()).thenComparing(Map.Entry::getKey))
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
    }

    private int normalizeLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return 50;
        }
        return Math.min(limit, 200);
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? "" : value.trim();
    }

    private String blankAsUnknown(String value) {
        return value == null || value.isBlank() ? "unknown" : value.trim();
    }
}
