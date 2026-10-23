package com.medical.agent.web.controller;

import com.medical.agent.common.dto.PatientDTO;
import com.medical.agent.common.dto.PatientMedicalHistoryDTO;
import com.medical.agent.model.entity.Patient;
import com.medical.agent.model.entity.PatientMedicalHistory;
import com.medical.agent.model.repository.PatientMedicalHistoryRepository;
import com.medical.agent.model.repository.PatientRepository;
import com.medical.agent.service.PatientService;
import com.medical.agent.service.cache.AiChatCacheInvalidationService;
import com.medical.agent.service.cache.AiChatCacheVersionService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.cache.support.NoOpCacheManager;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class PatientControllerTest {

    @Test
    void shouldReturnPatientDetailWithMedicalHistory() {
        PatientController controller = new PatientController(
                new StubPatientService(),
                createProxy(PatientRepository.class, Map.of()),
                createProxy(PatientMedicalHistoryRepository.class, Map.of())
        );

        ResponseEntity<PatientDTO> response = controller.getById(1L);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody().getMedicalHistory());
        assertEquals("高血压", response.getBody().getMedicalHistory().getPastDiseases());
    }

    @Test
    void shouldPersistMedicalHistoryOnCompleteProfile() {
        AtomicReference<PatientMedicalHistory> savedHistory = new AtomicReference<>();
        PatientRepository patientRepository = createProxy(PatientRepository.class, Map.of(
                "findById", args -> Optional.of(Patient.builder().id(1L).name("患者甲").build()),
                "save", args -> args[0]
        ));
        PatientMedicalHistoryRepository historyRepository = createProxy(PatientMedicalHistoryRepository.class, Map.of(
                "findByPatientId", args -> Optional.empty(),
                "save", args -> {
                    PatientMedicalHistory history = (PatientMedicalHistory) args[0];
                    savedHistory.set(history);
                    return history;
                }
        ));

        PatientController controller = new PatientController(
                new StubPatientService(),
                patientRepository,
                historyRepository
        );

        ResponseEntity<?> response = controller.completeProfile(1L, Map.of(
                "name", "患者甲",
                "gender", "男",
                "birthDate", "1980-01-01",
                "phone", "13900000000",
                "address", "上海",
                "medicalHistory", Map.of(
                        "pastDiseases", "高血压",
                        "drugAllergies", "青霉素"
                )
        ));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(savedHistory.get());
        assertEquals("高血压", savedHistory.get().getPastDiseases());
        assertEquals("青霉素", savedHistory.get().getDrugAllergies());
    }

    private static class StubPatientService extends PatientService {
        StubPatientService() {
            super(null, null,
                    null, null,
                    new AiChatCacheInvalidationService(new NoOpCacheManager(), new AiChatCacheVersionService()));
        }

        @Override
        public PatientDTO getById(Long id) {
            return PatientDTO.builder()
                    .id(id)
                    .name("患者甲")
                    .gender("男")
                    .birthDate(LocalDate.of(1980, 1, 1))
                    .medicalHistory(PatientMedicalHistoryDTO.builder()
                            .pastDiseases("高血压")
                            .drugAllergies("青霉素")
                            .build())
                    .build();
        }

        @Override
        public List<PatientDTO> listAll() {
            return List.of();
        }
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
