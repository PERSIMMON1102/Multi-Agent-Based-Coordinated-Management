package com.medical.agent.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VideoTranscriptChunkDTO {
    private Long id;
    private Long videoId;
    private Integer chunkIndex;
    private String content;
    private String sourceType;
    private String embeddingStatus;
    private String createdAt;
}
