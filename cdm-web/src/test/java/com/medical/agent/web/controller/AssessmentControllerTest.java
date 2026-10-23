package com.medical.agent.web.controller;

import com.medical.agent.common.dto.AssessmentResultDTO;
import com.medical.agent.common.enums.DiseaseType;
import com.medical.agent.common.enums.RiskLevel;
import com.medical.agent.service.AssessmentService;
import com.medical.agent.service.ClinicalAssessmentOrchestrator;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AssessmentControllerTest {

    @Test
    void shouldReturnNoContentWhenLatestAssessmentIsMissing() {
        AssessmentController controller = new AssessmentController(new StubAssessmentService(null), new StubAgentBridgeService());

        ResponseEntity<AssessmentResultDTO> response = controller.getLatest(1L, DiseaseType.HYPERTENSION);

        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
    }

    @Test
    void shouldReturnTriggerAckAndForwardRequest() {
        StubAgentBridgeService bridgeService = new StubAgentBridgeService();
        AssessmentController controller = new AssessmentController(new StubAssessmentService(sampleDto()), bridgeService);

        ResponseEntity<Map<String, String>> response = controller.triggerAssessment(
                8L,
                DiseaseType.DIABETES,
                Map.of("fastingGlucose", 7.4)
        );

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(8L, bridgeService.patientId);
        assertEquals(DiseaseType.DIABETES, bridgeService.diseaseType);
        assertEquals(7.4, bridgeService.healthData.get("fastingGlucose"));
        assertEquals("评估请求已发送至智能体", response.getBody().get("message"));
    }

    private AssessmentResultDTO sampleDto() {
        return AssessmentResultDTO.builder()
                .id(1L)
                .patientId(8L)
                .patientName("测试患者")
                .diseaseType(DiseaseType.DIABETES)
                .riskLevel(RiskLevel.MEDIUM)
                .createdAt(LocalDateTime.now())
                .build();
    }

    private static class StubAssessmentService extends AssessmentService {
        private final AssessmentResultDTO latest;

        StubAssessmentService(AssessmentResultDTO latest) {
            super(null, null);
            this.latest = latest;
        }

        @Override
        public List<AssessmentResultDTO> getByPatientId(Long patientId) {
            return latest == null ? List.of() : List.of(latest);
        }

        @Override
        public List<AssessmentResultDTO> getByPatientAndDisease(Long patientId, DiseaseType diseaseType) {
            return latest == null ? List.of() : List.of(latest);
        }

        @Override
        public AssessmentResultDTO getLatest(Long patientId, DiseaseType diseaseType) {
            return latest;
        }
    }

    private static class StubAgentBridgeService implements ClinicalAssessmentOrchestrator {
        private Long patientId;
        private DiseaseType diseaseType;
        private Map<String, Object> healthData;

        @Override
        public void triggerAssessment(Long patientId, DiseaseType diseaseType, Map<String, Object> healthData) {
            this.patientId = patientId;
            this.diseaseType = diseaseType;
            this.healthData = healthData;
        }

        @Override
        public void triggerAssessment(Long patientId,
                                      DiseaseType diseaseType,
                                      Map<String, Object> healthData,
                                      Map<String, Object> assessmentOptions) {
            triggerAssessment(patientId, diseaseType, healthData);
        }
    }
}
