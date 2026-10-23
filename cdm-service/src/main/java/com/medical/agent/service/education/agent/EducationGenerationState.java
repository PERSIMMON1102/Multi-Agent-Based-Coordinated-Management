package com.medical.agent.service.education.agent;

import com.medical.agent.common.dto.RagEvidenceDTO;
import com.medical.agent.common.dto.RagQueryContextDTO;
import com.medical.agent.common.dto.VideoResourceDTO;
import com.medical.agent.model.entity.Patient;
import com.medical.agent.model.entity.PatientMedicalHistory;
import com.medical.agent.model.entity.RiskAssessment;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class EducationGenerationState {

    private Long patientId;
    private Patient patient;
    private PatientMedicalHistory medicalHistory;
    private List<RiskAssessment> assessments = new ArrayList<>();
    private int age;
    private String generatedAt = LocalDateTime.now().toString();
    private String baseContext;
    private String medicalHistorySummary;
    private final Set<String> diseaseNames = new LinkedHashSet<>();

    private RagQueryContextDTO dietContext;
    private RagQueryContextDTO exerciseContext;
    private RagQueryContextDTO lifestyleContext;

    private List<RagEvidenceDTO> dietCandidates = new ArrayList<>();
    private List<RagEvidenceDTO> exerciseCandidates = new ArrayList<>();
    private List<RagEvidenceDTO> lifestyleCandidates = new ArrayList<>();

    private List<RagEvidenceDTO> dietPromptReferences = new ArrayList<>();
    private List<RagEvidenceDTO> exercisePromptReferences = new ArrayList<>();
    private List<RagEvidenceDTO> lifestylePromptReferences = new ArrayList<>();

    private List<RagEvidenceDTO> dietDisplayReferences = new ArrayList<>();
    private List<RagEvidenceDTO> exerciseDisplayReferences = new ArrayList<>();
    private List<RagEvidenceDTO> lifestyleDisplayReferences = new ArrayList<>();

    private String diet;
    private String exercise;
    private String lifestyle;

    private List<VideoResourceDTO> recommendedVideos = new ArrayList<>();
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

    public List<RiskAssessment> getAssessments() {
        return assessments;
    }

    public void setAssessments(List<RiskAssessment> assessments) {
        this.assessments = assessments == null ? new ArrayList<>() : new ArrayList<>(assessments);
    }

    public int getAge() {
        return age;
    }

    public void setAge(int age) {
        this.age = age;
    }

    public String getGeneratedAt() {
        return generatedAt;
    }

    public String getBaseContext() {
        return baseContext;
    }

    public void setBaseContext(String baseContext) {
        this.baseContext = baseContext;
    }

    public String getMedicalHistorySummary() {
        return medicalHistorySummary;
    }

    public void setMedicalHistorySummary(String medicalHistorySummary) {
        this.medicalHistorySummary = medicalHistorySummary;
    }

    public Set<String> getDiseaseNames() {
        return diseaseNames;
    }

    public RagQueryContextDTO getDietContext() {
        return dietContext;
    }

    public void setDietContext(RagQueryContextDTO dietContext) {
        this.dietContext = dietContext;
    }

    public RagQueryContextDTO getExerciseContext() {
        return exerciseContext;
    }

    public void setExerciseContext(RagQueryContextDTO exerciseContext) {
        this.exerciseContext = exerciseContext;
    }

    public RagQueryContextDTO getLifestyleContext() {
        return lifestyleContext;
    }

    public void setLifestyleContext(RagQueryContextDTO lifestyleContext) {
        this.lifestyleContext = lifestyleContext;
    }

    public List<RagEvidenceDTO> getDietCandidates() {
        return dietCandidates;
    }

    public void setDietCandidates(List<RagEvidenceDTO> dietCandidates) {
        this.dietCandidates = dietCandidates == null ? new ArrayList<>() : new ArrayList<>(dietCandidates);
    }

    public List<RagEvidenceDTO> getExerciseCandidates() {
        return exerciseCandidates;
    }

    public void setExerciseCandidates(List<RagEvidenceDTO> exerciseCandidates) {
        this.exerciseCandidates = exerciseCandidates == null ? new ArrayList<>() : new ArrayList<>(exerciseCandidates);
    }

    public List<RagEvidenceDTO> getLifestyleCandidates() {
        return lifestyleCandidates;
    }

    public void setLifestyleCandidates(List<RagEvidenceDTO> lifestyleCandidates) {
        this.lifestyleCandidates = lifestyleCandidates == null ? new ArrayList<>() : new ArrayList<>(lifestyleCandidates);
    }

    public List<RagEvidenceDTO> getDietPromptReferences() {
        return dietPromptReferences;
    }

    public void setDietPromptReferences(List<RagEvidenceDTO> dietPromptReferences) {
        this.dietPromptReferences = dietPromptReferences == null ? new ArrayList<>() : new ArrayList<>(dietPromptReferences);
    }

    public List<RagEvidenceDTO> getExercisePromptReferences() {
        return exercisePromptReferences;
    }

    public void setExercisePromptReferences(List<RagEvidenceDTO> exercisePromptReferences) {
        this.exercisePromptReferences = exercisePromptReferences == null ? new ArrayList<>() : new ArrayList<>(exercisePromptReferences);
    }

    public List<RagEvidenceDTO> getLifestylePromptReferences() {
        return lifestylePromptReferences;
    }

    public void setLifestylePromptReferences(List<RagEvidenceDTO> lifestylePromptReferences) {
        this.lifestylePromptReferences = lifestylePromptReferences == null ? new ArrayList<>() : new ArrayList<>(lifestylePromptReferences);
    }

    public List<RagEvidenceDTO> getDietDisplayReferences() {
        return dietDisplayReferences;
    }

    public void setDietDisplayReferences(List<RagEvidenceDTO> dietDisplayReferences) {
        this.dietDisplayReferences = dietDisplayReferences == null ? new ArrayList<>() : new ArrayList<>(dietDisplayReferences);
    }

    public List<RagEvidenceDTO> getExerciseDisplayReferences() {
        return exerciseDisplayReferences;
    }

    public void setExerciseDisplayReferences(List<RagEvidenceDTO> exerciseDisplayReferences) {
        this.exerciseDisplayReferences = exerciseDisplayReferences == null ? new ArrayList<>() : new ArrayList<>(exerciseDisplayReferences);
    }

    public List<RagEvidenceDTO> getLifestyleDisplayReferences() {
        return lifestyleDisplayReferences;
    }

    public void setLifestyleDisplayReferences(List<RagEvidenceDTO> lifestyleDisplayReferences) {
        this.lifestyleDisplayReferences = lifestyleDisplayReferences == null ? new ArrayList<>() : new ArrayList<>(lifestyleDisplayReferences);
    }

    public String getDiet() {
        return diet;
    }

    public void setDiet(String diet) {
        this.diet = diet;
    }

    public String getExercise() {
        return exercise;
    }

    public void setExercise(String exercise) {
        this.exercise = exercise;
    }

    public String getLifestyle() {
        return lifestyle;
    }

    public void setLifestyle(String lifestyle) {
        this.lifestyle = lifestyle;
    }

    public List<VideoResourceDTO> getRecommendedVideos() {
        return recommendedVideos;
    }

    public void setRecommendedVideos(List<VideoResourceDTO> recommendedVideos) {
        this.recommendedVideos = recommendedVideos == null ? new ArrayList<>() : new ArrayList<>(recommendedVideos);
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
