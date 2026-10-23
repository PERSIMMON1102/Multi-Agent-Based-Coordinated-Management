package com.medical.agent.service.report.agent;

import com.medical.agent.service.HealthReportService;
import com.medical.agent.service.LlmService;
import com.medical.agent.service.RagRetrievalService;
import org.springframework.stereotype.Component;

@Component
public class ReportDraftingSpecialistAgent implements ReportSpecialistAgent {

    private final LlmService llmService;
    private final RagRetrievalService ragRetrievalService;

    public ReportDraftingSpecialistAgent(LlmService llmService,
                                         RagRetrievalService ragRetrievalService) {
        this.llmService = llmService;
        this.ragRetrievalService = ragRetrievalService;
    }

    @Override
    public String name() {
        return "report_drafting_agent";
    }

    @Override
    public int order() {
        return 30;
    }

    @Override
    public void execute(ReportGenerationState state) {
        String content = null;
        if (llmService.isAvailable()) {
            String evidenceBlock = ragRetrievalService.renderEvidenceBlock(state.getPromptReferences());
            boolean patientWeekly = HealthReportService.TYPE_PATIENT_WEEKLY.equals(state.getType());
            String systemPrompt;
            String userPrompt;
            if (patientWeekly) {
                systemPrompt = "你是一名基层慢病管理AI助手。请生成面向患者的每周健康报告，语言通俗、具体、可执行。输出使用 Markdown 标题和项目符号。若提供了专业资料，请优先依据资料生成，不要编造来源。";
                userPrompt = "请基于以下患者数据，生成一份本周健康报告，包含：本周概览、主要风险、生活方式建议、用药与随访提醒、需要及时就医的警示。\n\n"
                        + state.getContext()
                        + "\n"
                        + evidenceBlock;
            } else {
                systemPrompt = "你是一名基层医生智能助手。请生成面向医生的结构化专业风险评估报告。输出必须使用规范 Markdown，严格包含以下二级标题：## 报告用途、## 一、风险分层结论、## 二、关键依据、## 三、当前重点问题、## 四、医生干预建议、## 五、随访建议、## 六、结论。不要输出占位符，不要编造医生姓名、档案编号或不存在的数据。若提供了专业资料，请优先依据资料生成，不要编造来源。";
                userPrompt = "请基于以下患者数据，生成一份给医生查看的结构化专业评估报告。要求：1）每个章节内容简洁明确；2）优先写结论和行动建议；3）不要输出“[医生姓名]”“[请填写]”这类占位词；4）没有数据就明确写“暂无”或“建议补充”。\n\n"
                        + state.getContext()
                        + "\n"
                        + evidenceBlock;
            }
            content = llmService.chat(systemPrompt, userPrompt);
        }
        if (content == null || content.isBlank()) {
            content = HealthReportPromptSupport.buildFallbackReport(
                    state.getType(),
                    state.getPatient(),
                    state.getMedicalHistorySummary(),
                    state.getAssessments(),
                    state.getPlans(),
                    state.getLatestRecord()
            );
        } else if (HealthReportService.TYPE_DOCTOR_ASSESSMENT.equals(state.getType())) {
            content = HealthReportPromptSupport.normalizeDoctorAssessmentReport(
                    content,
                    state.getPatient(),
                    state.getMedicalHistorySummary(),
                    state.getAssessments(),
                    state.getPlans(),
                    state.getLatestRecord()
            );
        }
        state.setContent(content);
        state.addStep(name(), "已生成报告正文，若模型不可用则回退为规则化模板内容。");
    }
}
