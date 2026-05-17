package com.yang.lblogserver.ai.memory.advisor;

import com.yang.lblogserver.ai.chat.domain.ChatMessage;
import com.yang.lblogserver.ai.memory.ChatMemoryStore;
import com.yang.lblogserver.ai.memory.converter.ContextPolicy;
import com.yang.lblogserver.ai.memory.converter.ModelMessageConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.client.advisor.api.BaseAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
public class ChatHistoryAdvisor implements BaseAdvisor {

    private static final Logger log = LoggerFactory.getLogger(ChatHistoryAdvisor.class);

    private final ChatMemoryStore chatMemoryStore;
    private final List<ModelMessageConverter> converters;
    private final ContextPolicy defaultPolicy;
    private final int order;

    public ChatHistoryAdvisor(ChatMemoryStore chatMemoryStore,
                              List<ModelMessageConverter> converters) {
        this.chatMemoryStore = chatMemoryStore;
        this.converters = converters;
        this.defaultPolicy = new ContextPolicy();
        this.order = BaseAdvisor.HIGHEST_PRECEDENCE;
    }

    @Override
    public ChatClientRequest before(ChatClientRequest request, AdvisorChain chain) {
        Map<String, Object> ctx = request.context();
        String sessionId = ctx != null ? (String) ctx.get("sessionId") : null;
        String modelName = ctx != null ? (String) ctx.get("modelName") : null;

        if (sessionId == null || sessionId.isBlank()) {
            return request;
        }

        try {
            Long sid = Long.parseLong(sessionId);
            List<ChatMessage> history = chatMemoryStore.loadHistory(sid);
            saveCurrentUserMessage(request, sid);

            ModelMessageConverter converter = findConverter(modelName);
            List<Message> contextMessages = buildContextMessages(history, converter);

            List<Message> instructions = request.prompt().getInstructions();
            List<Message> systemMessages = new ArrayList<>();
            List<Message> nonSystemMessages = new ArrayList<>();
            for (Message msg : instructions) {
                if (msg instanceof SystemMessage) {
                    systemMessages.add(msg);
                } else {
                    nonSystemMessages.add(msg);
                }
            }

            List<Message> allMessages = new ArrayList<>();
            allMessages.addAll(systemMessages);
            allMessages.addAll(contextMessages);
            allMessages.addAll(nonSystemMessages);

            Prompt newPrompt = new Prompt(allMessages, request.prompt().getOptions());
            return request.mutate().prompt(newPrompt).build();
        } catch (Exception e) {
            log.error("ChatHistoryAdvisor.before() error", e);
            return request;
        }
    }

    @Override
    public ChatClientResponse after(ChatClientResponse response, AdvisorChain chain) {
        Map<String, Object> ctx = response.context();
        String sessionId = ctx != null ? (String) ctx.get("sessionId") : null;
        if (sessionId == null) {
            return response;
        }

        try {
            Long sid = Long.parseLong(sessionId);
            String modelName = ctx != null ? (String) ctx.get("modelName") : null;
            ModelMessageConverter converter = findConverter(modelName);

            if (response.chatResponse() != null && response.chatResponse().getResult() != null) {
                Message output = response.chatResponse().getResult().getOutput();
                int msgIndex = chatMemoryStore.getMaxMsgIndex(sid) + 1;
                ChatMessage stored = converter.toStorageMessage(output, sid, msgIndex);
                chatMemoryStore.saveMessages(sid, List.of(stored));
                chatMemoryStore.touchSession(sid);
            }

            return response;
        } catch (Exception e) {
            log.error("ChatHistoryAdvisor.after() error", e);
            return response;
        }
    }

    @Override
    public Flux<ChatClientResponse> adviseStream(ChatClientRequest request, StreamAdvisorChain chain) {
        Map<String, Object> ctx = request.context();
        String sessionId = ctx != null ? (String) ctx.get("sessionId") : null;
        String modelName = ctx != null ? (String) ctx.get("modelName") : null;

        if (sessionId == null || sessionId.isBlank()) {
            return chain.nextStream(request);
        }

        try {
            Long sid = Long.parseLong(sessionId);
            List<ChatMessage> history = chatMemoryStore.loadHistory(sid);
            saveCurrentUserMessage(request, sid);

            ModelMessageConverter converter = findConverter(modelName);
            List<Message> contextMessages = buildContextMessages(history, converter);

            List<Message> instructions = request.prompt().getInstructions();
            List<Message> systemMessages = new ArrayList<>();
            List<Message> nonSystemMessages = new ArrayList<>();
            for (Message msg : instructions) {
                if (msg instanceof SystemMessage) {
                    systemMessages.add(msg);
                } else {
                    nonSystemMessages.add(msg);
                }
            }

            List<Message> allMessages = new ArrayList<>();
            allMessages.addAll(systemMessages);
            allMessages.addAll(contextMessages);
            allMessages.addAll(nonSystemMessages);

            Prompt newPrompt = new Prompt(allMessages, request.prompt().getOptions());
            ChatClientRequest modifiedRequest = request.mutate().prompt(newPrompt).build();

            // Capture values in local finals for use in Reactor callbacks
            final Long capturedSid = sid;
            final String capturedModelName = modelName;

            List<ChatClientResponse> chunks = new CopyOnWriteArrayList<>();
            Flux<ChatClientResponse> stream = chain.nextStream(modifiedRequest);

            return stream
                    .doOnNext(chunks::add)
                    .doOnComplete(() -> {
                        try {
                            if (!chunks.isEmpty()) {
                                ModelMessageConverter conv = findConverter(capturedModelName);
                                int msgIndex = chatMemoryStore.getMaxMsgIndex(capturedSid) + 1;
                                ChatMessage stored = conv.toStorageMessageFromChunks(chunks, capturedSid, msgIndex);
                                chatMemoryStore.saveMessages(capturedSid, List.of(stored));
                                chatMemoryStore.touchSession(capturedSid);
                            }
                        } catch (Exception e) {
                            log.error("ChatHistoryAdvisor.adviseStream doOnComplete error", e);
                        }
                    })
                    .doOnError(e -> log.error("ChatHistoryAdvisor.adviseStream error", e));

        } catch (Exception e) {
            log.error("ChatHistoryAdvisor.adviseStream() error", e);
            return chain.nextStream(request);
        }
    }

    @Override
    public int getOrder() {
        return this.order;
    }

    private void saveCurrentUserMessage(ChatClientRequest request, Long sessionId) {
        List<Message> instructions = request.prompt().getInstructions();
        for (int i = instructions.size() - 1; i >= 0; i--) {
            Message msg = instructions.get(i);
            if (msg instanceof UserMessage userMsg) {
                String text = userMsg.getText();
                if (text != null && !text.isBlank()) {
                    int maxIdx = chatMemoryStore.getMaxMsgIndex(sessionId);
                    ChatMessage chatMsg = new ChatMessage();
                    chatMsg.setSessionId(sessionId);
                    chatMsg.setRole("user");
                    chatMsg.setContent(text);
                    chatMsg.setMsgIndex(maxIdx + 1);
                    chatMemoryStore.saveMessages(sessionId, List.of(chatMsg));
                }
                break;
            }
        }
    }

    private ModelMessageConverter findConverter(String modelName) {
        if (modelName == null) {
            return converters.get(0);
        }
        return converters.stream()
                .filter(c -> c.supports(modelName))
                .findFirst()
                .orElse(converters.get(0));
    }

    private List<Message> buildContextMessages(List<ChatMessage> history, ModelMessageConverter converter) {
        List<Message> messages = new ArrayList<>();
        for (ChatMessage msg : history) {
            Message converted = converter.toContextMessage(msg, defaultPolicy);
            if (converted != null) {
                messages.add(converted);
                continue;
            }
            switch (msg.getRole()) {
                case "user" -> messages.add(new UserMessage(msg.getContent()));
                case "tool" -> {
                    // Tool messages are handled internally by the tool call advisor
                }
            }
        }
        return messages;
    }
}
