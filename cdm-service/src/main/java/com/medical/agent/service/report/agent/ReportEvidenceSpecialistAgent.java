package com.medical.agent.service.report.agent;

import com.medical.agent.common.dto.RagEvidenceDTO;
import com.medical.agent.service.ReferenceCurationService;
import com.medical.agent.service.RagRetrievalService;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ReportEvidenceSpecialistAgent implements ReportSpecialistAgent {

    private final RagRetrievalService ragRetrievalService;
    private final ReferenceCurationService referenceCurationService;

    public ReportEvidenceSpecialistAgent(RagRetrievalService ragRetrievalService,
                                         ReferenceCurationService referenceCurationService) {
        this.ragRetrievalService = ragRetrievalService;
        this.referenceCurationService = referenceCurationService;
    }

    @Override
    public String name() {
        return "report_evidence_agent";
    }

    @Override
    public int order() {
        return 20;
    }

    @Override
    public void execute(ReportGenerationState state) {
        List<RagEvidenceDTO> referenceCandidates = ragRetrievalService.retrieveKnowledge(state.getQueryContext(), 8);
        state.setReferenceCandidates(referenceCandidates);
        state.setPromptReferences(referenceCurationService.curateReportPromptReferences(
                state.getQueryContext(),
                state.getReportTopicKey(),
                referenceCandidates,
                5
        ));
        state.addStep(name(), "已检索报告候选证据并准备成稿引用。");
    }
}
