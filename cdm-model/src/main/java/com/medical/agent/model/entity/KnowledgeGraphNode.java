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
@Table(name = "knowledge_graph_node", indexes = {
        @Index(name = "idx_kg_node_type", columnList = "node_type"),
        @Index(name = "idx_kg_node_source", columnList = "source_type, source_ref_id")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_kg_node_type_key", columnNames = {"node_type", "node_key"})
})
public class KnowledgeGraphNode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "node_type", nullable = false, length = 50)
    private String nodeType;

    @Column(name = "node_key", nullable = false, length = 150)
    private String nodeKey;

    @Column(name = "display_name", nullable = false, length = 200)
    private String displayName;

    @Column(name = "source_type", length = 50)
    private String sourceType;

    @Column(name = "source_ref_id", length = 120)
    private String sourceRefId;

    @Column(name = "attributes_json", columnDefinition = "TEXT")
    private String attributesJson;

    @Builder.Default
    @Column(nullable = false)
    private Boolean active = true;

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
        if (active == null) {
            active = true;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
        if (active == null) {
            active = true;
        }
    }
}
