package com.yang.lblogserver.ai.memory.advisor;

import com.yang.lblogserver.ai.memory.compression.CompressionStrategy;
import com.yang.lblogserver.ai.memory.estimator.TokenEstimator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.client.advisor.api.BaseAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * 上下文压缩 advisor。每次 LLM 调用前（首次 + 工具循环递归）都会经过这里。
 * <p>
 * 委托 {@link CompressionStrategy} 做具体压缩决策和执行。
 * token 超预算则直接压缩，不超则问策略的 {@link CompressionStrategy#shouldCompress}。
 */
public class CompressionAdvisor implements BaseAdvisor {

    private static final Logger log = LoggerFactory.getLogger(CompressionAdvisor.class);

    private final TokenEstimator tokenEstimator;
    private final CompressionStrategy compressionStrategy;
    private final int maxHistoryTokens;
    private final int order;

    public CompressionAdvisor(TokenEstimator tokenEstimator,
                               CompressionStrategy compressionStrategy,
                               int maxHistoryTokens,
                               int order) {
        this.tokenEstimator = tokenEstimator;
        this.compressionStrategy = compressionStrategy;
        this.maxHistoryTokens = maxHistoryTokens;
        this.order = order;
    }

    @Override
    public ChatClientRequest before(ChatClientRequest request, AdvisorChain chain) {
        return compressIfNeeded(request);
    }

    @Override
    public ChatClientResponse after(ChatClientResponse response, AdvisorChain chain) {
        return response;
    }

    @Override
    public Flux<ChatClientResponse> adviseStream(ChatClientRequest request, StreamAdvisorChain chain) {
        return chain.nextStream(compressIfNeeded(request));
    }

    private ChatClientRequest compressIfNeeded(ChatClientRequest request) {
        List<Message> messages = request.prompt().getInstructions();
        if (messages == null || messages.isEmpty()) return request;

        int total = estimateTokens(messages);
        boolean overBudget = total > maxHistoryTokens;

        if (overBudget || compressionStrategy.shouldCompress(messages)) {
            List<Message> result = compressionStrategy.compress(messages);
            log.info("CompressionAdvisor: {} → {} messages", messages.size(), result.size());
            return request.mutate().prompt(new Prompt(result, request.prompt().getOptions())).build();
        }

        return request;
    }

    private int estimateTokens(List<Message> messages) {
        int total = 0;
        for (Message msg : messages) {
            String text = msg.getText();
            if (text != null) total += tokenEstimator.estimate(text);
        }
        return total;
    }

    @Override
    public int getOrder() {
        return order;
    }
}
