package com.medical.agent.agent.config;

import jade.core.Profile;
import jade.core.ProfileImpl;
import jade.core.Runtime;
import jade.wrapper.AgentContainer;
import jade.wrapper.AgentController;
import jade.wrapper.StaleProxyException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "jade.enabled", havingValue = "true")
public class JadeConfig {

    private static final Logger log = LoggerFactory.getLogger(JadeConfig.class);

    @Bean(destroyMethod = "")
    public AgentContainer mainContainer() {
        Runtime runtime = Runtime.instance();
        Profile profile = new ProfileImpl();
        profile.setParameter(Profile.MAIN_HOST, "localhost");
        profile.setParameter(Profile.MAIN_PORT, "1099");
        profile.setParameter(Profile.GUI, "false");

        AgentContainer container = runtime.createMainContainer(profile);
        log.info("JADE main container started on localhost:1099");
        return container;
    }

    public AgentController createAgent(AgentContainer container, String name, String className, Object[] args) {
        try {
            AgentController agent = container.createNewAgent(name, className, args);
            agent.start();
            log.info("Agent '{}' started successfully (class: {})", name, className);
            return agent;
        } catch (StaleProxyException e) {
            throw new RuntimeException("Failed to create agent: " + name, e);
        }
    }
}
