package com.medical.agent.web.controller;

import com.medical.agent.service.HealthEducationService;
import com.medical.agent.web.security.AccessGuard;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/education")
@PreAuthorize("hasAnyRole('ADMIN','DOCTOR','PATIENT')")
public class HealthEducationController {

    private final HealthEducationService educationService;
    private final AccessGuard accessGuard;

    @Autowired
    public HealthEducationController(HealthEducationService educationService,
                                     AccessGuard accessGuard) {
        this.educationService = educationService;
        this.accessGuard = accessGuard;
    }

    HealthEducationController(HealthEducationService educationService) {
        this(educationService, new AccessGuard(null, null, null, null, null, null));
    }

    @PostMapping("/generate/{patientId}")
    public ResponseEntity<Map<String, Object>> generate(@PathVariable Long patientId) {
        accessGuard.requirePatientAccess(patientId);
        return ResponseEntity.ok(educationService.generateEducation(patientId));
    }

    @GetMapping("/patient/{patientId}")
    public ResponseEntity<List<Map<String, Object>>> getByPatient(@PathVariable Long patientId,
                                                                  @RequestParam(required = false) Integer limit) {
        accessGuard.requirePatientAccess(patientId);
        return ResponseEntity.ok(educationService.getByPatient(patientId, limit));
    }
}
