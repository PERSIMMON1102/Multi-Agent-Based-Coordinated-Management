package com.medical.agent.model.mongo.repository;

import com.medical.agent.model.mongo.AgentMessageLog;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AgentMessageLogRepository extends MongoRepository<AgentMessageLog, String> {
    List<AgentMessageLog> findBySenderAgentOrderByTimestampDesc(String senderAgent);
    List<AgentMessageLog> findByReceiverAgentOrderByTimestampDesc(String receiverAgent);
}
