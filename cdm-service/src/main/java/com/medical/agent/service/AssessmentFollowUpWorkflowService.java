package com.medical.agent.service;

import com.medical.agent.common.dto.NotificationDTO;
import com.medical.agent.common.enums.DiseaseType;
import com.medical.agent.common.enums.FollowUpFrequency;
import com.medical.agent.common.enums.FollowUpStatus;
import com.medical.agent.common.enums.RiskLevel;
import com.medical.agent.model.entity.FollowUpPlan;
import com.medical.agent.model.entity.Patient;
import com.medical.agent.model.entity.ResourceRecommendation;
import com.medical.agent.model.entity.RiskAssessment;
import com.medical.agent.model.repository.FollowUpPlanRepository;
import com.medical.agent.model.repository.ResourceRecommendationRepository;
import com.medical.agent.service.cache.AiChatCacheInvalidationService;
import com.medical.agent.service.chat.RuleAssessmentResult;
import com.medical.agent.service.support.AssessmentWorkflowPayload;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class AssessmentFollowUpWorkflowService {

    private static final List<FollowUpStatus> ACTIVE_STATUSES = List.of(
            FollowUpStatus.PENDING,
            FollowUpStatus.IN_PROGRESS,
            FollowUpStatus.OVERDUE
    );

    private final FollowUpPlanRepository followUpPlanRepository;
    private final ResourceRecommendationRepository resourceRecommendationRepository;
    private final ClinicalNotificationGateway clinicalNotificationGateway;
    private final AiChatCacheInvalidationService aiChatCacheInvalidationService;

    public AssessmentFollowUpWorkflowService(FollowUpPlanRepository followUpPlanRepository,
                                             ResourceRecommendationRepository resourceRecommendationRepository,
                                             ClinicalNotificationGateway clinicalNotificationGateway,
                                             AiChatCacheInvalidationService aiChatCacheInvalidationService) {
        this.followUpPlanRepository = followUpPlanRepository;
        this.resourceRecommendationRepository = resourceRecommendationRepository;
        this.clinicalNotificationGateway = clinicalNotificationGateway;
        this.aiChatCacheInvalidationService = aiChatCacheInvalidationService;
    }

    public FollowUpPlan upsertReactFollowUpPlan(Patient patient,
                                                RuleAssessmentResult result,
                                                String agentId) {
        if (patient == null || result == null || result.diseaseType() == null || result.riskLevel() == null) {
            return null;
        }
        FollowUpFrequency frequency = determineFrequency(result.riskLevel());
        String planContent = generatePlanContent(result, frequency);
        String checkItems = String.join("; ", generateCheckItems(result.diseaseType(), result.riskLevel()));
        List<FollowUpPlan> activePlans = followUpPlanRepository.findByPatientIdAndDiseaseTypeAndStatusInOrderByCreatedAtDesc(
                patient.getId(),
                result.diseaseType(),
                ACTIVE_STATUSES
        );

        FollowUpPlan plan = activePlans.isEmpty()
                ? FollowUpPlan.builder()
                .patientId(patient.getId())
                .diseaseType(result.diseaseType())
                .agentId(agentId)
                .build()
                : activePlans.get(0);
        plan.setFrequency(frequency);
        plan.setStatus(FollowUpStatus.PENDING);
        plan.setNextFollowDate(LocalDate.now().plusDays(frequency.getIntervalDays()));
        plan.setPlanContent(planContent);
        plan.setCheckItems(checkItems);
        if (plan.getAgentId() == null || plan.getAgentId().isBlank()) {
            plan.setAgentId(agentId);
        }
        FollowUpPlan saved = followUpPlanRepository.save(plan);
        aiChatCacheInvalidationService.invalidatePatient(patient.getId());
        return saved;
    }

    public FollowUpPlan upsertAssessmentDrivenFollowUp(AssessmentWorkflowPayload payload, String agentId) {
        if (payload == null || payload.diseaseType() == null || payload.riskLevel() == null) {
            return null;
        }
        FollowUpFrequency frequency = determineFrequency(payload.riskLevel());
        List<String> checkItems = generateCheckItems(payload.diseaseType(), payload.riskLevel());
        String planContent = generatePlanContent(payload, frequency);
        return upsertFollowUpPlan(
                payload.patientId(),
                payload.patientName(),
                payload.diseaseType(),
                frequency,
                checkItems,
                planContent,
                agentId,
                true
        );
    }

    public boolean hasActiveFollowUpPlan(Long patientId, DiseaseType diseaseType) {
        if (patientId == null || diseaseType == null) {
            return false;
        }
        return !followUpPlanRepository.findByPatientIdAndDiseaseTypeAndStatusInOrderByCreatedAtDesc(
                patientId,
                diseaseType,
                ACTIVE_STATUSES
        ).isEmpty();
    }

    @SuppressWarnings("unchecked")
    public FollowUpPlan applyBridgeFollowUpMessage(AssessmentWorkflowPayload payload,
                                                   String agentId,
                                                   boolean suppressCreation) {
        if (payload == null || suppressCreation) {
            return null;
        }
        Map<String, Object> data = payload.data();
        FollowUpFrequency frequency = FollowUpFrequency.valueOf((String) data.get("frequency"));
        List<String> checkItems = (List<String>) data.get("checkItems");
        return upsertFollowUpPlan(
                payload.patientId(),
                payload.patientName(),
                payload.diseaseType(),
                frequency,
                checkItems,
                (String) data.get("planContent"),
                agentId,
                true
        );
    }

    public ResourceRecommendation createReactResourceRecommendation(Patient patient,
                                                                    RiskAssessment assessment,
                                                                    RuleAssessmentResult result,
                                                                    String agentId) {
        String reason = generateResourceRecommendationReason(patient, result);
        ResourceRecommendation recommendation = ResourceRecommendation.builder()
                .patientId(patient.getId())
                .assessmentId(assessment == null ? null : assessment.getId())
                .reason(reason)
                .agentId(agentId)
                .build();
        return resourceRecommendationRepository.save(recommendation);
    }

    public ResourceRecommendation createAssessmentDrivenResourceRecommendation(AssessmentWorkflowPayload payload, String agentId) {
        if (payload == null) {
            return null;
        }
        return createResourceRecommendation(
                payload.patientId(),
                payload.detail(),
                agentId,
                true,
                payload
        );
    }

    public ResourceRecommendation applyBridgeResourceRecommendation(AssessmentWorkflowPayload payload, String agentId) {
        return createResourceRecommendation(
                payload == null ? null : payload.patientId(),
                payload == null ? null : payload.detail(),
                agentId,
                true,
                payload
        );
    }

    private FollowUpFrequency determineFrequency(RiskLevel riskLevel) {
        return switch (riskLevel) {
            case LOW -> FollowUpFrequency.QUARTERLY;
            case MEDIUM -> FollowUpFrequency.MONTHLY;
            case HIGH -> FollowUpFrequency.WEEKLY;
            case CRITICAL -> FollowUpFrequency.IMMEDIATE;
        };
    }

    private List<String> generateCheckItems(DiseaseType diseaseType, RiskLevel riskLevel) {
        ArrayList<String> items = new ArrayList<>();
        switch (diseaseType) {
            case HYPERTENSION -> {
                items.add("测量血压（收缩压/舒张压）");
                items.add("评估服药依从性");
                items.add("记录生活方式（盐摄入、运动）");
                if (riskLevel.getLevel() >= RiskLevel.HIGH.getLevel()) {
                    items.add("心电图检查");
                    items.add("尿蛋白检测");
                }
            }
            case DIABETES -> {
                items.add("空腹血糖检测");
                items.add("评估服药/注射依从性");
                items.add("足部检查");
                if (riskLevel.getLevel() >= RiskLevel.MEDIUM.getLevel()) {
                    items.add("HbA1c检测");
                    items.add("尿微量白蛋白检测");
                }
                if (riskLevel.getLevel() >= RiskLevel.HIGH.getLevel()) {
                    items.add("眼底检查");
                }
            }
            case COPD -> {
                items.add("CAT问卷评估");
                items.add("mMRC呼吸困难评分");
                items.add("急性加重事件记录");
                items.add("吸入剂使用技术评估");
                if (riskLevel.getLevel() >= RiskLevel.HIGH.getLevel()) {
                    items.add("肺功能复查");
                    items.add("血氧饱和度监测");
                }
            }
            case CKD -> {
                items.add("eGFR检测");
                items.add("尿蛋白/肌酐比值");
                items.add("血压监测");
                items.add("电解质检查");
                if (riskLevel.getLevel() >= RiskLevel.HIGH.getLevel()) {
                    items.add("血红蛋白检测");
                    items.add("钙磷代谢指标");
                }
            }
            case CHD -> {
                items.add("胸痛症状评估");
                items.add("运动耐量评估");
                items.add("服药依从性检查（抗血小板+他汀）");
                items.add("血脂检测");
                if (riskLevel.getLevel() >= RiskLevel.HIGH.getLevel()) {
                    items.add("心电图检查");
                    items.add("心脏超声");
                }
            }
            case STROKE -> {
                items.add("血压监测");
                items.add("服药依从性评估");
                items.add("ADL生活能力评分");
                items.add("肢体运动功能评估");
                if (riskLevel.getLevel() >= RiskLevel.MEDIUM.getLevel()) {
                    items.add("MMSE认知功能评分");
                    items.add("血脂血糖复查");
                }
            }
        }
        return items;
    }

    private String generatePlanContent(RuleAssessmentResult result, FollowUpFrequency frequency) {
        return String.format("【%s随访计划】当前%s风险等级为%s，建议%s。重点关注：%s",
                result.diseaseType().getDisplayName(),
                result.diseaseType().getDisplayName(),
                result.riskLevel().getDisplayName(),
                frequency.getDisplayName(),
                result.detail() != null ? result.detail() : "定期监测相关指标");
    }

    private String generatePlanContent(AssessmentWorkflowPayload payload, FollowUpFrequency frequency) {
        return String.format("【%s随访计划】患者%s，当前%s风险等级为%s，建议%s。重点关注：%s",
                payload.diseaseType().getDisplayName(),
                payload.patientName(),
                payload.diseaseType().getDisplayName(),
                payload.riskLevel().getDisplayName(),
                frequency.getDisplayName(),
                payload.detail() != null ? payload.detail() : "定期监测相关指标");
    }

    private String generateResourceRecommendationReason(Patient patient, RuleAssessmentResult result) {
        String urgency = result.riskLevel() == RiskLevel.CRITICAL ? "紧急" : "建议尽快";
        return String.format("患者%s%s评估为%s，%s转诊相关专科进一步诊治。%s",
                patient.getName(),
                result.diseaseType().getDisplayName(),
                result.riskLevel().getDisplayName(),
                urgency,
                result.detail() == null ? "" : result.detail());
    }

    private FollowUpPlan upsertFollowUpPlan(Long patientId,
                                            String patientName,
                                            DiseaseType diseaseType,
                                            FollowUpFrequency frequency,
                                            List<String> checkItems,
                                            String planContent,
                                            String agentId,
                                            boolean notifyWhenCreated) {
        List<FollowUpPlan> activePlans = followUpPlanRepository.findByPatientIdAndDiseaseTypeAndStatusInOrderByCreatedAtDesc(
                patientId,
                diseaseType,
                ACTIVE_STATUSES
        );

        boolean created = activePlans.isEmpty();
        FollowUpPlan plan = created ? FollowUpPlan.builder()
                .patientId(patientId)
                .diseaseType(diseaseType)
                .agentId(agentId)
                .build() : activePlans.get(0);
        plan.setFrequency(frequency);
        plan.setStatus(FollowUpStatus.PENDING);
        plan.setNextFollowDate(LocalDate.now().plusDays(frequency.getIntervalDays()));
        plan.setPlanContent(planContent);
        plan.setCheckItems(checkItems != null ? String.join("; ", checkItems) : "");
        if (plan.getAgentId() == null || plan.getAgentId().isBlank()) {
            plan.setAgentId(agentId);
        }

        FollowUpPlan saved = followUpPlanRepository.save(plan);
        aiChatCacheInvalidationService.invalidatePatient(patientId);
        if (created && notifyWhenCreated) {
            clinicalNotificationGateway.sendNotification(NotificationDTO.builder()
                    .type("FOLLOW_UP_REMINDER")
                    .patientId(patientId)
                    .title("新随访计划已生成")
                    .message(String.format("患者%s的%s随访计划已创建，%s，下次随访日期：%s",
                            patientName,
                            diseaseType.getDisplayName(),
                            frequency.getDisplayName(),
                            saved.getNextFollowDate()))
                    .payload(Map.of(
                            "patientId", patientId,
                            "diseaseType", diseaseType.name(),
                            "agentId", agentId
                    ))
                    .timestamp(System.currentTimeMillis())
                    .build());
        }
        return saved;
    }

    private ResourceRecommendation createResourceRecommendation(Long patientId,
                                                                String detail,
                                                                String agentId,
                                                                boolean notify,
                                                                Object payload) {
        ResourceRecommendation recommendation = ResourceRecommendation.builder()
                .patientId(patientId)
                .reason(detail)
                .agentId(agentId)
                .build();
        ResourceRecommendation saved = resourceRecommendationRepository.save(recommendation);
        if (notify) {
            clinicalNotificationGateway.sendNotification(NotificationDTO.builder()
                    .type("RESOURCE_RECOMMENDATION")
                    .patientId(patientId)
                    .title("医疗资源推荐")
                    .message(detail)
                    .payload(payload)
                    .timestamp(System.currentTimeMillis())
                    .build());
        }
        return saved;
    }
}
