package com.medical.agent.service;

import com.medical.agent.common.dto.HealthRecordDTO;
import com.medical.agent.model.entity.Doctor;
import com.medical.agent.model.entity.HealthRecord;
import com.medical.agent.model.entity.Patient;
import com.medical.agent.model.entity.PatientMedicalHistory;
import com.medical.agent.model.repository.DoctorRepository;
import com.medical.agent.model.repository.HealthRecordRepository;
import com.medical.agent.model.repository.PatientMedicalHistoryRepository;
import com.medical.agent.model.repository.PatientRepository;
import com.medical.agent.service.cache.CacheNames;
import com.medical.agent.service.chat.ChatContextService;
import com.medical.agent.service.chat.ChatPatientContextData;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class PatientIntegrationService {

    private static final String DEMO_PATIENT_PREFIX = "demo-patient-";

    private final PatientRepository patientRepository;
    private final DoctorRepository doctorRepository;
    private final PatientMedicalHistoryRepository patientMedicalHistoryRepository;
    private final HealthRecordRepository healthRecordRepository;
    private final ChatContextService chatContextService;
    private final MedplumService medplumService;

    public PatientIntegrationService(PatientRepository patientRepository,
                                     DoctorRepository doctorRepository,
                                     PatientMedicalHistoryRepository patientMedicalHistoryRepository,
                                     HealthRecordRepository healthRecordRepository,
                                     ChatContextService chatContextService,
                                     MedplumService medplumService) {
        this.patientRepository = patientRepository;
        this.doctorRepository = doctorRepository;
        this.patientMedicalHistoryRepository = patientMedicalHistoryRepository;
        this.healthRecordRepository = healthRecordRepository;
        this.chatContextService = chatContextService;
        this.medplumService = medplumService;
    }

    public Map<String, Object> getOverview() {
        return getOverview(null, null);
    }

    @Cacheable(cacheNames = CacheNames.PATIENT_INTEGRATION_OVERVIEW, key = "(#keyword == null ? '' : #keyword) + '::' + (#readiness == null ? '' : #readiness)", sync = true)
    public Map<String, Object> getOverview(String keyword, String readiness) {
        Map<String, Object> medplumStatus = medplumService != null ? medplumService.getStatus(false) : Map.of();
        List<Patient> allPatients = findBusinessPatients();
        List<Patient> patients = allPatients.stream()
                .filter(patient -> matchesKeyword(patient, keyword == null ? "" : keyword.trim().toLowerCase()))
                .toList();

        List<Map<String, Object>> rows = patients.stream()
                .map(patient -> buildPatientRow(patient, medplumStatus))
                .filter(row -> matchesReadiness(row, readiness == null ? "" : readiness.trim().toLowerCase()))
                .toList();

        List<Map<String, Object>> allRows = allPatients.stream()
                .map(patient -> buildPatientRow(patient, medplumStatus))
                .toList();

        long assignedDoctorCount = allPatients.stream()
                .filter(patient -> patient.getAssignedDoctorId() != null)
                .count();
        long profileCompletedCount = allPatients.stream()
                .filter(patient -> Boolean.TRUE.equals(patient.getProfileCompleted()))
                .count();
        long readyForAgentCount = allRows.stream()
                .filter(row -> Boolean.TRUE.equals(row.get("agentContextReady")))
                .count();
        long medplumMappedCount = allRows.stream()
                .filter(row -> Boolean.TRUE.equals(row.get("medplumContextReady")))
                .count();
        long localRecordCount = allPatients.stream()
                .map(Patient::getId)
                .filter(id -> id != null)
                .mapToLong(id -> healthRecordRepository.findByPatientIdOrderByRecordDateDesc(id).size())
                .sum();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("registeredPatients", allPatients.size());
        result.put("assignedPatients", assignedDoctorCount);
        result.put("profileCompletedPatients", profileCompletedCount);
        result.put("localHealthRecords", localRecordCount);
        result.put("medplumMappedPatients", medplumMappedCount);
        result.put("agentReadyPatients", readyForAgentCount);
        result.put("filteredPatients", rows.size());
        result.put("filters", Map.of(
                "keyword", keyword == null ? "" : keyword.trim(),
                "readiness", readiness == null ? "" : readiness.trim()
        ));
        result.put("medplum", Map.of(
                "enabled", Boolean.TRUE.equals(medplumStatus.get("enabled")),
                "configured", Boolean.TRUE.equals(medplumStatus.get("configured")),
                "authenticated", Boolean.TRUE.equals(medplumStatus.get("authenticated")),
                "message", stringValue(medplumStatus.get("message"))
        ));
        result.put("patientOptions", allPatients.stream()
                .map(patient -> Map.<String, Object>of(
                        "patientId", patient.getId(),
                        "name", stringValue(patient.getName()),
                        "username", stringValue(patient.getUsername())
                ))
                .toList());
        result.put("patients", rows);
        result.put("message", "患者接入概览已加载。");
        return result;
    }

    @Cacheable(cacheNames = CacheNames.PATIENT_INTEGRATION_STATUS, key = "#patientId", sync = true)
    public Map<String, Object> getPatientStatus(Long patientId) {
        Patient patient = patientRepository.findById(patientId).orElse(null);
        if (patient == null || isDemoPatient(patient)) {
            return Map.of(
                    "patientId", patientId,
                    "found", false,
                    "message", "未找到正式患者接入记录。"
            );
        }

        Map<String, Object> medplumStatus = medplumService != null ? medplumService.getStatus(false) : Map.of();
        Map<String, Object> row = buildPatientRow(patient, medplumStatus);
        Map<String, Object> result = new LinkedHashMap<>(row);
        result.put("found", true);
        result.put("message", row.getOrDefault("agentContextMessage", "患者接入状态已加载。"));
        return result;
    }

    @CacheEvict(cacheNames = {
            CacheNames.MEDPLUM_STATUS,
            CacheNames.PATIENT_INTEGRATION_OVERVIEW,
            CacheNames.PATIENT_INTEGRATION_STATUS
    }, allEntries = true)
    public Map<String, Object> syncPatient(Long patientId, boolean includeObservations) {
        Patient patient = patientRepository.findById(patientId)
                .orElseThrow(() -> new com.medical.agent.common.exception.BusinessException(404, "患者不存在"));
        if (isDemoPatient(patient)) {
            throw new com.medical.agent.common.exception.BusinessException(400, "预置患者请走接入校验流程。");
        }

        Map<String, Object> syncResult = medplumService.syncPatient(patientId, includeObservations);
        Map<String, Object> status = getPatientStatus(patientId);

        Map<String, Object> result = new LinkedHashMap<>(syncResult);
        result.put("patient", status);
        result.put("message", "正式患者已同步到 Medplum，并刷新了 Agent 接入状态。");
        return result;
    }

    @Transactional
    @CacheEvict(cacheNames = {
            CacheNames.MEDPLUM_STATUS,
            CacheNames.PATIENT_INTEGRATION_OVERVIEW,
            CacheNames.PATIENT_INTEGRATION_STATUS
    }, allEntries = true)
    public Map<String, Object> importMockToMedplum(HealthRecordDTO dto, boolean includeObservations) {
        if (dto == null || dto.getPatientId() == null) {
            throw new com.medical.agent.common.exception.BusinessException(400, "patientId 不能为空");
        }
        Patient patient = patientRepository.findById(dto.getPatientId())
                .orElseThrow(() -> new com.medical.agent.common.exception.BusinessException(404, "患者不存在"));
        if (isDemoPatient(patient)) {
            throw new com.medical.agent.common.exception.BusinessException(400, "预置患者请走接入校验流程。");
        }

        Map<String, Object> syncResult = medplumService.importCloudOnlyObservations(dto, includeObservations);
        Map<String, Object> status = getPatientStatus(dto.getPatientId());

        Map<String, Object> result = new LinkedHashMap<>(syncResult);
        result.put("patientId", dto.getPatientId());
        result.put("patient", status);
        result.put("message", "正式患者随机健康数据仅已写入 Medplum，本地数据库未落库。");
        return result;
    }

    @Transactional
    @CacheEvict(cacheNames = {
            CacheNames.DOCTOR_DASHBOARD_OVERVIEW,
            CacheNames.PATIENT_INTEGRATION_OVERVIEW,
            CacheNames.PATIENT_INTEGRATION_STATUS
    }, allEntries = true)
    public Map<String, Object> bindDoctor(Long patientId, Long doctorId) {
        Patient patient = patientRepository.findById(patientId)
                .orElseThrow(() -> new com.medical.agent.common.exception.BusinessException(404, "患者不存在"));
        if (isDemoPatient(patient)) {
            throw new com.medical.agent.common.exception.BusinessException(400, "预置患者请走接入校验流程。");
        }

        Doctor doctor = null;
        if (doctorId != null) {
            doctor = doctorRepository.findById(doctorId)
                    .orElseThrow(() -> new com.medical.agent.common.exception.BusinessException(404, "责任医生不存在"));
        }

        patient.setAssignedDoctorId(doctorId);
        patientRepository.save(patient);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("patientId", patient.getId());
        result.put("assignedDoctorId", doctorId);
        result.put("assignedDoctorName", doctor != null ? doctor.getName() : "");
        result.put("message", doctor != null ? "责任医生绑定已更新。" : "责任医生绑定已清除。");
        result.put("patient", getPatientStatus(patientId));
        return result;
    }

    private List<Patient> findBusinessPatients() {
        return patientRepository.findAll().stream()
                .filter(patient -> !isDemoPatient(patient))
                .toList();
    }

    private boolean matchesKeyword(Patient patient, String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return true;
        }
        return containsIgnoreCase(patient.getName(), keyword)
                || containsIgnoreCase(patient.getUsername(), keyword)
                || containsIgnoreCase(patient.getPhone(), keyword)
                || containsIgnoreCase(patient.getFhirId(), keyword);
    }

    private boolean matchesReadiness(Map<String, Object> row,
                                     String readiness) {
        if (readiness == null || readiness.isBlank() || "all".equals(readiness)) {
            return true;
        }
        return switch (readiness) {
            case "unassigned" -> !Boolean.TRUE.equals(row.get("doctorBindingReady"));
            case "profile_incomplete" -> !Boolean.TRUE.equals(row.get("profileCompleted"));
            case "unmapped" -> !Boolean.TRUE.equals(row.get("medplumContextReady"));
            case "agent_ready" -> Boolean.TRUE.equals(row.get("agentContextReady"));
            default -> true;
        };
    }

    private boolean isDemoPatient(Patient patient) {
        return patient != null
                && patient.getUsername() != null
                && patient.getUsername().startsWith(DEMO_PATIENT_PREFIX);
    }

    private Map<String, Object> buildPatientRow(Patient patient, Map<String, Object> medplumStatus) {
        PatientMedicalHistory medicalHistory = patientMedicalHistoryRepository.findByPatientId(patient.getId()).orElse(null);
        List<HealthRecord> records = healthRecordRepository.findByPatientIdOrderByRecordDateDesc(patient.getId());
        HealthRecord latestRecord = records.isEmpty() ? null : records.get(0);
        Doctor assignedDoctor = patient.getAssignedDoctorId() == null
                ? null
                : doctorRepository.findById(patient.getAssignedDoctorId()).orElse(null);
        ChatPatientContextData patientContext = chatContextService != null
                ? chatContextService.loadPatientContext(patient.getId())
                : null;

        boolean hasMedicalHistory = medicalHistory != null && hasMedicalHistoryContent(medicalHistory);
        boolean hasLocalRecord = !records.isEmpty();
        boolean doctorBindingReady = patient.getAssignedDoctorId() != null;
        boolean patientContextReady = patientContext != null && patientContext.patient() != null;

        Map<String, Object> medplumPatientResult = readMedplumPatient(patient.getId(), medplumStatus);
        Map<String, Object> medplumObservationResult = readMedplumObservations(patient.getId(), medplumPatientResult, medplumStatus);
        boolean medplumContextReady = Boolean.TRUE.equals(medplumPatientResult.get("found"));

        List<String> contextSources = new ArrayList<>();
        if (hasMedicalHistory || hasLocalRecord || Boolean.TRUE.equals(patient.getProfileCompleted())) {
            contextSources.add("local");
        }
        if (medplumContextReady) {
            contextSources.add("medplum");
        }

        boolean agentContextReady = doctorBindingReady && patientContextReady;

        Map<String, Object> row = new LinkedHashMap<>();
        row.put("patientId", patient.getId());
        row.put("name", patient.getName());
        row.put("username", patient.getUsername());
        row.put("phone", patient.getPhone());
        row.put("fhirId", patient.getFhirId());
        row.put("profileCompleted", Boolean.TRUE.equals(patient.getProfileCompleted()));
        row.put("assignedDoctorId", patient.getAssignedDoctorId());
        row.put("assignedDoctorName", assignedDoctor != null ? assignedDoctor.getName() : "");
        row.put("doctorBindingReady", doctorBindingReady);
        row.put("hasMedicalHistory", hasMedicalHistory);
        row.put("hasLocalHealthRecord", hasLocalRecord);
        row.put("localHealthRecordCount", records.size());
        row.put("latestRecordDate", latestRecord != null ? latestRecord.getRecordDate() : null);
        row.put("patientContextReady", patientContextReady);
        row.put("medplumContextReady", medplumContextReady);
        row.put("medplumAvailable", Boolean.TRUE.equals(medplumPatientResult.get("available")));
        row.put("medplumPatientId", stringValue(medplumPatientResult.get("medplumPatientId")));
        row.put("medplumObservationCount", medplumObservationResult.getOrDefault("count", 0));
        row.put("contextSources", contextSources);
        row.put("agentContextReady", agentContextReady);
        row.put("agentContextMessage", buildAgentContextMessage(doctorBindingReady, patientContextReady, medplumContextReady));
        row.put("degradeReason", buildDegradeReason(doctorBindingReady, patientContextReady, medplumPatientResult, medplumContextReady));
        row.put("patientContextSummary", patientContext != null ? patientContext.promptContext() : "");
        row.put("medplumPatientSummary", stringValue(medplumPatientResult.get("message")));
        row.put("medplumObservationSummary", stringValue(medplumObservationResult.get("message")));
        return row;
    }

    private Map<String, Object> readMedplumPatient(Long patientId,
                                                   Map<String, Object> medplumStatus) {
        if (!Boolean.TRUE.equals(medplumStatus.get("enabled")) || !Boolean.TRUE.equals(medplumStatus.get("configured"))) {
            return Map.of(
                    "available", false,
                    "found", false,
                    "message", "Medplum 未启用或未配置完整。"
            );
        }
        return medplumService.getPatientByLocalId(patientId);
    }

    private Map<String, Object> readMedplumObservations(Long patientId,
                                                        Map<String, Object> medplumPatientResult,
                                                        Map<String, Object> medplumStatus) {
        if (!Boolean.TRUE.equals(medplumStatus.get("enabled")) || !Boolean.TRUE.equals(medplumStatus.get("configured"))) {
            return Map.of(
                    "available", false,
                    "found", false,
                    "count", 0,
                    "message", "Medplum 未启用或未配置完整。"
            );
        }
        if (!Boolean.TRUE.equals(medplumPatientResult.get("found"))) {
            return Map.of(
                    "available", true,
                    "found", false,
                    "count", 0,
                    "message", "Medplum 中未找到对应患者，无法读取 Observation。"
            );
        }
        return medplumService.getLatestObservationsForLocalPatient(patientId, 3);
    }

    private boolean hasMedicalHistoryContent(PatientMedicalHistory history) {
        return hasText(history.getPastDiseases())
                || hasText(history.getSurgicalHistory())
                || hasText(history.getDrugAllergies())
                || hasText(history.getFoodAllergies())
                || hasText(history.getImmunizationHistory())
                || hasText(history.getFamilyHistory())
                || hasText(history.getOtherNotes());
    }

    private String buildAgentContextMessage(boolean doctorBindingReady,
                                            boolean patientContextReady,
                                            boolean medplumContextReady) {
        if (!doctorBindingReady) {
            return "该患者尚未绑定责任医生，暂不适合作为医生端 AI 上下文患者。";
        }
        if (!patientContextReady) {
            return "该患者本地上下文尚不完整，AI 只能有限降级运行。";
        }
        if (medplumContextReady) {
            return "该患者已可用于医生端 AI 上下文增强对话，本地与 Medplum 云端视图均可用。";
        }
        return "该患者已可用于医生端 AI 对话，但当前仅使用本地上下文。";
    }

    private String buildDegradeReason(boolean doctorBindingReady,
                                      boolean patientContextReady,
                                      Map<String, Object> medplumPatientResult,
                                      boolean medplumContextReady) {
        if (!doctorBindingReady) {
            return "未绑定责任医生。";
        }
        if (!patientContextReady) {
            return "本地患者上下文尚未准备完成。";
        }
        if (Boolean.TRUE.equals(medplumPatientResult.get("available")) && !medplumContextReady) {
            return "Medplum 中未找到对应患者，当前仅使用本地上下文。";
        }
        if (!Boolean.TRUE.equals(medplumPatientResult.get("available"))) {
            return stringValue(medplumPatientResult.get("message"));
        }
        return "";
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private boolean containsIgnoreCase(String value, String keyword) {
        return value != null && value.toLowerCase().contains(keyword);
    }
}
