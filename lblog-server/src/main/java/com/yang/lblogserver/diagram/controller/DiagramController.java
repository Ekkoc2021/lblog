package com.yang.lblogserver.diagram.controller;

import com.yang.lblogserver.common.ApiResponse;
import com.yang.lblogserver.diagram.config.DiagramProperties;
import com.yang.lblogserver.diagram.config.DrawRateLimiter;
import com.yang.lblogserver.diagram.model.DrawChatRequest;
import com.yang.lblogserver.diagram.model.DrawConfigVO;
import com.yang.lblogserver.diagram.service.DiagramService;
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

    public DiagramController(DiagramService diagramService, DiagramProperties diagramProperties,
                             DrawRateLimiter drawRateLimiter) {
        this.diagramService = diagramService;
        this.diagramProperties = diagramProperties;
        this.drawRateLimiter = drawRateLimiter;
    }

    @Operation(summary = "AI 绘图对话", description = "SSE 流式接口，用于 AI 绘图对话")
    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chat(@RequestBody DrawChatRequest request, HttpServletRequest httpRequest) {
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
                diagramProperties.isEnabled(),
                diagramProperties.getModel(),
                diagramProperties.getMaxTokens()
        );
        return ApiResponse.success(config);
    }
}
