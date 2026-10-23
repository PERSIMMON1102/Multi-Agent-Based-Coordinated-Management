package com.medical.agent.web.controller;

import com.medical.agent.common.dto.DeviceImportSummaryDTO;
import com.medical.agent.service.device.HuaweiDeviceJsonImportService;
import com.medical.agent.service.security.UploadSecurityService;
import com.medical.agent.web.security.AuthenticatedUser;
import com.medical.agent.web.security.AccessGuard;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DeviceImportControllerTest {

    @Test
    void shouldRejectNonJsonDeviceImportUpload() {
        HuaweiDeviceJsonImportService importService = new HuaweiDeviceJsonImportService(null);

        DeviceImportController controller = new DeviceImportController(
                importService,
                new AccessGuard(null, null, null, null, null, null),
                new UploadSecurityService()
        );

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "device.json",
                "application/json",
                "not-json".getBytes()
        );

        assertThrows(com.medical.agent.common.exception.BusinessException.class,
                () -> controller.importHuaweiJson(1L, file));
    }

    @Test
    void shouldRequireDoctorPatientAccessBeforeImportingDeviceJson() throws Exception {
        AtomicReference<Long> guardedPatientId = new AtomicReference<>();
        StubAccessGuard accessGuard = new StubAccessGuard(guardedPatientId);
        StubHuaweiDeviceJsonImportService importService = new StubHuaweiDeviceJsonImportService();

        DeviceImportController controller = new DeviceImportController(
                importService,
                accessGuard,
                new UploadSecurityService()
        );

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "device.json",
                "application/json",
                "{\"shadow\":{\"reported\":{\"properties\":[]}}}".getBytes()
        );

        ResponseEntity<DeviceImportSummaryDTO> response = controller.importHuaweiJson(12L, file);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(12L, guardedPatientId.get());
        assertEquals(Long.valueOf(12L), importService.lastPatientId);
        assertEquals(1, response.getBody().getImportedRecords());
    }

    @Test
    void shouldAllowPatientToImportOwnDeviceJson() throws Exception {
        AtomicReference<Long> guardedPatientId = new AtomicReference<>();
        StubAccessGuard accessGuard = new StubAccessGuard(guardedPatientId);
        accessGuard.currentUser = new AuthenticatedUser(12L, "patient_test", "patient");
        StubHuaweiDeviceJsonImportService importService = new StubHuaweiDeviceJsonImportService();

        DeviceImportController controller = new DeviceImportController(
                importService,
                accessGuard,
                new UploadSecurityService()
        );

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "device.json",
                "application/json",
                "{\"shadow\":{\"reported\":{\"properties\":[]}}}".getBytes()
        );

        ResponseEntity<DeviceImportSummaryDTO> response = controller.importHuaweiJson(12L, file);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(12L, guardedPatientId.get());
        assertEquals(Long.valueOf(12L), importService.lastPatientId);
    }

    private static final class StubAccessGuard extends AccessGuard {
        private final AtomicReference<Long> patientIdRef;
        private AuthenticatedUser currentUser = new AuthenticatedUser(7L, "doctor_test", "doctor");

        private StubAccessGuard(AtomicReference<Long> patientIdRef) {
            super(null, null, null, null, null, null);
            this.patientIdRef = patientIdRef;
        }

        @Override
        public void requireDoctorPatientAccess(Long patientId) {
            patientIdRef.set(patientId);
        }

        @Override
        public void requirePatientAccess(Long patientId) {
            patientIdRef.set(patientId);
        }

        @Override
        public AuthenticatedUser currentUser() {
            return currentUser;
        }
    }

    private static final class StubHuaweiDeviceJsonImportService extends HuaweiDeviceJsonImportService {
        private Long lastPatientId;

        private StubHuaweiDeviceJsonImportService() {
            super(null);
        }

        @Override
        public DeviceImportSummaryDTO importJson(Long patientId, byte[] payload) {
            lastPatientId = patientId;
            return DeviceImportSummaryDTO.builder()
                    .patientId(patientId)
                    .vendor("huawei")
                    .importedRecords(1)
                    .totalRecords(1)
                    .build();
        }
    }
}
