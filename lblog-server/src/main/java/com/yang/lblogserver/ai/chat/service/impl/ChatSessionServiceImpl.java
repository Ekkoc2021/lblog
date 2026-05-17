package com.yang.lblogserver.ai.chat.service.impl;

import com.yang.lblogserver.ai.chat.domain.ChatSession;
import com.yang.lblogserver.ai.chat.domain.ChatSessionVO;
import com.yang.lblogserver.ai.chat.mapper.ChatSessionMapper;
import com.yang.lblogserver.ai.chat.service.ChatSessionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
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
        session.setMessageCount(0);
        session.setTotalTokens(0);
        session.setStatus(1);
        sessionMapper.insert(session);
        return session;
    }

    @Override
    public List<ChatSessionVO> listSessions(Long userId, String agentType, int page, int size) {
        int offset = (page - 1) * size;
        List<ChatSession> sessions = sessionMapper.selectByUserAndAgent(userId, agentType, offset, size);
        List<ChatSessionVO> result = new ArrayList<>();
        for (ChatSession s : sessions) {
            ChatSessionVO vo = new ChatSessionVO();
            vo.setId(s.getId());
            vo.setTitle(s.getTitle());
            vo.setAgentType(s.getAgentType());
            vo.setModelName(s.getModelName());
            vo.setMessageCount(s.getMessageCount());
            vo.setCreatedAt(s.getCreatedAt());
            vo.setUpdatedAt(s.getUpdatedAt());
            result.add(vo);
        }
        return result;
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
    public void updateStats(Long sessionId, int messageCountDelta, int tokensDelta) {
        sessionMapper.updateStats(sessionId, messageCountDelta, tokensDelta);
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
