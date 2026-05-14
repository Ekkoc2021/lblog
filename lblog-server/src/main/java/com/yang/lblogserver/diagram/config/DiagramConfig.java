package com.yang.lblogserver.diagram.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/**
 * AI 绘图线程池配置。
 *
 * 两个线程池：
 *   1. diagramTaskExecutor — AI 调用异步执行（@Async）
 *      核心 2 线程，最大 4 线程，队列 10
 *      每个 AI 请求 10-30s，2 个核心线程可处理约 4-12 并发
 *      超过队列上限时由 CallerRunsPolicy 降级（谁提交谁执行）
 *
 *   2. heartbeatScheduler — SSE 心跳定时器
 *      单线程 daemon，每 15s 向所有活跃 SSE 连接发送 heartbeat
 */
@Configuration
@EnableAsync  // 启用 @Async 注解，使 DiagramService.chatStream() 异步执行
public class DiagramConfig {

    /**
     * AI 任务线程池。
     * 用于 @Async("diagramTaskExecutor") 的方法（DiagramService.chatStream()）。
     * 每个请求独立一个线程，避免阻塞 Tomcat worker 线程。
     */
    @Bean("diagramTaskExecutor")
    public Executor diagramTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);      // 核心线程数
        executor.setMaxPoolSize(4);       // 最大线程数（超过队列上限时）
        executor.setQueueCapacity(10);    // 等待队列容量
        executor.setThreadNamePrefix("diagram-"); // 线程名前缀，方便日志定位
        executor.initialize();
        return executor;
    }

    /**
     * 心跳定时器。
     * 单线程 daemon，每 15s 向 SSE 连接发送 heartbeat 事件。
     * Nginx 默认 60s 断开空闲连接，heartbeat 能有效防止断连。
     */
    @Bean("heartbeatScheduler")
    public ScheduledExecutorService heartbeatScheduler() {
        return Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "diagram-heartbeat");
            t.setDaemon(true); // daemon 线程，不会阻止 JVM 退出
            return t;
        });
    }
}
