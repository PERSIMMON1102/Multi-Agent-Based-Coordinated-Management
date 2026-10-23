package com.medical.agent.web.controller;

import com.medical.agent.common.dto.HealthRecordDTO;
import com.medical.agent.service.HealthRecordService;
import com.medical.agent.web.security.AccessGuard;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HealthRecordControllerTest {

    @Test
    void shouldRequirePatientAccessBeforeListingHealthRecords() {
        AtomicReference<Long> guardedPatientId = new AtomicReference<>();
        StubHealthRecordService healthRecordService = new StubHealthRecordService();
        HealthRecordController controller = new HealthRecordController(
                healthRecordService,
                new StubAccessGuard(guardedPatientId)
        );

        ResponseEntity<List<HealthRecordDTO>> response = controller.getByPatientId(12L);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(12L, guardedPatientId.get());
        assertEquals(12L, healthRecordService.lastListedPatientId);
        assertEquals(1, response.getBody().size());
    }

    @Test
    void shouldRequirePatientAccessBeforeCreatingHealthRecord() {
        AtomicReference<Long> guardedPatientId = new AtomicReference<>();
        StubHealthRecordService healthRecordService = new StubHealthRecordService();
        HealthRecordController controller = new HealthRecordController(
                healthRecordService,
                new StubAccessGuard(guardedPatientId)
        );

        HealthRecordDTO payload = HealthRecordDTO.builder()
                .patientId(18L)
                .systolicBp(132)
                .diastolicBp(86)
                .build();

        ResponseEntity<HealthRecordDTO> response = controller.create(payload);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(18L, guardedPatientId.get());
        assertEquals(18L, healthRecordService.lastCreatedRecord.getPatientId());
        assertEquals(132, response.getBody().getSystolicBp());
        assertEquals(Boolean.TRUE, healthRecordService.lastAssessmentOptions.get("_deferAssessment"));
        assertEquals(Boolean.TRUE, healthRecordService.lastAssessmentOptions.get("_bestEffortAssessment"));
    }

    @Test
    void shouldForwardRecoveryJobIdWhenCreatingHealthRecord() {
        AtomicReference<Long> guardedPatientId = new AtomicReference<>();
        StubHealthRecordService healthRecordService = new StubHealthRecordService();
        HealthRecordController controller = new HealthRecordController(
                healthRecordService,
                new StubAccessGuard(guardedPatientId)
        );

        HealthRecordDTO payload = HealthRecordDTO.builder()
                .patientId(22L)
                .recoveryJobId(91L)
                .build();

        ResponseEntity<HealthRecordDTO> response = controller.create(payload);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(22L, guardedPatientId.get());
        assertEquals(91L, healthRecordService.lastCreatedRecord.getRecoveryJobId());
        assertEquals(91L, response.getBody().getRecoveryJobId());
        assertEquals("manual_entry", healthRecordService.lastAssessmentOptions.get("source"));
    }

    private static final class StubAccessGuard extends AccessGuard {
        private final AtomicReference<Long> patientIdRef;

        private StubAccessGuard(AtomicReference<Long> patientIdRef) {
            super(null, null, null, null, null, null);
            this.patientIdRef = patientIdRef;
        }

        @Override
        public void requirePatientAccess(Long patientId) {
            patientIdRef.set(patientId);
        }
    }

    private static final class StubHealthRecordService extends HealthRecordService {
        private Long lastListedPatientId;
        private HealthRecordDTO lastCreatedRecord;
        private Map<String, Object> lastAssessmentOptions;

        private StubHealthRecordService() {
            super(null, null, null);
        }

        @Override
        public List<HealthRecordDTO> getByPatientId(Long patientId) {
            lastListedPatientId = patientId;
            return List.of(HealthRecordDTO.builder().patientId(patientId).systolicBp(128).build());
        }

        @Override
        public HealthRecordDTO create(HealthRecordDTO dto,
                                      com.medical.agent.common.enums.DiseaseType diseaseType,
                                      boolean triggerAssessment,
                                      Map<String, Object> assessmentOptions) {
            lastCreatedRecord = dto;
            lastAssessmentOptions = assessmentOptions;
            return dto;
        }
    }
}
