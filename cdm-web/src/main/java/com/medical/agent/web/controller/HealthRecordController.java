package com.medical.agent.web.controller;

import com.medical.agent.common.dto.HealthRecordDTO;
import com.medical.agent.service.HealthRecordService;
import com.medical.agent.web.security.AccessGuard;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/health-records")
@PreAuthorize("hasAnyRole('ADMIN','DOCTOR','PATIENT')")
public class HealthRecordController {
    private static final Map<String, Object> DEFAULT_CREATE_ASSESSMENT_OPTIONS = Map.of(
            "_deferAssessment", true,
            "_bestEffortAssessment", true,
            "source", "manual_entry"
    );

    private final HealthRecordService healthRecordService;
    private final AccessGuard accessGuard;

    public HealthRecordController(HealthRecordService healthRecordService,
                                  AccessGuard accessGuard) {
        this.healthRecordService = healthRecordService;
        this.accessGuard = accessGuard;
    }

    @GetMapping("/patient/{patientId}")
    public ResponseEntity<List<HealthRecordDTO>> getByPatientId(@PathVariable Long patientId) {
        accessGuard.requirePatientAccess(patientId);
        return ResponseEntity.ok(healthRecordService.getByPatientId(patientId));
    }

    @PostMapping
    public ResponseEntity<HealthRecordDTO> create(@RequestBody HealthRecordDTO dto) {
        accessGuard.requirePatientAccess(dto.getPatientId());
        return ResponseEntity.ok(healthRecordService.create(dto, null, true, DEFAULT_CREATE_ASSESSMENT_OPTIONS));
    }
}
