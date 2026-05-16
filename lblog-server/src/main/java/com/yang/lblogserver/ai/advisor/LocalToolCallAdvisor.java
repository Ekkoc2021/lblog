package com.yang.lblogserver.ai.advisor;


import org.springframework.ai.chat.client.advisor.ToolCallAdvisor;
import org.springframework.ai.model.tool.ToolCallingManager;

public class LocalToolCallAdvisor extends ToolCallAdvisor {

    protected LocalToolCallAdvisor(ToolCallingManager toolCallingManager, int advisorOrder) {
        super(toolCallingManager, advisorOrder);
    }

    protected LocalToolCallAdvisor(ToolCallingManager toolCallingManager, int advisorOrder, boolean conversationHistoryEnabled) {
        super(toolCallingManager, advisorOrder, conversationHistoryEnabled);
    }

    protected LocalToolCallAdvisor(ToolCallingManager toolCallingManager, int advisorOrder, boolean conversationHistoryEnabled, boolean streamToolCallResponses) {
        super(toolCallingManager, advisorOrder, conversationHistoryEnabled, streamToolCallResponses);
    }
}
