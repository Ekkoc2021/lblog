# P2 滑动窗口压缩 — 设计文档

> 日期：2026-05-19
> 对应实现：commit `e43ba2e`
> 状态：✅ 已交付

---

## 一、要解决的问题

每次 LLM 调用前，上下文中包含系统指令、历史消息、工具调用结果等。
随着对话轮次增加，上下文持续膨胀，导致：

1. **token 浪费** — 旧消息的注意力权重低，但仍占窗口
2. **有效信息被稀释** — 过长历史中 LLM 容易忽略中间段关键信息
3. **超预算风险** — 工具调用循环中上下文可能超出合理预算

---

## 二、架构设计

### 2.1 Advisor 链

```
ChatHistoryAdvisor (HIGHEST_PRECEDENCE)
  → 从 DB 加载全量历史，拼入 prompt
  → 不做压缩

DeepSeekToolCallAdvisor (+2)
  → 工具调用循环
  → 每次递归调 chainCopy 中下一个 advisor

CompressionAdvisor (+3)                 ← 每次 LLM 调用前执行
  → token 超预算 → 直接压缩
  → token 没超   → 问 CompressionStrategy.shouldCompress
       ↓ true
  → CompressionStrategy.compress()
```

### 2.2 职责分离

| 组件 | 职责 | 设计模式 |
|------|------|---------|
| `CompressionAdvisor` | 压缩入口，控制时机 | 职责链节点 |
| `CompressionStrategy` | 压缩策略接口 | 策略模式 |
| `SlidingWindowStrategy` | 具体实现：按消息数丢旧 | 策略实现 |
| `TokenEstimator` | token 估算接口 | 策略模式 |
| `CharBasedTokenEstimator` | 字符估算实现 | 策略实现 |

### 2.3 CompressionAdvisor 决策逻辑

```
compressIfNeeded(request)
  │
  ├ estimateTokens(messages)
  │    ↓
  ├ total > maxHistoryTokens ?
  │    ↓ 是                      ↓ 否
  │  必须压缩              ┌────────────────────┐
  │                       │ shouldCompress() ?  │
  │                       └────────┬───────────┘
  │                            ↓ 是      ↓ 否
  │                          压缩      不压缩
  │    ↓
  └── 1. compressionStrategy.compress()    ← 按策略规则裁切（如按条数）
       │
       ├ 2. 硬截断兜底（仅 overBudget 时）
       │    while 还超预算 → 从索引 1 开始丢
       │    → 直到预算内或只剩 system 消息
       │
       └ 3. 返回改写后的 prompt
```

---

## 三、核心实现

### 3.1 CompressionAdvisor

```java
// ai/memory/advisor/CompressionAdvisor.java
// 每次 LLM 调用前执行，委托 CompressionStrategy 做具体压缩
```

关键代码：

```java
private ChatClientRequest compressIfNeeded(ChatClientRequest request) {
    List<Message> messages = request.prompt().getInstructions();
    if (messages == null || messages.isEmpty()) return request;

    int total = estimateTokens(messages);
    boolean overBudget = total > maxHistoryTokens;

    // token 超预算 → 直接压缩；没超但策略认为需要 → 也压缩
    if (overBudget || compressionStrategy.shouldCompress(messages)) {
        List<Message> result = compressionStrategy.compress(messages);

        // 硬截断兜底：策略压缩后 token 还超，从最旧消息开始丢
        while (result.size() > 1 && estimateTokens(result) > maxHistoryTokens) {
            result.remove(1);  // 始终丢索引 1（最旧的非 system 消息）
        }

        log.info("CompressionAdvisor: {} → {} messages", messages.size(), result.size());
        return request.mutate().prompt(new Prompt(result, request.prompt().getOptions())).build();
    }
    return request;
}
```

**硬截断说明：** `SlidingWindowStrategy` 按条数裁切（如从 30 条裁到 20 条）。
如果单条消息很大（如系统 prompt 本身就占 3000 token），裁完还是超预算。
硬截断从尾部一条条丢，直到预算内或只剩 system 消息。不依赖策略的实现细节。

### 3.2 CompressionStrategy 接口

```java
// ai/memory/compression/CompressionStrategy.java
public interface CompressionStrategy {
    boolean shouldCompress(List<Message> messages);
    List<Message> compress(List<Message> messages);
}
```

- `shouldCompress` — 策略自己的判断标准（如消息数超阈值）
- `compress` — 执行压缩，返回压缩后的消息列表

### 3.3 SlidingWindowStrategy

```java
// ai/memory/compression/SlidingWindowStrategy.java
// 保留 system message（索引0）+ 最近 N-1 条消息
```

| 参数 | 默认值 | 说明 |
|------|--------|------|
| `maxMessages` | 20 | 压缩后保留的消息数 |
| `minTrigger` | 30 | shouldCompress 触发阈值，超过即触发 |

```java
@Override
public List<Message> compress(List<Message> messages) {
    if (messages == null || messages.size() <= maxMessages) return messages;

    List<Message> result = new ArrayList<>();
    result.add(messages.getFirst());                              // 始终保留 system
    result.addAll(messages.subList(messages.size() - maxMessages + 1, messages.size()));
    return result;
}
```

### 3.4 TokenEstimator + CharBasedTokenEstimator

```java
// ai/memory/estimator/TokenEstimator.java
public interface TokenEstimator {
    int estimate(String text);
    int estimate(List<ChatMessage> messages);
}
```

```java
// ai/memory/estimator/CharBasedTokenEstimator.java
// 按字符长度的简单估算：len / 2 + 1
// 可替换为 jtokkit 实现更高精度
public int estimate(String text) {
    if (text == null || text.isEmpty()) return 0;
    return text.length() / 2 + 1;
}
```

---

## 四、配置

### 4.1 AiConfig 装配

```java
@Bean
public CompressionStrategy compressionStrategy() {
    return new SlidingWindowStrategy(20, 30);
}

@Bean
public ChatClient drawChatClient(..., TokenEstimator tokenEstimator,
                                   CompressionStrategy compressionStrategy,
                                   @Value("${ai.context.max-history-tokens:4000}") int maxHistoryTokens) {
    return ChatClient.builder(chatModel)
            .defaultAdvisors(
                    chatHistoryAdvisor,
                    new DeepSeekToolCallAdvisor(...),
                    new CompressionAdvisor(tokenEstimator, compressionStrategy, maxHistoryTokens,
                            BaseAdvisor.HIGHEST_PRECEDENCE + 3))
            .build();
}
```

### 4.2 配置项

| 配置 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `ai.context.max-history-tokens` | int | 4000 | token 预算上限 |
| `reasoning_inject` | boolean | false | 历史 reasoning 是否注入 |
| `SlidingWindowStrategy.maxMessages` | int | 20 | 压缩后保留的消息数 |
| `SlidingWindowStrategy.minTrigger` | int | 30 | 触发压缩的消息数阈值 |

---

## 五、扩展方式

### 新增压缩策略

只需实现 `CompressionStrategy` 接口，在 `AiConfig` 替换 bean 即可，`CompressionAdvisor` 不动：

```java
public class FreshnessBasedStrategy implements CompressionStrategy {
    @Override
    public boolean shouldCompress(List<Message> messages) {
        return messages.size() > 50;
    }

    @Override
    public List<Message> compress(List<Message> messages) {
        // 按新鲜度评分丢弃低分消息
    }
}
```

### 替换 Token 估算器

```java
@Component
@Primary
public class JtokkitTokenEstimator implements TokenEstimator {
    // 用 jtokkit 库做精确估算
}
```

---

## 六、测试结果

执行日期：2026-05-19

| 场景 | 结果 |
|------|------|
| 短对话（预算内）不触发压缩 | ✅ |
| 多轮对话（token 超预算）触发压缩 | ✅ `4→3 messages` |
| SlidingWindowStrategy 保留 system + 最近 N 条 | ✅ |
| shouldCompress 条数阈值判定 | ⚠️ 实际触发靠 overBudget |
| 策略压缩后硬截断兜底 | ✅ `estimateTokens + removeLast` |
| 换策略不改 CompressionAdvisor | ✅ 代码检视通过 |

**发现的问题：**
- `sessionId` 必须为数字（`Long.parseLong`），非数字静默跳过历史加载
- 当前压缩主要由 token 预算触发，`shouldCompress` 的条数阈值较少达到

---

## 七、文件清单

```
ai/memory/
├── advisor/
│   ├── ChatHistoryAdvisor.java      # 历史加载 + 消息保存
│   └── CompressionAdvisor.java      # 压缩入口
├── compression/
│   ├── CompressionStrategy.java     # 策略接口
│   └── SlidingWindowStrategy.java   # 滑动窗口实现
└── estimator/
    ├── TokenEstimator.java          # token 估算接口
    └── CharBasedTokenEstimator.java # 字符估算实现
```
