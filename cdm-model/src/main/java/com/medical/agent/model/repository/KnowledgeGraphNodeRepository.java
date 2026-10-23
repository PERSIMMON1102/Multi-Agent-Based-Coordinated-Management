package com.medical.agent.model.repository;

import com.medical.agent.model.entity.KnowledgeGraphNode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface KnowledgeGraphNodeRepository extends JpaRepository<KnowledgeGraphNode, Long> {

    Optional<KnowledgeGraphNode> findByNodeTypeAndNodeKey(String nodeType, String nodeKey);

    List<KnowledgeGraphNode> findByNodeTypeAndNodeKeyIn(String nodeType, Collection<String> nodeKeys);

    List<KnowledgeGraphNode> findByNodeTypeAndSourceRefIdIn(String nodeType, Collection<String> sourceRefIds);

    long countByNodeType(String nodeType);
}
