package com.medical.agent.agent;

import com.medical.agent.agent.behaviour.MessageListenerBehaviour;
import com.medical.agent.agent.message.ACLMessageHelper;
import com.medical.agent.common.message.AgentMessage;
import com.medical.agent.common.message.MessageType;
import com.medical.agent.common.enums.DiseaseType;
import com.medical.agent.common.enums.FollowUpFrequency;
import com.medical.agent.common.enums.RiskLevel;
import jade.core.Agent;
import jade.lang.acl.ACLMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Consumer;

/**
 * 随访智能体 - 根据评估结果自动生成随访计划，推送提醒
 * LLM增强：使用大模型生成个性化随访计划内容，替代固定模板
 */
public class FollowUpAgent extends Agent {

    private static final Logger log = LoggerFactory.getLogger(FollowUpAgent.class);
    private Consumer<AgentMessage> followUpCallback;
    /** LLM调用函数：(patientContext, task) -> llmResponse */
    private BiFunction<String, String, String> llmFunction;

    @Override
    protected void setup() {
        Object[] args = getArguments();
        if (args != null && args.length >= 1) {
            this.followUpCallback = (Consumer<AgentMessage>) args[0];
        }
        if (args != null && args.length >= 2) {
            this.llmFunction = (BiFunction<String, String, String>) args[1];
        }

        log.info("[FollowUpAgent] Started. LLM enhanced={}", llmFunction != null);

        // 监听评估结果
        addBehaviour(new MessageListenerBehaviour(this, ACLMessage.INFORM, this::handleAssessResult));
    }

    private void handleAssessResult(AgentMessage result) {
        if (!MessageType.ASSESS_RESULT.equals(result.getMessageType())) return;

        log.info("[FollowUpAgent] Generating follow-up plan for patient {} ({}), risk={}",
                result.getPatientName(), result.getDiseaseType().getDisplayName(),
                result.getRiskLevel().getDisplayName());

        FollowUpFrequency frequency = determineFrequency(result.getRiskLevel());
        List<String> checkItems = generateCheckItems(result.getDiseaseType(), result.getRiskLevel());
        String planContent = generatePlanContent(result);

        // LLM增强：生成个性化随访计划
        String llmPlan = generateLlmFollowUpPlan(result, frequency, checkItems);
        if (llmPlan != null) {
            planContent = llmPlan;
        }

        Map<String, Object> planData = new HashMap<>();
        planData.put("frequency", frequency.name());
        planData.put("frequencyDisplay", frequency.getDisplayName());
        planData.put("intervalDays", frequency.getIntervalDays());
        planData.put("checkItems", checkItems);
        planData.put("planContent", planContent);

        AgentMessage followUpMsg = AgentMessage.builder()
                .messageId(UUID.randomUUID().toString())
                .messageType(MessageType.FOLLOWUP_CREATED)
                .patientId(result.getPatientId())
                .patientName(result.getPatientName())
                .diseaseType(result.getDiseaseType())
                .riskLevel(result.getRiskLevel())
                .detail(planContent)
                .data(planData)
                .timestamp(System.currentTimeMillis())
                .build();

        if (followUpCallback != null) {
            followUpCallback.accept(followUpMsg);
        }

        log.info("[FollowUpAgent] Follow-up plan created: frequency={}, items={}, llmEnhanced={}",
                frequency.getDisplayName(), checkItems.size(), llmPlan != null);
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
        List<String> items = new ArrayList<>();

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

    private String generatePlanContent(AgentMessage result) {
        FollowUpFrequency freq = determineFrequency(result.getRiskLevel());
        return String.format("【%s随访计划】患者%s，当前%s风险等级为%s，建议%s。重点关注：%s",
                result.getDiseaseType().getDisplayName(),
                result.getPatientName(),
                result.getDiseaseType().getDisplayName(),
                result.getRiskLevel().getDisplayName(),
                freq.getDisplayName(),
                result.getDetail() != null ? result.getDetail() : "定期监测相关指标");
    }

    /**
     * 使用LLM生成个性化随访计划
     */
    private String generateLlmFollowUpPlan(AgentMessage result, FollowUpFrequency frequency, List<String> checkItems) {
        if (llmFunction == null) return null;

        try {
            StringBuilder context = new StringBuilder();
            context.append("患者姓名：").append(result.getPatientName()).append("\n");
            context.append("疾病类型：").append(result.getDiseaseType().getDisplayName()).append("\n");
            context.append("风险等级：").append(result.getRiskLevel().getDisplayName()).append("\n");
            context.append("评估详情：").append(result.getDetail() != null ? result.getDetail() : "无").append("\n");
            context.append("建议随访频率：").append(frequency.getDisplayName()).append("\n");
            context.append("基础检查项目：").append(String.join("、", checkItems)).append("\n");

            String task = "请基于以上患者评估信息，生成一份个性化的随访管理计划。要求：\n" +
                    "1. 明确随访频率和下次随访时间建议\n" +
                    "2. 列出每次随访需要检查的项目和目标值\n" +
                    "3. 给出居家自我监测建议（频率、方法、记录要求）\n" +
                    "4. 给出饮食、运动、用药等生活方式指导\n" +
                    "5. 说明需要紧急就医的危险信号\n" +
                    "6. 控制在400字以内，语言通俗易懂";

            String llmResult = llmFunction.apply(context.toString(), task);
            if (llmResult != null && !llmResult.isBlank()) {
                log.info("[FollowUpAgent] LLM follow-up plan generated for patient {}", result.getPatientName());
                return llmResult;
            }
        } catch (Exception e) {
            log.warn("[FollowUpAgent] LLM plan generation failed, using template: {}", e.getMessage());
        }
        return null;
    }

    @Override
    protected void takeDown() {
        log.info("[FollowUpAgent] Shutting down.");
    }
}
