package com.medical.agent.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.medical.agent.model.entity.AiChatSession;
import com.medical.agent.model.entity.AiChatMessage;
import com.medical.agent.model.entity.FollowUpPlan;
import com.medical.agent.model.entity.HealthRecord;
import com.medical.agent.model.entity.RiskAssessment;
import com.medical.agent.common.dto.RagEvidenceDTO;
import com.medical.agent.common.dto.RagQueryContextDTO;
import com.medical.agent.model.mongo.OrchestrationTaskSnapshot;
import com.medical.agent.service.chat.agent.ChatSupervisorService;
import com.medical.agent.service.chat.agent.ChatSupervisorService.ChatRunSession;
import com.medical.agent.service.chat.ChatContextService;
import com.medical.agent.service.chat.ChatPatientContextData;
import com.medical.agent.service.reference.agent.ReferenceSelectionSupervisorService;
import com.medical.agent.service.react.orchestration.OrchestrationWatchdogService;
import com.medical.agent.service.react.model.AgentState;
import com.medical.agent.service.react.model.AgentStep;
import com.medical.agent.service.react.model.ReActResult;
import com.medical.agent.model.repository.HealthRecordRepository;
import com.medical.agent.model.repository.RiskAssessmentRepository;
import com.medical.agent.model.repository.FollowUpPlanRepository;
import com.medical.agent.model.repository.AiChatSessionRepository;
import com.medical.agent.model.repository.AiChatMessageRepository;
import com.medical.agent.model.repository.DoctorRepository;
import com.medical.agent.model.repository.PatientRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * AI健康咨询服务 - 基于LLM的智能问答
 * 结合患者历史数据提供个性化健康咨询
 */
@Service
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);

    private final LlmService llmService;
    private final PatientRepository patientRepository;
    private final RiskAssessmentRepository assessmentRepository;
    private final FollowUpPlanRepository followUpPlanRepository;
    private final HealthRecordRepository healthRecordRepository;
    private final DoctorRepository doctorRepository;
    private final AiChatSessionRepository sessionRepository;
    private final AiChatMessageRepository messageRepository;
    private final RagRetrievalService ragRetrievalService;
    private final ChatContextService chatContextService;
    private final ChatSupervisorService chatSupervisorService;
    private final ReferenceSelectionSupervisorService referenceSelectionSupervisorService;
    private final OrchestrationWatchdogService orchestrationWatchdogService;
    private final ObjectMapper objectMapper;

    private static final Set<String> LOW_SIGNAL_MESSAGES = Set.of(
            "谢谢", "谢谢你", "好的", "好", "收到", "嗯", "嗯嗯", "在吗", "你好", "您好"
    );

    private static final int CHAT_REFERENCE_RETRIEVAL_LIMIT = 7;
    private static final int CHAT_REFERENCE_PROMPT_LIMIT = 5;
    private static final int CHAT_REFERENCE_DISPLAY_CANDIDATE_LIMIT = 4;
    private static final int CHAT_REFERENCE_DISPLAY_LIMIT = 3;

    private static final Set<String> HIDDEN_SOURCE_HINTS = Set.of(
            "chinese-medical-dialogue-data",
            "vector-smoke-test",
            "admin-live-index-test",
            "smoke",
            "dialogue_import_smoke",
            "dialogue_upload_smoke"
    );

    private static final Map<String, List<String>> CHAT_THEME_KEYWORDS = new LinkedHashMap<>();

    static {
        CHAT_THEME_KEYWORDS.put("diet", List.of("饮食", "食物", "吃", "主食", "水果", "蔬菜", "早餐", "午餐", "晚餐", "盐", "控盐", "糖", "脂肪"));
        CHAT_THEME_KEYWORDS.put("exercise", List.of("运动", "锻炼", "步行", "走路", "跑步", "有氧", "力量", "散步"));
        CHAT_THEME_KEYWORDS.put("medication", List.of("用药", "服药", "药", "停药", "漏服", "降压药", "降糖药", "胰岛素"));
        CHAT_THEME_KEYWORDS.put("metrics", List.of("血压", "血糖", "糖化", "hba1c", "空腹", "餐后", "指标", "化验", "bmi"));
        CHAT_THEME_KEYWORDS.put("monitoring", List.of("监测", "记录", "测量", "自测", "血压计", "血糖仪"));
        CHAT_THEME_KEYWORDS.put("risk", List.of("头晕", "胸痛", "胸闷", "气短", "呼吸困难", "急诊", "就医", "危险", "严重"));
        CHAT_THEME_KEYWORDS.put("followup", List.of("复查", "复诊", "随访", "下次", "检查"));
        CHAT_THEME_KEYWORDS.put("lifestyle", List.of("作息", "睡眠", "熬夜", "生活方式", "情绪", "压力", "戒烟", "饮酒"));
    }

    private static final String SYSTEM_PROMPT =
            "你是「慢病管家」，一个基层慢性病协同管理平台的AI健康顾问。\n" +
            "你的职责：\n" +
            "1. 回答患者关于慢性病（高血压、糖尿病、COPD、慢性肾病、冠心病、脑卒中）的健康咨询\n" +
            "2. 解读患者的健康评估报告和随访计划\n" +
            "3. 提供饮食、运动、用药等生活方式指导\n" +
            "4. 识别危急情况并建议及时就医\n\n" +
            "要求：\n" +
            "- 回答专业准确，符合中国慢性病管理指南\n" +
            "- 语言通俗易懂，避免过度使用医学术语\n" +
            "- 建议具体可执行\n" +
            "- 明确声明你是AI助手，不能替代医生诊疗\n" +
            "- 遇到紧急情况（如胸痛、严重高血压危象等），强烈建议立即就医\n" +
            "- 不要诊断疾病，只提供健康管理建议";

    public ChatService(LlmService llmService,
                       PatientRepository patientRepository,
                       RiskAssessmentRepository assessmentRepository,
                       FollowUpPlanRepository followUpPlanRepository,
                       HealthRecordRepository healthRecordRepository,
                       DoctorRepository doctorRepository,
                       AiChatSessionRepository sessionRepository,
                       AiChatMessageRepository messageRepository,
                       RagRetrievalService ragRetrievalService,
                       ChatContextService chatContextService,
                       ChatSupervisorService chatSupervisorService,
                       ReferenceSelectionSupervisorService referenceSelectionSupervisorService,
                       OrchestrationWatchdogService orchestrationWatchdogService,
                       ObjectMapper objectMapper) {
        this.llmService = llmService;
        this.patientRepository = patientRepository;
        this.assessmentRepository = assessmentRepository;
        this.followUpPlanRepository = followUpPlanRepository;
        this.healthRecordRepository = healthRecordRepository;
        this.doctorRepository = doctorRepository;
        this.sessionRepository = sessionRepository;
        this.messageRepository = messageRepository;
        this.ragRetrievalService = ragRetrievalService;
        this.chatContextService = chatContextService;
        this.chatSupervisorService = chatSupervisorService;
        this.referenceSelectionSupervisorService = referenceSelectionSupervisorService;
        this.orchestrationWatchdogService = orchestrationWatchdogService;
        this.objectMapper = objectMapper;
    }

    /**
     * 处理用户聊天消息
     * @param sessionId 会话ID
     * @param patientId 患者ID（可选，用于加载患者上下文）
     * @param userMessage 用户消息
     * @return AI回复
     */
    @Transactional
    public Map<String, Object> chat(String sessionId,
                                    String ownerRole,
                                    Long ownerUserId,
                                    Long patientId,
                                    String userMessage) {
        return chat(sessionId, ownerRole, ownerUserId, patientId, userMessage, null);
    }

    @Transactional
    public Map<String, Object> chat(String sessionId,
                                    String ownerRole,
                                    Long ownerUserId,
                                    Long patientId,
                                    String userMessage,
                                    Consumer<Map<String, Object>> initialResponseConsumer) {
        String normalizedSessionId = normalizeSessionId(sessionId);
        String normalizedOwnerRole = normalizeOwnerRole(ownerRole);
        Long resolvedOwnerUserId = resolveOwnerUserId(normalizedOwnerRole, ownerUserId, patientId);
        AiChatSession existingSession = sessionRepository.findById(normalizedSessionId).orElse(null);
        if (existingSession != null && !belongsToOwner(existingSession, normalizedOwnerRole, resolvedOwnerUserId)) {
            normalizedSessionId = UUID.randomUUID().toString();
        }
        final String effectiveSessionId = normalizedSessionId;
        final String effectiveOwnerRole = normalizedOwnerRole;
        final Long effectiveOwnerUserId = resolvedOwnerUserId;

        if (!llmService.isAvailable()) {
            return buildChatResponse(
                    effectiveSessionId,
                    "AI健康顾问暂未启用。请联系管理员配置LLM API密钥后使用此功能。\n\n" +
                    "您可以继续使用平台的其他功能：查看健康评估报告、随访计划等。",
                    List.of()
            );
        }

        // 清理超过30天的历史
        cleanupHistory();

        // 确保会话存在
        AiChatSession session = sessionRepository.findById(effectiveSessionId)
                .orElseGet(() -> {
                    AiChatSession s = AiChatSession.builder()
                            .id(effectiveSessionId)
                            .ownerRole(effectiveOwnerRole)
                            .ownerUserId(effectiveOwnerUserId)
                            .patientId(patientId)
                            .title(null)
                            .lastMessageAt(LocalDateTime.now())
                            .build();
                    return sessionRepository.save(s);
                });

        LocalDateTime cutoff = LocalDateTime.now().minusDays(30);
        List<AiChatMessage> history = messageRepository.findTop40BySessionIdAndCreatedAtAfterOrderByCreatedAtDesc(effectiveSessionId, cutoff);
        Collections.reverse(history);
        List<Map<String, String>> historyMessages = history.stream()
                .map(m -> Map.of("role", m.getRole(), "content", m.getContent()))
                .collect(Collectors.toList());

        messageRepository.save(AiChatMessage.builder()
                .sessionId(effectiveSessionId)
                .role("user")
                .content(userMessage)
                .build());

        try {
            AgentState agentState = new AgentState();
            agentState.setSessionId(effectiveSessionId);
            agentState.setOwnerRole(effectiveOwnerRole);
            agentState.setOwnerUserId(effectiveOwnerUserId);
            agentState.setPatientId(patientId);
            agentState.setUserMessage(userMessage);
            agentState.setHistoryMessages(historyMessages);
            agentState.setConversationPerspective(
                    chatContextService.buildConversationPerspective(
                            effectiveOwnerRole,
                            effectiveOwnerUserId,
                            patientId,
                            null
                    )
            );

            ChatRunSession runSession = chatSupervisorService.start(agentState);
            ReActResult initialResult = runSession.result();
            String initialReply = initialResult != null ? initialResult.reply() : null;
            if (initialResponseConsumer != null && initialResult != null) {
                if (initialReply == null || initialReply.isBlank()) {
                    initialReply = "我先给您一个初步判断，随后继续补充更完整的分析和证据。";
                }
                ChatReferenceBundle initialReferenceBundle = new ChatReferenceBundle(
                        initialResult.evidenceContext(),
                        initialResult.evidenceCandidates()
                );
                List<RagEvidenceDTO> initialDisplayReferences = finalizeChatReferences(userMessage, initialReply, initialReferenceBundle);
                initialResponseConsumer.accept(buildChatResponse(
                        effectiveSessionId,
                        initialReply,
                        initialReply,
                        initialDisplayReferences,
                        initialResult
                ));
            }

            ReActResult agentResult = chatSupervisorService.finish(runSession);
            String reply = agentResult != null ? agentResult.reply() : null;
            if (reply == null || reply.isBlank()) {
                reply = "抱歉，AI服务暂时无法响应，请稍后再试。";
            }

            ChatReferenceBundle referenceBundle = agentResult == null
                    ? ChatReferenceBundle.empty()
                    : new ChatReferenceBundle(agentResult.evidenceContext(), agentResult.evidenceCandidates());
            List<RagEvidenceDTO> displayReferences = finalizeChatReferences(userMessage, reply, referenceBundle);

            messageRepository.save(AiChatMessage.builder()
                    .sessionId(effectiveSessionId)
                    .role("assistant")
                    .content(reply)
                    .referencesJson(toReferencesJson(displayReferences))
                    .agentTraceJson(toAgentTraceJson(initialReply, reply, agentResult))
                    .build());
            session.setLastMessageAt(LocalDateTime.now());
            if (session.getTitle() == null && userMessage != null) {
                session.setTitle(generateTitle(userMessage));
            }
            session.setOwnerRole(effectiveOwnerRole);
            session.setOwnerUserId(effectiveOwnerUserId);
            session.setPatientId(patientId);
            sessionRepository.save(session);
            return buildChatResponse(effectiveSessionId, initialReply, reply, displayReferences, agentResult);
        } catch (Exception e) {
            log.error("[ChatService] Chat error: {}", e.getMessage(), e);
            return buildChatResponse(effectiveSessionId, "抱歉，处理您的问题时出现了错误，请稍后再试。", List.of());
        }
    }

    /**
     * 清除会话历史
     */
    @Transactional
    public void clearSession(String sessionId, String ownerRole, Long ownerUserId) {
        if (sessionId == null || sessionId.isBlank()) {
            return;
        }
        AiChatSession session = sessionRepository.findById(sessionId).orElse(null);
        if (session == null) {
            return;
        }
        if (!belongsToOwner(session, normalizeOwnerRole(ownerRole), ownerUserId)) {
            return;
        }
        messageRepository.deleteBySessionId(sessionId);
        sessionRepository.deleteById(sessionId);
    }

    public List<Map<String, Object>> listSessions(String ownerRole, Long ownerUserId, Long patientId) {
        String normalizedOwnerRole = normalizeOwnerRole(ownerRole);
        Long resolvedOwnerUserId = resolveOwnerUserId(normalizedOwnerRole, ownerUserId, patientId);
        if (normalizedOwnerRole == null || resolvedOwnerUserId == null) {
            return List.of();
        }
        List<AiChatSession> sessions = sessionRepository.findOwnedSessions(
                normalizedOwnerRole,
                resolvedOwnerUserId,
                patientId
        );
        return sessions.stream().map(s -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("id", s.getId());
                    m.put("title", s.getTitle());
                    m.put("patientId", s.getPatientId());
                    m.put("lastMessageAt", s.getLastMessageAt());
                    return m;
                })
                .collect(Collectors.toList());
    }

    public List<Map<String, Object>> getHistory(String sessionId, String ownerRole, Long ownerUserId) {
        if (sessionId == null || sessionId.isBlank()) {
            return List.of();
        }
        AiChatSession session = sessionRepository.findById(sessionId).orElse(null);
        if (session == null || !belongsToOwner(session, normalizeOwnerRole(ownerRole), ownerUserId)) {
            return List.of();
        }
        LocalDateTime cutoff = LocalDateTime.now().minusDays(30);
        List<AiChatMessage> history = messageRepository.findTop40BySessionIdAndCreatedAtAfterOrderByCreatedAtDesc(sessionId, cutoff);
        Collections.reverse(history);
        return history.stream()
                .filter(m -> !isLegacyRetryNoticeMessage(m))
                .map(m -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("id", m.getId());
                    map.put("role", m.getRole());
                    map.put("content", m.getContent());
                    map.put("references", parseReferencesJson(m.getReferencesJson()));
                    map.put("timestamp", m.getCreatedAt());
                    Map<String, Object> trace = parseAgentTraceJson(m.getAgentTraceJson());
                    map.put("usedTools", trace.getOrDefault("usedTools", List.of()));
                    map.put("steps", trace.getOrDefault("steps", List.of()));
                    map.put("safetyFlags", trace.getOrDefault("safetyFlags", List.of()));
                    map.put("answerPlan", trace.getOrDefault("answerPlan", ""));
                    map.put("thoughtSummary", trace.getOrDefault("thoughtSummary", ""));
                    map.put("goal", trace.getOrDefault("goal", ""));
                    map.put("progressSummary", trace.getOrDefault("progressSummary", ""));
                    map.put("nextAction", trace.getOrDefault("nextAction", ""));
                    map.put("blockers", trace.getOrDefault("blockers", List.of()));
                    map.put("degradationNotes", trace.getOrDefault("degradationNotes", List.of()));
                    map.put("knowledgeGraphSummary", trace.getOrDefault("knowledgeGraphSummary", ""));
                    map.put("knowledgeGraphItems", trace.getOrDefault("knowledgeGraphItems", List.of()));
                    map.put("usedCloudPatientData", trace.getOrDefault("usedCloudPatientData", false));
                    map.put("previewContent", trace.getOrDefault("previewContent", ""));
                    map.put("finalContent", trace.getOrDefault("finalContent", m.getContent()));
                    map.put("orchestrationTrace", trace.get("orchestrationTrace"));
                    return map;
                })
                .collect(Collectors.toList());
    }

    public Map<String, Object> getBootstrap(String ownerRole, Long ownerUserId, Long patientId) {
        List<Map<String, Object>> sessions = listSessions(ownerRole, ownerUserId, patientId);
        String activeSessionId = sessions.isEmpty() ? null : String.valueOf(sessions.get(0).get("id"));
        List<Map<String, Object>> messages = activeSessionId == null
                ? List.of()
                : getHistory(activeSessionId, ownerRole, ownerUserId);
        Map<String, Object> result = new HashMap<>();
        result.put("sessions", sessions);
        result.put("activeSessionId", activeSessionId);
        result.put("messages", messages);
        return result;
    }

    @Transactional
    public void appendOrchestrationRetryNotice(OrchestrationTaskSnapshot snapshot, String operator, String reason) {
        if (snapshot == null || !hasText(snapshot.getSessionId())) {
            return;
        }
        String content = "系统提示：管理员已触发这条会话对应的后台自治任务重新执行。"
                + "系统会在重跑完成后，将新的补充分析追加到当前会话中。";
        Map<String, Object> tracePayload = manualTracePayload(
                content,
                snapshot.getGoal(),
                "管理员从编排台触发了强制重新执行。",
                "后台自治任务已重新进入执行链路。",
                "等待后台补充分析完成。",
                reason == null || reason.isBlank() ? List.of() : List.of("管理员重跑原因：" + reason),
                Map.of(
                        "taskId", snapshot.getTaskId(),
                        "controlAction", "manual_retry",
                        "operator", operator == null ? "" : operator
                )
        );
        appendSessionMessage(snapshot, "assistant", content, null, toTraceJson(tracePayload));
    }

    @Transactional
    public void appendOrchestrationRetryResult(OrchestrationTaskSnapshot snapshot, String reply, ReActResult agentResult) {
        if (snapshot == null || !hasText(snapshot.getSessionId())) {
            return;
        }
        String normalizedReply = resolveRetryOutcomeReply(snapshot, reply);
        String content = buildRetryOutcomeContent(snapshot, normalizedReply);
        List<RagEvidenceDTO> displayReferences = agentResult == null
                ? List.of()
                : finalizeChatReferences(
                        snapshot.getUserMessage(),
                        normalizedReply,
                        new ChatReferenceBundle(agentResult.evidenceContext(), agentResult.evidenceCandidates())
                );
        String traceJson = agentResult == null
                ? toTraceJson(manualTracePayload(
                        content,
                        snapshot.getGoal(),
                        "后台重跑已经收敛，以下为补充分析结果。",
                        snapshot.getProgressSummary(),
                        snapshot.getNextAction(),
                        snapshot.getBlockers() == null ? List.of() : snapshot.getBlockers(),
                        Map.of(
                                "taskId", snapshot.getTaskId(),
                                "controlAction", "manual_retry_result"
                        )
                ))
                : toAgentTraceJson(content, content, agentResult);
        appendSessionMessage(snapshot, "assistant", content, toReferencesJson(displayReferences), traceJson);
    }

    private String resolveRetryOutcomeReply(OrchestrationTaskSnapshot snapshot, String reply) {
        if (hasText(reply)) {
            return reply.trim();
        }
        if (snapshot != null && "BLOCKED".equals(snapshot.getStatus())) {
            StringBuilder builder = new StringBuilder("管理员触发后台重新执行后，任务已重新执行，但当前仍然被阻塞。\n\n");
            if (hasText(snapshot.getProgressSummary())) {
                builder.append("当前进展：").append(snapshot.getProgressSummary().trim()).append("\n\n");
            }
            List<String> blockers = snapshot.getBlockers() == null ? List.of() : snapshot.getBlockers().stream()
                    .filter(text -> hasText(text))
                    .map(String::trim)
                    .toList();
            if (!blockers.isEmpty()) {
                builder.append("当前阻塞原因：\n");
                for (int index = 0; index < blockers.size(); index++) {
                    builder.append(index + 1).append(". ").append(blockers.get(index)).append("\n");
                }
                builder.append("\n");
            }
            if (hasText(snapshot.getNextAction())) {
                builder.append("建议下一步：").append(snapshot.getNextAction().trim());
            } else {
                builder.append("建议结合管理员编排时间线继续核查该任务的阻塞原因。");
            }
            return builder.toString().trim();
        }
        return "管理员触发的后台重新执行已经完成，但系统未生成新的补充正文，请结合编排时间线继续核查。";
    }

    private String buildRetryOutcomeContent(OrchestrationTaskSnapshot snapshot, String normalizedReply) {
        if (snapshot != null && "BLOCKED".equals(snapshot.getStatus())) {
            return "管理员触发后台重新执行后，任务仍未完成，当前阻塞信息如下：\n\n" + normalizedReply;
        }
        return "管理员触发后台重新执行后，系统补充分析如下：\n\n" + normalizedReply;
    }

    private boolean isLegacyRetryNoticeMessage(AiChatMessage message) {
        if (message == null || !"assistant".equalsIgnoreCase(message.getRole())) {
            return false;
        }
        String content = message.getContent();
        if (content == null || content.isBlank()) {
            return false;
        }
        return content.contains("后台自治任务重新执行")
                || content.contains("新的补充分析追加到当前会话")
                || content.contains("后台重新执行中");
    }

    public Map<String, Object> getSessionOrchestrationStatus(String sessionId, String ownerRole, Long ownerUserId) {
        Map<String, Object> emptyPayload = new LinkedHashMap<>();
        emptyPayload.put("sessionId", sessionId);
        emptyPayload.put("hasTask", false);

        if (sessionId == null || sessionId.isBlank()) {
            return emptyPayload;
        }
        AiChatSession session = sessionRepository.findById(sessionId).orElse(null);
        if (session == null || !belongsToOwner(session, normalizeOwnerRole(ownerRole), ownerUserId)) {
            return emptyPayload;
        }

        Map<String, Object> payload = orchestrationWatchdogService.getLatestSessionTask(sessionId)
                .map(LinkedHashMap::new)
                .orElseGet(LinkedHashMap::new);
        if (payload.isEmpty()) {
            return emptyPayload;
        }
        payload.put("hasTask", true);
        return payload;
    }

    /**
     * 定期清理30天前的会话及消息
     */
    public void cleanupHistory() {
        LocalDateTime threshold = LocalDateTime.now().minusDays(30);
        messageRepository.deleteByCreatedAtBefore(threshold);
        sessionRepository.deleteByLastMessageAtBefore(threshold);
    }

    /**
     * 构建患者上下文信息
     */
    private ChatPatientContextData loadPatientContext(Long patientId) {
        return chatContextService.loadPatientContext(patientId);
    }

    private String buildConversationPerspective(String ownerRole,
                                                Long ownerUserId,
                                                ChatPatientContextData patientContext) {
        Long boundPatientId = patientContext != null && patientContext.patient() != null
                ? patientContext.patient().getId()
                : null;
        return chatContextService.buildConversationPerspective(ownerRole, ownerUserId, boundPatientId, patientContext);
    }

    private String resolveDoctorName(Long doctorId) {
        return chatContextService.resolveDoctorName(doctorId);
    }

    private String resolvePatientOwnerName(Long patientId) {
        return chatContextService.resolvePatientOwnerName(patientId);
    }

    private ChatReferenceBundle prepareChatReferenceBundle(String userMessage, ChatPatientContextData patientContext) {
        if (userMessage == null || userMessage.isBlank()) {
            return ChatReferenceBundle.empty();
        }
        String normalized = userMessage.trim();
        if (normalized.length() <= 2 || LOW_SIGNAL_MESSAGES.contains(normalized)) {
            return ChatReferenceBundle.empty();
        }

        RagQueryContextDTO context;
        if (patientContext != null) {
            context = ragRetrievalService.buildContext(
                    "AI健康问答",
                    normalized,
                    patientContext.patient(),
                    patientContext.assessments(),
                    patientContext.plans(),
                    patientContext.latestRecord(),
                    patientContext.medicalHistorySummary()
            );
        } else {
            context = RagQueryContextDTO.builder()
                    .scenario("AI健康问答")
                    .focus(normalized)
                    .queryText("场景：AI健康问答\n用户问题：" + normalized)
                    .diseaseKeys(List.of())
                    .topicTags(List.of("general"))
                    .audience("doctor")
                    .build();
        }

        List<RagEvidenceDTO> retrieved = ragRetrievalService.retrieveKnowledge(context, CHAT_REFERENCE_RETRIEVAL_LIMIT);
        List<RagEvidenceDTO> filtered = filterReferencesByRules(
                retrieved,
                context,
                normalized,
                CHAT_REFERENCE_PROMPT_LIMIT,
                false,
                null
        );
        return new ChatReferenceBundle(context, filtered);
    }

    private List<RagEvidenceDTO> finalizeChatReferences(String userMessage,
                                                        String reply,
                                                        ChatReferenceBundle referenceBundle) {
        if (referenceBundle == null || referenceBundle.candidates().isEmpty()) {
            return List.of();
        }

        List<RagEvidenceDTO> displayCandidates = filterReferencesByRules(
                referenceBundle.candidates(),
                referenceBundle.context(),
                userMessage,
                CHAT_REFERENCE_DISPLAY_CANDIDATE_LIMIT,
                true,
                reply
        );
        if (displayCandidates.isEmpty()) {
            return List.of();
        }

        List<RagEvidenceDTO> judged = referenceSelectionSupervisorService.judgeChatDisplayReferences(
                userMessage,
                reply,
                displayCandidates,
                CHAT_REFERENCE_DISPLAY_LIMIT
        );
        if (judged != null) {
            return judged.stream().limit(CHAT_REFERENCE_DISPLAY_LIMIT).toList();
        }
        return displayCandidates.stream().limit(CHAT_REFERENCE_DISPLAY_LIMIT).toList();
    }

    private List<RagEvidenceDTO> filterReferencesByRules(List<RagEvidenceDTO> candidates,
                                                         RagQueryContextDTO context,
                                                         String userMessage,
                                                         int limit,
                                                         boolean displayMode,
                                                         String reply) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }

        List<String> themeKeys = detectThemes(userMessage, reply);
        Set<String> diseaseKeys = normalizeDiseaseKeys(context);
        boolean hasSpecificDisease = diseaseKeys.stream().anyMatch(key -> !"general".equals(key));
        Map<String, RagEvidenceDTO> deduped = new LinkedHashMap<>();

        List<RagEvidenceDTO> sorted = candidates.stream()
                .sorted(Comparator.comparingDouble(this::referenceScore).reversed())
                .toList();

        for (RagEvidenceDTO item : sorted) {
            if (!isDisplayableSource(item)) {
                continue;
            }
            if (hasSpecificDisease && isConflictingDisease(item, diseaseKeys)) {
                continue;
            }
            if (!themeKeys.isEmpty() && !isTopicAligned(item, themeKeys, displayMode)) {
                continue;
            }
            if (displayMode && !isAnswerAligned(item, reply, themeKeys)) {
                continue;
            }

            String key = buildDocumentKey(item);
            RagEvidenceDTO existing = deduped.get(key);
            if (existing == null || compareReferencePriority(item, existing, diseaseKeys) < 0) {
                deduped.put(key, item);
            }
        }

        List<RagEvidenceDTO> result = deduped.values().stream()
                .sorted((left, right) -> compareReferencePriority(left, right, diseaseKeys))
                .limit(limit)
                .toList();

        if (!result.isEmpty()) {
            return result;
        }
        return sorted.stream()
                .filter(this::isDisplayableSource)
                .limit(Math.min(limit, sorted.size()))
                .toList();
    }

    private Set<String> normalizeDiseaseKeys(RagQueryContextDTO context) {
        LinkedHashSet<String> keys = new LinkedHashSet<>();
        if (context == null || context.getDiseaseKeys() == null) {
            return keys;
        }
        for (String key : context.getDiseaseKeys()) {
            if (key != null && !key.isBlank()) {
                keys.add(key.trim().toLowerCase(Locale.ROOT));
            }
        }
        return keys;
    }

    private boolean isDisplayableSource(RagEvidenceDTO item) {
        String searchable = joinText(item.getSource(), item.getTitle(), item.getSection());
        for (String hint : HIDDEN_SOURCE_HINTS) {
            if (searchable.contains(hint.toLowerCase(Locale.ROOT))) {
                return false;
            }
        }
        return true;
    }

    private boolean isConflictingDisease(RagEvidenceDTO item, Set<String> diseaseKeys) {
        if (item == null || item.getDiseaseKey() == null || diseaseKeys.isEmpty()) {
            return false;
        }
        String diseaseKey = item.getDiseaseKey().trim().toLowerCase(Locale.ROOT);
        return !"general".equals(diseaseKey) && !diseaseKeys.contains(diseaseKey);
    }

    private boolean isTopicAligned(RagEvidenceDTO item, List<String> themeKeys, boolean displayMode) {
        if (themeKeys == null || themeKeys.isEmpty()) {
            return true;
        }
        String strongText = joinText(item.getTitle(), item.getSection(), item.getSource());
        String weakText = joinText(item.getContentSnippet());
        int strongHits = countThemeHits(strongText, themeKeys);
        int weakHits = countThemeHits(weakText, themeKeys);
        if (strongHits > 0) {
            return true;
        }
        return !displayMode && weakHits >= 2;
    }

    private boolean isAnswerAligned(RagEvidenceDTO item, String reply, List<String> themeKeys) {
        if (reply == null || reply.isBlank()) {
            return true;
        }
        if (themeKeys == null || themeKeys.isEmpty()) {
            return true;
        }
        String answerText = reply.toLowerCase(Locale.ROOT);
        String titleSection = joinText(item.getTitle(), item.getSection());
        if (!titleSection.isBlank()) {
            for (String token : titleSection.split("\\s+")) {
                String word = token.trim();
                if (word.length() >= 4 && answerText.contains(word)) {
                    return true;
                }
            }
        }
        return countThemeHits(answerText, themeKeys) > 0 && isTopicAligned(item, themeKeys, true);
    }

    private int countThemeHits(String text, List<String> themeKeys) {
        if (text == null || text.isBlank() || themeKeys == null || themeKeys.isEmpty()) {
            return 0;
        }
        int hits = 0;
        for (String theme : themeKeys) {
            List<String> keywords = CHAT_THEME_KEYWORDS.getOrDefault(theme, List.of());
            if (keywords.stream().anyMatch(keyword -> text.contains(keyword.toLowerCase(Locale.ROOT)))) {
                hits++;
            }
        }
        return hits;
    }

    private List<String> detectThemes(String userMessage, String reply) {
        String text = joinText(userMessage, reply);
        if (text.isBlank()) {
            return List.of();
        }
        List<String> themes = new ArrayList<>();
        for (Map.Entry<String, List<String>> entry : CHAT_THEME_KEYWORDS.entrySet()) {
            if (entry.getValue().stream().anyMatch(keyword -> text.contains(keyword.toLowerCase(Locale.ROOT)))) {
                themes.add(entry.getKey());
            }
        }
        return themes;
    }

    private int compareReferencePriority(RagEvidenceDTO left, RagEvidenceDTO right, Set<String> diseaseKeys) {
        double leftWeight = referenceScore(left) + diseaseWeight(left, diseaseKeys);
        double rightWeight = referenceScore(right) + diseaseWeight(right, diseaseKeys);
        return Double.compare(rightWeight, leftWeight);
    }

    private double diseaseWeight(RagEvidenceDTO item, Set<String> diseaseKeys) {
        if (item == null || item.getDiseaseKey() == null) {
            return 0D;
        }
        String key = item.getDiseaseKey().trim().toLowerCase(Locale.ROOT);
        if (diseaseKeys.contains(key) && !"general".equals(key)) {
            return 0.4D;
        }
        if ("general".equals(key)) {
            return -0.2D;
        }
        return -0.4D;
    }

    private double referenceScore(RagEvidenceDTO item) {
        if (item == null || item.getScore() == null) {
            return 0D;
        }
        return item.getScore();
    }

    private String buildDocumentKey(RagEvidenceDTO item) {
        return joinText(item.getSourceType(), item.getTitle(), item.getSource(), item.getPublishedAt());
    }

    private String joinText(String... parts) {
        return Arrays.stream(parts)
                .filter(part -> part != null && !part.isBlank())
                .map(part -> part.toLowerCase(Locale.ROOT))
                .collect(Collectors.joining(" "));
    }

    private void appendMetric(StringBuilder ctx, String label, Object value, String unit) {
        if (value == null) {
            return;
        }
        ctx.append("  - ").append(label).append("：").append(value);
        if (unit != null && !unit.isBlank()) {
            ctx.append(' ').append(unit);
        }
        ctx.append('\n');
    }

    private String generateTitle(String userMessage) {
        if (userMessage == null) return null;
        String trimmed = userMessage.trim();
        return trimmed.length() > 20 ? trimmed.substring(0, 20) + "..." : trimmed;
    }

    private void appendSessionMessage(OrchestrationTaskSnapshot snapshot,
                                      String role,
                                      String content,
                                      String referencesJson,
                                      String traceJson) {
        if (snapshot == null || !hasText(snapshot.getSessionId()) || !hasText(content)) {
            return;
        }
        AiChatSession session = sessionRepository.findById(snapshot.getSessionId()).orElse(null);
        if (session == null) {
            return;
        }
        messageRepository.save(AiChatMessage.builder()
                .sessionId(snapshot.getSessionId())
                .role(role)
                .content(content)
                .referencesJson(referencesJson)
                .agentTraceJson(traceJson)
                .build());
        session.setLastMessageAt(LocalDateTime.now());
        if (session.getTitle() == null && hasText(snapshot.getUserMessage())) {
            session.setTitle(generateTitle(snapshot.getUserMessage()));
        }
        sessionRepository.save(session);
    }

    private Map<String, Object> buildChatResponse(String sessionId,
                                                  String previewReply,
                                                  String reply,
                                                  List<RagEvidenceDTO> references,
                                                  ReActResult agentResult) {
        Map<String, Object> result = new HashMap<>();
        result.put("sessionId", sessionId);
        result.put("reply", reply);
        result.put("previewContent", previewReply == null ? "" : previewReply);
        result.put("finalContent", reply);
        result.put("references", references == null ? List.of() : references);
        result.put("usedTools", agentResult == null ? List.of() : agentResult.usedTools());
        result.put("steps", agentResult == null ? List.of() : summarizeSteps(agentResult.steps()));
        result.put("safetyFlags", agentResult == null ? List.of() : agentResult.safetyFlags().stream().toList());
        result.put("answerPlan", agentResult == null ? "" : agentResult.answerPlan());
        result.put("thoughtSummary", agentResult == null ? "" : agentResult.thoughtSummary());
        result.put("goal", agentResult == null ? "" : agentResult.goal());
        result.put("progressSummary", agentResult == null ? "" : agentResult.progressSummary());
        result.put("nextAction", agentResult == null ? "" : agentResult.nextAction());
        result.put("blockers", agentResult == null ? List.of() : agentResult.blockers());
        result.put("degradationNotes", agentResult == null ? List.of() : agentResult.degradationNotes());
        result.put("knowledgeGraphSummary", agentResult == null ? "" : agentResult.knowledgeGraphSummary());
        result.put("knowledgeGraphItems", agentResult == null ? List.of() : agentResult.knowledgeGraphItems());
        result.put("usedCloudPatientData", usedCloudPatientData(agentResult));
        result.put("orchestrationTrace", agentResult == null ? null : agentResult.orchestrationTrace());
        return result;
    }

    private Map<String, Object> buildChatResponse(String sessionId, String reply, List<RagEvidenceDTO> references) {
        return buildChatResponse(sessionId, reply, reply, references, null);
    }

    private String toReferencesJson(List<RagEvidenceDTO> references) {
        if (references == null || references.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(references);
        } catch (Exception e) {
            log.warn("[ChatService] Failed to serialize references: {}", e.getMessage());
            return null;
        }
    }

    private String toAgentTraceJson(String previewReply, String finalReply, ReActResult agentResult) {
        if (agentResult == null) {
            return null;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("previewContent", previewReply == null ? "" : previewReply);
        payload.put("finalContent", finalReply == null ? "" : finalReply);
        payload.put("usedTools", agentResult.usedTools() == null ? List.of() : agentResult.usedTools());
        payload.put("steps", summarizeSteps(agentResult.steps()));
        payload.put("safetyFlags", agentResult.safetyFlags() == null ? List.of() : agentResult.safetyFlags().stream().toList());
        payload.put("answerPlan", agentResult.answerPlan() == null ? "" : agentResult.answerPlan());
        payload.put("thoughtSummary", agentResult.thoughtSummary() == null ? "" : agentResult.thoughtSummary());
        payload.put("goal", agentResult.goal() == null ? "" : agentResult.goal());
        payload.put("progressSummary", agentResult.progressSummary() == null ? "" : agentResult.progressSummary());
        payload.put("nextAction", agentResult.nextAction() == null ? "" : agentResult.nextAction());
        payload.put("blockers", agentResult.blockers() == null ? List.of() : agentResult.blockers());
        payload.put("degradationNotes", agentResult.degradationNotes() == null ? List.of() : agentResult.degradationNotes());
        payload.put("knowledgeGraphSummary", agentResult.knowledgeGraphSummary() == null ? "" : agentResult.knowledgeGraphSummary());
        payload.put("knowledgeGraphItems", agentResult.knowledgeGraphItems() == null ? List.of() : agentResult.knowledgeGraphItems());
        payload.put("usedCloudPatientData", usedCloudPatientData(agentResult));
        payload.put("orchestrationTrace", agentResult.orchestrationTrace());
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            log.warn("[ChatService] Failed to serialize agent trace: {}", e.getMessage());
            return null;
        }
    }

    private String toTraceJson(Map<String, Object> payload) {
        if (payload == null || payload.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            log.warn("[ChatService] Failed to serialize manual trace json: {}", e.getMessage());
            return null;
        }
    }

    private List<Map<String, Object>> summarizeSteps(List<AgentStep> steps) {
        if (steps == null || steps.isEmpty()) {
            return List.of();
        }
        return steps.stream().map(step -> {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("index", step.index());
            map.put("actionName", step.actionName());
            map.put("reasoningSummary", step.reasoningSummary());
            map.put("observationSummary", step.observationSummary());
            return map;
        }).toList();
    }

    private Map<String, Object> parseAgentTraceJson(String agentTraceJson) {
        if (agentTraceJson == null || agentTraceJson.isBlank()) {
            return emptyAgentTraceMap();
        }
        try {
            return objectMapper.readValue(agentTraceJson, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.warn("[ChatService] Failed to parse agent trace json: {}", e.getMessage());
            return emptyAgentTraceMap();
        }
    }

    private Map<String, Object> emptyAgentTraceMap() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("usedTools", List.of());
        payload.put("steps", List.of());
        payload.put("safetyFlags", List.of());
        payload.put("previewContent", "");
        payload.put("finalContent", "");
        payload.put("answerPlan", "");
        payload.put("thoughtSummary", "");
        payload.put("goal", "");
        payload.put("progressSummary", "");
        payload.put("nextAction", "");
        payload.put("blockers", List.of());
        payload.put("degradationNotes", List.of());
        payload.put("knowledgeGraphSummary", "");
        payload.put("knowledgeGraphItems", List.of());
        payload.put("usedCloudPatientData", false);
        payload.put("orchestrationTrace", null);
        return payload;
    }

    private Map<String, Object> manualTracePayload(String previewContent,
                                                   String goal,
                                                   String thoughtSummary,
                                                   String progressSummary,
                                                   String nextAction,
                                                   List<String> blockers,
                                                   Map<String, Object> orchestrationTrace) {
        Map<String, Object> payload = emptyAgentTraceMap();
        payload.put("previewContent", previewContent == null ? "" : previewContent);
        payload.put("finalContent", previewContent == null ? "" : previewContent);
        payload.put("goal", goal == null ? "" : goal);
        payload.put("thoughtSummary", thoughtSummary == null ? "" : thoughtSummary);
        payload.put("progressSummary", progressSummary == null ? "" : progressSummary);
        payload.put("nextAction", nextAction == null ? "" : nextAction);
        payload.put("blockers", blockers == null ? List.of() : blockers);
        payload.put("orchestrationTrace", orchestrationTrace == null ? null : orchestrationTrace);
        return payload;
    }

    private boolean usedCloudPatientData(ReActResult agentResult) {
        if (agentResult == null || agentResult.usedTools() == null) {
            return false;
        }
        return agentResult.usedTools().stream()
                .filter(Objects::nonNull)
                .map(tool -> tool.toLowerCase(Locale.ROOT))
                .anyMatch(tool -> tool.contains("medplum"));
    }

    private List<Map<String, Object>> parseReferencesJson(String referencesJson) {
        if (referencesJson == null || referencesJson.isBlank()) {
            return List.of();
        }
        try {
            List<Map<String, Object>> references = objectMapper.readValue(
                    referencesJson,
                    new TypeReference<List<Map<String, Object>>>() {}
            );
            return references.stream()
                    .sorted(Comparator.comparingDouble(this::extractScore).reversed())
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("[ChatService] Failed to parse references json: {}", e.getMessage());
            return List.of();
        }
    }

    private double extractScore(Map<String, Object> reference) {
        if (reference == null) {
            return 0D;
        }
        Object score = reference.get("score");
        if (score instanceof Number number) {
            return number.doubleValue();
        }
        if (score != null) {
            try {
                return Double.parseDouble(score.toString());
            } catch (NumberFormatException ignored) {
                return 0D;
            }
        }
        return 0D;
    }

    private String normalizeSessionId(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return UUID.randomUUID().toString();
        }
        return sessionId;
    }

    private String normalizeOwnerRole(String ownerRole) {
        if (ownerRole == null || ownerRole.isBlank()) {
            return null;
        }
        String normalized = ownerRole.trim().toLowerCase(Locale.ROOT);
        if (!"doctor".equals(normalized) && !"patient".equals(normalized)) {
            return null;
        }
        return normalized;
    }

    private Long resolveOwnerUserId(String ownerRole, Long ownerUserId, Long patientId) {
        if (ownerUserId != null) {
            return ownerUserId;
        }
        if ("patient".equals(ownerRole)) {
            return patientId;
        }
        return null;
    }

    private boolean belongsToOwner(AiChatSession session, String ownerRole, Long ownerUserId) {
        if (session == null || ownerRole == null || ownerUserId == null) {
            return false;
        }
        return ownerRole.equalsIgnoreCase(session.getOwnerRole())
                && ownerUserId.equals(session.getOwnerUserId());
    }

    private boolean hasText(String text) {
        return text != null && !text.isBlank();
    }

    private record ChatReferenceBundle(RagQueryContextDTO context, List<RagEvidenceDTO> candidates) {
        private static ChatReferenceBundle empty() {
            return new ChatReferenceBundle(null, List.of());
        }
    }

}
