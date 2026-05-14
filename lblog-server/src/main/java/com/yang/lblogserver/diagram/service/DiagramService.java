package com.yang.lblogserver.diagram.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yang.lblogserver.diagram.config.DiagramProperties;
import com.yang.lblogserver.diagram.model.DrawChatRequest;
import com.yang.lblogserver.diagram.prompt.PromptManager;
import com.yang.lblogserver.diagram.tool.DisplayDiagramTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * AI 绘图核心编排服务。
 *
 * 数据流（前端 → DeepSeek → 前端）：
 *
 *  前端 POST /draw/chat (SSE)
 *    │
 *    ├─ DiagramController 创建 SseEmitter（3min 超时）
 *    ├─ DiagramService.chatStream() @Async 异步执行
 *    │
 *    ├─ 1. buildMessages() 拼装 prompt
 *    │     ├─ system: PromptManager.buildSystemPrompt() 6 层提示词
 *    │     ├─ system: 当前图表 XML 上下文
 *    │     ├─ user: 用户输入的绘图指令
 *    │     └─ assistant: 历史对话（旧 XML 已替换为占位符）
 *    │
 *    ├─ 2. chatClient.prompt().tools().call() 调用 DeepSeek
 *    │     ├─ Spring AI 将 @Tool 注解转为 OpenAI tool JSON schema
 *    │     ├─ schema 随请求发送给 DeepSeek（OpenAI 兼容接口）
 *    │     ├─ DeepSeek 决定调用 display_diagram
 *    │     └─ Spring AI 检测到 tool call → 自动执行 @Tool 方法
 *    │
 *    ├─ 3. DisplayDiagramTool.execute(xml) 执行
 *    │     ├─ ThreadLocal 获取当前请求的 SseEmitter
 *    │     ├─ 校验/修复/包装 XML（Jsoup）
 *    │     ├─ 通过 SSE 推送 tool-call 事件 → 前端渲染
 *    │     └─ 返回占位符给 AI → AI 继续生成文字回复
 *    │
 *    ├─ 4. AI 文字回复 → SSE text-delta 事件 → 前端展示
 *    │
 *    └─ 5. SSE done 事件 → 前端停止 loading
 *
 * 使用 .call() 而非 .stream() 的原因：
 * Spring AI 1.1.5 的 .stream() 在非 OpenAI 原生接口上
 * 不会传递 tool schema，导致 AI 无法调用 tool。
 * .call() 模式下 schema 传递正常，但文字失去流式效果。
 */
@Service
public class DiagramService {

    private static final Logger log = LoggerFactory.getLogger(DiagramService.class);

    private final ChatClient chatClient;              // Spring AI 聊天客户端（构建时从 Builder 创建）
    private final PromptManager promptManager;        // 6 层 system prompt 拼装器
    private final DiagramProperties diagramProperties; // lblog.diagram.* 配置项
    private final DisplayDiagramTool displayDiagramTool; // @Tool 定义（由 Spring AI 自动生成 schema）
    private final ScheduledExecutorService heartbeatScheduler; // 15s 心跳定时器
    private final ObjectMapper objectMapper;          // Jackson JSON 序列化（SSE payload 编码）

    public DiagramService(ChatClient.Builder chatClientBuilder,
                          PromptManager promptManager,
                          DiagramProperties diagramProperties,
                          DisplayDiagramTool displayDiagramTool,
                          ScheduledExecutorService heartbeatScheduler,
                          ObjectMapper objectMapper) {
        // ChatClient.Builder 由 Spring AI 自动配置（从 application.yml spring.ai.openai.* 读取）
        this.chatClient = chatClientBuilder.build();
        this.promptManager = promptManager;
        this.diagramProperties = diagramProperties;
        this.displayDiagramTool = displayDiagramTool;
        this.heartbeatScheduler = heartbeatScheduler;
        this.objectMapper = objectMapper;
    }

    /**
     * 入口：异步处理 AI 绘图请求。
     * 由 DiagramController 调用，每次请求创建一个 SseEmitter。
     *
     * @param emitter SseEmitter 由 Controller 创建传进来
     */
    @Async("diagramTaskExecutor")
    public void chatStream(DrawChatRequest request, SseEmitter emitter) {
        // === 1. 启动心跳 ===
        // Nginx 30s 无数据会断开连接，每 15s 发一个 heartbeat 保活
        ScheduledFuture<?> heartbeat = heartbeatScheduler.scheduleAtFixedRate(() -> {
            try {
                emitter.send(objectMapper.writeValueAsString(Map.of("type", "heartbeat")));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }, 15, 15, TimeUnit.SECONDS);

        try {
            // === 2. 拼装消息（system prompt + 对话历史） ===
            List<Message> messages = buildMessages(request);
            if (messages.isEmpty()) {
                emitter.complete();
                return;
            }

            // === 3. 绑定 SseEmitter 到 DisplayDiagramTool ===
            // ThreadLocal 方式：将当前请求的 emitter 存入 ThreadLocal，
            // 让 @Tool 方法在任意线程都能拿到 emitter 推送 SSE 事件
            DisplayDiagramTool.bindEmitter(emitter);

            try {
                // === 4. 调用 DeepSeek API ===
                // .call() 而非 .stream()：Spring AI 1.1.5 的流式模式
                // 不会传递 tool schema 给 DeepSeek。.call() 模式正常。
                // tools(displayDiagramTool) 注册 @Tool 方法，
                // Spring AI 自动生成 JSON schema 随请求发送。
                ChatResponse response = chatClient.prompt()
                        .messages(messages)
                        .tools(displayDiagramTool)
                        .call()
                        .chatResponse();

                // === 5. 发送 AI 文字回复 ===
                // .call() 返回完整的 ChatResponse，AI 的回复全文在 getText() 中
                if (response != null && response.getResult() != null) {
                    String text = response.getResult().getOutput().getText();
                    if (text != null && !text.isEmpty()) {
                        // 一次性推送 text-delta（非流式，没有打字机效果）
                        String payload = objectMapper.writeValueAsString(Map.of(
                                "type", "text-delta",
                                "delta", text
                        ));
                        emitter.send(payload);
                    }
                }
            } catch (Exception e) {
                log.error("Error in chat stream", e);
                emitter.completeWithError(e);
                return;
            } finally {
                // 无论成功还是异常，都要取消心跳 + 解绑 ThreadLocal
                heartbeat.cancel(false);
                DisplayDiagramTool.unbindEmitter();
            }

            // === 6. 发送完成事件 ===
            sendDone(emitter, request.getSessionId());
            emitter.complete();

        } catch (Exception e) {
            log.error("Error in chat stream", e);
            emitter.completeWithError(e);
        }
    }

    /**
     * 拼装发送给 AI 的消息列表。
     *
     * 消息结构：
     *   [0] system: 角色设定 + 行为约束 + 布局规则（6 层提示词）
     *   [1] system: 当前图表 XML 上下文（运行时注入）
     *   [2] user: 用户输入的绘图指令
     *   [3..N] 历史对话消息（旧 XML 已被前端替换为占位符）
     *
     * 多轮对话时，前端每次请求都会携带完整的历史消息列表，
     * 但旧的 tool-call XML 已被替换为 "[XML content replaced]"
     * 以节省 token。
     */
    private List<Message> buildMessages(DrawChatRequest request) {
        List<Message> messages = new ArrayList<>();

        // system prompt + 运行时 XML 上下文，合并为一条 system message
        String systemPrompt = promptManager.buildSystemPrompt(
                diagramProperties.getModel(), request.getMinimalStyle() != null && request.getMinimalStyle());
        String xmlContext = promptManager.buildXmlContext(request.getXml(), request.getPreviousXml());
        messages.add(new SystemMessage(systemPrompt + "\n\n" + xmlContext));

        // 用户自定义指令（追加在 XML 上下文之后）
        if (request.getCustomSystemMessage() != null && !request.getCustomSystemMessage().isBlank()) {
            messages.add(new SystemMessage("## Custom Instructions\n" + request.getCustomSystemMessage()));
        }

        // 对话历史（按角色映射为 Spring AI 的 Message 类型）
        if (request.getMessages() != null) {
            for (DrawChatRequest.ChatMessage msg : request.getMessages()) {
                if (msg.getContent() == null || msg.getContent().isBlank()) continue;
                switch (msg.getRole()) {
                    case "system" -> messages.add(new SystemMessage(msg.getContent()));
                    case "assistant" -> messages.add(new AssistantMessage(msg.getContent()));
                    default -> messages.add(new UserMessage(msg.getContent()));
                }
            }
        }

        return messages;
    }

    /**
     * 处理流式响应（当前未使用）。
     * 保留以支持未来 .stream() 模式切换。
     */
    private void handleChatResponse(ChatResponse response, SseEmitter emitter) {
        try {
            var result = response.getResult();
            if (result == null || result.getOutput() == null) return;

            var message = result.getOutput();
            String text = message.getText();
            if (text != null && !text.isEmpty()) {
                // text-delta: AI 文字回复的一段片段
                String payload = objectMapper.writeValueAsString(Map.of(
                        "type", "text-delta",
                        "delta", text
                ));
                emitter.send(payload);
            }
        } catch (IOException e) {
            // 前端断开连接时 SseEmitter 会抛 IOException
            log.warn("SSE emitter disconnected", e);
            emitter.complete();
        } catch (Exception e) {
            log.error("Failed to serialize SSE payload", e);
        }
    }

    /**
     * 发送 SSE 完成事件（type=done），通知前端结束 loading。
     */
    private void sendDone(SseEmitter emitter, String sessionId) {
        try {
            String payload = objectMapper.writeValueAsString(Map.of(
                    "type", "done",
                    "sessionId", sessionId != null ? sessionId : ""
            ));
            emitter.send(payload);
        } catch (Exception e) {
            log.warn("Failed to send done event", e);
        }
    }

    /**
     * 处理流式模式下的异常（当前未使用）。
     */
    private void handleError(Throwable error, SseEmitter emitter) {
        log.error("Stream error", error);
        emitter.completeWithError(error);
    }
}
