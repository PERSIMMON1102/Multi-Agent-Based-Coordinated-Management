package com.medical.agent.web.security;

import com.medical.agent.common.dto.PatientDTO;
import com.medical.agent.model.entity.Patient;
import com.medical.agent.model.repository.DoctorRepository;
import com.medical.agent.model.repository.FollowUpPlanRepository;
import com.medical.agent.model.repository.NotificationRepository;
import com.medical.agent.model.repository.PatientMedicalHistoryRepository;
import com.medical.agent.model.repository.PatientRepository;
import com.medical.agent.model.repository.VideoWatchSessionRepository;
import com.medical.agent.service.PatientService;
import com.medical.agent.service.cache.AiChatCacheInvalidationService;
import com.medical.agent.web.config.GlobalExceptionHandler;
import com.medical.agent.web.config.SecurityConfig;
import com.medical.agent.web.controller.PatientController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.data.mongo.MongoDataAutoConfiguration;
import org.springframework.boot.autoconfigure.data.mongo.MongoRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.Optional;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = SecurityIntegrationTest.TestApplication.class)
@AutoConfigureMockMvc
@TestPropertySource(properties = "auth.jwt.secret=MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTIzNDU2Nzg5MDE=")
class SecurityIntegrationTest {

    @SpringBootConfiguration
    @EnableAutoConfiguration(exclude = {
            DataSourceAutoConfiguration.class,
            HibernateJpaAutoConfiguration.class,
            JpaRepositoriesAutoConfiguration.class,
            MongoDataAutoConfiguration.class,
            MongoRepositoriesAutoConfiguration.class,
            UserDetailsServiceAutoConfiguration.class
    })
    @Import({
            PatientController.class,
            GlobalExceptionHandler.class,
            SecurityConfig.class,
            JwtAuthenticationFilter.class,
            JwtTokenService.class,
            CurrentUserService.class,
            AccessGuard.class
    })
    static class TestApplication {
        @Bean
        AdminOnlyController adminOnlyController() {
            return new AdminOnlyController();
        }
    }

    @RestController
    static class AdminOnlyController {
        @GetMapping("/test/admin-only")
        @PreAuthorize("hasRole('ADMIN')")
        public Map<String, Object> adminOnly() {
            return Map.of("ok", true);
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenService jwtTokenService;

    @MockBean
    private PatientService patientService;

    @MockBean
    private PatientRepository patientRepository;

    @MockBean
    private PatientMedicalHistoryRepository patientMedicalHistoryRepository;

    @MockBean
    private DoctorRepository doctorRepository;

    @MockBean
    private NotificationRepository notificationRepository;

    @MockBean
    private FollowUpPlanRepository followUpPlanRepository;

    @MockBean
    private VideoWatchSessionRepository videoWatchSessionRepository;

    @MockBean
    private AiChatCacheInvalidationService aiChatCacheInvalidationService;

    @Test
    void shouldRejectAnonymousRequestForProtectedEndpoint() throws Exception {
        mockMvc.perform(get("/api/patients/10"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldAllowPatientToAccessOwnRecordWithJwt() throws Exception {
        when(patientService.getById(10L)).thenReturn(PatientDTO.builder().id(10L).name("患者甲").build());

        String token = jwtTokenService.generateToken(
                new AuthenticatedUser(10L, "patient_10", "patient"),
                null
        );

        mockMvc.perform(get("/api/patients/10")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(10L))
                .andExpect(jsonPath("$.name").value("患者甲"));
    }

    @Test
    void shouldRejectPatientAccessingAnotherPatientsRecord() throws Exception {
        String token = jwtTokenService.generateToken(
                new AuthenticatedUser(10L, "patient_10", "patient"),
                null
        );

        mockMvc.perform(get("/api/patients/11")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldAllowDoctorToAccessAssignedPatientOnly() throws Exception {
        when(patientRepository.findById(10L)).thenReturn(Optional.of(
                Patient.builder().id(10L).assignedDoctorId(5L).build()
        ));
        when(patientRepository.findById(11L)).thenReturn(Optional.of(
                Patient.builder().id(11L).assignedDoctorId(9L).build()
        ));
        when(patientService.getById(10L)).thenReturn(PatientDTO.builder().id(10L).name("患者乙").build());

        String token = jwtTokenService.generateToken(
                new AuthenticatedUser(5L, "doctor_5", "doctor"),
                null
        );

        mockMvc.perform(get("/api/patients/10")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(10L));

        mockMvc.perform(get("/api/patients/11")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldReturnForbiddenInsteadOfInternalServerErrorForAdminOnlyEndpoint() throws Exception {
        String token = jwtTokenService.generateToken(
                new AuthenticatedUser(5L, "doctor_5", "doctor"),
                null
        );

        mockMvc.perform(get("/test/admin-only")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.message").value("无权访问该资源"));
    }
}
