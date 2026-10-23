package com.medical.agent.common.util;

import com.medical.agent.common.enums.RiskLevel;

public final class RiskScoreUtil {

    private RiskScoreUtil() {}

    /**
     * 根据累计评分计算风险等级（通用评分模型）
     */
    public static RiskLevel calculateByScore(double score, double lowMax, double medMax, double highMax) {
        if (score <= lowMax) return RiskLevel.LOW;
        if (score <= medMax) return RiskLevel.MEDIUM;
        if (score <= highMax) return RiskLevel.HIGH;
        return RiskLevel.CRITICAL;
    }

    /**
     * 计算BMI
     */
    public static double calculateBMI(double weightKg, double heightCm) {
        if (heightCm <= 0) return 0;
        double heightM = heightCm / 100.0;
        return Math.round(weightKg / (heightM * heightM) * 10.0) / 10.0;
    }
}
