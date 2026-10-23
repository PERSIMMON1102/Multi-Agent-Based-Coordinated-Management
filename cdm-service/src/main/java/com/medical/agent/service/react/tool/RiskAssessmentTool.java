package com.medical.agent.service.react.tool;

import com.medical.agent.common.enums.DiseaseType;
import com.medical.agent.common.enums.RiskLevel;
import com.medical.agent.model.entity.HealthRecord;
import com.medical.agent.service.chat.ChatContextService;
import com.medical.agent.service.chat.ChatPatientContextData;
import com.medical.agent.service.chat.RuleAssessmentResult;
import com.medical.agent.service.chat.RuleAssessmentService;
import com.medical.agent.service.react.model.AgentState;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.springframework.beans.BeanWrapper;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.beans.PropertyDescriptor;
import java.util.List;
import java.util.Locale;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class RiskAssessmentTool implements AgentTool {

    private static final Set<String> HEALTH_RECORD_PROPERTIES = java.util.Arrays.stream(
                    new BeanWrapperImpl(HealthRecord.class).getPropertyDescriptors()
            )
            .map(PropertyDescriptor::getName)
            .filter(name -> !"class".equals(name))
            .collect(Collectors.toUnmodifiableSet());

    private final ChatContextService chatContextService;
    private final RuleAssessmentService ruleAssessmentService;
    private final ObjectMapper objectMapper;

    @Autowired
    public RiskAssessmentTool(ChatContextService chatContextService,
                              RuleAssessmentService ruleAssessmentService) {
        this(chatContextService, ruleAssessmentService, JsonMapper.builder().findAndAddModules().build());
    }

    RiskAssessmentTool(ChatContextService chatContextService,
                       RuleAssessmentService ruleAssessmentService,
                       ObjectMapper objectMapper) {
        this.chatContextService = chatContextService;
        this.ruleAssessmentService = ruleAssessmentService;
        this.objectMapper = objectMapper;
    }

    @Override
    public String name() {
        return "risk_assessment";
    }

    @Override
    public String description() {
        return "调用同步 Drools 规则评估，输出指定或推断病种的风险等级、评估依据和管理建议。适用于血压、血糖、风险、是否严重等问题。";
    }

    @Override
    public ToolExecutionResult execute(AgentState state, Map<String, Object> input) {
        if (state.getPatientId() == null) {
            return new ToolExecutionResult(name(), false, "当前未绑定患者，无法执行患者级风险评估。");
        }
        ChatPatientContextData patientContext = state.getPatientContext();
        if (patientContext == null) {
            patientContext = chatContextService.loadPatientContext(state.getPatientId());
        }
        Map<String, Object> mergedInput = mergeRequestData(state, input);
        patientContext = applyHealthDataOverrides(patientContext, mergedInput);
        state.setPatientContext(patientContext);
        DiseaseType preferredDiseaseType = parseDiseaseType(mergedInput.get("diseaseType"));
        List<RuleAssessmentResult> results = ruleAssessmentService.assess(patientContext, state.getUserMessage(), preferredDiseaseType);
        state.setAssessmentResults(results);

        if (results.isEmpty()) {
            return new ToolExecutionResult(name(), false, "没有获得可用的风险评估结果。");
        }

        for (RuleAssessmentResult item : results) {
            if (item.riskLevel() == RiskLevel.HIGH || item.riskLevel() == RiskLevel.CRITICAL) {
                state.addSafetyFlag("high_risk_detected");
            }
        }

        String summary = results.stream()
                .map(item -> item.diseaseType().getDisplayName() + "：" + item.riskLevel().getDisplayName())
                .collect(Collectors.joining("；"));
        return new ToolExecutionResult(name(), true, "已完成规则评估。结果：" + summary);
    }

    private DiseaseType parseDiseaseType(Object raw) {
        if (raw == null) {
            return null;
        }
        String normalized = raw.toString().trim().toUpperCase(Locale.ROOT);
        try {
            return DiseaseType.valueOf(normalized);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private Map<String, Object> mergeRequestData(AgentState state, Map<String, Object> input) {
        Map<String, Object> mergedInput = new LinkedHashMap<>();
        if (state != null && state.getRequestData() != null && !state.getRequestData().isEmpty()) {
            mergedInput.putAll(state.getRequestData());
        }
        if (input != null && !input.isEmpty()) {
            mergedInput.putAll(input);
        }
        return mergedInput;
    }

    private ChatPatientContextData applyHealthDataOverrides(ChatPatientContextData patientContext, Map<String, Object> mergedInput) {
        if (patientContext == null || mergedInput == null || mergedInput.isEmpty()) {
            return patientContext;
        }

        Map<String, Object> normalized = mergedInput.entrySet().stream()
                .map(entry -> Map.entry(canonicalHealthKey(entry.getKey()), entry.getValue()))
                .filter(entry -> entry.getKey() != null && HEALTH_RECORD_PROPERTIES.contains(entry.getKey()))
                .filter(entry -> entry.getValue() != null)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (left, right) -> right));
        if (normalized.isEmpty()) {
            return patientContext;
        }

        try {
            HealthRecord overrides = objectMapper.convertValue(normalized, HealthRecord.class);
            HealthRecord mergedRecord = mergeLatestRecord(patientContext.latestRecord(), overrides, patientContext.patient() == null ? null : patientContext.patient().getId());
            return new ChatPatientContextData(
                    patientContext.patient(),
                    patientContext.assessments(),
                    patientContext.plans(),
                    mergedRecord,
                    patientContext.medicalHistory(),
                    patientContext.medicalHistorySummary(),
                    patientContext.promptContext(),
                    patientContext.diseaseKeys(),
                    patientContext.activeConcernThemes(),
                    patientContext.structuredContext()
            );
        } catch (IllegalArgumentException ignored) {
            return patientContext;
        }
    }

    private HealthRecord mergeLatestRecord(HealthRecord base, HealthRecord overrides, Long patientId) {
        HealthRecord merged = new HealthRecord();
        BeanWrapper target = new BeanWrapperImpl(merged);
        BeanWrapper baseWrapper = new BeanWrapperImpl(base == null ? new HealthRecord() : base);
        BeanWrapper overrideWrapper = new BeanWrapperImpl(overrides == null ? new HealthRecord() : overrides);

        for (PropertyDescriptor descriptor : target.getPropertyDescriptors()) {
            String propertyName = descriptor.getName();
            if ("class".equals(propertyName) || !target.isWritableProperty(propertyName)) {
                continue;
            }
            Object overrideValue = overrideWrapper.isReadableProperty(propertyName)
                    ? overrideWrapper.getPropertyValue(propertyName)
                    : null;
            Object finalValue = overrideValue != null
                    ? overrideValue
                    : (baseWrapper.isReadableProperty(propertyName) ? baseWrapper.getPropertyValue(propertyName) : null);
            target.setPropertyValue(propertyName, finalValue);
        }

        if (merged.getPatientId() == null) {
            merged.setPatientId(patientId);
        }
        return merged;
    }

    private String canonicalHealthKey(String rawKey) {
        if (rawKey == null) {
            return null;
        }
        return switch (rawKey.trim()) {
            case "triglyceride" -> "triglycerides";
            case "creatinine" -> "serumCreatinine";
            default -> rawKey.trim();
        };
    }
}
