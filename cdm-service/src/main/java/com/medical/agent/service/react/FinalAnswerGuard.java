package com.medical.agent.service.react;

import com.medical.agent.service.react.model.AgentState;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

@Component
public class FinalAnswerGuard {

    private static final Set<String> STRONG_ASSERTION_HINTS = Set.of(
            "根据当前患者情况",
            "结合该患者",
            "从目前资料看",
            "提示患者目前",
            "说明患者",
            "该患者目前",
            "最新健康记录显示",
            "最近评估显示",
            "当前评估结果表明"
    );

    public GuardResult guard(AgentState state, String reply) {
        String guardedReply = reply == null ? "" : reply.trim();
        Set<String> flags = new LinkedHashSet<>();

        if (guardedReply.isBlank()) {
            guardedReply = buildInsufficientObservationReply(state);
            flags.add("guard_replaced_blank_reply");
        }

        if (hasNoObservation(state) && looksLikePatientFactAssertion(guardedReply)) {
            guardedReply = buildInsufficientObservationReply(state);
            flags.add("guard_replaced_unsupported_patient_assertion");
        }

        if (requiresDoctorAnonymization(state) && state.getPatientContext() != null && state.getPatientContext().patient() != null) {
            String patientName = normalize(state.getPatientContext().patient().getName());
            if (patientName != null && guardedReply.contains(patientName)) {
                guardedReply = guardedReply.replace(patientName, "该患者");
                flags.add("guard_anonymized_patient_name");
            }
        }

        if (state != null
                && state.getSafetyFlags().contains("high_risk_detected")
                && !containsMedicalEscalation(guardedReply)) {
            guardedReply = appendEscalationHint(guardedReply);
            flags.add("guard_added_medical_escalation");
        }

        return new GuardResult(guardedReply, flags);
    }

    private boolean hasNoObservation(AgentState state) {
        return state == null
                || ((state.getPatientContext() == null || isBlank(state.getPatientContext().promptContext()))
                && state.getAssessmentResults().isEmpty()
                && state.getEvidenceCandidates().isEmpty()
                && isBlank(state.getKnowledgeGraphSummary())
                && state.getKnowledgeGraphItems().isEmpty()
                && state.getSteps().isEmpty());
    }

    private boolean looksLikePatientFactAssertion(String reply) {
        if (isBlank(reply)) {
            return false;
        }
        String normalized = reply.toLowerCase(Locale.ROOT);
        for (String hint : STRONG_ASSERTION_HINTS) {
            if (normalized.contains(hint.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return normalized.contains("患者目前")
                || normalized.contains("最新记录")
                || normalized.contains("最近评估")
                || normalized.contains("血压控制不佳")
                || normalized.contains("血糖控制不佳");
    }

    private boolean requiresDoctorAnonymization(AgentState state) {
        return state != null && "doctor".equalsIgnoreCase(state.getOwnerRole());
    }

    private boolean containsMedicalEscalation(String reply) {
        if (isBlank(reply)) {
            return false;
        }
        String normalized = reply.toLowerCase(Locale.ROOT);
        return normalized.contains("及时就医")
                || normalized.contains("线下就医")
                || normalized.contains("尽快就医")
                || normalized.contains("急诊");
    }

    private String appendEscalationHint(String reply) {
        if (isBlank(reply)) {
            return "若症状明显加重、指标持续恶化或出现胸痛、呼吸困难等情况，请及时线下就医。";
        }
        return reply + "\n\n若症状明显加重、指标持续恶化或出现胸痛、呼吸困难等情况，请及时线下就医。";
    }

    private String buildInsufficientObservationReply(AgentState state) {
        if (state != null && "doctor".equalsIgnoreCase(state.getOwnerRole())) {
            if (state.getPatientId() == null) {
                return "当前为通用咨询模式，未绑定具体患者，不能直接输出针对某位患者当前状态的判断。请切换到患者咨询并选择患者，或改为通用慢病管理问题继续提问。";
            }
            return "目前缺少足够的患者 observation 或证据，暂时不能对该患者当前状态下结论。建议先补充患者上下文、最新指标或规则评估结果，再继续分析。";
        }
        return "目前缺少足够的患者资料或证据，我不能直接下当前状态结论。建议先补充最近指标、病史信息或评估结果后再继续分析。";
    }

    private String normalize(String value) {
        return isBlank(value) ? null : value.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    public record GuardResult(String reply, Set<String> flags) {
    }
}
