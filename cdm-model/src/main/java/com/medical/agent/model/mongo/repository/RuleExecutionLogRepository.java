package com.medical.agent.model.mongo.repository;

import com.medical.agent.model.mongo.RuleExecutionLog;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RuleExecutionLogRepository extends MongoRepository<RuleExecutionLog, String> {
    List<RuleExecutionLog> findByPatientIdOrderByTimestampDesc(Long patientId);
    List<RuleExecutionLog> findByAgentIdOrderByTimestampDesc(String agentId);
    List<RuleExecutionLog> findByRuleGroupOrderByTimestampDesc(String ruleGroup);
}
