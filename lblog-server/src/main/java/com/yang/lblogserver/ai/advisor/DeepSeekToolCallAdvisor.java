package com.yang.lblogserver.ai.advisor;

import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.ChatClientMessageAggregator;
import org.springframework.ai.chat.client.advisor.ToolCallAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.deepseek.DeepSeekAssistantMessage;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.util.Assert;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

public class DeepSeekToolCallAdvisor extends ToolCallAdvisor {

    private final boolean streamResponses;

    protected DeepSeekToolCallAdvisor(ToolCallingManager toolCallingManager, int advisorOrder) {
        super(toolCallingManager, advisorOrder);
        this.streamResponses = false;
    }

    protected DeepSeekToolCallAdvisor(ToolCallingManager toolCallingManager, int advisorOrder, boolean conversationHistoryEnabled) {
        super(toolCallingManager, advisorOrder, conversationHistoryEnabled);
        this.streamResponses = false;
    }

    public DeepSeekToolCallAdvisor(ToolCallingManager toolCallingManager, int advisorOrder, boolean conversationHistoryEnabled, boolean streamToolCallResponses) {
        super(toolCallingManager, advisorOrder, conversationHistoryEnabled, streamToolCallResponses);
        this.streamResponses = streamToolCallResponses;
    }

    @Override
    public Flux<ChatClientResponse> adviseStream(ChatClientRequest chatClientRequest,
                                                  StreamAdvisorChain streamAdvisorChain) {
        Assert.notNull(streamAdvisorChain, "streamAdvisorChain must not be null");
        Assert.notNull(chatClientRequest, "chatClientRequest must not be null");

        if (chatClientRequest.prompt().getOptions() == null
                || !(chatClientRequest.prompt().getOptions() instanceof ToolCallingChatOptions)) {
            throw new IllegalArgumentException(
                    "ToolCall Advisor requires ToolCallingChatOptions to be set in the ChatClientRequest options.");
        }

        ChatClientRequest initializedRequest = this.doInitializeLoopStream(chatClientRequest, streamAdvisorChain);
        var optionsCopy = (ToolCallingChatOptions) chatClientRequest.prompt().getOptions().copy();
        optionsCopy.setInternalToolExecutionEnabled(false);

        return reasoningLoop(streamAdvisorChain, initializedRequest, optionsCopy,
                initializedRequest.prompt().getInstructions());
    }

    private Flux<ChatClientResponse> reasoningLoop(StreamAdvisorChain streamAdvisorChain,
                                                    ChatClientRequest originalRequest,
                                                    ToolCallingChatOptions optionsCopy,
                                                    List<Message> instructions) {

        ChatClientRequest baseRequest = ChatClientRequest.builder()
                .prompt(new Prompt(instructions, optionsCopy))
                .context(originalRequest.context())
                .build();

        ChatClientRequest processedRequest = this.doBeforeStream(baseRequest, streamAdvisorChain);
        final ChatClientRequest finalRequest = processedRequest;
        StreamAdvisorChain chainCopy = streamAdvisorChain.copy(this);

        // 放行到下一个 advisor,实际获取流结果
        Flux<ChatClientResponse> stream = chainCopy.nextStream(processedRequest);

        // 从流式 chunk 中拼接 reasoningContent（每个 chunk 只带一小段）
        AtomicReference<String> reasoningHolder = new AtomicReference<>();
        Flux<ChatClientResponse> captured = stream.doOnNext(ccr -> {
            if (ccr != null && ccr.chatResponse() != null) {
                ccr.chatResponse().getResults().forEach(gen -> {
                    if (gen.getOutput() instanceof DeepSeekAssistantMessage dsam) {
                        String rc = dsam.getReasoningContent();
                        if (rc != null && !rc.isEmpty()) {
                            reasoningHolder.updateAndGet(existing ->
                                    existing != null ? existing + rc : rc);
                        }
                    }
                });
            }
        });

        // 多播：一份实时流式输出，一份聚合后处理工具调用
        return captured.publish(shared -> {
            AtomicReference<ChatClientResponse> aggregatedRef = new AtomicReference<>();

            // 分支1：实时流式输出 + 聚合
            Flux<ChatClientResponse> streaming =
                    new ChatClientMessageAggregator()
                            .aggregateChatClientResponse(shared, aggregatedRef::set);

            // 分支2：流结束后检查工具调用
            Flux<ChatClientResponse> recursion = Flux.defer(() -> {
                ChatClientResponse aggregated = aggregatedRef.get();
                if (aggregated == null || aggregated.chatResponse() == null
                        || !aggregated.chatResponse().hasToolCalls()) {
                    return Flux.empty();
                }

                ChatResponse chatResponse = aggregated.chatResponse();
                ToolExecutionResult toolResult;
                try {
                    toolResult = this.toolCallingManager.executeToolCalls(
                            finalRequest.prompt(), chatResponse);
                } catch (Exception e) {
                    return Flux.error(e);
                }

                if (toolResult.returnDirect()) {
                    return Flux.just(aggregated);
                }

                // 注入 reasoningContent
                String rc = reasoningHolder.get();
                List<Message> nextInstructions;
                if (rc != null && !rc.isEmpty()) {
                    nextInstructions = patchHistory(toolResult.conversationHistory(), rc);
                } else {
                    nextInstructions = toolResult.conversationHistory();
                }

                return reasoningLoop(streamAdvisorChain, originalRequest,
                        optionsCopy, nextInstructions);
            }).subscribeOn(Schedulers.boundedElastic());

            return streaming.concatWith(recursion);
        }).filter(ccr -> this.streamResponses
                || !(ccr.chatResponse() != null && ccr.chatResponse().hasToolCalls()));
    }

    private List<Message> patchHistory(List<Message> history, String reasoningContent) {
        List<Message> result = new ArrayList<>();
        for (Message msg : history) {
            if (msg instanceof AssistantMessage am
                    && am.getToolCalls() != null && !am.getToolCalls().isEmpty()) {
                result.add(new DeepSeekAssistantMessage.Builder()
                        .content(am.getText())
                        .reasoningContent(reasoningContent)
                        .toolCalls(am.getToolCalls())
                        .build());
            } else {
                result.add(msg);
            }
        }
        return result;
    }
}
