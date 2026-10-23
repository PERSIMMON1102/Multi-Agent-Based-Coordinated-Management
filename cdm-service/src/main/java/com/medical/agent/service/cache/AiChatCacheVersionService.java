package com.medical.agent.service.cache;

import com.medical.agent.common.dto.RagQueryContextDTO;
import com.medical.agent.service.chat.ChatPatientContextData;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Service("aiChatCacheVersionService")
public class AiChatCacheVersionService {

    private final AtomicLong globalVersion = new AtomicLong();
    private final Map<Long, AtomicLong> patientVersions = new ConcurrentHashMap<>();

    public String patientContextVersion(Long patientId) {
        return versionFor(patientId);
    }

    public String assessmentVersion(ChatPatientContextData patientContext) {
        Long patientId = patientContext == null || patientContext.patient() == null
                ? null
                : patientContext.patient().getId();
        return versionFor(patientId);
    }

    public String evidenceVersion(RagQueryContextDTO context) {
        Long patientId = context == null ? null : context.getPatientId();
        return versionFor(patientId);
    }

    public void invalidatePatient(Long patientId) {
        if (patientId == null) {
            invalidateGlobal();
            return;
        }
        patientVersions.computeIfAbsent(patientId, ignored -> new AtomicLong()).incrementAndGet();
    }

    public void invalidateGlobal() {
        globalVersion.incrementAndGet();
    }

    private String versionFor(Long patientId) {
        long global = globalVersion.get();
        if (patientId == null) {
            return "v" + global;
        }
        long patient = patientVersions.computeIfAbsent(patientId, ignored -> new AtomicLong()).get();
        return "v" + global + "-p" + patient;
    }
}
