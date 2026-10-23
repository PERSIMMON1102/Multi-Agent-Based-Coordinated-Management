package com.medical.agent.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.medical.agent.model.entity.ClinicalAttachment;
import com.medical.agent.model.entity.ClinicalEncounter;
import com.medical.agent.model.entity.EncounterDiagnosis;
import com.medical.agent.model.entity.HealthRecord;
import com.medical.agent.model.entity.PaperCaseRecord;
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
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FhirServiceTest {

    @Test
    void shouldMapAllObservationFieldsAndUseBooleanTypeForFlags() throws Exception {
        Long patientId = 7L;
        Long recordId = 77L;

        Patient patient = Patient.builder()
                .id(patientId)
                .name("测试患者")
                .fhirId("FHIR DEMO 001")
                .build();

        HealthRecord record = HealthRecord.builder()
                .id(recordId)
                .patientId(patientId)
                .encounterId(901L)
                .recordDate(LocalDate.of(2026, 4, 10))
                .recordedAt(LocalDateTime.of(2026, 4, 10, 14, 35, 0))
                .systolicBp(140)
                .diastolicBp(90)
                .restingHeartRate(80)
                .fastingGlucose(7.1)
                .postprandialGlucose(10.4)
                .hba1c(7.0)
                .bmi(25.8)
                .weight(72.0)
                .height(167.0)
                .totalCholesterol(5.5)
                .triglycerides(2.0)
                .ldlCholesterol(3.1)
                .hdlCholesterol(1.2)
                .fev1Percent(64.0)
                .catScore(15)
                .mmrcScore(2)
                .acuteExacerbations(1)
                .smokingYears(15)
                .egfr(68.0)
                .uacr(52.0)
                .serumCreatinine(110.0)
                .hemoglobin(130.0)
                .wbc(6.8)
                .platelet(215)
                .neutrophilPct(63.0)
                .alt(31.0)
                .ast(26.0)
                .uricAcid(386.0)
                .ck(146.0)
                .potassium(4.3)
                .urineProtein("TRACE")
                .urineGlucose("NEGATIVE")
                .urineBlood("1+")
                .chestPainFrequency(1)
                .exerciseTolerance(3)
                .ejectionFraction(48.0)
                .bnp(160.0)
                .onAntiplatelet(true)
                .hasAtrialFibrillation(false)
                .hasDiabetesHistory(true)
                .isSmoking(false)
                .adlScore(88)
                .mmseScore(27)
                .carotidStenosis(36.0)
                .medicationMissRate(8)
                .onRegularMedication(true)
                .saltIntake(3)
                .exerciseFrequency(3)
                .alcoholIntake(1)
                .build();

        PatientRepository patientRepository = RepositoryProxyFactory.create(PatientRepository.class, Map.of(
                "findById", args -> Optional.of(patient)
        ));
        HealthRecordRepository healthRecordRepository = RepositoryProxyFactory.create(HealthRecordRepository.class, Map.of(
                "findByPatientIdOrderByRecordDateDesc", args -> List.of(record)
        ));
        PatientMedicalHistoryRepository historyRepository = RepositoryProxyFactory.create(PatientMedicalHistoryRepository.class, Map.of(
                "findByPatientId", args -> Optional.empty()
        ));
        PaperCaseRecordRepository paperCaseRecordRepository = RepositoryProxyFactory.create(PaperCaseRecordRepository.class, Map.of(
                "findByPatientIdOrderByCreatedAtDesc", args -> List.of()
        ));
        ClinicalEncounterRepository clinicalEncounterRepository = RepositoryProxyFactory.create(ClinicalEncounterRepository.class, Map.of(
                "findByPatientIdOrderByEncounterDateDescCreatedAtDesc", args -> List.of()
        ));
        EncounterDiagnosisRepository encounterDiagnosisRepository = RepositoryProxyFactory.create(EncounterDiagnosisRepository.class, Map.of());
        ClinicalAttachmentRepository clinicalAttachmentRepository = RepositoryProxyFactory.create(ClinicalAttachmentRepository.class, Map.of());

        FhirService fhirService = new FhirService(
                patientRepository,
                healthRecordRepository,
                historyRepository,
                paperCaseRecordRepository,
                clinicalEncounterRepository,
                encounterDiagnosisRepository,
                clinicalAttachmentRepository
        );

        String json = fhirService.toFhirObservationsJson(patientId);
        JsonNode root = new ObjectMapper().readTree(json);

        List<String> expectedCodes = List.of(
                "85354-9", "1558-6", "53094-9", "4548-4", "33914-3", "39156-5", "8867-4", "29463-7", "8302-2",
                "2093-3", "2571-8", "13457-7", "2085-9", "9318-7", "2160-0", "718-7",
                "6690-2", "777-3", "770-8", "1742-6", "1920-8", "3084-1", "2157-6", "2823-3",
                "20454-5", "2349-9", "5794-3",
                "fev1-percent", "cat-score", "mmrc-score", "acute-exacerbations", "smoking-years",
                "chest-pain-frequency", "exercise-tolerance", "ejection-fraction", "bnp",
                "on-antiplatelet", "has-atrial-fibrillation", "has-diabetes-history", "is-smoking",
                "adl-score", "mmse-score", "carotid-stenosis", "medication-miss-rate", "on-regular-medication",
                "salt-intake", "exercise-frequency", "alcohol-intake"
        );

        Map<String, JsonNode> observationByCode = new HashMap<>();
        JsonNode entries = root.path("entry");
        for (JsonNode entry : entries) {
            JsonNode resource = entry.path("resource");
            String code = resource.path("code").path("coding").path(0).path("code").asText("");
            if (!code.isBlank()) {
                observationByCode.put(code, resource);
            }
        }

        for (String code : expectedCodes) {
            assertTrue(observationByCode.containsKey(code), "missing mapped observation code: " + code);
        }

        assertEquals("obs-77-fasting-glucose", observationByCode.get("1558-6").path("id").asText());
        assertEquals("2026-04-10T14:35:00+08:00", observationByCode.get("1558-6").path("effectiveDateTime").asText());

        assertTrue(observationByCode.get("on-antiplatelet").has("valueBoolean"));
        assertTrue(observationByCode.get("has-atrial-fibrillation").has("valueBoolean"));
        assertTrue(observationByCode.get("has-diabetes-history").has("valueBoolean"));
        assertTrue(observationByCode.get("is-smoking").has("valueBoolean"));
        assertTrue(observationByCode.get("on-regular-medication").has("valueBoolean"));

        assertEquals(true, observationByCode.get("on-antiplatelet").path("valueBoolean").asBoolean());
        assertEquals(false, observationByCode.get("has-atrial-fibrillation").path("valueBoolean").asBoolean());
        assertEquals("Encounter/paper-case-encounter-901",
                observationByCode.get("1558-6").path("encounter").path("reference").asText());
    }

    @Test
    void shouldSanitizePatientFhirIdWhenMappingPatient() {
        Patient patient = Patient.builder()
                .id(1L)
                .name("王一")
                .fhirId("FHIR Demo/Patient A")
                .build();

        PatientRepository patientRepository = RepositoryProxyFactory.create(PatientRepository.class, Map.of(
                "findById", args -> Optional.of(patient)
        ));
        HealthRecordRepository healthRecordRepository = RepositoryProxyFactory.create(HealthRecordRepository.class, Map.of());
        PatientMedicalHistoryRepository historyRepository = RepositoryProxyFactory.create(PatientMedicalHistoryRepository.class, Map.of(
                "findByPatientId", args -> Optional.empty()
        ));
        PaperCaseRecordRepository paperCaseRecordRepository = RepositoryProxyFactory.create(PaperCaseRecordRepository.class, Map.of(
                "findByPatientIdOrderByCreatedAtDesc", args -> List.of()
        ));
        ClinicalEncounterRepository clinicalEncounterRepository = RepositoryProxyFactory.create(ClinicalEncounterRepository.class, Map.of(
                "findByPatientIdOrderByEncounterDateDescCreatedAtDesc", args -> List.of()
        ));
        EncounterDiagnosisRepository encounterDiagnosisRepository = RepositoryProxyFactory.create(EncounterDiagnosisRepository.class, Map.of());
        ClinicalAttachmentRepository clinicalAttachmentRepository = RepositoryProxyFactory.create(ClinicalAttachmentRepository.class, Map.of());

        FhirService fhirService = new FhirService(
                patientRepository,
                healthRecordRepository,
                historyRepository,
                paperCaseRecordRepository,
                clinicalEncounterRepository,
                encounterDiagnosisRepository,
                clinicalAttachmentRepository
        );
        String patientJson = fhirService.toFhirPatientJson(1L);

        assertNotNull(patientJson);
        assertTrue(patientJson.contains("\"id\": \"fhir-demo-patient-a\""));
    }

    @Test
    void shouldMapPaperCaseToEncounterConditionAndDocumentReference() throws Exception {
        Long patientId = 18L;
        Patient patient = Patient.builder()
                .id(patientId)
                .name("李某")
                .fhirId("paper-case-patient")
                .build();
        ClinicalEncounter encounter = ClinicalEncounter.builder()
                .id(901L)
                .patientId(patientId)
                .encounterDate(LocalDate.of(2026, 5, 28))
                .chiefComplaint("上腹痛伴反酸2周")
                .presentIllness("近2周反复上腹痛")
                .physicalExam("上腹部轻压痛")
                .build();
        EncounterDiagnosis diagnosis = EncounterDiagnosis.builder()
                .id(901L)
                .encounterId(901L)
                .diagnosisText("胃食管反流病")
                .icd11Code("DA22")
                .icd11Title("Gastro-oesophageal reflux disease")
                .codingStatus("manually_confirmed")
                .build();
        ClinicalAttachment attachment = ClinicalAttachment.builder()
                .id(901L)
                .encounterId(901L)
                .contentType("application/pdf")
                .fileName("demo.pdf")
                .fileUrl("/uploads/paper-cases/18/demo.pdf")
                .documentNarrative("主诉：上腹痛伴反酸2周\n初步诊断：胃食管反流病")
                .build();

        PatientRepository patientRepository = RepositoryProxyFactory.create(PatientRepository.class, Map.of(
                "findById", args -> Optional.of(patient)
        ));
        HealthRecordRepository healthRecordRepository = RepositoryProxyFactory.create(HealthRecordRepository.class, Map.of(
                "findByPatientIdOrderByRecordDateDesc", args -> List.of()
        ));
        PatientMedicalHistoryRepository historyRepository = RepositoryProxyFactory.create(PatientMedicalHistoryRepository.class, Map.of(
                "findByPatientId", args -> Optional.empty()
        ));
        PaperCaseRecordRepository paperCaseRecordRepository = RepositoryProxyFactory.create(PaperCaseRecordRepository.class, Map.of(
                "findByPatientIdOrderByCreatedAtDesc", args -> List.of()
        ));
        ClinicalEncounterRepository clinicalEncounterRepository = RepositoryProxyFactory.create(ClinicalEncounterRepository.class, Map.of(
                "findByPatientIdOrderByEncounterDateDescCreatedAtDesc", args -> List.of(encounter)
        ));
        EncounterDiagnosisRepository encounterDiagnosisRepository = RepositoryProxyFactory.create(EncounterDiagnosisRepository.class, Map.of(
                "findByEncounterIdOrderByIdAsc", args -> List.of(diagnosis)
        ));
        ClinicalAttachmentRepository clinicalAttachmentRepository = RepositoryProxyFactory.create(ClinicalAttachmentRepository.class, Map.of(
                "findByEncounterIdOrderByIdAsc", args -> List.of(attachment)
        ));

        FhirService fhirService = new FhirService(
                patientRepository,
                healthRecordRepository,
                historyRepository,
                paperCaseRecordRepository,
                clinicalEncounterRepository,
                encounterDiagnosisRepository,
                clinicalAttachmentRepository
        );
        String json = fhirService.toFhirPaperCasesJson(patientId);
        JsonNode root = new ObjectMapper().readTree(json);

        Map<String, JsonNode> resources = new HashMap<>();
        for (JsonNode entry : root.path("entry")) {
            JsonNode resource = entry.path("resource");
            resources.put(resource.path("resourceType").asText() + ":" + resource.path("id").asText(), resource);
        }

        JsonNode encounterResource = resources.get("Encounter:paper-case-encounter-901");
        JsonNode condition = resources.get("Condition:paper-case-condition-901");
        JsonNode documentReference = resources.get("DocumentReference:paper-case-document-901");

        assertNotNull(encounterResource);
        assertNotNull(condition);
        assertNotNull(documentReference);
        assertEquals("Patient/paper-case-patient", encounterResource.path("subject").path("reference").asText());
        assertEquals("DA22", condition.path("code").path("coding").path(0).path("code").asText());
        assertEquals("confirmed", condition.path("verificationStatus").path("coding").path(0).path("code").asText());
        assertEquals("/uploads/paper-cases/18/demo.pdf", documentReference.path("content").path(0).path("attachment").path("url").asText());
    }
}
