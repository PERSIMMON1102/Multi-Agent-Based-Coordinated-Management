package com.medical.agent.web;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(
        scanBasePackages = "com.medical.agent",
        exclude = UserDetailsServiceAutoConfiguration.class
)
@EntityScan(basePackages = "com.medical.agent.model.entity")
@EnableJpaRepositories(basePackages = "com.medical.agent.model.repository")
@EnableMongoRepositories(basePackages = "com.medical.agent.model.mongo.repository")
@EnableScheduling
@EnableCaching
public class CdmApplication {
    public static void main(String[] args) {
        SpringApplication.run(CdmApplication.class, args);
    }
}
