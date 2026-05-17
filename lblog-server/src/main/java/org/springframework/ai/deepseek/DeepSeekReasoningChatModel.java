package org.springframework.ai.deepseek;

import io.micrometer.observation.ObservationRegistry;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.deepseek.api.DeepSeekApi;
import org.springframework.ai.deepseek.api.DeepSeekApi.ChatCompletionMessage;
import org.springframework.ai.deepseek.api.DeepSeekApi.ChatCompletionMessage.ChatCompletionFunction;
import org.springframework.ai.deepseek.api.DeepSeekApi.ChatCompletionMessage.ToolCall;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionEligibilityPredicate;
import org.springframework.retry.support.RetryTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * 修复 createRequest() 中 reasoningContent 丢失的问题。
 *
 * 参考 Spring AI Issue #6016:
 * https://github.com/spring-projects/spring-ai/issues/6016
 */
public class DeepSeekReasoningChatModel extends DeepSeekChatModel {

    public DeepSeekReasoningChatModel(
            DeepSeekApi deepSeekApi, DeepSeekChatOptions options,
            ToolCallingManager toolCallingManager, RetryTemplate retryTemplate,
            ObservationRegistry observationRegistry,
            ToolExecutionEligibilityPredicate toolExecutionEligibilityPredicate) {
        super(deepSeekApi, options, toolCallingManager, retryTemplate,
                observationRegistry, toolExecutionEligibilityPredicate);
    }

    @Override
    DeepSeekApi.ChatCompletionRequest createRequest(Prompt prompt, boolean stream) {
        DeepSeekApi.ChatCompletionRequest request = super.createRequest(prompt, stream);

        List<ChatCompletionMessage> fixedMessages = new ArrayList<>();
        for (Message msg : prompt.getInstructions()) {
            fixedMessages.addAll(toChatCompletionMessage(msg));
        }

        return new DeepSeekApi.ChatCompletionRequest(
                fixedMessages,
                request.model(), request.frequencyPenalty(), request.maxTokens(),
                request.presencePenalty(), request.responseFormat(), request.stop(),
                request.stream(), request.temperature(), request.topP(),
                request.logprobs(), request.topLogprobs(), request.tools(),
                request.toolChoice());
    }

    private List<ChatCompletionMessage> toChatCompletionMessage(Message message) {
        if (message.getMessageType() == MessageType.USER || message.getMessageType() == MessageType.SYSTEM) {
            return List.of(new ChatCompletionMessage(message.getText(),
                    ChatCompletionMessage.Role.valueOf(message.getMessageType().name())));
        }
        else if (message.getMessageType() == MessageType.ASSISTANT) {
            var am = (AssistantMessage) message;

            String reasoningContent = null;
            if (message instanceof DeepSeekAssistantMessage dsam) {
                reasoningContent = dsam.getReasoningContent();
            }

            var toolCalls = am.getToolCalls() != null && !am.getToolCalls().isEmpty()
                    ? am.getToolCalls().stream()
                        .map(tc -> new ToolCall(tc.id(), tc.type(),
                                new ChatCompletionFunction(tc.name(), tc.arguments())))
                        .toList()
                    : null;

            Boolean isPrefix = (message instanceof DeepSeekAssistantMessage dsam2)
                    ? dsam2.getPrefix() : null;

            return List.of(new ChatCompletionMessage(
                    am.getText(), ChatCompletionMessage.Role.ASSISTANT,
                    null, null, toolCalls, isPrefix, reasoningContent));
        }
        else if (message.getMessageType() == MessageType.TOOL) {
            var tm = (ToolResponseMessage) message;
            return tm.getResponses().stream()
                    .map(r -> new ChatCompletionMessage(
                            r.responseData(), ChatCompletionMessage.Role.TOOL,
                            r.name(), r.id(), null))
                    .toList();
        }
        else {
            throw new IllegalArgumentException("Unsupported: " + message.getMessageType());
        }
    }
}
