package com.yang.lblogserver.ai.memory.compression;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.Message;

import java.util.ArrayList;
import java.util.List;

/**
 * 滑动窗口：消息数超过 minTrigger 时，保留最近 N 条。
 * 压缩不感知 token，由上层 CompressionAdvisor 检查 token 预算并循环调 tryDropOne。
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

        List<Message> result = new ArrayList<>();
        result.add(messages.getFirst());
        result.addAll(messages.subList(messages.size() - maxMessages + 1, messages.size()));
        log.info("SlidingWindow: {} → {} messages (max={})", messages.size(), result.size(), maxMessages);
        return result;
    }

    /** 再压缩一步：丢弃最旧的一条非 system 消息。 */
    @Override
    public List<Message> tryCompress(List<Message> messages) {
        if (messages == null || messages.size() <= 1) return messages;

        List<Message> result = new ArrayList<>(messages);
        result.remove(1);
        return result;
    }
}
