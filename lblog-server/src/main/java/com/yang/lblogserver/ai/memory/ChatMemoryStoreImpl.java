package com.yang.lblogserver.ai.memory;

import com.yang.lblogserver.ai.conversation.domain.ChatMessage;
import com.yang.lblogserver.ai.conversation.domain.ChatSession;
import com.yang.lblogserver.ai.conversation.service.ChatMessageService;
import com.yang.lblogserver.ai.conversation.service.ChatSessionService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ChatMemoryStoreImpl implements ChatMemoryStore {

    private final ChatSessionService sessionService;
    private final ChatMessageService messageService;

    public ChatMemoryStoreImpl(ChatSessionService sessionService, ChatMessageService messageService) {
        this.sessionService = sessionService;
        this.messageService = messageService;
    }

    @Override
    public List<ChatMessage> loadHistory(Long sessionId) {
        return messageService.loadMessages(sessionId);
    }

    @Override
    public void saveMessages(Long sessionId, List<ChatMessage> messages) {
        messageService.batchSave(sessionId, messages);
    }

    @Override
    public ChatSession getOrCreateSession(Long userId, String agentType, String modelName) {
        return sessionService.createSession(userId, agentType, modelName);
    }

    @Override
    public int getMaxMsgIndex(Long sessionId) {
        return messageService.getMaxMsgIndex(sessionId);
    }

    @Override
    public void touchSession(Long sessionId) {
    }
}
