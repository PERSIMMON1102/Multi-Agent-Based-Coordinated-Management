package com.medical.agent.service.papercase;

import com.medical.agent.common.dto.PaperCaseIcdCandidateDTO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaperCaseIcdMatchResult {
    private String code;
    private String title;
    private Double confidence;
    private String codingStatus;
    private String codingSource;
    @Builder.Default
    private List<PaperCaseIcdCandidateDTO> candidates = new ArrayList<>();
}
