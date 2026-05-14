package com.yang.lblogserver.diagram.model;

import lombok.Data;
import java.util.List;

/**
 * AI 绘图对话请求体。
 *
 * 对应 next-ai-draw-io 的 POST /api/chat 请求体结构。
 *
 * 字段说明：
 *   messages     — 对话历史 [{role, content}, ...]
 *                  每次请求携带全部历史，AI 据此理解上下文
 *   xml          — 当前图表 XML
 *                  多轮对话时携带，AI 作为"当前图表 XML"参考
 *                  首次对话为空字符串
 *   previousXml  — 上一版图表 XML
 *                  用于多轮对话的历史 XML 占位符替换
 *                  前端在上次渲染时会保存此值
 *   sessionId    — 前端生成的会话 UUID
 *                  用于关联多轮对话，暂未启用后端会话持久化
 *   minimalStyle — 是否启用极简风格
 *                  true=纯黑白无样式，false=默认彩色样式
 *   customSystemMessage — 用户自定义系统指令
 *                          追加在 system prompt 末尾，最长 5000 字符
 */
@Data
public class DrawChatRequest {
    private List<ChatMessage> messages;     // 对话历史
    private String xml;                      // 当前图表 XML
    private String previousXml;              // 上一版图表 XML
    private String sessionId;                // 会话 ID（前端生成）
    private Boolean minimalStyle;            // 极简风格
    private String customSystemMessage;      // 自定义指令

    @Data
    public static class ChatMessage {
        private String role;    // "user" | "assistant" | "system"
        private String content; // 消息内容
    }
}
