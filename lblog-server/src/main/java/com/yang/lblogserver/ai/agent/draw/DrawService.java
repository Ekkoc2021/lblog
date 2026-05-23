package com.yang.lblogserver.ai.agent.draw;

import com.yang.lblogserver.ai.agent.transport.AgentStreamTransport;
import com.yang.lblogserver.ai.skill.LoadSkillTool;
import com.yang.lblogserver.ai.skill.SkillSystemPromptBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.deepseek.DeepSeekAssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.Map;

@Service
public class DrawService {

    private static final Logger log = LoggerFactory.getLogger(DrawService.class);

    private final ChatClient chatClient;
    private final DrawPromptManager promptManager;
    private final DisplayDrawTool displayDiagramTool;
    private final LoadSkillTool loadSkillTool;
    private final SkillSystemPromptBuilder skillPromptBuilder;

    public DrawService(@Qualifier("drawChatClient") ChatClient chatClient,
                       DrawPromptManager promptManager,
                       DisplayDrawTool displayDiagramTool,
                       LoadSkillTool loadSkillTool,
                       SkillSystemPromptBuilder skillPromptBuilder) {
        this.chatClient = chatClient;
        this.promptManager = promptManager;
        this.displayDiagramTool = displayDiagramTool;
        this.loadSkillTool = loadSkillTool;
        this.skillPromptBuilder = skillPromptBuilder;
    }

    @Async("diagramTaskExecutor")
    public void chatNonStream(DrawChatRequest request, AgentStreamTransport transport) {
        transport.start();
        try {
            String userContent = getLatestUserMessage(request);
            if (userContent == null) {
                transport.complete();
                return;
            }

            String systemPrompt = buildFullSystemPrompt(request);
            String xmlContext = promptManager.buildXmlContext(request.getXml(), request.getPreviousXml());

            ChatClient.CallResponseSpec callSpec = chatClient.prompt()
                    .system(systemPrompt + "\n\n" + xmlContext)
                    .messages(new UserMessage(userContent))
                    .advisors(spec -> {
                        if (request.getSessionId() != null) {
                            spec.param("sessionId", request.getSessionId());
                            spec.param("agentType", "draw");
                            spec.param("modelName", "");
                        }
                    })
                    .tools(displayDiagramTool, loadSkillTool)
                    .toolContext(Map.of("transport", transport,
                            "sessionId", request.getSessionId() != null ? request.getSessionId() : "",
                            "agentType", "draw"))
                    .call();

            if (callSpec != null && callSpec.chatResponse() != null
                    && callSpec.chatResponse().getResult() != null) {
                AssistantMessage output = callSpec.chatResponse().getResult().getOutput();

                if (output instanceof DeepSeekAssistantMessage dsam) {
                    String rc = dsam.getReasoningContent();
                    if (rc != null && !rc.isEmpty()) {
                        transport.send("reasoning", Map.of("delta", rc));
                    }
                }

                String text = output.getText();
                if (text != null && !text.isEmpty()) {
                    transport.send("text-delta", Map.of("delta", text));
                }
            }

            transport.send("done", Map.of("sessionId", request.getSessionId() != null ? request.getSessionId() : ""));
            transport.complete();

        } catch (Exception e) {
            if (e instanceof WebClientResponseException wcre) {
                log.error("API error: status={}, body={}", wcre.getStatusCode(), wcre.getResponseBodyAsString());
            }
            log.error("Error in non-stream chat", e);
            transport.completeWithError(e);
        }
    }

    @Async("diagramTaskExecutor")
    public void chatStream(DrawChatRequest request, AgentStreamTransport transport) {
        transport.start();
        try {
            String userContent = getLatestUserMessage(request);
            if (userContent == null) {
                transport.complete();
                return;
            }

            String systemPrompt = buildFullSystemPrompt(request);
            String xmlContext = promptManager.buildXmlContext(request.getXml(), request.getPreviousXml());

            try {
                chatClient.prompt()
                        .system(systemPrompt + "\n\n" + xmlContext)
                        .messages(new UserMessage(userContent))
                        .advisors(spec -> {
                            if (request.getSessionId() != null) {
                                spec.param("sessionId", request.getSessionId());
                                spec.param("agentType", "draw");
                                spec.param("modelName", "");
                            }
                        })
                        .tools(displayDiagramTool, loadSkillTool)
                        .toolContext(Map.of("transport", transport,
                                "sessionId", request.getSessionId() != null ? request.getSessionId() : ""))
                        .stream()
                        .chatResponse()
                        .toStream()
                        .forEach(response -> {
                            if (response != null && response.getResult() != null) {
                                AssistantMessage output = response.getResult().getOutput();

                                if (output instanceof DeepSeekAssistantMessage dsam) {
                                    String rc = dsam.getReasoningContent();
                                    if (rc != null && !rc.isEmpty()) {
                                        transport.send("reasoning", Map.of("delta", rc));
                                    }
                                }

                                String text = output.getText();
                                if (text != null && !text.isEmpty()) {
                                    transport.send("text-delta", Map.of("delta", text));
                                }
                            }
                        });
            } catch (Exception e) {
                if (e instanceof WebClientResponseException wcre) {
                    log.error("API error: status={}, body={}", wcre.getStatusCode(), wcre.getResponseBodyAsString());
                }
                log.error("Error in chat stream", e);
                transport.completeWithError(e);
                return;
            }

            transport.send("done", Map.of("sessionId", request.getSessionId() != null ? request.getSessionId() : ""));
            transport.complete();

        } catch (Exception e) {
            if (e instanceof WebClientResponseException wcre) {
                log.error("API error: status={}, body={}", wcre.getStatusCode(), wcre.getResponseBodyAsString());
            }
            log.error("Error in chat stream", e);
            transport.completeWithError(e);
        }
    }

    private String buildFullSystemPrompt(DrawChatRequest request) {
        String prompt = promptManager.buildSystemPrompt(
                null, request.getMinimalStyle() != null && request.getMinimalStyle());
        String skillHint = skillPromptBuilder.buildLazyHint("draw");
        if (!skillHint.isEmpty()) {
            prompt += "\n" + skillHint;
        }
        return prompt;
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
}
