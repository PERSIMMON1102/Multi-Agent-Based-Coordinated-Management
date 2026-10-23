package com.medical.agent.model.repository;

import com.medical.agent.model.entity.VideoTranscriptChunk;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface VideoTranscriptChunkRepository extends JpaRepository<VideoTranscriptChunk, Long> {

    List<VideoTranscriptChunk> findByVideoIdOrderByChunkIndexAsc(Long videoId);

    List<VideoTranscriptChunk> findByVideoIdInOrderByVideoIdAscChunkIndexAsc(Collection<Long> videoIds);

    void deleteByVideoId(Long videoId);
}
