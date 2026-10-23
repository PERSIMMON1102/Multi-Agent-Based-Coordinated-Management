package com.medical.agent.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaperCaseIcdCandidateDTO {
    private String code;
    private String title;
    private Double confidence;
    private String source;
}
