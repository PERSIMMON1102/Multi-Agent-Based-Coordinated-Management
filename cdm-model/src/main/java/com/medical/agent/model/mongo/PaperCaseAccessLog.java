package com.medical.agent.model.mongo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@CompoundIndexes({
        @CompoundIndex(name = "paper_case_audit_patient_ts_idx", def = "{'patientId': 1, 'timestamp': -1}"),
        @CompoundIndex(name = "paper_case_audit_case_ts_idx", def = "{'paperCaseId': 1, 'timestamp': -1}")
})
@Document(collection = "paper_case_access_log")
public class PaperCaseAccessLog {
    @Id
    private String id;
    private Long paperCaseId;
    private Long patientId;
    private Long actorUserId;
    private String actorRole;
    private String action;
    private String outcome;
    private String resourceType;
    private String requestIp;
    private String userAgent;
    private String targetSummary;
    private String detailSummary;
    private LocalDateTime timestamp;
}
