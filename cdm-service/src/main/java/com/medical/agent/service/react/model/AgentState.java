package com.medical.agent.service.react.model;

import com.medical.agent.common.dto.RagEvidenceDTO;
import com.medical.agent.common.dto.RagQueryContextDTO;
import com.medical.agent.service.chat.ChatPatientContextData;
import com.medical.agent.service.chat.RuleAssessmentResult;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class AgentState {
    private String sessionId;
    private String ownerRole;
    private Long ownerUserId;
    private Long patientId;
    private String userMessage;
    private List<Map<String, String>> historyMessages = new ArrayList<>();
    private String conversationPerspective;
    private ChatPatientContextData patientContext;
    private List<RuleAssessmentResult> assessmentResults = new ArrayList<>();
    private RagQueryContextDTO evidenceContext;
    private List<RagEvidenceDTO> evidenceCandidates = new ArrayList<>();
    private String knowledgeGraphSummary;
    private List<Map<String, Object>> knowledgeGraphItems = new ArrayList<>();
    private List<AgentStep> steps = new ArrayList<>();
    private Set<String> usedTools = new LinkedHashSet<>();
    private Set<String> safetyFlags = new LinkedHashSet<>();
    private Set<String> unavailableExternalSources = new LinkedHashSet<>();
    private List<String> degradationNotes = new ArrayList<>();
    private String answerPlan;
    private String thoughtSummary;
    private String goal;
    private String progressSummary;
    private String nextAction;
    private int replanCount;
    private List<String> blockers = new ArrayList<>();
    private Map<String, Object> requestData = new LinkedHashMap<>();

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getOwnerRole() {
        return ownerRole;
    }

    public void setOwnerRole(String ownerRole) {
        this.ownerRole = ownerRole;
    }

    public Long getOwnerUserId() {
        return ownerUserId;
    }

    public void setOwnerUserId(Long ownerUserId) {
        this.ownerUserId = ownerUserId;
    }

    public Long getPatientId() {
        return patientId;
    }

    public void setPatientId(Long patientId) {
        this.patientId = patientId;
    }

    public String getUserMessage() {
        return userMessage;
    }

    public void setUserMessage(String userMessage) {
        this.userMessage = userMessage;
    }

    public List<Map<String, String>> getHistoryMessages() {
        return historyMessages;
    }

    public void setHistoryMessages(List<Map<String, String>> historyMessages) {
        this.historyMessages = historyMessages == null ? new ArrayList<>() : new ArrayList<>(historyMessages);
    }

    public String getConversationPerspective() {
        return conversationPerspective;
    }

    public void setConversationPerspective(String conversationPerspective) {
        this.conversationPerspective = conversationPerspective;
    }

    public ChatPatientContextData getPatientContext() {
        return patientContext;
    }

    public void setPatientContext(ChatPatientContextData patientContext) {
        this.patientContext = patientContext;
    }

    public List<RuleAssessmentResult> getAssessmentResults() {
        return assessmentResults;
    }

    public void setAssessmentResults(List<RuleAssessmentResult> assessmentResults) {
        this.assessmentResults = assessmentResults == null ? new ArrayList<>() : new ArrayList<>(assessmentResults);
    }

    public RagQueryContextDTO getEvidenceContext() {
        return evidenceContext;
    }

    public void setEvidenceContext(RagQueryContextDTO evidenceContext) {
        this.evidenceContext = evidenceContext;
    }

    public List<RagEvidenceDTO> getEvidenceCandidates() {
        return evidenceCandidates;
    }

    public void setEvidenceCandidates(List<RagEvidenceDTO> evidenceCandidates) {
        this.evidenceCandidates = evidenceCandidates == null ? new ArrayList<>() : new ArrayList<>(evidenceCandidates);
    }

    public String getKnowledgeGraphSummary() {
        return knowledgeGraphSummary;
    }

    public void setKnowledgeGraphSummary(String knowledgeGraphSummary) {
        this.knowledgeGraphSummary = knowledgeGraphSummary;
    }

    public List<Map<String, Object>> getKnowledgeGraphItems() {
        return knowledgeGraphItems;
    }

    public void setKnowledgeGraphItems(List<Map<String, Object>> knowledgeGraphItems) {
        this.knowledgeGraphItems = knowledgeGraphItems == null ? new ArrayList<>() : new ArrayList<>(knowledgeGraphItems);
    }

    public List<AgentStep> getSteps() {
        return steps;
    }

    public void addStep(AgentStep step) {
        if (step != null) {
            this.steps.add(step);
        }
    }

    public Set<String> getUsedTools() {
        return usedTools;
    }

    public void addUsedTool(String toolName) {
        if (toolName != null && !toolName.isBlank()) {
            this.usedTools.add(toolName);
        }
    }

    public Set<String> getSafetyFlags() {
        return safetyFlags;
    }

    public void addSafetyFlag(String safetyFlag) {
        if (safetyFlag != null && !safetyFlag.isBlank()) {
            this.safetyFlags.add(safetyFlag);
        }
    }

    public Set<String> getUnavailableExternalSources() {
        return unavailableExternalSources;
    }

    public void addUnavailableExternalSource(String source) {
        if (source != null && !source.isBlank()) {
            this.unavailableExternalSources.add(source);
        }
    }

    public List<String> getDegradationNotes() {
        return degradationNotes;
    }

    public void addDegradationNote(String note) {
        if (note != null && !note.isBlank()) {
            this.degradationNotes.add(note);
        }
    }

    public String getAnswerPlan() {
        return answerPlan;
    }

    public void setAnswerPlan(String answerPlan) {
        this.answerPlan = answerPlan;
    }

    public String getThoughtSummary() {
        return thoughtSummary;
    }

    public void setThoughtSummary(String thoughtSummary) {
        this.thoughtSummary = thoughtSummary;
    }

    public String getGoal() {
        return goal;
    }

    public void setGoal(String goal) {
        this.goal = goal;
    }

    public String getProgressSummary() {
        return progressSummary;
    }

    public void setProgressSummary(String progressSummary) {
        this.progressSummary = progressSummary;
    }

    public String getNextAction() {
        return nextAction;
    }

    public void setNextAction(String nextAction) {
        this.nextAction = nextAction;
    }

    public int getReplanCount() {
        return replanCount;
    }

    public void incrementReplanCount() {
        this.replanCount++;
    }

    public List<String> getBlockers() {
        return blockers;
    }

    public void setBlockers(List<String> blockers) {
        this.blockers = blockers == null ? new ArrayList<>() : new ArrayList<>(blockers);
    }

    public void addBlocker(String blocker) {
        if (blocker != null && !blocker.isBlank()) {
            this.blockers.add(blocker);
        }
    }

    public Map<String, Object> getRequestData() {
        return requestData;
    }

    public void setRequestData(Map<String, Object> requestData) {
        this.requestData = requestData == null ? new LinkedHashMap<>() : new LinkedHashMap<>(requestData);
    }

    public AgentState copy() {
        AgentState copy = new AgentState();
        copy.sessionId = this.sessionId;
        copy.ownerRole = this.ownerRole;
        copy.ownerUserId = this.ownerUserId;
        copy.patientId = this.patientId;
        copy.userMessage = this.userMessage;
        copy.historyMessages = this.historyMessages == null ? new ArrayList<>() : new ArrayList<>(this.historyMessages);
        copy.conversationPerspective = this.conversationPerspective;
        copy.patientContext = this.patientContext;
        copy.assessmentResults = this.assessmentResults == null ? new ArrayList<>() : new ArrayList<>(this.assessmentResults);
        copy.evidenceContext = this.evidenceContext;
        copy.evidenceCandidates = this.evidenceCandidates == null ? new ArrayList<>() : new ArrayList<>(this.evidenceCandidates);
        copy.knowledgeGraphSummary = this.knowledgeGraphSummary;
        copy.knowledgeGraphItems = this.knowledgeGraphItems == null ? new ArrayList<>() : new ArrayList<>(this.knowledgeGraphItems);
        copy.steps = this.steps == null ? new ArrayList<>() : new ArrayList<>(this.steps);
        copy.usedTools = this.usedTools == null ? new LinkedHashSet<>() : new LinkedHashSet<>(this.usedTools);
        copy.safetyFlags = this.safetyFlags == null ? new LinkedHashSet<>() : new LinkedHashSet<>(this.safetyFlags);
        copy.unavailableExternalSources = this.unavailableExternalSources == null ? new LinkedHashSet<>() : new LinkedHashSet<>(this.unavailableExternalSources);
        copy.degradationNotes = this.degradationNotes == null ? new ArrayList<>() : new ArrayList<>(this.degradationNotes);
        copy.answerPlan = this.answerPlan;
        copy.thoughtSummary = this.thoughtSummary;
        copy.goal = this.goal;
        copy.progressSummary = this.progressSummary;
        copy.nextAction = this.nextAction;
        copy.replanCount = this.replanCount;
        copy.blockers = this.blockers == null ? new ArrayList<>() : new ArrayList<>(this.blockers);
        copy.requestData = this.requestData == null ? new LinkedHashMap<>() : new LinkedHashMap<>(this.requestData);
        return copy;
    }
}
