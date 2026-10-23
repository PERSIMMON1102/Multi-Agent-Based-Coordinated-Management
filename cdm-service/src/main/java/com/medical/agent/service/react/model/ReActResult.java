package com.medical.agent.service.react.model;

import com.medical.agent.common.dto.RagEvidenceDTO;
import com.medical.agent.common.dto.RagQueryContextDTO;
import com.medical.agent.service.react.orchestration.OrchestrationTrace;

import java.util.List;
import java.util.Set;

public record ReActResult(String reply,
                          String answerPlan,
                          String thoughtSummary,
                          String goal,
                          String progressSummary,
                          String nextAction,
                          List<String> blockers,
                          RagQueryContextDTO evidenceContext,
                          List<RagEvidenceDTO> evidenceCandidates,
                          String knowledgeGraphSummary,
                          List<java.util.Map<String, Object>> knowledgeGraphItems,
                          List<String> usedTools,
                          List<AgentStep> steps,
                          List<String> degradationNotes,
                          Set<String> safetyFlags,
                          OrchestrationTrace orchestrationTrace) {
}
