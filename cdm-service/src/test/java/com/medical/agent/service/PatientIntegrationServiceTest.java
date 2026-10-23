package com.medical.agent.service;

import com.medical.agent.common.dto.HealthRecordDTO;
import com.medical.agent.model.entity.Doctor;
import com.medical.agent.model.entity.Patient;
import com.medical.agent.model.repository.DoctorRepository;
import com.medical.agent.model.repository.HealthRecordRepository;
import com.medical.agent.model.repository.MedplumSyncCheckpointRepository;
import com.medical.agent.model.repository.PatientMedicalHistoryRepository;
import com.medical.agent.model.repository.PatientRepository;
import com.medical.agent.service.chat.ChatContextService;
import com.medical.agent.service.chat.ChatPatientContextData;
import com.medical.agent.service.testsupport.RepositoryProxyFactory;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PatientIntegrationServiceTest {

    @Test
    void shouldFilterRegisteredPatientsByKeywordAndReadiness() {
        Map<Long, Patient> patients = new LinkedHashMap<>();
        Patient unassigned = patient(10L, "王未绑定", "patient-unassigned", null, false, "FHIR-U");
        Patient ready = patient(11L, "李已接入", "patient-ready", 7L, true, "FHIR-R");
        Patient demo = patient(12L, "演示患者", "demo-patient-sandbox", 7L, true, "FHIR-D");
        patients.put(unassigned.getId(), unassigned);
        patients.put(ready.getId(), ready);
        patients.put(demo.getId(), demo);

        PatientRepository patientRepository = RepositoryProxyFactory.create(PatientRepository.class, Map.of(
                "findAll", args -> new ArrayList<>(patients.values()),
                "findById", args -> Optional.ofNullable(patients.get((Long) args[0]))
        ));
        DoctorRepository doctorRepository = RepositoryProxyFactory.create(DoctorRepository.class, Map.of(
                "findById", args -> Optional.of(Doctor.builder().id(7L).name("责任医生").build())
        ));
        PatientMedicalHistoryRepository historyRepository = RepositoryProxyFactory.create(PatientMedicalHistoryRepository.class, Map.of());
        HealthRecordRepository healthRecordRepository = RepositoryProxyFactory.create(HealthRecordRepository.class, Map.of(
                "findByPatientIdOrderByRecordDateDesc", args -> List.of()
        ));
        ChatContextService chatContextService = new StubChatContextService(Map.of(
                11L, new ChatPatientContextData(
                        ready,
                        List.of(),
                        List.of(),
                        null,
                        null,
                        "",
                        "云端前的本地上下文"
                )
        ));
        MedplumService medplumService = new StubMedplumService(
                Map.of(
                        10L, Map.of("available", true, "found", false, "message", "未映射"),
                        11L, Map.of("available", true, "found", true, "message", "已映射", "medplumPatientId", "mp-11")
                ),
                Map.of(
                        10L, Map.of("available", true, "found", false, "count", 0, "message", "无 Observation"),
                        11L, Map.of("available", true, "found", true, "count", 2, "message", "Observation 已同步")
                )
        );

        PatientIntegrationService service = new PatientIntegrationService(
                patientRepository,
                doctorRepository,
                historyRepository,
                healthRecordRepository,
                chatContextService,
                medplumService
        );

        Map<String, Object> unassignedOverview = service.getOverview("王", "unassigned");
        Map<String, Object> readyOverview = service.getOverview(null, "agent_ready");

        List<Map<String, Object>> unassignedPatients = castRows(unassignedOverview.get("patients"));
        List<Map<String, Object>> readyPatients = castRows(readyOverview.get("patients"));

        assertEquals(1, unassignedPatients.size());
        assertEquals(10L, unassignedPatients.get(0).get("patientId"));
        assertEquals(1, readyPatients.size());
        assertEquals(11L, readyPatients.get(0).get("patientId"));
        assertEquals("云端前的本地上下文", readyPatients.get(0).get("patientContextSummary"));
    }

    @Test
    void shouldBindDoctorForBusinessPatient() {
        Map<Long, Patient> patients = new LinkedHashMap<>();
        Patient patient = patient(21L, "赵患者", "patient-zhao", null, false, "FHIR-Z");
        patients.put(patient.getId(), patient);

        PatientRepository patientRepository = RepositoryProxyFactory.create(PatientRepository.class, Map.of(
                "findById", args -> Optional.ofNullable(patients.get((Long) args[0])),
                "save", args -> {
                    Patient saved = (Patient) args[0];
                    patients.put(saved.getId(), saved);
                    return saved;
                }
        ));
        DoctorRepository doctorRepository = RepositoryProxyFactory.create(DoctorRepository.class, Map.of(
                "findById", args -> Optional.of(Doctor.builder().id(9L).name("新责任医生").build())
        ));
        PatientMedicalHistoryRepository historyRepository = RepositoryProxyFactory.create(PatientMedicalHistoryRepository.class, Map.of());
        HealthRecordRepository healthRecordRepository = RepositoryProxyFactory.create(HealthRecordRepository.class, Map.of(
                "findByPatientIdOrderByRecordDateDesc", args -> List.of()
        ));

        PatientIntegrationService service = new PatientIntegrationService(
                patientRepository,
                doctorRepository,
                historyRepository,
                healthRecordRepository,
                new StubChatContextService(Map.of()),
                new StubMedplumService(Map.of(), Map.of())
        );

        Map<String, Object> result = service.bindDoctor(21L, 9L);

        assertEquals(9L, patients.get(21L).getAssignedDoctorId());
        assertEquals(9L, result.get("assignedDoctorId"));
        assertEquals("新责任医生", result.get("assignedDoctorName"));
        assertTrue(result.containsKey("patient"));
    }

    @Test
    void shouldImportBusinessMockToMedplumWithoutSavingLocalHealthRecord() {
        Map<Long, Patient> patients = new LinkedHashMap<>();
        Patient patient = patient(31L, "云端验证患者", "patient-cloud-only", 8L, true, "FHIR-C");
        patients.put(patient.getId(), patient);
        AtomicBoolean localSaveCalled = new AtomicBoolean(false);

        PatientRepository patientRepository = RepositoryProxyFactory.create(PatientRepository.class, Map.of(
                "findById", args -> Optional.ofNullable(patients.get((Long) args[0]))
        ));
        DoctorRepository doctorRepository = RepositoryProxyFactory.create(DoctorRepository.class, Map.of(
                "findById", args -> Optional.of(Doctor.builder().id(8L).name("责任医生").build())
        ));
        PatientMedicalHistoryRepository historyRepository = RepositoryProxyFactory.create(PatientMedicalHistoryRepository.class, Map.of());
        HealthRecordRepository healthRecordRepository = RepositoryProxyFactory.create(HealthRecordRepository.class, Map.of(
                "findByPatientIdOrderByRecordDateDesc", args -> List.of(),
                "save", args -> {
                    localSaveCalled.set(true);
                    return args[0];
                }
        ));

        PatientIntegrationService service = new PatientIntegrationService(
                patientRepository,
                doctorRepository,
                historyRepository,
                healthRecordRepository,
                new StubChatContextService(Map.of()),
                new StubMedplumService(
                        Map.of(31L, Map.of("available", true, "found", true, "medplumPatientId", "mp-31", "message", "已映射")),
                        Map.of(31L, Map.of("available", true, "found", true, "count", 1, "message", "Observation 已同步"))
                ) {
                    @Override
                    public Map<String, Object> importCloudOnlyObservations(HealthRecordDTO dto, boolean ensurePatientSynced) {
                        return Map.of(
                                "success", true,
                                "patientId", dto.getPatientId(),
                                "patientFhirId", "mp-" + dto.getPatientId(),
                                "observationsSynced", 3,
                                "includeObservations", true,
                                "cloudOnly", true,
                                "message", "云端导入成功"
                        );
                    }
                }
        );

        Map<String, Object> result = service.importMockToMedplum(HealthRecordDTO.builder()
                .patientId(31L)
                .fastingGlucose(8.6)
                .potassium(2.9)
                .build(), true);

        assertEquals(false, localSaveCalled.get());
        assertEquals(true, result.get("cloudOnly"));
        assertEquals(31L, result.get("patientId"));
        assertEquals("正式患者随机健康数据仅已写入 Medplum，本地数据库未落库。", result.get("message"));
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> castRows(Object value) {
        return value instanceof List<?> list ? (List<Map<String, Object>>) list : List.of();
    }

    private Patient patient(Long id,
                            String name,
                            String username,
                            Long assignedDoctorId,
                            boolean profileCompleted,
                            String fhirId) {
        return Patient.builder()
                .id(id)
                .name(name)
                .username(username)
                .phone("1390000" + id)
                .birthDate(LocalDate.of(1980, 1, 1))
                .gender("男")
                .assignedDoctorId(assignedDoctorId)
                .profileCompleted(profileCompleted)
                .fhirId(fhirId)
                .build();
    }

    private static final class StubChatContextService extends ChatContextService {

        private final Map<Long, ChatPatientContextData> contexts;

        private StubChatContextService(Map<Long, ChatPatientContextData> contexts) {
            super(null, null, null, null, null, null);
            this.contexts = contexts;
        }

        @Override
        public ChatPatientContextData loadPatientContext(Long patientId) {
            return contexts.get(patientId);
        }
    }

    private static class StubMedplumService extends MedplumService {

        private final Map<Long, Map<String, Object>> patientByLocalId;
        private final Map<Long, Map<String, Object>> observationsByLocalId;

        private StubMedplumService(Map<Long, Map<String, Object>> patientByLocalId,
                                   Map<Long, Map<String, Object>> observationsByLocalId) {
            super(null, null, null, RepositoryProxyFactory.create(MedplumSyncCheckpointRepository.class, Map.of()));
            this.patientByLocalId = patientByLocalId;
            this.observationsByLocalId = observationsByLocalId;
        }

        @Override
        public Map<String, Object> getStatus(boolean probe) {
            return Map.of(
                    "enabled", true,
                    "configured", true,
                    "authenticated", true,
                    "message", "ok"
            );
        }

        @Override
        public Map<String, Object> getPatientByLocalId(Long patientId) {
            return patientByLocalId.getOrDefault(patientId, Map.of(
                    "available", true,
                    "found", false,
                    "message", "未找到云端患者"
            ));
        }

        @Override
        public Map<String, Object> getLatestObservationsForLocalPatient(Long patientId, int limit) {
            return observationsByLocalId.getOrDefault(patientId, Map.of(
                    "available", true,
                    "found", false,
                    "count", 0,
                    "message", "暂无 Observation"
            ));
        }
    }
}
