package com.medical.agent.service.chat;

import com.medical.agent.common.enums.DiseaseType;
import com.medical.agent.common.enums.RiskLevel;
import com.medical.agent.model.entity.Patient;
import com.medical.agent.model.entity.PatientMedicalHistory;
import com.medical.agent.model.entity.RiskAssessment;
import com.medical.agent.service.testsupport.RepositoryProxyFactory;
import org.junit.jupiter.api.Test;
import org.kie.api.runtime.KieContainer;
import org.kie.api.runtime.KieSession;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuleAssessmentServiceTest {

    @Test
    void shouldCarryMedicalHistoryNotesIntoFallbackAssessment() {
        KieSession kieSession = RepositoryProxyFactory.create(KieSession.class, Map.of(
                "insert", args -> null,
                "fireAllRules", args -> 0,
                "dispose", args -> null
        ));
        KieContainer kieContainer = RepositoryProxyFactory.create(KieContainer.class, Map.of(
                "newKieSession", args -> kieSession
        ));
        RuleAssessmentService service = new RuleAssessmentService(kieContainer);

        ChatPatientContextData patientContext = new ChatPatientContextData(
                Patient.builder()
                        .id(1L)
                        .name("患者甲")
                        .gender("男")
                        .birthDate(LocalDate.of(1980, 1, 1))
                        .build(),
                List.of(RiskAssessment.builder()
                        .patientId(1L)
                        .diseaseType(DiseaseType.HYPERTENSION)
                        .riskLevel(RiskLevel.HIGH)
                        .score(88.0)
                        .assessmentDetail("血压持续偏高")
                        .recommendations("规律复查")
                        .build()),
                List.of(),
                null,
                PatientMedicalHistory.builder()
                        .patientId(1L)
                        .pastDiseases("高血压")
                        .drugAllergies("青霉素")
                        .build(),
                "既往患病情况：高血压\n药物过敏：青霉素",
                "上下文"
        );

        List<RuleAssessmentResult> results = service.assess(patientContext, "我现在风险怎么样", null);

        assertEquals(1, results.size());
        assertTrue(results.get(0).detail().contains("病史提示"));
        assertTrue(results.get(0).recommendations().stream().anyMatch(item -> item.contains("药物过敏史")));
    }
}
