package com.medical.agent.service;

import com.medical.agent.common.dto.PaperCaseAccessAuditSummaryDTO;
import com.medical.agent.model.mongo.PaperCaseAccessLog;
import com.medical.agent.model.mongo.repository.PaperCaseAccessLogRepository;
import com.medical.agent.service.testsupport.RepositoryProxyFactory;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PaperCaseAccessAuditServiceTest {

    @Test
    void shouldAggregateAuditSummaryForPaperCaseFilter() {
        List<PaperCaseAccessLog> logs = List.of(
                log("1", 21L, 9L, 7L, "doctor", "download", "success", "paper_case_file", "下载病历原件", "detail", LocalDateTime.parse("2026-05-29T20:10:00")),
                log("2", 21L, 9L, 1L, "admin", "download", "success", "paper_case_file", "下载病历原件", "detail", LocalDateTime.parse("2026-05-29T20:05:00")),
                log("3", 21L, 9L, 7L, "doctor", "download", "denied", "paper_case_file", "下载病历原件", "detail", LocalDateTime.parse("2026-05-29T20:00:00"))
        );
        PaperCaseAccessLogRepository repository = RepositoryProxyFactory.create(PaperCaseAccessLogRepository.class, Map.of(
                "findByPaperCaseIdOrderByTimestampDesc", args -> logs
        ));
        PaperCaseAccessAuditService service = new PaperCaseAccessAuditService(repository);

        PaperCaseAccessAuditSummaryDTO summary = service.getAuditSummary(9L, 21L, 20);

        assertEquals(3L, summary.getTotalEntries());
        assertEquals(3L, summary.getActionCounts().get("download"));
        assertEquals(2L, summary.getOutcomeCounts().get("success"));
        assertEquals(2L, summary.getActorRoleCounts().get("doctor"));
        assertEquals("2026-05-29T20:10", summary.getEntries().get(0).getTimestamp());
    }

    @Test
    void shouldLoadGlobalRecentEntriesWhenNoFilterProvided() {
        List<PaperCaseAccessLog> logs = List.of(
                log("4", 22L, 10L, 99L, "admin", "download", "success", "paper_case_file", "下载病历原件", "detail", LocalDateTime.parse("2026-05-29T21:00:00"))
        );
        PaperCaseAccessLogRepository repository = RepositoryProxyFactory.create(PaperCaseAccessLogRepository.class, Map.of(
                "findAllByOrderByTimestampDesc", args -> logs
        ));
        PaperCaseAccessAuditService service = new PaperCaseAccessAuditService(repository);

        PaperCaseAccessAuditSummaryDTO summary = service.getAuditSummary(null, null, 10);

        assertEquals(1L, summary.getTotalEntries());
        assertEquals(22L, summary.getEntries().get(0).getPaperCaseId());
        assertEquals(1L, summary.getResourceTypeCounts().get("paper_case_file"));
    }

    private PaperCaseAccessLog log(String id,
                                   Long paperCaseId,
                                   Long patientId,
                                   Long actorUserId,
                                   String actorRole,
                                   String action,
                                   String outcome,
                                   String resourceType,
                                   String targetSummary,
                                   String detailSummary,
                                   LocalDateTime timestamp) {
        return PaperCaseAccessLog.builder()
                .id(id)
                .paperCaseId(paperCaseId)
                .patientId(patientId)
                .actorUserId(actorUserId)
                .actorRole(actorRole)
                .action(action)
                .outcome(outcome)
                .resourceType(resourceType)
                .requestIp("127.0.0.1")
                .userAgent("JUnit")
                .targetSummary(targetSummary)
                .detailSummary(detailSummary)
                .timestamp(timestamp)
                .build();
    }
}
