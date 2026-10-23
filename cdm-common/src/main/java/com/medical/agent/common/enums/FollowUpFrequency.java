package com.medical.agent.common.enums;

public enum FollowUpFrequency {
    IMMEDIATE("立即随访", 0),
    WEEKLY("每周随访", 7),
    BIWEEKLY("双周随访", 14),
    MONTHLY("每月随访", 30),
    QUARTERLY("每季度随访", 90);

    private final String displayName;
    private final int intervalDays;

    FollowUpFrequency(String displayName, int intervalDays) {
        this.displayName = displayName;
        this.intervalDays = intervalDays;
    }

    public String getDisplayName() { return displayName; }
    public int getIntervalDays() { return intervalDays; }
}
