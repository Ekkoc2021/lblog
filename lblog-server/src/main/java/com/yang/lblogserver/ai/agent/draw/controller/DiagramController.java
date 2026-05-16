package com.yang.lblogserver.ai.agent.draw.controller;

import com.yang.lblogserver.ai.agent.draw.config.DrawRateLimiter;
import com.yang.lblogserver.ai.agent.draw.DiagramService;
import com.yang.lblogserver.ai.agent.draw.DrawChatRequest;
import com.yang.lblogserver.ai.agent.draw.DrawConfigVO;
import com.yang.lblogserver.ai.agent.draw.DiagramProperties;
import com.yang.lblogserver.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.concurrent.TimeUnit;

/**
 * AI 绘图 API 入口。
 *
 * SSE 端点说明：
 *   GET  /draw/config   → 返回配置信息（模型名、是否启用等）
 *   POST /draw/chat     → SSE 流式对话，请求体为 DrawChatRequest
 *
 * SSE 响应格式（每行一个事件）：
 *   data: {"type":"text-delta","delta":"..."}
 *   data: {"type":"tool-call","name":"display_diagram","arguments":{"xml":"..."}}
 *   data: {"type":"done","sessionId":"xxx"}
 *   data: {"type":"heartbeat"}
 *   data: {"type":"error","content":"..."}
 *
 * 注意：SseEmitter.send(String) 输出格式为 "data:{json}\n\n"，
 * 冒号后没有空格。前端解析时需要用 startsWith("data:") 而非 "data: "。
 */
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
