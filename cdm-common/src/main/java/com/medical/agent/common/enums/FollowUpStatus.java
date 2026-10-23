package com.medical.agent.common.enums;

public enum FollowUpStatus {
    PENDING("待执行"),
    IN_PROGRESS("进行中"),
    COMPLETED("已完成"),
    OVERDUE("已逾期"),
    CANCELLED("已取消");

    private final String displayName;

    FollowUpStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() { return displayName; }
}
