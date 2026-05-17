package com.yang.lblogserver.ai.chat.service.impl;

import com.yang.lblogserver.ai.chat.domain.ChatMessage;
import com.yang.lblogserver.ai.chat.mapper.ChatMessageMapper;
import com.yang.lblogserver.ai.chat.service.ChatMessageService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ChatMessageServiceImpl implements ChatMessageService {

    private final ChatMessageMapper messageMapper;

    public ChatMessageServiceImpl(ChatMessageMapper messageMapper) {
        this.messageMapper = messageMapper;
    }

    @Override
    @Transactional
    public ChatMessage saveUserMessage(Long sessionId, String content, int msgIndex) {
        ChatMessage msg = new ChatMessage();
        msg.setSessionId(sessionId);
        msg.setRole("user");
        msg.setContent(content);
        msg.setMsgIndex(msgIndex);
        messageMapper.insert(msg);
        return msg;
    }

    @Override
    @Transactional
    public ChatMessage saveAssistantMessage(Long sessionId, String content,
                                            String reasoningContent, String toolCallsJson,
                                            int msgIndex, int tokens) {
        ChatMessage msg = new ChatMessage();
        msg.setSessionId(sessionId);
        msg.setRole("assistant");
        msg.setContent(content);
        msg.setReasoningContent(reasoningContent);
        msg.setToolCalls(toolCallsJson);
        msg.setMsgIndex(msgIndex);
        msg.setTokens(tokens);
        messageMapper.insert(msg);
        return msg;
    }

    @Override
    @Transactional
    public ChatMessage saveToolMessage(Long sessionId, String toolCallId,
                                       String name, String content, int msgIndex) {
        ChatMessage msg = new ChatMessage();
        msg.setSessionId(sessionId);
        msg.setRole("tool");
        msg.setToolCallId(toolCallId);
        msg.setName(name);
        msg.setContent(content);
        msg.setMsgIndex(msgIndex);
        messageMapper.insert(msg);
        return msg;
    }

    @Override
    @Transactional
    public List<ChatMessage> batchSave(Long sessionId, List<ChatMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return messages;
        }
        for (ChatMessage msg : messages) {
            msg.setSessionId(sessionId);
        }
        messageMapper.batchInsert(messages);
        return messages;
    }

    @Override
    public List<ChatMessage> loadMessages(Long sessionId) {
        return messageMapper.selectBySessionId(sessionId);
    }

    @Override
    public List<ChatMessage> loadRecentMessages(Long sessionId, int limit) {
        return messageMapper.selectRecentBySessionId(sessionId, limit);
    }

    @Override
    public int getMaxMsgIndex(Long sessionId) {
        return messageMapper.selectMaxMsgIndex(sessionId);
    }

    @Override
    @Transactional
    public void deleteBySession(Long sessionId) {
        messageMapper.deleteBySessionId(sessionId);
    }
}
