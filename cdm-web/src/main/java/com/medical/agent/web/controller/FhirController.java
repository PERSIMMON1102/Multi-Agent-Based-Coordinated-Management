package com.medical.agent.web.controller;

import com.medical.agent.service.FhirService;
import com.medical.agent.web.security.AccessGuard;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/fhir")
@PreAuthorize("hasAnyRole('ADMIN','DOCTOR','PATIENT')")
public class FhirController {

    private final FhirService fhirService;
    private final AccessGuard accessGuard;

    public FhirController(FhirService fhirService,
                          AccessGuard accessGuard) {
        this.fhirService = fhirService;
        this.accessGuard = accessGuard;
    }

    @GetMapping(value = "/patient/{patientId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> getFhirPatient(@PathVariable Long patientId) {
        accessGuard.requirePatientAccess(patientId);
        return ResponseEntity.ok(fhirService.toFhirPatientJson(patientId));
    }

    @GetMapping(value = "/observations/{patientId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> getFhirObservations(@PathVariable Long patientId) {
        accessGuard.requirePatientAccess(patientId);
        return ResponseEntity.ok(fhirService.toFhirObservationsJson(patientId));
    }

    @GetMapping(value = "/paper-cases/{patientId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> getFhirPaperCases(@PathVariable Long patientId) {
        accessGuard.requirePatientAccess(patientId);
        return ResponseEntity.ok(fhirService.toFhirPaperCasesJson(patientId));
    }
}
