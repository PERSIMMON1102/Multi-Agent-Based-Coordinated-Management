package com.medical.agent.service.react.agent;

import com.medical.agent.service.react.orchestration.OrchestrationPersistenceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class SubAgentRegistry {

    private static final int EXECUTION_HEALTH_LOOKBACK_HOURS = 24;
    public static final String HEALTH_STATE_HEALTHY = "healthy";
    public static final String HEALTH_STATE_DEGRADED = "degraded";
    public static final String HEALTH_STATE_QUARANTINED = "quarantined";
    public static final String HEALTH_STATE_UNKNOWN = "unknown";
    private static final int DEGRADED_EXECUTION_MIN = 3;
    private static final int QUARANTINED_EXECUTION_MIN = 6;
    private static final double DEGRADED_SUCCESS_RATE_THRESHOLD = 0.7D;
    private static final double DEGRADED_STABILITY_THRESHOLD = 0.2D;
    private static final double QUARANTINED_SUCCESS_RATE_THRESHOLD = 0.35D;
    private static final double QUARANTINED_STABILITY_THRESHOLD = -0.15D;
    private static final int RECENT_FAILURE_STREAK_DEGRADED_THRESHOLD = 2;
    private static final int RECENT_FAILURE_STREAK_QUARANTINED_THRESHOLD = 3;
    private static final int RECENT_FAILURE_STREAK_WINDOW_MINUTES = 30;

    private final Map<String, ChatSubAgent> subAgentsByName;
    private final Map<String, List<ChatSubAgent>> subAgentsByRoute;
    private final OrchestrationPersistenceService orchestrationPersistenceService;

    public SubAgentRegistry(List<ChatSubAgent> subAgents) {
        this(subAgents, null);
    }

    @Autowired
    public SubAgentRegistry(List<ChatSubAgent> subAgents,
                            OrchestrationPersistenceService orchestrationPersistenceService) {
        this.subAgentsByName = subAgents.stream().collect(Collectors.toMap(ChatSubAgent::name, Function.identity(), (left, right) -> left, LinkedHashMap::new));
        this.subAgentsByRoute = subAgents.stream()
                .collect(Collectors.groupingBy(agent -> normalize(agent.routeKey()), LinkedHashMap::new, Collectors.toList()));
        this.orchestrationPersistenceService = orchestrationPersistenceService;
    }

    public ChatSubAgent getAgent(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        ChatSubAgent direct = subAgentsByName.get(name);
        if (direct != null) {
            return direct;
        }
        return getPrimaryAgent(name);
    }

    public boolean hasAgent(String name) {
        return getAgent(name) != null;
    }

    public boolean hasRoute(String routeKey) {
        return routeKey != null && subAgentsByRoute.containsKey(normalize(routeKey));
    }

    public ChatSubAgent getPrimaryAgent(String routeKey) {
        if (routeKey == null || routeKey.isBlank()) {
            return null;
        }
        return getPrimaryAgent(routeKey, executionHealthStats());
    }

    public List<String> listAgentNames() {
        return listRoutes();
    }

    public List<String> listRoutes() {
        return subAgentsByRoute.keySet().stream()
                .sorted()
                .toList();
    }

    public List<ChatSubAgent> listRouteImplementations(String routeKey) {
        return listRouteImplementations(routeKey, executionHealthStats());
    }

    public List<ChatSubAgent> listRouteImplementations(String routeKey, Map<String, Map<String, Object>> executionHealthStats) {
        if (routeKey == null || routeKey.isBlank()) {
            return List.of();
        }
        return subAgentsByRoute.getOrDefault(normalize(routeKey), List.of()).stream()
                .sorted(routeImplementationComparator(executionHealthStats))
                .toList();
    }

    public List<ChatSubAgent> listAgentsByCapability(String capability) {
        if (capability == null || capability.isBlank()) {
            return List.of();
        }
        return listAgents().stream()
                .filter(agent -> agent.capabilities().stream().anyMatch(item -> capability.equalsIgnoreCase(item)))
                .toList();
    }

    public List<ChatSubAgent> listAgents() {
        return subAgentsByName.values().stream()
                .sorted(Comparator.comparing(ChatSubAgent::name))
                .toList();
    }

    public List<ChatSubAgent> listPrimaryAgents() {
        Map<String, Map<String, Object>> executionHealthStats = executionHealthStats();
        return listRoutes().stream()
                .map(routeKey -> getPrimaryAgent(routeKey, executionHealthStats))
                .filter(agent -> agent != null)
                .sorted(Comparator.comparing(ChatSubAgent::routeKey))
                .toList();
    }

    public String implementationHealthState(String agentName) {
        return implementationHealthState(agentName, executionHealthStats());
    }

    public String implementationHealthState(String agentName, Map<String, Map<String, Object>> executionHealthStats) {
        ChatSubAgent agent = getAgent(agentName);
        if (agent == null) {
            return HEALTH_STATE_UNKNOWN;
        }
        return implementationHealthState(agent, executionHealthStats);
    }

    public boolean isPrimaryImplementation(String agentName) {
        return isPrimaryImplementation(agentName, executionHealthStats());
    }

    public boolean isPrimaryImplementation(String agentName, Map<String, Map<String, Object>> executionHealthStats) {
        ChatSubAgent agent = getAgent(agentName);
        if (agent == null) {
            return false;
        }
        ChatSubAgent primary = getPrimaryAgent(agent.routeKey(), executionHealthStats);
        return primary != null && primary.name().equals(agent.name());
    }

    public String providerHealthState(String provider) {
        return providerHealthState(provider, executionHealthStats());
    }

    public String providerHealthState(String provider, Map<String, Map<String, Object>> executionHealthStats) {
        if (provider == null || provider.isBlank()) {
            return HEALTH_STATE_UNKNOWN;
        }
        ProviderHealthAggregate aggregate = providerHealthStats(executionHealthStats).get(normalize(provider));
        return aggregate == null ? HEALTH_STATE_UNKNOWN : aggregate.healthState();
    }

    public int providerHealthScore(String provider, Map<String, Map<String, Object>> executionHealthStats) {
        if (provider == null || provider.isBlank()) {
            return 0;
        }
        ProviderHealthAggregate aggregate = providerHealthStats(executionHealthStats).get(normalize(provider));
        if (aggregate == null || aggregate.executionCount() <= 0) {
            return 0;
        }
        return (int) Math.round((aggregate.successRate() * 80D) + (aggregate.stabilityScore() * 55D));
    }

    private ChatSubAgent getPrimaryAgent(String routeKey, Map<String, Map<String, Object>> executionHealthStats) {
        return listRouteImplementations(routeKey, executionHealthStats).stream().findFirst().orElse(null);
    }

    private Comparator<ChatSubAgent> routeImplementationComparator(Map<String, Map<String, Object>> executionHealthStats) {
        Map<String, ProviderHealthAggregate> providerHealthStats = providerHealthStats(executionHealthStats);
        return Comparator
                .comparingInt((ChatSubAgent agent) -> routeImplementationHealthStateRank(agent, executionHealthStats)).reversed()
                .thenComparing(Comparator.comparingInt((ChatSubAgent agent) -> providerHealthStateRank(agent.provider(), providerHealthStats)).reversed())
                .thenComparing(Comparator.comparingInt((ChatSubAgent agent) -> routeImplementationHealthScore(agent, executionHealthStats)).reversed())
                .thenComparing(Comparator.comparingInt((ChatSubAgent agent) -> providerHealthScoreFromAggregates(agent.provider(), providerHealthStats)).reversed())
                .thenComparing(Comparator.comparingInt(ChatSubAgent::baselineHealthScore).reversed())
                .thenComparingInt(ChatSubAgent::estimatedCostScore)
                .thenComparing(ChatSubAgent::provider)
                .thenComparing(ChatSubAgent::name);
    }

    private int routeImplementationHealthScore(ChatSubAgent agent, Map<String, Map<String, Object>> executionHealthStats) {
        if (agent == null || executionHealthStats == null || executionHealthStats.isEmpty()) {
            return 0;
        }
        Map<String, Object> stats = executionHealthStats.getOrDefault(normalize(agent.name()), Map.of());
        int executionCount = integerValue(stats.get("executionCount"));
        if (executionCount <= 0) {
            return 0;
        }
        double successRate = doubleValue(stats.get("successRate"));
        double stabilityScore = doubleValue(stats.get("stabilityScore"));
        double recentExecutionHealth = recencyWeightedExecutionHealth(stats.get("recent24hExecutionTrend"));
        double volumeConfidence = Math.min(executionCount, 6) * 2D;
        return (int) Math.round((successRate * 140D) + (stabilityScore * 80D) + recentExecutionHealth + volumeConfidence);
    }

    private int routeImplementationHealthStateRank(ChatSubAgent agent, Map<String, Map<String, Object>> executionHealthStats) {
        return switch (implementationHealthState(agent, executionHealthStats)) {
            case HEALTH_STATE_HEALTHY -> 3;
            case HEALTH_STATE_UNKNOWN -> 2;
            case HEALTH_STATE_DEGRADED -> 1;
            case HEALTH_STATE_QUARANTINED -> 0;
            default -> 0;
        };
    }

    private int providerHealthStateRank(String provider, Map<String, ProviderHealthAggregate> providerHealthStats) {
        if (provider == null || provider.isBlank() || providerHealthStats == null || providerHealthStats.isEmpty()) {
            return 2;
        }
        ProviderHealthAggregate aggregate = providerHealthStats.get(normalize(provider));
        if (aggregate == null) {
            return 2;
        }
        return switch (aggregate.healthState()) {
            case HEALTH_STATE_HEALTHY -> 3;
            case HEALTH_STATE_UNKNOWN -> 2;
            case HEALTH_STATE_DEGRADED -> 1;
            case HEALTH_STATE_QUARANTINED -> 0;
            default -> 0;
        };
    }

    private String implementationHealthState(ChatSubAgent agent, Map<String, Map<String, Object>> executionHealthStats) {
        if (agent == null || executionHealthStats == null || executionHealthStats.isEmpty()) {
            return HEALTH_STATE_UNKNOWN;
        }
        Map<String, Object> stats = executionHealthStats.getOrDefault(normalize(agent.name()), Map.of());
        int executionCount = integerValue(stats.get("executionCount"));
        double successRate = doubleValue(stats.get("successRate"));
        double stabilityScore = doubleValue(stats.get("stabilityScore"));
        int recentConsecutiveFailureCount = integerValue(stats.get("recentConsecutiveFailureCount"));
        LocalDateTime lastExecutionAt = localDateTimeValue(stats.get("lastExecutionAt"));
        return classifyImplementationHealthState(
                executionCount,
                successRate,
                stabilityScore,
                recentConsecutiveFailureCount,
                lastExecutionAt
        );
    }

    public static String classifyImplementationHealthState(int executionCount,
                                                           double successRate,
                                                           double stabilityScore) {
        return classifyImplementationHealthState(executionCount, successRate, stabilityScore, 0, null);
    }

    private static String classifyImplementationHealthState(int executionCount,
                                                            double successRate,
                                                            double stabilityScore,
                                                            int recentConsecutiveFailureCount,
                                                            LocalDateTime lastExecutionAt) {
        if (executionCount <= 0) {
            return HEALTH_STATE_UNKNOWN;
        }
        if (hasActiveFailureCooldown(
                recentConsecutiveFailureCount,
                lastExecutionAt,
                RECENT_FAILURE_STREAK_QUARANTINED_THRESHOLD
        )) {
            return HEALTH_STATE_QUARANTINED;
        }
        if (hasActiveFailureCooldown(
                recentConsecutiveFailureCount,
                lastExecutionAt,
                RECENT_FAILURE_STREAK_DEGRADED_THRESHOLD
        )) {
            return HEALTH_STATE_DEGRADED;
        }
        if (executionCount >= QUARANTINED_EXECUTION_MIN
                && (successRate < QUARANTINED_SUCCESS_RATE_THRESHOLD || stabilityScore < QUARANTINED_STABILITY_THRESHOLD)) {
            return HEALTH_STATE_QUARANTINED;
        }
        if (executionCount >= DEGRADED_EXECUTION_MIN
                && (successRate < DEGRADED_SUCCESS_RATE_THRESHOLD || stabilityScore < DEGRADED_STABILITY_THRESHOLD)) {
            return HEALTH_STATE_DEGRADED;
        }
        return HEALTH_STATE_HEALTHY;
    }

    private Map<String, ProviderHealthAggregate> providerHealthStats(Map<String, Map<String, Object>> executionHealthStats) {
        if (executionHealthStats == null || executionHealthStats.isEmpty()) {
            return Map.of();
        }
        LinkedHashMap<String, ProviderHealthAccumulator> accumulators = new LinkedHashMap<>();
        for (ChatSubAgent agent : listAgents()) {
            if (agent.provider() == null || agent.provider().isBlank()) {
                continue;
            }
            Map<String, Object> stats = executionHealthStats.getOrDefault(normalize(agent.name()), Map.of());
            int executionCount = integerValue(stats.get("executionCount"));
            if (executionCount <= 0) {
                continue;
            }
            accumulators.computeIfAbsent(normalize(agent.provider()), ignored -> new ProviderHealthAccumulator())
                    .record(
                            executionCount,
                            doubleValue(stats.get("successRate")),
                            doubleValue(stats.get("stabilityScore")),
                            implementationHealthState(agent, executionHealthStats),
                            integerValue(stats.get("recentConsecutiveFailureCount")),
                            localDateTimeValue(stats.get("lastExecutionAt"))
                    );
        }
        LinkedHashMap<String, ProviderHealthAggregate> payload = new LinkedHashMap<>();
        accumulators.forEach((provider, accumulator) -> payload.put(provider, accumulator.toAggregate()));
        return payload;
    }

    private int providerHealthScoreFromAggregates(String provider, Map<String, ProviderHealthAggregate> providerHealthStats) {
        if (provider == null || provider.isBlank() || providerHealthStats == null || providerHealthStats.isEmpty()) {
            return 0;
        }
        ProviderHealthAggregate aggregate = providerHealthStats.get(normalize(provider));
        if (aggregate == null || aggregate.executionCount() <= 0) {
            return 0;
        }
        return (int) Math.round((aggregate.successRate() * 80D) + (aggregate.stabilityScore() * 55D));
    }

    private double recencyWeightedExecutionHealth(Object trend) {
        if (!(trend instanceof List<?> items) || items.isEmpty()) {
            return 0D;
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
            double stability = ((completed * 1.0D) - (failed * 1.2D) - (blocked * 0.6D) - (rerouted * 0.4D)) / total;
            double weight = recencyWeight(items.size(), index);
            weightedScore += (((successRate * 40D) + (stability * 25D)) * weight);
            totalWeight += weight;
        }
        if (totalWeight <= 0D) {
            return 0D;
        }
        return weightedScore / totalWeight;
    }

    private double recencyWeight(int totalBuckets, int index) {
        int distanceFromLatest = Math.max((totalBuckets - 1) - index, 0);
        return switch (distanceFromLatest) {
            case 0 -> 1.0D;
            case 1 -> 0.6D;
            case 2 -> 0.35D;
            default -> 0.15D;
        };
    }

    private Map<String, Map<String, Object>> executionHealthStats() {
        if (orchestrationPersistenceService == null) {
            return Map.of();
        }
        Map<String, Map<String, Object>> stats = new LinkedHashMap<>();
        for (Map<String, Object> item : orchestrationPersistenceService.summarizeRecentAgentExecutionHealth(EXECUTION_HEALTH_LOOKBACK_HOURS)) {
            String agent = normalize(stringValue(item.get("agent")));
            if (!agent.isBlank()) {
                stats.put(agent, item);
            }
        }
        return stats;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase();
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

    private LocalDateTime localDateTimeValue(Object value) {
        if (value instanceof LocalDateTime time) {
            return time;
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return LocalDateTime.parse(text.trim());
            } catch (Exception ignored) {
                return null;
            }
        }
        return null;
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

    private static boolean hasActiveFailureCooldown(int recentConsecutiveFailureCount,
                                                    LocalDateTime lastExecutionAt,
                                                    int threshold) {
        return recentConsecutiveFailureCount >= threshold
                && lastExecutionAt != null
                && !lastExecutionAt.isBefore(LocalDateTime.now().minusMinutes(RECENT_FAILURE_STREAK_WINDOW_MINUTES));
    }

    private record ProviderHealthAggregate(int executionCount,
                                           double successRate,
                                           double stabilityScore,
                                           String healthState,
                                           int maxRecentFailureStreak,
                                           LocalDateTime latestFailureStreakAt) {
    }

    private static final class ProviderHealthAccumulator {
        private int executionCount;
        private double weightedSuccessRate;
        private double weightedStabilityScore;
        private int healthyImplementations;
        private int degradedImplementations;
        private int quarantinedImplementations;
        private int maxRecentFailureStreak;
        private LocalDateTime latestFailureStreakAt;

        private void record(int count,
                            double successRate,
                            double stabilityScore,
                            String implementationHealthState,
                            int recentConsecutiveFailureCount,
                            LocalDateTime lastExecutionAt) {
            executionCount += count;
            weightedSuccessRate += successRate * count;
            weightedStabilityScore += stabilityScore * count;
            if (HEALTH_STATE_HEALTHY.equals(implementationHealthState)) {
                healthyImplementations++;
            } else if (HEALTH_STATE_DEGRADED.equals(implementationHealthState)) {
                degradedImplementations++;
            } else if (HEALTH_STATE_QUARANTINED.equals(implementationHealthState)) {
                quarantinedImplementations++;
            }
            if (recentConsecutiveFailureCount > maxRecentFailureStreak
                    || (recentConsecutiveFailureCount == maxRecentFailureStreak
                    && lastExecutionAt != null
                    && (latestFailureStreakAt == null || lastExecutionAt.isAfter(latestFailureStreakAt)))) {
                maxRecentFailureStreak = recentConsecutiveFailureCount;
                latestFailureStreakAt = lastExecutionAt;
            }
        }

        private ProviderHealthAggregate toAggregate() {
            if (executionCount <= 0) {
                return new ProviderHealthAggregate(0, 0D, 0D, HEALTH_STATE_UNKNOWN, 0, null);
            }
            double aggregateSuccessRate = weightedSuccessRate / executionCount;
            double aggregateStabilityScore = weightedStabilityScore / executionCount;
            String baseHealthState = classifyImplementationHealthState(executionCount, aggregateSuccessRate, aggregateStabilityScore);
            return new ProviderHealthAggregate(
                    executionCount,
                    aggregateSuccessRate,
                    aggregateStabilityScore,
                    resolveProviderHealthState(baseHealthState),
                    maxRecentFailureStreak,
                    latestFailureStreakAt
            );
        }

        private String resolveProviderHealthState(String baseHealthState) {
            boolean activeQuarantineCooldown = hasActiveFailureCooldown(
                    maxRecentFailureStreak,
                    latestFailureStreakAt,
                    RECENT_FAILURE_STREAK_QUARANTINED_THRESHOLD
            );
            boolean activeDegradedCooldown = hasActiveFailureCooldown(
                    maxRecentFailureStreak,
                    latestFailureStreakAt,
                    RECENT_FAILURE_STREAK_DEGRADED_THRESHOLD
            );
            if (activeQuarantineCooldown || quarantinedImplementations > 0) {
                return healthyImplementations > 0 ? HEALTH_STATE_DEGRADED : HEALTH_STATE_QUARANTINED;
            }
            if (activeDegradedCooldown || degradedImplementations > 0) {
                return HEALTH_STATE_DEGRADED;
            }
            return baseHealthState;
        }
    }
}
