package com.medical.agent.service;

import com.medical.agent.common.dto.FollowUpCompletionDTO;
import com.medical.agent.common.dto.FollowUpPlanDTO;
import com.medical.agent.common.dto.HealthRecordDTO;
import com.medical.agent.common.enums.FollowUpStatus;
import com.medical.agent.model.entity.FollowUpPlan;
import com.medical.agent.model.entity.HealthRecord;
import com.medical.agent.model.entity.Patient;
import com.medical.agent.model.repository.FollowUpPlanRepository;
import com.medical.agent.model.repository.HealthRecordRepository;
import com.medical.agent.model.repository.PatientRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class FollowUpService {

    private static final Logger log = LoggerFactory.getLogger(FollowUpService.class);

    private final FollowUpPlanRepository followUpPlanRepository;
    private final PatientRepository patientRepository;
    private final HealthRecordRepository healthRecordRepository;
    private final ClinicalAssessmentOrchestrator clinicalAssessmentOrchestrator;
    private final HealthRecordService healthRecordService;

    public FollowUpService(FollowUpPlanRepository followUpPlanRepository,
                           PatientRepository patientRepository,
                           HealthRecordRepository healthRecordRepository,
                           ClinicalAssessmentOrchestrator clinicalAssessmentOrchestrator,
                           HealthRecordService healthRecordService) {
        this.followUpPlanRepository = followUpPlanRepository;
        this.patientRepository = patientRepository;
        this.healthRecordRepository = healthRecordRepository;
        this.clinicalAssessmentOrchestrator = clinicalAssessmentOrchestrator;
        this.healthRecordService = healthRecordService;
    }

    public List<FollowUpPlanDTO> getByPatientId(Long patientId) {
        return followUpPlanRepository.findByPatientIdOrderByCreatedAtDesc(patientId)
                .stream()
                .peek(this::normalizeScheduleStatus)
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    public List<FollowUpPlanDTO> getOverduePlans() {
        return followUpPlanRepository.findByStatusIn(List.of(FollowUpStatus.PENDING, FollowUpStatus.OVERDUE))
                .stream()
                .peek(this::normalizeScheduleStatus)
                .filter(plan -> plan.getStatus() == FollowUpStatus.OVERDUE)
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    public List<FollowUpPlanDTO> getPendingPlans() {
        return followUpPlanRepository.findByStatusIn(List.of(FollowUpStatus.PENDING, FollowUpStatus.OVERDUE))
                .stream()
                .peek(this::normalizeScheduleStatus)
                .filter(plan -> plan.getStatus() == FollowUpStatus.PENDING)
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    public List<FollowUpPlanDTO> getCompletedPlans() {
        return followUpPlanRepository.findByStatus(FollowUpStatus.COMPLETED)
                .stream().map(this::toDTO).collect(Collectors.toList());
    }

    public List<FollowUpPlanDTO> getInProgressPlans() {
        return followUpPlanRepository.findByStatus(FollowUpStatus.IN_PROGRESS)
                .stream().map(this::toDTO).collect(Collectors.toList());
    }

    public List<FollowUpPlanDTO> getCancelledPlans() {
        return followUpPlanRepository.findByStatus(FollowUpStatus.CANCELLED)
                .stream().map(this::toDTO).collect(Collectors.toList());
    }

    public FollowUpPlanDTO createFollowUp(FollowUpPlanDTO dto) {
        Long patientId = dto.getPatientId();
        if (patientId == null || !patientRepository.existsById(patientId)) {
            throw new RuntimeException("患者不存在: " + patientId);
        }
        if (dto.getDiseaseType() == null) {
            throw new RuntimeException("疾病类型不能为空");
        }
        if (dto.getFrequency() == null) {
            throw new RuntimeException("随访频次不能为空");
        }

        FollowUpPlan plan = FollowUpPlan.builder()
                .patientId(patientId)
                .diseaseType(dto.getDiseaseType())
                .frequency(dto.getFrequency())
                .status(dto.getStatus() != null ? dto.getStatus() : FollowUpStatus.PENDING)
                .nextFollowDate(resolveNextFollowDate(dto.getNextFollowDate(), dto.getFrequency()))
                .planContent(dto.getPlanContent())
                .checkItems(joinCheckItems(dto.getCheckItems()))
                .agentId(dto.getAgentId() != null && !dto.getAgentId().isBlank() ? dto.getAgentId() : "DoctorManual")
                .build();
        normalizeScheduleStatus(plan);

        return toDTO(followUpPlanRepository.save(plan));
    }

    public FollowUpPlanDTO updateFollowUp(Long id, FollowUpPlanDTO dto) {
        FollowUpPlan plan = followUpPlanRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("随访计划不存在: " + id));
        boolean rescheduled = false;

        if (dto.getDiseaseType() != null) {
            plan.setDiseaseType(dto.getDiseaseType());
        }
        if (dto.getFrequency() != null) {
            plan.setFrequency(dto.getFrequency());
            rescheduled = true;
        }
        if (dto.getNextFollowDate() != null || dto.getFrequency() != null) {
            plan.setNextFollowDate(resolveNextFollowDate(dto.getNextFollowDate(), plan.getFrequency()));
            rescheduled = true;
        }
        if (dto.getPlanContent() != null) {
            plan.setPlanContent(dto.getPlanContent());
        }
        if (dto.getCheckItems() != null) {
            plan.setCheckItems(joinCheckItems(dto.getCheckItems()));
        }
        if (dto.getAgentId() != null) {
            plan.setAgentId(dto.getAgentId());
        }

        if (rescheduled) {
            normalizeScheduleStatus(plan);
        }

        return toDTO(followUpPlanRepository.save(plan));
    }

    public FollowUpPlanDTO startFollowUp(Long id) {
        return updateStatus(id, FollowUpStatus.IN_PROGRESS);
    }

    public FollowUpPlanDTO cancelFollowUp(Long id) {
        return updateStatus(id, FollowUpStatus.CANCELLED);
    }

    public FollowUpPlanDTO completeFollowUp(Long id, FollowUpCompletionDTO completion) {
        FollowUpPlan plan = followUpPlanRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("随访计划不存在: " + id));
        plan.setStatus(FollowUpStatus.COMPLETED);
        followUpPlanRepository.save(plan);

        boolean triggerReassessment = completion != null && Boolean.TRUE.equals(completion.getTriggerReassessment());
        HealthRecordDTO healthRecord = completion != null ? completion.getHealthRecord() : null;

        try {
            if (hasClinicalData(healthRecord)) {
                healthRecord.setPatientId(plan.getPatientId());
                if (healthRecord.getRecordDate() == null) {
                    healthRecord.setRecordDate(LocalDate.now());
                }
                healthRecordService.create(
                        healthRecord,
                        plan.getDiseaseType(),
                        triggerReassessment,
                        silentAssessmentOptions()
                );
                log.info("Follow-up {} completed with fresh health data for patient {}",
                        id, plan.getPatientId());
            } else if (triggerReassessment) {
                triggerSilentReassessmentFromLatestRecord(id, plan);
            }
        } catch (Exception e) {
            log.warn("Failed to process follow-up completion workflow: {}", e.getMessage());
        }

        return toDTO(plan);
    }

    private void triggerSilentReassessmentFromLatestRecord(Long followUpId, FollowUpPlan plan) {
        List<HealthRecord> records = healthRecordRepository.findByPatientIdOrderByRecordDateDesc(plan.getPatientId());
        if (records.isEmpty()) {
            return;
        }
        HealthRecord latest = records.get(0);
        Map<String, Object> healthData = buildHealthDataMap(latest);
        clinicalAssessmentOrchestrator.triggerAssessment(
                plan.getPatientId(),
                plan.getDiseaseType(),
                healthData,
                silentAssessmentOptions()
        );
        log.info("Re-assessment triggered after follow-up {} completed for patient {}",
                followUpId, plan.getPatientId());
    }

    private Map<String, Object> silentAssessmentOptions() {
        return Map.of(
                "_suppressFollowUpCreation", true,
                "_silentPatientNotification", true
        );
    }

    private FollowUpPlanDTO updateStatus(Long id, FollowUpStatus status) {
        FollowUpPlan plan = followUpPlanRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("随访计划不存在: " + id));
        plan.setStatus(status);
        return toDTO(followUpPlanRepository.save(plan));
    }

    private LocalDate resolveNextFollowDate(LocalDate nextFollowDate, com.medical.agent.common.enums.FollowUpFrequency frequency) {
        if (nextFollowDate != null) {
            return nextFollowDate;
        }
        if (frequency == null) {
            return null;
        }
        return LocalDate.now().plusDays(Math.max(frequency.getIntervalDays(), 0));
    }

    private String joinCheckItems(List<String> checkItems) {
        if (checkItems == null) {
            return null;
        }
        return checkItems.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .collect(Collectors.joining("; "));
    }

    private void normalizeScheduleStatus(FollowUpPlan plan) {
        if (plan == null) {
            return;
        }
        if (plan.getStatus() == FollowUpStatus.COMPLETED
                || plan.getStatus() == FollowUpStatus.CANCELLED
                || plan.getStatus() == FollowUpStatus.IN_PROGRESS) {
            return;
        }
        if (plan.getNextFollowDate() != null && plan.getNextFollowDate().isBefore(LocalDate.now())) {
            plan.setStatus(FollowUpStatus.OVERDUE);
        } else {
            plan.setStatus(FollowUpStatus.PENDING);
        }
    }

    private boolean hasClinicalData(HealthRecordDTO dto) {
        if (dto == null) {
            return false;
        }
        return dto.getSystolicBp() != null
                || dto.getDiastolicBp() != null
                || dto.getRestingHeartRate() != null
                || dto.getFastingGlucose() != null
                || dto.getPostprandialGlucose() != null
                || dto.getHba1c() != null
                || dto.getBmi() != null
                || dto.getWeight() != null
                || dto.getHeight() != null
                || dto.getTotalCholesterol() != null
                || dto.getTriglycerides() != null
                || dto.getLdlCholesterol() != null
                || dto.getHdlCholesterol() != null
                || dto.getFev1Percent() != null
                || dto.getCatScore() != null
                || dto.getMmrcScore() != null
                || dto.getAcuteExacerbations() != null
                || dto.getSmokingYears() != null
                || dto.getEgfr() != null
                || dto.getUacr() != null
                || dto.getSerumCreatinine() != null
                || dto.getHemoglobin() != null
                || dto.getChestPainFrequency() != null
                || dto.getExerciseTolerance() != null
                || dto.getEjectionFraction() != null
                || dto.getBnp() != null
                || dto.getOnAntiplatelet() != null
                || dto.getHasAtrialFibrillation() != null
                || dto.getHasDiabetesHistory() != null
                || dto.getIsSmoking() != null
                || dto.getAdlScore() != null
                || dto.getMmseScore() != null
                || dto.getCarotidStenosis() != null
                || dto.getMedicationMissRate() != null
                || dto.getOnRegularMedication() != null
                || dto.getSaltIntake() != null
                || dto.getExerciseFrequency() != null
                || dto.getAlcoholIntake() != null;
    }

    private Map<String, Object> buildHealthDataMap(HealthRecord r) {
        Map<String, Object> data = new HashMap<>();
        if (r.getSystolicBp() != null) data.put("systolicBp", r.getSystolicBp());
        if (r.getDiastolicBp() != null) data.put("diastolicBp", r.getDiastolicBp());
        if (r.getRestingHeartRate() != null) data.put("restingHeartRate", r.getRestingHeartRate());
        if (r.getFastingGlucose() != null) data.put("fastingGlucose", r.getFastingGlucose());
        if (r.getPostprandialGlucose() != null) data.put("postprandialGlucose", r.getPostprandialGlucose());
        if (r.getHba1c() != null) data.put("hba1c", r.getHba1c());
        if (r.getBmi() != null) data.put("bmi", r.getBmi());
        if (r.getWeight() != null) data.put("weight", r.getWeight());
        if (r.getTriglycerides() != null) data.put("triglyceride", r.getTriglycerides());
        if (r.getEgfr() != null) data.put("egfr", r.getEgfr());
        if (r.getUacr() != null) data.put("uacr", r.getUacr());
        if (r.getSerumCreatinine() != null) data.put("creatinine", r.getSerumCreatinine());
        if (r.getFev1Percent() != null) data.put("fev1Percent", r.getFev1Percent());
        if (r.getCatScore() != null) data.put("catScore", r.getCatScore());
        if (r.getEjectionFraction() != null) data.put("ejectionFraction", r.getEjectionFraction());
        if (r.getLdlCholesterol() != null) data.put("ldlCholesterol", r.getLdlCholesterol());
        return data;
    }

    private FollowUpPlanDTO toDTO(FollowUpPlan plan) {
        String patientName = patientRepository.findById(plan.getPatientId())
                .map(Patient::getName).orElse("未知");
        return FollowUpPlanDTO.builder()
                .id(plan.getId())
                .patientId(plan.getPatientId())
                .patientName(patientName)
                .diseaseType(plan.getDiseaseType())
                .frequency(plan.getFrequency())
                .status(plan.getStatus())
                .nextFollowDate(plan.getNextFollowDate())
                .planContent(plan.getPlanContent())
                .checkItems(plan.getCheckItems() != null ?
                        Arrays.stream(plan.getCheckItems().split("; "))
                                .map(String::trim)
                                .filter(s -> !s.isBlank())
                                .collect(Collectors.toList()) : List.of())
                .agentId(plan.getAgentId())
                .build();
    }
}
