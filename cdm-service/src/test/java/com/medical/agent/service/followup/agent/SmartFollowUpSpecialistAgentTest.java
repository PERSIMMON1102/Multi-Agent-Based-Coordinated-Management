package com.medical.agent.service.followup.agent;

import com.medical.agent.common.enums.DiseaseType;
import com.medical.agent.common.enums.FollowUpFrequency;
import com.medical.agent.common.enums.FollowUpStatus;
import com.medical.agent.model.entity.FollowUpPlan;
import com.medical.agent.model.entity.Patient;
import com.medical.agent.model.entity.PatientMedicalHistory;
import com.medical.agent.service.LlmService;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SmartFollowUpSpecialistAgentTest {

    @Test
    void contextAgentShouldBuildPatientAndPlanContext() {
        SmartFollowUpGenerationState state = new SmartFollowUpGenerationState();
        state.setPatient(patient("张三"));
        state.setPendingPlans(java.util.List.of(plan(DiseaseType.HYPERTENSION, LocalDate.of(2026, 4, 20))));

        new SmartFollowUpContextSpecialistAgent().execute(state);

        assertTrue(state.getContext().contains("患者姓名：张三"));
        assertTrue(state.getContext().contains("高血压"));
        assertTrue(state.getContext().contains("2026-04-20"));
        assertEquals(1, state.getAgentSteps().size());
        assertEquals("followup_context_agent", state.getAgentSteps().get(0).get("agentName"));
    }

    @Test
    void focusAgentShouldFallbackWhenNoPendingPlanExists() {
        SmartFollowUpGenerationState state = new SmartFollowUpGenerationState();
        state.setPatient(patient("李四"));
        state.setMedicalHistory(PatientMedicalHistory.builder().drugAllergies("青霉素").build());

        new SmartFollowUpFocusSpecialistAgent().execute(state);

        assertEquals("当前以通用服药提醒、症状询问和药物过敏史核对为主。", state.getFocusSummary());
        assertEquals(1, state.getAgentSteps().size());
        assertTrue(String.valueOf(state.getAgentSteps().get(0).get("summary")).contains("回退"));
    }

    @Test
    void draftingAgentShouldFallbackWithoutLlm() {
        SmartFollowUpGenerationState state = new SmartFollowUpGenerationState();
        state.setPatient(patient("王五"));
        state.setContext("患者姓名：王五");
        state.setFocusSummary("高血压需规律复查");
        state.setPendingPlans(java.util.List.of(plan(DiseaseType.DIABETES, LocalDate.of(2026, 4, 30))));
        state.setMedicalHistory(PatientMedicalHistory.builder().drugAllergies("青霉素").build());

        new SmartFollowUpDraftingSpecialistAgent(new LlmService()).execute(state);

        assertTrue(state.getMessage().contains("王五您好"));
        assertTrue(state.getMessage().contains("糖尿病"));
        assertTrue(state.getMessage().contains("2026-04-30"));
        assertTrue(state.getMessage().contains("药物过敏史"));
        assertTrue(state.getMessage().contains("按时服药"));
        assertEquals(1, state.getAgentSteps().size());
        assertTrue(String.valueOf(state.getAgentSteps().get(0).get("summary")).contains("模板"));
    }

    private Patient patient(String name) {
        return Patient.builder()
                .id(1L)
                .name(name)
                .gender("男")
                .build();
    }

    private FollowUpPlan plan(DiseaseType diseaseType, LocalDate nextFollowDate) {
        return FollowUpPlan.builder()
                .patientId(1L)
                .diseaseType(diseaseType)
                .frequency(FollowUpFrequency.MONTHLY)
                .status(FollowUpStatus.PENDING)
                .nextFollowDate(nextFollowDate)
                .planContent("请规律复查并记录指标")
                .build();
    }
}
