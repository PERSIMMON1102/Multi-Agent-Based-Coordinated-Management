package com.medical.agent.service.cache;

import com.medical.agent.common.dto.RagQueryContextDTO;
import com.medical.agent.common.enums.DiseaseType;
import com.medical.agent.service.chat.ChatPatientContextData;
import org.springframework.stereotype.Component;

@Component("aiCacheKeyFactory")
public class AiCacheKeyFactory {

    public String patientContext(Long patientId, String version) {
        return AiCacheKeySupport.patientContext(patientId, version);
    }

    public String assessment(ChatPatientContextData patientContext,
                             String userQuestion,
                             DiseaseType preferredDiseaseType,
                             String version) {
        return AiCacheKeySupport.assessment(patientContext, userQuestion, preferredDiseaseType, version);
    }

    public String evidence(RagQueryContextDTO context, int limit, String version) {
        return AiCacheKeySupport.evidence(context, limit, version);
    }
}
