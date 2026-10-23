package com.medical.agent.service.react.agent;

import com.medical.agent.service.AgentMessageAuditService;
import com.medical.agent.service.LlmService;
import com.medical.agent.service.react.ReActPromptBuilder;
import com.medical.agent.service.react.model.AgentState;
import com.medical.agent.service.react.tool.PatientContextTool;
import com.medical.agent.service.react.tool.RiskAssessmentTool;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class RiskAssessmentSubAgent extends AbstractDelegatingSubAgent {

    public RiskAssessmentSubAgent(LlmService llmService,
                                  ReActPromptBuilder promptBuilder,
                                  AgentMessageAuditService auditService,
                                  SubAgentResultContractSupport contractSupport,
                                  PatientContextTool patientContextTool,
                                  RiskAssessmentTool riskAssessmentTool) {
        super(llmService, promptBuilder, auditService, contractSupport, List.of(patientContextTool, riskAssessmentTool));
    }

    @Override
    public String name() {
        return "risk_assessment_agent";
    }

    @Override
    public String description() {
        return "负责独立评估慢病风险、识别高危信号，并决定是否需要先补齐患者上下文后再调用规则评估。";
    }

    @Override
    public List<String> capabilities() {
        return List.of(
                CAPABILITY_PATIENT_CONTEXT,
                CAPABILITY_RISK_ASSESSMENT
        );
    }

    @Override
    protected SubAgentResult executeFallback(AgentState state, String delegatedTask) {
        return runTools(state, delegatedTask, List.of(
                new ToolInvocation("patient_context", Map.of(), "先读取患者上下文，避免脱离病史直接下结论。"),
                new ToolInvocation("risk_assessment", Map.of(), "基于患者上下文完成规则风险评估。")
        ), "风险评估子智能体已完成风险判断并补充了管理依据。");
    }
}
