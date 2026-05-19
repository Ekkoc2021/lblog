package com.yang.lblogserver.ai.memory.estimator;

import com.yang.lblogserver.ai.conversation.domain.ChatMessage;
import org.springframework.stereotype.Component;
import java.util.List;

@Component
public class CharBasedTokenEstimator implements TokenEstimator {

    @Override
    public int estimate(String text) {
        if (text == null || text.isEmpty()) return 0;
        return text.length() / 2 + 1;
    }

    @Override
    public int estimate(List<ChatMessage> messages) {
        if (messages == null) return 0;
        return messages.stream()
                .mapToInt(msg -> estimate(msg.getContent()))
                .sum();
    }
}
