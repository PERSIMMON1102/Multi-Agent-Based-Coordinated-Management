package com.medical.agent.web.controller;

import com.medical.agent.common.dto.PaperCaseAccessAuditSummaryDTO;
import com.medical.agent.common.dto.PaperCasePatientSummaryDTO;
import com.medical.agent.common.exception.BusinessException;
import com.medical.agent.common.dto.PaperCaseRecordDTO;
import com.medical.agent.service.PaperCaseAccessAuditService;
import com.medical.agent.service.PaperCaseService;
import com.medical.agent.service.security.PaperCaseFileAccess;
import com.medical.agent.web.security.AuthenticatedUser;
import com.medical.agent.web.security.AccessGuard;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PaperCaseControllerTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldDownloadPaperCaseFileThroughGuardedEndpoint() throws Exception {
        Path file = tempDir.resolve("case.pdf");
        Files.writeString(file, "demo");

        AtomicReference<Long> guardedPatientId = new AtomicReference<>();
        AtomicReference<Map<String, Object>> auditPayload = new AtomicReference<>();
        StubAccessGuard accessGuard = new StubAccessGuard(guardedPatientId);
        PaperCaseController controller = new PaperCaseController(
                new StubPaperCaseService(file),
                accessGuard,
                new StubPaperCaseAccessAuditService(auditPayload)
        );

        ResponseEntity<Resource> response = controller.downloadPaperCaseFile(9L, request("127.0.0.1", "JUnit-Agent"));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("application/pdf", response.getHeaders().getContentType().toString());
        assertNotNull(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION));
        assertEquals(12L, guardedPatientId.get());
        assertEquals("download", auditPayload.get().get("action"));
        assertEquals("success", auditPayload.get().get("outcome"));
        assertEquals(9L, auditPayload.get().get("paperCaseId"));
    }

    @Test
    void shouldListPaperCaseRecordsAfterPatientGuard() {
        AtomicReference<Long> guardedPatientId = new AtomicReference<>();
        StubAccessGuard accessGuard = new StubAccessGuard(guardedPatientId);
        PaperCaseController controller = new PaperCaseController(
                new StubPaperCaseService(null),
                accessGuard,
                new StubPaperCaseAccessAuditService(new AtomicReference<>())
        );

        ResponseEntity<List<PaperCaseRecordDTO>> response = controller.getByPatientId(12L);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(1, response.getBody().size());
        assertEquals(12L, guardedPatientId.get());
    }

    @Test
    void shouldReturnPendingReviewPaperCasesForAdminReviewQueue() {
        AtomicReference<Long> guardedPatientId = new AtomicReference<>();
        StubAccessGuard accessGuard = new StubAccessGuard(guardedPatientId);
        accessGuard.currentUser = new AuthenticatedUser(1L, "admin", "admin");
        StubPaperCaseService paperCaseService = new StubPaperCaseService(null);
        PaperCaseController controller = new PaperCaseController(
                paperCaseService,
                accessGuard,
                new StubPaperCaseAccessAuditService(new AtomicReference<>())
        );

        ResponseEntity<List<PaperCaseRecordDTO>> response = controller.getPendingReviewRecords();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(1, response.getBody().size());
        assertEquals(99L, response.getBody().get(0).getId());
        assertEquals("admin-pending-review", paperCaseService.lastScope);
    }

    @Test
    void shouldReturnPatientScopedPaperCaseSummaryForDoctor() {
        AtomicReference<Long> guardedPatientId = new AtomicReference<>();
        StubAccessGuard accessGuard = new StubAccessGuard(guardedPatientId);
        PaperCaseController controller = new PaperCaseController(
                new StubPaperCaseService(null),
                accessGuard,
                new StubPaperCaseAccessAuditService(new AtomicReference<>())
        );

        ResponseEntity<PaperCasePatientSummaryDTO> response = controller.getPatientSummary(12L);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(12L, response.getBody().getPatientId());
        assertEquals(1L, response.getBody().getPendingReviewCount());
        assertEquals(12L, guardedPatientId.get());
    }

    @Test
    void shouldReturnFailedSyncPaperCasesForRecoveryQueue() {
        AtomicReference<Long> guardedPatientId = new AtomicReference<>();
        StubAccessGuard accessGuard = new StubAccessGuard(guardedPatientId);
        accessGuard.currentUser = new AuthenticatedUser(1L, "admin", "admin");
        StubPaperCaseService paperCaseService = new StubPaperCaseService(null);
        PaperCaseController controller = new PaperCaseController(
                paperCaseService,
                accessGuard,
                new StubPaperCaseAccessAuditService(new AtomicReference<>())
        );

        ResponseEntity<List<PaperCaseRecordDTO>> response = controller.getFailedSyncRecords();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(1, response.getBody().size());
        assertEquals(199L, response.getBody().get(0).getId());
        assertEquals("admin-failed-sync", paperCaseService.lastScope);
    }

    @Test
    void shouldReturnDoctorScopedPendingReviewPaperCasesForDoctor() {
        AtomicReference<Long> guardedPatientId = new AtomicReference<>();
        StubAccessGuard accessGuard = new StubAccessGuard(guardedPatientId);
        accessGuard.currentUser = new AuthenticatedUser(7L, "doctor_test", "doctor");
        StubPaperCaseService paperCaseService = new StubPaperCaseService(null);
        PaperCaseController controller = new PaperCaseController(
                paperCaseService,
                accessGuard,
                new StubPaperCaseAccessAuditService(new AtomicReference<>())
        );

        ResponseEntity<List<PaperCaseRecordDTO>> response = controller.getPendingReviewRecords();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(1, response.getBody().size());
        assertEquals(1099L, response.getBody().get(0).getId());
        assertEquals("doctor-pending-review", paperCaseService.lastScope);
        assertEquals(7L, paperCaseService.lastDoctorId);
    }

    @Test
    void shouldReturnDoctorScopedFailedSyncPaperCasesForDoctor() {
        AtomicReference<Long> guardedPatientId = new AtomicReference<>();
        StubAccessGuard accessGuard = new StubAccessGuard(guardedPatientId);
        accessGuard.currentUser = new AuthenticatedUser(7L, "doctor_test", "doctor");
        StubPaperCaseService paperCaseService = new StubPaperCaseService(null);
        PaperCaseController controller = new PaperCaseController(
                paperCaseService,
                accessGuard,
                new StubPaperCaseAccessAuditService(new AtomicReference<>())
        );

        ResponseEntity<List<PaperCaseRecordDTO>> response = controller.getFailedSyncRecords();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(1, response.getBody().size());
        assertEquals(1199L, response.getBody().get(0).getId());
        assertEquals("doctor-failed-sync", paperCaseService.lastScope);
        assertEquals(7L, paperCaseService.lastDoctorId);
    }

    @Test
    void shouldReturnScopedAuditSummaryForDoctor() {
        AtomicReference<Long> guardedPatientId = new AtomicReference<>();
        StubAccessGuard accessGuard = new StubAccessGuard(guardedPatientId);
        StubPaperCaseAccessAuditService auditService = new StubPaperCaseAccessAuditService(new AtomicReference<>());
        auditService.summary = PaperCaseAccessAuditSummaryDTO.builder()
                .totalEntries(2L)
                .build();
        PaperCaseController controller = new PaperCaseController(
                new StubPaperCaseService(null),
                accessGuard,
                auditService
        );

        ResponseEntity<PaperCaseAccessAuditSummaryDTO> response = controller.getAuditSummary(12L, null, 20);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(2L, response.getBody().getTotalEntries());
        assertEquals(12L, guardedPatientId.get());
    }

    @Test
    void shouldAuditDeniedPaperCaseDownloadAttempt() {
        AtomicReference<Long> guardedPatientId = new AtomicReference<>();
        StubAccessGuard accessGuard = new StubAccessGuard(guardedPatientId);
        accessGuard.deniedMessage = "无权访问其他患者数据";
        StubPaperCaseAccessAuditService auditService = new StubPaperCaseAccessAuditService(new AtomicReference<>());
        PaperCaseController controller = new PaperCaseController(
                new StubPaperCaseService(tempDir.resolve("missing.pdf")),
                accessGuard,
                auditService
        );

        BusinessException ex = assertThrows(BusinessException.class,
                () -> controller.downloadPaperCaseFile(19L, request("127.0.0.2", "Denied-Agent")));

        assertEquals(403, ex.getCode());
        assertEquals(1, auditService.records.size());
        assertEquals("denied", auditService.records.get(0).get("outcome"));
        assertEquals("download", auditService.records.get(0).get("action"));
        assertEquals(12L, guardedPatientId.get());
    }

    @Test
    void shouldAllowPatientToUploadOwnPaperCase() throws Exception {
        AtomicReference<Long> guardedPatientId = new AtomicReference<>();
        StubAccessGuard accessGuard = new StubAccessGuard(guardedPatientId);
        accessGuard.currentUser = new AuthenticatedUser(12L, "patient_test", "patient");
        StubPaperCaseService paperCaseService = new StubPaperCaseService(null);
        PaperCaseController controller = new PaperCaseController(
                paperCaseService,
                accessGuard,
                new StubPaperCaseAccessAuditService(new AtomicReference<>())
        );

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "case.pdf",
                "application/pdf",
                "demo".getBytes()
        );

        ResponseEntity<PaperCaseRecordDTO> response = controller.uploadAndProcess(12L, file, LocalDate.of(2026, 5, 31));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(12L, guardedPatientId.get());
        assertEquals(12L, response.getBody().getPatientId());
        assertEquals("pending_review", response.getBody().getIcdCodingStatus());
    }

    private HttpServletRequest request(String remoteAddr, String userAgent) {
        return new jakarta.servlet.http.HttpServletRequestWrapper(new org.springframework.mock.web.MockHttpServletRequest()) {
            @Override
            public String getRemoteAddr() {
                return remoteAddr;
            }

            @Override
            public String getHeader(String name) {
                if (HttpHeaders.USER_AGENT.equalsIgnoreCase(name)) {
                    return userAgent;
                }
                return super.getHeader(name);
            }
        };
    }

    private static final class StubPaperCaseService extends PaperCaseService {
        private final Path filePath;
        private String lastScope;
        private Long lastDoctorId;

        private StubPaperCaseService(Path filePath) {
            super(null, null, null, null, null, null, null, null, null, null, null);
            this.filePath = filePath;
        }

        @Override
        public Long getPatientIdByPaperCaseId(Long paperCaseId) {
            return 12L;
        }

        @Override
        public PaperCaseFileAccess resolveFileAccess(Long paperCaseId) {
            return PaperCaseFileAccess.builder()
                    .paperCaseId(paperCaseId)
                    .patientId(12L)
                    .filePath(filePath)
                    .mediaType("application/pdf")
                    .downloadFileName("case.pdf")
                    .build();
        }

        @Override
        public List<PaperCaseRecordDTO> getByPatientId(Long patientId) {
            return List.of(PaperCaseRecordDTO.builder().id(1L).patientId(patientId).build());
        }

        @Override
        public List<PaperCaseRecordDTO> getPendingReviewRecords() {
            lastScope = "admin-pending-review";
            return List.of(PaperCaseRecordDTO.builder()
                    .id(99L)
                    .patientId(12L)
                    .icdCodingStatus("pending_review")
                    .build());
        }

        @Override
        public List<PaperCaseRecordDTO> getPendingReviewRecordsForDoctor(Long doctorId) {
            lastScope = "doctor-pending-review";
            lastDoctorId = doctorId;
            return List.of(PaperCaseRecordDTO.builder()
                    .id(1099L)
                    .patientId(12L)
                    .icdCodingStatus("pending_review")
                    .build());
        }

        @Override
        public PaperCasePatientSummaryDTO getPatientSummary(Long patientId) {
            return PaperCasePatientSummaryDTO.builder()
                    .patientId(patientId)
                    .totalRecords(2L)
                    .pendingReviewCount(1L)
                    .failedSyncCount(1L)
                    .failedJobCount(1L)
                    .pendingReviewRecords(List.of(PaperCaseRecordDTO.builder().id(99L).patientId(patientId).icdCodingStatus("pending_review").build()))
                    .failedSyncRecords(List.of(PaperCaseRecordDTO.builder().id(199L).patientId(patientId).medplumSyncStatus("failed").build()))
                    .attentionRecords(List.of(
                            PaperCaseRecordDTO.builder().id(99L).patientId(patientId).icdCodingStatus("pending_review").build(),
                            PaperCaseRecordDTO.builder().id(199L).patientId(patientId).medplumSyncStatus("failed").build()
                    ))
                    .allRecords(List.of(
                            PaperCaseRecordDTO.builder().id(99L).patientId(patientId).icdCodingStatus("pending_review").build(),
                            PaperCaseRecordDTO.builder().id(199L).patientId(patientId).medplumSyncStatus("failed").build()
                    ))
                    .build();
        }

        @Override
        public List<PaperCaseRecordDTO> getFailedSyncRecords() {
            lastScope = "admin-failed-sync";
            return List.of(PaperCaseRecordDTO.builder()
                    .id(199L)
                    .patientId(12L)
                    .medplumSyncStatus("failed")
                    .medplumSyncMessage("stub sync failed")
                    .build());
        }

        @Override
        public List<PaperCaseRecordDTO> getFailedSyncRecordsForDoctor(Long doctorId) {
            lastScope = "doctor-failed-sync";
            lastDoctorId = doctorId;
            return List.of(PaperCaseRecordDTO.builder()
                    .id(1199L)
                    .patientId(12L)
                    .medplumSyncStatus("failed")
                    .medplumSyncMessage("stub sync failed")
                    .build());
        }

        @Override
        public PaperCaseRecordDTO processUpload(Long patientId,
                                                org.springframework.web.multipart.MultipartFile file,
                                                LocalDate recordDate) {
            return PaperCaseRecordDTO.builder()
                    .id(3001L)
                    .patientId(patientId)
                    .recordDate(recordDate)
                    .preliminaryDiagnosis("待医生审核")
                    .icdCodingStatus("pending_review")
                    .medplumSyncStatus("review_pending")
                    .build();
        }
    }

    private static final class StubAccessGuard extends AccessGuard {
        private final AtomicReference<Long> patientIdRef;
        private boolean adminRequired;
        private String deniedMessage;
        private AuthenticatedUser currentUser = new AuthenticatedUser(7L, "doctor_test", "doctor");

        private StubAccessGuard(AtomicReference<Long> patientIdRef) {
            super(null, null, null, null, null, null);
            this.patientIdRef = patientIdRef;
        }

        @Override
        public void requirePatientAccess(Long patientId) {
            patientIdRef.set(patientId);
            if (deniedMessage != null) {
                throw new BusinessException(403, deniedMessage);
            }
        }

        @Override
        public void requireDoctorPatientAccess(Long patientId) {
            patientIdRef.set(patientId);
        }

        @Override
        public AuthenticatedUser currentUser() {
            return currentUser;
        }

        @Override
        public void requireAdmin() {
            adminRequired = true;
        }
    }

    private static final class StubPaperCaseAccessAuditService extends PaperCaseAccessAuditService {
        private final AtomicReference<Map<String, Object>> payloadRef;
        private PaperCaseAccessAuditSummaryDTO summary = PaperCaseAccessAuditSummaryDTO.builder().totalEntries(0L).build();
        private final List<Map<String, Object>> records = new ArrayList<>();

        private StubPaperCaseAccessAuditService(AtomicReference<Map<String, Object>> payloadRef) {
            super(null);
            this.payloadRef = payloadRef;
        }

        @Override
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
            Map<String, Object> payload = Map.of(
                    "paperCaseId", paperCaseId,
                    "patientId", patientId,
                    "actorUserId", actorUserId,
                    "actorRole", actorRole,
                    "action", action,
                    "outcome", outcome,
                    "resourceType", resourceType
            );
            payloadRef.set(payload);
            records.add(payload);
        }

        @Override
        public PaperCaseAccessAuditSummaryDTO getAuditSummary(Long patientId, Long paperCaseId, Integer limit) {
            return summary;
        }
    }
}
