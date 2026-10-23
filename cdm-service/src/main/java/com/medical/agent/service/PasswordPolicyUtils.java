package com.medical.agent.service;

import com.medical.agent.common.exception.BusinessException;

public final class PasswordPolicyUtils {

    private PasswordPolicyUtils() {
    }

    public static String requireStrongPassword(String password, String blankMessage) {
        if (password == null || password.trim().isEmpty()) {
            throw new BusinessException(400, blankMessage);
        }

        String normalized = password.trim();
        if (!isStrongPassword(normalized)) {
            throw new BusinessException(400, "密码至少 8 位，且需同时包含字母和数字");
        }
        return normalized;
    }

    public static String optionalStrongPassword(String password) {
        if (password == null || password.trim().isEmpty()) {
            return null;
        }

        String normalized = password.trim();
        if (!isStrongPassword(normalized)) {
            throw new BusinessException(400, "密码至少 8 位，且需同时包含字母和数字");
        }
        return normalized;
    }

    private static boolean isStrongPassword(String password) {
        if (password.length() < 8) {
            return false;
        }

        boolean hasLetter = false;
        boolean hasDigit = false;
        for (int i = 0; i < password.length(); i++) {
            char ch = password.charAt(i);
            if (Character.isLetter(ch)) {
                hasLetter = true;
            } else if (Character.isDigit(ch)) {
                hasDigit = true;
            }
        }
        return hasLetter && hasDigit;
    }
}
