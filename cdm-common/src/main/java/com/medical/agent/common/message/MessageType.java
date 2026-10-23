package com.medical.agent.common.message;

public final class MessageType {
    public static final String ASSESS_REQUEST = "ASSESS_REQUEST";
    public static final String ASSESS_RESULT = "ASSESS_RESULT";
    public static final String FOLLOWUP_REQUEST = "FOLLOWUP_REQUEST";
    public static final String FOLLOWUP_CREATED = "FOLLOWUP_CREATED";
    public static final String RESOURCE_REQUEST = "RESOURCE_REQUEST";
    public static final String RESOURCE_RESULT = "RESOURCE_RESULT";

    private MessageType() {
    }
}
