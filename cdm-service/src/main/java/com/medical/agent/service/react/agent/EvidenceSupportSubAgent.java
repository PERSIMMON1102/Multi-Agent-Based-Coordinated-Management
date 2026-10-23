package com.medical.agent.service.react.agent;

import com.medical.agent.service.AgentMessageAuditService;
import com.medical.agent.service.LlmService;
import com.medical.agent.service.react.ReActPromptBuilder;
import com.medical.agent.service.react.model.AgentState;
import com.medical.agent.service.react.tool.KnowledgeGraphSupportTool;
import com.medical.agent.service.react.tool.MedicalEvidenceSearchTool;
import com.medical.agent.service.react.tool.PatientContextTool;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class EvidenceSupportSubAgent extends AbstractDelegatingSubAgent {

    public EvidenceSupportSubAgent(LlmService llmService,
                                   ReActPromptBuilder promptBuilder,
                                   AgentMessageAuditService auditService,
                                   SubAgentResultContractSupport contractSupport,
                                   PatientContextTool patientContextTool,
                                   MedicalEvidenceSearchTool medicalEvidenceSearchTool,
                                   KnowledgeGraphSupportTool knowledgeGraphSupportTool) {
        super(llmService, promptBuilder, auditService, contractSupport, List.of(patientContextTool, medicalEvidenceSearchTool, knowledgeGraphSupportTool));
    }

    @Override
    public String name() {
        return "evidence_support_agent";
    }

    @Override
    public String description() {
        return "负责独立补充指南证据、知识图谱支撑和慢病管理依据，避免最终回答脱离证据。";
    }

    @Override
    public List<String> capabilities() {
        return List.of(
                CAPABILITY_PATIENT_CONTEXT,
                CAPABILITY_MEDICAL_EVIDENCE,
                CAPABILITY_KNOWLEDGE_GRAPH
        );
    }

    @Override
    protected SubAgentResult executeFallback(AgentState state, String delegatedTask) {
        List<ToolInvocation> plan = new ArrayList<>();
        if (state.getPatientId() != null) {
            plan.add(new ToolInvocation("patient_context", Map.of(), "先读取患者上下文，保证证据检索围绕真实患者场景展开。"));
        }
        plan.add(new ToolInvocation("medical_evidence_search", mapOf("focus", delegatedTask), "检索与当前问题直接相关的慢病管理证据。"));
        plan.add(new ToolInvocation("knowledge_graph_support", mapOf("focus", delegatedTask), "补充知识图谱支撑链路，增强证据关联性。"));
        return runTools(state, delegatedTask, plan, "证据支撑子智能体已补充文献证据与知识图谱依据。");
    }
}
