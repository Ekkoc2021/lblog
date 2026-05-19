package com.yang.lblogserver.ai.memory.compression;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.Message;

import java.util.ArrayList;
import java.util.List;

/**
 * 滑动窗口：消息数超过 maxMessages 时，从最旧开始丢弃，保留最近 maxMessages 条。
 * 始终保留索引 0（system message），至少保留 1 条。
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

        int beforeSize = messages.size();
        List<Message> result = new ArrayList<>();
        result.add(messages.getFirst());                              // system
        result.addAll(messages.subList(messages.size() - maxMessages + 1, messages.size()));
        log.info("SlidingWindow: {} → {} messages, maxMessages={}", beforeSize, result.size(), maxMessages);
        return result;
    }
}
