package com.medical.agent.service.report.agent;

import com.medical.agent.common.dto.RagEvidenceDTO;
import com.medical.agent.common.dto.RagQueryContextDTO;
import com.medical.agent.model.entity.FollowUpPlan;
import com.medical.agent.model.entity.HealthRecord;
import com.medical.agent.model.entity.Patient;
import com.medical.agent.model.entity.PatientMedicalHistory;
import com.medical.agent.model.entity.RiskAssessment;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ReportGenerationState {

    private Long patientId;
    private String type;
    private Patient patient;
    private PatientMedicalHistory medicalHistory;
    private List<RiskAssessment> assessments = new ArrayList<>();
    private List<FollowUpPlan> plans = new ArrayList<>();
    private HealthRecord latestRecord;
    private String medicalHistorySummary;
    private String title;
    private String context;
    private RagQueryContextDTO queryContext;
    private String reportTopicKey;
    private List<RagEvidenceDTO> referenceCandidates = new ArrayList<>();
    private List<RagEvidenceDTO> promptReferences = new ArrayList<>();
    private List<RagEvidenceDTO> displayReferences = new ArrayList<>();
    private String content;
    private final List<Map<String, Object>> agentSteps = new ArrayList<>();

    public Long getPatientId() {
        return patientId;
    }

    public void setPatientId(Long patientId) {
        this.patientId = patientId;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
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

    public List<RiskAssessment> getAssessments() {
        return assessments;
    }

    public void setAssessments(List<RiskAssessment> assessments) {
        this.assessments = assessments == null ? new ArrayList<>() : new ArrayList<>(assessments);
    }

    public List<FollowUpPlan> getPlans() {
        return plans;
    }

    public void setPlans(List<FollowUpPlan> plans) {
        this.plans = plans == null ? new ArrayList<>() : new ArrayList<>(plans);
    }

    public HealthRecord getLatestRecord() {
        return latestRecord;
    }

    public void setLatestRecord(HealthRecord latestRecord) {
        this.latestRecord = latestRecord;
    }

    public String getMedicalHistorySummary() {
        return medicalHistorySummary;
    }

    public void setMedicalHistorySummary(String medicalHistorySummary) {
        this.medicalHistorySummary = medicalHistorySummary;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getContext() {
        return context;
    }

    public void setContext(String context) {
        this.context = context;
    }

    public RagQueryContextDTO getQueryContext() {
        return queryContext;
    }

    public void setQueryContext(RagQueryContextDTO queryContext) {
        this.queryContext = queryContext;
    }

    public String getReportTopicKey() {
        return reportTopicKey;
    }

    public void setReportTopicKey(String reportTopicKey) {
        this.reportTopicKey = reportTopicKey;
    }

    public List<RagEvidenceDTO> getReferenceCandidates() {
        return referenceCandidates;
    }

    public void setReferenceCandidates(List<RagEvidenceDTO> referenceCandidates) {
        this.referenceCandidates = referenceCandidates == null ? new ArrayList<>() : new ArrayList<>(referenceCandidates);
    }

    public List<RagEvidenceDTO> getPromptReferences() {
        return promptReferences;
    }

    public void setPromptReferences(List<RagEvidenceDTO> promptReferences) {
        this.promptReferences = promptReferences == null ? new ArrayList<>() : new ArrayList<>(promptReferences);
    }

    public List<RagEvidenceDTO> getDisplayReferences() {
        return displayReferences;
    }

    public void setDisplayReferences(List<RagEvidenceDTO> displayReferences) {
        this.displayReferences = displayReferences == null ? new ArrayList<>() : new ArrayList<>(displayReferences);
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
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
