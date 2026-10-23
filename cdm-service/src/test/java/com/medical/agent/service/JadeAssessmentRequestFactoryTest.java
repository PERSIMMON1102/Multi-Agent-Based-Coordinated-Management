package com.medical.agent.service;

import com.medical.agent.common.enums.DiseaseType;
import com.medical.agent.model.entity.Patient;
import com.medical.agent.model.entity.PatientMedicalHistory;
import com.medical.agent.model.repository.PatientMedicalHistoryRepository;
import com.medical.agent.model.repository.PatientRepository;
import com.medical.agent.service.testsupport.RepositoryProxyFactory;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class JadeAssessmentRequestFactoryTest {

    @Test
    void shouldBuildAssessmentRequestWithHistoryEnrichment() {
        PatientRepository patientRepository = RepositoryProxyFactory.create(PatientRepository.class, Map.of(
                "findById", args -> Optional.of(Patient.builder().id(5L).name("测试患者").build())
        ));
        PatientMedicalHistoryRepository historyRepository = RepositoryProxyFactory.create(PatientMedicalHistoryRepository.class, Map.of(
                "findByPatientId", args -> Optional.of(PatientMedicalHistory.builder()
                        .patientId(5L)
                        .pastDiseases("高血压、糖尿病")
                        .drugAllergies("青霉素")
                        .familyHistory("父亲高血压")
                        .build())
        ));

        JadeAssessmentRequestFactory factory = new JadeAssessmentRequestFactory(patientRepository, historyRepository);

        JadeAssessmentRequestFactory.PreparedJadeAssessmentRequest prepared = factory.prepare(
                5L,
                DiseaseType.HYPERTENSION,
                Map.of("systolicBp", 155),
                Map.of("_silentPatientNotification", true)
        );

        assertNotNull(prepared.patient());
        assertEquals("测试患者", prepared.patient().getName());
        assertEquals(DiseaseType.HYPERTENSION, prepared.request().getDiseaseType());
        assertEquals(155, prepared.request().getData().get("systolicBp"));
        assertEquals(true, prepared.request().getData().get("_silentPatientNotification"));
        assertEquals(true, prepared.request().getData().get("_disableJadeSecondaryAgents"));
        assertEquals("高血压、糖尿病", prepared.request().getData().get("pastDiseases"));
        assertEquals("青霉素", prepared.request().getData().get("drugAllergies"));
        assertEquals(List.of("HYPERTENSION", "DIABETES"), prepared.request().getData().get("historyDiseaseTypes"));
    }
}
