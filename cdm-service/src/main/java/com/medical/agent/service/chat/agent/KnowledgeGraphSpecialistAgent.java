package com.medical.agent.service.chat.agent;

import com.medical.agent.service.react.model.AgentState;
import com.medical.agent.service.react.tool.KnowledgeGraphSupportTool;
import com.medical.agent.service.react.tool.ToolExecutionResult;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class KnowledgeGraphSpecialistAgent implements ChatSpecialistAgent {

    private final KnowledgeGraphSupportTool knowledgeGraphSupportTool;

    public KnowledgeGraphSpecialistAgent(KnowledgeGraphSupportTool knowledgeGraphSupportTool) {
        this.knowledgeGraphSupportTool = knowledgeGraphSupportTool;
    }

    @Override
    public String name() {
        return "knowledge_graph_agent";
    }

    @Override
    public int order() {
        return 40;
    }

    @Override
    public boolean shouldEngage(AgentState state) {
        return state != null && "doctor".equalsIgnoreCase(state.getOwnerRole());
    }

    @Override
    public ChatSpecialistExecutionResult execute(AgentState state) {
        Map<String, Object> input = Map.of("focus", state.getUserMessage());
        ToolExecutionResult result = knowledgeGraphSupportTool.execute(state, input);
        return new ChatSpecialistExecutionResult(
                result.success(),
                "由知识图谱智能体补充病种、主题和文档之间的支撑链路。",
                input,
                result.summary()
        );
    }
}
