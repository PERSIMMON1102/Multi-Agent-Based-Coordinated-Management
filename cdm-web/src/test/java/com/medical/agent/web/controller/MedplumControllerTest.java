package com.medical.agent.web.controller;

import com.medical.agent.common.dto.ClinicalDataJobDTO;
import com.medical.agent.model.repository.MedplumSyncCheckpointRepository;
import com.medical.agent.service.ClinicalDataJobReplayService;
import com.medical.agent.service.MedplumService;
import com.medical.agent.web.security.AccessGuard;
import com.medical.agent.web.security.AuthenticatedUser;
import com.medical.agent.web.security.CurrentUserService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MedplumControllerTest {

    @Test
    void shouldReturnFailedJobs() {
        StubClinicalDataJobReplayService replayService = new StubClinicalDataJobReplayService();
        MedplumController controller = new MedplumController(
                new StubMedplumService(),
                replayService,
                accessGuardFor(new AuthenticatedUser(1L, "admin", "admin"))
        );

        ResponseEntity<List<ClinicalDataJobDTO>> response = controller.getFailedJobs();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(1, response.getBody().size());
        assertEquals(42L, response.getBody().get(0).getId());
        assertEquals("admin-failed", replayService.lastScope);
    }

    @Test
    void shouldReplayJobSync() {
        StubClinicalDataJobReplayService replayService = new StubClinicalDataJobReplayService();
        MedplumController controller = new MedplumController(
                new StubMedplumService(),
                replayService,
                accessGuardFor(new AuthenticatedUser(1L, "admin", "admin"))
        );

        ResponseEntity<Map<String, Object>> response = controller.replayJobSync(42L);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(true, response.getBody().get("success"));
        assertEquals("synced", response.getBody().get("medplumSyncStatus"));
        assertEquals("admin-replay", replayService.lastScope);
    }

    @Test
    void shouldReturnReplayableJobs() {
        StubClinicalDataJobReplayService replayService = new StubClinicalDataJobReplayService();
        MedplumController controller = new MedplumController(
                new StubMedplumService(),
                replayService,
                accessGuardFor(new AuthenticatedUser(1L, "admin", "admin"))
        );

        ResponseEntity<List<ClinicalDataJobDTO>> response = controller.getReplayableJobs();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(1, response.getBody().size());
        assertEquals(43L, response.getBody().get(0).getId());
        assertEquals("admin-replayable", replayService.lastScope);
    }

    @Test
    void shouldReplayFailedJobsInBatch() {
        StubClinicalDataJobReplayService replayService = new StubClinicalDataJobReplayService();
        MedplumController controller = new MedplumController(
                new StubMedplumService(),
                replayService,
                accessGuardFor(new AuthenticatedUser(1L, "admin", "admin"))
        );

        ResponseEntity<Map<String, Object>> response = controller.replayFailedJobs(20);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(1, response.getBody().get("requestedJobs"));
        assertEquals(1, response.getBody().get("replayedJobs"));
        assertEquals("admin-replay-batch", replayService.lastScope);
    }

    @Test
    void shouldUseDoctorScopedFailedJobsForDoctor() {
        StubClinicalDataJobReplayService replayService = new StubClinicalDataJobReplayService();
        MedplumController controller = new MedplumController(
                new StubMedplumService(),
                replayService,
                accessGuardFor(new AuthenticatedUser(9L, "doctor", "doctor"))
        );

        ResponseEntity<List<ClinicalDataJobDTO>> response = controller.getFailedJobs();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("doctor-failed", replayService.lastScope);
        assertEquals(9L, replayService.lastDoctorId);
        assertEquals(142L, response.getBody().get(0).getId());
    }

    @Test
    void shouldUseDoctorScopedReplayableJobsForDoctor() {
        StubClinicalDataJobReplayService replayService = new StubClinicalDataJobReplayService();
        MedplumController controller = new MedplumController(
                new StubMedplumService(),
                replayService,
                accessGuardFor(new AuthenticatedUser(9L, "doctor", "doctor"))
        );

        ResponseEntity<List<ClinicalDataJobDTO>> response = controller.getReplayableJobs();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("doctor-replayable", replayService.lastScope);
        assertEquals(9L, replayService.lastDoctorId);
        assertEquals(143L, response.getBody().get(0).getId());
    }

    @Test
    void shouldUseDoctorScopedReplayForDoctor() {
        StubClinicalDataJobReplayService replayService = new StubClinicalDataJobReplayService();
        MedplumController controller = new MedplumController(
                new StubMedplumService(),
                replayService,
                accessGuardFor(new AuthenticatedUser(9L, "doctor", "doctor"))
        );

        ResponseEntity<Map<String, Object>> response = controller.replayJobSync(42L);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(true, response.getBody().get("success"));
        assertEquals("doctor-replay", replayService.lastScope);
        assertEquals(9L, replayService.lastDoctorId);
        assertEquals(42L, replayService.lastJobId);
    }

    @Test
    void shouldUseDoctorScopedBatchReplayForDoctor() {
        StubClinicalDataJobReplayService replayService = new StubClinicalDataJobReplayService();
        MedplumController controller = new MedplumController(
                new StubMedplumService(),
                replayService,
                accessGuardFor(new AuthenticatedUser(9L, "doctor", "doctor"))
        );

        ResponseEntity<Map<String, Object>> response = controller.replayFailedJobs(20);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(1, response.getBody().get("requestedJobs"));
        assertEquals(1, response.getBody().get("replayedJobs"));
        assertEquals("doctor-replay-batch", replayService.lastScope);
        assertEquals(9L, replayService.lastDoctorId);
    }

    @Test
    void shouldClampObservationLimitToRequestedWindow() {
        StubMedplumService medplumService = new StubMedplumService();
        MedplumController controller = new MedplumController(
                medplumService,
                new StubClinicalDataJobReplayService(),
                new AccessGuard(null, null, null, null, null, null)
        );

        ResponseEntity<Map<String, Object>> response = controller.getLatestObservations(9L, 5);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(5, response.getBody().get("limit"));
        assertEquals(5, medplumService.lastObservationLimit);
    }

    @Test
    void shouldClampPaperCaseLimitToRequestedWindow() {
        StubMedplumService medplumService = new StubMedplumService();
        MedplumController controller = new MedplumController(
                medplumService,
                new StubClinicalDataJobReplayService(),
                new AccessGuard(null, null, null, null, null, null)
        );

        ResponseEntity<Map<String, Object>> response = controller.getPaperCaseResources(9L, 7);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(7, response.getBody().get("limit"));
        assertEquals(7, medplumService.lastPaperCaseLimit);
    }

    private static final class StubMedplumService extends MedplumService {
        private int lastObservationLimit;
        private int lastPaperCaseLimit;

        private StubMedplumService() {
            super(null, null, null, checkpointRepository());
        }

        @Override
        public Map<String, Object> getLatestObservationsForLocalPatient(Long localPatientId, int limit) {
            lastObservationLimit = limit;
            return Map.of(
                    "available", true,
                    "found", true,
                    "localPatientId", localPatientId,
                    "limit", limit,
                    "observations", List.of()
            );
        }

        @Override
        public Map<String, Object> getPaperCaseResourcesForLocalPatient(Long localPatientId, int limit) {
            lastPaperCaseLimit = limit;
            return Map.of(
                    "available", true,
                    "found", true,
                    "localPatientId", localPatientId,
                    "limit", limit,
                    "encounters", List.of(),
                    "conditions", List.of(),
                    "documentReferences", List.of()
            );
        }
    }

    private static MedplumSyncCheckpointRepository checkpointRepository() {
        return (MedplumSyncCheckpointRepository) Proxy.newProxyInstance(
                MedplumSyncCheckpointRepository.class.getClassLoader(),
                new Class<?>[]{MedplumSyncCheckpointRepository.class},
                (proxy, method, args) -> {
                    if ("toString".equals(method.getName())) {
                        return "MedplumSyncCheckpointRepositoryProxy";
                    }
                    if ("hashCode".equals(method.getName())) {
                        return System.identityHashCode(proxy);
                    }
                    if ("equals".equals(method.getName())) {
                        return proxy == args[0];
                    }
                    return null;
                }
        );
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

    private static final class StubClinicalDataJobReplayService extends ClinicalDataJobReplayService {
        private String lastScope;
        private Long lastDoctorId;
        private Long lastJobId;

        private StubClinicalDataJobReplayService() {
            super(null, null, null);
        }

        @Override
        public List<ClinicalDataJobDTO> listFailedJobs() {
            lastScope = "admin-failed";
            return List.of(ClinicalDataJobDTO.builder()
                    .id(42L)
                    .jobType("paper_case_ingest")
                    .jobStatus("failed")
                    .lastStage("medplum_sync_failed")
                    .build());
        }

        @Override
        public List<ClinicalDataJobDTO> listFailedJobsForDoctor(Long doctorId) {
            lastScope = "doctor-failed";
            lastDoctorId = doctorId;
            return List.of(ClinicalDataJobDTO.builder()
                    .id(142L)
                    .jobType("paper_case_ingest")
                    .jobStatus("failed")
                    .lastStage("medplum_sync_failed")
                    .build());
        }

        @Override
        public List<ClinicalDataJobDTO> listReplayableJobs() {
            lastScope = "admin-replayable";
            return List.of(ClinicalDataJobDTO.builder()
                    .id(43L)
                    .jobType("paper_case_ingest")
                    .jobStatus("dead_letter")
                    .sourceKind("paper_case_record")
                    .build());
        }

        @Override
        public List<ClinicalDataJobDTO> listReplayableJobsForDoctor(Long doctorId) {
            lastScope = "doctor-replayable";
            lastDoctorId = doctorId;
            return List.of(ClinicalDataJobDTO.builder()
                    .id(143L)
                    .jobType("paper_case_ingest")
                    .jobStatus("dead_letter")
                    .sourceKind("paper_case_record")
                    .build());
        }

        @Override
        public Map<String, Object> replayMedplumSync(Long jobId) {
            lastScope = "admin-replay";
            lastJobId = jobId;
            return Map.of(
                    "success", true,
                    "medplumSyncStatus", "synced",
                    "message", "replayed",
                    "job", ClinicalDataJobDTO.builder()
                            .id(jobId)
                            .jobStatus("synced")
                            .lastStage("medplum_replayed")
                            .build()
            );
        }

        @Override
        public Map<String, Object> replayMedplumSyncForDoctor(Long doctorId, Long jobId) {
            lastScope = "doctor-replay";
            lastDoctorId = doctorId;
            lastJobId = jobId;
            return Map.of(
                    "success", true,
                    "medplumSyncStatus", "synced",
                    "message", "replayed",
                    "job", ClinicalDataJobDTO.builder()
                            .id(jobId)
                            .jobStatus("synced")
                            .lastStage("medplum_replayed")
                            .build()
            );
        }

        @Override
        public Map<String, Object> replayReplayableJobs(Integer limit) {
            lastScope = "admin-replay-batch";
            return Map.of(
                    "success", true,
                    "requestedJobs", 1,
                    "replayedJobs", 1,
                    "failedJobs", 0,
                    "message", "已批量完成 Medplum 重放",
                    "results", List.of(Map.of("jobId", 43L, "success", true))
            );
        }

        @Override
        public Map<String, Object> replayReplayableJobsForDoctor(Long doctorId, Integer limit) {
            lastScope = "doctor-replay-batch";
            lastDoctorId = doctorId;
            return Map.of(
                    "success", true,
                    "requestedJobs", 1,
                    "replayedJobs", 1,
                    "failedJobs", 0,
                    "message", "已批量完成 Medplum 重放",
                    "results", List.of(Map.of("jobId", 143L, "success", true))
            );
        }
    }
}
