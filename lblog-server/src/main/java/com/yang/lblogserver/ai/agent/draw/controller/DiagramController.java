package com.yang.lblogserver.ai.agent.draw.controller;

import com.yang.lblogserver.ai.agent.draw.DiagramService;
import com.yang.lblogserver.ai.agent.draw.DrawChatRequest;
import com.yang.lblogserver.ai.agent.draw.DrawConfigVO;
import com.yang.lblogserver.ai.agent.draw.DiagramProperties;
import com.yang.lblogserver.ai.agent.draw.config.DrawRateLimiter;
import com.yang.lblogserver.common.ApiResponse;
import com.yang.lblogserver.site.mapper.SiteConfigMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.concurrent.TimeUnit;

@Tag(name = "AI 绘图", description = "AI 辅助 draw.io 图表生成")
@RestController
@RequestMapping("/api/v1/draw")
public class DiagramController {

    private final DiagramService diagramService;
    private final DiagramProperties diagramProperties;
    private final DrawRateLimiter drawRateLimiter;
    private final SiteConfigMapper siteConfigMapper;

    public DiagramController(DiagramService diagramService, DiagramProperties diagramProperties,
                             DrawRateLimiter drawRateLimiter, SiteConfigMapper siteConfigMapper) {
        this.diagramService = diagramService;
        this.diagramProperties = diagramProperties;
        this.drawRateLimiter = drawRateLimiter;
        this.siteConfigMapper = siteConfigMapper;
    }

    private boolean isAiDrawEnabled() {
        String val = siteConfigMapper.selectConfigValue("ai_draw_chat_enabled");
        return "true".equalsIgnoreCase(val);
    }

    @Operation(summary = "AI 绘图对话", description = "SSE 流式接口，用于 AI 绘图对话")
    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chat(@RequestBody DrawChatRequest request, HttpServletRequest httpRequest) {
        if (!isAiDrawEnabled()) {
            throw new IllegalArgumentException("AI drawing is disabled");
        }

        if (request.getMessages() == null || request.getMessages().isEmpty()) {
            throw new IllegalArgumentException("messages cannot be empty");
        }

        String ip = httpRequest.getRemoteAddr();
        if (!drawRateLimiter.tryAcquire(ip)) {
            throw new IllegalArgumentException("Too many requests. Please try again later.");
        }

        SseEmitter emitter = new SseEmitter(TimeUnit.MINUTES.toMillis(3));
        emitter.onTimeout(emitter::complete);

        diagramService.chatStream(request, emitter);

        return emitter;
    }

    @Operation(summary = "获取绘图配置", description = "返回 AI 绘图功能的配置信息")
    @GetMapping("/config")
    public ApiResponse<DrawConfigVO> getConfig() {
        DrawConfigVO config = new DrawConfigVO(
                isAiDrawEnabled(),
                diagramProperties.getModel(),
                diagramProperties.getMaxTokens()
        );
        return ApiResponse.success(config);
    }
}
