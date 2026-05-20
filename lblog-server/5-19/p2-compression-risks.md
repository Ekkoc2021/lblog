# P2 压缩风险与待优化点

> 状态：📄 已识别，当前不做
> 日期：2026-05-19

---

## 风险 1：工具循环中压缩掉 user 消息

**场景：**

```
第 1 轮: [system, ..., U_current]
         ↓ LLM 返回 tool_calls
第 2 轮: [system, U1, A1, ..., U_current, A_tool, T_result]

compress(保留最近 3 条): [system, A_tool, T_result]
                                ↑ U_current 丢了
```

当前画图场景中 AI 第一轮已处理用户请求，后续工具循环只看工具结果，不影响功能。但当工具循环中用户追加新要求时会出问题。

**可能的修法：** 保留最近一条 `role=user` 的消息，排除在压缩之外。

```java
// compress 后检查最后一条 user 是否还在
Optional<Message> lastUser = messages.stream()
        .filter(m -> "user".equals(m.getRole()))
        .reduce((first, second) -> second);
if (lastUser.isPresent() && !result.contains(lastUser.get())) {
    result.add(lastUser.get());
}
```

---

## 风险 2：工具调用对被拆散

`tryCompress` 的 `removeFirst()` 如果丢到 `AssistantMessage(hasToolCalls)`，对应的 `ToolResponseMessage` 就成了孤儿，DeepSeek API 会报错。

```
[..., A_tool, T_result, ...]
       ↑ 被 removeFirst()
       
剩下的: [..., T_result, ...]  ← 没有对应的 assistant 消息
```

**可能的修法 1：** 成对丢

```java
@Override
public List<Message> tryCompress(List<Message> messages) {
    // 从头部找第一对非 tool 的消息丢，如果头部是 tool 对则一起丢
    for (int i = 0; i < messages.size(); i++) {
        if (i + 1 < messages.size() 
            && messages.get(i) instanceof AssistantMessage am 
            && am.hasToolCalls()
            && messages.get(i + 1) instanceof ToolResponseMessage) {
            // 跳过 tool 对，丢后面的
            continue;
        }
        // 丢这条非 tool 消息
        List<Message> result = new ArrayList<>(messages);
        result.remove(i);
        return result;
    }
    return messages;
}
```

**可能的修法 2：** 非 tool 消息优先丢，实在没有了才丢 tool 对

```java
@Override
public List<Message> tryCompress(List<Message> messages) {
    // 先找非 tool 消息丢
    for (int i = 0; i < messages.size(); i++) {
        Message msg = messages.get(i);
        boolean isToolPart = (msg instanceof AssistantMessage am && am.hasToolCalls())
            || msg instanceof ToolResponseMessage;
        if (!isToolPart) {
            List<Message> result = new ArrayList<>(messages);
            result.remove(i);
            return result;
        }
    }
    // 全是 tool 对，成对丢最旧的
    if (messages.size() >= 2) {
        List<Message> result = new ArrayList<>(messages.subList(2, messages.size()));
        return result;
    }
    return messages;
}
```

---

## 风险 3：System 消息位置假设

当前代码 `messages.getFirst()` 假设 system 在索引 0。但 `ChatHistoryAdvisor` 或 `PromptManager` 改了拼接顺序，或 advisor 链中混入了其他 SystemMessage 时会失效。

**可能的修法：** 按类型过滤而不是按位置取。

```java
// 改为：
List<Message> systemMessages = messages.stream()
        .filter(m -> m instanceof SystemMessage)
        .toList();
List<Message> nonSystemMessages = messages.stream()
        .filter(m -> !(m instanceof SystemMessage))
        .toList();
```

---

## 风险 4：压缩打断 Prompt Caching

每次压缩都改变了缓存前缀。如果未来接入支持 Prompt Caching 的模型（如 Claude），这种打断会导致缓存命中率下降。

**可能的修法：** 预留 buffer，在预算使用率达到指定阈值前不触发压缩。

```java
// 预算 80% 以下不压缩，留 buffer 给工具循环
int softLimit = (int)(maxHistoryTokens * 0.8);
if (total <= softLimit && !strategy.shouldCompress(messages)) return request;
```

---

## 总结

| 风险 | 影响 | 当前阶段是否处理 |
|------|------|----------------|
| 工具循环中丢 user | 低（画图场景不影响） | ❌ |
| tool 对被拆散 | 中（DeepSeek 可能报错） | ❌ |
| system 位置假设 | 低（当前稳定） | ❌ |
| 缓存打断 | 低（DeepSeek 不支持缓存） | ❌ |

风险 2 在后续对话轮次增多、工具调用频繁时优先级最高，建议优先修复。
