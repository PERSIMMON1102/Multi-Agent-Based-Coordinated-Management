package com.medical.agent.service.security;

import com.medical.agent.model.entity.converter.SensitiveFieldCrypto;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class SensitiveFieldEncryptionBootstrap {

    private static final Logger log = LoggerFactory.getLogger(SensitiveFieldEncryptionBootstrap.class);

    @Value("${security.field-encryption.key:${CDM_FIELD_ENCRYPTION_KEY:}}")
    private String encryptionKey;

    @Value("${security.field-encryption.fail-on-missing-key:false}")
    private boolean failOnMissingKey;

    @PostConstruct
    public void init() {
        SensitiveFieldCrypto.configure(encryptionKey, failOnMissingKey);
        if (encryptionKey == null || encryptionKey.isBlank()) {
            if (failOnMissingKey) {
                log.error("[SensitiveFieldEncryption] encryption key is missing and fail-on-missing-key is enabled.");
            } else {
                log.warn("[SensitiveFieldEncryption] encryption key is not configured, sensitive fields will remain plaintext until CDM_FIELD_ENCRYPTION_KEY is provided.");
            }
        } else {
            log.info("[SensitiveFieldEncryption] sensitive field encryption configured.");
        }
    }
}
