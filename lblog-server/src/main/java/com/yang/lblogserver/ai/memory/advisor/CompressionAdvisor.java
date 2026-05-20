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

import java.util.ArrayList;
import java.util.List;

/**
 * 上下文压缩 advisor。每次 LLM 调用前委托 {@link CompressionStrategy} 压缩。
 * <p>
 * 职责：拆分 system 消息 → 策略只操作非 system 部分 → 补回 system → 循环调 tryCompress
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

        if (total > maxHistoryTokens || compressionStrategy.shouldCompress(messages)) {
            // 1. 拆分 system，策略只操作非 system 部分
            Message system = messages.getFirst();
            List<Message> target = messages.subList(1, messages.size());

            // 2. 策略压缩
            List<Message> compressed = compressionStrategy.compress(target);

            // 3. 补回 system
            List<Message> result = new ArrayList<>();
            result.add(system);
            result.addAll(compressed);

            // 4. token 还超则循环调 tryCompress，至少保留 system
            int guard = 50;
            while (result.size() > 1 && estimateTokens(result) > maxHistoryTokens && guard-- > 0) {
                List<Message> after = compressionStrategy.tryCompress(result.subList(1, result.size()));
                if (after.isEmpty() || after.size() >= result.size() - 1) break;
                result = new ArrayList<>();
                result.add(system);
                result.addAll(after);
            }

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
