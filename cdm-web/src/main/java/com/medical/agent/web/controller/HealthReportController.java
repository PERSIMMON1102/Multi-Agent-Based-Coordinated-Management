package com.medical.agent.web.controller;

import com.medical.agent.service.HealthReportService;
import com.medical.agent.web.security.AccessGuard;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/health-reports")
@PreAuthorize("hasAnyRole('ADMIN','DOCTOR','PATIENT')")
public class HealthReportController {

    private final HealthReportService healthReportService;
    private final AccessGuard accessGuard;

    @Autowired
    public HealthReportController(HealthReportService healthReportService,
                                  AccessGuard accessGuard) {
        this.healthReportService = healthReportService;
        this.accessGuard = accessGuard;
    }

    HealthReportController(HealthReportService healthReportService) {
        this(healthReportService, new AccessGuard(null, null, null, null, null, null));
    }

    @PostMapping("/patient-weekly/{patientId}")
    public ResponseEntity<Map<String, Object>> generatePatientWeekly(@PathVariable Long patientId) {
        accessGuard.requirePatientAccess(patientId);
        return ResponseEntity.ok(healthReportService.generatePatientWeekly(patientId));
    }

    @PostMapping("/doctor-assessment/{patientId}")
    @PreAuthorize("hasAnyRole('ADMIN','DOCTOR')")
    public ResponseEntity<Map<String, Object>> generateDoctorAssessment(@PathVariable Long patientId) {
        accessGuard.requireDoctorPatientAccess(patientId);
        return ResponseEntity.ok(healthReportService.generateDoctorAssessment(patientId));
    }

    @GetMapping("/patient/{patientId}")
    public ResponseEntity<List<Map<String, Object>>> getByPatient(@PathVariable Long patientId,
                                                                  @RequestParam(value = "type", required = false) String type) {
        accessGuard.requirePatientAccess(patientId);
        return ResponseEntity.ok(healthReportService.listByPatient(patientId, type));
    }
}
