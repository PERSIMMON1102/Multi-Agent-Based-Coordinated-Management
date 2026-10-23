package com.medical.agent.service.report.agent;

import com.medical.agent.service.HealthReportService;
import com.medical.agent.service.RagRetrievalService;
import org.springframework.stereotype.Component;

@Component
public class ReportContextSpecialistAgent implements ReportSpecialistAgent {

    private final RagRetrievalService ragRetrievalService;

    public ReportContextSpecialistAgent(RagRetrievalService ragRetrievalService) {
        this.ragRetrievalService = ragRetrievalService;
    }

    @Override
    public String name() {
        return "report_context_agent";
    }

    @Override
    public int order() {
        return 10;
    }

    @Override
    public void execute(ReportGenerationState state) {
        boolean patientWeekly = HealthReportService.TYPE_PATIENT_WEEKLY.equals(state.getType());
        state.setTitle(patientWeekly ? "每周健康报告" : "专业风险评估");
        state.setContext(HealthReportPromptSupport.buildContext(
                state.getPatient(),
                state.getMedicalHistorySummary(),
                state.getAssessments(),
                state.getPlans(),
                state.getLatestRecord()
        ));
        state.setQueryContext(ragRetrievalService.buildContext(
                patientWeekly ? "每周健康报告" : "专业风险评估",
                patientWeekly ? "患者周报" : "医生评估",
                state.getPatient(),
                state.getAssessments(),
                state.getPlans(),
                state.getLatestRecord(),
                state.getMedicalHistorySummary()
        ));
        state.setReportTopicKey(patientWeekly ? "patient_report" : "doctor_report");
        state.addStep(name(), state.getMedicalHistorySummary() == null || state.getMedicalHistorySummary().isBlank()
                ? "已整理患者风险评估、随访计划和最新健康记录。"
                : "已整理患者病史档案、风险评估、随访计划和最新健康记录。");
    }
}
