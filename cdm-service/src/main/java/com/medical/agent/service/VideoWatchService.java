package com.medical.agent.service;

import com.medical.agent.common.exception.BusinessException;
import com.medical.agent.model.entity.Patient;
import com.medical.agent.model.entity.VideoResource;
import com.medical.agent.model.entity.VideoWatchSession;
import com.medical.agent.model.repository.PatientRepository;
import com.medical.agent.model.repository.VideoResourceRepository;
import com.medical.agent.model.repository.VideoWatchSessionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

@Service
public class VideoWatchService {

    private static final double COMPLETION_THRESHOLD = 90D;

    private final VideoWatchSessionRepository videoWatchSessionRepository;
    private final PatientRepository patientRepository;
    private final VideoResourceRepository videoResourceRepository;

    public VideoWatchService(VideoWatchSessionRepository videoWatchSessionRepository,
                             PatientRepository patientRepository,
                             VideoResourceRepository videoResourceRepository) {
        this.videoWatchSessionRepository = videoWatchSessionRepository;
        this.patientRepository = patientRepository;
        this.videoResourceRepository = videoResourceRepository;
    }

    @Transactional
    public Map<String, Object> startSession(Long patientId,
                                            Long videoId,
                                            String sourceType,
                                            Long sourceRefId,
                                            Long durationSeconds) {
        Patient patient = patientRepository.findById(patientId)
                .orElseThrow(() -> new BusinessException(404, "患者不存在"));
        VideoResource video = videoResourceRepository.findById(videoId)
                .orElseThrow(() -> new BusinessException(404, "视频不存在"));
        if (!Boolean.TRUE.equals(video.getActive())) {
            throw new BusinessException(400, "视频当前不可播放");
        }

        VideoWatchSession session = VideoWatchSession.builder()
                .patientId(patient.getId())
                .videoId(video.getId())
                .sourceType(normalizeSourceType(sourceType))
                .sourceRefId(sourceRefId)
                .durationSeconds(durationSeconds != null && durationSeconds > 0 ? durationSeconds : video.getDurationSeconds())
                .watchedSeconds(0L)
                .progressPercent(0D)
                .completed(false)
                .startedAt(LocalDateTime.now())
                .lastActiveAt(LocalDateTime.now())
                .build();
        session = videoWatchSessionRepository.save(session);

        return Map.of(
                "watchSessionId", session.getId(),
                "videoId", session.getVideoId(),
                "patientId", session.getPatientId(),
                "sourceType", session.getSourceType(),
                "startedAt", session.getStartedAt().toString()
        );
    }

    @Transactional
    public Map<String, Object> updateProgress(Long watchSessionId,
                                              Long watchedSeconds,
                                              Double progressPercent) {
        VideoWatchSession session = loadSession(watchSessionId);
        applyProgress(session, watchedSeconds, progressPercent, false);
        return toResponse(session);
    }

    @Transactional
    public Map<String, Object> completeSession(Long watchSessionId,
                                               Long watchedSeconds,
                                               Double progressPercent) {
        VideoWatchSession session = loadSession(watchSessionId);
        applyProgress(session, watchedSeconds, progressPercent, true);
        return toResponse(session);
    }

    private VideoWatchSession loadSession(Long watchSessionId) {
        return videoWatchSessionRepository.findById(watchSessionId)
                .orElseThrow(() -> new BusinessException(404, "观看会话不存在"));
    }

    private void applyProgress(VideoWatchSession session,
                               Long watchedSeconds,
                               Double progressPercent,
                               boolean forceComplete) {
        long normalizedWatched = normalizeWatchedSeconds(session, watchedSeconds);
        double normalizedProgress = normalizeProgress(session, progressPercent, normalizedWatched);

        session.setWatchedSeconds(Math.max(session.getWatchedSeconds() == null ? 0L : session.getWatchedSeconds(), normalizedWatched));
        session.setProgressPercent(Math.max(session.getProgressPercent() == null ? 0D : session.getProgressPercent(), normalizedProgress));
        session.setLastActiveAt(LocalDateTime.now());

        if (forceComplete || session.getProgressPercent() >= COMPLETION_THRESHOLD) {
            session.setCompleted(true);
            if (session.getCompletedAt() == null) {
                session.setCompletedAt(LocalDateTime.now());
            }
        }

        videoWatchSessionRepository.save(session);
    }

    private long normalizeWatchedSeconds(VideoWatchSession session, Long watchedSeconds) {
        long value = watchedSeconds == null || watchedSeconds < 0 ? 0L : watchedSeconds;
        if (session.getDurationSeconds() != null && session.getDurationSeconds() > 0) {
            return Math.min(value, session.getDurationSeconds());
        }
        return value;
    }

    private double normalizeProgress(VideoWatchSession session, Double progressPercent, long watchedSeconds) {
        double progress = progressPercent == null ? -1D : progressPercent;
        if (progress < 0D && session.getDurationSeconds() != null && session.getDurationSeconds() > 0) {
            progress = (double) watchedSeconds / session.getDurationSeconds() * 100D;
        }
        if (progress < 0D) {
            progress = 0D;
        }
        return Math.max(0D, Math.min(progress, 100D));
    }

    private String normalizeSourceType(String sourceType) {
        if (sourceType == null || sourceType.isBlank()) {
            return "video_center";
        }
        return sourceType.trim().toLowerCase(Locale.ROOT);
    }

    private Map<String, Object> toResponse(VideoWatchSession session) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("watchSessionId", session.getId());
        result.put("watchedSeconds", session.getWatchedSeconds());
        result.put("progressPercent", session.getProgressPercent());
        result.put("completed", session.getCompleted());
        result.put("completedAt", session.getCompletedAt() == null ? null : session.getCompletedAt().toString());
        result.put("lastActiveAt", session.getLastActiveAt() == null ? null : session.getLastActiveAt().toString());
        return result;
    }
}
