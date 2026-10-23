package com.medical.agent.model.repository;

import com.medical.agent.model.entity.AiChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface AiChatMessageRepository extends JpaRepository<AiChatMessage, Long> {

    List<AiChatMessage> findTop40BySessionIdAndCreatedAtAfterOrderByCreatedAtDesc(String sessionId, LocalDateTime cutoff);

    void deleteBySessionId(String sessionId);

    void deleteByCreatedAtBefore(LocalDateTime threshold);

    List<AiChatMessage> findBySessionIdInAndCreatedAtBetween(List<String> sessionIds,
                                                             LocalDateTime start,
                                                             LocalDateTime end);
}
