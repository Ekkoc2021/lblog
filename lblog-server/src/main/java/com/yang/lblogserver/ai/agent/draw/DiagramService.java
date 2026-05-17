package com.yang.lblogserver.ai.agent.draw;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.deepseek.DeepSeekAssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import org.springframework.web.reactive.function.client.WebClientResponseException;
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

    public DiagramService(@Qualifier("drawChatClient") ChatClient chatClient,
                          PromptManager promptManager,
                          DiagramProperties diagramProperties,
                          DisplayDiagramTool displayDiagramTool,
                          ScheduledExecutorService heartbeatScheduler) {
        this.chatClient = chatClient;
        this.promptManager = promptManager;
        this.diagramProperties = diagramProperties;
        this.displayDiagramTool = displayDiagramTool;
        this.heartbeatScheduler = heartbeatScheduler;
    }

    @Async("diagramTaskExecutor")
    public void chatNonStream(DrawChatRequest request, SseEmitter emitter) {
        Thread asyncThread = Thread.currentThread();

        ScheduledFuture<?> heartbeat = heartbeatScheduler.scheduleAtFixedRate(() -> {
            try {
                emitter.send(SseEmitter.event().data("{}"));
            } catch (Exception e) {
                asyncThread.interrupt();
                throw new RuntimeException(e);
            }
        }, diagramProperties.getDisconnectCheckIntervalSeconds(), diagramProperties.getDisconnectCheckIntervalSeconds(), TimeUnit.SECONDS);

        emitter.onCompletion(() -> heartbeat.cancel(false));

        try {
            List<Message> messages = buildMessages(request);
            if (messages.isEmpty()) {
                emitter.complete();
                return;
            }

            ChatResponse response = chatClient.prompt()
                    .messages(messages)
                    .tools(displayDiagramTool)
                    .toolContext(Map.of("emitter", emitter))
                    .call()
                    .chatResponse();

            if (response != null && response.getResult() != null) {
                AssistantMessage output = response.getResult().getOutput();

                // 发送思考内容
                if (output instanceof DeepSeekAssistantMessage dsam) {
                    String rc = dsam.getReasoningContent();
                    if (rc != null && !rc.isEmpty()) {
                        try {
                            emitter.send(SseEmitter.event()
                                    .data(Map.of("type", "reasoning", "delta", rc)));
                        } catch (Exception e) {
                            log.warn("Failed to send reasoning", e);
                        }
                    }
                }

                // 发送回复文本
                String text = output.getText();
                if (text != null && !text.isEmpty()) {
                    try {
                        emitter.send(SseEmitter.event()
                                .data(Map.of("type", "text-delta", "delta", text)));
                    } catch (Exception e) {
                        log.warn("Failed to send delta", e);
                    }
                }
            }

            sendDone(emitter, request.getSessionId());
            emitter.complete();

        } catch (Exception e) {
            if (e instanceof WebClientResponseException wcre) {
                log.error("API error: status={}, body={}", wcre.getStatusCode(), wcre.getResponseBodyAsString());
            }
            log.error("Error in non-stream chat", e);
            emitter.completeWithError(e);
        } finally {
            heartbeat.cancel(false);
        }
    }

    @Async("diagramTaskExecutor")
    public void chatStream(DrawChatRequest request, SseEmitter emitter) {
        Thread asyncThread = Thread.currentThread();

        ScheduledFuture<?> heartbeat = heartbeatScheduler.scheduleAtFixedRate(() -> {
            try {
                emitter.send(SseEmitter.event().data("{}"));
            } catch (Exception e) {
                // 心跳检测失败,直接中断ai主线程,后续响应没有任何意义
                // emitter发送失败自动关闭
                asyncThread.interrupt();
                throw new RuntimeException(e);
            }
        }, diagramProperties.getDisconnectCheckIntervalSeconds(), diagramProperties.getDisconnectCheckIntervalSeconds(), TimeUnit.SECONDS);

        emitter.onCompletion(() -> heartbeat.cancel(false));

        try {
            List<Message> messages = buildMessages(request);
            if (messages.isEmpty()) {
                emitter.complete();
                return;
            }

            try {
                chatClient.prompt()
                        .messages(messages)
                        .tools(displayDiagramTool)
                        .toolContext(Map.of("emitter", emitter))
                        .stream()
                        .chatResponse()
                        .toStream()
                        .forEach(response -> {
                            if (response != null && response.getResult() != null) {
                                AssistantMessage output = response.getResult().getOutput();

                                // 发送思考内容
                                if (output instanceof DeepSeekAssistantMessage dsam) {
                                    String rc = dsam.getReasoningContent();
                                    if (rc != null && !rc.isEmpty()) {
                                        try {
                                            emitter.send(SseEmitter.event()
                                                    .data(Map.of("type", "reasoning", "delta", rc)));
                                        } catch (Exception e) {
                                            log.warn("Failed to send reasoning", e);
                                        }
                                    }
                                }

                                // 发送回复文本
                                String text = output.getText();
                                if (text != null && !text.isEmpty()) {
                                    try {
                                        emitter.send(SseEmitter.event()
                                                .data(Map.of("type", "text-delta", "delta", text)));
                                    } catch (Exception e) {
                                        log.warn("Failed to send delta", e);
                                    }
                                }
                            }
                        });
            } catch (Exception e) {
                if (e instanceof WebClientResponseException wcre) {
                    log.error("API error: status={}, body={}", wcre.getStatusCode(), wcre.getResponseBodyAsString());
                }
                log.error("Error in chat stream", e);
                emitter.completeWithError(e);
                return;
            } finally {
                heartbeat.cancel(false);
            }

            sendDone(emitter, request.getSessionId());
            emitter.complete();

        } catch (Exception e) {
            if (e instanceof WebClientResponseException wcre) {
                log.error("API error: status={}, body={}", wcre.getStatusCode(), wcre.getResponseBodyAsString());
            }
            log.error("Error in chat stream", e);
            emitter.completeWithError(e);
        } finally {
            heartbeat.cancel(false);
        }
    }

    private List<Message> buildMessages(DrawChatRequest request) {
        List<Message> messages = new ArrayList<>();

        String systemPrompt = promptManager.buildSystemPrompt(
                null, request.getMinimalStyle() != null && request.getMinimalStyle());
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
            emitter.send(SseEmitter.event()
                    .data(Map.of("type", "done", "sessionId", sessionId != null ? sessionId : "")));
        } catch (Exception e) {
            log.warn("Failed to send done event", e);
        }
    }

}
