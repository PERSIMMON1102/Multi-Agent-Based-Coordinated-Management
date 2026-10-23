package com.medical.agent.model.repository;

import com.medical.agent.model.entity.KnowledgeGraphEdge;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface KnowledgeGraphEdgeRepository extends JpaRepository<KnowledgeGraphEdge, Long> {

    List<KnowledgeGraphEdge> findByRelationTypeAndSourceNodeIdIn(String relationType, Collection<Long> sourceNodeIds);

    List<KnowledgeGraphEdge> findByRelationTypeAndTargetNodeIdIn(String relationType, Collection<Long> targetNodeIds);

    long countByRelationType(String relationType);
}
