package com.yang.lblogserver.ai.conversation.domain;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ChatSessionVO {
    private Long id;
    private String title;
    private String agentType;
    private String modelName;
    private Integer messageCount;
    private String previewText;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
