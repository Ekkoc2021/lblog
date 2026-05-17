package com.yang.lblogserver.ai.config;

import com.yang.lblogserver.ai.advisor.DeepSeekToolCallAdvisor;
import com.yang.lblogserver.ai.memory.advisor.ChatHistoryAdvisor;
import com.yang.lblogserver.ai.skill.SkillAwareToolCallAdvisor;
import com.yang.lblogserver.ai.skill.SkillSessionManager;
import com.yang.lblogserver.ai.skill.SkillToolRegistry;
import io.micrometer.observation.ObservationRegistry;
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
                                     ChatHistoryAdvisor chatHistoryAdvisor,
                                     SkillToolRegistry skillToolRegistry,
                                     SkillSessionManager skillSessionManager) {
        return ChatClient.builder(chatModel)
                .defaultAdvisors(
                        chatHistoryAdvisor,
                        new SkillAwareToolCallAdvisor(
                                toolCallingManager,
                                BaseAdvisor.HIGHEST_PRECEDENCE + 1,
                                skillToolRegistry,
                                skillSessionManager),
                        new DeepSeekToolCallAdvisor(
                                toolCallingManager,
                                BaseAdvisor.HIGHEST_PRECEDENCE + 2,
                                true,
                                true))
                .build();
    }
}
