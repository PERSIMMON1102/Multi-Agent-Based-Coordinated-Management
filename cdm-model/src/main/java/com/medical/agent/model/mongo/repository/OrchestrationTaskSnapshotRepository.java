package com.medical.agent.model.mongo.repository;

import com.medical.agent.model.mongo.OrchestrationTaskSnapshot;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface OrchestrationTaskSnapshotRepository extends MongoRepository<OrchestrationTaskSnapshot, String> {
    Optional<OrchestrationTaskSnapshot> findFirstBySessionIdOrderByUpdatedAtDesc(String sessionId);
    List<OrchestrationTaskSnapshot> findBySessionIdOrderByUpdatedAtDesc(String sessionId);
    Optional<OrchestrationTaskSnapshot> findFirstByTaskIdOrderByUpdatedAtDesc(String taskId);
    List<OrchestrationTaskSnapshot> findTop200ByOrderByUpdatedAtDesc();
    List<OrchestrationTaskSnapshot> findByStatusInOrderByUpdatedAtAsc(List<String> statuses);
    List<OrchestrationTaskSnapshot> findByStatusInAndNextRunAtLessThanEqualOrderByUpdatedAtAsc(List<String> statuses, LocalDateTime nextRunAt);
}
