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

    private final DiagramService diagramService;     // 核心编排服务
    private final DiagramProperties diagramProperties; // 配置项
    private final DrawRateLimiter drawRateLimiter;   // IP 限流器（Caffeine）

    public DiagramController(DiagramService diagramService, DiagramProperties diagramProperties,
                             DrawRateLimiter drawRateLimiter) {
        this.diagramService = diagramService;
        this.diagramProperties = diagramProperties;
        this.drawRateLimiter = drawRateLimiter;
    }

    /**
     * AI 绘图对话（SSE 流式接口）。
     *
     * 请求体示例：
     *   {"messages":[{"role":"user","content":"画一个微服务架构图"}],
     *    "xml":"","previousXml":"","sessionId":"uuid"}
     *
     * 响应（SSE 事件流，Content-Type: text/event-stream）：
     *   1. text-delta: AI 文字回复（逐段推送）
     *   2. tool-call:  AI 调用 display_diagram 工具（含完整 XML）
     *   3. done:       完成信号
     *   4. heartbeat:  每 15s 保活
     */
    @Operation(summary = "AI 绘图对话", description = "SSE 流式接口，用于 AI 绘图对话")
    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chat(@RequestBody DrawChatRequest request, HttpServletRequest httpRequest) {
        // === 1. 请求体验证 ===
        if (request.getMessages() == null || request.getMessages().isEmpty()) {
            throw new IllegalArgumentException("messages cannot be empty");
        }

        // === 2. IP 限流（每分钟每 IP 最多 10 次） ===
        String ip = httpRequest.getRemoteAddr();
        if (!drawRateLimiter.tryAcquire(ip)) {
            throw new IllegalArgumentException("Too many requests. Please try again later.");
        }

        // === 3. 创建 SSE 连接（3 分钟超时） ===
        SseEmitter emitter = new SseEmitter(TimeUnit.MINUTES.toMillis(3));

        // 超时时自动完成（不抛异常）
        emitter.onTimeout(emitter::complete);

        // === 4. 异步执行 AI 调用 ===
        // chatStream 是 @Async 方法，立即返回
        // AI 调用和 SSE 推送在独立线程池中执行
        diagramService.chatStream(request, emitter);

        return emitter;
    }

    /**
     * 获取 AI 绘图配置。
     * 前端通过此接口判断 AI 绘图功能是否可用及显示模型名称。
     */
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
