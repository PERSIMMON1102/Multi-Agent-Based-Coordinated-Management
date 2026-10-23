package com.medical.agent.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.medical.agent.model.entity.ClinicalAttachment;
import com.medical.agent.model.entity.ClinicalEncounter;
import com.medical.agent.model.entity.EncounterDiagnosis;
import com.medical.agent.model.entity.HealthRecord;
import com.medical.agent.model.entity.MedplumSyncCheckpoint;
import com.medical.agent.model.entity.Patient;
import com.medical.agent.model.repository.ClinicalAttachmentRepository;
import com.medical.agent.model.repository.ClinicalEncounterRepository;
import com.medical.agent.model.repository.EncounterDiagnosisRepository;
import com.medical.agent.model.repository.HealthRecordRepository;
import com.medical.agent.model.repository.MedplumSyncCheckpointRepository;
import com.medical.agent.model.repository.PaperCaseRecordRepository;
import com.medical.agent.model.repository.PatientMedicalHistoryRepository;
import com.medical.agent.model.repository.PatientRepository;
import com.medical.agent.service.testsupport.RepositoryProxyFactory;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MedplumServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private MockWebServer server;

    @BeforeEach
    void setUp() throws Exception {
        server = new MockWebServer();
        server.start();
    }

    @AfterEach
    void tearDown() throws Exception {
        server.shutdown();
    }

    @Test
    void shouldSyncObservationsAndPaperCaseViaTransactionBundleAndSkipUnchangedFollowUp() throws Exception {
        Patient patient = Patient.builder()
                .id(11L)
                .name("李四")
                .gender("男")
                .birthDate(LocalDate.of(1981, 5, 10))
                .phone("13900001111")
                .fhirId("FHIR-11")
                .build();
        HealthRecord record = HealthRecord.builder()
                .id(101L)
                .patientId(11L)
                .recordDate(LocalDate.of(2026, 5, 29))
                .recordedAt(LocalDateTime.of(2026, 5, 29, 9, 15))
                .systolicBp(128)
                .diastolicBp(82)
                .fastingGlucose(6.8)
                .createdAt(LocalDateTime.of(2026, 5, 29, 9, 0))
                .build();
        ClinicalEncounter encounter = ClinicalEncounter.builder()
                .id(301L)
                .patientId(11L)
                .encounterType("outpatient")
                .encounterDate(LocalDate.of(2026, 5, 20))
                .chiefComplaint("上腹痛")
                .presentIllness("反酸 2 周")
                .createdAt(LocalDateTime.of(2026, 5, 20, 10, 0))
                .build();
        EncounterDiagnosis diagnosis = EncounterDiagnosis.builder()
                .id(401L)
                .encounterId(301L)
                .diagnosisText("胃食管反流病")
                .icd11Code("DA22")
                .icd11Title("Gastro-oesophageal reflux disease")
                .codingStatus("manually_confirmed")
                .build();
        ClinicalAttachment attachment = ClinicalAttachment.builder()
                .id(501L)
                .encounterId(301L)
                .fileName("paper-case.pdf")
                .fileUrl("https://example.test/paper-case.pdf")
                .contentType("application/pdf")
                .documentNarrative("主诉：上腹痛")
                .createdAt(LocalDateTime.of(2026, 5, 20, 10, 5))
                .build();

        Map<String, MedplumSyncCheckpoint> checkpoints = new LinkedHashMap<>();
        MedplumSyncCheckpointRepository checkpointRepository = RepositoryProxyFactory.create(MedplumSyncCheckpointRepository.class, Map.of(
                "findByPatientIdAndSyncScope", args -> Optional.ofNullable(checkpoints.get(args[0] + ":" + args[1])),
                "save", args -> {
                    MedplumSyncCheckpoint checkpoint = (MedplumSyncCheckpoint) args[0];
                    if (checkpoint.getId() == null) {
                        checkpoint.setId((long) checkpoints.size() + 1);
                    }
                    checkpoints.put(checkpoint.getPatientId() + ":" + checkpoint.getSyncScope(), checkpoint);
                    return checkpoint;
                }
        ));

        PatientRepository patientRepository = RepositoryProxyFactory.create(PatientRepository.class, Map.of(
                "findById", args -> Optional.of(patient)
        ));
        HealthRecordRepository healthRecordRepository = RepositoryProxyFactory.create(HealthRecordRepository.class, Map.of(
                "findByPatientIdOrderByRecordDateDesc", args -> List.of(record)
        ));
        PatientMedicalHistoryRepository patientMedicalHistoryRepository = RepositoryProxyFactory.create(PatientMedicalHistoryRepository.class, Map.of(
                "findByPatientId", args -> Optional.empty()
        ));
        PaperCaseRecordRepository paperCaseRecordRepository = RepositoryProxyFactory.create(PaperCaseRecordRepository.class, Map.of());
        ClinicalEncounterRepository encounterRepository = RepositoryProxyFactory.create(ClinicalEncounterRepository.class, Map.of(
                "findByPatientIdOrderByEncounterDateDescCreatedAtDesc", args -> List.of(encounter)
        ));
        EncounterDiagnosisRepository diagnosisRepository = RepositoryProxyFactory.create(EncounterDiagnosisRepository.class, Map.of(
                "findByEncounterIdOrderByIdAsc", args -> List.of(diagnosis)
        ));
        ClinicalAttachmentRepository attachmentRepository = RepositoryProxyFactory.create(ClinicalAttachmentRepository.class, Map.of(
                "findByEncounterIdOrderByIdAsc", args -> List.of(attachment)
        ));

        FhirService fhirService = new FhirService(
                patientRepository,
                healthRecordRepository,
                patientMedicalHistoryRepository,
                paperCaseRecordRepository,
                encounterRepository,
                diagnosisRepository,
                attachmentRepository
        );
        MedplumService medplumService = new MedplumService(
                fhirService,
                patientRepository,
                healthRecordRepository,
                checkpointRepository
        );
        ReflectionTestUtils.setField(medplumService, "enabled", true);
        ReflectionTestUtils.setField(medplumService, "baseUrl", server.url("/").toString());
        ReflectionTestUtils.setField(medplumService, "accessToken", "test-token");
        ReflectionTestUtils.setField(medplumService, "transactionBundleEnabled", true);
        ReflectionTestUtils.setField(medplumService, "incrementalSyncEnabled", true);
        ReflectionTestUtils.setField(medplumService, "timeoutSeconds", 5L);
        medplumService.init();

        server.enqueue(jsonResponse(200, "{\"resourceType\":\"Bundle\",\"type\":\"searchset\",\"entry\":[]}"));
        server.enqueue(jsonResponse(200, "{\"resourceType\":\"Patient\",\"id\":\"medplum-patient-11\"}"));
        server.enqueue(jsonResponse(200, "{\"resourceType\":\"Bundle\",\"type\":\"transaction-response\",\"entry\":[{\"response\":{\"status\":\"200\"}},{\"response\":{\"status\":\"200\"}}]}"));
        server.enqueue(jsonResponse(200, "{\"resourceType\":\"Bundle\",\"type\":\"transaction-response\",\"entry\":[{\"response\":{\"status\":\"200\"}},{\"response\":{\"status\":\"200\"}},{\"response\":{\"status\":\"200\"}}]}"));

        Map<String, Object> first = medplumService.syncPatient(11L, true);
        assertEquals(2, first.get("observationsSynced"));
        assertEquals(0, first.get("observationsSkipped"));
        assertEquals(3, first.get("paperCaseResourcesSynced"));
        assertEquals(0, first.get("paperCaseResourcesSkipped"));

        RecordedRequest patientRequest = server.takeRequest();
        assertEquals("GET", patientRequest.getMethod());
        assertTrue(patientRequest.getPath().contains("/fhir/R4/Patient?identifier="));

        RecordedRequest patientCreateRequest = server.takeRequest();
        assertEquals("POST", patientCreateRequest.getMethod());
        assertEquals("/fhir/R4/Patient", patientCreateRequest.getPath());

        RecordedRequest observationBundleRequest = server.takeRequest();
        assertEquals("POST", observationBundleRequest.getMethod());
        JsonNode observationBundle = objectMapper.readTree(observationBundleRequest.getBody().readUtf8());
        assertEquals("Bundle", observationBundle.path("resourceType").asText());
        assertEquals("transaction", observationBundle.path("type").asText());
        assertEquals(2, observationBundle.path("entry").size());
        assertTrue(observationBundle.path("entry").get(0).path("request").path("url").asText().startsWith("Observation?identifier="));
        assertEquals("2026-05-29T09:15:00+08:00", observationBundle.path("entry").get(0).path("resource").path("effectiveDateTime").asText());

        RecordedRequest paperCaseBundleRequest = server.takeRequest();
        assertEquals("POST", paperCaseBundleRequest.getMethod());
        JsonNode paperCaseBundle = objectMapper.readTree(paperCaseBundleRequest.getBody().readUtf8());
        assertEquals("transaction", paperCaseBundle.path("type").asText());
        assertEquals(3, paperCaseBundle.path("entry").size());
        assertTrue(paperCaseBundle.path("entry").get(0).path("request").path("url").asText().contains("?identifier="));

        assertNotNull(checkpoints.get("11:patient"));
        assertNotNull(checkpoints.get("11:observation"));
        assertNotNull(checkpoints.get("11:paper_case"));
        assertEquals("medplum-patient-11", checkpoints.get("11:patient").getResourceId());

        Map<String, Object> second = medplumService.syncPatient(11L, true);
        assertEquals(0, second.get("observationsSynced"));
        assertEquals(2, second.get("observationsSkipped"));
        assertEquals(0, second.get("paperCaseResourcesSynced"));
        assertEquals(3, second.get("paperCaseResourcesSkipped"));
        assertEquals(4, server.getRequestCount());
    }

    @Test
    void shouldFallbackToPerResourceUpsertWhenTransactionBundleFails() throws Exception {
        Patient patient = Patient.builder()
                .id(12L)
                .name("王五")
                .gender("男")
                .birthDate(LocalDate.of(1979, 8, 8))
                .phone("13900001212")
                .fhirId("FHIR-12")
                .build();
        HealthRecord record = HealthRecord.builder()
                .id(102L)
                .patientId(12L)
                .recordDate(LocalDate.of(2026, 5, 29))
                .systolicBp(136)
                .diastolicBp(88)
                .fastingGlucose(7.1)
                .createdAt(LocalDateTime.of(2026, 5, 29, 8, 0))
                .build();

        Map<String, MedplumSyncCheckpoint> checkpoints = new LinkedHashMap<>();
        MedplumSyncCheckpointRepository checkpointRepository = RepositoryProxyFactory.create(MedplumSyncCheckpointRepository.class, Map.of(
                "findByPatientIdAndSyncScope", args -> Optional.ofNullable(checkpoints.get(args[0] + ":" + args[1])),
                "save", args -> {
                    MedplumSyncCheckpoint checkpoint = (MedplumSyncCheckpoint) args[0];
                    if (checkpoint.getId() == null) {
                        checkpoint.setId((long) checkpoints.size() + 1);
                    }
                    checkpoints.put(checkpoint.getPatientId() + ":" + checkpoint.getSyncScope(), checkpoint);
                    return checkpoint;
                }
        ));
        PatientRepository patientRepository = RepositoryProxyFactory.create(PatientRepository.class, Map.of(
                "findById", args -> Optional.of(patient)
        ));
        HealthRecordRepository healthRecordRepository = RepositoryProxyFactory.create(HealthRecordRepository.class, Map.of(
                "findByPatientIdOrderByRecordDateDesc", args -> List.of(record)
        ));
        PatientMedicalHistoryRepository patientMedicalHistoryRepository = RepositoryProxyFactory.create(PatientMedicalHistoryRepository.class, Map.of(
                "findByPatientId", args -> Optional.empty()
        ));
        FhirService fhirService = new FhirService(
                patientRepository,
                healthRecordRepository,
                patientMedicalHistoryRepository,
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
        MedplumService medplumService = new MedplumService(
                fhirService,
                patientRepository,
                healthRecordRepository,
                checkpointRepository
        );
        ReflectionTestUtils.setField(medplumService, "enabled", true);
        ReflectionTestUtils.setField(medplumService, "baseUrl", server.url("/").toString());
        ReflectionTestUtils.setField(medplumService, "accessToken", "test-token");
        ReflectionTestUtils.setField(medplumService, "transactionBundleEnabled", true);
        ReflectionTestUtils.setField(medplumService, "transactionBundleFallbackEnabled", true);
        ReflectionTestUtils.setField(medplumService, "incrementalSyncEnabled", false);
        medplumService.init();

        server.enqueue(jsonResponse(200, "{\"resourceType\":\"Bundle\",\"type\":\"searchset\",\"entry\":[]}"));
        server.enqueue(jsonResponse(200, "{\"resourceType\":\"Patient\",\"id\":\"medplum-patient-12\"}"));
        server.enqueue(jsonResponse(500, "{\"issue\":[{\"diagnostics\":\"bundle failed\"}]}"));
        server.enqueue(jsonResponse(200, "{\"resourceType\":\"Bundle\",\"type\":\"searchset\",\"entry\":[]}"));
        server.enqueue(jsonResponse(200, "{\"resourceType\":\"Observation\",\"id\":\"obs-server-1\"}"));
        server.enqueue(jsonResponse(200, "{\"resourceType\":\"Bundle\",\"type\":\"searchset\",\"entry\":[]}"));
        server.enqueue(jsonResponse(200, "{\"resourceType\":\"Observation\",\"id\":\"obs-server-2\"}"));

        Map<String, Object> result = medplumService.syncPatient(12L, true);
        assertEquals(2, result.get("observationsSynced"));
        assertEquals(0, result.get("paperCaseResourcesSynced"));

        server.takeRequest(); // patient search
        server.takeRequest(); // patient create
        RecordedRequest failedBundleRequest = server.takeRequest();
        assertEquals("POST", failedBundleRequest.getMethod());
        JsonNode failedBundle = objectMapper.readTree(failedBundleRequest.getBody().readUtf8());
        assertEquals("transaction", failedBundle.path("type").asText());

        RecordedRequest firstObservationSearch = server.takeRequest();
        assertEquals("GET", firstObservationSearch.getMethod());
        assertTrue(firstObservationSearch.getPath().contains("/fhir/R4/Observation?identifier="));
        RecordedRequest firstObservationCreate = server.takeRequest();
        assertEquals("POST", firstObservationCreate.getMethod());
        assertEquals("/fhir/R4/Observation", firstObservationCreate.getPath());

        RecordedRequest secondObservationSearch = server.takeRequest();
        assertEquals("GET", secondObservationSearch.getMethod());
        assertTrue(secondObservationSearch.getPath().contains("/fhir/R4/Observation?identifier="));
        RecordedRequest secondObservationCreate = server.takeRequest();
        assertEquals("POST", secondObservationCreate.getMethod());
        assertEquals("/fhir/R4/Observation", secondObservationCreate.getPath());
    }

    @Test
    void shouldPreferStablePatientIdentifierOverReusedLocalPatientId() throws Exception {
        Patient patient = Patient.builder()
                .id(1L)
                .name("陈建国")
                .username("patientli")
                .gender("男")
                .birthDate(LocalDate.of(1970, 6, 18))
                .phone("13800001234")
                .fhirId("FHIR-a13a144b")
                .build();

        Map<String, MedplumSyncCheckpoint> checkpoints = new LinkedHashMap<>();
        MedplumSyncCheckpointRepository checkpointRepository = RepositoryProxyFactory.create(MedplumSyncCheckpointRepository.class, Map.of(
                "findByPatientIdAndSyncScope", args -> Optional.ofNullable(checkpoints.get(args[0] + ":" + args[1])),
                "save", args -> args[0]
        ));
        PatientRepository patientRepository = RepositoryProxyFactory.create(PatientRepository.class, Map.of(
                "findById", args -> Optional.of(patient)
        ));
        HealthRecordRepository healthRecordRepository = RepositoryProxyFactory.create(HealthRecordRepository.class, Map.of());
        PatientMedicalHistoryRepository patientMedicalHistoryRepository = RepositoryProxyFactory.create(PatientMedicalHistoryRepository.class, Map.of(
                "findByPatientId", args -> Optional.empty()
        ));

        FhirService fhirService = new FhirService(
                patientRepository,
                healthRecordRepository,
                patientMedicalHistoryRepository,
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
        MedplumService medplumService = new MedplumService(
                fhirService,
                patientRepository,
                healthRecordRepository,
                checkpointRepository
        );
        ReflectionTestUtils.setField(medplumService, "enabled", true);
        ReflectionTestUtils.setField(medplumService, "baseUrl", server.url("/").toString());
        ReflectionTestUtils.setField(medplumService, "accessToken", "test-token");
        medplumService.init();

        server.enqueue(jsonResponse(200, "{\"resourceType\":\"Bundle\",\"type\":\"searchset\",\"entry\":[]}"));
        server.enqueue(jsonResponse(404, "{\"resourceType\":\"OperationOutcome\"}"));
        server.enqueue(jsonResponse(200, "{\"resourceType\":\"Bundle\",\"type\":\"searchset\",\"entry\":[]}"));

        Map<String, Object> result = medplumService.getPatientByLocalId(1L);
        assertEquals(false, result.get("found"));

        RecordedRequest stableIdentifierSearch = server.takeRequest();
        assertTrue(stableIdentifierSearch.getPath().contains("patient-fhir-id%7Cfhir-a13a144b"));

        RecordedRequest directResourceRead = server.takeRequest();
        assertEquals("/fhir/R4/Patient/fhir-a13a144b", directResourceRead.getPath());

        RecordedRequest localIdSearch = server.takeRequest();
        assertTrue(localIdSearch.getPath().contains("patient-id%7C1"));
    }

    @Test
    void shouldRepairDuplicatePatientBindingsDuringAudit() throws Exception {
        Patient patient = Patient.builder()
                .id(1L)
                .name("李天帝")
                .username("patientli")
                .gender("男")
                .birthDate(LocalDate.of(1970, 6, 18))
                .phone("13800001234")
                .fhirId("FHIR-a13a144b")
                .build();

        PatientRepository patientRepository = RepositoryProxyFactory.create(PatientRepository.class, Map.of(
                "findAll", args -> List.of(patient),
                "findById", args -> Optional.of(patient)
        ));
        HealthRecordRepository healthRecordRepository = RepositoryProxyFactory.create(HealthRecordRepository.class, Map.of());
        PatientMedicalHistoryRepository patientMedicalHistoryRepository = RepositoryProxyFactory.create(PatientMedicalHistoryRepository.class, Map.of(
                "findByPatientId", args -> Optional.empty()
        ));
        MedplumSyncCheckpointRepository checkpointRepository = RepositoryProxyFactory.create(MedplumSyncCheckpointRepository.class, Map.of(
                "findByPatientIdAndSyncScope", args -> Optional.empty(),
                "save", args -> args[0]
        ));

        FhirService fhirService = new FhirService(
                patientRepository,
                healthRecordRepository,
                patientMedicalHistoryRepository,
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
        MedplumService medplumService = new MedplumService(
                fhirService,
                patientRepository,
                healthRecordRepository,
                checkpointRepository
        );
        ReflectionTestUtils.setField(medplumService, "enabled", true);
        ReflectionTestUtils.setField(medplumService, "baseUrl", server.url("/").toString());
        ReflectionTestUtils.setField(medplumService, "accessToken", "test-token");
        medplumService.init();

        String canonical = """
                {
                  "resourceType":"Bundle",
                  "type":"searchset",
                  "entry":[
                    {
                      "resource":{
                        "resourceType":"Patient",
                        "id":"patient-good",
                        "identifier":[
                          {"system":"https://cdm.local/fhir/identifier/patient-fhir-id","value":"fhir-a13a144b"},
                          {"system":"https://cdm.local/fhir/identifier/patient-username","value":"patientli"},
                          {"system":"https://cdm.local/fhir/identifier/patient-id","value":"1"}
                        ]
                      }
                    },
                    {
                      "resource":{
                        "resourceType":"Patient",
                        "id":"patient-dup",
                        "identifier":[
                          {"system":"https://cdm.local/fhir/identifier/patient-fhir-id","value":"fhir-a13a144b"},
                          {"system":"https://cdm.local/fhir/identifier/patient-username","value":"patientli"},
                          {"system":"https://cdm.local/fhir/identifier/patient-id","value":"1"}
                        ]
                      }
                    }
                  ]
                }
                """;
        String duplicateOnly = """
                {
                  "resourceType":"Bundle",
                  "type":"searchset",
                  "entry":[
                    {
                      "resource":{
                        "resourceType":"Patient",
                        "id":"patient-good",
                        "identifier":[
                          {"system":"https://cdm.local/fhir/identifier/patient-fhir-id","value":"fhir-a13a144b"},
                          {"system":"https://cdm.local/fhir/identifier/patient-username","value":"patientli"},
                          {"system":"https://cdm.local/fhir/identifier/patient-id","value":"1"}
                        ]
                      }
                    },
                    {
                      "resource":{
                        "resourceType":"Patient",
                        "id":"patient-dup",
                        "identifier":[
                          {"system":"https://cdm.local/fhir/identifier/patient-fhir-id","value":"fhir-a13a144b"},
                          {"system":"https://cdm.local/fhir/identifier/patient-username","value":"patientli"},
                          {"system":"https://cdm.local/fhir/identifier/patient-id","value":"1"}
                        ]
                      }
                    }
                  ]
                }
                """;
        String localIdOnly = """
                {
                  "resourceType":"Bundle",
                  "type":"searchset",
                  "entry":[
                    {
                      "resource":{
                        "resourceType":"Patient",
                        "id":"patient-good",
                        "identifier":[
                          {"system":"https://cdm.local/fhir/identifier/patient-fhir-id","value":"fhir-a13a144b"},
                          {"system":"https://cdm.local/fhir/identifier/patient-username","value":"patientli"},
                          {"system":"https://cdm.local/fhir/identifier/patient-id","value":"1"}
                        ]
                      }
                    }
                  ]
                }
                """;

        server.enqueue(jsonResponse(200, canonical));
        server.enqueue(jsonResponse(200, duplicateOnly));
        server.enqueue(jsonResponse(200, localIdOnly));
        server.enqueue(jsonResponse(200, "{\"resourceType\":\"Patient\",\"id\":\"patient-dup\",\"identifier\":[]}"));

        Map<String, Object> result = medplumService.auditAndRepairPatientMappings(true, 10);
        assertEquals(1, result.get("inspectedPatients"));
        assertEquals(1, result.get("repairedPatients"));

        RecordedRequest stableSearch = server.takeRequest();
        assertTrue(stableSearch.getPath().contains("patient-fhir-id%7Cfhir-a13a144b"));

        RecordedRequest usernameSearch = server.takeRequest();
        assertTrue(usernameSearch.getPath().contains("patient-username%7Cpatientli"));

        RecordedRequest localIdSearch = server.takeRequest();
        assertTrue(localIdSearch.getPath().contains("patient-id%7C1"));

        RecordedRequest duplicateUpdate = server.takeRequest();
        assertEquals("PUT", duplicateUpdate.getMethod());
        assertEquals("/fhir/R4/Patient/patient-dup", duplicateUpdate.getPath());
        JsonNode updatedPatient = objectMapper.readTree(duplicateUpdate.getBody().readUtf8());
        assertTrue(updatedPatient.path("identifier").isArray());
        assertEquals(0, updatedPatient.path("identifier").size());
    }

    @Test
    void shouldFallbackOnlyFailedTransactionChunkInsteadOfWholeObservationGroup() throws Exception {
        Patient patient = Patient.builder()
                .id(13L)
                .name("赵六")
                .gender("男")
                .birthDate(LocalDate.of(1978, 3, 15))
                .phone("13900001313")
                .fhirId("FHIR-13")
                .build();
        List<HealthRecord> records = List.of(
                buildRichObservationRecord(201L, 13L, LocalDate.of(2026, 5, 29), LocalDateTime.of(2026, 5, 29, 8, 0)),
                buildRichObservationRecord(202L, 13L, LocalDate.of(2026, 5, 28), LocalDateTime.of(2026, 5, 28, 8, 0))
        );

        Map<String, MedplumSyncCheckpoint> checkpoints = new LinkedHashMap<>();
        MedplumSyncCheckpointRepository checkpointRepository = RepositoryProxyFactory.create(MedplumSyncCheckpointRepository.class, Map.of(
                "findByPatientIdAndSyncScope", args -> Optional.ofNullable(checkpoints.get(args[0] + ":" + args[1])),
                "save", args -> {
                    MedplumSyncCheckpoint checkpoint = (MedplumSyncCheckpoint) args[0];
                    if (checkpoint.getId() == null) {
                        checkpoint.setId((long) checkpoints.size() + 1);
                    }
                    checkpoints.put(checkpoint.getPatientId() + ":" + checkpoint.getSyncScope(), checkpoint);
                    return checkpoint;
                }
        ));
        PatientRepository patientRepository = RepositoryProxyFactory.create(PatientRepository.class, Map.of(
                "findById", args -> Optional.of(patient)
        ));
        HealthRecordRepository healthRecordRepository = RepositoryProxyFactory.create(HealthRecordRepository.class, Map.of(
                "findByPatientIdOrderByRecordDateDesc", args -> records
        ));
        PatientMedicalHistoryRepository patientMedicalHistoryRepository = RepositoryProxyFactory.create(PatientMedicalHistoryRepository.class, Map.of(
                "findByPatientId", args -> Optional.empty()
        ));
        FhirService fhirService = new FhirService(
                patientRepository,
                healthRecordRepository,
                patientMedicalHistoryRepository,
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
        int observationCount = objectMapper.readTree(fhirService.toFhirObservationsJson(13L)).path("entry").size();
        assertTrue(observationCount > 50, "fixture should produce more than one transaction chunk");
        int firstChunkSize = 50;
        int remainingChunkSize = observationCount - firstChunkSize;

        MedplumService medplumService = new MedplumService(
                fhirService,
                patientRepository,
                healthRecordRepository,
                checkpointRepository
        );
        ReflectionTestUtils.setField(medplumService, "enabled", true);
        ReflectionTestUtils.setField(medplumService, "baseUrl", server.url("/").toString());
        ReflectionTestUtils.setField(medplumService, "accessToken", "test-token");
        ReflectionTestUtils.setField(medplumService, "transactionBundleEnabled", true);
        ReflectionTestUtils.setField(medplumService, "transactionBundleFallbackEnabled", true);
        ReflectionTestUtils.setField(medplumService, "incrementalSyncEnabled", false);
        medplumService.init();

        server.enqueue(jsonResponse(200, "{\"resourceType\":\"Bundle\",\"type\":\"searchset\",\"entry\":[]}"));
        server.enqueue(jsonResponse(200, "{\"resourceType\":\"Patient\",\"id\":\"medplum-patient-13\"}"));
        server.enqueue(transactionBundleResponse(firstChunkSize));
        server.enqueue(jsonResponse(500, "{\"issue\":[{\"diagnostics\":\"chunk failed\"}]}"));
        for (int i = 0; i < remainingChunkSize; i++) {
            server.enqueue(jsonResponse(200, "{\"resourceType\":\"Bundle\",\"type\":\"searchset\",\"entry\":[]}"));
            server.enqueue(jsonResponse(200, "{\"resourceType\":\"Observation\",\"id\":\"obs-fallback-" + (i + 1) + "\"}"));
        }

        Map<String, Object> result = medplumService.syncPatient(13L, true);
        assertEquals(observationCount, result.get("observationsSynced"));
        assertEquals(0, result.get("paperCaseResourcesSynced"));

        server.takeRequest(); // patient search
        server.takeRequest(); // patient create

        RecordedRequest firstBundleRequest = server.takeRequest();
        assertEquals("POST", firstBundleRequest.getMethod());
        JsonNode firstBundle = objectMapper.readTree(firstBundleRequest.getBody().readUtf8());
        assertEquals(firstChunkSize, firstBundle.path("entry").size());

        RecordedRequest secondBundleRequest = server.takeRequest();
        assertEquals("POST", secondBundleRequest.getMethod());
        JsonNode secondBundle = objectMapper.readTree(secondBundleRequest.getBody().readUtf8());
        assertEquals(remainingChunkSize, secondBundle.path("entry").size());

        RecordedRequest firstFallbackSearch = server.takeRequest();
        assertEquals("GET", firstFallbackSearch.getMethod());
        assertTrue(firstFallbackSearch.getPath().contains("/fhir/R4/Observation?identifier="));
        RecordedRequest firstFallbackCreate = server.takeRequest();
        assertEquals("POST", firstFallbackCreate.getMethod());
        assertEquals("/fhir/R4/Observation", firstFallbackCreate.getPath());

        assertEquals(4 + remainingChunkSize * 2, server.getRequestCount());
    }

    private MockResponse jsonResponse(int status, String body) {
        return new MockResponse()
                .setResponseCode(status)
                .addHeader("Content-Type", "application/fhir+json")
                .setBody(body);
    }

    private MockResponse transactionBundleResponse(int entryCount) throws Exception {
        JsonNode bundle = objectMapper.createObjectNode()
                .put("resourceType", "Bundle")
                .put("type", "transaction-response");
        var entries = objectMapper.createArrayNode();
        for (int i = 0; i < entryCount; i++) {
            entries.add(objectMapper.createObjectNode()
                    .set("response", objectMapper.createObjectNode().put("status", "200")));
        }
        ((com.fasterxml.jackson.databind.node.ObjectNode) bundle).set("entry", entries);
        return jsonResponse(200, objectMapper.writeValueAsString(bundle));
    }

    private HealthRecord buildRichObservationRecord(Long id,
                                                    Long patientId,
                                                    LocalDate recordDate,
                                                    LocalDateTime createdAt) {
        return HealthRecord.builder()
                .id(id)
                .patientId(patientId)
                .recordDate(recordDate)
                .systolicBp(138)
                .diastolicBp(86)
                .restingHeartRate(74)
                .fastingGlucose(6.7)
                .postprandialGlucose(8.9)
                .hba1c(6.4)
                .bmi(23.6)
                .weight(70.2)
                .height(172.0)
                .totalCholesterol(4.8)
                .triglycerides(1.6)
                .ldlCholesterol(2.8)
                .hdlCholesterol(1.3)
                .fev1Percent(76.0)
                .catScore(12)
                .mmrcScore(2)
                .acuteExacerbations(1)
                .smokingYears(18)
                .egfr(92.0)
                .uacr(17.0)
                .serumCreatinine(84.0)
                .hemoglobin(139.0)
                .wbc(6.4)
                .platelet(228)
                .neutrophilPct(61.0)
                .alt(26.0)
                .ast(23.0)
                .uricAcid(332.0)
                .ck(99.0)
                .potassium(4.3)
                .urineProtein("-")
                .urineGlucose("-")
                .urineBlood("-")
                .chestPainFrequency(2)
                .exerciseTolerance(4)
                .ejectionFraction(58.0)
                .bnp(46.0)
                .onAntiplatelet(Boolean.TRUE)
                .hasAtrialFibrillation(Boolean.FALSE)
                .hasDiabetesHistory(Boolean.TRUE)
                .isSmoking(Boolean.FALSE)
                .adlScore(95)
                .mmseScore(29)
                .carotidStenosis(18.0)
                .medicationMissRate(5)
                .onRegularMedication(Boolean.TRUE)
                .saltIntake(2)
                .exerciseFrequency(4)
                .alcoholIntake(1)
                .createdAt(createdAt)
                .build();
    }
}
