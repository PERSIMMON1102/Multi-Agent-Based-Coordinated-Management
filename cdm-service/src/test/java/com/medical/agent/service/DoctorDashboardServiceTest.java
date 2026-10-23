package com.medical.agent.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.medical.agent.model.entity.ClinicalDataJob;
import com.medical.agent.model.entity.Patient;
import com.medical.agent.model.entity.PaperCaseRecord;
import com.medical.agent.model.repository.AiChatMessageRepository;
import com.medical.agent.model.repository.AiChatSessionRepository;
import com.medical.agent.model.repository.ClinicalDataJobRepository;
import com.medical.agent.model.repository.FollowUpPlanRepository;
import com.medical.agent.model.repository.HealthReportRepository;
import com.medical.agent.model.repository.HealthReportVideoSnapshotRepository;
import com.medical.agent.model.repository.NotificationRepository;
import com.medical.agent.model.repository.PaperCaseRecordRepository;
import com.medical.agent.model.repository.PatientRepository;
import com.medical.agent.model.repository.RiskAssessmentRepository;
import com.medical.agent.model.repository.VideoResourceRepository;
import com.medical.agent.model.repository.VideoWatchSessionRepository;
import com.medical.agent.service.clinicaljob.ClinicalDataJobService;
import com.medical.agent.service.testsupport.RepositoryProxyFactory;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DoctorDashboardServiceTest {

    @Test
    void shouldExposePaperCaseOperationalSummaryOnDashboard() {
        PatientRepository patientRepository = RepositoryProxyFactory.create(PatientRepository.class, Map.of(
                "findByAssignedDoctorId", args -> List.of(
                        Patient.builder().id(1L).name("患者甲").assignedDoctorId(9L).build(),
                        Patient.builder().id(2L).name("患者乙").assignedDoctorId(9L).build()
                )
        ));
        PaperCaseRecordRepository paperCaseRecordRepository = RepositoryProxyFactory.create(PaperCaseRecordRepository.class, Map.of(
                "findByPatientIdInOrderByCreatedAtDesc", args -> List.of(
                        PaperCaseRecord.builder()
                                .id(101L)
                                .patientId(1L)
                                .recordDate(LocalDate.of(2026, 5, 29))
                                .originalFileName("case-a.pdf")
                                .preliminaryDiagnosis("胃食管反流病")
                                .icdCodingStatus("pending_review")
                                .medplumSyncStatus("review_pending")
                                .createdAt(LocalDateTime.of(2026, 5, 29, 9, 0))
                                .build(),
                        PaperCaseRecord.builder()
                                .id(102L)
                                .patientId(2L)
                                .recordDate(LocalDate.of(2026, 5, 28))
                                .originalFileName("case-b.pdf")
                                .preliminaryDiagnosis("高血压")
                                .icdCodingStatus("auto_coded")
                                .medplumSyncStatus("failed")
                                .createdAt(LocalDateTime.of(2026, 5, 29, 8, 0))
                                .build()
                )
        ));
        ClinicalDataJobRepository clinicalDataJobRepository = RepositoryProxyFactory.create(ClinicalDataJobRepository.class, Map.of(
                "findBySourceKindAndSourceRefIdInOrderByCreatedAtDesc", args -> List.of(
                        ClinicalDataJob.builder()
                                .sourceKind(ClinicalDataJobService.SOURCE_KIND_PAPER_CASE_RECORD)
                                .sourceRefId(101L)
                                .jobStatus(ClinicalDataJobService.STATUS_REVIEW_PENDING)
                                .createdAt(LocalDateTime.of(2026, 5, 29, 9, 10))
                                .build(),
                        ClinicalDataJob.builder()
                                .sourceKind(ClinicalDataJobService.SOURCE_KIND_PAPER_CASE_RECORD)
                                .sourceRefId(102L)
                                .jobStatus(ClinicalDataJobService.STATUS_FAILED)
                                .createdAt(LocalDateTime.of(2026, 5, 29, 8, 10))
                                .build()
                )
        ));

        DoctorDashboardService service = new DoctorDashboardService(
                patientRepository,
                RepositoryProxyFactory.create(RiskAssessmentRepository.class, Map.of()),
                RepositoryProxyFactory.create(FollowUpPlanRepository.class, Map.of()),
                RepositoryProxyFactory.create(AiChatSessionRepository.class, Map.of()),
                RepositoryProxyFactory.create(AiChatMessageRepository.class, Map.of()),
                RepositoryProxyFactory.create(HealthReportRepository.class, Map.of()),
                RepositoryProxyFactory.create(HealthReportVideoSnapshotRepository.class, Map.of()),
                RepositoryProxyFactory.create(NotificationRepository.class, Map.of(
                        "findTop50ByOrderByCreatedAtDesc", args -> List.of()
                )),
                RepositoryProxyFactory.create(VideoResourceRepository.class, Map.of(
                        "countByActiveTrue", args -> 0L,
                        "countByActiveTrueAndHasTranscriptTrue", args -> 0L
                )),
                RepositoryProxyFactory.create(VideoWatchSessionRepository.class, Map.of()),
                paperCaseRecordRepository,
                clinicalDataJobRepository,
                new ObjectMapper()
        );

        Map<String, Object> overview = service.getOverview(9L);

        @SuppressWarnings("unchecked")
        Map<String, Object> kpis = (Map<String, Object>) overview.get("kpis");
        @SuppressWarnings("unchecked")
        Map<String, Object> paperCaseOps = (Map<String, Object>) overview.get("paperCaseOps");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> recentAttentionPatients = (List<Map<String, Object>>) paperCaseOps.get("recentAttentionPatients");

        assertEquals(2L, kpis.get("paperCaseAttentionPatients"));
        assertEquals(1L, kpis.get("paperCasePendingReviews"));
        assertEquals(1L, kpis.get("paperCaseFailedSyncs"));
        assertEquals(2L, paperCaseOps.get("attentionCount"));
        assertEquals(2L, paperCaseOps.get("attentionPatientCount"));
        assertEquals(2, recentAttentionPatients.size());
    }
}
