package com.medical.agent.model.mongo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "orchestration_task_snapshot")
@CompoundIndexes({
        @CompoundIndex(name = "idx_orchestration_snapshot_contract_compatibility", def = "{'latestContractCompatibility': 1, 'updatedAt': -1}"),
        @CompoundIndex(name = "idx_orchestration_snapshot_contract_errors", def = "{'latestContractValidated': 1, 'latestContractValidationErrorCount': -1, 'updatedAt': -1}"),
        @CompoundIndex(name = "idx_orchestration_snapshot_contract_handoff", def = "{'latestContractSuggestedHandoffAgent': 1, 'updatedAt': -1}")
})
public class OrchestrationTaskSnapshot {
    @Id
    private String id;
    private String sessionId;
    private String taskId;
    private String ownerRole;
    private Long ownerUserId;
    private Long patientId;
    private String userMessage;
    private String progressSummary;
    private String nextAction;
    private List<String> blockers;
    private List<String> usedTools;
    private List<String> safetyFlags;
    private String goal;
    private String taskType;
    private String status;
    private String assignedAgent;
    @Indexed(name = "idx_orchestration_snapshot_contract_validated")
    private Boolean latestContractValidated;
    @Indexed(name = "idx_orchestration_snapshot_contract_schema")
    private String latestContractSchema;
    @Indexed(name = "idx_orchestration_snapshot_contract_version")
    private String latestContractVersion;
    @Indexed(name = "idx_orchestration_snapshot_contract_compatibility_single")
    private String latestContractCompatibility;
    @Indexed(name = "idx_orchestration_snapshot_contract_error_count")
    private Integer latestContractValidationErrorCount;
    private List<String> latestContractValidationErrors;
    @Indexed(name = "idx_orchestration_snapshot_contract_failure_category")
    private String latestContractFailureCategory;
    @Indexed(name = "idx_orchestration_snapshot_contract_suggested_handoff")
    private String latestContractSuggestedHandoffAgent;
    private List<String> latestContractMissingCapabilities;
    private List<String> dependencies;
    private List<Map<String, Object>> subTasks;
    private List<Map<String, Object>> events;
    private Map<String, Object> nextDecision;
    private String controlStatus;
    private String lastControlAction;
    private String lastControlBy;
    private String lastControlReason;
    private LocalDateTime lastControlAt;
    private Integer failureCount;
    private Integer watchdogFailureStreak;
    private LocalDateTime circuitOpenUntil;
    private String circuitOpenReason;
    private LocalDateTime nextRunAt;
    @Indexed(name = "idx_orchestration_snapshot_updated_at")
    private LocalDateTime updatedAt;
}
