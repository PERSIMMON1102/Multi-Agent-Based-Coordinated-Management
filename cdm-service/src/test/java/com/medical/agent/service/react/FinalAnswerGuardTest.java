package com.medical.agent.service.react;

import com.medical.agent.model.entity.Patient;
import com.medical.agent.service.chat.ChatPatientContextData;
import com.medical.agent.service.react.model.AgentState;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FinalAnswerGuardTest {

    @Test
    void shouldReplaceUnsupportedPatientAssertionWithoutObservation() {
        FinalAnswerGuard guard = new FinalAnswerGuard();
        AgentState state = new AgentState();
        state.setOwnerRole("patient");

        FinalAnswerGuard.GuardResult result = guard.guard(state, "根据当前患者情况，血压控制不佳，建议立即调整方案。");

        assertTrue(result.flags().contains("guard_replaced_unsupported_patient_assertion"));
        assertTrue(result.reply().contains("缺少足够的患者资料或证据"));
    }

    @Test
    void shouldGuideDoctorToPatientModeWhenGeneralConsultationLacksBoundPatient() {
        FinalAnswerGuard guard = new FinalAnswerGuard();
        AgentState state = new AgentState();
        state.setOwnerRole("doctor");
        state.setPatientId(null);

        FinalAnswerGuard.GuardResult result = guard.guard(state, "根据当前患者情况，血压控制不佳，建议立即调整方案。");

        assertTrue(result.flags().contains("guard_replaced_unsupported_patient_assertion"));
        assertTrue(result.reply().contains("当前为通用咨询模式"));
        assertTrue(result.reply().contains("切换到患者咨询"));
    }

    @Test
    void shouldAnonymizePatientNameForDoctorView() {
        FinalAnswerGuard guard = new FinalAnswerGuard();
        AgentState state = new AgentState();
        state.setOwnerRole("doctor");
        state.setPatientContext(new ChatPatientContextData(
                Patient.builder().id(10L).name("张三").build(),
                List.of(),
                List.of(),
                null,
                null,
                "",
                "patient context"
        ));

        FinalAnswerGuard.GuardResult result = guard.guard(state, "张三目前血压波动较大，建议先复测。");

        assertTrue(result.flags().contains("guard_anonymized_patient_name"));
        assertFalse(result.reply().contains("张三"));
        assertTrue(result.reply().contains("该患者"));
    }
}
