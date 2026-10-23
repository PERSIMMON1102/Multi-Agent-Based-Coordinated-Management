package com.medical.agent.model.repository;

import com.medical.agent.model.entity.ChatMessage;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    @Query("SELECT m FROM ChatMessage m WHERE (m.fromId = :u1 AND m.toId = :u2) OR (m.fromId = :u2 AND m.toId = :u1) ORDER BY m.createdAt ASC")
    List<ChatMessage> findConversation(@Param("u1") String u1, @Param("u2") String u2);

    @Query("SELECT DISTINCT CASE WHEN m.fromId = :userId THEN m.toId ELSE m.fromId END FROM ChatMessage m WHERE m.fromId = :userId OR m.toId = :userId")
    List<String> findContactIds(@Param("userId") String userId);

    @Query("SELECT m FROM ChatMessage m WHERE (m.fromId = :u1 AND m.toId = :u2) OR (m.fromId = :u2 AND m.toId = :u1) ORDER BY m.createdAt DESC")
    List<ChatMessage> findLatestMessages(@Param("u1") String u1, @Param("u2") String u2, Pageable pageable);

    default ChatMessage findLatestMessage(String u1, String u2) {
        return findLatestMessages(u1, u2, PageRequest.of(0, 1)).stream().findFirst().orElse(null);
    }
}
