package com.medical.agent.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.medical.agent.model.entity.HealthEducationPlan;
import com.medical.agent.model.repository.HealthEducationPlanRepository;
import com.medical.agent.service.education.agent.HealthEducationSupervisorService;
import com.medical.agent.service.testsupport.RepositoryProxyFactory;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class HealthEducationServiceTest {

    @Test
    void shouldPersistNewestEducationResultFirst() {
        AtomicLong idGenerator = new AtomicLong();
        List<HealthEducationPlan> storedPlans = new ArrayList<>();
        HealthEducationPlanRepository repository = createRepository(storedPlans, idGenerator);

        HealthEducationService service = new HealthEducationService(
                new StubSupervisorService(),
                repository,
                new ObjectMapper()
        );

        Map<String, Object> first = service.generateEducation(1L);
        Map<String, Object> second = service.generateEducation(1L);

        List<Map<String, Object>> history = service.getByPatient(1L);
        assertEquals("第2次饮食建议", second.get("diet"));
        assertEquals(2, history.size());
        assertEquals("第2次饮食建议", history.get(0).get("diet"));
        assertEquals("第1次饮食建议", history.get(1).get("diet"));
        assertEquals("测试患者", history.get(0).get("patientName"));
        assertEquals(List.of("高血压"), history.get(0).get("diseases"));

        Object steps = history.get(0).get("agentSteps");
        assertInstanceOf(List.class, steps);
        assertEquals("diet_education_agent", ((Map<?, ?>) ((List<?>) steps).get(0)).get("agentName"));
        assertEquals("第1次饮食建议", first.get("diet"));
    }

    @Test
    void shouldTrimEducationHistoryBeyondLimit() {
        AtomicLong idGenerator = new AtomicLong();
        List<HealthEducationPlan> storedPlans = new ArrayList<>();
        HealthEducationPlanRepository repository = createRepository(storedPlans, idGenerator);

        HealthEducationService service = new HealthEducationService(
                new StubSupervisorService(),
                repository,
                new ObjectMapper()
        );

        for (int i = 0; i < 14; i++) {
            service.generateEducation(1L);
        }

        List<Map<String, Object>> history = service.getByPatient(1L);
        assertEquals(12, history.size());
        assertEquals("第14次饮食建议", history.get(0).get("diet"));
        assertEquals("第3次饮食建议", history.get(11).get("diet"));
    }

    @Test
    void shouldLimitEducationHistoryQuery() {
        AtomicLong idGenerator = new AtomicLong();
        List<HealthEducationPlan> storedPlans = new ArrayList<>();
        HealthEducationPlanRepository repository = createRepository(storedPlans, idGenerator);

        for (int i = 1; i <= 5; i++) {
            storedPlans.add(HealthEducationPlan.builder()
                    .id(idGenerator.incrementAndGet())
                    .planId("plan-" + i)
                    .patientId(i <= 3 ? 1L : 2L)
                    .patientName(i <= 3 ? "甲" : "乙")
                    .generatedAt(LocalDateTime.of(2026, 4, i, 10, 0))
                    .diet("饮食建议" + i)
                    .build());
        }

        HealthEducationService service = new HealthEducationService(
                new StubSupervisorService(),
                repository,
                new ObjectMapper()
        );

        List<Map<String, Object>> history = service.getByPatient(1L, 2);
        assertEquals(2, history.size());
        assertEquals(1L, history.get(0).get("patientId"));
        assertEquals("饮食建议3", history.get(0).get("diet"));
        assertEquals("饮食建议2", history.get(1).get("diet"));
    }

    private HealthEducationPlanRepository createRepository(List<HealthEducationPlan> storedPlans, AtomicLong idGenerator) {
        Map<String, java.util.function.Function<Object[], Object>> handlers = new LinkedHashMap<>();
        handlers.put("save", args -> {
            HealthEducationPlan plan = (HealthEducationPlan) args[0];
            if (plan.getId() == null) {
                plan.setId(idGenerator.incrementAndGet());
            }
            if (plan.getGeneratedAt() == null) {
                plan.setGeneratedAt(LocalDateTime.now());
            }
            storedPlans.removeIf(item -> Objects.equals(item.getId(), plan.getId()));
            storedPlans.add(plan);
            return plan;
        });
        handlers.put("findByPatientIdOrderByGeneratedAtDesc", args -> {
            long patientId = Long.parseLong(String.valueOf(args[0]));
            long limit = args.length > 1 && args[1] instanceof Pageable pageable ? pageable.getPageSize() : Long.MAX_VALUE;
            return storedPlans.stream()
                    .filter(plan -> Objects.equals(plan.getPatientId(), patientId))
                    .sorted(Comparator.comparing(HealthEducationPlan::getGeneratedAt).reversed())
                    .limit(limit)
                    .toList();
        });
        handlers.put("deleteAll", args -> {
            @SuppressWarnings("unchecked")
            Iterable<HealthEducationPlan> plans = (Iterable<HealthEducationPlan>) args[0];
            for (HealthEducationPlan plan : plans) {
                storedPlans.removeIf(item -> Objects.equals(item.getId(), plan.getId()));
            }
            return null;
        });
        return RepositoryProxyFactory.create(HealthEducationPlanRepository.class, handlers);
    }

    private static class StubSupervisorService extends HealthEducationSupervisorService {
        private final AtomicInteger counter = new AtomicInteger();

        StubSupervisorService() {
            super(null, null, null, null, List.of());
        }

        @Override
        public Map<String, Object> generate(Long patientId) {
            int value = counter.incrementAndGet();
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("planId", "plan-" + value);
            result.put("patientId", patientId);
            result.put("patientName", "测试患者");
            result.put("generatedAt", String.format("2026-04-%02dT10:00:00", value));
            result.put("diseases", List.of("高血压"));
            result.put("diet", "第" + value + "次饮食建议");
            result.put("exercise", "第" + value + "次运动建议");
            result.put("lifestyle", "第" + value + "次生活方式建议");
            result.put("dietReferences", List.of(Map.of("title", "饮食参考" + value)));
            result.put("exerciseReferences", List.of());
            result.put("lifestyleReferences", List.of());
            result.put("references", List.of(Map.of("title", "综合参考" + value)));
            result.put("recommendedVideos", List.of(Map.of("title", "视频" + value)));
            result.put("agentSteps", List.of(Map.of("index", 1, "agentName", "diet_education_agent", "summary", "完成饮食方案")));
            return result;
        }
    }
}
