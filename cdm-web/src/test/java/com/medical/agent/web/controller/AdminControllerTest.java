package com.medical.agent.web.controller;

import com.medical.agent.service.AdminDemoDataService;
import com.medical.agent.service.AdminDoctorService;
import com.medical.agent.service.PatientIntegrationService;
import com.medical.agent.common.dto.HealthRecordDTO;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class AdminControllerTest {

    @Test
    void shouldReturnDemoDataOverviewAndRefreshResult() {
        AdminController controller = new AdminController(
                new StubAdminDoctorService(),
                new StubAdminDemoDataService(),
                new StubPatientIntegrationService()
        );

        ResponseEntity<Map<String, Object>> overviewResponse = controller.getDemoDataOverview();
        ResponseEntity<Map<String, Object>> refreshResponse = controller.refreshDemoData();

        assertEquals(HttpStatus.OK, overviewResponse.getStatusCode());
        assertEquals(HttpStatus.OK, refreshResponse.getStatusCode());
        assertNotNull(overviewResponse.getBody());
        assertNotNull(refreshResponse.getBody());
        assertEquals(2, overviewResponse.getBody().get("demoDoctors"));
        assertEquals(4, refreshResponse.getBody().get("healthRecordsCreated"));
    }

    @Test
    void shouldImportMockDataViaAdminEndpoint() {
        AdminController controller = new AdminController(
                new StubAdminDoctorService(),
                new StubAdminDemoDataService(),
                new StubPatientIntegrationService()
        );

        HealthRecordDTO dto = HealthRecordDTO.builder()
                .patientId(99L)
                .systolicBp(138)
                .diastolicBp(86)
                .fastingGlucose(7.2)
                .build();

        ResponseEntity<Map<String, Object>> response = controller.importMockToMedplum(dto);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(99L, response.getBody().get("patientId"));
        assertEquals(8, response.getBody().get("observationsSynced"));
    }

    @Test
    void shouldReturnPatientIntegrationOverviewAndStatus() {
        AdminController controller = new AdminController(
                new StubAdminDoctorService(),
                new StubAdminDemoDataService(),
                new StubPatientIntegrationService()
        );

        ResponseEntity<Map<String, Object>> overviewResponse = controller.getPatientIntegrationOverview(null, null);
        ResponseEntity<Map<String, Object>> statusResponse = controller.getPatientIntegrationStatus(12L);
        ResponseEntity<Map<String, Object>> syncResponse = controller.syncPatientIntegration(12L);
        ResponseEntity<Map<String, Object>> importResponse = controller.importBusinessMockToMedplum(HealthRecordDTO.builder().patientId(12L).fastingGlucose(7.5).build());
        ResponseEntity<Map<String, Object>> bindResponse = controller.bindPatientDoctor(12L, Map.of("doctorId", 5L));

        assertEquals(HttpStatus.OK, overviewResponse.getStatusCode());
        assertEquals(HttpStatus.OK, statusResponse.getStatusCode());
        assertEquals(HttpStatus.OK, syncResponse.getStatusCode());
        assertEquals(HttpStatus.OK, importResponse.getStatusCode());
        assertEquals(HttpStatus.OK, bindResponse.getStatusCode());
        assertEquals(3, overviewResponse.getBody().get("registeredPatients"));
        assertEquals(true, statusResponse.getBody().get("agentContextReady"));
        assertEquals(12L, syncResponse.getBody().get("patientId"));
        assertEquals(true, importResponse.getBody().get("cloudOnly"));
        assertEquals(5L, bindResponse.getBody().get("assignedDoctorId"));
    }

    private static class StubAdminDoctorService extends AdminDoctorService {
        StubAdminDoctorService() {
            super(null, null, null, null);
        }

        @Override
        public List<Map<String, Object>> listDoctors() {
            return List.of(Map.of("id", 1L, "name", "医生"));
        }
    }

    private static class StubAdminDemoDataService extends AdminDemoDataService {
        StubAdminDemoDataService() {
            super(null, null, null, null, null, null);
        }

        @Override
        public Map<String, Object> getOverview() {
            return Map.of(
                    "demoDoctors", 2,
                    "demoPatients", 2,
                    "demoMedicalHistories", 2,
                    "demoHealthRecords", 4
            );
        }

        @Override
        public Map<String, Object> refreshDemoData() {
            return Map.of(
                    "doctorsCreated", 0,
                    "doctorsUpdated", 2,
                    "patientsCreated", 0,
                    "patientsUpdated", 2,
                    "healthRecordsDeleted", 4,
                    "healthRecordsCreated", 4
            );
        }

        @Override
        public Map<String, Object> importMockToMedplum(HealthRecordDTO dto, boolean includeObservations) {
            return Map.of(
                    "patientId", dto.getPatientId(),
                    "localRecordId", 1L,
                    "patientFhirId", "demo-patient-1",
                    "observationsSynced", 8,
                    "message", "ok"
            );
        }
    }

    private static class StubPatientIntegrationService extends PatientIntegrationService {
        StubPatientIntegrationService() {
            super(null, null, null, null, null, null);
        }

        @Override
        public Map<String, Object> getOverview() {
            return getOverview(null, null);
        }

        @Override
        public Map<String, Object> getOverview(String keyword, String readiness) {
            return Map.of(
                    "registeredPatients", 3,
                    "agentReadyPatients", 2,
                    "medplumMappedPatients", 1
            );
        }

        @Override
        public Map<String, Object> getPatientStatus(Long patientId) {
            return Map.of(
                    "patientId", patientId,
                    "found", true,
                    "agentContextReady", true,
                    "message", "ok"
            );
        }

        @Override
        public Map<String, Object> syncPatient(Long patientId, boolean includeObservations) {
            return Map.of(
                    "patientId", patientId,
                    "patientFhirId", "patient-" + patientId,
                    "observationsSynced", 3,
                    "message", "ok"
            );
        }

        @Override
        public Map<String, Object> bindDoctor(Long patientId, Long doctorId) {
            return Map.of(
                    "patientId", patientId,
                    "assignedDoctorId", doctorId,
                    "message", "ok"
            );
        }

        @Override
        public Map<String, Object> importMockToMedplum(HealthRecordDTO dto, boolean includeObservations) {
            return Map.of(
                    "patientId", dto.getPatientId(),
                    "patientFhirId", "patient-" + dto.getPatientId(),
                    "observationsSynced", 4,
                    "cloudOnly", true,
                    "message", "ok"
            );
        }
    }
}
