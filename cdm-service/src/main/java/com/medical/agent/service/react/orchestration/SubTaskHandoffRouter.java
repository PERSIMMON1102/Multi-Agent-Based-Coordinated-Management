package com.medical.agent.service.react.orchestration;

import com.medical.agent.model.mongo.OrchestrationTaskSnapshot;
import com.medical.agent.service.react.agent.ChatSubAgent;
import com.medical.agent.service.react.agent.SubAgentRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

@Component
public class SubTaskHandoffRouter {

    private static final String RISK_AGENT = "risk_assessment_agent";
    private static final String PROFILE_AGENT = "patient_profile_agent";
    private static final String EVIDENCE_AGENT = "evidence_support_agent";

    private final SubAgentRegistry subAgentRegistry;
    private final OrchestrationPersistenceService orchestrationPersistenceService;

    public SubTaskHandoffRouter(SubAgentRegistry subAgentRegistry) {
        this(subAgentRegistry, null);
    }
    @Autowired
    public SubTaskHandoffRouter(SubAgentRegistry subAgentRegistry,
                                OrchestrationPersistenceService orchestrationPersistenceService) {
        this.subAgentRegistry = subAgentRegistry;
        this.orchestrationPersistenceService = orchestrationPersistenceService;
    }

    public String selectReplacementAgent(OrchestrationTaskSnapshot snapshot, Map<String, Object> subTask) {
        if (subTask == null) {
            return null;
        }
        String currentAgent = stringValue(subTask.get("targetAgent"));
        String goal = stringValue(subTask.get("goal"));
        String failureCategory = recentFailureCategory(snapshot, subTask);
        List<String> missingCapabilities = recentMissingCapabilities(snapshot, subTask);
        Set<String> avoidedAgents = collectAvoidedAgents(snapshot, subTask);
        Map<String, Map<String, Integer>> longTermStats = longTermPerformanceStats();
        Map<String, Map<String, Object>> executionHealthStats = executionHealthStats();
        return preferredCandidates(currentAgent, executionHealthStats).stream()
                .filter(agent -> !matchesRouteOrName(agent, currentAgent))
                .filter(agent -> !avoidedAgents.contains(agent.name()))
                .max(Comparator.comparingInt(agent -> scoreAgent(snapshot, subTask, agent, missingCapabilities, failureCategory, goal, currentAgent, longTermStats, executionHealthStats)))
                .map(ChatSubAgent::routeKey)
                .orElseGet(() -> fallbackAgentName(currentAgent));
    }

    public String selectAgentForCapabilities(List<String> missingCapabilities, String currentAgent) {
        Map<String, Map<String, Object>> executionHealthStats = executionHealthStats();
        return preferredCandidates(currentAgent, executionHealthStats).stream()
                .filter(agent -> !matchesRouteOrName(agent, currentAgent))
                .max(Comparator.comparingInt(agent -> capabilityScore(agent, missingCapabilities, currentAgent)))
                .map(ChatSubAgent::routeKey)
                .orElseGet(() -> fallbackAgentName(currentAgent));
    }

    public String explainReplacementReason(OrchestrationTaskSnapshot snapshot, Map<String, Object> subTask) {
        if (subTask == null) {
            return null;
        }
        List<String> missingCapabilities = recentMissingCapabilities(snapshot, subTask);
        if (!normalizeCapabilities(missingCapabilities).isEmpty()) {
            return "missing_capabilities:" + String.join(",", normalizeCapabilities(missingCapabilities));
        }
        String failureCategory = recentFailureCategory(snapshot, subTask);
        if (failureCategory != null && !failureCategory.isBlank()) {
            return "failure_category:" + failureCategory;
        }
        String successfulAgent = bestRecentSuccessfulAgent(snapshot, subTask);
        if (successfulAgent != null && !successfulAgent.isBlank()) {
            return "recent_success:" + successfulAgent;
        }
        String longTermAgent = bestLongTermAgent();
        if (longTermAgent != null && !longTermAgent.isBlank()) {
            return "long_term_success:" + longTermAgent;
        }
        String goal = stringValue(subTask.get("goal"));
        if (goal != null && !routeByGoal(goal).isEmpty()) {
            return "goal_semantic";
        }
        String currentAgent = stringValue(subTask.get("targetAgent"));
        if (currentAgent != null && !currentAgent.isBlank()) {
            return "fallback_from:" + currentAgent;
        }
        return "default_route";
    }

    public List<Map<String, Object>> scoreReplacementCandidates(OrchestrationTaskSnapshot snapshot, Map<String, Object> subTask) {
        if (subTask == null) {
            return List.of();
        }
        String currentAgent = stringValue(subTask.get("targetAgent"));
        String goal = stringValue(subTask.get("goal"));
        String failureCategory = recentFailureCategory(snapshot, subTask);
        List<String> missingCapabilities = recentMissingCapabilities(snapshot, subTask);
        Set<String> avoidedAgents = collectAvoidedAgents(snapshot, subTask);
        Map<String, Map<String, Integer>> longTermStats = longTermPerformanceStats();
        Map<String, Map<String, Object>> executionHealthStats = executionHealthStats();
        return preferredCandidates(currentAgent, executionHealthStats).stream()
                .filter(agent -> !matchesRouteOrName(agent, currentAgent))
                .map(agent -> scoreBreakdown(snapshot, subTask, agent, missingCapabilities, failureCategory, goal, currentAgent, avoidedAgents, longTermStats, executionHealthStats))
                .filter(item -> !(boolean) item.get("avoided"))
                .sorted((left, right) -> Integer.compare((int) right.get("score"), (int) left.get("score")))
                .toList();
    }

    public Map<String, Object> summarizeRecommendation(List<Map<String, Object>> scoredCandidates) {
        if (scoredCandidates == null || scoredCandidates.isEmpty()) {
            return Map.of(
                    "confidence", "low",
                    "scoreGap", 0,
                    "needsReview", true,
                    "volatilityScore", 0D,
                    "reviewTrigger", "no_candidate"
            );
        }
        int topScore = integerValue(scoredCandidates.get(0).get("score"));
        int secondScore = scoredCandidates.size() > 1 ? integerValue(scoredCandidates.get(1).get("score")) : 0;
        int gap = topScore - secondScore;
        double volatilityScore = recommendationVolatility(scoredCandidates.get(0));
        String reviewTrigger = "";
        String confidence;
        if (topScore >= 160 && gap >= 40) {
            confidence = "high";
        } else if (topScore >= 100 && gap >= 15) {
            confidence = "medium";
        } else {
            confidence = "low";
        }
        if (volatilityScore >= 28D) {
            confidence = "low";
            reviewTrigger = "high_recent_volatility";
        } else if (gap <= 12 && volatilityScore >= 14D) {
            confidence = "low";
            reviewTrigger = "close_candidates_with_recent_volatility";
        } else if ("low".equals(confidence)) {
            reviewTrigger = "low_confidence_score_gap";
        }
        boolean needsReview = "low".equals(confidence);
        return Map.of(
                "confidence", confidence,
                "scoreGap", gap,
                "needsReview", needsReview,
                "volatilityScore", volatilityScore,
                "reviewTrigger", reviewTrigger
        );
    }

    private List<String> routeByGoal(String goal) {
        String normalized = goal == null ? "" : goal.toLowerCase(Locale.ROOT);
        if (containsAny(normalized, "证据", "指南", "文献", "知识图谱", "evidence", "guideline")) {
            return List.of(EVIDENCE_AGENT, PROFILE_AGENT, RISK_AGENT);
        }
        if (containsAny(normalized, "画像", "病史", "资料", "档案", "medplum", "患者")) {
            return List.of(PROFILE_AGENT, RISK_AGENT, EVIDENCE_AGENT);
        }
        if (containsAny(normalized, "风险", "分层", "高危", "评估", "risk")) {
            return List.of(RISK_AGENT, PROFILE_AGENT, EVIDENCE_AGENT);
        }
        return List.of();
    }

    private List<String> routeByCurrentAgent(String currentAgent) {
        if (RISK_AGENT.equals(currentAgent)) {
            return List.of(PROFILE_AGENT, EVIDENCE_AGENT);
        }
        if (PROFILE_AGENT.equals(currentAgent)) {
            return List.of(RISK_AGENT, EVIDENCE_AGENT);
        }
        if (EVIDENCE_AGENT.equals(currentAgent)) {
            return List.of(RISK_AGENT, PROFILE_AGENT);
        }
        return List.of(PROFILE_AGENT, RISK_AGENT, EVIDENCE_AGENT);
    }

    private List<String> defaultOrder() {
        return List.of(PROFILE_AGENT, RISK_AGENT, EVIDENCE_AGENT);
    }

    private int scoreAgent(OrchestrationTaskSnapshot snapshot,
                           Map<String, Object> subTask,
                           ChatSubAgent agent,
                           List<String> missingCapabilities,
                           String failureCategory,
                           String goal,
                           String currentAgent,
                           Map<String, Map<String, Integer>> longTermStats,
                           Map<String, Map<String, Object>> executionHealthStats) {
        RoutingWeightProfile weightProfile = routingWeightProfile(snapshot, goal);
        int score = 0;
        score += orderedMatchScore(agent.name(), routeByMissingCapabilities(missingCapabilities), weightProfile.missingCapabilityRoute());
        score += capabilityScore(agent, missingCapabilities, currentAgent);
        score += orderedMatchScore(agent.name(), routeByFailureCategory(failureCategory), weightProfile.failureCategoryRoute());
        score += orderedMatchScore(agent.name(), routeByGoal(goal), weightProfile.goalSemanticRoute());
        score += orderedMatchScore(agent.name(), routeByCurrentAgent(currentAgent), 5);
        score += orderedMatchScore(agent.name(), defaultOrder(), 2);
        score += capabilityCoverageBonus(agent, goal);
        score += recentPerformanceScore(snapshot, subTask, agent.name());
        score += longTermPerformanceScore(agent.name(), longTermStats);
        score += executionHealthScore(agent.name(), executionHealthStats, weightProfile);
        score += recencyWeightedExecutionHealthScore(agent.name(), executionHealthStats, weightProfile);
        score += baselineHealthBonus(agent);
        score += providerDiversityScore(agent, currentAgent);
        score += costEfficiencyScore(agent, goal);
        return score;
    }

    private Map<String, Object> scoreBreakdown(OrchestrationTaskSnapshot snapshot,
                                               Map<String, Object> subTask,
                                               ChatSubAgent agent,
                                               List<String> missingCapabilities,
                                               String failureCategory,
                                               String goal,
                                               String currentAgent,
                                               Set<String> avoidedAgents,
                                               Map<String, Map<String, Integer>> longTermStats,
                                               Map<String, Map<String, Object>> executionHealthStats) {
        RoutingWeightProfile weightProfile = routingWeightProfile(snapshot, goal);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("agent", agent.name());
        payload.put("capabilities", agent.capabilities());
        payload.put("avoided", avoidedAgents.contains(agent.name()));
        payload.put("quarantinedByHealthPolicy", isQuarantinedByHealthPolicy(agent, executionHealthStats));
        Map<String, Object> breakdown = new LinkedHashMap<>();
        int missingCapabilityRoute = orderedMatchScore(agent.name(), routeByMissingCapabilities(missingCapabilities), weightProfile.missingCapabilityRoute());
        int capabilityMatch = capabilityScore(agent, missingCapabilities, currentAgent);
        int failureCategoryRoute = orderedMatchScore(agent.name(), routeByFailureCategory(failureCategory), weightProfile.failureCategoryRoute());
        int goalSemanticRoute = orderedMatchScore(agent.name(), routeByGoal(goal), weightProfile.goalSemanticRoute());
        int currentAgentFallback = orderedMatchScore(agent.name(), routeByCurrentAgent(currentAgent), 5);
        int defaultRoute = orderedMatchScore(agent.name(), defaultOrder(), 2);
        int goalCapabilityBonus = capabilityCoverageBonus(agent, goal);
        int recentPerformance = recentPerformanceScore(snapshot, subTask, agent.name());
        int longTermPerformance = longTermPerformanceScore(agent.name(), longTermStats);
        int executionHealth = executionHealthScore(agent.name(), executionHealthStats, weightProfile);
        int recencyWeightedHealth = recencyWeightedExecutionHealthScore(agent.name(), executionHealthStats, weightProfile);
        int baselineHealth = baselineHealthBonus(agent);
        int providerHealth = providerHealthScore(agent, executionHealthStats);
        int providerDiversity = providerDiversityScore(agent, currentAgent);
        int costEfficiency = costEfficiencyScore(agent, goal);
        breakdown.put("missingCapabilityRoute", missingCapabilityRoute);
        breakdown.put("capabilityMatch", capabilityMatch);
        breakdown.put("failureCategoryRoute", failureCategoryRoute);
        breakdown.put("goalSemanticRoute", goalSemanticRoute);
        breakdown.put("currentAgentFallback", currentAgentFallback);
        breakdown.put("defaultRoute", defaultRoute);
        breakdown.put("goalCapabilityBonus", goalCapabilityBonus);
        breakdown.put("recentPerformance", recentPerformance);
        breakdown.put("longTermPerformance", longTermPerformance);
        breakdown.put("longTermStats", longTermStats.getOrDefault(agent.name(), Map.of()));
        breakdown.put("executionHealth", executionHealth);
        breakdown.put("recencyWeightedHealth", recencyWeightedHealth);
        breakdown.put("baselineHealth", baselineHealth);
        breakdown.put("providerHealth", providerHealth);
        breakdown.put("providerHealthState", subAgentRegistry.providerHealthState(agent.provider(), executionHealthStats));
        breakdown.put("implementationHealthState", subAgentRegistry.implementationHealthState(agent.name(), executionHealthStats));
        breakdown.put("providerDiversity", providerDiversity);
        breakdown.put("costEfficiency", costEfficiency);
        breakdown.put("provider", agent.provider());
        breakdown.put("routeKey", agent.routeKey());
        breakdown.put("taskType", weightProfile.taskType());
        breakdown.put("taskTemplateWeights", weightProfile.asMap());
        breakdown.put("executionHealthStats", executionHealthStats.getOrDefault(agent.name(), Map.of()));
        int total = missingCapabilityRoute
                + capabilityMatch
                + failureCategoryRoute
                + goalSemanticRoute
                + currentAgentFallback
                + defaultRoute
                + goalCapabilityBonus
                + recentPerformance
                + longTermPerformance
                + executionHealth
                + recencyWeightedHealth
                + baselineHealth
                + providerHealth
                + providerDiversity
                + costEfficiency;
        payload.put("score", total);
        payload.put("breakdown", breakdown);
        return payload;
    }

    private int longTermPerformanceScore(String agentName, Map<String, Map<String, Integer>> longTermStats) {
        if (agentName == null || agentName.isBlank() || longTermStats == null || longTermStats.isEmpty()) {
            return 0;
        }
        return longTermStats.getOrDefault(agentName, Map.of()).getOrDefault("score", 0);
    }

    private int executionHealthScore(String agentName,
                                     Map<String, Map<String, Object>> executionHealthStats,
                                     RoutingWeightProfile weightProfile) {
        if (agentName == null || agentName.isBlank() || executionHealthStats == null || executionHealthStats.isEmpty()) {
            return 0;
        }
        Map<String, Object> stats = executionHealthStats.getOrDefault(agentName, Map.of());
        double successRate = doubleValue(stats.get("successRate"));
        double stabilityScore = doubleValue(stats.get("stabilityScore"));
        int executionCount = integerValue(stats.get("executionCount"));
        if (executionCount <= 0) {
            return 0;
        }
        double healthWeight = weightProfile == null ? 1D : weightProfile.healthWeight();
        return (int) Math.round(((successRate * 35D) + (stabilityScore * 25D)) * healthWeight);
    }

    private int recencyWeightedExecutionHealthScore(String agentName,
                                                    Map<String, Map<String, Object>> executionHealthStats,
                                                    RoutingWeightProfile weightProfile) {
        if (agentName == null || agentName.isBlank() || executionHealthStats == null || executionHealthStats.isEmpty()) {
            return 0;
        }
        Map<String, Object> stats = executionHealthStats.getOrDefault(agentName, Map.of());
        Object trend = stats.get("recent24hExecutionTrend");
        if (!(trend instanceof List<?> items) || items.isEmpty()) {
            return 0;
        }
        double weightedScore = 0D;
        double totalWeight = 0D;
        for (int index = 0; index < items.size(); index++) {
            Object item = items.get(index);
            if (!(item instanceof Map<?, ?> bucket)) {
                continue;
            }
            long completed = longValue(bucket.get("completedCount"));
            long failed = longValue(bucket.get("failedCount"));
            long blocked = longValue(bucket.get("blockedCount"));
            long rerouted = longValue(bucket.get("reroutedCount"));
            long total = completed + failed + blocked + rerouted;
            if (total <= 0) {
                continue;
            }
            double successRate = doubleValue(bucket.get("successRate"));
            double bucketStability = ((completed * 1.0) - (failed * 1.2) - (blocked * 0.6) - (rerouted * 0.4)) / total;
            double weight = recencyWeight(items.size(), index);
            weightedScore += (((successRate * 40D) + (bucketStability * 30D)) * weight);
            totalWeight += weight;
        }
        if (totalWeight <= 0D) {
            return 0;
        }
        double healthWeight = weightProfile == null ? 1D : weightProfile.healthWeight();
        return (int) Math.round((weightedScore / totalWeight) * healthWeight);
    }

    private double recommendationVolatility(Map<String, Object> scoredCandidate) {
        if (scoredCandidate == null || !(scoredCandidate.get("breakdown") instanceof Map<?, ?> breakdown)) {
            return 0D;
        }
        Object executionHealthStats = breakdown.get("executionHealthStats");
        if (!(executionHealthStats instanceof Map<?, ?> stats)) {
            return 0D;
        }
        Object trend = stats.get("recent24hExecutionTrend");
        if (!(trend instanceof List<?> items) || items.size() < 2) {
            return 0D;
        }
        double previousBucketHealth = bucketHealthScore(items.get(items.size() - 2));
        double latestBucketHealth = bucketHealthScore(items.get(items.size() - 1));
        return Math.round(Math.abs(latestBucketHealth - previousBucketHealth) * 1000D) / 1000D;
    }

    private double bucketHealthScore(Object item) {
        if (!(item instanceof Map<?, ?> bucket)) {
            return 0D;
        }
        long completed = longValue(bucket.get("completedCount"));
        long failed = longValue(bucket.get("failedCount"));
        long blocked = longValue(bucket.get("blockedCount"));
        long rerouted = longValue(bucket.get("reroutedCount"));
        long total = completed + failed + blocked + rerouted;
        if (total <= 0) {
            return 0D;
        }
        double successRate = doubleValue(bucket.get("successRate"));
        double stability = ((completed * 1.0) - (failed * 1.2) - (blocked * 0.6) - (rerouted * 0.4)) / total;
        return (successRate * 40D) + (stability * 30D);
    }

    private double recencyWeight(int totalBuckets, int index) {
        int distanceFromLatest = Math.max((totalBuckets - 1) - index, 0);
        return switch (distanceFromLatest) {
            case 0 -> 1.0D;
            case 1 -> 0.75D;
            case 2 -> 0.5D;
            case 3 -> 0.35D;
            default -> 0.2D;
        };
    }

    private int capabilityScore(ChatSubAgent agent, List<String> missingCapabilities, String currentAgent) {
        if (agent == null) {
            return Integer.MIN_VALUE;
        }
        int score = orderedMatchScore(agent.routeKey(), routeByCurrentAgent(currentAgent), 2);
        List<String> normalizedCapabilities = normalizeCapabilities(missingCapabilities);
        if (normalizedCapabilities.isEmpty()) {
            return score;
        }
        for (String capability : normalizedCapabilities) {
            if (agent.capabilities().stream().anyMatch(item -> item.equalsIgnoreCase(capability))) {
                score += 120;
            }
        }
        return score;
    }

    private int capabilityCoverageBonus(ChatSubAgent agent, String goal) {
        if (agent == null || goal == null || goal.isBlank()) {
            return 0;
        }
        String normalizedGoal = goal.toLowerCase(Locale.ROOT);
        int score = 0;
        for (String capability : agent.capabilities()) {
            if (capability == null) {
                continue;
            }
            String normalizedCapability = capability.toLowerCase(Locale.ROOT);
            if (normalizedGoal.contains(normalizedCapability.replace('_', ' ')) || normalizedGoal.contains(normalizedCapability)) {
                score += 15;
            }
        }
        return score;
    }

    private int orderedMatchScore(String agentName, List<String> orderedCandidates, int baseScore) {
        if (agentName == null || orderedCandidates == null || orderedCandidates.isEmpty()) {
            return 0;
        }
        int index = orderedCandidates.indexOf(agentName);
        if (index < 0) {
            return 0;
        }
        return Math.max(baseScore - (index * 10), 1);
    }

    private String fallbackAgentName(String currentAgent) {
        return defaultOrder().stream()
                .filter(subAgentRegistry::hasRoute)
                .filter(name -> currentAgent == null || !name.equals(currentAgent))
                .findFirst()
                .orElse(null);
    }

    private List<String> routeByFailureCategory(String failureCategory) {
        if (failureCategory == null || failureCategory.isBlank()) {
            return List.of();
        }
        return switch (failureCategory) {
            case "context_gap" -> List.of(PROFILE_AGENT, EVIDENCE_AGENT, RISK_AGENT);
            case "evidence_gap" -> List.of(EVIDENCE_AGENT, PROFILE_AGENT, RISK_AGENT);
            case "risk_reassessment" -> List.of(RISK_AGENT, PROFILE_AGENT, EVIDENCE_AGENT);
            case "tool_or_runtime_failure" -> List.of(PROFILE_AGENT, EVIDENCE_AGENT, RISK_AGENT);
            case "missing_agent" -> List.of(PROFILE_AGENT, RISK_AGENT, EVIDENCE_AGENT);
            default -> List.of();
        };
    }

    private List<String> routeByMissingCapabilities(List<String> missingCapabilities) {
        List<String> normalizedCapabilities = normalizeCapabilities(missingCapabilities);
        if (normalizedCapabilities.isEmpty()) {
            return List.of();
        }
        List<String> candidates = new ArrayList<>();
        for (String capability : normalizedCapabilities) {
            if (capability == null || capability.isBlank()) {
                continue;
            }
            String normalized = capability.toLowerCase(Locale.ROOT);
            subAgentRegistry.listAgentsByCapability(normalized).stream()
                    .map(ChatSubAgent::routeKey)
                    .forEach(candidates::add);
            if (containsAny(normalized, "patient_context", "patient_profile", "profile", "context", "history", "medplum")) {
                candidates.add(PROFILE_AGENT);
            }
            if (containsAny(normalized, "medical_evidence", "evidence", "guideline", "knowledge_graph", "citation")) {
                candidates.add(EVIDENCE_AGENT);
            }
            if (containsAny(normalized, "risk_assessment", "risk", "triage", "stratification")) {
                candidates.add(RISK_AGENT);
            }
        }
        return candidates.stream().distinct().toList();
    }

    private int baselineHealthBonus(ChatSubAgent agent) {
        return Math.max(0, agent.baselineHealthScore() - 50);
    }

    private int providerDiversityScore(ChatSubAgent agent, String currentAgent) {
        if (agent == null || currentAgent == null || currentAgent.isBlank()) {
            return 0;
        }
        ChatSubAgent current = subAgentRegistry.getPrimaryAgent(currentAgent);
        if (current == null) {
            current = subAgentRegistry.getAgent(currentAgent);
        }
        if (current == null) {
            return 0;
        }
        return agent.provider().equalsIgnoreCase(current.provider()) ? 0 : 6;
    }

    private int providerHealthScore(ChatSubAgent agent, Map<String, Map<String, Object>> executionHealthStats) {
        if (agent == null) {
            return 0;
        }
        String state = subAgentRegistry.providerHealthState(agent.provider(), executionHealthStats);
        return switch (state) {
            case SubAgentRegistry.HEALTH_STATE_HEALTHY -> 12;
            case SubAgentRegistry.HEALTH_STATE_UNKNOWN -> 0;
            case SubAgentRegistry.HEALTH_STATE_DEGRADED -> -8;
            case SubAgentRegistry.HEALTH_STATE_QUARANTINED -> -20;
            default -> 0;
        };
    }

    private List<ChatSubAgent> preferredCandidates(String currentAgent, Map<String, Map<String, Object>> executionHealthStats) {
        List<ChatSubAgent> candidates = subAgentRegistry.listPrimaryAgents().stream()
                .filter(agent -> !matchesRouteOrName(agent, currentAgent))
                .toList();
        List<ChatSubAgent> nonQuarantined = candidates.stream()
                .filter(agent -> !isQuarantinedByHealthPolicy(agent, executionHealthStats))
                .toList();
        return nonQuarantined.isEmpty() ? candidates : nonQuarantined;
    }

    private boolean isQuarantinedByHealthPolicy(ChatSubAgent agent, Map<String, Map<String, Object>> executionHealthStats) {
        if (agent == null) {
            return false;
        }
        String implementationState = subAgentRegistry.implementationHealthState(agent.name(), executionHealthStats);
        if (SubAgentRegistry.HEALTH_STATE_QUARANTINED.equals(implementationState)) {
            return true;
        }
        String providerState = subAgentRegistry.providerHealthState(agent.provider(), executionHealthStats);
        return SubAgentRegistry.HEALTH_STATE_QUARANTINED.equals(providerState);
    }

    private int costEfficiencyScore(ChatSubAgent agent, String goal) {
        if (agent == null) {
            return 0;
        }
        int costScore = Math.max(0, 100 - agent.estimatedCostScore());
        if (goal == null || goal.isBlank()) {
            return costScore / 6;
        }
        String normalizedGoal = goal.toLowerCase(Locale.ROOT);
        if (containsAny(normalizedGoal, "快速", "尽快", "先", "立即", "紧急")) {
            return costScore / 4;
        }
        return costScore / 8;
    }

    private boolean matchesRouteOrName(ChatSubAgent agent, String currentAgent) {
        if (agent == null || currentAgent == null || currentAgent.isBlank()) {
            return false;
        }
        return agent.name().equals(currentAgent) || agent.routeKey().equals(currentAgent);
    }

    private int recentPerformanceScore(OrchestrationTaskSnapshot snapshot, Map<String, Object> subTask, String agentName) {
        if (snapshot == null || snapshot.getEvents() == null || snapshot.getEvents().isEmpty() || agentName == null || agentName.isBlank()) {
            return 0;
        }
        String subTaskId = stringValue(subTask.get("subTaskId"));
        List<Map<String, Object>> events = new ArrayList<>(snapshot.getEvents());
        Collections.reverse(events);
        int score = 0;
        int inspected = 0;
        for (Map<String, Object> event : events) {
            if (!(event.get("type") instanceof String typeText)) {
                continue;
            }
            String eventSubTaskId = metadataValue(event, "subTaskId");
            if (subTaskId != null && !subTaskId.isBlank() && eventSubTaskId != null && !subTaskId.equals(eventSubTaskId)) {
                continue;
            }
            String eventAgent = trackedEventAgent(event);
            if (!agentName.equals(eventAgent)) {
                continue;
            }
            score += switch (typeText) {
                case "COMPLETED" -> 70;
                case "FAILED", "BLOCKED" -> -80;
                case "SUBTASK_HANDOFF", "SUBTASK_SKIPPED" -> -45;
                default -> 0;
            };
            inspected++;
            if (inspected >= 4) {
                break;
            }
        }
        return score;
    }

    private String bestRecentSuccessfulAgent(OrchestrationTaskSnapshot snapshot, Map<String, Object> subTask) {
        if (snapshot == null || snapshot.getEvents() == null || snapshot.getEvents().isEmpty()) {
            return null;
        }
        String subTaskId = stringValue(subTask.get("subTaskId"));
        List<Map<String, Object>> events = new ArrayList<>(snapshot.getEvents());
        Collections.reverse(events);
        for (Map<String, Object> event : events) {
            if (!(event.get("type") instanceof String typeText) || !"COMPLETED".equals(typeText)) {
                continue;
            }
            String eventSubTaskId = metadataValue(event, "subTaskId");
            if (subTaskId != null && !subTaskId.isBlank() && eventSubTaskId != null && !subTaskId.equals(eventSubTaskId)) {
                continue;
            }
            String eventAgent = trackedEventAgent(event);
            if (eventAgent != null && !eventAgent.isBlank()) {
                return eventAgent;
            }
        }
        return null;
    }

    private String bestLongTermAgent() {
        return longTermPerformanceStats().entrySet().stream()
                .max((left, right) -> Integer.compare(
                        left.getValue().getOrDefault("score", 0),
                        right.getValue().getOrDefault("score", 0)
                ))
                .map(Map.Entry::getKey)
                .orElse(null);
    }

    private Map<String, Map<String, Integer>> longTermPerformanceStats() {
        if (orchestrationPersistenceService == null) {
            return Map.of();
        }
        return orchestrationPersistenceService.summarizeRecentAgentPerformance();
    }

    private Map<String, Map<String, Object>> executionHealthStats() {
        if (orchestrationPersistenceService == null) {
            return Map.of();
        }
        Map<String, Map<String, Object>> stats = new LinkedHashMap<>();
        for (Map<String, Object> item : orchestrationPersistenceService.summarizeRecentAgentExecutionHealth(24)) {
            String agent = stringValue(item.get("agent"));
            if (agent != null && !agent.isBlank()) {
                stats.put(agent, item);
            }
        }
        return stats;
    }

    private RoutingWeightProfile routingWeightProfile(OrchestrationTaskSnapshot snapshot, String goal) {
        String taskType = snapshot == null ? classifyTaskType(goal) : stringValue(snapshot.getTaskType());
        if (taskType == null || taskType.isBlank()) {
            taskType = classifyTaskType(goal);
        }
        return switch (taskType) {
            case "context_enrichment" -> new RoutingWeightProfile(taskType, 155, 90, 65, 0.9D);
            case "evidence_synthesis" -> new RoutingWeightProfile(taskType, 125, 120, 95, 1.0D);
            case "risk_triage" -> new RoutingWeightProfile(taskType, 130, 130, 105, 1.15D);
            default -> new RoutingWeightProfile("general_coordination", 140, 90, 60, 1.0D);
        };
    }

    private String classifyTaskType(String goal) {
        String normalized = goal == null ? "" : goal.toLowerCase(Locale.ROOT);
        if (containsAny(normalized, "画像", "病史", "资料", "档案", "患者", "medplum", "context", "profile", "history")) {
            return "context_enrichment";
        }
        if (containsAny(normalized, "证据", "指南", "文献", "知识图谱", "evidence", "guideline", "citation")) {
            return "evidence_synthesis";
        }
        if (containsAny(normalized, "风险", "分层", "高危", "评估", "risk", "triage", "stratification")) {
            return "risk_triage";
        }
        return "general_coordination";
    }

    private List<String> normalizeCapabilities(List<String> capabilities) {
        if (capabilities == null || capabilities.isEmpty()) {
            return List.of();
        }
        return capabilities.stream()
                .filter(item -> item != null && !item.isBlank())
                .flatMap(item -> expandCapabilityAlias(item).stream())
                .distinct()
                .toList();
    }

    private List<String> expandCapabilityAlias(String capability) {
        String normalized = capability == null ? "" : capability.toLowerCase(Locale.ROOT).trim();
        if (normalized.isBlank()) {
            return List.of();
        }
        return switch (normalized) {
            case "patient_context", "patient_profile", "context", "profile", "history", "medplum", "medplum_insight" ->
                    List.of(
                            ChatSubAgent.CAPABILITY_PATIENT_CONTEXT,
                            ChatSubAgent.CAPABILITY_PATIENT_PROFILE,
                            ChatSubAgent.CAPABILITY_MEDPLUM_INSIGHT
                    );
            case "medical_evidence_search", "medical_evidence", "evidence", "guideline", "citation" ->
                    List.of(ChatSubAgent.CAPABILITY_MEDICAL_EVIDENCE);
            case "knowledge_graph_support", "knowledge_graph", "graph" ->
                    List.of(ChatSubAgent.CAPABILITY_KNOWLEDGE_GRAPH);
            case "risk_assessment", "risk", "triage", "stratification" ->
                    List.of(ChatSubAgent.CAPABILITY_RISK_ASSESSMENT);
            default -> List.of(normalized);
        };
    }

    private Set<String> collectAvoidedAgents(OrchestrationTaskSnapshot snapshot, Map<String, Object> subTask) {
        if (snapshot == null || snapshot.getEvents() == null || snapshot.getEvents().isEmpty()) {
            return Set.of();
        }
        String subTaskId = stringValue(subTask.get("subTaskId"));
        List<Map<String, Object>> events = new ArrayList<>(snapshot.getEvents());
        Collections.reverse(events);
        Set<String> avoided = new LinkedHashSet<>();
        for (Map<String, Object> event : events) {
            if (!(event.get("type") instanceof String typeText)) {
                continue;
            }
            if (!isAvoidanceEvent(typeText)) {
                continue;
            }
            String eventSubTaskId = metadataValue(event, "subTaskId");
            if (subTaskId != null && !subTaskId.isBlank() && eventSubTaskId != null && !subTaskId.equals(eventSubTaskId)) {
                continue;
            }
            String agent = trackedEventAgent(event);
            if (agent != null && !agent.isBlank()) {
                avoided.add(agent);
            }
            if (avoided.size() >= 2) {
                break;
            }
        }
        return avoided;
    }

    private String recentFailureCategory(OrchestrationTaskSnapshot snapshot, Map<String, Object> subTask) {
        if (snapshot == null || snapshot.getEvents() == null || snapshot.getEvents().isEmpty()) {
            return null;
        }
        String subTaskId = stringValue(subTask.get("subTaskId"));
        List<Map<String, Object>> events = new ArrayList<>(snapshot.getEvents());
        Collections.reverse(events);
        for (Map<String, Object> event : events) {
            if (!(event.get("type") instanceof String typeText) || !isAvoidanceEvent(typeText)) {
                continue;
            }
            String eventSubTaskId = metadataValue(event, "subTaskId");
            if (subTaskId != null && !subTaskId.isBlank() && eventSubTaskId != null && !subTaskId.equals(eventSubTaskId)) {
                continue;
            }
            String failureCategory = metadataValue(event, "failureCategory");
            if (failureCategory != null && !failureCategory.isBlank()) {
                return failureCategory;
            }
        }
        return null;
    }

    private List<String> recentMissingCapabilities(OrchestrationTaskSnapshot snapshot, Map<String, Object> subTask) {
        if (snapshot == null || snapshot.getEvents() == null || snapshot.getEvents().isEmpty()) {
            return List.of();
        }
        String subTaskId = stringValue(subTask.get("subTaskId"));
        List<Map<String, Object>> events = new ArrayList<>(snapshot.getEvents());
        Collections.reverse(events);
        for (Map<String, Object> event : events) {
            if (!(event.get("type") instanceof String typeText) || !isAvoidanceEvent(typeText)) {
                continue;
            }
            String eventSubTaskId = metadataValue(event, "subTaskId");
            if (subTaskId != null && !subTaskId.isBlank() && eventSubTaskId != null && !subTaskId.equals(eventSubTaskId)) {
                continue;
            }
            List<String> capabilities = metadataArray(event, "missingCapabilities");
            if (!capabilities.isEmpty()) {
                return capabilities;
            }
        }
        return List.of();
    }

    private boolean isAvoidanceEvent(String typeText) {
        return "FAILED".equals(typeText)
                || "BLOCKED".equals(typeText)
                || "SUBTASK_HANDOFF".equals(typeText)
                || "SUBTASK_SKIPPED".equals(typeText);
    }

    private String metadataValue(Map<String, Object> event, String key) {
        if (event == null || !(event.get("metadata") instanceof Map<?, ?> metadata)) {
            return null;
        }
        Object value = metadata.get(key);
        return stringValue(value);
    }

    private String trackedEventAgent(Map<String, Object> event) {
        String resolvedAgent = metadataValue(event, "resolvedAgentName");
        if (resolvedAgent != null && !resolvedAgent.isBlank()) {
            return resolvedAgent;
        }
        return metadataValue(event, "targetAgent");
    }

    private List<String> metadataArray(Map<String, Object> event, String key) {
        if (event == null || !(event.get("metadata") instanceof Map<?, ?> metadata)) {
            return List.of();
        }
        Object value = metadata.get(key);
        if (!(value instanceof List<?> items)) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        for (Object item : items) {
            String text = stringValue(item);
            if (text != null && !text.isBlank()) {
                values.add(text);
            }
        }
        return values;
    }

    private boolean containsAny(String text, String... patterns) {
        for (String pattern : patterns) {
            if (text.contains(pattern)) {
                return true;
            }
        }
        return false;
    }

    private String stringValue(Object value) {
        return value == null ? null : value.toString();
    }

    private int integerValue(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null) {
            return 0;
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private double doubleValue(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value == null) {
            return 0D;
        }
        try {
            return Double.parseDouble(value.toString());
        } catch (NumberFormatException ignored) {
            return 0D;
        }
    }

    private long longValue(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value == null) {
            return 0L;
        }
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException ignored) {
            return 0L;
        }
    }

    private record RoutingWeightProfile(String taskType,
                                        int missingCapabilityRoute,
                                        int failureCategoryRoute,
                                        int goalSemanticRoute,
                                        double healthWeight) {

        private Map<String, Object> asMap() {
            return Map.of(
                    "taskType", taskType,
                    "missingCapabilityRoute", missingCapabilityRoute,
                    "failureCategoryRoute", failureCategoryRoute,
                    "goalSemanticRoute", goalSemanticRoute,
                    "healthWeight", healthWeight
            );
        }
    }
}
