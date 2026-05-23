package com.yang.lblogserver.ai.agent.transport;

/**
 * Agent 流式传输抽象。不定义业务事件——事件类型和数据结构由 Agent 自行约定。
 * 当前实现 SSE，未来可切换 WebSocket 等。
 */
public interface AgentStreamTransport {

    /**
     * 发送一个事件。eventType 由 Agent 自定义（如 "text-delta"、"reasoning"、"tool-call"、"done"），
     * data 为事件负载，实现层负责序列化（SSE 转为 JSON）。
     */
    void send(String eventType, Object data);

    /**
     * 启动传输。实现类自行决定是否启用心跳断连检测。
     * SseStreamTransport 在 heartbeatIntervalSeconds <= 0 时跳过心跳。
     */
    void start();

    /** 停止传输，清理心跳 */
    void stop();

    /** 正常结束 */
    void complete();

    /** 异常结束 */
    void completeWithError(Throwable e);
}
