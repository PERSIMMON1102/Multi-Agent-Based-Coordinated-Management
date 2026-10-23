package com.medical.agent.web.controller;

import com.medical.agent.common.dto.ClinicalDataJobDTO;
import com.medical.agent.service.ClinicalDataJobReplayService;
import com.medical.agent.service.MedplumService;
import com.medical.agent.web.security.AccessGuard;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.List;

@RestController
@RequestMapping("/api/medplum")
@PreAuthorize("hasAnyRole('ADMIN','DOCTOR','PATIENT')")
public class MedplumController {

    private static final int DEFAULT_HISTORY_WINDOW = 120;
    private final MedplumService medplumService;
    private final ClinicalDataJobReplayService clinicalDataJobReplayService;
    private final AccessGuard accessGuard;

    public MedplumController(MedplumService medplumService,
                             ClinicalDataJobReplayService clinicalDataJobReplayService,
                             AccessGuard accessGuard) {
        this.medplumService = medplumService;
        this.clinicalDataJobReplayService = clinicalDataJobReplayService;
        this.accessGuard = accessGuard;
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus(@RequestParam(defaultValue = "true") boolean probe) {
        accessGuard.currentUser();
        return ResponseEntity.ok(medplumService.getStatus(probe));
    }

    @PostMapping("/patient/{patientId}/sync")
    @PreAuthorize("hasAnyRole('ADMIN','DOCTOR')")
    public ResponseEntity<Map<String, Object>> syncPatient(@PathVariable("patientId") Long patientId,
                                                           @RequestParam(name = "includeObservations", defaultValue = "true") boolean includeObservations) {
        accessGuard.requireDoctorPatientAccess(patientId);
        return ResponseEntity.ok(medplumService.syncPatient(patientId, includeObservations));
    }

    @PostMapping("/sync-all")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> syncAllPatients(@RequestParam(name = "includeObservations", defaultValue = "true") boolean includeObservations,
                                                               @RequestParam(name = "continueOnError", defaultValue = "true") boolean continueOnError) {
        accessGuard.requireAdmin();
        return ResponseEntity.ok(medplumService.syncAllPatients(includeObservations, continueOnError));
    }

    @GetMapping("/patient/{patientId}")
    public ResponseEntity<Map<String, Object>> getPatientByLocalId(@PathVariable("patientId") Long patientId) {
        accessGuard.requirePatientAccess(patientId);
        return ResponseEntity.ok(medplumService.getPatientByLocalId(patientId));
    }

    @GetMapping("/patient/{patientId}/observations")
    public ResponseEntity<Map<String, Object>> getLatestObservations(@PathVariable("patientId") Long patientId,
                                                                     @RequestParam(name = "limit", defaultValue = "5") int limit) {
        accessGuard.requirePatientAccess(patientId);
        int effectiveLimit = limit > 0 ? Math.min(limit, DEFAULT_HISTORY_WINDOW) : DEFAULT_HISTORY_WINDOW;
        return ResponseEntity.ok(medplumService.getLatestObservationsForLocalPatient(patientId, effectiveLimit));
    }

    @GetMapping("/patient/{patientId}/paper-cases")
    public ResponseEntity<Map<String, Object>> getPaperCaseResources(@PathVariable("patientId") Long patientId,
                                                                     @RequestParam(name = "limit", defaultValue = "5") int limit) {
        accessGuard.requirePatientAccess(patientId);
        int effectiveLimit = limit > 0 ? Math.min(limit, DEFAULT_HISTORY_WINDOW) : DEFAULT_HISTORY_WINDOW;
        return ResponseEntity.ok(medplumService.getPaperCaseResourcesForLocalPatient(patientId, effectiveLimit));
    }

    @GetMapping("/jobs/failed")
    @PreAuthorize("hasAnyRole('ADMIN','DOCTOR')")
    public ResponseEntity<List<ClinicalDataJobDTO>> getFailedJobs() {
        accessGuard.requireDoctorOrAdmin();
        if (accessGuard.currentUser().isDoctor()) {
            return ResponseEntity.ok(clinicalDataJobReplayService.listFailedJobsForDoctor(accessGuard.currentUser().id()));
        }
        return ResponseEntity.ok(clinicalDataJobReplayService.listFailedJobs());
    }

    @GetMapping("/jobs/replayable")
    @PreAuthorize("hasAnyRole('ADMIN','DOCTOR')")
    public ResponseEntity<List<ClinicalDataJobDTO>> getReplayableJobs() {
        accessGuard.requireDoctorOrAdmin();
        if (accessGuard.currentUser().isDoctor()) {
            return ResponseEntity.ok(clinicalDataJobReplayService.listReplayableJobsForDoctor(accessGuard.currentUser().id()));
        }
        return ResponseEntity.ok(clinicalDataJobReplayService.listReplayableJobs());
    }

    @PostMapping("/jobs/{jobId}/replay-sync")
    @PreAuthorize("hasAnyRole('ADMIN','DOCTOR')")
    public ResponseEntity<Map<String, Object>> replayJobSync(@PathVariable("jobId") Long jobId) {
        accessGuard.requireDoctorOrAdmin();
        if (accessGuard.currentUser().isDoctor()) {
            return ResponseEntity.ok(clinicalDataJobReplayService.replayMedplumSyncForDoctor(accessGuard.currentUser().id(), jobId));
        }
        return ResponseEntity.ok(clinicalDataJobReplayService.replayMedplumSync(jobId));
    }

    @PostMapping("/jobs/replay-failed")
    @PreAuthorize("hasAnyRole('ADMIN','DOCTOR')")
    public ResponseEntity<Map<String, Object>> replayFailedJobs(
            @RequestParam(name = "limit", required = false) Integer limit) {
        accessGuard.requireDoctorOrAdmin();
        if (accessGuard.currentUser().isDoctor()) {
            return ResponseEntity.ok(clinicalDataJobReplayService.replayReplayableJobsForDoctor(accessGuard.currentUser().id(), limit));
        }
        return ResponseEntity.ok(clinicalDataJobReplayService.replayReplayableJobs(limit));
    }
}
