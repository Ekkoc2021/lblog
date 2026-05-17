package com.yang.lblogserver.ai.chat.domain;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessage {
    private Long id;
    private Long sessionId;
    private String role;
    private String content;
    private String reasoningContent;
    private String toolCalls;
    private String toolCallId;
    private String name;
    private Integer msgIndex;
    private Integer tokens;
    private String metadata;
    private LocalDateTime createdAt;
}
