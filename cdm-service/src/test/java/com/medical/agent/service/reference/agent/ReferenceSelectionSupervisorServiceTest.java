package com.medical.agent.service.reference.agent;

import com.medical.agent.common.dto.RagEvidenceDTO;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ReferenceSelectionSupervisorServiceTest {

    @Test
    void shouldExecuteSpecialistsOnceInSortedOrder() {
        List<String> executionOrder = new ArrayList<>();
        List<RagEvidenceDTO> candidates = List.of(
                evidence("指南A"),
                evidence("指南B"),
                evidence("指南C")
        );

        ReferenceSelectionSupervisorService service = new ReferenceSelectionSupervisorService(List.of(
                specialist("judge", 20, executionOrder, state ->
                        state.setSelectedReferences(List.of(state.getCandidates().get(0), state.getCandidates().get(2)))),
                specialist("prompt", 10, executionOrder, state -> state.addStep("prompt", "提示词已构造")),
                specialist("post_filter", 30, executionOrder, state -> state.addStep("post_filter", "结果已确认"))
        ));

        List<RagEvidenceDTO> selected = service.judgeContentDisplayReferences(
                "diet",
                "建议控制盐摄入并规律复查。",
                candidates,
                2
        );

        assertEquals(List.of("prompt", "judge", "post_filter"), executionOrder);
        assertEquals(2, selected.size());
        assertEquals("指南A", selected.get(0).getTitle());
        assertEquals("指南C", selected.get(1).getTitle());
    }

    @Test
    void shouldReturnNullWhenNoReferenceIsSelected() {
        ReferenceSelectionSupervisorService service = new ReferenceSelectionSupervisorService(List.of(
                specialist("prompt", 10, new ArrayList<>(), state -> state.addStep("prompt", "已准备候选引用")),
                specialist("judge", 20, new ArrayList<>(), state -> state.setSelectedReferences(List.of()))
        ));

        List<RagEvidenceDTO> selected = service.judgeChatDisplayReferences(
                "血压高怎么办",
                "建议先规律监测。",
                List.of(evidence("指南A")),
                1
        );

        assertNull(selected);
    }

    @Test
    void shouldSkipReferenceSpecialistsBeyondBoundedLimit() {
        List<String> executionOrder = new ArrayList<>();
        ReferenceSelectionSupervisorService service = new ReferenceSelectionSupervisorService(List.of(
                specialist("agent_4", 40, executionOrder, state -> state.addStep("agent_4", "skipped expected")),
                specialist("agent_2", 20, executionOrder, state -> state.addStep("agent_2", "executed")),
                specialist("agent_1", 10, executionOrder, state -> state.addStep("agent_1", "executed")),
                specialist("agent_3", 30, executionOrder, state -> state.setSelectedReferences(List.of(evidence("指南A"))))
        ));

        List<RagEvidenceDTO> selected = service.judgeChatDisplayReferences(
                "请给出引用",
                "这里是回答",
                List.of(evidence("指南A"), evidence("指南B")),
                1
        );

        assertEquals(List.of("agent_1", "agent_2", "agent_3"), executionOrder);
        assertEquals(1, selected.size());
    }

    private ReferenceSelectionSpecialistAgent specialist(String name,
                                                         int order,
                                                         List<String> executionOrder,
                                                         java.util.function.Consumer<ReferenceSelectionState> action) {
        return new ReferenceSelectionSpecialistAgent() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public int order() {
                return order;
            }

            @Override
            public void execute(ReferenceSelectionState state) {
                executionOrder.add(name);
                action.accept(state);
            }
        };
    }

    private RagEvidenceDTO evidence(String title) {
        RagEvidenceDTO dto = new RagEvidenceDTO();
        dto.setTitle(title);
        dto.setSource("指南来源");
        dto.setSourceType("guideline");
        dto.setContentSnippet("证据摘要");
        return dto;
    }
}
