package com.medical.agent.web.controller;

import com.medical.agent.common.dto.MedicalResourceDTO;
import com.medical.agent.model.entity.ResourceRecommendation;
import com.medical.agent.service.MedicalResourceService;
import com.medical.agent.web.security.AccessGuard;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/resources")
@PreAuthorize("hasAnyRole('ADMIN','DOCTOR','PATIENT')")
public class ResourceController {

    private final MedicalResourceService resourceService;
    private final AccessGuard accessGuard;

    public ResourceController(MedicalResourceService resourceService,
                              AccessGuard accessGuard) {
        this.resourceService = resourceService;
        this.accessGuard = accessGuard;
    }

    @GetMapping
    public ResponseEntity<List<MedicalResourceDTO>> getAll() {
        accessGuard.currentUser();
        return ResponseEntity.ok(resourceService.getAll());
    }

    @GetMapping("/nearby")
    public ResponseEntity<List<MedicalResourceDTO>> findNearby(
            @RequestParam double lat, @RequestParam double lng,
            @RequestParam(defaultValue = "10") double radius) {
        accessGuard.currentUser();
        return ResponseEntity.ok(resourceService.findNearby(lat, lng, radius));
    }

    @GetMapping("/specialty/{specialty}")
    public ResponseEntity<List<MedicalResourceDTO>> findBySpecialty(@PathVariable String specialty) {
        accessGuard.currentUser();
        return ResponseEntity.ok(resourceService.findBySpecialty(specialty));
    }

    @GetMapping("/recommendations/{patientId}")
    public ResponseEntity<List<ResourceRecommendation>> getRecommendations(@PathVariable Long patientId) {
        accessGuard.requirePatientAccess(patientId);
        return ResponseEntity.ok(resourceService.getRecommendationsByPatientId(patientId));
    }
}
