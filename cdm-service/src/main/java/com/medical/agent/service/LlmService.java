package com.medical.agent.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * LLM服务 - 封装OpenAI兼容API调用
 * 支持任意OpenAI兼容端点（OpenAI、DeepSeek、通义千问、本地Ollama等）
 */
@Service
public class LlmService {

    private static final Logger log = LoggerFactory.getLogger(LlmService.class);
    private static final MediaType JSON_TYPE = MediaType.get("application/json; charset=utf-8");

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final OkHttpClient httpClient;

    @Value("${llm.api.base-url:https://api.openai.com/v1}")
    private String baseUrl;

    @Value("${llm.api.key:}")
    private String apiKey;

    @Value("${llm.api.model:gpt-3.5-turbo}")
    private String model;

    @Value("${llm.api.temperature:0.7}")
    private double temperature;

    @Value("${llm.api.max-tokens:2048}")
    private int maxTokens;

    @Value("${llm.api.enabled:true}")
    private boolean enabled;

    public LlmService() {
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
    }

    @PostConstruct
    public void logConfiguration() {
        log.info("[LlmService] Config loaded: enabled={}, baseUrl={}, model={}, hasKey={}",
                enabled, baseUrl, model, hasApiKey());
    }

    /**
     * 单轮对话 - 给定系统提示词和用户消息，返回LLM回复
     */
    public String chat(String systemPrompt, String userMessage) {
        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", systemPrompt));
        messages.add(Map.of("role", "user", "content", userMessage));
        return callApi(messages);
    }

    /**
     * 多轮对话 - 给定完整消息历史，返回LLM回复
     */
    public String chatWithHistory(List<Map<String, String>> messages) {
        return callApi(messages);
    }

    /**
     * 结构化对话 - 要求模型返回单个 JSON 对象，并进行宽松解析。
     */
    public JsonNode chatJsonObject(String systemPrompt, String userMessage) {
        String raw = chat(systemPrompt, userMessage);
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return parseJsonObject(raw);
    }

    /**
     * 智能体专用 - 给定角色设定、患者上下文和具体任务，返回LLM分析结果
     */
    public String agentAnalyze(String agentRole, String patientContext, String task) {
        String systemPrompt = String.format(
                "你是一个专业的%s。你在一个基层慢性病协同管理平台中工作，负责为基层医疗机构的医生和患者提供专业的健康管理建议。\n" +
                "请基于提供的患者信息，给出专业、详细、个性化的分析和建议。\n" +
                "要求：\n" +
                "1. 回答必须专业准确，符合中国基层慢性病管理指南\n" +
                "2. 语言通俗易懂，适合基层医疗工作者和患者阅读\n" +
                "3. 建议要具体可执行，不要泛泛而谈\n" +
                "4. 如果信息不足，基于现有信息给出最佳建议并说明局限性",
                agentRole);

        String userMessage = String.format("【患者信息】\n%s\n\n【任务】\n%s", patientContext, task);
        return chat(systemPrompt, userMessage);
    }

    /**
     * 检查LLM服务是否可用
     */
    public boolean isAvailable() {
        return enabled && hasApiKey();
    }

    public boolean hasApiKey() {
        return apiKey != null && !apiKey.isBlank();
    }

    public Map<String, Object> getStatus() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("available", isAvailable());
        status.put("enabled", enabled);
        status.put("hasApiKey", hasApiKey());
        status.put("baseUrl", baseUrl);
        status.put("model", model);
        status.put("message", isAvailable() ? "AI健康顾问已就绪" : "AI服务未配置，请设置LLM API密钥");
        return status;
    }

    protected JsonNode parseJsonObject(String rawContent) {
        if (rawContent == null || rawContent.isBlank()) {
            return null;
        }

        String trimmed = rawContent.trim();
        try {
            return objectMapper.readTree(trimmed);
        } catch (IOException ignored) {
            String extracted = extractJsonObject(trimmed);
            if (extracted == null) {
                log.warn("[LlmService] Failed to parse JSON response: {}", abbreviate(trimmed, 240));
                return null;
            }
            try {
                return objectMapper.readTree(extracted);
            } catch (IOException ex) {
                log.warn("[LlmService] Failed to parse extracted JSON response: {}", abbreviate(extracted, 240));
                return null;
            }
        }
    }

    private String callApi(List<Map<String, String>> messages) {
        if (!isAvailable()) {
            log.warn("[LlmService] LLM service not available (enabled={}, hasKey={})", enabled, hasApiKey());
            return null;
        }

        try {
            Map<String, Object> requestBody = Map.of(
                    "model", model,
                    "messages", messages,
                    "temperature", temperature,
                    "max_tokens", maxTokens
            );

            String json = objectMapper.writeValueAsString(requestBody);
            String url = baseUrl.endsWith("/") ? baseUrl + "chat/completions" : baseUrl + "/chat/completions";

            Request request = new Request.Builder()
                    .url(url)
                    .addHeader("Authorization", "Bearer " + apiKey)
                    .addHeader("Content-Type", "application/json")
                    .post(RequestBody.create(json, JSON_TYPE))
                    .build();

            log.info("[LlmService] Calling LLM API: model={}, messages={}", model, messages.size());

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    String errorBody = response.body() != null ? response.body().string() : "no body";
                    log.error("[LlmService] API call failed: status={}, body={}", response.code(), errorBody);
                    return null;
                }

                String responseBody = response.body().string();
                JsonNode root = objectMapper.readTree(responseBody);
                JsonNode choices = root.get("choices");
                if (choices != null && choices.isArray() && !choices.isEmpty()) {
                    String content = choices.get(0).get("message").get("content").asText();
                    log.info("[LlmService] LLM response received, length={}", content.length());
                    return content;
                }

                log.warn("[LlmService] No choices in response: {}", responseBody);
                return null;
            }
        } catch (IOException e) {
            log.error("[LlmService] API call error: {}", e.getMessage(), e);
            return null;
        }
    }

    private String extractJsonObject(String text) {
        int codeFenceStart = text.indexOf("```");
        if (codeFenceStart >= 0) {
            int firstBrace = text.indexOf('{', codeFenceStart);
            if (firstBrace >= 0) {
                int fenceEnd = text.indexOf("```", firstBrace);
                String fenced = fenceEnd > firstBrace ? text.substring(firstBrace, fenceEnd).trim() : text.substring(firstBrace).trim();
                String candidate = sliceFirstJsonObject(fenced);
                if (candidate != null) {
                    return candidate;
                }
            }
        }
        return sliceFirstJsonObject(text);
    }

    private String sliceFirstJsonObject(String text) {
        int start = text.indexOf('{');
        if (start < 0) {
            return null;
        }

        int depth = 0;
        boolean inString = false;
        boolean escaped = false;
        for (int i = start; i < text.length(); i++) {
            char current = text.charAt(i);
            if (escaped) {
                escaped = false;
                continue;
            }
            if (current == '\\') {
                escaped = true;
                continue;
            }
            if (current == '"') {
                inString = !inString;
                continue;
            }
            if (inString) {
                continue;
            }
            if (current == '{') {
                depth++;
            } else if (current == '}') {
                depth--;
                if (depth == 0) {
                    return text.substring(start, i + 1);
                }
            }
        }
        return null;
    }

    private String abbreviate(String text, int maxLength) {
        if (text == null) {
            return "";
        }
        String normalized = text.replaceAll("[\\r\\n]+", " ").trim();
        if (normalized.length() <= maxLength) {
            return normalized;
        }
        return normalized.substring(0, maxLength) + "...";
    }
}
