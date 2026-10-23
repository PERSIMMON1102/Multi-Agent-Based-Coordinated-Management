package com.medical.agent.web.controller;

import com.medical.agent.service.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class AuthControllerTest {

    @Test
    void shouldLoginWithoutRoleAndReturnDetectedRole() {
        AuthController controller = new AuthController(new StubAuthService());

        ResponseEntity<Map<String, Object>> response = controller.login(Map.of(
                "username", "demo-doctor",
                "password", "Demo12345"
        ));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("doctor", response.getBody().get("role"));
    }

    @Test
    void shouldRegisterPatientOnly() {
        AuthController controller = new AuthController(new StubAuthService());
        ResponseEntity<Map<String, Object>> response = controller.register(Map.of(
                "username", "patient-new",
                "password", "Patient12345",
                "name", "新患者"
        ));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("注册成功", response.getBody().get("message"));
    }

    @Test
    void shouldReturnAuthConfig() {
        AuthController controller = new AuthController(new StubAuthService());
        ResponseEntity<Map<String, Object>> response = controller.getConfig();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(true, response.getBody().get("patientRegistrationEnabled"));
    }

    private static class StubAuthService extends AuthService {
        StubAuthService() {
            super(null, null, null);
        }

        @Override
        public Map<String, Object> login(String username, String password, String role) {
            return Map.of(
                    "role", "doctor",
                    "username", username,
                    "displayName", "演示医生",
                    "doctorId", 5L
            );
        }

        @Override
        public Map<String, Object> registerPatient(String username, String password, String name) {
            return Map.of(
                    "message", "注册成功",
                    "username", username,
                    "displayName", name,
                    "patientId", 101L,
                    "profileCompleted", false
            );
        }

        @Override
        public List<Map<String, Object>> listDoctors() {
            return List.of(Map.of("id", 5L, "name", "演示医生"));
        }

        @Override
        public Map<String, Object> getAuthConfig() {
            return Map.of("patientRegistrationEnabled", true);
        }
    }
}
