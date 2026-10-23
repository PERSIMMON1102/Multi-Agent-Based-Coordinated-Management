package com.medical.agent.web.controller;

import com.medical.agent.common.dto.HealthRecordDTO;
import com.medical.agent.service.AdminDoctorService;
import com.medical.agent.service.AdminDemoDataService;
import com.medical.agent.service.PatientIntegrationService;
import com.medical.agent.web.security.AccessGuard;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final AdminDoctorService adminDoctorService;
    private final AdminDemoDataService adminDemoDataService;
    private final PatientIntegrationService patientIntegrationService;
    private final AccessGuard accessGuard;

    @Autowired
    public AdminController(AdminDoctorService adminDoctorService,
                           AdminDemoDataService adminDemoDataService,
                           PatientIntegrationService patientIntegrationService,
                           AccessGuard accessGuard) {
        this.adminDoctorService = adminDoctorService;
        this.adminDemoDataService = adminDemoDataService;
        this.patientIntegrationService = patientIntegrationService;
        this.accessGuard = accessGuard;
    }

    AdminController(AdminDoctorService adminDoctorService,
                    AdminDemoDataService adminDemoDataService,
                    PatientIntegrationService patientIntegrationService) {
        this(adminDoctorService, adminDemoDataService, patientIntegrationService,
                new AccessGuard(null, null, null, null, null, null));
    }

    @GetMapping("/doctors")
    public ResponseEntity<List<Map<String, Object>>> getDoctors() {
        accessGuard.requireAdmin();
        return ResponseEntity.ok(adminDoctorService.listDoctors());
    }

    @PostMapping("/doctors")
    public ResponseEntity<Map<String, Object>> createDoctor(@RequestBody Map<String, Object> body) {
        accessGuard.requireAdmin();
        return ResponseEntity.ok(adminDoctorService.createDoctor(body));
    }

    @PutMapping("/doctors/{id}")
    public ResponseEntity<Map<String, Object>> updateDoctor(@PathVariable("id") Long id,
                                                            @RequestBody Map<String, Object> body) {
        accessGuard.requireAdmin();
        return ResponseEntity.ok(adminDoctorService.updateDoctor(id, body));
    }

    @DeleteMapping("/doctors/{id}")
    public ResponseEntity<Map<String, Object>> deleteDoctor(@PathVariable("id") Long id) {
        accessGuard.requireAdmin();
        return ResponseEntity.ok(adminDoctorService.deleteDoctor(id));
    }

    @PutMapping("/profile/password")
    public ResponseEntity<Map<String, Object>> changePassword(@RequestBody Map<String, Object> body) {
        accessGuard.requireAdmin();
        body.put("username", accessGuard.currentUser().username());
        return ResponseEntity.ok(adminDoctorService.changeAdminPassword(body));
    }

    @GetMapping("/demo-data/overview")
    public ResponseEntity<Map<String, Object>> getDemoDataOverview() {
        accessGuard.requireAdmin();
        return ResponseEntity.ok(adminDemoDataService.getOverview());
    }

    @GetMapping("/patient-access/overview")
    public ResponseEntity<Map<String, Object>> getPatientAccessOverview() {
        accessGuard.requireAdmin();
        return ResponseEntity.ok(adminDemoDataService.getOverview());
    }

    @PostMapping("/demo-data/refresh")
    public ResponseEntity<Map<String, Object>> refreshDemoData() {
        accessGuard.requireAdmin();
        return ResponseEntity.ok(adminDemoDataService.refreshDemoData());
    }

    @PostMapping("/patient-access/refresh")
    public ResponseEntity<Map<String, Object>> refreshPatientAccessData() {
        accessGuard.requireAdmin();
        return ResponseEntity.ok(adminDemoDataService.refreshDemoData());
    }

    @PostMapping("/demo-data/import-medplum")
    public ResponseEntity<Map<String, Object>> importMockToMedplum(@RequestBody HealthRecordDTO dto) {
        accessGuard.requireAdmin();
        return ResponseEntity.ok(adminDemoDataService.importMockToMedplum(dto, true));
    }

    @PostMapping("/patient-access/import-medplum")
    public ResponseEntity<Map<String, Object>> importPatientAccessMedplumData(@RequestBody HealthRecordDTO dto) {
        accessGuard.requireAdmin();
        return ResponseEntity.ok(adminDemoDataService.importMockToMedplum(dto, true));
    }

    @GetMapping("/patient-integration/overview")
    public ResponseEntity<Map<String, Object>> getPatientIntegrationOverview(@RequestParam(name = "keyword", required = false) String keyword,
                                                                            @RequestParam(name = "readiness", required = false) String readiness) {
        accessGuard.requireAdmin();
        return ResponseEntity.ok(patientIntegrationService.getOverview(keyword, readiness));
    }

    @GetMapping("/patient-integration/{patientId}/status")
    public ResponseEntity<Map<String, Object>> getPatientIntegrationStatus(@PathVariable("patientId") Long patientId) {
        accessGuard.requireAdmin();
        return ResponseEntity.ok(patientIntegrationService.getPatientStatus(patientId));
    }

    @PostMapping("/patient-integration/{patientId}/sync")
    public ResponseEntity<Map<String, Object>> syncPatientIntegration(@PathVariable("patientId") Long patientId) {
        accessGuard.requireAdmin();
        return ResponseEntity.ok(patientIntegrationService.syncPatient(patientId, true));
    }

    @PostMapping("/patient-integration/import-medplum")
    public ResponseEntity<Map<String, Object>> importBusinessMockToMedplum(@RequestBody HealthRecordDTO dto) {
        accessGuard.requireAdmin();
        return ResponseEntity.ok(patientIntegrationService.importMockToMedplum(dto, true));
    }

    @PutMapping("/patient-integration/{patientId}/doctor")
    public ResponseEntity<Map<String, Object>> bindPatientDoctor(@PathVariable("patientId") Long patientId,
                                                                 @RequestBody Map<String, Object> body) {
        accessGuard.requireAdmin();
        Long doctorId = null;
        if (body != null && body.get("doctorId") != null && !body.get("doctorId").toString().isBlank()) {
            doctorId = Long.valueOf(body.get("doctorId").toString());
        }
        return ResponseEntity.ok(patientIntegrationService.bindDoctor(patientId, doctorId));
    }
}
