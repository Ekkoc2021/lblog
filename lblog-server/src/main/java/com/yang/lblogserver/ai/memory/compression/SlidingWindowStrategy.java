package com.yang.lblogserver.ai.memory.compression;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.Message;

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
    public boolean shouldCompress(List<Message> messages) {
        return messages != null && messages.size() > minTrigger;
    }

    @Override
    public List<Message> compress(List<Message> messages) {
        if (messages == null || messages.size() <= maxMessages) return messages;
        List<Message> result = new ArrayList<>(messages.subList(messages.size() - maxMessages, messages.size()));
        log.info("SlidingWindow: {} → {} messages", messages.size(), result.size());
        return result;
    }

    @Override
    public List<Message> tryCompress(List<Message> messages) {
        if (messages == null || messages.isEmpty()) return messages;
        List<Message> result = new ArrayList<>(messages);
        result.removeFirst();
        return result;
    }
}
