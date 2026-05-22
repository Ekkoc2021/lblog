package com.yang.lblogserver.ai.conversation.service;

import com.yang.lblogserver.ai.conversation.domain.ChatSession;
import com.yang.lblogserver.ai.conversation.domain.ChatSessionVO;

import java.util.List;

public interface ChatSessionService {

    ChatSession createSession(Long userId, String agentType, String modelName);

    List<ChatSessionVO> listSessions(Long userId, String agentType, int page, int size);

    ChatSession getSession(Long sessionId);

    void updateTitle(Long sessionId, String title);

    void archiveSession(Long sessionId);

    void deleteSession(Long sessionId);

    boolean checkOwnership(Long sessionId, Long userId);
}
