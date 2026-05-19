# P2 新鲜度评分 — 设计草案

> 状态：📄 待实现（当前不做）
> 前置依赖：P0（对话持久化）、P2 Token 预算计算

---

## 一、概念

给每条消息算一个"新鲜度分"，越新的消息分越高。压缩时优先丢低分消息。相比简单滑动窗口（只丢最旧），新鲜度评分可以**更精细地控制保留哪些消息**。

```
滑动窗口（按位置丢）：
  [旧] [旧] [旧] [新] [新] [最新]
    ↓ 丢前 3 条
  [新] [新] [最新]

新鲜度评分（按分丢）：
  [User: 背景红色]  → 2分
  [Assistant: OK]   → 2分
  [User: 用MySQL]   → 1分  ← 无关话题，分低
  [User: 改成蓝色]  → 5分
  [Assistant: 已完成] → 5分

  预算不够时先丢"用MySQL"那条，而不是按位置丢
```

---

## 二、评分模型

### 2.1 因子

| 因子 | 权重 | 说明 |
|------|------|------|
| 时间顺序 | 高 | 越新的消息分越高 |
| 是否 tool 调用 | 中 | tool 调用对分较高（包含执行结果） |
| 是否 system | 最高 | system 消息始终保留 |
| 内容长度 | 低 | 极短的消息（纯语气词）分略低 |

### 2.2 评分公式

```java
double score(Message msg, int index, int totalCount) {
    double s = 0;
    // 1. 时序分：越新越高
    s += 5.0 * index / totalCount;
    // 2. 类型加权
    if (msg instanceof SystemMessage) s += 5;
    if (msg instanceof ToolResponseMessage) s += 1;
    // 3. 内容加分
    if (msg.getText() != null && msg.getText().length() > 50) s += 0.5;
    return s;
}
```

---

## 三、实现

### 3.1 作为 CompressionStrategy 实现

```java
public class FreshnessScoringStrategy implements CompressionStrategy {

    private final int maxMessages;

    public FreshnessScoringStrategy(int maxMessages) {
        this.maxMessages = maxMessages;
    }

    @Override
    public boolean shouldCompress(List<Message> messages) {
        return messages.size() > maxMessages;
    }

    @Override
    public List<Message> compress(List<Message> messages) {
        if (messages.size() <= maxMessages) return messages;

        // 1. 计算每条消息的分数
        // 2. 保留 system message（强制高分）
        // 3. 按分数排序，保留 top N
        // 4. 按原始顺序重排
    }
}
```

### 3.2 替换策略

```java
@Bean
public CompressionStrategy compressionStrategy() {
    return new FreshnessScoringStrategy(20);
}
```

---

## 四、与滑动窗口的对比

| 维度 | SlidingWindowStrategy | FreshnessScoringStrategy |
|------|----------------------|--------------------------|
| 丢弃策略 | 只按位置丢最旧 | 按分数丢 | 
| 保留策略 | 始终保留最近 N 条 | 保留高分消息，可能保留旧但重要的消息 |
| 复杂度 | O(1) | O(n log n) 排序 |
| 场景 | 一般对话 | 需要精细控制上下文内容的场景 |

**结论：滑动窗口算法简单、可预测、行为直观。新鲜度评分提供了更精细的控制，但当前画图场景中用户不需要这种粒度。需要时再实现。**
