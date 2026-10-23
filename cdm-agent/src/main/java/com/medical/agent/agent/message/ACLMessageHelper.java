package com.medical.agent.agent.message;

import com.medical.agent.common.message.AgentMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jade.lang.acl.ACLMessage;

/**
 * ACL消息与AgentMessage之间的序列化/反序列化工具
 */
public final class ACLMessageHelper {
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    private ACLMessageHelper() {}

    public static String serialize(AgentMessage msg) {
        try {
            return MAPPER.writeValueAsString(msg);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize AgentMessage", e);
        }
    }

    public static AgentMessage deserialize(String json) {
        try {
            return MAPPER.readValue(json, AgentMessage.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to deserialize AgentMessage", e);
        }
    }

    public static ACLMessage createInformMessage(AgentMessage msg) {
        ACLMessage aclMsg = new ACLMessage(ACLMessage.INFORM);
        aclMsg.setContent(serialize(msg));
        aclMsg.setLanguage("JSON");
        aclMsg.setOntology("chronic-disease-management");
        return aclMsg;
    }

    public static ACLMessage createRequestMessage(AgentMessage msg) {
        ACLMessage aclMsg = new ACLMessage(ACLMessage.REQUEST);
        aclMsg.setContent(serialize(msg));
        aclMsg.setLanguage("JSON");
        aclMsg.setOntology("chronic-disease-management");
        return aclMsg;
    }
}
