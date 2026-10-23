package com.medical.agent.agent.behaviour;

import com.medical.agent.agent.message.ACLMessageHelper;
import com.medical.agent.common.message.AgentMessage;
import com.medical.agent.common.message.MessageType;
import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Consumer;

/**
 * 通用消息接收行为 - 循环监听指定类型的ACL消息
 */
public class MessageListenerBehaviour extends CyclicBehaviour {

    private static final Logger log = LoggerFactory.getLogger(MessageListenerBehaviour.class);
    private final MessageTemplate template;
    private final Consumer<AgentMessage> handler;

    public MessageListenerBehaviour(Agent agent, int performative, Consumer<AgentMessage> handler) {
        super(agent);
        this.template = MessageTemplate.MatchPerformative(performative);
        this.handler = handler;
    }

    @Override
    public void action() {
        ACLMessage msg = myAgent.receive(template);
        if (msg != null) {
            try {
                AgentMessage agentMsg = ACLMessageHelper.deserialize(msg.getContent());
                log.info("[{}] Received message type={} from {}",
                        myAgent.getLocalName(), agentMsg.getMessageType(), msg.getSender().getLocalName());
                handler.accept(agentMsg);
            } catch (Exception e) {
                log.error("[{}] Failed to process message: {}", myAgent.getLocalName(), e.getMessage(), e);
            }
        } else {
            block();
        }
    }
}
