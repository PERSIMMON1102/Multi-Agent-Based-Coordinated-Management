package com.medical.agent.web.controller;

import com.medical.agent.common.dto.PatientDTO;
import com.medical.agent.model.entity.Patient;
import com.medical.agent.model.entity.PatientMedicalHistory;
import com.medical.agent.model.repository.PatientMedicalHistoryRepository;
import com.medical.agent.model.repository.PatientRepository;
import com.medical.agent.service.PatientService;
import com.medical.agent.service.cache.AiChatCacheInvalidationService;
import com.medical.agent.web.security.AccessGuard;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/patients")
public class PatientController {

    private final PatientService patientService;
    private final PatientRepository patientRepository;
    private final PatientMedicalHistoryRepository patientMedicalHistoryRepository;
    private final AccessGuard accessGuard;
    private final AiChatCacheInvalidationService aiChatCacheInvalidationService;

    @Autowired
    public PatientController(PatientService patientService,
                             PatientRepository patientRepository,
                             PatientMedicalHistoryRepository patientMedicalHistoryRepository,
                             AccessGuard accessGuard,
                             AiChatCacheInvalidationService aiChatCacheInvalidationService) {
        this.patientService = patientService;
        this.patientRepository = patientRepository;
        this.patientMedicalHistoryRepository = patientMedicalHistoryRepository;
        this.accessGuard = accessGuard;
        this.aiChatCacheInvalidationService = aiChatCacheInvalidationService;
    }

    PatientController(PatientService patientService,
                      PatientRepository patientRepository,
                      PatientMedicalHistoryRepository patientMedicalHistoryRepository) {
        this(patientService, patientRepository, patientMedicalHistoryRepository,
                new AccessGuard(null, null, null, null, null, null), null);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','DOCTOR')")
    public ResponseEntity<List<PatientDTO>> listAll(@RequestParam(required = false) String name,
                                                     @RequestParam(required = false) Long doctorId) {
        Long effectiveDoctorId = accessGuard.currentUser().isDoctor()
                ? accessGuard.resolveDoctorIdOrCurrent(doctorId)
                : doctorId;
        if (effectiveDoctorId != null) {
            if (name != null && !name.isEmpty()) {
                return ResponseEntity.ok(patientService.listByDoctorIdAndName(effectiveDoctorId, name));
            }
            return ResponseEntity.ok(patientService.listByDoctorId(effectiveDoctorId));
        }
        if (name != null && !name.isEmpty()) {
            return ResponseEntity.ok(patientService.searchByName(name));
        }
        return ResponseEntity.ok(patientService.listAll());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','DOCTOR','PATIENT')")
    public ResponseEntity<PatientDTO> getById(@PathVariable Long id) {
        accessGuard.requirePatientAccess(id);
        return ResponseEntity.ok(patientService.getById(id));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','DOCTOR')")
    public ResponseEntity<PatientDTO> create(@Valid @RequestBody PatientDTO dto) {
        accessGuard.requireDoctorOrAdmin();
        return ResponseEntity.ok(patientService.create(dto));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','DOCTOR')")
    public ResponseEntity<PatientDTO> update(@PathVariable Long id, @Valid @RequestBody PatientDTO dto) {
        accessGuard.requireDoctorPatientAccess(id);
        return ResponseEntity.ok(patientService.update(id, dto));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','DOCTOR')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        accessGuard.requireDoctorPatientAccess(id);
        patientService.delete(id);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/{id}/complete-profile")
    @PreAuthorize("hasAnyRole('ADMIN','DOCTOR','PATIENT')")
    public ResponseEntity<?> completeProfile(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        accessGuard.requirePatientAccess(id);
        return patientRepository.findById(id)
                .map(patient -> {
                    if (body.containsKey("name") && body.get("name") != null) {
                        patient.setName(body.get("name").toString());
                    }
                    if (body.containsKey("gender") && body.get("gender") != null) {
                        patient.setGender(body.get("gender").toString());
                    }
                    if (body.containsKey("birthDate") && body.get("birthDate") != null) {
                        patient.setBirthDate(LocalDate.parse(body.get("birthDate").toString()));
                    }
                    if (body.containsKey("phone") && body.get("phone") != null) {
                        patient.setPhone(body.get("phone").toString());
                    }
                    if (body.containsKey("address") && body.get("address") != null) {
                        patient.setAddress(body.get("address").toString());
                    }
                    if (body.containsKey("assignedDoctorId") && body.get("assignedDoctorId") != null) {
                        patient.setAssignedDoctorId(Long.valueOf(body.get("assignedDoctorId").toString()));
                    }
                    if (body.containsKey("emergencyContact") && body.get("emergencyContact") != null) {
                        patient.setEmergencyContact(body.get("emergencyContact").toString());
                    }
                    if (body.containsKey("emergencyPhone") && body.get("emergencyPhone") != null) {
                        patient.setEmergencyPhone(body.get("emergencyPhone").toString());
                    }
                    upsertMedicalHistory(patient.getId(), body);
                    patient.setProfileCompleted(true);
                    patientRepository.save(patient);
                    invalidateAiChatCaches(patient.getId());
                    Map<String, Object> result = new java.util.LinkedHashMap<>();
                    result.put("message", "资料完善成功");
                    result.put("profileCompleted", true);
                    result.put("assignedDoctorId", patient.getAssignedDoctorId());
                    result.put("patientId", patient.getId());
                    return ResponseEntity.ok(result);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    private void upsertMedicalHistory(Long patientId, Map<String, Object> body) {
        Object nested = body.get("medicalHistory");
        if (!(nested instanceof Map<?, ?> nestedMap)) {
            return;
        }
        PatientMedicalHistory history = patientMedicalHistoryRepository.findByPatientId(patientId)
                .orElseGet(() -> PatientMedicalHistory.builder().patientId(patientId).build());
        history.setPastDiseases(asNullableString(nestedMap.get("pastDiseases")));
        history.setSurgicalHistory(asNullableString(nestedMap.get("surgicalHistory")));
        history.setDrugAllergies(asNullableString(nestedMap.get("drugAllergies")));
        history.setFoodAllergies(asNullableString(nestedMap.get("foodAllergies")));
        history.setImmunizationHistory(asNullableString(nestedMap.get("immunizationHistory")));
        history.setFamilyHistory(asNullableString(nestedMap.get("familyHistory")));
        history.setOtherNotes(asNullableString(nestedMap.get("otherNotes")));
        patientMedicalHistoryRepository.save(history);
    }

    private void invalidateAiChatCaches(Long patientId) {
        if (aiChatCacheInvalidationService != null) {
            aiChatCacheInvalidationService.invalidatePatient(patientId);
        }
    }

    private String asNullableString(Object value) {
        if (value == null) {
            return null;
        }
        String text = value.toString().trim();
        return text.isEmpty() ? null : text;
    }
}
