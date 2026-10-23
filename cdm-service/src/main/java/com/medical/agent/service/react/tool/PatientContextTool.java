package com.medical.agent.service.react.tool;

import com.medical.agent.service.chat.ChatContextService;
import com.medical.agent.service.chat.ChatPatientContextData;
import com.medical.agent.service.react.model.AgentState;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class PatientContextTool implements AgentTool {

    private final ChatContextService chatContextService;

    public PatientContextTool(ChatContextService chatContextService) {
        this.chatContextService = chatContextService;
    }

    @Override
    public String name() {
        return "patient_context";
    }

    @Override
    public String description() {
        return "读取绑定患者的基本信息、病史档案、近期评估、随访计划和最新健康记录。适用于解读最近情况、结合患者背景作答。";
    }

    @Override
    public ToolExecutionResult execute(AgentState state, Map<String, Object> input) {
        if (state.getPatientId() == null) {
            return new ToolExecutionResult(name(), false, "当前未绑定患者，无法读取患者上下文。");
        }
        ChatPatientContextData patientContext = chatContextService.loadPatientContext(state.getPatientId());
        state.setPatientContext(patientContext);
        if (patientContext == null || patientContext.patient() == null) {
            return new ToolExecutionResult(name(), false, "未找到患者上下文。");
        }

        StringBuilder summary = new StringBuilder();
        summary.append("已读取该患者的上下文。");
        if (patientContext.assessments() != null && !patientContext.assessments().isEmpty()) {
            summary.append(" 最近评估数量=").append(Math.min(3, patientContext.assessments().size())).append("。");
        }
        if (patientContext.latestRecord() != null) {
            summary.append(" 已获取最新健康记录。");
        }
        if (patientContext.medicalHistorySummary() != null && !patientContext.medicalHistorySummary().isBlank()) {
            summary.append(" 已获取病史档案摘要。");
        }
        return new ToolExecutionResult(name(), true, summary.toString());
    }
}
