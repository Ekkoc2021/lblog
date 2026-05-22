package com.yang.lblogserver.ai.config;

import java.util.List;

import com.yang.lblogserver.ai.advisor.DeepSeekToolCallAdvisor;
import com.yang.lblogserver.ai.memory.ChatMemoryStore;
import com.yang.lblogserver.ai.memory.advisor.ChatHistoryAdvisor;
import com.yang.lblogserver.ai.memory.advisor.CompressionAdvisor;
import com.yang.lblogserver.ai.memory.compression.SlidingWindowStrategy;
import com.yang.lblogserver.ai.memory.converter.ModelMessageConverter;
import com.yang.lblogserver.ai.memory.estimator.TokenEstimator;
import com.yang.lblogserver.site.mapper.SiteConfigMapper;
import io.micrometer.observation.ObservationRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.api.BaseAdvisor;
import org.springframework.ai.deepseek.DeepSeekChatModel;
import org.springframework.ai.deepseek.DeepSeekChatOptions;
import org.springframework.ai.deepseek.DeepSeekReasoningChatModel;
import org.springframework.ai.deepseek.api.DeepSeekApi;
import org.springframework.ai.model.tool.DefaultToolExecutionEligibilityPredicate;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.support.RetryTemplate;

@Configuration
public class AiConfig {

    private static final Logger log = LoggerFactory.getLogger(AiConfig.class);

    @Bean
    public DeepSeekApi deepSeekApi(@Value("${spring.ai.deepseek.api-key}") String apiKey) {
        return DeepSeekApi.builder().apiKey(apiKey).build();
    }

    @Bean
    public DeepSeekReasoningChatModel reasoningChatModel(DeepSeekApi deepSeekApi,
                                                  ToolCallingManager toolCallingManager,
                                                  @Value("${spring.ai.deepseek.chat.options.model}") String modelName) {
        var options = DeepSeekChatOptions.builder().model(modelName).build();
        return new DeepSeekReasoningChatModel(
                deepSeekApi, options, toolCallingManager,
                new RetryTemplate(),
                ObservationRegistry.NOOP,
                new DefaultToolExecutionEligibilityPredicate());
    }

    @Bean
    public ChatClient drawChatClient(DeepSeekChatModel chatModel,
                                     ToolCallingManager toolCallingManager,
                                     ChatMemoryStore chatMemoryStore,
                                     List<ModelMessageConverter> converters,
                                     SiteConfigMapper siteConfigMapper,
                                     TokenEstimator tokenEstimator,
                                     @Value("${ai.context.max-history-tokens:4000}") int maxHistoryTokens) {
        SlidingWindowStrategy slidingWindow = new SlidingWindowStrategy(20, 30);
        return ChatClient.builder(chatModel)
                .defaultAdvisors(
                        new ChatHistoryAdvisor(chatMemoryStore, converters, slidingWindow, siteConfigMapper),
                        new DeepSeekToolCallAdvisor(
                                toolCallingManager,
                                BaseAdvisor.HIGHEST_PRECEDENCE + 2,
                                true,
                                true),
                        new CompressionAdvisor(
                                tokenEstimator,
                                slidingWindow,
                                maxHistoryTokens,
                                BaseAdvisor.HIGHEST_PRECEDENCE + 3))
                .build();
    }
}
