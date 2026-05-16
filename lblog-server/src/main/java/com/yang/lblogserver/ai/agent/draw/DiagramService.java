package com.yang.lblogserver.ai.agent.draw;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.deepseek.DeepSeekChatModel;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@Service
public class DiagramService {

    private static final Logger log = LoggerFactory.getLogger(DiagramService.class);

    private final ChatClient chatClient;
    private final PromptManager promptManager;
    private final DiagramProperties diagramProperties;
    private final DisplayDiagramTool displayDiagramTool;
    private final ScheduledExecutorService heartbeatScheduler;
    private final ObjectMapper objectMapper;

    public DiagramService(DeepSeekChatModel deepSeekChatModel,
                          PromptManager promptManager,
                          DiagramProperties diagramProperties,
                          DisplayDiagramTool displayDiagramTool,
                          ScheduledExecutorService heartbeatScheduler,
                          ObjectMapper objectMapper) {
        this.chatClient = ChatClient.builder(deepSeekChatModel).build();
        this.promptManager = promptManager;
        this.diagramProperties = diagramProperties;
        this.displayDiagramTool = displayDiagramTool;
        this.heartbeatScheduler = heartbeatScheduler;
        this.objectMapper = objectMapper;
    }

    @Async("diagramTaskExecutor")
    public void chatStream(DrawChatRequest request, SseEmitter emitter) {
        Thread asyncThread = Thread.currentThread();

        ScheduledFuture<?> heartbeat = heartbeatScheduler.scheduleAtFixedRate(() -> {
            try {
                emitter.send(objectMapper.writeValueAsString(Map.of("type", "heartbeat")));
            } catch (Exception e) {
                // 心跳检测失败,直接中断ai主线程,后续响应没有任何意义
                // emitter发送失败自动关闭
                asyncThread.interrupt();
                throw new RuntimeException(e);
            }
        }, 15, 15, TimeUnit.SECONDS);

        emitter.onCompletion(() -> heartbeat.cancel(false));

        try {
            List<Message> messages = buildMessages(request);
            if (messages.isEmpty()) {
                emitter.complete();
                return;
            }

            try {
                ChatResponse response = chatClient.prompt()
                        .messages(messages)
                        .tools(displayDiagramTool)
                        .toolContext(Map.of("emitter", emitter))
                        .call()
                        .chatResponse();

                if (response != null && response.getResult() != null) {
                    String text = response.getResult().getOutput().getText();
                    if (text != null && !text.isEmpty()) {
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
                heartbeat.cancel(false);
            }

            sendDone(emitter, request.getSessionId());
            emitter.complete();

        } catch (Exception e) {
            log.error("Error in chat stream", e);
            emitter.completeWithError(e);
        } finally {
            heartbeat.cancel(false);
        }
    }

    private List<Message> buildMessages(DrawChatRequest request) {
        List<Message> messages = new ArrayList<>();

        String systemPrompt = promptManager.buildSystemPrompt(
                diagramProperties.getModel(), request.getMinimalStyle() != null && request.getMinimalStyle());
        String xmlContext = promptManager.buildXmlContext(request.getXml(), request.getPreviousXml());
        messages.add(new SystemMessage(systemPrompt + "\n\n" + xmlContext));

        if (request.getCustomSystemMessage() != null && !request.getCustomSystemMessage().isBlank()) {
            messages.add(new SystemMessage("## Custom Instructions\n" + request.getCustomSystemMessage()));
        }

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

}
