package com.yang.lblogserver.ai.conversation.service.impl;

import com.yang.lblogserver.ai.conversation.domain.ChatSession;
import com.yang.lblogserver.ai.conversation.domain.ChatSessionVO;
import com.yang.lblogserver.ai.conversation.mapper.ChatSessionMapper;
import com.yang.lblogserver.ai.conversation.service.ChatSessionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ChatSessionServiceImpl implements ChatSessionService {

    private final ChatSessionMapper sessionMapper;

    public ChatSessionServiceImpl(ChatSessionMapper sessionMapper) {
        this.sessionMapper = sessionMapper;
    }

    @Override
    @Transactional
    public ChatSession createSession(Long userId, String agentType, String modelName) {
        ChatSession session = new ChatSession();
        session.setUserId(userId);
        session.setAgentType(agentType);
        session.setModelName(modelName);
        session.setTitle("New " + agentType + " session");
        session.setStatus(1);
        sessionMapper.insert(session);
        return session;
    }

    @Override
    public List<ChatSessionVO> listSessions(Long userId, String agentType, int page, int size) {
        int offset = (page - 1) * size;
        return sessionMapper.selectByUserAndAgent(userId, agentType, offset, size);
    }

    @Override
    public ChatSession getSession(Long sessionId) {
        return sessionMapper.selectById(sessionId);
    }

    @Override
    @Transactional
    public void updateTitle(Long sessionId, String title) {
        sessionMapper.updateTitle(sessionId, title);
    }

    @Override
    @Transactional
    public void archiveSession(Long sessionId) {
        sessionMapper.updateStatus(sessionId, 0);
    }

    @Override
    @Transactional
    public void deleteSession(Long sessionId) {
        sessionMapper.updateStatus(sessionId, -1);
    }

    @Override
    public boolean checkOwnership(Long sessionId, Long userId) {
        ChatSession session = sessionMapper.selectById(sessionId);
        if (session == null) return false;
        if (userId == null) return session.getUserId() == null;
        return userId.equals(session.getUserId());
    }
}
