package com.yang.lblogserver.ai.chat.controller;

import com.yang.lblogserver.ai.chat.domain.ChatMessage;
import com.yang.lblogserver.ai.chat.domain.ChatMessageVO;
import com.yang.lblogserver.ai.chat.domain.ChatSession;
import com.yang.lblogserver.ai.chat.domain.ChatSessionVO;
import com.yang.lblogserver.ai.chat.service.ChatMessageService;
import com.yang.lblogserver.ai.chat.service.ChatSessionService;
import com.yang.lblogserver.auth.security.model.LoginUser;
import com.yang.lblogserver.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

@Tag(name = "AI 对话历史", description = "AI 对话会话管理")
@RestController
@RequestMapping("/api/v1/ai/chat")
public class ChatSessionController {

    private final ChatSessionService sessionService;
    private final ChatMessageService messageService;

    public ChatSessionController(ChatSessionService sessionService, ChatMessageService messageService) {
        this.sessionService = sessionService;
        this.messageService = messageService;
    }

    @Operation(summary = "获取会话列表")
    @GetMapping("/sessions")
    public ApiResponse<List<ChatSessionVO>> listSessions(
            @RequestParam String agentType,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        Long userId = getCurrentUserId();
        return ApiResponse.success(sessionService.listSessions(userId, agentType, page, size));
    }

    @Operation(summary = "创建新会话")
    @PostMapping("/sessions")
    public ApiResponse<ChatSessionVO> createSession(@RequestBody CreateSessionRequest request) {
        Long userId = getCurrentUserId();
        ChatSession session = sessionService.createSession(userId, request.getAgentType(), request.getModelName());
        return ApiResponse.success(convertToVO(session));
    }

    @Operation(summary = "更新会话标题")
    @PutMapping("/sessions/{id}/title")
    public ApiResponse<Void> updateTitle(@PathVariable Long id, @RequestBody UpdateTitleRequest request) {
        checkOwnership(id);
        sessionService.updateTitle(id, request.getTitle());
        return ApiResponse.success(null);
    }

    @Operation(summary = "删除会话")
    @DeleteMapping("/sessions/{id}")
    public ApiResponse<Void> deleteSession(@PathVariable Long id) {
        checkOwnership(id);
        sessionService.deleteSession(id);
        return ApiResponse.success(null);
    }

    @Operation(summary = "加载会话消息")
    @GetMapping("/sessions/{id}/messages")
    public ApiResponse<List<ChatMessageVO>> loadMessages(@PathVariable Long id) {
        checkOwnership(id);
        List<ChatMessage> messages = messageService.loadMessages(id);
        return ApiResponse.success(convertToVOList(messages));
    }

    private Long getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof LoginUser) {
            return ((LoginUser) authentication.getPrincipal()).getUserId();
        }
        return null;
    }

    private void checkOwnership(Long sessionId) {
        Long userId = getCurrentUserId();
        if (!sessionService.checkOwnership(sessionId, userId)) {
            throw new IllegalArgumentException("Session not found or access denied");
        }
    }

    private ChatSessionVO convertToVO(ChatSession session) {
        ChatSessionVO vo = new ChatSessionVO();
        vo.setId(session.getId());
        vo.setTitle(session.getTitle());
        vo.setAgentType(session.getAgentType());
        vo.setModelName(session.getModelName());
        vo.setMessageCount(session.getMessageCount());
        vo.setCreatedAt(session.getCreatedAt());
        vo.setUpdatedAt(session.getUpdatedAt());
        return vo;
    }

    private List<ChatMessageVO> convertToVOList(List<ChatMessage> messages) {
        List<ChatMessageVO> result = new ArrayList<>();
        for (ChatMessage msg : messages) {
            ChatMessageVO vo = new ChatMessageVO();
            vo.setId(msg.getId());
            vo.setSessionId(msg.getSessionId());
            vo.setRole(msg.getRole());
            vo.setContent(msg.getContent());
            vo.setReasoningContent(msg.getReasoningContent());
            vo.setMsgIndex(msg.getMsgIndex());
            vo.setCreatedAt(msg.getCreatedAt());
            result.add(vo);
        }
        return result;
    }

    public static class CreateSessionRequest {
        private String agentType;
        private String modelName;

        public String getAgentType() { return agentType; }
        public void setAgentType(String agentType) { this.agentType = agentType; }
        public String getModelName() { return modelName; }
        public void setModelName(String modelName) { this.modelName = modelName; }
    }

    public static class UpdateTitleRequest {
        private String title;

        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
    }
}
