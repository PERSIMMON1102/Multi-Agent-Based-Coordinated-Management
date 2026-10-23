package com.medical.agent.web.controller;

import com.medical.agent.common.dto.ClinicalDataJobDTO;
import com.medical.agent.common.dto.ClinicalDataJobSummaryDTO;
import com.medical.agent.service.clinicaljob.ClinicalDataJobAdminService;
import com.medical.agent.service.clinicaljob.ClinicalDataJobDoctorViewService;
import com.medical.agent.web.security.AccessGuard;
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
@RequestMapping("/api/clinical-data-jobs")
@PreAuthorize("hasAnyRole('ADMIN','DOCTOR')")
public class ClinicalDataJobController {

    private final ClinicalDataJobAdminService clinicalDataJobAdminService;
    private final ClinicalDataJobDoctorViewService clinicalDataJobDoctorViewService;
    private final AccessGuard accessGuard;

    public ClinicalDataJobController(ClinicalDataJobAdminService clinicalDataJobAdminService,
                                     ClinicalDataJobDoctorViewService clinicalDataJobDoctorViewService,
                                     AccessGuard accessGuard) {
        this.clinicalDataJobAdminService = clinicalDataJobAdminService;
        this.clinicalDataJobDoctorViewService = clinicalDataJobDoctorViewService;
        this.accessGuard = accessGuard;
    }

    @GetMapping
    public ResponseEntity<List<ClinicalDataJobDTO>> listByStatus(@RequestParam("status") String status) {
        accessGuard.requireDoctorOrAdmin();
        if (accessGuard.currentUser().isDoctor()) {
            return ResponseEntity.ok(clinicalDataJobDoctorViewService.listByStatus(accessGuard.currentUser().id(), status));
        }
        return ResponseEntity.ok(clinicalDataJobAdminService.listByStatus(status));
    }

    @GetMapping("/patient/{patientId}")
    public ResponseEntity<List<ClinicalDataJobDTO>> listByPatient(@PathVariable("patientId") Long patientId,
                                                                  @RequestParam(value = "status", required = false) String status) {
        accessGuard.requireDoctorOrAdmin();
        if (accessGuard.currentUser().isDoctor()) {
            return ResponseEntity.ok(clinicalDataJobDoctorViewService.listByPatient(accessGuard.currentUser().id(), patientId, status));
        }
        return ResponseEntity.ok(clinicalDataJobAdminService.listByPatient(patientId, status));
    }

    @GetMapping("/summary")
    public ResponseEntity<ClinicalDataJobSummaryDTO> summary() {
        accessGuard.requireDoctorOrAdmin();
        if (accessGuard.currentUser().isDoctor()) {
            return ResponseEntity.ok(clinicalDataJobDoctorViewService.getSummary(accessGuard.currentUser().id()));
        }
        return ResponseEntity.ok(clinicalDataJobAdminService.getSummary());
    }

    @GetMapping("/correlation/{correlationId}")
    public ResponseEntity<List<ClinicalDataJobDTO>> listByCorrelationId(@PathVariable("correlationId") String correlationId) {
        accessGuard.requireDoctorOrAdmin();
        if (accessGuard.currentUser().isDoctor()) {
            return ResponseEntity.ok(clinicalDataJobDoctorViewService.listByCorrelationId(accessGuard.currentUser().id(), correlationId));
        }
        return ResponseEntity.ok(clinicalDataJobAdminService.listByCorrelationId(correlationId));
    }

    @GetMapping("/retryable")
    public ResponseEntity<List<ClinicalDataJobDTO>> listRetryableDue() {
        accessGuard.requireDoctorOrAdmin();
        if (accessGuard.currentUser().isDoctor()) {
            return ResponseEntity.ok(clinicalDataJobDoctorViewService.listRetryableDue(accessGuard.currentUser().id()));
        }
        return ResponseEntity.ok(clinicalDataJobAdminService.listRetryableDue());
    }

    @GetMapping("/patient/{patientId}/retryable")
    public ResponseEntity<List<ClinicalDataJobDTO>> listRetryableByPatient(@PathVariable("patientId") Long patientId) {
        accessGuard.requireDoctorOrAdmin();
        if (accessGuard.currentUser().isDoctor()) {
            return ResponseEntity.ok(clinicalDataJobDoctorViewService.listRetryableByPatient(accessGuard.currentUser().id(), patientId));
        }
        return ResponseEntity.ok(clinicalDataJobAdminService.listRetryableByPatient(patientId));
    }

    @PostMapping("/{jobId}/requeue")
    public ResponseEntity<Map<String, Object>> requeue(@PathVariable("jobId") Long jobId) {
        accessGuard.requireDoctorOrAdmin();
        if (accessGuard.currentUser().isDoctor()) {
            return ResponseEntity.ok(clinicalDataJobDoctorViewService.requeueJob(accessGuard.currentUser().id(), jobId));
        }
        return ResponseEntity.ok(clinicalDataJobAdminService.requeueJob(jobId));
    }

    @PostMapping("/{jobId}/cancel")
    public ResponseEntity<Map<String, Object>> cancel(@PathVariable("jobId") Long jobId,
                                                      @RequestParam(value = "reason", required = false) String reason) {
        accessGuard.requireDoctorOrAdmin();
        if (accessGuard.currentUser().isDoctor()) {
            return ResponseEntity.ok(clinicalDataJobDoctorViewService.cancelJob(accessGuard.currentUser().id(), jobId, reason));
        }
        return ResponseEntity.ok(clinicalDataJobAdminService.cancelJob(jobId, reason));
    }

    @PostMapping("/requeue-due")
    public ResponseEntity<Map<String, Object>> requeueDue() {
        accessGuard.requireDoctorOrAdmin();
        if (accessGuard.currentUser().isDoctor()) {
            return ResponseEntity.ok(clinicalDataJobDoctorViewService.requeueAllDue(accessGuard.currentUser().id()));
        }
        return ResponseEntity.ok(clinicalDataJobAdminService.requeueAllDueJobs());
    }
}
