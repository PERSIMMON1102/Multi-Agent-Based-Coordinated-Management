package com.medical.agent.service.report.agent;

public interface ReportSpecialistAgent {

    String name();

    int order();

    void execute(ReportGenerationState state);
}
