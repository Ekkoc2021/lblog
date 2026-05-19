package com.yang.lblogserver.ai.memory;

import com.yang.lblogserver.ai.conversation.domain.ChatMessage;
import com.yang.lblogserver.ai.conversation.domain.ChatSession;

import java.util.List;

public interface ChatMemoryStore {

    List<ChatMessage> loadHistory(Long sessionId);

    void saveMessages(Long sessionId, List<ChatMessage> messages);

    ChatSession getOrCreateSession(Long userId, String agentType, String modelName);

    int getMaxMsgIndex(Long sessionId);

    void touchSession(Long sessionId);
}
