package com.yang.lblogserver.ai.agent.draw.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

import java.util.concurrent.*;

/**
 * AI 线程池配置（可被多个 AI 子域共用）。
 */
@Configuration
@EnableAsync
public class DiagramConfig {

    @Bean("diagramTaskExecutor")
    public Executor diagramTaskExecutor() {
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                20, 20, 0L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(100),
                Thread.ofVirtual().factory()
        );
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
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
