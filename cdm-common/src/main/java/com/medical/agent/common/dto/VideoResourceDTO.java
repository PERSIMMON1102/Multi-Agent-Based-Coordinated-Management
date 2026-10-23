package com.medical.agent.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VideoResourceDTO {
    private Long id;
    private String title;
    private String description;
    private String diseaseType;
    private String category;
    private String coverUrl;
    private String videoUrl;
    private String objectKey;
    private String coverObjectKey;
    private String sourceType;
    private Long durationSeconds;
    private Integer sortOrder;
    private Boolean active;
    private Boolean hasTranscript;
    private List<String> tags;
    private String recommendReason;
    private String createdAt;
    private String updatedAt;
}
