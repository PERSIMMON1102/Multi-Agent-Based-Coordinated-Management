package com.medical.agent.service;

import com.medical.agent.common.exception.BusinessException;
import com.medical.agent.model.entity.Admin;
import com.medical.agent.model.entity.Doctor;
import com.medical.agent.model.entity.Patient;
import com.medical.agent.model.repository.AdminRepository;
import com.medical.agent.model.repository.DoctorRepository;
import com.medical.agent.model.repository.PatientRepository;
import com.medical.agent.service.cache.CacheNames;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class AuthService {

    private final AdminRepository adminRepository;
    private final DoctorRepository doctorRepository;
    private final PatientRepository patientRepository;
    private final PasswordService passwordService;
    @Value("${auth.patient-registration-enabled:true}")
    private boolean patientRegistrationEnabled;

    @Autowired
    public AuthService(AdminRepository adminRepository,
                       DoctorRepository doctorRepository,
                       PatientRepository patientRepository,
                       PasswordService passwordService) {
        this.adminRepository = adminRepository;
        this.doctorRepository = doctorRepository;
        this.patientRepository = patientRepository;
        this.passwordService = passwordService;
    }

    public AuthService(AdminRepository adminRepository,
                       DoctorRepository doctorRepository,
                       PatientRepository patientRepository) {
        this(adminRepository, doctorRepository, patientRepository, new PasswordService());
    }

    public Map<String, Object> login(String username, String password, String role) {
        if (isBlank(username) || isBlank(password)) {
            throw new BusinessException(400, "用户名和密码不能为空");
        }

        if (isBlank(role)) {
            return autoDetectRoleLogin(username, password);
        }

        switch (role) {
            case "admin":
                return adminLogin(username, password);
            case "doctor":
                return doctorLogin(username, password);
            case "patient":
                return patientLogin(username, password);
            default:
                throw new BusinessException(400, "不支持的登录角色: " + role);
        }
    }

    private Map<String, Object> autoDetectRoleLogin(String username, String password) {
        String normalizedUsername = username.trim();

        Admin admin = adminRepository.findByUsername(normalizedUsername).orElse(null);
        if (admin != null && passwordService.matches(password, admin.getPassword())) {
            return adminLogin(normalizedUsername, password);
        }

        Doctor doctor = doctorRepository.findByUsername(normalizedUsername).orElse(null);
        if (doctor != null && passwordService.matches(password, doctor.getPassword())) {
            return doctorLogin(normalizedUsername, password);
        }

        Patient patient = patientRepository.findByUsername(normalizedUsername).orElse(null);
        if (patient != null && passwordService.matches(password, patient.getPassword())) {
            return patientLogin(normalizedUsername, password);
        }

        throw new BusinessException(401, "用户名或密码错误");
    }

    @Transactional
    @CacheEvict(cacheNames = {
            CacheNames.DOCTOR_DASHBOARD_OVERVIEW,
            CacheNames.PATIENT_INTEGRATION_OVERVIEW,
            CacheNames.PATIENT_INTEGRATION_STATUS
    }, allEntries = true)
    public Map<String, Object> registerPatient(String username, String password, String name) {
        if (!patientRegistrationEnabled) {
            throw new BusinessException(403, "患者自助注册已关闭，请联系管理员创建账号");
        }
        if (isBlank(username) || isBlank(password) || isBlank(name)) {
            throw new BusinessException(400, "用户名、密码和姓名不能为空");
        }
        String normalizedPassword = passwordService.encodeRequiredStrongPassword(password, "密码不能为空");
        ensureUsernameAvailable(username, null, null, null);

        Patient patient = Patient.builder()
                .name(name.trim())
                .username(username.trim())
                .password(normalizedPassword)
                .profileCompleted(false)
                .fhirId("FHIR-" + UUID.randomUUID().toString().substring(0, 8))
                .build();
        patient = patientRepository.save(patient);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("message", "注册成功");
        result.put("patientId", patient.getId());
        result.put("username", patient.getUsername());
        result.put("displayName", patient.getName());
        result.put("profileCompleted", Boolean.FALSE);
        return result;
    }

    @Cacheable(cacheNames = CacheNames.AUTH_CONFIG, key = "'default'", sync = true)
    public Map<String, Object> getAuthConfig() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("patientRegistrationEnabled", patientRegistrationEnabled);
        return result;
    }

    @Cacheable(cacheNames = CacheNames.DOCTOR_DIRECTORY, key = "'all'", sync = true)
    public List<Map<String, Object>> listDoctors() {
        List<Doctor> doctors = doctorRepository.findAll(Sort.by(Sort.Direction.ASC, "id"));
        List<Map<String, Object>> result = new ArrayList<>();
        for (Doctor doctor : doctors) {
            result.add(toDoctorMap(doctor));
        }
        return result;
    }

    public void ensureUsernameAvailable(String username, Long excludeAdminId, Long excludeDoctorId, Long excludePatientId) {
        String normalized = username.trim();

        adminRepository.findByUsername(normalized).ifPresent(admin -> {
            if (excludeAdminId == null || !admin.getId().equals(excludeAdminId)) {
                throw new BusinessException(409, "用户名已存在");
            }
        });

        doctorRepository.findByUsername(normalized).ifPresent(doctor -> {
            if (excludeDoctorId == null || !doctor.getId().equals(excludeDoctorId)) {
                throw new BusinessException(409, "用户名已存在");
            }
        });

        patientRepository.findByUsername(normalized).ifPresent(patient -> {
            if (excludePatientId == null || !patient.getId().equals(excludePatientId)) {
                throw new BusinessException(409, "用户名已存在");
            }
        });
    }

    private Map<String, Object> adminLogin(String username, String password) {
        Admin admin = adminRepository.findByUsername(username.trim())
                .orElseThrow(() -> new BusinessException(401, "用户名或密码错误"));
        if (!passwordService.matches(password, admin.getPassword())) {
            throw new BusinessException(401, "用户名或密码错误");
        }
        if (passwordService.needsUpgrade(admin.getPassword())) {
            admin.setPassword(passwordService.encode(password));
            adminRepository.save(admin);
        }
        Integer mustChangePasswordFlag = adminRepository.findMustChangePasswordFlagByUsername(admin.getUsername());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("role", "admin");
        result.put("adminId", admin.getId());
        result.put("username", admin.getUsername());
        result.put("displayName", admin.getName());
        result.put("mustChangePassword", mustChangePasswordFlag != null && mustChangePasswordFlag == 1);
        return result;
    }

    private Map<String, Object> doctorLogin(String username, String password) {
        Doctor doctor = doctorRepository.findByUsername(username.trim())
                .orElseThrow(() -> new BusinessException(401, "用户名或密码错误"));
        if (!passwordService.matches(password, doctor.getPassword())) {
            throw new BusinessException(401, "用户名或密码错误");
        }
        if (passwordService.needsUpgrade(doctor.getPassword())) {
            doctor.setPassword(passwordService.encode(password));
            doctorRepository.save(doctor);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("role", "doctor");
        result.put("username", doctor.getUsername());
        result.put("displayName", doctor.getName());
        result.put("doctorId", doctor.getId());
        return result;
    }

    private Map<String, Object> patientLogin(String username, String password) {
        Patient patient = patientRepository.findByUsername(username.trim())
                .orElseThrow(() -> new BusinessException(401, "用户名或密码错误"));
        if (!passwordService.matches(password, patient.getPassword())) {
            throw new BusinessException(401, "用户名或密码错误");
        }
        if (passwordService.needsUpgrade(patient.getPassword())) {
            patient.setPassword(passwordService.encode(password));
            patientRepository.save(patient);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("role", "patient");
        result.put("username", patient.getUsername());
        result.put("displayName", patient.getName());
        result.put("patientId", patient.getId());
        result.put("assignedDoctorId", patient.getAssignedDoctorId());
        result.put("profileCompleted", Boolean.TRUE.equals(patient.getProfileCompleted()));
        return result;
    }

    private Map<String, Object> toDoctorMap(Doctor doctor) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", doctor.getId());
        map.put("name", doctor.getName());
        map.put("department", doctor.getDepartment());
        map.put("phone", doctor.getPhone());
        map.put("username", doctor.getUsername());
        return map;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
