package com.medical.agent.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.medical.agent.common.dto.RagEvidenceDTO;
import com.medical.agent.common.dto.VideoResourceDTO;
import com.medical.agent.model.entity.HealthReport;
import com.medical.agent.model.entity.HealthReportReferenceSnapshot;
import com.medical.agent.model.entity.HealthReportVideoSnapshot;
import com.medical.agent.model.repository.HealthReportReferenceSnapshotRepository;
import com.medical.agent.model.repository.HealthReportRepository;
import com.medical.agent.model.repository.HealthReportVideoSnapshotRepository;
import com.medical.agent.service.report.agent.HealthReportSupervisorService;
import com.medical.agent.service.report.agent.ReportGenerationState;
import com.medical.agent.service.testsupport.RepositoryProxyFactory;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HealthReportServiceTest {

    @Test
    void shouldPersistAndReadBackAgentTraceReferencesAndVideos() {
        AtomicLong idGenerator = new AtomicLong(100);
        List<HealthReport> savedReports = new ArrayList<>();
        List<HealthReportReferenceSnapshot> savedReferenceSnapshots = new ArrayList<>();
        List<HealthReportVideoSnapshot> savedVideoSnapshots = new ArrayList<>();

        HealthReportRepository reportRepository = RepositoryProxyFactory.create(HealthReportRepository.class, Map.of(
                "save", args -> {
                    HealthReport report = (HealthReport) args[0];
                    report.setId(idGenerator.incrementAndGet());
                    report.setGeneratedAt(LocalDateTime.of(2026, 4, 11, 10, 0));
                    savedReports.add(report);
                    return report;
                },
                "findByPatientIdOrderByGeneratedAtDesc", args -> savedReports,
                "findByPatientIdAndTypeOrderByGeneratedAtDesc", args -> savedReports.stream()
                        .filter(report -> args[1].equals(report.getType()))
                        .toList()
        ));
        HealthReportReferenceSnapshotRepository referenceRepository = RepositoryProxyFactory.create(HealthReportReferenceSnapshotRepository.class, Map.of(
                "saveAll", args -> {
                    @SuppressWarnings("unchecked")
                    List<HealthReportReferenceSnapshot> snapshots = (List<HealthReportReferenceSnapshot>) args[0];
                    savedReferenceSnapshots.addAll(snapshots);
                    return snapshots;
                },
                "findByReportIdOrderBySortOrderAscIdAsc", args -> savedReferenceSnapshots.stream()
                        .filter(snapshot -> snapshot.getReportId().equals(args[0]))
                        .toList()
        ));
        HealthReportVideoSnapshotRepository videoRepository = RepositoryProxyFactory.create(HealthReportVideoSnapshotRepository.class, Map.of(
                "saveAll", args -> {
                    @SuppressWarnings("unchecked")
                    List<HealthReportVideoSnapshot> snapshots = (List<HealthReportVideoSnapshot>) args[0];
                    savedVideoSnapshots.addAll(snapshots);
                    return snapshots;
                },
                "findByReportIdOrderBySortOrderAscIdAsc", args -> savedVideoSnapshots.stream()
                        .filter(snapshot -> snapshot.getReportId().equals(args[0]))
                        .toList()
        ));

        HealthReportService service = new HealthReportService(
                null,
                null,
                null,
                null,
                reportRepository,
                videoRepository,
                referenceRepository,
                new StubVideoResourceService(),
                new StubHealthReportSupervisorService(),
                new ObjectMapper()
        );

        Map<String, Object> generated = service.generateDoctorAssessment(1L);
        List<Map<String, Object>> listed = service.listByPatient(1L, HealthReportService.TYPE_DOCTOR_ASSESSMENT);

        assertEquals("专业评估报告", generated.get("title"));
        assertEquals(2, ((List<?>) generated.get("agentSteps")).size());
        assertEquals(1, savedReferenceSnapshots.size());
        assertEquals(1, savedVideoSnapshots.size());
        assertEquals(1, listed.size());
        assertEquals("专业评估报告", listed.get(0).get("title"));
        assertFalse(((List<?>) listed.get(0).get("agentSteps")).isEmpty());
        assertEquals(1, ((List<?>) listed.get(0).get("references")).size());
        assertEquals(1, ((List<?>) listed.get(0).get("recommendedVideos")).size());
    }

    private static class StubHealthReportSupervisorService extends HealthReportSupervisorService {
        StubHealthReportSupervisorService() {
            super(null, null, null, null, null, List.of());
        }

        @Override
        public ReportGenerationState generate(Long patientId, String type) {
            ReportGenerationState state = new ReportGenerationState();
            state.setPatientId(patientId);
            state.setType(type);
            state.setTitle("专业评估报告");
            state.setContent("报告正文");
            state.setDisplayReferences(List.of(RagEvidenceDTO.builder()
                    .sourceType("guideline")
                    .refId(1L)
                    .title("指南A")
                    .source("国家指南")
                    .publishedAt("2025")
                    .contentSnippet("摘要")
                    .build()));
            state.addStep("report_context_agent", "已准备上下文");
            state.addStep("report_drafting_agent", "已完成正文");
            return state;
        }
    }

    private static class StubVideoResourceService extends VideoResourceService {
        StubVideoResourceService() {
            super(null, null, null, null, null, null);
        }

        @Override
        public List<VideoResourceDTO> getRecommendedVideos(Long patientId, Integer limit) {
            return List.of(VideoResourceDTO.builder()
                    .id(7L)
                    .title("视频A")
                    .videoUrl("https://example.com/video-a")
                    .recommendReason("适合高风险患者")
                    .build());
        }

        @Override
        public HealthReportVideoSnapshot toSnapshotEntity(Long reportId, VideoResourceDTO video, int sortOrder) {
            return HealthReportVideoSnapshot.builder()
                    .reportId(reportId)
                    .videoId(video.getId())
                    .title(video.getTitle())
                    .videoUrl(video.getVideoUrl())
                    .recommendReason(video.getRecommendReason())
                    .sortOrder(sortOrder)
                    .build();
        }

        @Override
        public VideoResourceDTO toDTO(HealthReportVideoSnapshot snapshot) {
            return VideoResourceDTO.builder()
                    .id(snapshot.getVideoId())
                    .title(snapshot.getTitle())
                    .videoUrl(snapshot.getVideoUrl())
                    .recommendReason(snapshot.getRecommendReason())
                    .build();
        }
    }
}
