package com.medical.agent.service.reference.agent;

import com.medical.agent.common.dto.RagEvidenceDTO;
import org.springframework.stereotype.Component;

@Component
public class ReferencePromptSpecialistAgent implements ReferenceSelectionSpecialistAgent {

    @Override
    public String name() {
        return "reference_prompt_agent";
    }

    @Override
    public int order() {
        return 10;
    }

    @Override
    public void execute(ReferenceSelectionState state) {
        StringBuilder candidateBlock = new StringBuilder();
        for (int i = 0; i < state.getCandidates().size(); i++) {
            RagEvidenceDTO item = state.getCandidates().get(i);
            candidateBlock.append('[').append(i + 1).append("] ")
                    .append(item.getTitle() == null || item.getTitle().isBlank() ? "知识片段" : item.getTitle());
            if (item.getSection() != null && !item.getSection().isBlank()) {
                candidateBlock.append(" / ").append(item.getSection());
            }
            if (item.getSource() != null && !item.getSource().isBlank()) {
                candidateBlock.append(" / 来源：").append(item.getSource());
            }
            if (item.getPublishedAt() != null && !item.getPublishedAt().isBlank()) {
                candidateBlock.append(" / 发布：").append(item.getPublishedAt());
            }
            candidateBlock.append("\n摘要：")
                    .append(item.getContentSnippet() == null ? "" : item.getContentSnippet())
                    .append("\n\n");
        }

        if (state.getMode() == ReferenceSelectionState.Mode.CHAT) {
            state.setSystemPrompt(
                    "你是医疗问答的证据审查员。你的任务不是改写答案，而是从候选引用中筛选出真正支撑该答案关键内容的资料。\n" +
                    "筛选规则：\n" +
                    "1. 只保留能直接支撑答案中某个结论、建议或风险提醒的引用\n" +
                    "2. 仅仅和问题同领域、同病种，但没有支撑答案关键内容的引用，一律删除\n" +
                    "3. 若拿不准，宁可不保留\n" +
                    "4. 最多保留 " + state.getLimit() + " 条\n" +
                    "请只返回 JSON，对象格式固定为 {\"keep\":[1,2]}，不要输出任何额外文字。"
            );
            state.setUserPrompt(
                    "【用户问题】\n" + state.getUserQuestion() + "\n\n" +
                    "【AI回答】\n" + state.getReply() + "\n\n" +
                    "【候选引用】\n" + candidateBlock
            );
            state.addStep(name(), "已为聊天回答构造候选引用审查提示词。");
            return;
        }

        state.setSystemPrompt(
                "你是慢病管理内容的证据审查员。你的任务不是修改内容，而是从候选引用中筛选出真正支撑当前内容关键建议或结论的资料。\n" +
                "规则：\n" +
                "1. 只保留能直接支撑当前内容关键结论或建议的引用\n" +
                "2. 仅同主题但不支撑主要内容的引用删除\n" +
                "3. 若拿不准，宁可不保留\n" +
                "4. 最多保留 " + state.getLimit() + " 条\n" +
                "请只返回 JSON，例如 {\"keep\":[1,3]}。"
        );
        state.setUserPrompt(
                "【内容主题】\n" + state.getTopicKey() + "\n\n" +
                "【当前内容】\n" + state.getGeneratedContent() + "\n\n" +
                "【候选引用】\n" + candidateBlock
        );
        state.addStep(name(), "已为内容型引用裁剪构造候选引用审查提示词。");
    }
}
