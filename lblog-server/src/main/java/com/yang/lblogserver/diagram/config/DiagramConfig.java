package com.yang.lblogserver.diagram.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

@Configuration
@EnableAsync
public class DiagramConfig {

    @Bean("diagramTaskExecutor")
    public Executor diagramTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(10);
        executor.setThreadNamePrefix("diagram-");
        executor.initialize();
        return executor;
    }

    @Bean("heartbeatScheduler")
    public ScheduledExecutorService heartbeatScheduler() {
        return Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "diagram-heartbeat");
            t.setDaemon(true);
            return t;
        });
    }
}
