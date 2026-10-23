package com.medical.agent.web.controller;

import com.medical.agent.common.dto.ClinicalDataJobDTO;
import com.medical.agent.common.dto.ClinicalDataJobSummaryDTO;
import com.medical.agent.service.clinicaljob.ClinicalDataJobAdminService;
import com.medical.agent.service.clinicaljob.ClinicalDataJobDoctorViewService;
import com.medical.agent.web.security.AccessGuard;
import com.medical.agent.web.security.AuthenticatedUser;
import com.medical.agent.web.security.CurrentUserService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ClinicalDataJobControllerTest {

    @Test
    void shouldUseAdminServiceForAdminStatusList() {
        StubClinicalDataJobAdminService adminService = new StubClinicalDataJobAdminService();
        StubClinicalDataJobDoctorViewService doctorService = new StubClinicalDataJobDoctorViewService();
        ClinicalDataJobController controller = new ClinicalDataJobController(
                adminService,
                doctorService,
                accessGuardFor(new AuthenticatedUser(1L, "admin", "admin"))
        );

        ResponseEntity<List<ClinicalDataJobDTO>> response = controller.listByStatus("failed");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(1, response.getBody().size());
        assertEquals("admin-failed", response.getBody().get(0).getCorrelationId());
    }

    @Test
    void shouldUseDoctorScopedServiceForDoctorStatusList() {
        StubClinicalDataJobAdminService adminService = new StubClinicalDataJobAdminService();
        StubClinicalDataJobDoctorViewService doctorService = new StubClinicalDataJobDoctorViewService();
        ClinicalDataJobController controller = new ClinicalDataJobController(
                adminService,
                doctorService,
                accessGuardFor(new AuthenticatedUser(9L, "doctor", "doctor"))
        );

        ResponseEntity<List<ClinicalDataJobDTO>> response = controller.listByStatus("failed");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(1, response.getBody().size());
        assertEquals("doctor-failed", response.getBody().get(0).getCorrelationId());
        assertEquals(9L, doctorService.lastDoctorId);
    }

    @Test
    void shouldUseDoctorScopedPatientListForDoctor() {
        StubClinicalDataJobDoctorViewService doctorService = new StubClinicalDataJobDoctorViewService();
        ClinicalDataJobController controller = new ClinicalDataJobController(
                new StubClinicalDataJobAdminService(),
                doctorService,
                accessGuardFor(new AuthenticatedUser(9L, "doctor", "doctor"))
        );

        ResponseEntity<List<ClinicalDataJobDTO>> response = controller.listByPatient(101L, "failed");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(1, response.getBody().size());
        assertEquals("doctor-patient-failed", response.getBody().get(0).getCorrelationId());
        assertEquals(9L, doctorService.lastDoctorId);
        assertEquals(101L, doctorService.lastPatientId);
    }

    @Test
    void shouldUseDoctorScopedSummaryForDoctor() {
        ClinicalDataJobController controller = new ClinicalDataJobController(
                new StubClinicalDataJobAdminService(),
                new StubClinicalDataJobDoctorViewService(),
                accessGuardFor(new AuthenticatedUser(9L, "doctor", "doctor"))
        );

        ResponseEntity<ClinicalDataJobSummaryDTO> response = controller.summary();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(2L, response.getBody().getTotalJobs());
        assertEquals(1L, response.getBody().getStatusCounts().get("failed"));
    }

    @Test
    void shouldUseDoctorScopedCorrelationLookupForDoctor() {
        StubClinicalDataJobDoctorViewService doctorService = new StubClinicalDataJobDoctorViewService();
        ClinicalDataJobController controller = new ClinicalDataJobController(
                new StubClinicalDataJobAdminService(),
                doctorService,
                accessGuardFor(new AuthenticatedUser(9L, "doctor", "doctor"))
        );

        ResponseEntity<List<ClinicalDataJobDTO>> response = controller.listByCorrelationId("corr-1");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(1, response.getBody().size());
        assertEquals("corr-1", response.getBody().get(0).getCorrelationId());
        assertEquals(9L, doctorService.lastDoctorId);
    }

    @Test
    void shouldUseDoctorScopedRetryableListForDoctor() {
        StubClinicalDataJobDoctorViewService doctorService = new StubClinicalDataJobDoctorViewService();
        ClinicalDataJobController controller = new ClinicalDataJobController(
                new StubClinicalDataJobAdminService(),
                doctorService,
                accessGuardFor(new AuthenticatedUser(9L, "doctor", "doctor"))
        );

        ResponseEntity<List<ClinicalDataJobDTO>> response = controller.listRetryableDue();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(1, response.getBody().size());
        assertEquals("retryable", response.getBody().get(0).getCorrelationId());
    }

    @Test
    void shouldUseDoctorScopedPatientRetryableListForDoctor() {
        StubClinicalDataJobDoctorViewService doctorService = new StubClinicalDataJobDoctorViewService();
        ClinicalDataJobController controller = new ClinicalDataJobController(
                new StubClinicalDataJobAdminService(),
                doctorService,
                accessGuardFor(new AuthenticatedUser(9L, "doctor", "doctor"))
        );

        ResponseEntity<List<ClinicalDataJobDTO>> response = controller.listRetryableByPatient(101L);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(1, response.getBody().size());
        assertEquals("patient-retryable", response.getBody().get(0).getCorrelationId());
        assertEquals(101L, doctorService.lastPatientId);
    }

    @Test
    void shouldUseDoctorScopedRequeueForDoctor() {
        StubClinicalDataJobDoctorViewService doctorService = new StubClinicalDataJobDoctorViewService();
        ClinicalDataJobController controller = new ClinicalDataJobController(
                new StubClinicalDataJobAdminService(),
                doctorService,
                accessGuardFor(new AuthenticatedUser(9L, "doctor", "doctor"))
        );

        ResponseEntity<Map<String, Object>> response = controller.requeue(42L);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(true, response.getBody().get("success"));
        assertEquals("doctor-requeue", response.getBody().get("scope"));
        assertEquals(42L, doctorService.lastJobId);
    }

    @Test
    void shouldUseDoctorScopedCancelForDoctor() {
        StubClinicalDataJobDoctorViewService doctorService = new StubClinicalDataJobDoctorViewService();
        ClinicalDataJobController controller = new ClinicalDataJobController(
                new StubClinicalDataJobAdminService(),
                doctorService,
                accessGuardFor(new AuthenticatedUser(9L, "doctor", "doctor"))
        );

        ResponseEntity<Map<String, Object>> response = controller.cancel(42L, "manual stop");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(true, response.getBody().get("success"));
        assertEquals("doctor-cancel", response.getBody().get("scope"));
        assertEquals("manual stop", doctorService.lastReason);
    }

    @Test
    void shouldUseDoctorScopedBulkRequeueForDoctor() {
        StubClinicalDataJobDoctorViewService doctorService = new StubClinicalDataJobDoctorViewService();
        ClinicalDataJobController controller = new ClinicalDataJobController(
                new StubClinicalDataJobAdminService(),
                doctorService,
                accessGuardFor(new AuthenticatedUser(9L, "doctor", "doctor"))
        );

        ResponseEntity<Map<String, Object>> response = controller.requeueDue();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(true, response.getBody().get("success"));
        assertEquals("doctor-requeue-due", response.getBody().get("scope"));
        assertEquals(9L, doctorService.lastDoctorId);
    }

    @Test
    void shouldUseAdminMutationEndpointsForAdmin() {
        StubClinicalDataJobAdminService adminService = new StubClinicalDataJobAdminService();
        ClinicalDataJobController controller = new ClinicalDataJobController(
                adminService,
                new StubClinicalDataJobDoctorViewService(),
                accessGuardFor(new AuthenticatedUser(1L, "admin", "admin"))
        );

        ResponseEntity<Map<String, Object>> requeueResponse = controller.requeue(11L);
        ResponseEntity<Map<String, Object>> cancelResponse = controller.cancel(12L, "stop");
        ResponseEntity<Map<String, Object>> requeueDueResponse = controller.requeueDue();

        assertEquals("admin-requeue", requeueResponse.getBody().get("scope"));
        assertEquals("admin-cancel", cancelResponse.getBody().get("scope"));
        assertEquals("admin-requeue-due", requeueDueResponse.getBody().get("scope"));
        assertEquals(12L, adminService.lastCancelledJobId);
    }

    @Test
    void shouldUseAdminPatientEndpointsForAdmin() {
        StubClinicalDataJobAdminService adminService = new StubClinicalDataJobAdminService();
        ClinicalDataJobController controller = new ClinicalDataJobController(
                adminService,
                new StubClinicalDataJobDoctorViewService(),
                accessGuardFor(new AuthenticatedUser(1L, "admin", "admin"))
        );

        ResponseEntity<List<ClinicalDataJobDTO>> listResponse = controller.listByPatient(101L, "failed");
        ResponseEntity<List<ClinicalDataJobDTO>> retryableResponse = controller.listRetryableByPatient(101L);

        assertEquals("admin-patient-failed", listResponse.getBody().get(0).getCorrelationId());
        assertEquals("admin-patient-retryable", retryableResponse.getBody().get(0).getCorrelationId());
    }

    private static AccessGuard accessGuardFor(AuthenticatedUser user) {
        return new AccessGuard(
                new StubCurrentUserService(user),
                null,
                null,
                null,
                null,
                null
        );
    }

    private static final class StubCurrentUserService extends CurrentUserService {
        private final AuthenticatedUser user;

        private StubCurrentUserService(AuthenticatedUser user) {
            this.user = user;
        }

        @Override
        public AuthenticatedUser requireCurrentUser() {
            return user;
        }
    }

    private static final class StubClinicalDataJobAdminService extends ClinicalDataJobAdminService {
        private Long lastCancelledJobId;

        private StubClinicalDataJobAdminService() {
            super(null, null);
        }

        @Override
        public List<ClinicalDataJobDTO> listByStatus(String status) {
            return List.of(ClinicalDataJobDTO.builder()
                    .id(1L)
                    .jobStatus(status)
                    .correlationId("admin-" + status)
                    .build());
        }

        @Override
        public List<ClinicalDataJobDTO> listRetryableDue() {
            return List.of(ClinicalDataJobDTO.builder()
                    .id(2L)
                    .jobStatus("failed")
                    .correlationId("admin-retryable")
                    .build());
        }

        @Override
        public ClinicalDataJobSummaryDTO getSummary() {
            return ClinicalDataJobSummaryDTO.builder()
                    .totalJobs(8L)
                    .statusCounts(Map.of("failed", 3L))
                    .recentAlertCounts(Map.of())
                    .build();
        }

        @Override
        public List<ClinicalDataJobDTO> listByCorrelationId(String correlationId) {
            return List.of(ClinicalDataJobDTO.builder()
                    .id(3L)
                    .jobStatus("failed")
                    .correlationId(correlationId)
                    .build());
        }

        @Override
        public List<ClinicalDataJobDTO> listByPatient(Long patientId, String status) {
            return List.of(ClinicalDataJobDTO.builder()
                    .id(31L)
                    .patientId(patientId)
                    .jobStatus(status)
                    .correlationId("admin-patient-" + status)
                    .build());
        }

        @Override
        public List<ClinicalDataJobDTO> listRetryableByPatient(Long patientId) {
            return List.of(ClinicalDataJobDTO.builder()
                    .id(32L)
                    .patientId(patientId)
                    .jobStatus("failed")
                    .correlationId("admin-patient-retryable")
                    .build());
        }

        @Override
        public Map<String, Object> requeueJob(Long jobId) {
            return Map.of("success", true, "scope", "admin-requeue", "jobId", jobId);
        }

        @Override
        public Map<String, Object> cancelJob(Long jobId, String reason) {
            this.lastCancelledJobId = jobId;
            return Map.of("success", true, "scope", "admin-cancel", "jobId", jobId, "reason", reason);
        }

        @Override
        public Map<String, Object> requeueAllDueJobs() {
            return Map.of("success", true, "scope", "admin-requeue-due", "requeuedJobs", 2);
        }
    }

    private static final class StubClinicalDataJobDoctorViewService extends ClinicalDataJobDoctorViewService {
        private Long lastDoctorId;
        private Long lastJobId;
        private Long lastPatientId;
        private String lastReason;

        private StubClinicalDataJobDoctorViewService() {
            super(null, null, null);
        }

        @Override
        public List<ClinicalDataJobDTO> listByStatus(Long doctorId, String status) {
            this.lastDoctorId = doctorId;
            return List.of(ClinicalDataJobDTO.builder()
                    .id(10L)
                    .jobStatus(status)
                    .correlationId("doctor-" + status)
                    .build());
        }

        @Override
        public ClinicalDataJobSummaryDTO getSummary(Long doctorId) {
            this.lastDoctorId = doctorId;
            return ClinicalDataJobSummaryDTO.builder()
                    .totalJobs(2L)
                    .statusCounts(Map.of("failed", 1L, "review_pending", 1L))
                    .recentAlertCounts(Map.of())
                    .build();
        }

        @Override
        public List<ClinicalDataJobDTO> listByCorrelationId(Long doctorId, String correlationId) {
            this.lastDoctorId = doctorId;
            return List.of(ClinicalDataJobDTO.builder()
                    .id(11L)
                    .jobStatus("failed")
                    .correlationId(correlationId)
                    .build());
        }

        @Override
        public List<ClinicalDataJobDTO> listRetryableDue(Long doctorId) {
            this.lastDoctorId = doctorId;
            return List.of(ClinicalDataJobDTO.builder()
                    .id(12L)
                    .jobStatus("failed")
                    .correlationId("retryable")
                    .build());
        }

        @Override
        public List<ClinicalDataJobDTO> listByPatient(Long doctorId, Long patientId, String status) {
            this.lastDoctorId = doctorId;
            this.lastPatientId = patientId;
            return List.of(ClinicalDataJobDTO.builder()
                    .id(13L)
                    .patientId(patientId)
                    .jobStatus(status)
                    .correlationId("doctor-patient-" + status)
                    .build());
        }

        @Override
        public List<ClinicalDataJobDTO> listRetryableByPatient(Long doctorId, Long patientId) {
            this.lastDoctorId = doctorId;
            this.lastPatientId = patientId;
            return List.of(ClinicalDataJobDTO.builder()
                    .id(14L)
                    .patientId(patientId)
                    .jobStatus("failed")
                    .correlationId("patient-retryable")
                    .build());
        }

        @Override
        public Map<String, Object> requeueJob(Long doctorId, Long jobId) {
            this.lastDoctorId = doctorId;
            this.lastJobId = jobId;
            return Map.of("success", true, "scope", "doctor-requeue", "jobId", jobId);
        }

        @Override
        public Map<String, Object> cancelJob(Long doctorId, Long jobId, String reason) {
            this.lastDoctorId = doctorId;
            this.lastJobId = jobId;
            this.lastReason = reason;
            return Map.of("success", true, "scope", "doctor-cancel", "jobId", jobId);
        }

        @Override
        public Map<String, Object> requeueAllDue(Long doctorId) {
            this.lastDoctorId = doctorId;
            return Map.of("success", true, "scope", "doctor-requeue-due", "requeuedJobs", 1);
        }
    }
}
