package com.medical.agent.common.enums;

public enum DiseaseType {
    DIABETES("2型糖尿病", "T2DM"),
    HYPERTENSION("原发性高血压", "HTN"),
    COPD("慢性阻塞性肺疾病", "COPD"),
    CKD("慢性肾脏病", "CKD"),
    CHD("冠心病", "CHD"),
    STROKE("脑卒中", "STROKE");

    private final String displayName;
    private final String code;

    DiseaseType(String displayName, String code) {
        this.displayName = displayName;
        this.code = code;
    }

    public String getDisplayName() { return displayName; }
    public String getCode() { return code; }
}
