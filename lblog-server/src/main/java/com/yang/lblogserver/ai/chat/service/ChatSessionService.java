package com.yang.lblogserver.ai.chat.service;

import com.yang.lblogserver.ai.chat.domain.ChatSession;
import com.yang.lblogserver.ai.chat.domain.ChatSessionVO;

import java.util.List;

public interface ChatSessionService {

    ChatSession createSession(Long userId, String agentType, String modelName);

    List<ChatSessionVO> listSessions(Long userId, String agentType, int page, int size);

    ChatSession getSession(Long sessionId);

    void updateTitle(Long sessionId, String title);

    void updateStats(Long sessionId, int messageCountDelta, int tokensDelta);

    void archiveSession(Long sessionId);

    void deleteSession(Long sessionId);

    boolean checkOwnership(Long sessionId, Long userId);
}
