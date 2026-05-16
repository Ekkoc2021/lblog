package com.yang.lblogserver.ai.config;

import com.yang.lblogserver.ai.advisor.LocalToolCallAdvisor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.api.BaseAdvisor;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.deepseek.DeepSeekChatModel;
import org.springframework.ai.deepseek.DeepSeekChatOptions;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

@Configuration
public class AiConfig {

    @Bean
    public ChatClient drawChatClient(DeepSeekChatModel chatModel) {
        return ChatClient.builder(chatModel)
                .defaultAdvisors(
                        LocalToolCallAdvisor
                                .builder()
                                .advisorOrder(BaseAdvisor.HIGHEST_PRECEDENCE+1) // 最先执行
                                .build())
                .build();
    }

    private static ChatOptions getDefaultOptions(ChatModel chatModel) {
        return chatModel.getDefaultOptions();
    }
}
