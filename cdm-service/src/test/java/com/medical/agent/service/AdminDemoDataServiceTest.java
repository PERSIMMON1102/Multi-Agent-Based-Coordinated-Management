package com.medical.agent.service;

import com.medical.agent.model.entity.Doctor;
import com.medical.agent.model.entity.HealthRecord;
import com.medical.agent.model.entity.Patient;
import com.medical.agent.model.entity.PatientMedicalHistory;
import com.medical.agent.model.repository.DoctorRepository;
import com.medical.agent.model.repository.HealthRecordRepository;
import com.medical.agent.model.repository.PatientMedicalHistoryRepository;
import com.medical.agent.model.repository.PatientRepository;
import com.medical.agent.service.testsupport.RepositoryProxyFactory;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class AdminDemoDataServiceTest {

    private final PasswordService passwordService = new PasswordService();

    @Test
    void shouldRefreshDemoDataIdempotentlyWithoutTouchingNormalUsers() {
        AtomicLong doctorIdSeq = new AtomicLong(10);
        AtomicLong patientIdSeq = new AtomicLong(100);
        AtomicLong recordIdSeq = new AtomicLong(1000);
        AtomicLong historyIdSeq = new AtomicLong(2000);

        Map<Long, Doctor> doctors = new LinkedHashMap<>();
        Map<String, Doctor> doctorByUsername = new LinkedHashMap<>();
        Doctor normalDoctor = Doctor.builder()
                .id(1L)
                .name("正式医生")
                .username("doctor-normal")
                .password("x")
                .build();
        doctors.put(normalDoctor.getId(), normalDoctor);
        doctorByUsername.put(normalDoctor.getUsername(), normalDoctor);

        Map<Long, Patient> patients = new LinkedHashMap<>();
        Map<String, Patient> patientByUsername = new LinkedHashMap<>();
        Patient normalPatient = Patient.builder()
                .id(2L)
                .name("正式患者")
                .username("patient-normal")
                .password("x")
                .build();
        patients.put(normalPatient.getId(), normalPatient);
        patientByUsername.put(normalPatient.getUsername(), normalPatient);

        Map<Long, PatientMedicalHistory> medicalHistoryByPatientId = new LinkedHashMap<>();
        Map<Long, List<HealthRecord>> recordsByPatientId = new LinkedHashMap<>();

        DoctorRepository doctorRepository = RepositoryProxyFactory.create(DoctorRepository.class, Map.of(
                "findByUsername", args -> Optional.ofNullable(doctorByUsername.get(String.valueOf(args[0]))),
                "findAll", args -> new ArrayList<>(doctors.values()),
                "save", args -> {
                    Doctor doctor = (Doctor) args[0];
                    if (doctor.getId() == null) {
                        doctor.setId(doctorIdSeq.incrementAndGet());
                    }
                    doctors.put(doctor.getId(), doctor);
                    doctorByUsername.put(doctor.getUsername(), doctor);
                    return doctor;
                }
        ));

        PatientRepository patientRepository = RepositoryProxyFactory.create(PatientRepository.class, Map.of(
                "findByUsername", args -> Optional.ofNullable(patientByUsername.get(String.valueOf(args[0]))),
                "findAll", args -> new ArrayList<>(patients.values()),
                "save", args -> {
                    Patient patient = (Patient) args[0];
                    if (patient.getId() == null) {
                        patient.setId(patientIdSeq.incrementAndGet());
                    }
                    patients.put(patient.getId(), patient);
                    patientByUsername.put(patient.getUsername(), patient);
                    return patient;
                }
        ));

        PatientMedicalHistoryRepository historyRepository = RepositoryProxyFactory.create(PatientMedicalHistoryRepository.class, Map.of(
                "findByPatientId", args -> Optional.ofNullable(medicalHistoryByPatientId.get((Long) args[0])),
                "save", args -> {
                    PatientMedicalHistory history = (PatientMedicalHistory) args[0];
                    if (history.getId() == null) {
                        history.setId(historyIdSeq.incrementAndGet());
                    }
                    medicalHistoryByPatientId.put(history.getPatientId(), history);
                    return history;
                }
        ));

        HealthRecordRepository healthRecordRepository = RepositoryProxyFactory.create(HealthRecordRepository.class, Map.of(
                "findByPatientIdOrderByRecordDateDesc", args -> new ArrayList<>(
                        recordsByPatientId.getOrDefault((Long) args[0], List.of())
                ),
                "deleteAll", args -> {
                    List<?> deleting = (List<?>) args[0];
                    for (Object item : deleting) {
                        HealthRecord record = (HealthRecord) item;
                        List<HealthRecord> records = recordsByPatientId.get(record.getPatientId());
                        if (records != null) {
                            records.removeIf(existing -> existing.getId().equals(record.getId()));
                        }
                    }
                    return null;
                },
                "save", args -> {
                    HealthRecord record = (HealthRecord) args[0];
                    if (record.getId() == null) {
                        record.setId(recordIdSeq.incrementAndGet());
                    }
                    recordsByPatientId.computeIfAbsent(record.getPatientId(), key -> new ArrayList<>()).add(record);
                    return record;
                }
        ));

        AdminDemoDataService service = new AdminDemoDataService(
                doctorRepository,
                patientRepository,
                historyRepository,
                healthRecordRepository,
                null,
                null,
                passwordService
        );

        Map<String, Object> first = service.refreshDemoData();
        Map<String, Object> second = service.refreshDemoData();
        Map<String, Object> overview = service.getOverview();

        assertEquals(2, first.get("doctorsCreated"));
        assertEquals(3, first.get("patientsCreated"));
        assertEquals(6, first.get("healthRecordsCreated"));
        assertEquals(2, second.get("doctorsUpdated"));
        assertEquals(3, second.get("patientsUpdated"));
        assertEquals(6, second.get("healthRecordsDeleted"));
        assertEquals(6, second.get("healthRecordsCreated"));

        assertEquals(2, overview.get("demoDoctors"));
        assertEquals(3, overview.get("demoPatients"));
        assertEquals(3L, overview.get("demoMedicalHistories"));
        assertEquals(6L, overview.get("demoHealthRecords"));

        assertNotNull(doctorByUsername.get("doctor-normal"));
        assertNotNull(patientByUsername.get("patient-normal"));
    }
}
