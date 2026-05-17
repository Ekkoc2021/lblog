package com.yang.lblogserver.ai.memory.converter;

import com.yang.lblogserver.ai.chat.domain.ChatMessage;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.messages.Message;

import java.util.List;

public interface ModelMessageConverter {

    boolean supports(String modelName);

    Message toContextMessage(ChatMessage stored, ContextPolicy policy);

    ChatMessage toStorageMessage(Message output, Long sessionId, int msgIndex);

    ChatMessage toStorageMessageFromChunks(
            List<ChatClientResponse> chunks, Long sessionId, int msgIndex);

    List<String> supportedModels();
}
