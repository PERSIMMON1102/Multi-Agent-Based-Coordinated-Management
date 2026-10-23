package com.medical.agent.service.react.tool;

import com.medical.agent.service.KnowledgeGraphService;
import com.medical.agent.service.chat.ChatContextService;
import com.medical.agent.service.chat.ChatPatientContextData;
import com.medical.agent.service.react.model.AgentState;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class KnowledgeGraphSupportTool implements AgentTool {

    private static final int GRAPH_DOC_LIMIT = 3;

    private final ChatContextService chatContextService;
    private final KnowledgeGraphService knowledgeGraphService;

    public KnowledgeGraphSupportTool(ChatContextService chatContextService,
                                     KnowledgeGraphService knowledgeGraphService) {
        this.chatContextService = chatContextService;
        this.knowledgeGraphService = knowledgeGraphService;
    }

    @Override
    public String name() {
        return "knowledge_graph_support";
    }

    @Override
    public String description() {
        return "读取与当前咨询主题相关的知识图谱支撑链路；有患者上下文时优先结合患者病种和记录，无患者时回退到通用慢病主题支撑。";
    }

    @Override
    public ToolExecutionResult execute(AgentState state, Map<String, Object> input) {
        ChatPatientContextData patientContext = state.getPatientContext();
        if (patientContext == null && state.getPatientId() != null) {
            patientContext = chatContextService.loadPatientContext(state.getPatientId());
            state.setPatientContext(patientContext);
        }

        String focus = state.getUserMessage();
        if (input != null && input.get("focus") != null && !input.get("focus").toString().isBlank()) {
            focus = input.get("focus").toString();
        }

        KnowledgeGraphService.ChatGraphSupport support = knowledgeGraphService.buildChatSupport(
                focus,
                patientContext == null ? List.of() : patientContext.assessments(),
                patientContext == null ? null : patientContext.latestRecord(),
                GRAPH_DOC_LIMIT
        );
        if (support.isEmpty()) {
            return new ToolExecutionResult(name(), false, "当前知识图谱没有返回足够相关的支撑链路。");
        }

        state.setKnowledgeGraphSummary(support.summary());
        state.setKnowledgeGraphItems(toItems(support.items()));
        return new ToolExecutionResult(
                name(),
                true,
                patientContext == null
                        ? "已补充通用知识图谱支撑，命中文档 " + support.items().size() + " 条。"
                        : "已补充患者级知识图谱支撑，命中文档 " + support.items().size() + " 条。"
        );
    }

    private List<Map<String, Object>> toItems(List<KnowledgeGraphService.ChatGraphSupportItem> items) {
        return items.stream().map(item -> {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("nodeId", item.nodeId());
            map.put("sourceType", item.sourceType());
            map.put("refId", item.refId());
            map.put("title", item.title());
            map.put("source", item.source());
            map.put("publishedAt", item.publishedAt());
            map.put("matchedDiseases", item.matchedDiseases());
            map.put("matchedTopics", item.matchedTopics());
            return map;
        }).toList();
    }
}
