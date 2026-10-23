package com.medical.agent.web.controller;

import com.medical.agent.common.dto.AssessmentResultDTO;
import com.medical.agent.common.enums.DiseaseType;
import com.medical.agent.service.AssessmentService;
import com.medical.agent.service.ClinicalAssessmentOrchestrator;
import com.medical.agent.web.security.AccessGuard;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/assessments")
@PreAuthorize("hasAnyRole('ADMIN','DOCTOR','PATIENT')")
public class AssessmentController {

    private final AssessmentService assessmentService;
    private final ClinicalAssessmentOrchestrator clinicalAssessmentOrchestrator;
    private final AccessGuard accessGuard;

    @Autowired
    public AssessmentController(AssessmentService assessmentService,
                                ClinicalAssessmentOrchestrator clinicalAssessmentOrchestrator,
                                AccessGuard accessGuard) {
        this.assessmentService = assessmentService;
        this.clinicalAssessmentOrchestrator = clinicalAssessmentOrchestrator;
        this.accessGuard = accessGuard;
    }

    AssessmentController(AssessmentService assessmentService,
                         ClinicalAssessmentOrchestrator clinicalAssessmentOrchestrator) {
        this(assessmentService, clinicalAssessmentOrchestrator, new AccessGuard(null, null, null, null, null, null));
    }

    @GetMapping("/patient/{patientId}")
    public ResponseEntity<List<AssessmentResultDTO>> getByPatientId(@PathVariable Long patientId) {
        accessGuard.requirePatientAccess(patientId);
        return ResponseEntity.ok(assessmentService.getByPatientId(patientId));
    }

    @GetMapping("/patient/{patientId}/disease/{diseaseType}")
    public ResponseEntity<List<AssessmentResultDTO>> getByPatientAndDisease(
            @PathVariable Long patientId, @PathVariable DiseaseType diseaseType) {
        accessGuard.requirePatientAccess(patientId);
        return ResponseEntity.ok(assessmentService.getByPatientAndDisease(patientId, diseaseType));
    }

    @GetMapping("/patient/{patientId}/latest/{diseaseType}")
    public ResponseEntity<AssessmentResultDTO> getLatest(
            @PathVariable Long patientId, @PathVariable DiseaseType diseaseType) {
        accessGuard.requirePatientAccess(patientId);
        AssessmentResultDTO result = assessmentService.getLatest(patientId, diseaseType);
        return result != null ? ResponseEntity.ok(result) : ResponseEntity.noContent().build();
    }

    @PostMapping("/trigger/{patientId}")
    @PreAuthorize("hasAnyRole('ADMIN','DOCTOR')")
    public ResponseEntity<Map<String, String>> triggerAssessment(
            @PathVariable Long patientId,
            @RequestParam(required = false) DiseaseType diseaseType,
            @RequestBody Map<String, Object> healthData) {
        accessGuard.requireDoctorPatientAccess(patientId);
        clinicalAssessmentOrchestrator.triggerAssessment(patientId, diseaseType, healthData);
        return ResponseEntity.ok(Map.of("message", "评估请求已发送至智能体", "patientId", patientId.toString()));
    }
}
