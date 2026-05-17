package com.yang.lblogserver.ai.chat.service;

import com.yang.lblogserver.ai.chat.domain.ChatMessage;

import java.util.List;

public interface ChatMessageService {

    ChatMessage saveUserMessage(Long sessionId, String content, int msgIndex);

    ChatMessage saveAssistantMessage(Long sessionId, String content,
                                     String reasoningContent, String toolCallsJson,
                                     int msgIndex, int tokens);

    ChatMessage saveToolMessage(Long sessionId, String toolCallId,
                                String name, String content, int msgIndex);

    List<ChatMessage> batchSave(Long sessionId, List<ChatMessage> messages);

    List<ChatMessage> loadMessages(Long sessionId);

    List<ChatMessage> loadRecentMessages(Long sessionId, int limit);

    int getMaxMsgIndex(Long sessionId);

    void deleteBySession(Long sessionId);
}
