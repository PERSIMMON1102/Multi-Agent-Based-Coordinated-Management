package com.medical.agent.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.medical.agent.model.entity.HealthEducationPlan;
import com.medical.agent.model.repository.HealthEducationPlanRepository;
import com.medical.agent.service.education.agent.HealthEducationSupervisorService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * AI健康宣教服务 - 根据患者慢病类型生成个性化饮食/运动/作息方案
 */
@Service
public class HealthEducationService {
    private static final int DEFAULT_HISTORY_LIMIT = 12;
    private static final int MAX_HISTORY_QUERY_LIMIT = 50;
    private static final int MAX_HISTORY_PER_PATIENT = 12;

    private final HealthEducationSupervisorService healthEducationSupervisorService;
    private final HealthEducationPlanRepository healthEducationPlanRepository;
    private final ObjectMapper objectMapper;

    @Autowired
    public HealthEducationService(HealthEducationSupervisorService healthEducationSupervisorService,
                                  HealthEducationPlanRepository healthEducationPlanRepository,
                                  ObjectMapper objectMapper) {
        this.healthEducationSupervisorService = healthEducationSupervisorService;
        this.healthEducationPlanRepository = healthEducationPlanRepository;
        this.objectMapper = objectMapper;
    }

    protected HealthEducationService(HealthEducationSupervisorService healthEducationSupervisorService) {
        this(healthEducationSupervisorService, null, new ObjectMapper());
    }

    /**
     * 为患者生成个性化健康宣教方案
     */
    @Transactional
    public Map<String, Object> generateEducation(Long patientId) {
        Map<String, Object> generatedPlan = healthEducationSupervisorService.generate(patientId);
        HealthEducationPlan savedPlan = healthEducationPlanRepository.save(toEntity(generatedPlan));
        trimHistory(patientId);
        return toMap(savedPlan);
    }

    /**
     * 获取患者的历史宣教记录
     */
    public List<Map<String, Object>> getByPatient(Long patientId) {
        return healthEducationPlanRepository.findByPatientIdOrderByGeneratedAtDesc(patientId)
                .stream()
                .map(this::toMap)
                .toList();
    }

    public List<Map<String, Object>> getByPatient(Long patientId, Integer limit) {
        int queryLimit = normalizeLimit(limit);
        return healthEducationPlanRepository.findByPatientIdOrderByGeneratedAtDesc(patientId, PageRequest.of(0, queryLimit))
                .stream()
                .map(this::toMap)
                .toList();
    }

    private HealthEducationPlan toEntity(Map<String, Object> generatedPlan) {
        return HealthEducationPlan.builder()
                .planId(asText(generatedPlan.get("planId"), UUID.randomUUID().toString()))
                .patientId(asLong(generatedPlan.get("patientId")))
                .patientName(asText(generatedPlan.get("patientName"), null))
                .generatedAt(parseDateTime(generatedPlan.get("generatedAt")))
                .diseasesJson(toJson(generatedPlan.get("diseases")))
                .diet(asText(generatedPlan.get("diet"), null))
                .exercise(asText(generatedPlan.get("exercise"), null))
                .lifestyle(asText(generatedPlan.get("lifestyle"), null))
                .dietReferencesJson(toJson(generatedPlan.get("dietReferences")))
                .exerciseReferencesJson(toJson(generatedPlan.get("exerciseReferences")))
                .lifestyleReferencesJson(toJson(generatedPlan.get("lifestyleReferences")))
                .referencesJson(toJson(generatedPlan.get("references")))
                .recommendedVideosJson(toJson(generatedPlan.get("recommendedVideos")))
                .agentTraceJson(toJson(generatedPlan.get("agentSteps")))
                .build();
    }

    private void trimHistory(Long patientId) {
        List<HealthEducationPlan> history = healthEducationPlanRepository.findByPatientIdOrderByGeneratedAtDesc(patientId);
        if (history.size() <= MAX_HISTORY_PER_PATIENT) {
            return;
        }
        healthEducationPlanRepository.deleteAll(history.subList(MAX_HISTORY_PER_PATIENT, history.size()));
    }

    private int normalizeLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return DEFAULT_HISTORY_LIMIT;
        }
        return Math.min(limit, MAX_HISTORY_QUERY_LIMIT);
    }

    private Map<String, Object> toMap(HealthEducationPlan plan) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("planId", plan.getPlanId());
        result.put("patientId", plan.getPatientId());
        result.put("patientName", plan.getPatientName());
        result.put("generatedAt", plan.getGeneratedAt() != null ? plan.getGeneratedAt().toString() : null);
        result.put("diseases", readStringList(plan.getDiseasesJson()));
        result.put("diet", plan.getDiet());
        result.put("exercise", plan.getExercise());
        result.put("lifestyle", plan.getLifestyle());
        result.put("dietReferences", readMapList(plan.getDietReferencesJson()));
        result.put("exerciseReferences", readMapList(plan.getExerciseReferencesJson()));
        result.put("lifestyleReferences", readMapList(plan.getLifestyleReferencesJson()));
        result.put("references", readMapList(plan.getReferencesJson()));
        result.put("recommendedVideos", readMapList(plan.getRecommendedVideosJson()));
        result.put("agentSteps", readMapList(plan.getAgentTraceJson()));
        return result;
    }

    private String toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return null;
        }
    }

    private List<String> readStringList(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            return List.of();
        }
    }

    private List<Map<String, Object>> readMapList(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<Map<String, Object>>>() {});
        } catch (Exception e) {
            return List.of();
        }
    }

    private LocalDateTime parseDateTime(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof LocalDateTime localDateTime) {
            return localDateTime;
        }
        try {
            return LocalDateTime.parse(String.valueOf(value));
        } catch (Exception e) {
            return LocalDateTime.now();
        }
    }

    private String asText(Object value, String fallback) {
        return value == null ? fallback : String.valueOf(value);
    }

    private Long asLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.valueOf(String.valueOf(value));
    }
}
