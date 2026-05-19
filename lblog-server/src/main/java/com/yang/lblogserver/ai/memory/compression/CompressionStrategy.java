package com.yang.lblogserver.ai.memory.compression;

import org.springframework.ai.chat.messages.Message;
import java.util.List;

public interface CompressionStrategy {

    /** 按自己的标准判断是否需要压缩（如消息数超过阈值）。 */
    boolean shouldCompress(List<Message> messages);

    /** 执行压缩。按策略自己的规则裁切（如保留最近 N 条）。 */
    List<Message> compress(List<Message> messages);

    /**
     * 再压缩一步。上层在 token 仍超预算时循环调用，直到预算内。
     * 各策略自行决定这一步做什么：
     * - 滑动窗口 → 丢弃最旧一条
     * - 摘要压缩 → 将最旧两条合并为摘要
     * - 去重 → 移除一组重复消息
     * 返回 size 不变表示无法再压缩。
     */
    List<Message> tryCompress(List<Message> messages);
}
