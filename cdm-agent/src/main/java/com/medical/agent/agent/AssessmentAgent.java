package com.medical.agent.agent;

import com.medical.agent.agent.behaviour.MessageListenerBehaviour;
import com.medical.agent.agent.message.ACLMessageHelper;
import com.medical.agent.common.fact.PatientHealthFact;
import com.medical.agent.common.message.AgentMessage;
import com.medical.agent.common.message.MessageType;
import com.medical.agent.common.enums.DiseaseType;
import com.medical.agent.common.enums.RiskLevel;
import com.medical.agent.common.util.PatientMedicalHistoryUtil;
import jade.core.AID;
import jade.core.Agent;
import jade.lang.acl.ACLMessage;
import org.kie.api.runtime.KieContainer;
import org.kie.api.runtime.KieSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Consumer;

/**
 * 评估智能体 - 接收患者健康数据，使用Drools规则引擎计算风险等级
 * LLM增强：在Drools结构化评估基础上，使用大模型生成个性化分析报告
 * 支持6种慢性病：高血压、糖尿病、COPD、CKD、冠心病、脑卒中
 */
public class AssessmentAgent extends Agent {

    private static final Logger log = LoggerFactory.getLogger(AssessmentAgent.class);
    private KieContainer kieContainer;
    private Consumer<AgentMessage> resultCallback;
    /** LLM调用函数：(patientContext, task) -> llmResponse */
    private BiFunction<String, String, String> llmFunction;

    @Override
    protected void setup() {
        Object[] args = getArguments();
        if (args != null && args.length >= 1) {
            this.kieContainer = (KieContainer) args[0];
        }
        if (args != null && args.length >= 2) {
            this.resultCallback = (Consumer<AgentMessage>) args[1];
        }
        if (args != null && args.length >= 3) {
            this.llmFunction = (BiFunction<String, String, String>) args[2];
        }

        // 启用O2A通信，接收来自Spring的对象
        setEnabledO2ACommunication(true, 0);

        log.info("[AssessmentAgent] Started. Ready to assess 6 chronic diseases.");

        // 监听ACL评估请求
        addBehaviour(new MessageListenerBehaviour(this, ACLMessage.REQUEST, this::handleAssessRequest));

        // 监听O2A对象（来自Spring AgentBridgeService的putO2AObject调用）
        addBehaviour(new jade.core.behaviours.CyclicBehaviour(this) {
            @Override
            public void action() {
                Object obj = myAgent.getO2AObject();
                if (obj instanceof AgentMessage msg) {
                    handleAssessRequest(msg);
                } else {
                    block(200);
                }
            }
        });
    }

    private void handleAssessRequest(AgentMessage request) {
        if (!MessageType.ASSESS_REQUEST.equals(request.getMessageType())) return;

        log.info("[AssessmentAgent] Processing assessment for patient {} (ID: {})",
                request.getPatientName(), request.getPatientId());

        try {
            Map<String, Object> data = request.getData();
            if (data == null) {
                log.warn("[AssessmentAgent] No data provided for assessment");
                return;
            }

            // 构建Fact对象
            PatientHealthFact fact = buildFact(request.getPatientId(), request.getPatientName(), data);

            // 确定要评估的疾病类型
            List<DiseaseType> diseaseTypes = determineDiseaseTypes(data, request.getDiseaseType());

            for (DiseaseType dt : diseaseTypes) {
                PatientHealthFact assessFact = cloneFact(fact);
                assessFact.setAssessedDiseaseType(dt);

                // 使用Drools规则引擎评估
                KieSession session = kieContainer.newKieSession();
                try {
                    session.insert(assessFact);
                    int firedRules = session.fireAllRules(100); // 限制最大规则触发次数，防止无限循环
                    log.info("[AssessmentAgent] Disease={}, Fired {} rules, Risk={}",
                            dt.getCode(), firedRules, assessFact.getRiskLevel());
                } finally {
                    session.dispose();
                }

                if (assessFact.getRiskLevel() == null) {
                    assessFact.setRiskLevel(RiskLevel.LOW);
                    assessFact.setRiskScore(0.0);
                    assessFact.setAssessmentDetail("数据不足，无法完成" + dt.getDisplayName() + "评估");
                }

                applyMedicalHistoryContext(dt, assessFact, request.getData());

                // LLM增强：生成个性化评估分析
                String llmAnalysis = generateLlmAnalysis(request, dt, assessFact);
                String finalDetail = assessFact.getAssessmentDetail();
                if (llmAnalysis != null) {
                    finalDetail = llmAnalysis;
                }

                // 构建评估结果
                AgentMessage result = AgentMessage.builder()
                        .messageId(UUID.randomUUID().toString())
                        .messageType(MessageType.ASSESS_RESULT)
                        .patientId(request.getPatientId())
                        .patientName(request.getPatientName())
                        .diseaseType(dt)
                        .riskLevel(assessFact.getRiskLevel())
                        .score(assessFact.getRiskScore())
                        .detail(finalDetail)
                        .recommendations(assessFact.getRecommendations())
                        .data(request.getData())
                        .timestamp(System.currentTimeMillis())
                        .build();

                boolean suppressFollowUpCreation = isMetaFlagEnabled(request.getData(), "_suppressFollowUpCreation");
                boolean silentPatientNotification = isMetaFlagEnabled(request.getData(), "_silentPatientNotification");
                boolean disableSecondaryAgents = isMetaFlagEnabled(request.getData(), "_disableJadeSecondaryAgents");

                if (!disableSecondaryAgents && !suppressFollowUpCreation) {
                    // 通知随访智能体
                    sendToAgent("FollowUpAgent", result);
                }

                // 如果风险为高/危急，通知资源协调智能体
                if (!disableSecondaryAgents
                        && !silentPatientNotification
                        && assessFact.getRiskLevel().getLevel() >= RiskLevel.HIGH.getLevel()) {
                    sendToAgent("ResourceCoordinatorAgent", result);
                }

                // 回调Spring服务层
                if (resultCallback != null) {
                    resultCallback.accept(result);
                }
            }
        } catch (Exception e) {
            log.error("[AssessmentAgent] Error processing assessment for patient {}: {}",
                    request.getPatientId(), e.getMessage(), e);
        }
    }

    private List<DiseaseType> determineDiseaseTypes(Map<String, Object> data, DiseaseType specified) {
        if (specified != null) return List.of(specified);

        List<DiseaseType> types = new ArrayList<>();
        if (data.containsKey("systolicBp") || data.containsKey("diastolicBp")) types.add(DiseaseType.HYPERTENSION);
        if (data.containsKey("fastingGlucose") || data.containsKey("hba1c")) types.add(DiseaseType.DIABETES);
        if (data.containsKey("wbc") || data.containsKey("neutrophilPct")) types.add(DiseaseType.COPD);
        if (data.containsKey("creatinine") || data.containsKey("serumCreatinine") || data.containsKey("egfr")
                || data.containsKey("uacr") || data.containsKey("urineProtein")) {
            types.add(DiseaseType.CKD);
        }
        if (data.containsKey("ldlCholesterol") || data.containsKey("ck") || data.containsKey("triglyceride")
                || data.containsKey("triglycerides")) {
            types.add(DiseaseType.CHD);
        }
        if (data.containsKey("uricAcid") || data.containsKey("platelet")) types.add(DiseaseType.STROKE);
        types.addAll(extractHistoryDiseaseTypes(data));

        return types.isEmpty() ? List.of(DiseaseType.HYPERTENSION) : types;
    }

    private void sendToAgent(String agentName, AgentMessage message) {
        try {
            ACLMessage aclMsg = ACLMessageHelper.createInformMessage(message);
            aclMsg.addReceiver(new AID(agentName, AID.ISLOCALNAME));
            send(aclMsg);
            log.info("[AssessmentAgent] Sent {} to {}", message.getMessageType(), agentName);
        } catch (Exception e) {
            log.error("[AssessmentAgent] Failed to send message to {}: {}", agentName, e.getMessage());
        }
    }

    private boolean isMetaFlagEnabled(Map<String, Object> data, String key) {
        if (data == null || !data.containsKey(key)) {
            return false;
        }
        Object value = data.get(key);
        if (value instanceof Boolean boolValue) {
            return boolValue;
        }
        return "true".equalsIgnoreCase(String.valueOf(value));
    }

    private PatientHealthFact buildFact(Long patientId, String patientName, Map<String, Object> data) {
        PatientHealthFact.PatientHealthFactBuilder b = PatientHealthFact.builder()
                .patientId(patientId)
                .patientName(patientName);

        b.age(getInt(data, "age"));
        // 一般检查
        b.systolicBp(getInt(data, "systolicBp"));
        b.diastolicBp(getInt(data, "diastolicBp"));
        b.bmi(getDouble(data, "bmi"));
        // 血常规
        b.hemoglobin(getInt(data, "hemoglobin"));
        b.wbc(getDouble(data, "wbc"));
        b.platelet(getInt(data, "platelet"));
        b.neutrophilPct(getInt(data, "neutrophilPct"));
        // 生化 - 肝功
        b.alt(getInt(data, "alt"));
        b.ast(getInt(data, "ast"));
        // 生化 - 肾功
        b.creatinine(getDouble(data, "creatinine", "serumCreatinine"));
        b.uricAcid(getDouble(data, "uricAcid"));
        // 生化 - 血脂
        b.totalCholesterol(getDouble(data, "totalCholesterol"));
        b.triglyceride(getDouble(data, "triglyceride", "triglycerides"));
        b.ldlCholesterol(getDouble(data, "ldlCholesterol"));
        b.hdlCholesterol(getDouble(data, "hdlCholesterol"));
        // 生化 - 血糖
        b.fastingGlucose(getDouble(data, "fastingGlucose"));
        b.hba1c(getDouble(data, "hba1c"));
        // 生化 - 心肌酶
        b.ck(getInt(data, "ck"));
        // 生化 - 电解质
        b.potassium(getDouble(data, "potassium"));
        // 尿常规
        b.urineProtein(getString(data, "urineProtein"));
        b.urineGlucose(getString(data, "urineGlucose"));
        b.urineBlood(getString(data, "urineBlood"));

        return b.build();
    }

    private PatientHealthFact cloneFact(PatientHealthFact src) {
        return PatientHealthFact.builder()
                .patientId(src.getPatientId()).patientName(src.getPatientName()).age(src.getAge())
                .systolicBp(src.getSystolicBp()).diastolicBp(src.getDiastolicBp()).bmi(src.getBmi())
                .hemoglobin(src.getHemoglobin()).wbc(src.getWbc()).platelet(src.getPlatelet()).neutrophilPct(src.getNeutrophilPct())
                .alt(src.getAlt()).ast(src.getAst())
                .creatinine(src.getCreatinine()).uricAcid(src.getUricAcid())
                .totalCholesterol(src.getTotalCholesterol()).triglyceride(src.getTriglyceride())
                .ldlCholesterol(src.getLdlCholesterol()).hdlCholesterol(src.getHdlCholesterol())
                .fastingGlucose(src.getFastingGlucose()).hba1c(src.getHba1c())
                .ck(src.getCk()).potassium(src.getPotassium())
                .urineProtein(src.getUrineProtein()).urineGlucose(src.getUrineGlucose()).urineBlood(src.getUrineBlood())
                .recommendations(new ArrayList<>())
                .build();
    }

    private Integer getInt(Map<String, Object> data, String key) {
        Object v = data.get(key);
        if (v == null) return null;
        if (v instanceof Number) return ((Number) v).intValue();
        try { return Integer.parseInt(v.toString()); } catch (Exception e) { return null; }
    }

    private Double getDouble(Map<String, Object> data, String... keys) {
        if (data == null || keys == null) {
            return null;
        }
        for (String key : keys) {
            if (key == null) {
                continue;
            }
            Object v = data.get(key);
            if (v == null) {
                continue;
            }
            if (v instanceof Number) return ((Number) v).doubleValue();
            try {
                return Double.parseDouble(v.toString());
            } catch (Exception ignored) {
                // continue to next alias
            }
        }
        return null;
    }

    private Boolean getBool(Map<String, Object> data, String key) {
        Object v = data.get(key);
        if (v == null) return null;
        if (v instanceof Boolean) return (Boolean) v;
        return Boolean.parseBoolean(v.toString());
    }

    private String getString(Map<String, Object> data, String key) {
        Object v = data.get(key);
        if (v == null) return null;
        return v.toString();
    }

    private Set<DiseaseType> extractHistoryDiseaseTypes(Map<String, Object> data) {
        LinkedHashSet<DiseaseType> detected = new LinkedHashSet<>();
        if (data == null) {
            return detected;
        }

        Object historyDiseaseTypes = data.get("historyDiseaseTypes");
        if (historyDiseaseTypes instanceof Iterable<?> iterable) {
            for (Object item : iterable) {
                if (item == null) {
                    continue;
                }
                try {
                    detected.add(DiseaseType.valueOf(item.toString()));
                } catch (IllegalArgumentException ignored) {
                }
            }
        }

        detected.addAll(PatientMedicalHistoryUtil.detectDiseaseTypes(
                getString(data, "pastDiseases"),
                getString(data, "medicalHistorySummary")
        ));
        return detected;
    }

    private void applyMedicalHistoryContext(DiseaseType diseaseType,
                                            PatientHealthFact fact,
                                            Map<String, Object> data) {
        if (fact == null || data == null) {
            return;
        }

        if (fact.getRecommendations() == null) {
            fact.setRecommendations(new ArrayList<>());
        }

        List<String> notes = new ArrayList<>();
        if (PatientMedicalHistoryUtil.hasDiseaseHistory(
                diseaseType,
                getString(data, "pastDiseases"),
                getString(data, "medicalHistorySummary")
        )) {
            notes.add("既往" + diseaseType.getDisplayName() + "病史");
            addRecommendationIfAbsent(
                    fact.getRecommendations(),
                    "结合既往" + diseaseType.getDisplayName() + "病史，建议核对长期治疗方案并保持连续随访。"
            );
        }

        String drugAllergies = getString(data, "drugAllergies");
        if (drugAllergies != null && !drugAllergies.isBlank()) {
            notes.add("药物过敏史：" + shorten(drugAllergies, 40));
            addRecommendationIfAbsent(
                    fact.getRecommendations(),
                    "已记录药物过敏史，调整或新增药物前请先核对：" + shorten(drugAllergies, 40)
            );
        }

        String foodAllergies = getString(data, "foodAllergies");
        if (foodAllergies != null && !foodAllergies.isBlank()) {
            addRecommendationIfAbsent(
                    fact.getRecommendations(),
                    "已记录食物过敏史，制定饮食建议前请先核对：" + shorten(foodAllergies, 40)
            );
        }

        if (!notes.isEmpty()) {
            String detail = fact.getAssessmentDetail();
            String historyNote = "病史提示：" + String.join("；", notes);
            if (detail == null || detail.isBlank()) {
                fact.setAssessmentDetail(historyNote);
            } else if (!detail.contains(historyNote)) {
                fact.setAssessmentDetail(detail + "\n" + historyNote);
            }
        }
    }

    private void addRecommendationIfAbsent(List<String> recommendations, String recommendation) {
        if (recommendations == null || recommendation == null || recommendation.isBlank()) {
            return;
        }
        boolean exists = recommendations.stream().anyMatch(item -> recommendation.equalsIgnoreCase(String.valueOf(item)));
        if (!exists) {
            recommendations.add(recommendation);
        }
    }

    private String shorten(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String normalized = value.replaceAll("[\\r\\n]+", "；").trim();
        if (normalized.length() <= maxLength) {
            return normalized;
        }
        return normalized.substring(0, maxLength) + "...";
    }

    @Override
    protected void takeDown() {
        log.info("[AssessmentAgent] Shutting down.");
    }

    /**
     * 使用LLM生成个性化评估分析报告
     * 如果LLM不可用，返回null（调用方将使用Drools原始结果）
     */
    private String generateLlmAnalysis(AgentMessage request, DiseaseType dt, PatientHealthFact fact) {
        if (llmFunction == null) return null;

        try {
            StringBuilder context = new StringBuilder();
            context.append("患者姓名：").append(request.getPatientName()).append("\n");
            context.append("疾病类型：").append(dt.getDisplayName()).append("\n");
            context.append("Drools规则引擎评估结果：\n");
            context.append("  - 风险等级：").append(fact.getRiskLevel().getDisplayName()).append("\n");
            context.append("  - 风险评分：").append(fact.getRiskScore()).append("\n");
            if (fact.getAssessmentDetail() != null) {
                context.append("  - 评估详情：").append(fact.getAssessmentDetail()).append("\n");
            }

            // 附加关键指标
            Map<String, Object> data = request.getData();
            if (data != null) {
                context.append("关键健康指标：\n");
                data.forEach((k, v) -> {
                    if (v != null) context.append("  - ").append(k).append("：").append(v).append("\n");
                });
            }

            String task = String.format(
                    "请基于以上患者信息和Drools规则引擎的评估结果，生成一份简洁的%s风险评估分析报告。\n" +
                    "要求：\n" +
                    "1. 解读关键指标的临床意义\n" +
                    "2. 分析当前风险等级的依据\n" +
                    "3. 给出3-5条具体的管理建议\n" +
                    "4. 控制在300字以内",
                    dt.getDisplayName());

            String result = llmFunction.apply(context.toString(), task);
            if (result != null && !result.isBlank()) {
                log.info("[AssessmentAgent] LLM analysis generated for patient {}", request.getPatientName());
                return result;
            }
        } catch (Exception e) {
            log.warn("[AssessmentAgent] LLM analysis failed, using Drools result: {}", e.getMessage());
        }
        return null;
    }
}
