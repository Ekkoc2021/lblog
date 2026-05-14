package com.yang.lblogserver.diagram.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * SSE 事件模型（预留）。
 * 当前实测量直接使用 Map + ObjectMapper 序列化 SSE 事件，
 * 未使用此类。保留以供后续统一事件格式使用。
 *
 * 实际使用的 SSE 事件结构（在 DisplayDiagramTool 和 DiagramService 中构建）：
 *   text-delta: {"type":"text-delta","delta":"..."}
 *   tool-call:  {"type":"tool-call","name":"display_diagram","arguments":{"xml":"..."}}
 *   done:       {"type":"done","sessionId":"..."}
 *   heartbeat:  {"type":"heartbeat"}
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SseEvent {
    private String type;
    private String name;
    private String content;

    public static SseEvent withType(String type, String name, String content) {
        return new SseEvent(type, name, content);
    }
}
