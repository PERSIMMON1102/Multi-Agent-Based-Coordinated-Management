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
@Document(collection = "agent_message_log")
public class AgentMessageLog {
    @Id
    private String id;
    private String senderAgent;
    private String receiverAgent;
    private String performative;
    private String content;
    private Map<String, Object> metadata;
    private LocalDateTime timestamp;
}
