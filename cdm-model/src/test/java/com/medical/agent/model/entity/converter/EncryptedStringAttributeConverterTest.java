package com.medical.agent.model.entity.converter;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EncryptedStringAttributeConverterTest {

    private final EncryptedStringAttributeConverter converter = new EncryptedStringAttributeConverter();

    @AfterEach
    void tearDown() {
        SensitiveFieldCrypto.clearConfiguration();
    }

    @Test
    void shouldEncryptAndDecryptWhenKeyConfigured() {
        SensitiveFieldCrypto.configure("unit-test-sensitive-key-1234567890", true);

        String plain = "患者李某，男，45岁。主诉：上腹痛伴反酸2周。";
        String encrypted = converter.convertToDatabaseColumn(plain);
        String decrypted = converter.convertToEntityAttribute(encrypted);

        assertNotEquals(plain, encrypted);
        assertTrue(SensitiveFieldCrypto.isEncrypted(encrypted));
        assertEquals(plain, decrypted);
    }

    @Test
    void shouldFallbackToPlaintextWhenKeyMissingAndFailOpen() {
        SensitiveFieldCrypto.configure(null, false);

        String plain = "GERD";
        String stored = converter.convertToDatabaseColumn(plain);

        assertEquals(plain, stored);
        assertEquals(plain, converter.convertToEntityAttribute(stored));
    }
}
