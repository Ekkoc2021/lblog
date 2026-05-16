package com.yang.lblogserver.ai.agent.draw;

import lombok.Data;
import java.util.List;

@Data
public class DrawChatRequest {
    private List<ChatMessage> messages;
    private String xml;
    private String previousXml;
    private String sessionId;
    private Boolean minimalStyle;
    private String customSystemMessage;

    @Data
    public static class ChatMessage {
        private String role;
        private String content;
    }
}
