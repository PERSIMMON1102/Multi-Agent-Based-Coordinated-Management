package com.medical.agent.service.react.orchestration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
public class OrchestrationExecutorConfig {

    @Bean(name = "orchestrationTaskExecutor")
    public Executor orchestrationTaskExecutor(
            @Value("${orchestration.dispatch.pool.core-size:2}") int coreSize,
            @Value("${orchestration.dispatch.pool.max-size:4}") int maxSize,
            @Value("${orchestration.dispatch.pool.queue-capacity:32}") int queueCapacity,
            @Value("${orchestration.dispatch.pool.keep-alive-seconds:60}") int keepAliveSeconds) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(Math.max(1, coreSize));
        executor.setMaxPoolSize(Math.max(Math.max(1, coreSize), maxSize));
        executor.setQueueCapacity(Math.max(0, queueCapacity));
        executor.setKeepAliveSeconds(Math.max(10, keepAliveSeconds));
        executor.setThreadNamePrefix("orch-dispatch-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.initialize();
        return executor;
    }
}
