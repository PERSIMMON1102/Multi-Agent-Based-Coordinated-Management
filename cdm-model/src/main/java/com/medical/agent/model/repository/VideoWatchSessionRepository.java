package com.medical.agent.model.repository;

import com.medical.agent.model.entity.VideoWatchSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface VideoWatchSessionRepository extends JpaRepository<VideoWatchSession, Long> {

    List<VideoWatchSession> findByPatientIdIn(List<Long> patientIds);

    List<VideoWatchSession> findByPatientIdInAndLastActiveAtBetween(List<Long> patientIds,
                                                                    LocalDateTime start,
                                                                    LocalDateTime end);

    List<VideoWatchSession> findByVideoIdInAndLastActiveAtBetween(List<Long> videoIds,
                                                                  LocalDateTime start,
                                                                  LocalDateTime end);

    List<VideoWatchSession> findByPatientIdAndVideoId(Long patientId, Long videoId);

    Optional<VideoWatchSession> findTopByPatientIdAndVideoIdOrderByLastActiveAtDesc(Long patientId, Long videoId);
}
