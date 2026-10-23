package com.medical.agent.service.followup.agent;

public interface SmartFollowUpSpecialistAgent {

    String name();

    int order();

    void execute(SmartFollowUpGenerationState state);
}
