package com.yang.lblogserver.ai.memory.compression;

import com.yang.lblogserver.ai.conversation.domain.ChatMessage;
import com.yang.lblogserver.ai.memory.ChatMemoryStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;

import java.util.ArrayList;
import java.util.List;

/**
 * 滑动窗口：消息数超过 minTrigger 时，保留最近 N 条。
 * <p>
 * 不感知 system 消息位置，不做首尾保护（由 CompressionAdvisor 负责）。
 */
public class SlidingWindowStrategy implements CompressionStrategy {

    private static final Logger log = LoggerFactory.getLogger(SlidingWindowStrategy.class);

    private final int maxMessages;
    private final int minTrigger;

    public SlidingWindowStrategy(int maxMessages, int minTrigger) {
        this.maxMessages = maxMessages;
        this.minTrigger = minTrigger;
    }

    @Override
    public List<ChatMessage> load(Long sessionId, ChatMemoryStore store) {
        List<ChatMessage> history = store.loadHistory(sessionId);
        if (history.size() <= maxMessages) return new ArrayList<>(history);
        return new ArrayList<>(history.subList(history.size() - maxMessages, history.size()));
    }

    @Override
    public boolean shouldCompress(List<Message> messages) {
        return messages != null && messages.size() > minTrigger;
    }

    @Override
    public List<Message> compress(List<Message> messages) {
        if (messages == null || messages.size() <= maxMessages) return messages;
        int start = messages.size() - maxMessages;
        // 如果保留区第一条是 tool 消息，说明对应的 assistant(tool_calls) 被切掉了，
        // 需要往回找到 assistant 消息一起保留，避免 tool 消息变成孤儿
        while (start > 0 && messages.get(start).getMessageType() == MessageType.TOOL) {
            start--;
        }
        List<Message> result = new ArrayList<>(messages.subList(start, messages.size()));
        log.info("SlidingWindow: {} → {} messages", messages.size(), result.size());
        return result;
    }

    @Override
    public List<Message> tryCompress(List<Message> messages) {
        if (messages == null || messages.isEmpty()) return messages;
        List<Message> result = new ArrayList<>(messages);
        // 删除最旧的消息，但如果删除的是带 tool_calls 的 assistant 消息，
        // 则必须同时删除紧随其后的 tool 响应消息，避免 tool 消息变成孤儿
        // （DeepSeek API 要求 tool 消息必须有前驱 assistant 消息包含 tool_calls）
        Message removed = result.removeFirst();
        if (removed instanceof AssistantMessage am && am.hasToolCalls()) {
            while (!result.isEmpty() && result.getFirst().getMessageType() == MessageType.TOOL) {
                result.removeFirst();
            }
        }
        return result;
    }
}
