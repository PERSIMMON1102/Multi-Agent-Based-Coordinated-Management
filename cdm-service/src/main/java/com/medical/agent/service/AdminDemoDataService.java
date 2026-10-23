package com.medical.agent.service;

import com.medical.agent.common.dto.HealthRecordDTO;
import com.medical.agent.common.exception.BusinessException;
import com.medical.agent.model.entity.Doctor;
import com.medical.agent.model.entity.HealthRecord;
import com.medical.agent.model.entity.Patient;
import com.medical.agent.model.entity.PatientMedicalHistory;
import com.medical.agent.model.repository.DoctorRepository;
import com.medical.agent.model.repository.HealthRecordRepository;
import com.medical.agent.model.repository.PatientMedicalHistoryRepository;
import com.medical.agent.model.repository.PatientRepository;
import com.medical.agent.service.cache.CacheNames;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class AdminDemoDataService {

    private static final String DEMO_DOCTOR_PREFIX = "demo-doctor-";
    private static final String DEMO_PATIENT_PREFIX = "demo-patient-";
    private static final String DEMO_FHIR_PREFIX = "demo-patient-";

    private final DoctorRepository doctorRepository;
    private final PatientRepository patientRepository;
    private final PatientMedicalHistoryRepository patientMedicalHistoryRepository;
    private final HealthRecordRepository healthRecordRepository;
    private final HealthRecordService healthRecordService;
    private final MedplumService medplumService;
    private final PasswordService passwordService;

    @Autowired
    public AdminDemoDataService(DoctorRepository doctorRepository,
                                PatientRepository patientRepository,
                                PatientMedicalHistoryRepository patientMedicalHistoryRepository,
                                HealthRecordRepository healthRecordRepository,
                                HealthRecordService healthRecordService,
                                MedplumService medplumService,
                                PasswordService passwordService) {
        this.doctorRepository = doctorRepository;
        this.patientRepository = patientRepository;
        this.patientMedicalHistoryRepository = patientMedicalHistoryRepository;
        this.healthRecordRepository = healthRecordRepository;
        this.healthRecordService = healthRecordService;
        this.medplumService = medplumService;
        this.passwordService = passwordService;
    }

    public AdminDemoDataService(DoctorRepository doctorRepository,
                                PatientRepository patientRepository,
                                PatientMedicalHistoryRepository patientMedicalHistoryRepository,
                                HealthRecordRepository healthRecordRepository,
                                HealthRecordService healthRecordService,
                                MedplumService medplumService) {
        this(doctorRepository, patientRepository, patientMedicalHistoryRepository, healthRecordRepository,
                healthRecordService, medplumService, new PasswordService());
    }

    public Map<String, Object> getOverview() {
        Map<String, Object> result = new LinkedHashMap<>();
        List<Doctor> demoDoctors = findDemoDoctors();
        List<Patient> demoPatients = findDemoPatients();

        long demoHealthRecordCount = demoPatients.stream()
                .map(Patient::getId)
                .filter(id -> id != null)
                .mapToLong(id -> healthRecordRepository.findByPatientIdOrderByRecordDateDesc(id).size())
                .sum();
        long demoMedicalHistoryCount = demoPatients.stream()
                .map(Patient::getId)
                .filter(id -> id != null)
                .map(patientMedicalHistoryRepository::findByPatientId)
                .filter(Optional::isPresent)
                .count();

        result.put("demoDoctors", demoDoctors.size());
        result.put("demoPatients", demoPatients.size());
        result.put("demoMedicalHistories", demoMedicalHistoryCount);
        result.put("demoHealthRecords", demoHealthRecordCount);
        result.put("doctors", demoDoctors.stream().map(doctor -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", doctor.getId());
            row.put("name", doctor.getName());
            row.put("username", doctor.getUsername());
            row.put("department", doctor.getDepartment());
            return row;
        }).toList());
        result.put("patients", demoPatients.stream().map(patient -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", patient.getId());
            row.put("name", patient.getName());
            row.put("username", patient.getUsername());
            row.put("assignedDoctorId", patient.getAssignedDoctorId());
            return row;
        }).toList());
        result.put("doctorUsernames", demoDoctors.stream().map(Doctor::getUsername).toList());
        result.put("patientUsernames", demoPatients.stream().map(Patient::getUsername).toList());
        result.put("message", "预置接入数据概览已加载。");
        return result;
    }

    @Transactional
    @CacheEvict(cacheNames = {
            CacheNames.DOCTOR_DIRECTORY,
            CacheNames.DOCTOR_DASHBOARD_OVERVIEW,
            CacheNames.MEDPLUM_STATUS,
            CacheNames.PATIENT_INTEGRATION_OVERVIEW,
            CacheNames.PATIENT_INTEGRATION_STATUS
    }, allEntries = true)
    public Map<String, Object> refreshDemoData() {
        int doctorsCreated = 0;
        int doctorsUpdated = 0;
        int patientsCreated = 0;
        int patientsUpdated = 0;
        int medicalHistoriesCreated = 0;
        int medicalHistoriesUpdated = 0;
        int healthRecordsCreated = 0;
        int healthRecordsDeleted = 0;

        Map<String, Long> doctorIdByUsername = new LinkedHashMap<>();
        for (DemoDoctorSpec spec : demoDoctors()) {
            Doctor doctor = doctorRepository.findByUsername(spec.username()).orElse(null);
            if (doctor == null) {
                doctor = Doctor.builder()
                        .name(spec.name())
                        .department(spec.department())
                        .phone(spec.phone())
                        .username(spec.username())
                        .password(passwordService.encodeRequiredStrongPassword(spec.password(), "demo doctor password is required"))
                        .build();
                doctor = doctorRepository.save(doctor);
                doctorsCreated++;
            } else {
                doctor.setName(spec.name());
                doctor.setDepartment(spec.department());
                doctor.setPhone(spec.phone());
                doctor.setPassword(passwordService.encodeRequiredStrongPassword(spec.password(), "demo doctor password is required"));
                doctor = doctorRepository.save(doctor);
                doctorsUpdated++;
            }
            doctorIdByUsername.put(spec.username(), doctor.getId());
        }

        List<Patient> refreshedPatients = new ArrayList<>();
        for (DemoPatientSpec spec : demoPatients()) {
            Long doctorId = doctorIdByUsername.get(spec.doctorUsername());
            Patient patient = patientRepository.findByUsername(spec.username()).orElse(null);
            if (patient == null) {
                patient = Patient.builder()
                        .name(spec.name())
                        .gender(spec.gender())
                        .birthDate(spec.birthDate())
                        .phone(spec.phone())
                        .idCard(spec.idCard())
                        .address(spec.address())
                        .username(spec.username())
                        .password(passwordService.encodeRequiredStrongPassword(spec.password(), "demo patient password is required"))
                        .fhirId(spec.fhirId())
                        .emergencyContact(spec.emergencyContact())
                        .emergencyPhone(spec.emergencyPhone())
                        .assignedDoctorId(doctorId)
                        .profileCompleted(true)
                        .build();
                patient = patientRepository.save(patient);
                patientsCreated++;
            } else {
                patient.setName(spec.name());
                patient.setGender(spec.gender());
                patient.setBirthDate(spec.birthDate());
                patient.setPhone(spec.phone());
                patient.setIdCard(spec.idCard());
                patient.setAddress(spec.address());
                patient.setPassword(passwordService.encodeRequiredStrongPassword(spec.password(), "demo patient password is required"));
                patient.setFhirId(spec.fhirId());
                patient.setEmergencyContact(spec.emergencyContact());
                patient.setEmergencyPhone(spec.emergencyPhone());
                patient.setAssignedDoctorId(doctorId);
                patient.setProfileCompleted(true);
                patient = patientRepository.save(patient);
                patientsUpdated++;
            }
            refreshedPatients.add(patient);

            PatientMedicalHistory history = patientMedicalHistoryRepository.findByPatientId(patient.getId()).orElse(null);
            if (history == null) {
                history = PatientMedicalHistory.builder()
                        .patientId(patient.getId())
                        .build();
                medicalHistoriesCreated++;
            } else {
                medicalHistoriesUpdated++;
            }
            history.setPastDiseases(spec.pastDiseases());
            history.setSurgicalHistory(spec.surgicalHistory());
            history.setDrugAllergies(spec.drugAllergies());
            history.setFoodAllergies(spec.foodAllergies());
            history.setImmunizationHistory(spec.immunizationHistory());
            history.setFamilyHistory(spec.familyHistory());
            history.setOtherNotes(spec.otherNotes());
            patientMedicalHistoryRepository.save(history);
        }

        for (Patient patient : refreshedPatients) {
            List<HealthRecord> existingRecords = healthRecordRepository.findByPatientIdOrderByRecordDateDesc(patient.getId());
            healthRecordsDeleted += existingRecords.size();
            if (!existingRecords.isEmpty()) {
                healthRecordRepository.deleteAll(existingRecords);
            }

            for (HealthRecord template : demoHealthRecords(patient.getId())) {
                healthRecordRepository.save(template);
                healthRecordsCreated++;
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("doctorsCreated", doctorsCreated);
        result.put("doctorsUpdated", doctorsUpdated);
        result.put("patientsCreated", patientsCreated);
        result.put("patientsUpdated", patientsUpdated);
        result.put("medicalHistoriesCreated", medicalHistoriesCreated);
        result.put("medicalHistoriesUpdated", medicalHistoriesUpdated);
        result.put("healthRecordsDeleted", healthRecordsDeleted);
        result.put("healthRecordsCreated", healthRecordsCreated);
        result.put("message", "预置账号、患者、病史与健康记录已幂等刷新。");
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
            throw new BusinessException(400, "patientId 不能为空");
        }
        Patient patient = patientRepository.findById(dto.getPatientId())
                .orElseThrow(() -> new BusinessException(404, "患者不存在"));
        if (patient.getUsername() == null || !patient.getUsername().startsWith(DEMO_PATIENT_PREFIX)) {
            throw new BusinessException(400, "当前仅允许导入预置患者数据，普通业务患者请走医生录入流程。");
        }
        HealthRecordDTO saved = healthRecordService.create(dto, null, false, Map.of());
        Map<String, Object> syncResult = medplumService.syncPatient(dto.getPatientId(), includeObservations);

        Map<String, Object> result = new LinkedHashMap<>(syncResult);
        result.put("localRecordId", saved.getId());
        result.put("patientId", dto.getPatientId());
        result.put("message", "补充数据已写入本地并同步到 Medplum Cloud。");
        return result;
    }

    private List<Doctor> findDemoDoctors() {
        return doctorRepository.findAll().stream()
                .filter(doctor -> doctor.getUsername() != null && doctor.getUsername().startsWith(DEMO_DOCTOR_PREFIX))
                .collect(Collectors.toList());
    }

    private List<Patient> findDemoPatients() {
        return patientRepository.findAll().stream()
                .filter(patient -> patient.getUsername() != null && patient.getUsername().startsWith(DEMO_PATIENT_PREFIX))
                .collect(Collectors.toList());
    }

    private List<DemoDoctorSpec> demoDoctors() {
        return List.of(
                new DemoDoctorSpec(
                        "心内科医生",
                        "心内科",
                        "13800001001",
                        DEMO_DOCTOR_PREFIX + "cardio",
                        "DemoDoc123"
                ),
                new DemoDoctorSpec(
                        "内分泌科医生",
                        "内分泌科",
                        "13800001002",
                        DEMO_DOCTOR_PREFIX + "endo",
                        "DemoDoc123"
                )
        );
    }

    private List<DemoPatientSpec> demoPatients() {
        return List.of(
                new DemoPatientSpec(
                        "高血压患者",
                        "男",
                        LocalDate.of(1968, 4, 12),
                        "13900001001",
                        "DEMO000000000001",
                        "北京市朝阳区健康路 1 号",
                        DEMO_PATIENT_PREFIX + "hypertension",
                        "DemoPt123",
                        DEMO_FHIR_PREFIX + "hypertension",
                        "家属甲",
                        "13900001991",
                        DEMO_DOCTOR_PREFIX + "cardio",
                        "高血压；血脂异常",
                        "阑尾切除术",
                        "青霉素",
                        "无",
                        "流感疫苗（每年）",
                        "父亲高血压",
                        "长期居家血压监测"
                ),
                new DemoPatientSpec(
                        "糖尿病患者",
                        "女",
                        LocalDate.of(1975, 9, 20),
                        "13900001002",
                        "DEMO000000000002",
                        "上海市浦东新区康宁路 2 号",
                        DEMO_PATIENT_PREFIX + "diabetes",
                        "DemoPt123",
                        DEMO_FHIR_PREFIX + "diabetes",
                        "家属乙",
                        "13900001992",
                        DEMO_DOCTOR_PREFIX + "endo",
                        "2 型糖尿病；慢性肾病 3 期",
                        "无",
                        "磺胺类过敏",
                        "海鲜轻度过敏",
                        "乙肝疫苗",
                        "母亲糖尿病",
                        "需关注肾功能趋势"
                ),
                new DemoPatientSpec(
                        "综合慢病患者",
                        "男",
                        LocalDate.of(1959, 12, 5),
                        "13900001003",
                        "DEMO000000000003",
                        "广州市天河区安康路 3 号",
                        DEMO_PATIENT_PREFIX + "complex",
                        "DemoPt123",
                        DEMO_FHIR_PREFIX + "complex",
                        "家属丙",
                        "13900001993",
                        DEMO_DOCTOR_PREFIX + "cardio",
                        "冠心病；脑卒中后遗症；COPD",
                        "冠脉支架植入术",
                        "无",
                        "无",
                        "肺炎疫苗",
                        "父亲冠心病",
                        "需长期综合慢病管理"
                )
        );
    }

    private List<HealthRecord> demoHealthRecords(Long patientId) {
        HealthRecord latest = HealthRecord.builder()
                .patientId(patientId)
                .recordDate(LocalDate.now().minusDays(1))
                .systolicBp(148)
                .diastolicBp(92)
                .restingHeartRate(84)
                .fastingGlucose(8.2)
                .postprandialGlucose(11.4)
                .hba1c(7.6)
                .bmi(27.4)
                .weight(76.0)
                .height(166.0)
                .totalCholesterol(5.9)
                .triglycerides(2.4)
                .ldlCholesterol(3.6)
                .hdlCholesterol(1.0)
                .fev1Percent(63.0)
                .catScore(18)
                .mmrcScore(2)
                .acuteExacerbations(1)
                .smokingYears(20)
                .egfr(62.0)
                .uacr(74.0)
                .serumCreatinine(118.0)
                .hemoglobin(132.0)
                .wbc(7.2)
                .platelet(226)
                .neutrophilPct(64.0)
                .alt(34.0)
                .ast(28.0)
                .uricAcid(402.0)
                .ck(162.0)
                .potassium(4.2)
                .urineProtein("TRACE")
                .urineGlucose("NEGATIVE")
                .urineBlood("NEGATIVE")
                .chestPainFrequency(2)
                .exerciseTolerance(3)
                .ejectionFraction(46.0)
                .bnp(188.0)
                .onAntiplatelet(true)
                .hasAtrialFibrillation(false)
                .hasDiabetesHistory(true)
                .isSmoking(false)
                .adlScore(85)
                .mmseScore(26)
                .carotidStenosis(38.0)
                .medicationMissRate(9)
                .onRegularMedication(true)
                .saltIntake(3)
                .exerciseFrequency(3)
                .alcoholIntake(1)
                .build();

        HealthRecord baseline = HealthRecord.builder()
                .patientId(patientId)
                .recordDate(LocalDate.now().minusDays(35))
                .systolicBp(156)
                .diastolicBp(96)
                .restingHeartRate(88)
                .fastingGlucose(8.9)
                .postprandialGlucose(12.6)
                .hba1c(8.1)
                .bmi(28.0)
                .weight(77.5)
                .height(166.0)
                .totalCholesterol(6.3)
                .triglycerides(2.8)
                .ldlCholesterol(3.9)
                .hdlCholesterol(0.9)
                .fev1Percent(60.0)
                .catScore(20)
                .mmrcScore(3)
                .acuteExacerbations(2)
                .smokingYears(20)
                .egfr(58.0)
                .uacr(86.0)
                .serumCreatinine(126.0)
                .hemoglobin(128.0)
                .wbc(8.1)
                .platelet(248)
                .neutrophilPct(69.0)
                .alt(41.0)
                .ast(35.0)
                .uricAcid(436.0)
                .ck(188.0)
                .potassium(4.4)
                .urineProtein("1+")
                .urineGlucose("TRACE")
                .urineBlood("TRACE")
                .chestPainFrequency(3)
                .exerciseTolerance(2)
                .ejectionFraction(43.0)
                .bnp(220.0)
                .onAntiplatelet(true)
                .hasAtrialFibrillation(true)
                .hasDiabetesHistory(true)
                .isSmoking(true)
                .adlScore(80)
                .mmseScore(24)
                .carotidStenosis(42.0)
                .medicationMissRate(15)
                .onRegularMedication(false)
                .saltIntake(4)
                .exerciseFrequency(1)
                .alcoholIntake(2)
                .build();

        return List.of(latest, baseline);
    }

    private record DemoDoctorSpec(String name,
                                  String department,
                                  String phone,
                                  String username,
                                  String password) {
    }

    private record DemoPatientSpec(String name,
                                   String gender,
                                   LocalDate birthDate,
                                   String phone,
                                   String idCard,
                                   String address,
                                   String username,
                                   String password,
                                   String fhirId,
                                   String emergencyContact,
                                   String emergencyPhone,
                                   String doctorUsername,
                                   String pastDiseases,
                                   String surgicalHistory,
                                   String drugAllergies,
                                   String foodAllergies,
                                   String immunizationHistory,
                                   String familyHistory,
                                   String otherNotes) {
    }
}
