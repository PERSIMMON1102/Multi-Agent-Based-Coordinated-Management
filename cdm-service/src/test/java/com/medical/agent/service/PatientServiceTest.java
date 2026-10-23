package com.medical.agent.service;

import com.medical.agent.common.dto.PatientDTO;
import com.medical.agent.model.entity.ClinicalDataJob;
import com.medical.agent.model.entity.Patient;
import com.medical.agent.model.entity.PatientMedicalHistory;
import com.medical.agent.model.entity.PaperCaseRecord;
import com.medical.agent.model.repository.ClinicalDataJobRepository;
import com.medical.agent.model.repository.PaperCaseRecordRepository;
import com.medical.agent.model.repository.PatientMedicalHistoryRepository;
import com.medical.agent.model.repository.PatientRepository;
import com.medical.agent.service.cache.AiChatCacheInvalidationService;
import com.medical.agent.service.cache.AiChatCacheVersionService;
import com.medical.agent.service.clinicaljob.ClinicalDataJobService;
import com.medical.agent.service.testsupport.RepositoryProxyFactory;
import org.junit.jupiter.api.Test;
import org.springframework.cache.support.NoOpCacheManager;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class PatientServiceTest {

    @Test
    void shouldIncludeMedicalHistoryWhenQueryingPatientDetail() {
        PatientRepository patientRepository = RepositoryProxyFactory.create(PatientRepository.class, Map.of(
                "findById", args -> Optional.of(patient())
        ));
        PatientMedicalHistoryRepository historyRepository = RepositoryProxyFactory.create(PatientMedicalHistoryRepository.class, Map.of(
                "findByPatientId", args -> Optional.of(history())
        ));

        PatientService service = new PatientService(
                patientRepository,
                historyRepository,
                null,
                null,
                new AiChatCacheInvalidationService(new NoOpCacheManager(), new AiChatCacheVersionService())
        );

        PatientDTO dto = service.getById(1L);

        assertEquals("患者甲", dto.getName());
        assertNotNull(dto.getMedicalHistory());
        assertEquals("高血压、糖尿病", dto.getMedicalHistory().getPastDiseases());
        assertEquals("青霉素", dto.getMedicalHistory().getDrugAllergies());
    }

    @Test
    void shouldPersistMedicalHistoryWhenUpdatingPatient() {
        AtomicReference<PatientMedicalHistory> savedHistory = new AtomicReference<>();
        PatientRepository patientRepository = RepositoryProxyFactory.create(PatientRepository.class, Map.of(
                "findById", args -> Optional.of(patient()),
                "save", args -> args[0]
        ));
        PatientMedicalHistoryRepository historyRepository = RepositoryProxyFactory.create(PatientMedicalHistoryRepository.class, Map.of(
                "findByPatientId", args -> Optional.ofNullable(savedHistory.get()),
                "save", args -> {
                    PatientMedicalHistory history = (PatientMedicalHistory) args[0];
                    savedHistory.set(history);
                    return history;
                }
        ));

        PatientService service = new PatientService(
                patientRepository,
                historyRepository,
                null,
                null,
                new AiChatCacheInvalidationService(new NoOpCacheManager(), new AiChatCacheVersionService())
        );

        PatientDTO updated = service.update(1L, PatientDTO.builder()
                .name("患者甲")
                .gender("男")
                .birthDate(LocalDate.of(1980, 1, 1))
                .phone("13900000000")
                .address("上海")
                .medicalHistory(com.medical.agent.common.dto.PatientMedicalHistoryDTO.builder()
                        .pastDiseases("高血压")
                        .foodAllergies("海鲜")
                        .build())
                .build());

        assertNotNull(savedHistory.get());
        assertEquals("高血压", savedHistory.get().getPastDiseases());
        assertEquals("海鲜", savedHistory.get().getFoodAllergies());
        assertNotNull(updated.getMedicalHistory());
    }

    @Test
    void listShouldNotEagerlyAttachMedicalHistory() {
        PatientRepository patientRepository = RepositoryProxyFactory.create(PatientRepository.class, Map.of(
                "findAll", args -> List.of(patient())
        ));
        PatientMedicalHistoryRepository historyRepository = RepositoryProxyFactory.create(PatientMedicalHistoryRepository.class, Map.of());

        PatientService service = new PatientService(
                patientRepository,
                historyRepository,
                null,
                null,
                new AiChatCacheInvalidationService(new NoOpCacheManager(), new AiChatCacheVersionService())
        );

        List<PatientDTO> patients = service.listAll();

        assertEquals(1, patients.size());
        assertNull(patients.get(0).getMedicalHistory());
    }

    @Test
    void listShouldExposePaperCaseOperationalSignals() {
        Patient patient = patient();
        PatientRepository patientRepository = RepositoryProxyFactory.create(PatientRepository.class, Map.of(
                "findByAssignedDoctorId", args -> List.of(patient)
        ));
        PatientMedicalHistoryRepository historyRepository = RepositoryProxyFactory.create(PatientMedicalHistoryRepository.class, Map.of());
        PaperCaseRecordRepository paperCaseRecordRepository = RepositoryProxyFactory.create(PaperCaseRecordRepository.class, Map.of(
                "findByPatientIdInOrderByCreatedAtDesc", args -> List.of(
                        PaperCaseRecord.builder()
                                .id(11L)
                                .patientId(1L)
                                .icdCodingStatus("pending_review")
                                .medplumSyncStatus("review_pending")
                                .createdAt(LocalDateTime.now())
                                .build(),
                        PaperCaseRecord.builder()
                                .id(12L)
                                .patientId(1L)
                                .icdCodingStatus("auto_coded")
                                .medplumSyncStatus("failed")
                                .createdAt(LocalDateTime.now().minusMinutes(1))
                                .build(),
                        PaperCaseRecord.builder()
                                .id(13L)
                                .patientId(1L)
                                .icdCodingStatus("manually_confirmed")
                                .medplumSyncStatus("synced")
                                .createdAt(LocalDateTime.now().minusMinutes(2))
                                .build()
                )
        ));
        ClinicalDataJobRepository clinicalDataJobRepository = RepositoryProxyFactory.create(ClinicalDataJobRepository.class, Map.of(
                "findBySourceKindAndSourceRefIdInOrderByCreatedAtDesc", args -> List.of(
                        ClinicalDataJob.builder()
                                .id(201L)
                                .sourceKind(ClinicalDataJobService.SOURCE_KIND_PAPER_CASE_RECORD)
                                .sourceRefId(11L)
                                .jobStatus(ClinicalDataJobService.STATUS_REVIEW_PENDING)
                                .createdAt(LocalDateTime.now())
                                .build(),
                        ClinicalDataJob.builder()
                                .id(202L)
                                .sourceKind(ClinicalDataJobService.SOURCE_KIND_PAPER_CASE_RECORD)
                                .sourceRefId(12L)
                                .jobStatus(ClinicalDataJobService.STATUS_FAILED)
                                .createdAt(LocalDateTime.now().minusMinutes(1))
                                .build(),
                        ClinicalDataJob.builder()
                                .id(203L)
                                .sourceKind(ClinicalDataJobService.SOURCE_KIND_PAPER_CASE_RECORD)
                                .sourceRefId(13L)
                                .jobStatus(ClinicalDataJobService.STATUS_SYNCED)
                                .createdAt(LocalDateTime.now().minusMinutes(2))
                                .build()
                )
        ));

        PatientService service = new PatientService(
                patientRepository,
                historyRepository,
                paperCaseRecordRepository,
                clinicalDataJobRepository,
                new AiChatCacheInvalidationService(new NoOpCacheManager(), new AiChatCacheVersionService())
        );

        List<PatientDTO> patients = service.listByDoctorId(99L);

        assertEquals(1, patients.size());
        PatientDTO dto = patients.get(0);
        assertEquals(3L, dto.getPaperCaseTotalCount());
        assertEquals(1L, dto.getPaperCasePendingReviewCount());
        assertEquals(1L, dto.getPaperCaseFailedSyncCount());
        assertEquals(2L, dto.getPaperCaseAttentionCount());
    }

    private Patient patient() {
        return Patient.builder()
                .id(1L)
                .name("患者甲")
                .gender("男")
                .birthDate(LocalDate.of(1980, 1, 1))
                .phone("13800000000")
                .address("上海")
                .build();
    }

    private PatientMedicalHistory history() {
        return PatientMedicalHistory.builder()
                .patientId(1L)
                .pastDiseases("高血压、糖尿病")
                .drugAllergies("青霉素")
                .build();
    }
}
