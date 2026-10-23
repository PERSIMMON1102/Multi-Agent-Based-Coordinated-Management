package com.medical.agent.model.entity.converter;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

public final class SensitiveFieldCrypto {

    private static final String PREFIX = "enc:v1:";
    private static final int IV_LENGTH = 12;
    private static final int TAG_LENGTH_BITS = 128;
    private static final SecureRandom RANDOM = new SecureRandom();

    private static volatile String configuredKey;
    private static volatile boolean failOnMissingKey;

    private SensitiveFieldCrypto() {
    }

    public static void configure(String key, boolean failIfMissing) {
        configuredKey = normalize(key);
        failOnMissingKey = failIfMissing;
    }

    public static void clearConfiguration() {
        configuredKey = null;
        failOnMissingKey = false;
    }

    public static String encrypt(String plainText) {
        if (plainText == null || plainText.isBlank()) {
            return plainText;
        }
        if (isEncrypted(plainText)) {
            return plainText;
        }
        SecretKeySpec keySpec = resolveKeySpec();
        if (keySpec == null) {
            return plainText;
        }
        try {
            byte[] iv = new byte[IV_LENGTH];
            RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            byte[] cipherText = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            ByteBuffer buffer = ByteBuffer.allocate(iv.length + cipherText.length);
            buffer.put(iv);
            buffer.put(cipherText);
            return PREFIX + Base64.getEncoder().encodeToString(buffer.array());
        } catch (Exception ex) {
            throw new IllegalStateException("failed to encrypt sensitive field", ex);
        }
    }

    public static String decrypt(String storedValue) {
        if (storedValue == null || storedValue.isBlank() || !isEncrypted(storedValue)) {
            return storedValue;
        }
        SecretKeySpec keySpec = resolveKeySpec();
        if (keySpec == null) {
            return storedValue;
        }
        try {
            byte[] combined = Base64.getDecoder().decode(storedValue.substring(PREFIX.length()));
            ByteBuffer buffer = ByteBuffer.wrap(combined);
            byte[] iv = new byte[IV_LENGTH];
            buffer.get(iv);
            byte[] cipherText = new byte[buffer.remaining()];
            buffer.get(cipherText);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, keySpec, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            byte[] plainText = cipher.doFinal(cipherText);
            return new String(plainText, StandardCharsets.UTF_8);
        } catch (Exception ex) {
            throw new IllegalStateException("failed to decrypt sensitive field", ex);
        }
    }

    public static boolean isEncrypted(String value) {
        return value != null && value.startsWith(PREFIX);
    }

    private static SecretKeySpec resolveKeySpec() {
        String key = resolveKey();
        if (key == null) {
            if (failOnMissingKey) {
                throw new IllegalStateException("sensitive field encryption key is missing");
            }
            return null;
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(key.getBytes(StandardCharsets.UTF_8));
            return new SecretKeySpec(hash, "AES");
        } catch (Exception ex) {
            throw new IllegalStateException("failed to initialize sensitive field key", ex);
        }
    }

    private static String resolveKey() {
        String direct = normalize(configuredKey);
        if (direct != null) {
            return direct;
        }
        String systemProperty = normalize(System.getProperty("cdm.field.encryption.key"));
        if (systemProperty != null) {
            return systemProperty;
        }
        return normalize(System.getenv("CDM_FIELD_ENCRYPTION_KEY"));
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
