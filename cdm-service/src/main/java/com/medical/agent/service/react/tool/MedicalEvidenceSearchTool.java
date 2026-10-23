package com.medical.agent.service.react.tool;

import com.medical.agent.common.dto.RagEvidenceDTO;
import com.medical.agent.common.dto.RagQueryContextDTO;
import com.medical.agent.service.RagRetrievalService;
import com.medical.agent.service.react.model.AgentState;
import com.medical.agent.service.chat.ChatContextService;
import com.medical.agent.service.chat.ChatPatientContextData;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class MedicalEvidenceSearchTool implements AgentTool {

    private static final int EVIDENCE_LIMIT = 5;

    private final ChatContextService chatContextService;
    private final RagRetrievalService ragRetrievalService;

    public MedicalEvidenceSearchTool(ChatContextService chatContextService,
                                     RagRetrievalService ragRetrievalService) {
        this.chatContextService = chatContextService;
        this.ragRetrievalService = ragRetrievalService;
    }

    @Override
    public String name() {
        return "medical_evidence_search";
    }

    @Override
    public String description() {
        return "检索与当前问题和患者背景相关的慢病管理知识证据，返回标题、摘要和来源，用于支撑最终回答。";
    }

    @Override
    public ToolExecutionResult execute(AgentState state, Map<String, Object> input) {
        String focus = state.getUserMessage();
        if (input != null && input.get("focus") != null && !input.get("focus").toString().isBlank()) {
            focus = input.get("focus").toString();
        }

        ChatPatientContextData patientContext = state.getPatientContext();
        if (patientContext == null && state.getPatientId() != null) {
            patientContext = chatContextService.loadPatientContext(state.getPatientId());
            state.setPatientContext(patientContext);
        }

        RagQueryContextDTO context;
        if (patientContext != null) {
            context = ragRetrievalService.buildContext(
                    "AI健康问答",
                    focus,
                    patientContext.patient(),
                    patientContext.assessments(),
                    patientContext.plans(),
                    patientContext.latestRecord(),
                    patientContext.medicalHistorySummary()
            );
        } else {
            context = RagQueryContextDTO.builder()
                    .scenario("AI健康问答")
                    .focus(focus)
                    .queryText("场景：AI健康问答\n用户问题：" + focus)
                    .diseaseKeys(List.of())
                    .topicTags(List.of("general"))
                    .audience("doctor")
                    .build();
        }

        List<RagEvidenceDTO> references = ragRetrievalService.retrieveKnowledge(context, EVIDENCE_LIMIT);
        state.setEvidenceContext(context);
        state.setEvidenceCandidates(references);

        if (references.isEmpty()) {
            return new ToolExecutionResult(name(), false, "没有检索到足够相关的知识证据。");
        }

        String summary = "已检索到 " + references.size() + " 条候选证据，首条为：" +
                (references.get(0).getTitle() == null ? "知识片段" : references.get(0).getTitle());
        return new ToolExecutionResult(name(), true, summary);
    }
}
