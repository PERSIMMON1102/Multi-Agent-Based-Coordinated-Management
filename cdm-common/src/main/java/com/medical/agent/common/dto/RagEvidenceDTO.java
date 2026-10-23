package com.medical.agent.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RagEvidenceDTO {
    private String sourceType;
    private Long refId;
    private Long videoId;
    private String diseaseKey;
    private String title;
    private String section;
    private String source;
    private String publishedAt;
    private String contentSnippet;
    private Double score;
}
