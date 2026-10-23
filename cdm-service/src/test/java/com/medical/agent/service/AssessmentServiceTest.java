package com.medical.agent.service;

import com.medical.agent.common.dto.AssessmentResultDTO;
import com.medical.agent.common.enums.DiseaseType;
import com.medical.agent.common.enums.RiskLevel;
import com.medical.agent.model.entity.Patient;
import com.medical.agent.model.entity.RiskAssessment;
import com.medical.agent.model.repository.PatientRepository;
import com.medical.agent.model.repository.RiskAssessmentRepository;
import com.medical.agent.service.testsupport.RepositoryProxyFactory;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AssessmentServiceTest {

    @Test
    void shouldExposeHistoryAlertsFromAssessmentDetail() {
        RiskAssessmentRepository assessmentRepository = RepositoryProxyFactory.create(RiskAssessmentRepository.class, Map.of(
                "findByPatientIdOrderByCreatedAtDesc", args -> List.of(assessment(
                        "基础评估完成\n病史提示：既往高血压病史；药物过敏史：青霉素",
                        "规律复查; 已记录药物过敏史，调整或新增药物前请先核对：青霉素"
                ))
        ));
        PatientRepository patientRepository = RepositoryProxyFactory.create(PatientRepository.class, Map.of(
                "findById", args -> Optional.of(Patient.builder().id(1L).name("患者甲").build())
        ));

        AssessmentService service = new AssessmentService(assessmentRepository, patientRepository);

        AssessmentResultDTO result = service.getByPatientId(1L).get(0);

        assertEquals(List.of("既往高血压病史", "药物过敏史：青霉素"), result.getHistoryAlerts());
        assertEquals("jade", result.getAssessmentProvider());
        assertEquals("assessment-msg-1", result.getOrchestrationSessionId());
        assertEquals("AssessmentAgent", result.getOrchestrationAssignedAgent());
    }

    @Test
    void shouldFallbackToRecommendationHistoryAlertsWhenDetailHasNoHistoryLine() {
        RiskAssessmentRepository assessmentRepository = RepositoryProxyFactory.create(RiskAssessmentRepository.class, Map.of(
                "findFirstByPatientIdAndDiseaseTypeOrderByCreatedAtDesc", args -> Optional.of(assessment(
                        "本次血压控制不稳定",
                        "结合既往高血压病史，建议继续按长期管理方案复核指标与治疗依从性。; 规律复查"
                ))
        ));
        PatientRepository patientRepository = RepositoryProxyFactory.create(PatientRepository.class, Map.of(
                "findById", args -> Optional.of(Patient.builder().id(1L).name("患者甲").build())
        ));

        AssessmentService service = new AssessmentService(assessmentRepository, patientRepository);

        AssessmentResultDTO result = service.getLatest(1L, DiseaseType.HYPERTENSION);

        assertEquals(1, result.getHistoryAlerts().size());
        assertTrue(result.getHistoryAlerts().get(0).contains("既往高血压病史"));
    }

    private RiskAssessment assessment(String detail, String recommendations) {
        return RiskAssessment.builder()
                .id(10L)
                .patientId(1L)
                .diseaseType(DiseaseType.HYPERTENSION)
                .riskLevel(RiskLevel.HIGH)
                .score(88.0)
                .assessmentDetail(detail)
                .recommendations(recommendations)
                .agentId("AssessmentAgent")
                .assessmentProvider("jade")
                .orchestrationSessionId("assessment-msg-1")
                .orchestrationAssignedAgent("AssessmentAgent")
                .build();
    }
}
