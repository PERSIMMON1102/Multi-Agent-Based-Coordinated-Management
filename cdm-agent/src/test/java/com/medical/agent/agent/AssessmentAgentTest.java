package com.medical.agent.agent;

import com.medical.agent.common.fact.PatientHealthFact;
import com.medical.agent.common.message.AgentMessage;
import com.medical.agent.common.enums.DiseaseType;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AssessmentAgentTest {

    @Test
    void shouldRecognizeDiseaseTypesFromAliasFields() throws Exception {
        AssessmentAgent agent = new AssessmentAgent();

        @SuppressWarnings("unchecked")
        List<DiseaseType> diseaseTypes = (List<DiseaseType>) invoke(
                agent,
                "determineDiseaseTypes",
                new Class[]{Map.class, DiseaseType.class},
                Map.of(
                        "serumCreatinine", 98.6,
                        "triglycerides", 2.3,
                        "fastingGlucose", 7.1
                ),
                null
        );

        assertTrue(diseaseTypes.contains(DiseaseType.CKD));
        assertTrue(diseaseTypes.contains(DiseaseType.CHD));
        assertTrue(diseaseTypes.contains(DiseaseType.DIABETES));
    }

    @Test
    void shouldPreferSpecifiedDiseaseTypeWhenProvided() throws Exception {
        AssessmentAgent agent = new AssessmentAgent();

        @SuppressWarnings("unchecked")
        List<DiseaseType> diseaseTypes = (List<DiseaseType>) invoke(
                agent,
                "determineDiseaseTypes",
                new Class[]{Map.class, DiseaseType.class},
                Map.of("systolicBp", 170, "hba1c", 8.8),
                DiseaseType.STROKE
        );

        assertEquals(List.of(DiseaseType.STROKE), diseaseTypes);
    }

    @Test
    void shouldBuildFactFromAliasAndStringMetrics() throws Exception {
        AssessmentAgent agent = new AssessmentAgent();

        PatientHealthFact fact = (PatientHealthFact) invoke(
                agent,
                "buildFact",
                new Class[]{Long.class, String.class, Map.class},
                1L,
                "测试患者",
                Map.of(
                        "age", "67",
                        "serumCreatinine", "101.5",
                        "triglycerides", "1.8",
                        "wbc", "7.2",
                        "urineProtein", "1+"
                )
        );

        assertEquals(67, fact.getAge());
        assertEquals(101.5, fact.getCreatinine());
        assertEquals(1.8, fact.getTriglyceride());
        assertEquals(7.2, fact.getWbc());
        assertEquals("1+", fact.getUrineProtein());
    }

    @Test
    void shouldRecognizeDiseaseTypesFromMedicalHistoryText() throws Exception {
        AssessmentAgent agent = new AssessmentAgent();

        @SuppressWarnings("unchecked")
        List<DiseaseType> diseaseTypes = (List<DiseaseType>) invoke(
                agent,
                "determineDiseaseTypes",
                new Class[]{Map.class, DiseaseType.class},
                Map.of("pastDiseases", "既往有高血压和糖尿病病史"),
                null
        );

        assertTrue(diseaseTypes.contains(DiseaseType.HYPERTENSION));
        assertTrue(diseaseTypes.contains(DiseaseType.DIABETES));
    }

    @Test
    void shouldAppendAllergyRecommendationsFromMedicalHistory() throws Exception {
        AssessmentAgent agent = new AssessmentAgent();
        PatientHealthFact fact = PatientHealthFact.builder()
                .patientId(1L)
                .patientName("测试患者")
                .assessmentDetail("基础评估完成")
                .recommendations(new java.util.ArrayList<>())
                .build();

        invoke(
                agent,
                "applyMedicalHistoryContext",
                new Class[]{DiseaseType.class, PatientHealthFact.class, Map.class},
                DiseaseType.HYPERTENSION,
                fact,
                Map.of(
                        "pastDiseases", "高血压",
                        "drugAllergies", "青霉素",
                        "foodAllergies", "海鲜"
                )
        );

        assertTrue(fact.getAssessmentDetail().contains("病史提示"));
        assertTrue(fact.getRecommendations().stream().anyMatch(item -> item.contains("药物过敏史")));
        assertTrue(fact.getRecommendations().stream().anyMatch(item -> item.contains("食物过敏史")));
    }

    @Test
    void shouldTreatBooleanAndStringMetaFlagsAsEnabled() throws Exception {
        AssessmentAgent agent = new AssessmentAgent();

        boolean boolFlag = (boolean) invoke(
                agent,
                "isMetaFlagEnabled",
                new Class[]{Map.class, String.class},
                Map.of("_silentPatientNotification", true),
                "_silentPatientNotification"
        );
        boolean stringFlag = (boolean) invoke(
                agent,
                "isMetaFlagEnabled",
                new Class[]{Map.class, String.class},
                Map.of("_suppressFollowUpCreation", "true"),
                "_suppressFollowUpCreation"
        );

        assertTrue(boolFlag);
        assertTrue(stringFlag);
    }

    private Object invoke(Object target, String methodName, Class<?>[] parameterTypes, Object... args) throws Exception {
        Method method = target.getClass().getDeclaredMethod(methodName, parameterTypes);
        method.setAccessible(true);
        return method.invoke(target, args);
    }
}
