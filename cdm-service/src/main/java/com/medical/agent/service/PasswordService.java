package com.medical.agent.service;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class PasswordService {

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public String encodeRequiredStrongPassword(String rawPassword, String blankMessage) {
        String normalized = PasswordPolicyUtils.requireStrongPassword(rawPassword, blankMessage);
        return passwordEncoder.encode(normalized);
    }

    public String encodeOptionalStrongPassword(String rawPassword) {
        String normalized = PasswordPolicyUtils.optionalStrongPassword(rawPassword);
        if (normalized == null) {
            return null;
        }
        return passwordEncoder.encode(normalized);
    }

    public boolean matches(String rawPassword, String storedPassword) {
        if (rawPassword == null || storedPassword == null || storedPassword.isBlank()) {
            return false;
        }
        if (looksEncoded(storedPassword)) {
            return passwordEncoder.matches(rawPassword, storedPassword);
        }
        return rawPassword.equals(storedPassword);
    }

    public boolean needsUpgrade(String storedPassword) {
        return storedPassword != null && !storedPassword.isBlank() && !looksEncoded(storedPassword);
    }

    public String encode(String rawPassword) {
        return passwordEncoder.encode(rawPassword);
    }

    private boolean looksEncoded(String storedPassword) {
        return storedPassword.startsWith("$2a$") || storedPassword.startsWith("$2b$") || storedPassword.startsWith("$2y$");
    }
}
