package com.medical.agent.service.react.agent;

import com.medical.agent.service.AgentMessageAuditService;
import com.medical.agent.service.LlmService;
import com.medical.agent.service.react.ReActPromptBuilder;
import com.medical.agent.service.react.model.AgentState;
import com.medical.agent.service.react.tool.MedplumPatientInsightTool;
import com.medical.agent.service.react.tool.PatientContextTool;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public class PatientProfileSubAgent extends AbstractDelegatingSubAgent {

    public PatientProfileSubAgent(LlmService llmService,
                                  ReActPromptBuilder promptBuilder,
                                  AgentMessageAuditService auditService,
                                  SubAgentResultContractSupport contractSupport,
                                  PatientContextTool patientContextTool,
                                  MedplumPatientInsightTool medplumPatientInsightTool) {
        super(llmService, promptBuilder, auditService, contractSupport, List.of(patientContextTool, medplumPatientInsightTool));
    }

    @Override
    public String name() {
        return "patient_profile_agent";
    }

    @Override
    public String description() {
        return "负责独立理解患者基础信息、病史、最近记录和云端档案差异，必要时主动补充本地上下文与 Medplum 视图。";
    }

    @Override
    public List<String> capabilities() {
        return List.of(
                CAPABILITY_PATIENT_CONTEXT,
                CAPABILITY_PATIENT_PROFILE,
                CAPABILITY_MEDPLUM_INSIGHT
        );
    }

    @Override
    protected SubAgentResult executeFallback(AgentState state, String delegatedTask) {
        List<ToolInvocation> plan = new ArrayList<>();
        plan.add(new ToolInvocation("patient_context", Map.of(), "先读取本地患者资料，建立患者画像。"));
        if (requiresExternalView(delegatedTask)) {
            plan.add(new ToolInvocation("medplum_patient_insight", Map.of("limit", 3), "任务涉及云端或外部视图，补充 Medplum 患者信息。"));
        }
        return runTools(state, delegatedTask, plan, "患者画像子智能体已整理患者基础资料与档案视图。");
    }

    private boolean requiresExternalView(String delegatedTask) {
        if (delegatedTask == null || delegatedTask.isBlank()) {
            return false;
        }
        String normalized = delegatedTask.toLowerCase(Locale.ROOT);
        return normalized.contains("medplum")
                || normalized.contains("mcp")
                || normalized.contains("云端")
                || normalized.contains("外部");
    }
}
