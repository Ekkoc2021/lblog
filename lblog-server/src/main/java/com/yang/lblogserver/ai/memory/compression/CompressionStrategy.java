package com.yang.lblogserver.ai.memory.compression;

import org.springframework.ai.chat.messages.Message;
import java.util.List;

public interface CompressionStrategy extends LoadingStrategy {

    /** 按自己的标准判断是否需要压缩（如消息数超过阈值）。 */
    boolean shouldCompress(List<Message> messages);

    /**
     * 执行压缩。
     * <p>
     * 注意：
     * <ul>
     *   <li>传入的 messages 来自 {@code CompressionAdvisor} 拆分后的非 system 部分</li>
     *   <li>不保证包含 system 消息，策略无需做首尾保护（由 advisor 负责）</li>
     *   <li>传入的可能是 {@link List#subList subList} 视图，不要直接对传入的 list 做结构性修改
     *       （如 add/remove），应 {@code new ArrayList<>(messages)} 再操作</li>
     * </ul>
     * 返回空列表表示"全部丢弃"，advisor 自行决定是否接受。
     */
    List<Message> compress(List<Message> messages);

    /**
     * 再压缩一步。上层在 token 仍超预算时循环调用，直到预算内。
     * <p>
     * 注意：
     * <ul>
     *   <li>传入的 messages 同样不保证包含 system 消息</li>
     *   <li>传入的可能是 subList 视图，不要直接对传入的 list 做结构性修改</li>
     * </ul>
     * 返回 size 不变或返回空列表表示无法再压缩。
     */
    List<Message> tryCompress(List<Message> messages);
}
