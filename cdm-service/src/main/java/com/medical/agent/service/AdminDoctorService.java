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
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class AdminDoctorService {

    private final AdminRepository adminRepository;
    private final DoctorRepository doctorRepository;
    private final PatientRepository patientRepository;
    private final AuthService authService;
    private final PasswordService passwordService;

    @Autowired
    public AdminDoctorService(AdminRepository adminRepository,
                              DoctorRepository doctorRepository,
                              PatientRepository patientRepository,
                              AuthService authService,
                              PasswordService passwordService) {
        this.adminRepository = adminRepository;
        this.doctorRepository = doctorRepository;
        this.patientRepository = patientRepository;
        this.authService = authService;
        this.passwordService = passwordService;
    }

    public AdminDoctorService(AdminRepository adminRepository,
                              DoctorRepository doctorRepository,
                              PatientRepository patientRepository,
                              AuthService authService) {
        this(adminRepository, doctorRepository, patientRepository, authService, new PasswordService());
    }

    @Cacheable(cacheNames = CacheNames.DOCTOR_DIRECTORY, key = "'all'", sync = true)
    public List<Map<String, Object>> listDoctors() {
        List<Doctor> doctors = doctorRepository.findAll(Sort.by(Sort.Direction.ASC, "id"));
        List<Map<String, Object>> result = new ArrayList<>();
        for (Doctor doctor : doctors) {
            result.add(toMap(doctor));
        }
        return result;
    }

    @Transactional
    @CacheEvict(cacheNames = {
            CacheNames.DOCTOR_DIRECTORY,
            CacheNames.DOCTOR_DASHBOARD_OVERVIEW,
            CacheNames.PATIENT_INTEGRATION_OVERVIEW,
            CacheNames.PATIENT_INTEGRATION_STATUS
    }, allEntries = true)
    public Map<String, Object> createDoctor(Map<String, Object> body) {
        String name = requireText(body.get("name"), "姓名不能为空");
        String username = requireText(body.get("username"), "用户名不能为空");
        String password = passwordService.encodeRequiredStrongPassword(text(body.get("password")), "密码不能为空");

        authService.ensureUsernameAvailable(username, null, null, null);

        Doctor doctor = Doctor.builder()
                .name(name)
                .department(text(body.get("department")))
                .phone(text(body.get("phone")))
                .username(username)
                .password(password)
                .build();
        doctor = doctorRepository.save(doctor);
        return toMap(doctor);
    }

    @Transactional
    @CacheEvict(cacheNames = {
            CacheNames.DOCTOR_DIRECTORY,
            CacheNames.DOCTOR_DASHBOARD_OVERVIEW,
            CacheNames.PATIENT_INTEGRATION_OVERVIEW,
            CacheNames.PATIENT_INTEGRATION_STATUS
    }, allEntries = true)
    public Map<String, Object> updateDoctor(Long id, Map<String, Object> body) {
        Doctor doctor = doctorRepository.findById(id)
                .orElseThrow(() -> new BusinessException(404, "医生不存在"));

        if (body.containsKey("name") && body.get("name") != null) {
            doctor.setName(requireText(body.get("name"), "姓名不能为空"));
        }
        if (body.containsKey("department")) {
            doctor.setDepartment(text(body.get("department")));
        }
        if (body.containsKey("phone")) {
            doctor.setPhone(text(body.get("phone")));
        }
        if (body.containsKey("username") && body.get("username") != null) {
            String username = requireText(body.get("username"), "用户名不能为空");
            authService.ensureUsernameAvailable(username, null, doctor.getId(), null);
            doctor.setUsername(username);
        }
        if (body.containsKey("password")) {
            String password = passwordService.encodeOptionalStrongPassword(text(body.get("password")));
            if (password != null) {
                doctor.setPassword(password);
            }
        }

        doctor = doctorRepository.save(doctor);
        return toMap(doctor);
    }

    @Transactional
    @CacheEvict(cacheNames = {
            CacheNames.DOCTOR_DIRECTORY,
            CacheNames.DOCTOR_DASHBOARD_OVERVIEW,
            CacheNames.PATIENT_INTEGRATION_OVERVIEW,
            CacheNames.PATIENT_INTEGRATION_STATUS
    }, allEntries = true)
    public Map<String, Object> deleteDoctor(Long id) {
        Doctor doctor = doctorRepository.findById(id)
                .orElseThrow(() -> new BusinessException(404, "医生不存在"));

        List<Patient> patients = patientRepository.findByAssignedDoctorId(id);
        for (Patient patient : patients) {
            patient.setAssignedDoctorId(null);
        }
        patientRepository.saveAll(patients);
        doctorRepository.delete(doctor);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("message", "医生账号已删除");
        result.put("doctorId", id);
        result.put("unassignedPatients", patients.size());
        return result;
    }

    @Transactional
    @CacheEvict(cacheNames = CacheNames.PATIENT_INTEGRATION_STATUS, allEntries = true)
    public Map<String, Object> changeAdminPassword(Map<String, Object> body) {
        String username = requireText(body.get("username"), "管理员用户名不能为空");
        String currentPassword = requireText(body.get("currentPassword"), "当前密码不能为空");
        String newPassword = PasswordPolicyUtils.requireStrongPassword(text(body.get("newPassword")), "新密码不能为空");

        Admin admin = adminRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessException(404, "管理员不存在"));
        if (!passwordService.matches(currentPassword, admin.getPassword())) {
            throw new BusinessException(401, "当前密码错误");
        }
        if (currentPassword.equals(newPassword)) {
            throw new BusinessException(400, "新密码不能与当前密码相同");
        }

        admin.setPassword(passwordService.encodeRequiredStrongPassword(newPassword, "新密码不能为空"));
        admin.setMustChangePassword(false);
        adminRepository.save(admin);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("message", "管理员密码已更新");
        result.put("username", admin.getUsername());
        return result;
    }

    private Map<String, Object> toMap(Doctor doctor) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", doctor.getId());
        map.put("name", doctor.getName());
        map.put("department", doctor.getDepartment());
        map.put("phone", doctor.getPhone());
        map.put("username", doctor.getUsername());
        map.put("createdAt", doctor.getCreatedAt() != null ? doctor.getCreatedAt().toString() : null);
        return map;
    }

    private String requireText(Object value, String message) {
        String text = text(value);
        if (text == null || text.isBlank()) {
            throw new BusinessException(400, message);
        }
        return text;
    }

    private String text(Object value) {
        return value == null ? null : value.toString().trim();
    }
}
