package com.yang.lblogserver.ai.memory.estimator;

import com.yang.lblogserver.ai.conversation.domain.ChatMessage;
import java.util.List;

public interface TokenEstimator {

    int estimate(String text);

    int estimate(List<ChatMessage> messages);
}
