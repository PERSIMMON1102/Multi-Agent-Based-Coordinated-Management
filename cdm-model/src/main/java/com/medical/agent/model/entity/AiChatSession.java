package com.medical.agent.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "ai_chat_session", indexes = {
    @Index(name = "idx_ai_session_patient", columnList = "patient_id"),
    @Index(name = "idx_ai_session_last", columnList = "last_message_at"),
    @Index(name = "idx_ai_session_owner", columnList = "owner_role, owner_user_id")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiChatSession {

    @Id
    @Column(length = 64)
    private String id;

    @Column(name = "patient_id")
    private Long patientId;

    @Column(name = "owner_role", length = 20)
    private String ownerRole;

    @Column(name = "owner_user_id")
    private Long ownerUserId;

    @Column(length = 200)
    private String title;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "last_message_at")
    private LocalDateTime lastMessageAt;
}
