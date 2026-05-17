package com.yang.lblogserver.ai.agent.draw;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.deepseek.DeepSeekAssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import org.springframework.web.reactive.function.client.WebClientResponseException;
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
            String userContent = getLatestUserMessage(request);
            if (userContent == null) {
                emitter.complete();
                return;
            }

            String systemPrompt = promptManager.buildSystemPrompt(
                    null, request.getMinimalStyle() != null && request.getMinimalStyle());
            String xmlContext = promptManager.buildXmlContext(request.getXml(), request.getPreviousXml());

            ChatClient.CallResponseSpec callSpec = chatClient.prompt()
                    .system(systemPrompt + "\n\n" + xmlContext)
                    .messages(new UserMessage(userContent))
                    .advisors(spec -> {
                        if (request.getSessionId() != null) {
                            spec.param("sessionId", request.getSessionId());
                            spec.param("modelName", "");
                        }
                    })
                    .tools(displayDiagramTool)
                    .toolContext(Map.of("emitter", emitter))
                    .call();

            if (callSpec != null && callSpec.chatResponse() != null
                    && callSpec.chatResponse().getResult() != null) {
                AssistantMessage output = callSpec.chatResponse().getResult().getOutput();

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
                asyncThread.interrupt();
                throw new RuntimeException(e);
            }
        }, diagramProperties.getDisconnectCheckIntervalSeconds(), diagramProperties.getDisconnectCheckIntervalSeconds(), TimeUnit.SECONDS);

        emitter.onCompletion(() -> heartbeat.cancel(false));

        try {
            String userContent = getLatestUserMessage(request);
            if (userContent == null) {
                emitter.complete();
                return;
            }

            String systemPrompt = promptManager.buildSystemPrompt(
                    null, request.getMinimalStyle() != null && request.getMinimalStyle());
            String xmlContext = promptManager.buildXmlContext(request.getXml(), request.getPreviousXml());

            try {
                chatClient.prompt()
                        .system(systemPrompt + "\n\n" + xmlContext)
                        .messages(new UserMessage(userContent))
                        .advisors(spec -> {
                            if (request.getSessionId() != null) {
                                spec.param("sessionId", request.getSessionId());
                                spec.param("modelName", "");
                            }
                        })
                        .tools(displayDiagramTool)
                        .toolContext(Map.of("emitter", emitter))
                        .stream()
                        .chatResponse()
                        .toStream()
                        .forEach(response -> {
                            if (response != null && response.getResult() != null) {
                                AssistantMessage output = response.getResult().getOutput();

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

    private String getLatestUserMessage(DrawChatRequest request) {
        if (request.getMessages() != null) {
            for (int i = request.getMessages().size() - 1; i >= 0; i--) {
                DrawChatRequest.ChatMessage msg = request.getMessages().get(i);
                if ("user".equals(msg.getRole()) && msg.getContent() != null && !msg.getContent().isBlank()) {
                    return msg.getContent();
                }
            }
        }
        return null;
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
