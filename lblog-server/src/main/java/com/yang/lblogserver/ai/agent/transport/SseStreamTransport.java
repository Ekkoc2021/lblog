package com.yang.lblogserver.ai.agent.transport;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * SSE 实现。只管协议——把 data 序列化为 JSON、写 SSE 格式、管理 emitter 和心跳。
 * 不管 data 里有什么（事件类型和结构由 Agent 自行定义）。
 */
public class SseStreamTransport implements AgentStreamTransport {

    private static final Logger log = LoggerFactory.getLogger(SseStreamTransport.class);

    private final SseEmitter emitter;
    private final ScheduledExecutorService heartbeatScheduler;
    private final int heartbeatIntervalSeconds;
    private ScheduledFuture<?> heartbeat;

    public SseStreamTransport(SseEmitter emitter, ScheduledExecutorService heartbeatScheduler,
                              int heartbeatIntervalSeconds) {
        this.emitter = emitter;
        this.heartbeatScheduler = heartbeatScheduler;
        this.heartbeatIntervalSeconds = heartbeatIntervalSeconds;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void send(String eventType, Object data) {
        // 前端通过 data.type 路由，SSE 协议层负责注入
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", eventType);
        if (data instanceof Map) {
            payload.putAll((Map<String, Object>) data);
        }
        try {
            emitter.send(SseEmitter.event().data(payload));
        } catch (Exception e) {
            log.warn("Failed to send SSE event [{}]", eventType, e);
        }
    }

    @Override
    public void start() {
        // interval <= 0 表示不使用心跳，Agent 自行决定
        if (heartbeatIntervalSeconds <= 0) {
            return;
        }
        Thread workThread = Thread.currentThread();
        this.heartbeat = heartbeatScheduler.scheduleAtFixedRate(() -> {
            try {
                emitter.send(SseEmitter.event().data("{}"));
            } catch (Exception e) {
                workThread.interrupt();
                throw new RuntimeException(e);
            }
        }, heartbeatIntervalSeconds, heartbeatIntervalSeconds, TimeUnit.SECONDS);

        emitter.onCompletion(() -> heartbeat.cancel(false));
    }

    @Override
    public void stop() {
        if (heartbeat != null) {
            heartbeat.cancel(false);
        }
    }

    @Override
    public void complete() {
        stop();
        emitter.complete();
    }

    @Override
    public void completeWithError(Throwable e) {
        stop();
        emitter.completeWithError(e);
    }
}
