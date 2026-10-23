package com.medical.agent.service.education.agent;

public interface EducationSpecialistAgent {

    String name();

    int order();

    void execute(EducationGenerationState state);
}
