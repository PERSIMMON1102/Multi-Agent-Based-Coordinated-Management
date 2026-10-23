package com.medical.agent.service;

import com.medical.agent.model.entity.Patient;
import com.medical.agent.model.repository.ClinicalAttachmentRepository;
import com.medical.agent.model.repository.ClinicalEncounterRepository;
import com.medical.agent.model.repository.EncounterDiagnosisRepository;
import com.medical.agent.model.repository.HealthRecordRepository;
import com.medical.agent.model.repository.PaperCaseRecordRepository;
import com.medical.agent.model.repository.PatientMedicalHistoryRepository;
import com.medical.agent.model.repository.PatientRepository;
import com.medical.agent.service.testsupport.RepositoryProxyFactory;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertTrue;

class FhirServicePatientIdentifierTest {

    @Test
    void shouldExposeStablePatientIdentifiersInFhirPatient() {
        Patient patient = Patient.builder()
                .id(1L)
                .name("陈建国")
                .username("patientli")
                .fhirId("FHIR-a13a144b")
                .gender("男")
                .birthDate(LocalDate.of(1970, 6, 18))
                .phone("13800001234")
                .build();

        PatientRepository patientRepository = RepositoryProxyFactory.create(PatientRepository.class, Map.of(
                "findById", args -> Optional.of(patient)
        ));

        FhirService fhirService = new FhirService(
                patientRepository,
                RepositoryProxyFactory.create(HealthRecordRepository.class, Map.of()),
                RepositoryProxyFactory.create(PatientMedicalHistoryRepository.class, Map.of(
                        "findByPatientId", args -> Optional.empty()
                )),
                RepositoryProxyFactory.create(PaperCaseRecordRepository.class, Map.of()),
                RepositoryProxyFactory.create(ClinicalEncounterRepository.class, Map.of(
                        "findByPatientIdOrderByEncounterDateDescCreatedAtDesc", args -> List.of()
                )),
                RepositoryProxyFactory.create(EncounterDiagnosisRepository.class, Map.of(
                        "findByEncounterIdOrderByIdAsc", args -> List.of()
                )),
                RepositoryProxyFactory.create(ClinicalAttachmentRepository.class, Map.of(
                        "findByEncounterIdOrderByIdAsc", args -> List.of()
                ))
        );

        String json = fhirService.toFhirPatientJson(1L);
        assertTrue(json.contains("\"system\" : \"https://cdm.local/fhir/identifier/patient-fhir-id\""));
        assertTrue(json.contains("\"value\" : \"fhir-a13a144b\""));
        assertTrue(json.contains("\"system\" : \"https://cdm.local/fhir/identifier/patient-username\""));
        assertTrue(json.contains("\"value\" : \"patientli\""));
    }
}
