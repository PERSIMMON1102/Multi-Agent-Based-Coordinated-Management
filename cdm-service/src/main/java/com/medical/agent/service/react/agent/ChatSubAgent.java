package com.medical.agent.service.react.agent;

import com.medical.agent.service.react.model.AgentState;
import com.medical.agent.service.react.tool.AgentTool;

import java.util.List;

public interface ChatSubAgent {

    String CAPABILITY_PATIENT_CONTEXT = "patient_context";
    String CAPABILITY_PATIENT_PROFILE = "patient_profile";
    String CAPABILITY_RISK_ASSESSMENT = "risk_assessment";
    String CAPABILITY_MEDICAL_EVIDENCE = "medical_evidence";
    String CAPABILITY_KNOWLEDGE_GRAPH = "knowledge_graph";
    String CAPABILITY_MEDPLUM_INSIGHT = "medplum_insight";

    String name();

    String description();

    List<AgentTool> tools();

    default List<String> capabilities() {
        return List.of();
    }

    default String routeKey() {
        return name();
    }

    default String provider() {
        return "default";
    }

    default int estimatedCostScore() {
        return 50;
    }

    default int baselineHealthScore() {
        return 80;
    }

    SubAgentResult execute(AgentState state, String delegatedTask);
}
