package com.medical.agent.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "knowledge_chunk", indexes = {
    @Index(name = "idx_knowledge_disease", columnList = "disease"),
    @Index(name = "idx_knowledge_version", columnList = "version_tag")
})
public class KnowledgeChunk {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String disease;

    @Column(length = 200)
    private String title;

    @Column(length = 200)
    private String section;

    @Lob
    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(length = 200)
    private String source;

    @Column(name = "published_at")
    private LocalDate publishedAt;

    @Column(name = "version_tag", length = 50)
    private String versionTag;

    @Column(name = "content_tags", length = 500)
    private String contentTags;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String embedding;

    @Column(name = "vector_point_id", length = 100)
    private String vectorPointId;

    @Column(name = "embedding_model", length = 100)
    private String embeddingModel;

    @Column(name = "embedding_status", length = 30)
    private String embeddingStatus;

    @Column(name = "indexed_at")
    private LocalDateTime indexedAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
