package com.medical.agent.agent;

import com.medical.agent.common.message.AgentMessage;
import com.medical.agent.common.enums.DiseaseType;
import com.medical.agent.common.enums.FollowUpFrequency;
import com.medical.agent.common.enums.RiskLevel;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FollowUpAgentTest {

    @Test
    void shouldMapRiskLevelsToFollowUpFrequency() throws Exception {
        FollowUpAgent agent = new FollowUpAgent();

        assertEquals(FollowUpFrequency.QUARTERLY, invoke(agent, "determineFrequency", RiskLevel.LOW));
        assertEquals(FollowUpFrequency.MONTHLY, invoke(agent, "determineFrequency", RiskLevel.MEDIUM));
        assertEquals(FollowUpFrequency.WEEKLY, invoke(agent, "determineFrequency", RiskLevel.HIGH));
        assertEquals(FollowUpFrequency.IMMEDIATE, invoke(agent, "determineFrequency", RiskLevel.CRITICAL));
    }

    @Test
    void shouldIncludeHighRiskCheckItemsForDiabetes() throws Exception {
        FollowUpAgent agent = new FollowUpAgent();

        @SuppressWarnings("unchecked")
        List<String> checkItems = (List<String>) invoke(
                agent,
                "generateCheckItems",
                new Class[]{DiseaseType.class, RiskLevel.class},
                DiseaseType.DIABETES,
                RiskLevel.HIGH
        );

        assertTrue(checkItems.contains("空腹血糖检测"));
        assertTrue(checkItems.contains("HbA1c检测"));
        assertTrue(checkItems.contains("尿微量白蛋白检测"));
        assertTrue(checkItems.contains("眼底检查"));
    }

    @Test
    void shouldBuildReadablePlanContent() throws Exception {
        FollowUpAgent agent = new FollowUpAgent();
        AgentMessage message = AgentMessage.builder()
                .patientId(1L)
                .patientName("王医生")
                .diseaseType(DiseaseType.HYPERTENSION)
                .riskLevel(RiskLevel.HIGH)
                .detail("近期血压持续偏高")
                .build();

        String planContent = (String) invoke(
                agent,
                "generatePlanContent",
                new Class[]{AgentMessage.class},
                message
        );

        assertTrue(planContent.contains("高血压"));
        assertTrue(planContent.contains("高风险"));
        assertTrue(planContent.contains("近期血压持续偏高"));
        assertTrue(planContent.contains(FollowUpFrequency.WEEKLY.getDisplayName()));
    }

    private Object invoke(Object target, String methodName, Object arg) throws Exception {
        return invoke(target, methodName, new Class[]{arg.getClass()}, arg);
    }

    private Object invoke(Object target, String methodName, Class<?>[] parameterTypes, Object... args) throws Exception {
        Method method = target.getClass().getDeclaredMethod(methodName, parameterTypes);
        method.setAccessible(true);
        return method.invoke(target, args);
    }
}
