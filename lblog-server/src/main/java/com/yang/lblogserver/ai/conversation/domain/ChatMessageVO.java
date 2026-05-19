package com.yang.lblogserver.ai.conversation.domain;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
public class ChatMessageVO {
    private Long id;
    private Long sessionId;
    private String role;
    private String content;
    private String reasoningContent;
    private List<ToolCallVO> toolCalls;
    private Integer msgIndex;
    private LocalDateTime createdAt;

    @Data
    public static class ToolCallVO {
        private String id;
        private String name;
        private Map<String, Object> arguments;
    }
}
