package com.medical.agent.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "knowledge_graph_edge", indexes = {
        @Index(name = "idx_kg_edge_relation", columnList = "relation_type"),
        @Index(name = "idx_kg_edge_source", columnList = "source_node_id"),
        @Index(name = "idx_kg_edge_target", columnList = "target_node_id")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_kg_edge_relation_nodes", columnNames = {"relation_type", "source_node_id", "target_node_id"})
})
public class KnowledgeGraphEdge {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "relation_type", nullable = false, length = 60)
    private String relationType;

    @Column(name = "source_node_id", nullable = false)
    private Long sourceNodeId;

    @Column(name = "target_node_id", nullable = false)
    private Long targetNodeId;

    @Builder.Default
    @Column(nullable = false)
    private Double weight = 1.0d;

    @Column(name = "source_type", length = 50)
    private String sourceType;

    @Column(name = "source_ref_id", length = 120)
    private String sourceRefId;

    @Column(name = "evidence_text", columnDefinition = "TEXT")
    private String evidenceText;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
        if (weight == null) {
            weight = 1.0d;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
        if (weight == null) {
            weight = 1.0d;
        }
    }
}
