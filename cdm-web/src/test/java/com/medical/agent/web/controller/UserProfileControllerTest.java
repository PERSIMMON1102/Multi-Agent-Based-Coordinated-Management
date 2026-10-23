package com.medical.agent.web.controller;

import com.medical.agent.model.entity.Patient;
import com.medical.agent.model.entity.PatientMedicalHistory;
import com.medical.agent.model.repository.DoctorRepository;
import com.medical.agent.model.repository.PatientMedicalHistoryRepository;
import com.medical.agent.model.repository.PatientRepository;
import com.medical.agent.service.security.UploadSecurityService;
import com.medical.agent.web.config.OssConfig;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class UserProfileControllerTest {

    @Test
    void shouldReturnPatientProfileWithNestedMedicalHistory() {
        PatientRepository patientRepository = createProxy(PatientRepository.class, Map.of(
                "findById", args -> Optional.of(patient())
        ));
        PatientMedicalHistoryRepository historyRepository = createProxy(PatientMedicalHistoryRepository.class, Map.of(
                "findByPatientId", args -> Optional.of(history())
        ));
        DoctorRepository doctorRepository = createProxy(DoctorRepository.class, Map.of());

        UserProfileController controller = new UserProfileController(
                patientRepository,
                historyRepository,
                doctorRepository,
                new OssConfig(),
                null
        );

        ResponseEntity<Map<String, Object>> response = controller.getPatientProfile(1L);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("患者甲", response.getBody().get("name"));
        @SuppressWarnings("unchecked")
        Map<String, Object> medicalHistory = (Map<String, Object>) response.getBody().get("medicalHistory");
        assertNotNull(medicalHistory);
        assertEquals("高血压、糖尿病", medicalHistory.get("pastDiseases"));
        assertEquals("青霉素", medicalHistory.get("drugAllergies"));
    }

    @Test
    void shouldUpsertMedicalHistoryWhenUpdatingPatientProfile() {
        AtomicReference<PatientMedicalHistory> savedHistory = new AtomicReference<>();
        PatientRepository patientRepository = createProxy(PatientRepository.class, Map.of(
                "findById", args -> Optional.of(patient()),
                "save", args -> args[0]
        ));
        PatientMedicalHistoryRepository historyRepository = createProxy(PatientMedicalHistoryRepository.class, Map.of(
                "findByPatientId", args -> Optional.of(PatientMedicalHistory.builder().patientId(1L).pastDiseases("高血压").build()),
                "save", args -> {
                    PatientMedicalHistory history = (PatientMedicalHistory) args[0];
                    savedHistory.set(history);
                    return history;
                }
        ));
        DoctorRepository doctorRepository = createProxy(DoctorRepository.class, Map.of());

        UserProfileController controller = new UserProfileController(
                patientRepository,
                historyRepository,
                doctorRepository,
                new OssConfig(),
                null
        );

        ResponseEntity<Map<String, Object>> response = controller.updatePatientProfile(1L, Map.of(
                "phone", "13900000000",
                "medicalHistory", Map.of(
                        "pastDiseases", "高血压、糖尿病",
                        "drugAllergies", "青霉素"
                )
        ));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(savedHistory.get());
        assertEquals("高血压、糖尿病", savedHistory.get().getPastDiseases());
        assertEquals("青霉素", savedHistory.get().getDrugAllergies());
    }

    @Test
    void shouldRejectAvatarUploadWhenImageSignatureIsInvalid() {
        PatientRepository patientRepository = createProxy(PatientRepository.class, Map.of());
        PatientMedicalHistoryRepository historyRepository = createProxy(PatientMedicalHistoryRepository.class, Map.of());
        DoctorRepository doctorRepository = createProxy(DoctorRepository.class, Map.of());

        UserProfileController controller = new UserProfileController(
                patientRepository,
                historyRepository,
                doctorRepository,
                new OssConfig(),
                null,
                new com.medical.agent.web.security.AccessGuard(null, null, null, null, null, null),
                null,
                new UploadSecurityService()
        );

        MockMultipartFile fakeAvatar = new MockMultipartFile(
                "file",
                "avatar.jpg",
                "image/jpeg",
                "not-an-image".getBytes()
        );

        Assertions.assertThrows(com.medical.agent.common.exception.BusinessException.class,
                () -> controller.uploadAvatar(fakeAvatar));
    }

    private Patient patient() {
        return Patient.builder()
                .id(1L)
                .name("患者甲")
                .gender("男")
                .phone("13800000000")
                .build();
    }

    private PatientMedicalHistory history() {
        return PatientMedicalHistory.builder()
                .patientId(1L)
                .pastDiseases("高血压、糖尿病")
                .drugAllergies("青霉素")
                .build();
    }

    private <T> T createProxy(Class<T> type, Map<String, Function<Object[], Object>> handlers) {
        InvocationHandler invocationHandler = (proxy, method, args) -> {
            String methodName = method.getName();
            if ("toString".equals(methodName)) {
                return type.getSimpleName() + "Proxy";
            }
            if ("hashCode".equals(methodName)) {
                return System.identityHashCode(proxy);
            }
            if ("equals".equals(methodName)) {
                return proxy == args[0];
            }

            Function<Object[], Object> handler = handlers.get(methodName);
            if (handler != null) {
                return handler.apply(args == null ? new Object[0] : args);
            }
            return defaultValue(method);
        };
        Object proxy = Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, invocationHandler);
        return type.cast(proxy);
    }

    private Object defaultValue(Method method) {
        Class<?> returnType = method.getReturnType();
        if (!returnType.isPrimitive()) {
            if (Optional.class.isAssignableFrom(returnType)) {
                return Optional.empty();
            }
            if (List.class.isAssignableFrom(returnType)) {
                return List.of();
            }
            if (Set.class.isAssignableFrom(returnType)) {
                return Set.of();
            }
            if (Map.class.isAssignableFrom(returnType)) {
                return Map.of();
            }
            return null;
        }
        if (boolean.class.equals(returnType)) {
            return false;
        }
        if (byte.class.equals(returnType)) {
            return (byte) 0;
        }
        if (short.class.equals(returnType)) {
            return (short) 0;
        }
        if (int.class.equals(returnType)) {
            return 0;
        }
        if (long.class.equals(returnType)) {
            return 0L;
        }
        if (float.class.equals(returnType)) {
            return 0f;
        }
        if (double.class.equals(returnType)) {
            return 0d;
        }
        if (char.class.equals(returnType)) {
            return '\0';
        }
        return null;
    }
}
