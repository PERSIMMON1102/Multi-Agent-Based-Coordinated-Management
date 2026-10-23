package com.medical.agent.agent;

import com.medical.agent.common.message.AgentMessage;
import com.medical.agent.common.enums.DiseaseType;
import com.medical.agent.common.enums.RiskLevel;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ResourceCoordinatorAgentTest {

    @Test
    void shouldRecommendExpectedSpecialtiesAndExaminationsForCriticalChd() throws Exception {
        ResourceCoordinatorAgent agent = new ResourceCoordinatorAgent();

        @SuppressWarnings("unchecked")
        List<String> specialties = (List<String>) invoke(
                agent,
                "recommendSpecialties",
                new Class[]{DiseaseType.class},
                DiseaseType.CHD
        );
        @SuppressWarnings("unchecked")
        List<String> examinations = (List<String>) invoke(
                agent,
                "recommendExaminations",
                new Class[]{DiseaseType.class, RiskLevel.class},
                DiseaseType.CHD,
                RiskLevel.CRITICAL
        );

        assertTrue(specialties.contains("心内科"));
        assertTrue(specialties.contains("心血管介入科"));
        assertTrue(examinations.contains("心电图"));
        assertTrue(examinations.contains("心肌酶谱"));
        assertTrue(examinations.contains("冠脉造影"));
    }

    @Test
    void shouldBuildUrgentReasonForCriticalPatient() throws Exception {
        ResourceCoordinatorAgent agent = new ResourceCoordinatorAgent();
        AgentMessage message = AgentMessage.builder()
                .patientId(1L)
                .patientName("李患者")
                .diseaseType(DiseaseType.STROKE)
                .riskLevel(RiskLevel.CRITICAL)
                .detail("突发肢体无力")
                .build();

        String reason = (String) invoke(
                agent,
                "generateReason",
                new Class[]{AgentMessage.class},
                message
        );

        assertTrue(reason.contains("李患者"));
        assertTrue(reason.contains("脑卒中"));
        assertTrue(reason.contains("危急"));
        assertTrue(reason.contains("紧急"));
        assertTrue(reason.contains("突发肢体无力"));
    }

    private Object invoke(Object target, String methodName, Class<?>[] parameterTypes, Object... args) throws Exception {
        Method method = target.getClass().getDeclaredMethod(methodName, parameterTypes);
        method.setAccessible(true);
        return method.invoke(target, args);
    }
}
