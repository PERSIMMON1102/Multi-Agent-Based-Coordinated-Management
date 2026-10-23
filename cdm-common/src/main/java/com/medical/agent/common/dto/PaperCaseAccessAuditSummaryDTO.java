package com.medical.agent.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaperCaseAccessAuditSummaryDTO {
    @Builder.Default
    private Long totalEntries = 0L;
    @Builder.Default
    private Map<String, Long> actionCounts = new LinkedHashMap<>();
    @Builder.Default
    private Map<String, Long> outcomeCounts = new LinkedHashMap<>();
    @Builder.Default
    private Map<String, Long> actorRoleCounts = new LinkedHashMap<>();
    @Builder.Default
    private Map<String, Long> resourceTypeCounts = new LinkedHashMap<>();
    @Builder.Default
    private List<PaperCaseAccessLogDTO> entries = new ArrayList<>();
}
