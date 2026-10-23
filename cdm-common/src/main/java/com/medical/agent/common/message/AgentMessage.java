package com.medical.agent.common.message;

import com.medical.agent.common.enums.DiseaseType;
import com.medical.agent.common.enums.RiskLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * 智能体/兼容桥接链共享的消息内容载体。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentMessage implements Serializable {
    private static final long serialVersionUID = 1L;

    private String messageId;
    private String messageType;
    private Long patientId;
    private String patientName;
    private DiseaseType diseaseType;
    private RiskLevel riskLevel;
    private Double score;
    private String detail;
    private List<String> recommendations;
    private Map<String, Object> data;
    private long timestamp;
}
