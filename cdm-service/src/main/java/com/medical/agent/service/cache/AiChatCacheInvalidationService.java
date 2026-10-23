package com.medical.agent.service.cache;

import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;

@Service
public class AiChatCacheInvalidationService {

    private static final String[] CHAT_CACHE_NAMES = {
            CacheNames.CHAT_PATIENT_CONTEXT,
            CacheNames.CHAT_RULE_ASSESSMENT,
            CacheNames.CHAT_EVIDENCE_RETRIEVAL
    };

    private final CacheManager cacheManager;
    private final AiChatCacheVersionService aiChatCacheVersionService;

    public AiChatCacheInvalidationService(CacheManager cacheManager,
                                          AiChatCacheVersionService aiChatCacheVersionService) {
        this.cacheManager = cacheManager;
        this.aiChatCacheVersionService = aiChatCacheVersionService;
    }

    public void invalidatePatient(Long patientId) {
        aiChatCacheVersionService.invalidatePatient(patientId);
    }

    public void invalidateAll() {
        aiChatCacheVersionService.invalidateGlobal();
        for (String cacheName : CHAT_CACHE_NAMES) {
            Cache cache = cacheManager.getCache(cacheName);
            if (cache != null) {
                cache.clear();
            }
        }
    }
}
