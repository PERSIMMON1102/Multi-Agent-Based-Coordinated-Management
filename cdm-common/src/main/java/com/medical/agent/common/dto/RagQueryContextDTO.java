package com.medical.agent.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RagQueryContextDTO {
    private Long patientId;
    private String scenario;
    private String focus;
    private String queryText;
    private List<String> diseaseKeys;
    private List<String> topicTags;
    private String audience;
    private Map<String, Object> structuredContext;
}
