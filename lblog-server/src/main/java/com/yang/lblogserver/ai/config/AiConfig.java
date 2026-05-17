package com.yang.lblogserver.ai.config;

import com.yang.lblogserver.ai.advisor.LocalToolCallAdvisor;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.api.BaseAdvisor;
import org.springframework.ai.deepseek.DeepSeekChatModel;
import org.springframework.ai.deepseek.DeepSeekChatOptions;
import org.springframework.ai.deepseek.ReasoningChatModel;
import org.springframework.ai.deepseek.api.DeepSeekApi;
import org.springframework.ai.model.tool.DefaultToolExecutionEligibilityPredicate;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.support.RetryTemplate;

@Configuration
public class AiConfig {

    @Bean
    public DeepSeekApi deepSeekApi(@Value("${spring.ai.deepseek.api-key}") String apiKey) {
        return DeepSeekApi.builder().apiKey(apiKey).build();
    }

    @Bean
    public ReasoningChatModel reasoningChatModel(DeepSeekApi deepSeekApi,
                                                  ToolCallingManager toolCallingManager,
                                                  @Value("${spring.ai.deepseek.chat.options.model}") String modelName) {
        var options = DeepSeekChatOptions.builder().model(modelName).build();
        return new ReasoningChatModel(
                deepSeekApi, options, toolCallingManager,
                new RetryTemplate(),
                ObservationRegistry.NOOP,
                new DefaultToolExecutionEligibilityPredicate());
    }

    @Bean
    public ChatClient drawChatClient(DeepSeekChatModel chatModel, ToolCallingManager toolCallingManager) {
        return ChatClient.builder(chatModel)
                .defaultAdvisors(
                        new LocalToolCallAdvisor(
                                toolCallingManager,
                                BaseAdvisor.HIGHEST_PRECEDENCE + 1,
                                true,
                                true))
                .build();
    }
}
