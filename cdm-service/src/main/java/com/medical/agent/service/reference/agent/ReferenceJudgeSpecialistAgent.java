package com.medical.agent.service.reference.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.medical.agent.common.dto.RagEvidenceDTO;
import com.medical.agent.service.LlmService;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

@Component
public class ReferenceJudgeSpecialistAgent implements ReferenceSelectionSpecialistAgent {

    private final LlmService llmService;
    private final ObjectMapper objectMapper;

    public ReferenceJudgeSpecialistAgent(LlmService llmService, ObjectMapper objectMapper) {
        this.llmService = llmService;
        this.objectMapper = objectMapper;
    }

    @Override
    public String name() {
        return "reference_judge_agent";
    }

    @Override
    public int order() {
        return 20;
    }

    @Override
    public void execute(ReferenceSelectionState state) {
        if (!llmService.isAvailable()
                || state.getCandidates().size() <= 1
                || state.getUserPrompt() == null
                || state.getUserPrompt().isBlank()) {
            state.addStep(name(), "模型不可用或候选不足，跳过引用精筛。");
            return;
        }

        try {
            String raw = llmService.chat(state.getSystemPrompt(), state.getUserPrompt());
            if (raw == null || raw.isBlank()) {
                state.addStep(name(), "模型未返回可解析结果，沿用规则筛选结果。");
                return;
            }

            JsonNode root = objectMapper.readTree(extractJsonObject(raw));
            JsonNode keep = root.path("keep");
            if (!keep.isArray()) {
                state.addStep(name(), "模型返回格式不符合预期，沿用规则筛选结果。");
                return;
            }

            List<RagEvidenceDTO> judged = new ArrayList<>();
            for (JsonNode item : keep) {
                if (!item.canConvertToInt()) {
                    continue;
                }
                int index = item.asInt() - 1;
                if (index >= 0 && index < state.getCandidates().size()) {
                    judged.add(state.getCandidates().get(index));
                }
            }
            state.setSelectedReferences(deduplicate(state.getLimit(), judged));
            state.addStep(name(), "已完成引用精筛，保留 " + state.getSelectedReferences().size() + " 条引用。");
        } catch (Exception e) {
            state.addStep(name(), "引用精筛失败，沿用规则筛选结果。");
        }
    }

    private String extractJsonObject(String raw) {
        String cleaned = raw.replace("```json", "").replace("```", "").trim();
        int start = cleaned.indexOf('{');
        int end = cleaned.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return cleaned.substring(start, end + 1);
        }
        return cleaned;
    }

    private List<RagEvidenceDTO> deduplicate(int limit, List<RagEvidenceDTO> items) {
        LinkedHashMap<String, RagEvidenceDTO> deduped = new LinkedHashMap<>();
        for (RagEvidenceDTO item : items) {
            if (item == null) {
                continue;
            }
            String key = String.join("::",
                    item.getSourceType() == null ? "" : item.getSourceType(),
                    item.getTitle() == null ? "" : item.getTitle(),
                    item.getSource() == null ? "" : item.getSource(),
                    item.getPublishedAt() == null ? "" : item.getPublishedAt());
            deduped.putIfAbsent(key, item);
        }
        return deduped.values().stream().limit(limit).toList();
    }
}
