package com.medical.agent.model.repository;

import com.medical.agent.model.entity.AiChatSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface AiChatSessionRepository extends JpaRepository<AiChatSession, String> {

    @Query("""
            SELECT s FROM AiChatSession s
            WHERE s.ownerRole = :ownerRole
              AND s.ownerUserId = :ownerUserId
              AND (
                  (:patientId IS NULL AND s.patientId IS NULL) OR
                  (:patientId IS NOT NULL AND s.patientId = :patientId)
              )
            ORDER BY s.lastMessageAt DESC
            """)
    List<AiChatSession> findOwnedSessions(@Param("ownerRole") String ownerRole,
                                          @Param("ownerUserId") Long ownerUserId,
                                          @Param("patientId") Long patientId);

    List<AiChatSession> findByOwnerRoleAndOwnerUserIdAndLastMessageAtBetween(String ownerRole,
                                                                             Long ownerUserId,
                                                                             LocalDateTime start,
                                                                             LocalDateTime end);

    List<AiChatSession> findTop20ByOwnerRoleAndOwnerUserIdOrderByLastMessageAtDesc(String ownerRole, Long ownerUserId);

    void deleteByLastMessageAtBefore(LocalDateTime threshold);
}
