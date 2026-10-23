package com.medical.agent.model.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "forum_replies")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ForumReply {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "comment_id", nullable = false)
    @JsonIgnore
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private ForumComment comment;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;

    @Column(nullable = false, length = 50)
    private String author;

    @Column(length = 50)
    private String authorUsername;

    @Column(nullable = false, length = 20)
    private String authorRole;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;
}
