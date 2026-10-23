package com.medical.agent.service;

import com.medical.agent.common.exception.BusinessException;
import com.medical.agent.model.entity.Admin;
import com.medical.agent.model.entity.Doctor;
import com.medical.agent.model.entity.Patient;
import com.medical.agent.model.repository.AdminRepository;
import com.medical.agent.model.repository.DoctorRepository;
import com.medical.agent.model.repository.PatientRepository;
import com.medical.agent.service.testsupport.RepositoryProxyFactory;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AuthServiceTest {

    private final PasswordService passwordService = new PasswordService();

    @Test
    void shouldAutoDetectRoleWhenRoleIsBlank() {
        Admin admin = Admin.builder()
                .id(1L)
                .username("admin01")
                .password("Admin12345")
                .name("系统管理员")
                .build();
        Doctor doctor = Doctor.builder()
                .id(2L)
                .username("doctor01")
                .password("Doctor12345")
                .name("张医生")
                .build();
        Patient patient = Patient.builder()
                .id(3L)
                .username("patient01")
                .password("Patient12345")
                .name("李患者")
                .profileCompleted(true)
                .build();

        AdminRepository adminRepository = RepositoryProxyFactory.create(AdminRepository.class, Map.of(
                "findByUsername", args -> Optional.ofNullable("admin01".equals(args[0]) ? admin : null),
                "findMustChangePasswordFlagByUsername", args -> 1
        ));
        DoctorRepository doctorRepository = RepositoryProxyFactory.create(DoctorRepository.class, Map.of(
                "findByUsername", args -> Optional.ofNullable("doctor01".equals(args[0]) ? doctor : null)
        ));
        PatientRepository patientRepository = RepositoryProxyFactory.create(PatientRepository.class, Map.of(
                "findByUsername", args -> Optional.ofNullable("patient01".equals(args[0]) ? patient : null)
        ));

        AuthService authService = new AuthService(adminRepository, doctorRepository, patientRepository, passwordService);

        Map<String, Object> adminResult = authService.login("admin01", "Admin12345", null);
        Map<String, Object> doctorResult = authService.login("doctor01", "Doctor12345", "");
        Map<String, Object> patientResult = authService.login("patient01", "Patient12345", "   ");

        assertEquals("admin", adminResult.get("role"));
        assertEquals("doctor", doctorResult.get("role"));
        assertEquals("patient", patientResult.get("role"));
    }

    @Test
    void shouldRejectWhenAutoDetectCannotFindMatchingCredential() {
        AdminRepository adminRepository = RepositoryProxyFactory.create(AdminRepository.class, Map.of());
        DoctorRepository doctorRepository = RepositoryProxyFactory.create(DoctorRepository.class, Map.of());
        PatientRepository patientRepository = RepositoryProxyFactory.create(PatientRepository.class, Map.of());
        AuthService authService = new AuthService(adminRepository, doctorRepository, patientRepository, passwordService);

        BusinessException error = assertThrows(BusinessException.class,
                () -> authService.login("nobody", "bad", null));
        assertEquals(401, error.getCode());
    }
}

