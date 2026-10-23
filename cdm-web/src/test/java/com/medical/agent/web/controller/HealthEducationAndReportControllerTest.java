package com.medical.agent.web.controller;

import com.medical.agent.service.HealthEducationService;
import com.medical.agent.service.HealthReportService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HealthEducationAndReportControllerTest {

    @Test
    void shouldReturnEducationGenerationAndHistory() {
        HealthEducationController controller = new HealthEducationController(new StubHealthEducationService());

        assertEquals("饮食建议", controller.generate(5L).getBody().get("diet"));
        assertEquals(1, controller.getByPatient(5L, null).getBody().size());
        assertEquals(3, controller.getByPatient(5L, 3).getBody().size());
    }

    @Test
    void shouldReturnReportGenerationAndList() {
        HealthReportController controller = new HealthReportController(new StubHealthReportService());

        assertEquals("周报", controller.generatePatientWeekly(3L).getBody().get("title"));
        assertEquals("评估", controller.generateDoctorAssessment(3L).getBody().get("title"));
        assertEquals(1, controller.getByPatient(3L, "doctor_assessment").getBody().size());
    }

    private static class StubHealthEducationService extends HealthEducationService {
        StubHealthEducationService() {
            super(null);
        }

        @Override
        public Map<String, Object> generateEducation(Long patientId) {
            return Map.of("patientId", patientId, "diet", "饮食建议", "agentSteps", List.of(Map.of("agentName", "diet_education_agent")));
        }

        @Override
        public List<Map<String, Object>> getByPatient(Long patientId) {
            return List.of(Map.of("patientId", patientId, "diet", "历史饮食建议"));
        }

        @Override
        public List<Map<String, Object>> getByPatient(Long patientId, Integer limit) {
            if (limit == null) {
                return getByPatient(patientId);
            }
            return java.util.stream.IntStream.range(0, limit)
                    .mapToObj(index -> Map.<String, Object>of("patientId", patientId, "diet", "历史饮食建议" + index))
                    .toList();
        }
    }

    private static class StubHealthReportService extends HealthReportService {
        StubHealthReportService() {
            super(null, null, null, null, null, null, null, null, null, null);
        }

        @Override
        public Map<String, Object> generatePatientWeekly(Long patientId) {
            return Map.of("patientId", patientId, "title", "周报", "agentSteps", List.of());
        }

        @Override
        public Map<String, Object> generateDoctorAssessment(Long patientId) {
            return Map.of("patientId", patientId, "title", "评估", "agentSteps", List.of());
        }

        @Override
        public List<Map<String, Object>> listByPatient(Long patientId, String type) {
            return List.of(Map.of("patientId", patientId, "type", type));
        }
    }
}
