package com.yang.lblogserver.ai.memory.compression;

import com.yang.lblogserver.ai.conversation.domain.ChatMessage;
import com.yang.lblogserver.ai.memory.ChatMemoryStore;

import java.util.List;

/**
 * 历史加载策略。决定 ChatHistoryAdvisor 从 DB 取多少消息。
 * <p>
 * 是 {@link CompressionStrategy} 的"加载面"——同一个策略既管加载时取什么，
 * 也管运行时压缩兜底。ChatHistoryAdvisor 注入此策略后按策略加载，未注入则降级为全量加载。
 */
public interface LoadingStrategy {

    List<ChatMessage> load(Long sessionId, ChatMemoryStore store);
}
