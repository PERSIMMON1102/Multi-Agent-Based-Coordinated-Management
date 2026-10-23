package com.medical.agent.web.controller;

import com.medical.agent.common.dto.FollowUpCompletionDTO;
import com.medical.agent.common.dto.FollowUpPlanDTO;
import com.medical.agent.service.FollowUpService;
import com.medical.agent.web.security.AccessGuard;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/follow-ups")
@PreAuthorize("hasAnyRole('ADMIN','DOCTOR','PATIENT')")
public class FollowUpController {

    private final FollowUpService followUpService;
    private final AccessGuard accessGuard;

    public FollowUpController(FollowUpService followUpService,
                              AccessGuard accessGuard) {
        this.followUpService = followUpService;
        this.accessGuard = accessGuard;
    }

    @GetMapping("/patient/{patientId}")
    public ResponseEntity<List<FollowUpPlanDTO>> getByPatientId(@PathVariable Long patientId) {
        accessGuard.requireFollowUpAccess(patientId);
        return ResponseEntity.ok(followUpService.getByPatientId(patientId));
    }

    @GetMapping("/overdue")
    @PreAuthorize("hasAnyRole('ADMIN','DOCTOR')")
    public ResponseEntity<List<FollowUpPlanDTO>> getOverdue() {
        accessGuard.requireDoctorOrAdmin();
        return ResponseEntity.ok(followUpService.getOverduePlans());
    }

    @GetMapping("/pending")
    @PreAuthorize("hasAnyRole('ADMIN','DOCTOR')")
    public ResponseEntity<List<FollowUpPlanDTO>> getPending() {
        accessGuard.requireDoctorOrAdmin();
        return ResponseEntity.ok(followUpService.getPendingPlans());
    }

    @GetMapping("/completed")
    @PreAuthorize("hasAnyRole('ADMIN','DOCTOR')")
    public ResponseEntity<List<FollowUpPlanDTO>> getCompleted() {
        accessGuard.requireDoctorOrAdmin();
        return ResponseEntity.ok(followUpService.getCompletedPlans());
    }

    @GetMapping("/in-progress")
    @PreAuthorize("hasAnyRole('ADMIN','DOCTOR')")
    public ResponseEntity<List<FollowUpPlanDTO>> getInProgress() {
        accessGuard.requireDoctorOrAdmin();
        return ResponseEntity.ok(followUpService.getInProgressPlans());
    }

    @GetMapping("/cancelled")
    @PreAuthorize("hasAnyRole('ADMIN','DOCTOR')")
    public ResponseEntity<List<FollowUpPlanDTO>> getCancelled() {
        accessGuard.requireDoctorOrAdmin();
        return ResponseEntity.ok(followUpService.getCancelledPlans());
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','DOCTOR')")
    public ResponseEntity<FollowUpPlanDTO> create(@RequestBody FollowUpPlanDTO dto) {
        accessGuard.requireDoctorPatientAccess(dto.getPatientId());
        return ResponseEntity.ok(followUpService.createFollowUp(dto));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','DOCTOR')")
    public ResponseEntity<FollowUpPlanDTO> update(@PathVariable Long id, @RequestBody FollowUpPlanDTO dto) {
        accessGuard.requireFollowUpManageById(id);
        return ResponseEntity.ok(followUpService.updateFollowUp(id, dto));
    }

    @PutMapping("/{id}/start")
    @PreAuthorize("hasAnyRole('ADMIN','DOCTOR')")
    public ResponseEntity<FollowUpPlanDTO> startFollowUp(@PathVariable Long id) {
        accessGuard.requireFollowUpManageById(id);
        return ResponseEntity.ok(followUpService.startFollowUp(id));
    }

    @PutMapping("/{id}/complete")
    public ResponseEntity<FollowUpPlanDTO> completeFollowUp(@PathVariable Long id,
                                                            @RequestBody(required = false) FollowUpCompletionDTO completion) {
        accessGuard.requireFollowUpCompleteById(id);
        return ResponseEntity.ok(followUpService.completeFollowUp(id, completion));
    }

    @PutMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('ADMIN','DOCTOR')")
    public ResponseEntity<FollowUpPlanDTO> cancelFollowUp(@PathVariable Long id) {
        accessGuard.requireFollowUpManageById(id);
        return ResponseEntity.ok(followUpService.cancelFollowUp(id));
    }
}
