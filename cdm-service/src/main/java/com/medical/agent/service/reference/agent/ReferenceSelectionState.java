package com.medical.agent.service.reference.agent;

import com.medical.agent.common.dto.RagEvidenceDTO;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ReferenceSelectionState {

    public enum Mode {
        CHAT,
        CONTENT
    }

    private Mode mode;
    private String userQuestion;
    private String reply;
    private String topicKey;
    private String generatedContent;
    private int limit;
    private List<RagEvidenceDTO> candidates = new ArrayList<>();
    private String systemPrompt;
    private String userPrompt;
    private List<RagEvidenceDTO> selectedReferences = new ArrayList<>();
    private final List<Map<String, Object>> agentSteps = new ArrayList<>();

    public Mode getMode() {
        return mode;
    }

    public void setMode(Mode mode) {
        this.mode = mode;
    }

    public String getUserQuestion() {
        return userQuestion;
    }

    public void setUserQuestion(String userQuestion) {
        this.userQuestion = userQuestion;
    }

    public String getReply() {
        return reply;
    }

    public void setReply(String reply) {
        this.reply = reply;
    }

    public String getTopicKey() {
        return topicKey;
    }

    public void setTopicKey(String topicKey) {
        this.topicKey = topicKey;
    }

    public String getGeneratedContent() {
        return generatedContent;
    }

    public void setGeneratedContent(String generatedContent) {
        this.generatedContent = generatedContent;
    }

    public int getLimit() {
        return limit;
    }

    public void setLimit(int limit) {
        this.limit = limit;
    }

    public List<RagEvidenceDTO> getCandidates() {
        return candidates;
    }

    public void setCandidates(List<RagEvidenceDTO> candidates) {
        this.candidates = candidates == null ? new ArrayList<>() : new ArrayList<>(candidates);
    }

    public String getSystemPrompt() {
        return systemPrompt;
    }

    public void setSystemPrompt(String systemPrompt) {
        this.systemPrompt = systemPrompt;
    }

    public String getUserPrompt() {
        return userPrompt;
    }

    public void setUserPrompt(String userPrompt) {
        this.userPrompt = userPrompt;
    }

    public List<RagEvidenceDTO> getSelectedReferences() {
        return selectedReferences;
    }

    public void setSelectedReferences(List<RagEvidenceDTO> selectedReferences) {
        this.selectedReferences = selectedReferences == null ? new ArrayList<>() : new ArrayList<>(selectedReferences);
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
