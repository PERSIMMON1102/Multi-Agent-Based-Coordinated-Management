package com.medical.agent.service.report.agent;

import com.medical.agent.service.HealthReportService;
import com.medical.agent.service.ReferenceCurationService;
import org.springframework.stereotype.Component;

@Component
public class ReportReferenceSpecialistAgent implements ReportSpecialistAgent {

    private final ReferenceCurationService referenceCurationService;

    public ReportReferenceSpecialistAgent(ReferenceCurationService referenceCurationService) {
        this.referenceCurationService = referenceCurationService;
    }

    @Override
    public String name() {
        return "report_reference_agent";
    }

    @Override
    public int order() {
        return 40;
    }

    @Override
    public void execute(ReportGenerationState state) {
        state.setDisplayReferences(referenceCurationService.curateReportDisplayReferences(
                state.getQueryContext(),
                state.getReportTopicKey(),
                state.getContent(),
                state.getReferenceCandidates(),
                4,
                HealthReportService.TYPE_DOCTOR_ASSESSMENT.equals(state.getType())
        ));
        state.addStep(name(), "已筛出最终展示给报告读者的引用资料。");
    }
}
