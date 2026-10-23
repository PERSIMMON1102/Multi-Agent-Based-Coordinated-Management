package com.medical.agent.agent;

import com.medical.agent.agent.behaviour.MessageListenerBehaviour;
import com.medical.agent.common.message.AgentMessage;
import com.medical.agent.common.message.MessageType;
import com.medical.agent.common.enums.DiseaseType;
import com.medical.agent.common.enums.RiskLevel;
import jade.core.Agent;
import jade.lang.acl.ACLMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Consumer;

/**
 * 资源协调智能体 - 当风险升高时推荐附近医疗资源
 * LLM增强：使用大模型生成个性化的转诊建议和资源推荐理由
 */
public class ResourceCoordinatorAgent extends Agent {

    private static final Logger log = LoggerFactory.getLogger(ResourceCoordinatorAgent.class);
    private Consumer<AgentMessage> resourceCallback;
    /** LLM调用函数：(patientContext, task) -> llmResponse */
    private BiFunction<String, String, String> llmFunction;

    @Override
    protected void setup() {
        Object[] args = getArguments();
        if (args != null && args.length >= 1) {
            this.resourceCallback = (Consumer<AgentMessage>) args[0];
        }
        if (args != null && args.length >= 2) {
            this.llmFunction = (BiFunction<String, String, String>) args[1];
        }

        log.info("[ResourceCoordinatorAgent] Started. LLM enhanced={}", llmFunction != null);

        // 监听高风险评估结果
        addBehaviour(new MessageListenerBehaviour(this, ACLMessage.INFORM, this::handleHighRiskResult));
    }

    private void handleHighRiskResult(AgentMessage result) {
        if (!MessageType.ASSESS_RESULT.equals(result.getMessageType())) return;
        if (result.getRiskLevel() == null || result.getRiskLevel().getLevel() < RiskLevel.HIGH.getLevel()) return;

        log.info("[ResourceCoordinatorAgent] High risk detected for patient {} ({}), coordinating resources...",
                result.getPatientName(), result.getDiseaseType().getDisplayName());

        List<String> specialties = recommendSpecialties(result.getDiseaseType());
        List<String> examinations = recommendExaminations(result.getDiseaseType(), result.getRiskLevel());
        String reason = generateReason(result);

        // LLM增强：生成个性化转诊建议
        String llmReason = generateLlmRecommendation(result, specialties, examinations);
        if (llmReason != null) {
            reason = llmReason;
        }

        Map<String, Object> resourceData = new HashMap<>();
        resourceData.put("specialties", specialties);
        resourceData.put("examinations", examinations);
        resourceData.put("reason", reason);
        resourceData.put("urgency", result.getRiskLevel() == RiskLevel.CRITICAL ? "紧急" : "尽快");

        AgentMessage resourceMsg = AgentMessage.builder()
                .messageId(UUID.randomUUID().toString())
                .messageType(MessageType.RESOURCE_RESULT)
                .patientId(result.getPatientId())
                .patientName(result.getPatientName())
                .diseaseType(result.getDiseaseType())
                .riskLevel(result.getRiskLevel())
                .detail(reason)
                .data(resourceData)
                .timestamp(System.currentTimeMillis())
                .build();

        if (resourceCallback != null) {
            resourceCallback.accept(resourceMsg);
        }

        log.info("[ResourceCoordinatorAgent] Resource recommendation sent: specialties={}, llmEnhanced={}",
                specialties, llmReason != null);
    }

    private List<String> recommendSpecialties(DiseaseType diseaseType) {
        return switch (diseaseType) {
            case HYPERTENSION -> List.of("心内科", "高血压专科");
            case DIABETES -> List.of("内分泌科", "糖尿病专科", "营养科");
            case COPD -> List.of("呼吸内科", "肺功能室");
            case CKD -> List.of("肾内科", "血液透析中心");
            case CHD -> List.of("心内科", "心血管介入科", "胸痛中心");
            case STROKE -> List.of("神经内科", "脑卒中中心", "康复科");
        };
    }

    private List<String> recommendExaminations(DiseaseType diseaseType, RiskLevel riskLevel) {
        List<String> exams = new ArrayList<>();
        switch (diseaseType) {
            case HYPERTENSION -> {
                exams.add("24小时动态血压监测");
                exams.add("心电图");
                if (riskLevel == RiskLevel.CRITICAL) exams.add("心脏超声");
            }
            case DIABETES -> {
                exams.add("糖化血红蛋白");
                exams.add("尿微量白蛋白");
                if (riskLevel == RiskLevel.CRITICAL) {
                    exams.add("血酮体");
                    exams.add("血气分析");
                }
            }
            case COPD -> {
                exams.add("肺功能检查");
                exams.add("胸部CT");
                if (riskLevel == RiskLevel.CRITICAL) exams.add("血气分析");
            }
            case CKD -> {
                exams.add("肾功能全套");
                exams.add("肾脏超声");
                if (riskLevel == RiskLevel.CRITICAL) exams.add("透析评估");
            }
            case CHD -> {
                exams.add("心电图");
                exams.add("心肌酶谱");
                if (riskLevel == RiskLevel.CRITICAL) exams.add("冠脉造影");
            }
            case STROKE -> {
                exams.add("头颅MRI/CT");
                exams.add("颈动脉超声");
                if (riskLevel == RiskLevel.CRITICAL) exams.add("脑血管造影");
            }
        }
        return exams;
    }

    private String generateReason(AgentMessage result) {
        String urgency = result.getRiskLevel() == RiskLevel.CRITICAL ? "紧急" : "建议尽快";
        return String.format("患者%s%s评估为%s，%s转诊专科进一步诊治。%s",
                result.getPatientName(),
                result.getDiseaseType().getDisplayName(),
                result.getRiskLevel().getDisplayName(),
                urgency,
                result.getDetail() != null ? result.getDetail() : "");
    }

    /**
     * 使用LLM生成个性化转诊建议
     */
    private String generateLlmRecommendation(AgentMessage result, List<String> specialties, List<String> examinations) {
        if (llmFunction == null) return null;

        try {
            StringBuilder context = new StringBuilder();
            context.append("患者姓名：").append(result.getPatientName()).append("\n");
            context.append("疾病类型：").append(result.getDiseaseType().getDisplayName()).append("\n");
            context.append("风险等级：").append(result.getRiskLevel().getDisplayName()).append("\n");
            context.append("评估详情：").append(result.getDetail() != null ? result.getDetail() : "无").append("\n");
            context.append("推荐科室：").append(String.join("、", specialties)).append("\n");
            context.append("推荐检查：").append(String.join("、", examinations)).append("\n");

            String task = "请基于以上高风险患者信息，生成一份转诊与资源协调建议。要求：\n" +
                    "1. 说明为什么需要转诊（基于患者当前风险等级和指标）\n" +
                    "2. 建议优先就诊的科室及原因\n" +
                    "3. 说明需要做的检查项目及其意义\n" +
                    "4. 转诊前患者需要注意的事项\n" +
                    "5. 如果是危急情况，明确标注紧急程度\n" +
                    "6. 控制在300字以内";

            String llmResult = llmFunction.apply(context.toString(), task);
            if (llmResult != null && !llmResult.isBlank()) {
                log.info("[ResourceCoordinatorAgent] LLM recommendation generated for patient {}", result.getPatientName());
                return llmResult;
            }
        } catch (Exception e) {
            log.warn("[ResourceCoordinatorAgent] LLM recommendation failed, using template: {}", e.getMessage());
        }
        return null;
    }

    @Override
    protected void takeDown() {
        log.info("[ResourceCoordinatorAgent] Shutting down.");
    }
}
