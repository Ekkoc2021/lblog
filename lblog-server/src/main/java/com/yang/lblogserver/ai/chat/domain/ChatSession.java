package com.yang.lblogserver.ai.chat.domain;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatSession {
    private Long id;
    private Long userId;
    private String agentType;
    private String title;
    private String modelName;
    private Integer messageCount;
    private Integer totalTokens;
    private Integer status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
