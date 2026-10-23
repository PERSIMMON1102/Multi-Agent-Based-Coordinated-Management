package com.medical.agent.web.controller;

import com.aliyun.oss.OSS;
import com.medical.agent.model.entity.Doctor;
import com.medical.agent.model.entity.Patient;
import com.medical.agent.model.entity.PatientMedicalHistory;
import com.medical.agent.model.repository.DoctorRepository;
import com.medical.agent.model.repository.PatientRepository;
import com.medical.agent.model.repository.PatientMedicalHistoryRepository;
import com.medical.agent.service.cache.AiChatCacheInvalidationService;
import com.medical.agent.service.security.UploadSecurityService;
import com.medical.agent.web.config.OssConfig;
import com.medical.agent.web.security.AccessGuard;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/profile")
@PreAuthorize("hasAnyRole('ADMIN','DOCTOR','PATIENT')")
public class UserProfileController {

    private final PatientRepository patientRepository;
    private final PatientMedicalHistoryRepository patientMedicalHistoryRepository;
    private final DoctorRepository doctorRepository;
    private final OSS ossClient;
    private final OssConfig ossConfig;
    private final AccessGuard accessGuard;
    private final AiChatCacheInvalidationService aiChatCacheInvalidationService;
    private final UploadSecurityService uploadSecurityService;

    @Autowired
    public UserProfileController(PatientRepository patientRepository,
                                 PatientMedicalHistoryRepository patientMedicalHistoryRepository,
                                 DoctorRepository doctorRepository,
                                 OssConfig ossConfig,
                                 @org.springframework.lang.Nullable OSS ossClient,
                                 AccessGuard accessGuard,
                                 AiChatCacheInvalidationService aiChatCacheInvalidationService,
                                 UploadSecurityService uploadSecurityService) {
        this.patientRepository = patientRepository;
        this.patientMedicalHistoryRepository = patientMedicalHistoryRepository;
        this.doctorRepository = doctorRepository;
        this.ossConfig = ossConfig;
        this.ossClient = ossClient;
        this.accessGuard = accessGuard;
        this.aiChatCacheInvalidationService = aiChatCacheInvalidationService;
        this.uploadSecurityService = uploadSecurityService;
    }

    UserProfileController(PatientRepository patientRepository,
                          PatientMedicalHistoryRepository patientMedicalHistoryRepository,
                          DoctorRepository doctorRepository,
                          OssConfig ossConfig,
                          @org.springframework.lang.Nullable OSS ossClient) {
        this(patientRepository, patientMedicalHistoryRepository, doctorRepository, ossConfig, ossClient,
                new AccessGuard(null, null, null, null, null, null), null, new UploadSecurityService());
    }

    // ========== 患者 Profile ==========

    @GetMapping("/patient/{id}")
    public ResponseEntity<Map<String, Object>> getPatientProfile(@PathVariable Long id) {
        accessGuard.requirePatientAccess(id);
        return patientRepository.findById(id)
                .map(p -> ResponseEntity.ok(patientToMap(p)))
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/patient/{id}")
    public ResponseEntity<Map<String, Object>> updatePatientProfile(
            @PathVariable Long id, @RequestBody Map<String, Object> body) {
        accessGuard.requirePatientAccess(id);
        return patientRepository.findById(id).map(p -> {
            if (body.containsKey("name")) p.setName((String) body.get("name"));
            if (body.containsKey("gender")) p.setGender((String) body.get("gender"));
            if (body.containsKey("phone")) p.setPhone((String) body.get("phone"));
            if (body.containsKey("address")) p.setAddress((String) body.get("address"));
            if (body.containsKey("emergencyContact")) p.setEmergencyContact((String) body.get("emergencyContact"));
            if (body.containsKey("emergencyPhone")) p.setEmergencyPhone((String) body.get("emergencyPhone"));
            if (body.containsKey("assignedDoctorId")) {
                Object val = body.get("assignedDoctorId");
                if (val == null) {
                    p.setAssignedDoctorId(null);
                } else {
                    try {
                        p.setAssignedDoctorId(Long.valueOf(val.toString()));
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
            if (body.containsKey("avatar")) p.setAvatar((String) body.get("avatar"));
            upsertPatientMedicalHistory(p.getId(), body);
            patientRepository.save(p);
            invalidateAiChatCaches(p.getId());
            return ResponseEntity.ok(patientToMap(p));
        }).orElse(ResponseEntity.notFound().build());
    }

    // ========== 医生 Profile ==========

    @GetMapping("/doctor/{id}")
    public ResponseEntity<Map<String, Object>> getDoctorProfile(@PathVariable Long id) {
        accessGuard.requireDoctorProfileAccess(id);
        return doctorRepository.findById(id)
                .map(d -> ResponseEntity.ok(doctorToMap(d)))
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/doctor/{id}")
    public ResponseEntity<Map<String, Object>> updateDoctorProfile(
            @PathVariable Long id, @RequestBody Map<String, Object> body) {
        accessGuard.requireDoctorProfileAccess(id);
        return doctorRepository.findById(id).map(d -> {
            if (body.containsKey("name")) d.setName((String) body.get("name"));
            if (body.containsKey("department")) d.setDepartment((String) body.get("department"));
            if (body.containsKey("phone")) d.setPhone((String) body.get("phone"));
            if (body.containsKey("avatar")) d.setAvatar((String) body.get("avatar"));
            doctorRepository.save(d);
            return ResponseEntity.ok(doctorToMap(d));
        }).orElse(ResponseEntity.notFound().build());
    }

    // ========== 头像上传 ==========

    @PostMapping("/avatar/upload")
    public ResponseEntity<Map<String, Object>> uploadAvatar(@RequestParam("file") MultipartFile file) {
        accessGuard.currentUser();
        uploadSecurityService.validateAvatarImage(file);

        String originalName = file.getOriginalFilename();
        String ext = "";
        if (originalName != null && originalName.contains(".")) {
            ext = originalName.substring(originalName.lastIndexOf("."));
        }
        String objectKey = "avatars/" + UUID.randomUUID() + ext;

        if (ossClient == null) {
            // OSS 未配置时返回提示
            log.warn("Aliyun OSS not configured, avatar upload skipped");
            return ResponseEntity.ok(Map.of(
                    "url", "/default-avatar.png",
                    "message", "OSS未配置，请在环境变量中配置阿里云OSS参数"
            ));
        }

        try (InputStream is = file.getInputStream()) {
            ossClient.putObject(ossConfig.getBucketName(), objectKey, is);
            String url;
            if (ossConfig.getUrlPrefix() != null && !ossConfig.getUrlPrefix().isBlank()) {
                url = ossConfig.getUrlPrefix() + "/" + objectKey;
            } else {
                String endpoint = ossConfig.getEndpoint().replace("https://", "").replace("http://", "");
                url = "https://" + ossConfig.getBucketName() + "." + endpoint + "/" + objectKey;
            }
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("url", url);
            result.put("objectKey", objectKey);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Avatar upload failed", e);
            return ResponseEntity.internalServerError().body(Map.of("error", "上传失败: " + e.getMessage()));
        }
    }

    // ========== 转换 ==========

    private Map<String, Object> patientToMap(Patient p) {
        PatientMedicalHistory history = patientMedicalHistoryRepository.findByPatientId(p.getId()).orElse(null);
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", p.getId());
        map.put("name", p.getName());
        map.put("gender", p.getGender());
        map.put("birthDate", p.getBirthDate() != null ? p.getBirthDate().toString() : null);
        map.put("phone", p.getPhone());
        map.put("idCard", p.getIdCard());
        map.put("address", p.getAddress());
        map.put("avatar", p.getAvatar());
        map.put("emergencyContact", p.getEmergencyContact());
        map.put("emergencyPhone", p.getEmergencyPhone());
        map.put("assignedDoctorId", p.getAssignedDoctorId());
        map.put("medicalHistory", medicalHistoryToMap(history));
        map.put("createdAt", p.getCreatedAt() != null ? p.getCreatedAt().toString() : null);
        return map;
    }

    private Map<String, Object> doctorToMap(Doctor d) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", d.getId());
        map.put("name", d.getName());
        map.put("department", d.getDepartment());
        map.put("phone", d.getPhone());
        map.put("username", d.getUsername());
        map.put("avatar", d.getAvatar());
        map.put("createdAt", d.getCreatedAt() != null ? d.getCreatedAt().toString() : null);
        return map;
    }

    private void upsertPatientMedicalHistory(Long patientId, Map<String, Object> body) {
        Map<String, Object> payload = extractMedicalHistoryPayload(body);
        if (payload.isEmpty()) {
            return;
        }
        PatientMedicalHistory history = patientMedicalHistoryRepository.findByPatientId(patientId)
                .orElseGet(() -> PatientMedicalHistory.builder().patientId(patientId).build());
        if (payload.containsKey("pastDiseases")) history.setPastDiseases(asNullableString(payload.get("pastDiseases")));
        if (payload.containsKey("surgicalHistory")) history.setSurgicalHistory(asNullableString(payload.get("surgicalHistory")));
        if (payload.containsKey("drugAllergies")) history.setDrugAllergies(asNullableString(payload.get("drugAllergies")));
        if (payload.containsKey("foodAllergies")) history.setFoodAllergies(asNullableString(payload.get("foodAllergies")));
        if (payload.containsKey("immunizationHistory")) history.setImmunizationHistory(asNullableString(payload.get("immunizationHistory")));
        if (payload.containsKey("familyHistory")) history.setFamilyHistory(asNullableString(payload.get("familyHistory")));
        if (payload.containsKey("otherNotes")) history.setOtherNotes(asNullableString(payload.get("otherNotes")));
        patientMedicalHistoryRepository.save(history);
    }

    private void invalidateAiChatCaches(Long patientId) {
        if (aiChatCacheInvalidationService != null) {
            aiChatCacheInvalidationService.invalidatePatient(patientId);
        }
    }

    private Map<String, Object> extractMedicalHistoryPayload(Map<String, Object> body) {
        if (body == null || body.isEmpty()) {
            return Map.of();
        }

        Object nested = body.get("medicalHistory");
        if (nested instanceof Map<?, ?> nestedMap) {
            Map<String, Object> result = new LinkedHashMap<>();
            nestedMap.forEach((key, value) -> {
                if (key != null) {
                    result.put(String.valueOf(key), value);
                }
            });
            return result;
        }

        Map<String, Object> result = new LinkedHashMap<>();
        copyIfPresent(body, result, "pastDiseases");
        copyIfPresent(body, result, "surgicalHistory");
        copyIfPresent(body, result, "drugAllergies");
        copyIfPresent(body, result, "foodAllergies");
        copyIfPresent(body, result, "immunizationHistory");
        copyIfPresent(body, result, "familyHistory");
        copyIfPresent(body, result, "otherNotes");
        return result;
    }

    private void copyIfPresent(Map<String, Object> source, Map<String, Object> target, String key) {
        if (source.containsKey(key)) {
            target.put(key, source.get(key));
        }
    }

    private Map<String, Object> medicalHistoryToMap(PatientMedicalHistory history) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("pastDiseases", history != null ? history.getPastDiseases() : null);
        map.put("surgicalHistory", history != null ? history.getSurgicalHistory() : null);
        map.put("drugAllergies", history != null ? history.getDrugAllergies() : null);
        map.put("foodAllergies", history != null ? history.getFoodAllergies() : null);
        map.put("immunizationHistory", history != null ? history.getImmunizationHistory() : null);
        map.put("familyHistory", history != null ? history.getFamilyHistory() : null);
        map.put("otherNotes", history != null ? history.getOtherNotes() : null);
        map.put("updatedAt", history != null && history.getUpdatedAt() != null ? history.getUpdatedAt().toString() : null);
        return map;
    }

    private String asNullableString(Object value) {
        if (value == null) {
            return null;
        }
        String text = value.toString().trim();
        return text.isEmpty() ? null : text;
    }
}
