package com.medical.agent.service.report.agent;

import com.medical.agent.common.enums.DiseaseType;
import com.medical.agent.common.enums.FollowUpFrequency;
import com.medical.agent.common.enums.FollowUpStatus;
import com.medical.agent.common.enums.RiskLevel;
import com.medical.agent.model.entity.FollowUpPlan;
import com.medical.agent.model.entity.HealthRecord;
import com.medical.agent.model.entity.Patient;
import com.medical.agent.model.entity.RiskAssessment;
import com.medical.agent.service.HealthReportService;
import com.medical.agent.service.LlmService;
import com.medical.agent.service.RagRetrievalService;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HealthReportSupportAndDraftingTest {

    @Test
    void promptSupportShouldBuildReadableContext() {
        String context = HealthReportPromptSupport.buildContext(
                patient(),
                "既往患病情况：高血压\n药物过敏：青霉素",
                List.of(assessment()),
                List.of(plan()),
                record()
        );

        assertTrue(context.contains("患者姓名：周八"));
        assertTrue(context.contains("病史档案"));
        assertTrue(context.contains("高血压 / 高风险"));
        assertTrue(context.contains("随访计划"));
        assertTrue(context.contains("收缩压：148"));
        assertTrue(context.contains("血肌酐：96.0"));
    }

    @Test
    void promptSupportShouldBuildFallbackReportsForTwoTypes() {
        String weekly = HealthReportPromptSupport.buildFallbackReport(
                HealthReportService.TYPE_PATIENT_WEEKLY,
                patient(),
                "既往患病情况：高血压\n药物过敏：青霉素",
                List.of(assessment()),
                List.of(plan()),
                record()
        );
        String doctor = HealthReportPromptSupport.buildFallbackReport(
                HealthReportService.TYPE_DOCTOR_ASSESSMENT,
                patient(),
                "既往患病情况：高血压\n药物过敏：青霉素",
                List.of(assessment()),
                List.of(plan()),
                record()
        );

        assertTrue(weekly.contains("## 本周健康概览"));
        assertTrue(weekly.contains("重要病史"));
        assertTrue(weekly.contains("最近血压"));
        assertTrue(doctor.contains("## 风险分层结论"));
        assertTrue(doctor.contains("## 病史档案摘要"));
        assertTrue(doctor.contains("## 管理建议"));
    }

    @Test
    void draftingAgentShouldFallbackWithoutLlm() {
        ReportGenerationState state = new ReportGenerationState();
        state.setType(HealthReportService.TYPE_DOCTOR_ASSESSMENT);
        state.setPatient(patient());
        state.setAssessments(List.of(assessment()));
        state.setPlans(List.of(plan()));
        state.setLatestRecord(record());
        state.setContext("患者已有风险评估和随访计划");

        new ReportDraftingSpecialistAgent(new LlmService(), new RagRetrievalService(null, null, null)).execute(state);

        assertFalse(state.getContent().isBlank());
        assertTrue(state.getContent().contains("## 风险分层结论"));
        assertEquals(1, state.getAgentSteps().size());
        assertTrue(String.valueOf(state.getAgentSteps().get(0).get("summary")).contains("回退"));
    }

    private Patient patient() {
        return Patient.builder()
                .id(1L)
                .name("周八")
                .gender("男")
                .birthDate(LocalDate.of(1968, 8, 8))
                .phone("13800000000")
                .address("上海市浦东新区")
                .build();
    }

    private RiskAssessment assessment() {
        return RiskAssessment.builder()
                .patientId(1L)
                .diseaseType(DiseaseType.HYPERTENSION)
                .riskLevel(RiskLevel.HIGH)
                .score(82.5)
                .assessmentDetail("血压持续偏高")
                .build();
    }

    private FollowUpPlan plan() {
        return FollowUpPlan.builder()
                .patientId(1L)
                .diseaseType(DiseaseType.HYPERTENSION)
                .frequency(FollowUpFrequency.MONTHLY)
                .status(FollowUpStatus.PENDING)
                .nextFollowDate(LocalDate.of(2026, 4, 18))
                .build();
    }

    private HealthRecord record() {
        return HealthRecord.builder()
                .patientId(1L)
                .recordDate(LocalDate.of(2026, 4, 10))
                .systolicBp(148)
                .diastolicBp(92)
                .fastingGlucose(6.8)
                .weight(72.5)
                .serumCreatinine(96.0)
                .build();
    }
}
