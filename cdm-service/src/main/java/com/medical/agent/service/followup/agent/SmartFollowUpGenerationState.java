package com.medical.agent.service.followup.agent;

import com.medical.agent.model.entity.FollowUpPlan;
import com.medical.agent.model.entity.Patient;
import com.medical.agent.model.entity.PatientMedicalHistory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class SmartFollowUpGenerationState {

    private Long patientId;
    private Patient patient;
    private PatientMedicalHistory medicalHistory;
    private List<FollowUpPlan> pendingPlans = new ArrayList<>();
    private String medicalHistorySummary;
    private String context;
    private String focusSummary;
    private String message;
    private final List<Map<String, Object>> agentSteps = new ArrayList<>();

    public Long getPatientId() {
        return patientId;
    }

    public void setPatientId(Long patientId) {
        this.patientId = patientId;
    }

    public Patient getPatient() {
        return patient;
    }

    public void setPatient(Patient patient) {
        this.patient = patient;
    }

    public PatientMedicalHistory getMedicalHistory() {
        return medicalHistory;
    }

    public void setMedicalHistory(PatientMedicalHistory medicalHistory) {
        this.medicalHistory = medicalHistory;
    }

    public List<FollowUpPlan> getPendingPlans() {
        return pendingPlans;
    }

    public void setPendingPlans(List<FollowUpPlan> pendingPlans) {
        this.pendingPlans = pendingPlans == null ? new ArrayList<>() : new ArrayList<>(pendingPlans);
    }

    public String getMedicalHistorySummary() {
        return medicalHistorySummary;
    }

    public void setMedicalHistorySummary(String medicalHistorySummary) {
        this.medicalHistorySummary = medicalHistorySummary;
    }

    public String getContext() {
        return context;
    }

    public void setContext(String context) {
        this.context = context;
    }

    public String getFocusSummary() {
        return focusSummary;
    }

    public void setFocusSummary(String focusSummary) {
        this.focusSummary = focusSummary;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public List<Map<String, Object>> getAgentSteps() {
        return agentSteps;
    }

    public void addStep(String agentName, String summary) {
        Map<String, Object> step = new LinkedHashMap<>();
        step.put("index", agentSteps.size() + 1);
        step.put("agentName", agentName);
        step.put("summary", summary);
        agentSteps.add(step);
    }
}
