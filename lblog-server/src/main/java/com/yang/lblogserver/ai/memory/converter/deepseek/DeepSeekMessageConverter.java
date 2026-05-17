package com.yang.lblogserver.ai.memory.converter.deepseek;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yang.lblogserver.ai.chat.domain.ChatMessage;
import com.yang.lblogserver.ai.memory.converter.ContextPolicy;
import com.yang.lblogserver.ai.memory.converter.ModelMessageConverter;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.AssistantMessage.ToolCall;
import org.springframework.ai.deepseek.DeepSeekAssistantMessage;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class DeepSeekMessageConverter implements ModelMessageConverter {

    private final ObjectMapper objectMapper;

    public DeepSeekMessageConverter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean supports(String modelName) {
        return modelName != null && modelName.startsWith("deepseek");
    }

    @Override
    public List<String> supportedModels() {
        return List.of("deepseek-chat", "deepseek-v4-flash", "deepseek-v4-pro");
    }

    @Override
    public Message toContextMessage(ChatMessage stored, ContextPolicy policy) {
        if (!"assistant".equals(stored.getRole())) {
            return null;
        }
        if (policy.isIncludeReasoning() && stored.getReasoningContent() != null) {
            return new DeepSeekAssistantMessage.Builder()
                    .content(stored.getContent())
                    .reasoningContent(stored.getReasoningContent())
                    .build();
        }
        return new AssistantMessage(stored.getContent());
    }

    @Override
    public ChatMessage toStorageMessage(Message output, Long sessionId, int msgIndex) {
        ChatMessage msg = new ChatMessage();
        msg.setSessionId(sessionId);
        msg.setMsgIndex(msgIndex);
        msg.setRole("assistant");
        msg.setContent(output.getText());

        if (output instanceof DeepSeekAssistantMessage dsam) {
            msg.setReasoningContent(dsam.getReasoningContent());
        }

        if (output instanceof AssistantMessage asstMsg && asstMsg.hasToolCalls()) {
            msg.setToolCalls(serializeToolCalls(asstMsg.getToolCalls()));
        }

        return msg;
    }

    @Override
    public ChatMessage toStorageMessageFromChunks(
            List<ChatClientResponse> chunks, Long sessionId, int msgIndex) {
        StringBuilder content = new StringBuilder();
        StringBuilder reasoning = new StringBuilder();

        for (ChatClientResponse chunk : chunks) {
            if (chunk.chatResponse() != null && chunk.chatResponse().getResult() != null) {
                Message output = chunk.chatResponse().getResult().getOutput();
                if (output instanceof DeepSeekAssistantMessage dsam) {
                    String text = dsam.getText();
                    String rc = dsam.getReasoningContent();
                    if (text != null) content.append(text);
                    if (rc != null) reasoning.append(rc);
                } else if (output instanceof AssistantMessage am) {
                    String text = am.getText();
                    if (text != null) content.append(text);
                }
            }
        }

        ChatMessage msg = new ChatMessage();
        msg.setSessionId(sessionId);
        msg.setMsgIndex(msgIndex);
        msg.setRole("assistant");
        msg.setContent(content.toString());
        msg.setReasoningContent(reasoning.toString());
        return msg;
    }

    private String serializeToolCalls(List<ToolCall> toolCalls) {
        try {
            return objectMapper.writeValueAsString(toolCalls);
        } catch (JsonProcessingException e) {
            return "[]";
        }
    }
}
