package com.yang.lblogserver.ai.skill;

import com.yang.lblogserver.ai.advisor.DeepSeekToolCallAdvisor;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.advisor.ToolCallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.tool.ToolCallback;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class SkillAwareToolCallAdvisor extends ToolCallAdvisor {

    private static final String ORIGINAL_TOOL_CALLBACKS_KEY = "_skillAware_originalToolCallbacks";

    private final SkillToolRegistry skillToolRegistry;
    private final SkillSessionManager skillSessionManager;

    public SkillAwareToolCallAdvisor(
            ToolCallingManager toolCallingManager,
            int advisorOrder,
            SkillToolRegistry skillToolRegistry,
            SkillSessionManager skillSessionManager) {
        super(toolCallingManager, advisorOrder);
        this.skillToolRegistry = skillToolRegistry;
        this.skillSessionManager = skillSessionManager;
    }

    @Override
    protected ChatClientRequest doInitializeLoop(ChatClientRequest request, CallAdvisorChain chain) {
        saveOriginalToolCallbacks(request.context(), request);
        return super.doInitializeLoop(request, chain);
    }

    @Override
    protected ChatClientRequest doInitializeLoopStream(ChatClientRequest request, StreamAdvisorChain chain) {
        saveOriginalToolCallbacks(request.context(), request);
        return super.doInitializeLoopStream(request, chain);
    }

    @Override
    protected ChatClientRequest doBeforeCall(ChatClientRequest request, CallAdvisorChain chain) {
        filterTools(request);
        return super.doBeforeCall(request, chain);
    }

    @Override
    protected ChatClientRequest doBeforeStream(ChatClientRequest request, StreamAdvisorChain chain) {
        filterTools(request);
        return super.doBeforeStream(request, chain);
    }

    @SuppressWarnings("unchecked")
    private void saveOriginalToolCallbacks(Map<String, Object> context, ChatClientRequest request) {
        if (context.containsKey(ORIGINAL_TOOL_CALLBACKS_KEY)) {
            return;
        }
        if (request.prompt().getOptions() instanceof ToolCallingChatOptions opts) {
            context.put(ORIGINAL_TOOL_CALLBACKS_KEY, new ArrayList<>(opts.getToolCallbacks()));
        }
    }

    @SuppressWarnings("unchecked")
    private void filterTools(ChatClientRequest request) {
        if (!(request.prompt().getOptions() instanceof ToolCallingChatOptions toolOptions)) {
            return;
        }

        List<ToolCallback> originalCallbacks =
                (List<ToolCallback>) request.context().get(ORIGINAL_TOOL_CALLBACKS_KEY);
        if (originalCallbacks == null || originalCallbacks.isEmpty()) {
            return;
        }

        String sessionId = (String) request.context().get("sessionId");
        Set<String> loadedSkills = sessionId != null
                ? skillSessionManager.getLoadedSkills(sessionId)
                : Set.of();

        List<ToolCallback> filtered = new ArrayList<>();
        for (ToolCallback cb : originalCallbacks) {
            String toolName = cb.getToolDefinition().name();
            if (shouldIncludeTool(toolName, loadedSkills)) {
                filtered.add(cb);
            }
        }

        toolOptions.setToolCallbacks(filtered);
    }

    private boolean shouldIncludeTool(String toolName, Set<String> loadedSkills) {
        if (skillToolRegistry.isAlwaysAvailable(toolName)) {
            return true;
        }
        if (skillToolRegistry.isSkillTool(toolName)) {
            Map<String, List<String>> toolSkills = skillToolRegistry.getToolSkills();
            List<String> requiredSkills = toolSkills.get(toolName);
            if (requiredSkills != null) {
                for (String skill : requiredSkills) {
                    if (loadedSkills.contains(skill)) {
                        return true;
                    }
                }
            }
            return false;
        }
        return true;
    }
}
