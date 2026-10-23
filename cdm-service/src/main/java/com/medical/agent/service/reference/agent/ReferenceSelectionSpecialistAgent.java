package com.medical.agent.service.reference.agent;

public interface ReferenceSelectionSpecialistAgent {

    String name();

    int order();

    void execute(ReferenceSelectionState state);
}
