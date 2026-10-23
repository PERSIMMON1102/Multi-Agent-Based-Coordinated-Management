package com.medical.agent.service.clinicaljob;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
public class ClinicalDataJobExecutorConfig {

    @Bean(name = "clinicalDataJobExecutor")
    public Executor clinicalDataJobExecutor(
            @Value("${clinical-data-job.pool.core-size:2}") int coreSize,
            @Value("${clinical-data-job.pool.max-size:4}") int maxSize,
            @Value("${clinical-data-job.pool.queue-capacity:64}") int queueCapacity,
            @Value("${clinical-data-job.pool.keep-alive-seconds:60}") int keepAliveSeconds) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(Math.max(1, coreSize));
        executor.setMaxPoolSize(Math.max(Math.max(1, coreSize), maxSize));
        executor.setQueueCapacity(Math.max(0, queueCapacity));
        executor.setKeepAliveSeconds(Math.max(10, keepAliveSeconds));
        executor.setThreadNamePrefix("clinical-job-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.initialize();
        return executor;
    }
}
