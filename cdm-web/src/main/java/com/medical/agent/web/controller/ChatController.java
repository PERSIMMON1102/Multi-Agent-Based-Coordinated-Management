package com.medical.agent.web.controller;

import com.medical.agent.service.ChatService;
import com.medical.agent.service.LlmService;
import com.medical.agent.web.security.AccessGuard;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * AI健康咨询控制器
 */
@RestController
@RequestMapping("/api/chat")
@PreAuthorize("hasAnyRole('DOCTOR','PATIENT')")
public class ChatController {

    private final ChatService chatService;
    private final LlmService llmService;
    private final AccessGuard accessGuard;

    @Autowired
    public ChatController(ChatService chatService,
                          LlmService llmService,
                          AccessGuard accessGuard) {
        this.chatService = chatService;
        this.llmService = llmService;
        this.accessGuard = accessGuard;
    }

    ChatController(ChatService chatService, LlmService llmService) {
        this(chatService, llmService, new AccessGuard(null, null, null, null, null, null));
    }

    /**
     * 发送聊天消息
     * POST /api/chat/send
     * Body: { "sessionId": "xxx", "patientId": 1, "message": "我的血压高怎么办？" }
     */
    @PostMapping("/send")
    public ResponseEntity<Map<String, Object>> sendMessage(@RequestBody Map<String, Object> request) {
        ChatRequest chatRequest;
        try {
            chatRequest = resolveChatRequest(request);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
        accessGuard.requireChatAccess(chatRequest.patientId());

        Map<String, Object> chatResult = chatService.chat(
                chatRequest.sessionId(),
                accessGuard.currentChatOwnerRole(),
                accessGuard.currentChatOwnerUserId(),
                chatRequest.patientId(),
                chatRequest.message()
        );

        Map<String, Object> response = buildChatResponse(chatResult, chatRequest.sessionId());
        return ResponseEntity.ok(response);
    }

    @PostMapping(path = "/send-stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter sendMessageStream(@RequestBody Map<String, Object> request) {
        ChatRequest chatRequest = resolveChatRequest(request);
        accessGuard.requireChatAccess(chatRequest.patientId());
        String ownerRole = accessGuard.currentChatOwnerRole();
        Long ownerUserId = accessGuard.currentChatOwnerUserId();

        SseEmitter emitter = new SseEmitter(Duration.ofMinutes(15).toMillis());
        CompletableFuture.runAsync(() -> {
            try {
                sendEvent(emitter, "meta", Map.of("sessionId", chatRequest.sessionId()));
                sendEvent(emitter, "status", Map.of("phase", "thinking"));

                Map<String, Object> chatResult = chatService.chat(
                        chatRequest.sessionId(),
                        ownerRole,
                        ownerUserId,
                        chatRequest.patientId(),
                        chatRequest.message(),
                        initialResponse -> {
                            try {
                                streamTrace(emitter, initialResponse);
                                streamArtifacts(emitter, initialResponse);
                                streamText(emitter, "thought", (String) initialResponse.getOrDefault("thoughtSummary", ""));
                                sendEvent(emitter, "status", Map.of("phase", "answering"));
                                streamText(emitter, "answer", (String) initialResponse.getOrDefault("reply", ""));
                            } catch (Exception streamException) {
                                throw new RuntimeException(streamException);
                            }
                        }
                );
                Map<String, Object> response = buildChatResponse(chatResult, chatRequest.sessionId());

                sendEvent(emitter, "done", response);
                emitter.complete();
            } catch (Exception ex) {
                try {
                    sendEvent(emitter, "error", Map.of("message", ex.getMessage() == null ? "流式聊天失败" : ex.getMessage()));
                } catch (IOException ignored) {
                    // ignore secondary failure
                }
                emitter.completeWithError(ex);
            }
        });
        return emitter;
    }

    private ChatRequest resolveChatRequest(Map<String, Object> request) {
        Object sessionIdValue = request.get("sessionId");
        String sessionId = sessionIdValue == null || sessionIdValue.toString().isBlank()
                ? UUID.randomUUID().toString()
                : sessionIdValue.toString();
        Long patientId = request.get("patientId") != null ?
                Long.valueOf(request.get("patientId").toString()) : null;
        String message = (String) request.get("message");

        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("消息不能为空");
        }
        return new ChatRequest(sessionId, patientId, message);
    }

    private Map<String, Object> buildChatResponse(Map<String, Object> chatResult, String sessionId) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("sessionId", chatResult.getOrDefault("sessionId", sessionId));
        response.put("reply", chatResult.getOrDefault("reply", ""));
        response.put("previewContent", chatResult.getOrDefault("previewContent", ""));
        response.put("finalContent", chatResult.getOrDefault("finalContent", chatResult.getOrDefault("reply", "")));
        response.put("references", chatResult.getOrDefault("references", java.util.List.of()));
        response.put("usedTools", chatResult.getOrDefault("usedTools", java.util.List.of()));
        response.put("steps", chatResult.getOrDefault("steps", java.util.List.of()));
        response.put("safetyFlags", chatResult.getOrDefault("safetyFlags", java.util.List.of()));
        response.put("answerPlan", chatResult.getOrDefault("answerPlan", ""));
        response.put("thoughtSummary", chatResult.getOrDefault("thoughtSummary", ""));
        response.put("goal", chatResult.getOrDefault("goal", ""));
        response.put("progressSummary", chatResult.getOrDefault("progressSummary", ""));
        response.put("nextAction", chatResult.getOrDefault("nextAction", ""));
        response.put("blockers", chatResult.getOrDefault("blockers", java.util.List.of()));
        response.put("degradationNotes", chatResult.getOrDefault("degradationNotes", java.util.List.of()));
        response.put("knowledgeGraphSummary", chatResult.getOrDefault("knowledgeGraphSummary", ""));
        response.put("knowledgeGraphItems", chatResult.getOrDefault("knowledgeGraphItems", java.util.List.of()));
        response.put("usedCloudPatientData", chatResult.getOrDefault("usedCloudPatientData", false));
        response.put("timestamp", System.currentTimeMillis());
        return response;
    }

    private void streamText(SseEmitter emitter, String eventName, String text) throws IOException, InterruptedException {
        List<String> chunks = chunkText(text, 38);
        for (String chunk : chunks) {
            sendEvent(emitter, eventName, Map.of("delta", chunk));
            Thread.sleep(90);
        }
    }

    @SuppressWarnings("unchecked")
    private void streamTrace(SseEmitter emitter, Map<String, Object> response) throws IOException, InterruptedException {
        Object stepsValue = response.get("steps");
        if (!(stepsValue instanceof List<?> rawSteps) || rawSteps.isEmpty()) {
            return;
        }
        int total = rawSteps.size();
        for (int i = 0; i < total; i++) {
            Object rawStep = rawSteps.get(i);
            if (!(rawStep instanceof Map<?, ?> stepMap)) {
                continue;
            }
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("step", new LinkedHashMap<>((Map<String, Object>) stepMap));
            payload.put("stepIndex", i);
            payload.put("totalSteps", total);
            sendEvent(emitter, "trace", payload);
            Thread.sleep(110);
        }
    }

    private void streamArtifacts(SseEmitter emitter, Map<String, Object> response) throws IOException {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("usedTools", response.getOrDefault("usedTools", List.of()));
        payload.put("safetyFlags", response.getOrDefault("safetyFlags", List.of()));
        payload.put("goal", response.getOrDefault("goal", ""));
        payload.put("progressSummary", response.getOrDefault("progressSummary", ""));
        payload.put("nextAction", response.getOrDefault("nextAction", ""));
        payload.put("blockers", response.getOrDefault("blockers", List.of()));
        payload.put("degradationNotes", response.getOrDefault("degradationNotes", List.of()));
        payload.put("knowledgeGraphSummary", response.getOrDefault("knowledgeGraphSummary", ""));
        payload.put("knowledgeGraphItems", response.getOrDefault("knowledgeGraphItems", List.of()));
        payload.put("usedCloudPatientData", response.getOrDefault("usedCloudPatientData", false));
        sendEvent(emitter, "artifacts", payload);
    }

    private List<String> chunkText(String text, int maxChunkLength) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        List<String> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            current.append(ch);
            boolean sentenceEnd = "。！？；!?;\n".indexOf(ch) >= 0;
            if (current.length() >= maxChunkLength || sentenceEnd) {
                chunks.add(current.toString());
                current = new StringBuilder();
            }
        }
        if (current.length() > 0) {
            chunks.add(current.toString());
        }
        return chunks;
    }

    private void sendEvent(SseEmitter emitter, String eventName, Map<String, Object> payload) throws IOException {
        emitter.send(SseEmitter.event()
                .name(eventName)
                .data(payload, MediaType.APPLICATION_JSON));
    }

    private record ChatRequest(String sessionId, Long patientId, String message) {
    }

    /**
     * 获取患者的历史会话列表
     */
    @GetMapping("/sessions")
    public ResponseEntity<?> listSessions(@RequestParam String ownerRole,
                                          @RequestParam Long ownerUserId,
                                          @RequestParam(required = false) Long patientId) {
        accessGuard.requireChatAccess(patientId);
        return ResponseEntity.ok(chatService.listSessions(
                accessGuard.currentChatOwnerRole(),
                accessGuard.currentChatOwnerUserId(),
                patientId
        ));
    }

    @GetMapping("/bootstrap")
    public ResponseEntity<?> getBootstrap(@RequestParam String ownerRole,
                                          @RequestParam Long ownerUserId,
                                          @RequestParam(required = false) Long patientId) {
        accessGuard.requireChatAccess(patientId);
        return ResponseEntity.ok(chatService.getBootstrap(
                accessGuard.currentChatOwnerRole(),
                accessGuard.currentChatOwnerUserId(),
                patientId
        ));
    }

    /**
     * 获取指定会话的最近消息
     */
    @GetMapping("/session/{sessionId}/messages")
    public ResponseEntity<?> getHistory(@PathVariable String sessionId,
                                        @RequestParam String ownerRole,
                                        @RequestParam Long ownerUserId) {
        return ResponseEntity.ok(chatService.getHistory(
                sessionId,
                accessGuard.currentChatOwnerRole(),
                accessGuard.currentChatOwnerUserId()
        ));
    }

    @GetMapping("/session/{sessionId}/orchestration")
    public ResponseEntity<?> getSessionOrchestration(@PathVariable String sessionId,
                                                     @RequestParam String ownerRole,
                                                     @RequestParam Long ownerUserId) {
        return ResponseEntity.ok(chatService.getSessionOrchestrationStatus(
                sessionId,
                accessGuard.currentChatOwnerRole(),
                accessGuard.currentChatOwnerUserId()
        ));
    }

    /**
     * 清除会话历史
     * DELETE /api/chat/session/{sessionId}
     */
    @DeleteMapping("/session/{sessionId}")
    public ResponseEntity<Map<String, String>> clearSession(@PathVariable String sessionId,
                                                            @RequestParam String ownerRole,
                                                            @RequestParam Long ownerUserId) {
        chatService.clearSession(
                sessionId,
                accessGuard.currentChatOwnerRole(),
                accessGuard.currentChatOwnerUserId()
        );
        return ResponseEntity.ok(Map.of("message", "会话已清除"));
    }

    /**
     * 获取LLM服务状态
     * GET /api/chat/status
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus() {
        return ResponseEntity.ok(llmService.getStatus());
    }
}
