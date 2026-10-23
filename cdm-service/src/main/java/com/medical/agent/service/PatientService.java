package com.medical.agent.service;

import com.medical.agent.common.dto.PatientDTO;
import com.medical.agent.common.dto.PatientMedicalHistoryDTO;
import com.medical.agent.model.entity.ClinicalDataJob;
import com.medical.agent.model.entity.Patient;
import com.medical.agent.model.entity.PatientMedicalHistory;
import com.medical.agent.model.entity.PaperCaseRecord;
import com.medical.agent.model.repository.ClinicalDataJobRepository;
import com.medical.agent.model.repository.PaperCaseRecordRepository;
import com.medical.agent.model.repository.PatientMedicalHistoryRepository;
import com.medical.agent.model.repository.PatientRepository;
import com.medical.agent.service.cache.AiChatCacheInvalidationService;
import com.medical.agent.service.clinicaljob.ClinicalDataJobService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class PatientService {

    private final PatientRepository patientRepository;
    private final PatientMedicalHistoryRepository patientMedicalHistoryRepository;
    private final PaperCaseRecordRepository paperCaseRecordRepository;
    private final ClinicalDataJobRepository clinicalDataJobRepository;
    private final AiChatCacheInvalidationService aiChatCacheInvalidationService;

    @Autowired
    public PatientService(PatientRepository patientRepository,
                          PatientMedicalHistoryRepository patientMedicalHistoryRepository,
                          PaperCaseRecordRepository paperCaseRecordRepository,
                          ClinicalDataJobRepository clinicalDataJobRepository,
                          AiChatCacheInvalidationService aiChatCacheInvalidationService) {
        this.patientRepository = patientRepository;
        this.patientMedicalHistoryRepository = patientMedicalHistoryRepository;
        this.paperCaseRecordRepository = paperCaseRecordRepository;
        this.clinicalDataJobRepository = clinicalDataJobRepository;
        this.aiChatCacheInvalidationService = aiChatCacheInvalidationService;
    }

    public PatientService(PatientRepository patientRepository,
                          PatientMedicalHistoryRepository patientMedicalHistoryRepository,
                          AiChatCacheInvalidationService aiChatCacheInvalidationService) {
        this(patientRepository, patientMedicalHistoryRepository, null, null, aiChatCacheInvalidationService);
    }

    public List<PatientDTO> listAll() {
        return toPatientDTOs(patientRepository.findAll(), false);
    }

    public List<PatientDTO> listByDoctorId(Long doctorId) {
        return toPatientDTOs(patientRepository.findByAssignedDoctorId(doctorId), false);
    }

    public List<PatientDTO> listByDoctorIdAndName(Long doctorId, String name) {
        return toPatientDTOs(patientRepository.findByNameContainingAndAssignedDoctorId(name, doctorId), false);
    }

    public PatientDTO getById(Long id) {
        return patientRepository.findById(id).map(patient -> toDTO(patient, true))
                .orElseThrow(() -> new RuntimeException("患者不存在: " + id));
    }

    public List<PatientDTO> searchByName(String name) {
        return toPatientDTOs(patientRepository.findByNameContaining(name), false);
    }

    @Transactional
    public PatientDTO create(PatientDTO dto) {
        Patient patient = Patient.builder()
                .name(dto.getName())
                .gender(dto.getGender())
                .birthDate(dto.getBirthDate())
                .phone(dto.getPhone())
                .idCard(dto.getIdCard())
                .address(dto.getAddress())
                .fhirId("FHIR-" + UUID.randomUUID().toString().substring(0, 8))
                .emergencyContact(dto.getEmergencyContact())
                .emergencyPhone(dto.getEmergencyPhone())
                .assignedDoctorId(dto.getAssignedDoctorId())
                .profileCompleted(dto.getProfileCompleted())
                .build();
        patient = patientRepository.save(patient);
        upsertMedicalHistory(patient.getId(), dto.getMedicalHistory());
        aiChatCacheInvalidationService.invalidatePatient(patient.getId());
        return toDTO(patient, true);
    }

    @Transactional
    public PatientDTO update(Long id, PatientDTO dto) {
        Patient patient = patientRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("患者不存在: " + id));
        patient.setName(dto.getName());
        patient.setGender(dto.getGender());
        patient.setBirthDate(dto.getBirthDate());
        patient.setPhone(dto.getPhone());
        patient.setAddress(dto.getAddress());
        patient.setEmergencyContact(dto.getEmergencyContact());
        patient.setEmergencyPhone(dto.getEmergencyPhone());
        patient.setAssignedDoctorId(dto.getAssignedDoctorId());
        patient = patientRepository.save(patient);
        upsertMedicalHistory(patient.getId(), dto.getMedicalHistory());
        aiChatCacheInvalidationService.invalidatePatient(patient.getId());
        return toDTO(patient, true);
    }

    @Transactional
    public void delete(Long id) {
        patientRepository.deleteById(id);
        aiChatCacheInvalidationService.invalidatePatient(id);
    }

    private PatientDTO toDTO(Patient p, boolean includeMedicalHistory) {
        return toDTO(p, includeMedicalHistory, null);
    }

    private PatientDTO toDTO(Patient p, boolean includeMedicalHistory, PaperCasePatientOperationsSummary paperCaseSummary) {
        return PatientDTO.builder()
                .id(p.getId())
                .name(p.getName())
                .gender(p.getGender())
                .birthDate(p.getBirthDate())
                .phone(p.getPhone())
                .idCard(p.getIdCard())
                .address(p.getAddress())
                .fhirId(p.getFhirId())
                .emergencyContact(p.getEmergencyContact())
                .emergencyPhone(p.getEmergencyPhone())
                .assignedDoctorId(p.getAssignedDoctorId())
                .profileCompleted(p.getProfileCompleted())
                .paperCaseTotalCount(paperCaseSummary == null ? 0L : paperCaseSummary.totalCount())
                .paperCasePendingReviewCount(paperCaseSummary == null ? 0L : paperCaseSummary.pendingReviewCount())
                .paperCaseFailedSyncCount(paperCaseSummary == null ? 0L : paperCaseSummary.failedSyncCount())
                .paperCaseAttentionCount(paperCaseSummary == null ? 0L : paperCaseSummary.attentionCount())
                .medicalHistory(includeMedicalHistory ? toMedicalHistoryDTO(
                        patientMedicalHistoryRepository.findByPatientId(p.getId()).orElse(null)
                ) : null)
                .build();
    }

    private List<PatientDTO> toPatientDTOs(List<Patient> patients, boolean includeMedicalHistory) {
        Map<Long, PaperCasePatientOperationsSummary> summaries = buildPaperCaseOperationsSummaries(patients);
        return patients.stream()
                .map(patient -> toDTO(patient, includeMedicalHistory, summaries.get(patient.getId())))
                .collect(Collectors.toList());
    }

    private Map<Long, PaperCasePatientOperationsSummary> buildPaperCaseOperationsSummaries(List<Patient> patients) {
        if (patients == null || patients.isEmpty()
                || paperCaseRecordRepository == null
                || clinicalDataJobRepository == null) {
            return Collections.emptyMap();
        }
        List<Long> patientIds = patients.stream()
                .map(Patient::getId)
                .filter(id -> id != null)
                .toList();
        if (patientIds.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<Long, PaperCasePatientOperationsSummaryAccumulator> accumulators = new HashMap<>();
        List<PaperCaseRecord> records = paperCaseRecordRepository.findByPatientIdInOrderByCreatedAtDesc(patientIds);
        records.forEach(record -> accumulatePaperCase(accumulators, record));

        List<Long> recordIds = accumulators.values().stream()
                .flatMap(item -> item.recordIds().stream())
                .toList();
        if (recordIds.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<Long, ClinicalDataJob> latestJobsByRecordId = clinicalDataJobRepository
                .findBySourceKindAndSourceRefIdInOrderByCreatedAtDesc(
                        ClinicalDataJobService.SOURCE_KIND_PAPER_CASE_RECORD,
                        recordIds
                ).stream()
                .filter(job -> job.getSourceRefId() != null)
                .collect(Collectors.toMap(
                        ClinicalDataJob::getSourceRefId,
                        job -> job,
                        (existing, ignored) -> existing
                ));

        accumulators.values().forEach(accumulator -> accumulator.applyLatestJobs(latestJobsByRecordId));

        Map<Long, PaperCasePatientOperationsSummary> result = new HashMap<>();
        accumulators.forEach((patientId, accumulator) -> result.put(patientId, accumulator.toSummary()));
        return result;
    }

    private void accumulatePaperCase(Map<Long, PaperCasePatientOperationsSummaryAccumulator> accumulators,
                                     PaperCaseRecord record) {
        if (record == null || record.getPatientId() == null || record.getId() == null) {
            return;
        }
        PaperCasePatientOperationsSummaryAccumulator accumulator = accumulators.computeIfAbsent(
                record.getPatientId(),
                ignored -> new PaperCasePatientOperationsSummaryAccumulator()
        );
        accumulator.totalCount++;
        accumulator.recordIds().add(record.getId());
        PaperCaseRecordFlags flags = accumulator.recordFlags.computeIfAbsent(record.getId(), ignored -> new PaperCaseRecordFlags());
        if (equalsIgnoreCase(record.getIcdCodingStatus(), "pending_review")) {
            accumulator.pendingReviewCount++;
            flags.attention = true;
        }
        if (equalsIgnoreCase(record.getMedplumSyncStatus(), "failed")) {
            accumulator.failedSyncCount++;
            flags.attention = true;
        }
    }

    private boolean equalsIgnoreCase(String left, String right) {
        return left != null && left.equalsIgnoreCase(right);
    }

    private void upsertMedicalHistory(Long patientId, PatientMedicalHistoryDTO dto) {
        if (dto == null) {
            return;
        }
        PatientMedicalHistory history = patientMedicalHistoryRepository.findByPatientId(patientId)
                .orElseGet(() -> PatientMedicalHistory.builder().patientId(patientId).build());
        history.setPastDiseases(normalize(dto.getPastDiseases()));
        history.setSurgicalHistory(normalize(dto.getSurgicalHistory()));
        history.setDrugAllergies(normalize(dto.getDrugAllergies()));
        history.setFoodAllergies(normalize(dto.getFoodAllergies()));
        history.setImmunizationHistory(normalize(dto.getImmunizationHistory()));
        history.setFamilyHistory(normalize(dto.getFamilyHistory()));
        history.setOtherNotes(normalize(dto.getOtherNotes()));
        patientMedicalHistoryRepository.save(history);
    }

    private PatientMedicalHistoryDTO toMedicalHistoryDTO(PatientMedicalHistory history) {
        if (history == null) {
            return null;
        }
        return PatientMedicalHistoryDTO.builder()
                .pastDiseases(history.getPastDiseases())
                .surgicalHistory(history.getSurgicalHistory())
                .drugAllergies(history.getDrugAllergies())
                .foodAllergies(history.getFoodAllergies())
                .immunizationHistory(history.getImmunizationHistory())
                .familyHistory(history.getFamilyHistory())
                .otherNotes(history.getOtherNotes())
                .updatedAt(history.getUpdatedAt() == null ? null : history.getUpdatedAt().toString())
                .build();
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private record PaperCasePatientOperationsSummary(
            long totalCount,
            long pendingReviewCount,
            long failedSyncCount,
            long attentionCount
    ) {
    }

    private static final class PaperCasePatientOperationsSummaryAccumulator {
        private final Set<Long> paperCaseRecordIds = new java.util.LinkedHashSet<>();
        private final Map<Long, PaperCaseRecordFlags> recordFlags = new HashMap<>();
        private long totalCount;
        private long pendingReviewCount;
        private long failedSyncCount;

        Set<Long> recordIds() {
            return paperCaseRecordIds;
        }

        void applyLatestJobs(Map<Long, ClinicalDataJob> latestJobsByRecordId) {
            for (Long recordId : paperCaseRecordIds) {
                ClinicalDataJob job = latestJobsByRecordId.get(recordId);
                if (job == null) {
                    continue;
                }
                PaperCaseRecordFlags flags = recordFlags.computeIfAbsent(recordId, ignored -> new PaperCaseRecordFlags());
                if (ClinicalDataJobService.STATUS_REVIEW_PENDING.equalsIgnoreCase(job.getJobStatus())
                        || ClinicalDataJobService.STATUS_FAILED.equalsIgnoreCase(job.getJobStatus())
                        || ClinicalDataJobService.STATUS_DEAD_LETTER.equalsIgnoreCase(job.getJobStatus())) {
                    flags.attention = true;
                }
            }
        }

        PaperCasePatientOperationsSummary toSummary() {
            long attentionCount = recordFlags.values().stream().filter(flags -> flags.attention).count();
            return new PaperCasePatientOperationsSummary(
                    totalCount,
                    pendingReviewCount,
                    failedSyncCount,
                    attentionCount
            );
        }
    }

    private static final class PaperCaseRecordFlags {
        private boolean attention;
    }
}
