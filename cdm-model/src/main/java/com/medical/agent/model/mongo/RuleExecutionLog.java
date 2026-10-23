package com.medical.agent.model.mongo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "rule_execution_log")
public class RuleExecutionLog {
    @Id
    private String id;
    private String agentId;
    private String ruleGroup;
    private String ruleName;
    private Long patientId;
    private Map<String, Object> inputFacts;
    private Map<String, Object> outputResult;
    private long executionTimeMs;
    private LocalDateTime timestamp;
}
