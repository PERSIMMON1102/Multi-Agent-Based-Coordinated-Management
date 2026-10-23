package com.medical.agent.service.react.tool;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class ToolRegistry {

    private final Map<String, AgentTool> tools;

    public ToolRegistry(List<AgentTool> tools) {
        this.tools = tools.stream().collect(Collectors.toMap(AgentTool::name, Function.identity()));
    }

    public AgentTool getTool(String name) {
        return tools.get(name);
    }

    public List<AgentTool> listTools() {
        return tools.values().stream()
                .sorted(java.util.Comparator.comparing(AgentTool::name))
                .toList();
    }
}
