package com.medical.agent.common.enums;

public enum RiskLevel {
    LOW("低风险", 1),
    MEDIUM("中风险", 2),
    HIGH("高风险", 3),
    CRITICAL("危急", 4);

    private final String displayName;
    private final int level;

    RiskLevel(String displayName, int level) {
        this.displayName = displayName;
        this.level = level;
    }

    public String getDisplayName() { return displayName; }
    public int getLevel() { return level; }

    public static RiskLevel fromLevel(int level) {
        for (RiskLevel rl : values()) {
            if (rl.level == level) return rl;
        }
        return LOW;
    }
}
