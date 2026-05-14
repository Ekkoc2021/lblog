package com.yang.lblogserver.diagram.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

import java.util.concurrent.*;

/**
 * AI 绘图线程池配置。
 *
 * diagramTaskExecutor 使用虚拟线程 + 有限队列（最大 20 并发）。
 * 超过时由 CallerRunsPolicy 降级（谁提交谁执行），
 * 配合 DrawRateLimiter（每 IP 每分钟 10 次）做双层保护。
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
