package com.yang.lblogserver.ai.memory.advisor;

import com.yang.lblogserver.ai.conversation.domain.ChatMessage;
import com.yang.lblogserver.ai.memory.ChatMemoryStore;
import com.yang.lblogserver.ai.memory.compression.LoadingStrategy;
import com.yang.lblogserver.ai.memory.converter.ContextPolicy;
import com.yang.lblogserver.ai.memory.converter.ModelMessageConverter;
import com.yang.lblogserver.site.service.SiteConfigCacheService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.client.advisor.api.BaseAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 对话记忆 advisor，负责注入历史 + 保存回复。不做压缩，压缩由 {@code CompressionAdvisor} 负责。
 * <p>
 * <ul>
 *   <li><b>before</b> — 从 DB 加载历史消息（全量），经 ModelMessageConverter 转为 Message，拼入 prompt</li>
 *   <li><b>after</b> — 保存 user 消息（before 阶段提前存）和 assistant 回复</li>
 * </ul>
 * Advisor 链位置：{@link BaseAdvisor#HIGHEST_PRECEDENCE}，最先执行。
 */
public class ChatHistoryAdvisor implements BaseAdvisor {

    private static final Logger log = LoggerFactory.getLogger(ChatHistoryAdvisor.class);

    private final ChatMemoryStore chatMemoryStore;
    private final List<ModelMessageConverter> converters;
    private final LoadingStrategy loadingStrategy;
    private final SiteConfigCacheService siteConfigCacheService;
    private final int order;

    public ChatHistoryAdvisor(ChatMemoryStore chatMemoryStore,
                              List<ModelMessageConverter> converters,
                              LoadingStrategy loadingStrategy,
                              SiteConfigCacheService siteConfigCacheService) {
        this.chatMemoryStore = chatMemoryStore;
        this.converters = converters;
        this.loadingStrategy = loadingStrategy;
        this.siteConfigCacheService = siteConfigCacheService;
        this.order = BaseAdvisor.HIGHEST_PRECEDENCE;
    }

    @Override
    public ChatClientRequest before(ChatClientRequest request, AdvisorChain chain) {
        // 从 toolContext 中取出 sessionId 和 modelName
        Map<String, Object> ctx = request.context();
        String sessionId = ctx != null ? (String) ctx.get("sessionId") : null;
        String modelName = ctx != null ? (String) ctx.get("modelName") : null;

        // 没有 sessionId 说明是无状态对话，跳过历史加载
        if (sessionId == null || sessionId.isBlank()) {
            return request;
        }

        try {
            Long sid = Long.parseLong(sessionId);

            // 1. 历史加载（有策略则按策略，否则全量降级）
            List<ChatMessage> history = loadingStrategy != null
                    ? loadingStrategy.load(sid, chatMemoryStore)
                    : chatMemoryStore.loadHistory(sid);

            // 2. 提前持久化当前 user 消息，这样后续的多轮工具调用也能读到它
            saveCurrentUserMessage(request, sid);

            // 3. 将 DB 的 ChatMessage 转为 Spring AI 的 Message（按 policy 决定是否带 reasoning）
            ContextPolicy policy = buildPolicy();
            ModelMessageConverter converter = findConverter(modelName);
            List<Message> contextMessages = buildContextMessages(history, converter, policy);

            // 4. 拆出原 prompt 中的 system 消息和其他消息
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

            // 5. 组装最终的消息序列：system → 历史 → 当前请求
            // 利用 LLM 的 Primacy/Recency 效应，system 指令在开头，当前请求在末尾
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

    /**
     * 同步场景：调用返回后保存 assistant 的完整回复。
     * 流式场景不走这里，由 adviseStream 的 doOnComplete 处理。
     */
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
                // 把 assistant 的回复 message 转成 DB entity 保存
                Message output = response.chatResponse().getResult().getOutput();
                int msgIndex = chatMemoryStore.getMaxMsgIndex(sid) + 1;
                ChatMessage stored = converter.toStorageMessage(output, sid, msgIndex);
                chatMemoryStore.saveMessages(sid, List.of(stored));
                // updated_at 通过 DB ON UPDATE CURRENT_TIMESTAMP 自动刷新
            }

            return response;
        } catch (Exception e) {
            log.error("ChatHistoryAdvisor.after() error", e);
            return response;
        }
    }

    /**
     * 流式场景：before 逻辑与同步一致，之后放行到下一个 advisor。
     * 用 doOnNext 收集 SSE chunk，doOnComplete 时拼装成完整消息再持久化。
     */
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

            // --- before 阶段：加载历史 ---
            List<ChatMessage> history = loadingStrategy != null
                    ? loadingStrategy.load(sid, chatMemoryStore)
                    : chatMemoryStore.loadHistory(sid);
            saveCurrentUserMessage(request, sid);

            ContextPolicy policy = buildPolicy();
            ModelMessageConverter converter = findConverter(modelName);
            List<Message> contextMessages = buildContextMessages(history, converter, policy);

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
            // --- before 阶段结束 ---

            // 将 sid/modelName 捕获到 final 变量，供 doOnComplete 闭包使用
            final Long capturedSid = sid;
            final String capturedModelName = modelName;

            // 收集所有 SSE chunk，doOnComplete 时拼装为完整消息
            List<ChatClientResponse> chunks = new CopyOnWriteArrayList<>();
            Flux<ChatClientResponse> stream = chain.nextStream(modifiedRequest);

            return stream
                    .doOnNext(chunks::add)
                    .doOnComplete(() -> {
                        try {
                            if (!chunks.isEmpty()) {
                                // 将所有 chunk 中的文本片段拼接成一条完整的 assistant 消息
                                ModelMessageConverter conv = findConverter(capturedModelName);
                                int msgIndex = chatMemoryStore.getMaxMsgIndex(capturedSid) + 1;
                                ChatMessage stored = conv.toStorageMessageFromChunks(
                                        chunks, capturedSid, msgIndex);
                                chatMemoryStore.saveMessages(capturedSid, List.of(stored));
                                // updated_at 通过 DB ON UPDATE CURRENT_TIMESTAMP 自动刷新
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

    /**
     * 在 before 阶段提前持久化当前 user 消息。
     * 原因：after 阶段执行时可能已经触发多轮工具调用，需要确保 user 消息在历史中可用。
     * 从 messages 列表末尾往前找 user 消息（最后一个 user 消息就是当前请求）。
     */
    private void saveCurrentUserMessage(ChatClientRequest request, Long sessionId) {
        List<Message> instructions = request.prompt().getInstructions();
        // 倒序遍历，找到最后一条 UserMessage（就是本次用户的输入）
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
                // 只处理最后一条 user 消息，找到就停
                break;
            }
        }
    }

    /**
     * 根据 modelName 找到对应的消息转换器。
     * 匹配不到时返回第一个注册的 converter 兜底。
     */
    private ModelMessageConverter findConverter(String modelName) {
        if (modelName == null) {
            return converters.get(0);
        }
        return converters.stream()
                .filter(c -> c.supports(modelName))
                .findFirst()
                .orElse(converters.get(0));
    }

    /**
     * 从 site_config 读取 reasoning_inject 配置。
     * true = 历史中的 reasoning_content 也会注入上下文
     * false/不存在 = 只带纯文本内容，跳过 reasoning
     */
    private ContextPolicy buildPolicy() {
        ContextPolicy policy = new ContextPolicy();
        String val = siteConfigCacheService.getConfigValue("reasoning_inject");
        policy.setIncludeReasoning("true".equals(val));
        return policy;
    }

    /**
     * 将 DB 中的 ChatMessage 列表转为 Spring AI 的 Message 列表：
     * - assistant 消息 → 通过 converter 转为 AssistantMessage/DeepSeekAssistantMessage
     * - user 消息 → 直接转为 UserMessage
     * - tool 消息 → 跳过（由 DeepSeekToolCallAdvisor 的循环处理）
     */
    private List<Message> buildContextMessages(List<ChatMessage> history, ModelMessageConverter converter, ContextPolicy policy) {
        List<Message> messages = new ArrayList<>();
        for (ChatMessage msg : history) {
            // converter 只处理 assistant 消息，其他返回 null
            Message converted = converter.toContextMessage(msg, policy);
            if (converted != null) {
                messages.add(converted);
                continue;
            }
            // converter 不处理的 role 走 fallback
            switch (msg.getRole()) {
                case "user" -> messages.add(new UserMessage(msg.getContent()));
                case "tool" -> {
                    // tool 消息会在 DeepSeekToolCallAdvisor 的调用循环中被重新构建，这里跳过
                }
            }
        }
        return messages;
    }
}
