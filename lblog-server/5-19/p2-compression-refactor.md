# P2 压缩重构记录

> 日期：2026-05-19
> 对应实现：commit `e43ba2e` 之后

---

## 改动概述

基于 CompressionAdvisor + CompressionStrategy 策略模式的审查和重构，核心目标：**策略不感知 token，上层负责预算控制**。

---

## 重构要点

### 1. CompressionStrategy 接口调整

```java
public interface CompressionStrategy {
    boolean shouldCompress(List<Message> messages);
    List<Message> compress(List<Message> messages);

    /** 再压缩一步。上层在 token 仍超预算时循环调用。
     *  各策略自行决定这一步做什么（丢消息、合并、清 reasoning 等）。 */
    List<Message> tryCompress(List<Message> messages);
}
```

`tryDropOne` → `tryCompress`，去掉"丢弃"语义，不同策略可做不同的事。

### 2. 硬截断逻辑移到上层

**之前：** `SlidingWindowStrategy` 内部持有 `TokenEstimator` 做硬截断，策略感知 token。

```
SlidingWindowStrategy.compress(messages, estimator, maxTokens)
  → 按条数裁 + 硬截断兜底（知道 token 预算）
```

**之后：** 策略不碰 token，`CompressionAdvisor` 循环调 `tryCompress` 直到预算内。

```
CompressionAdvisor:
  → strategy.compress(messages)          // 策略按自己规则压
  → while (仍超预算) strategy.tryCompress()  // 上层控制预算
```

### 3. 循环条件修正

```java
int guard = 50;
while (result.size() > 1           // 至少保留 system
    && estimateTokens(result) > maxHistoryTokens  // 仍然超预算
    && guard-- > 0) {              // 防死循环
    result = strategy.tryCompress(result);
}
```

之前用 `result.size() < lastSize` 判断是否还有压缩空间，但某些策略可能减少 token 而不减少条数（如清 reasoning），所以改用 guard 计数器。

### 4. Fix: removeLast → remove(1)

硬截断时从 `removeLast()`（丢最新消息）改为 `remove(1)`（丢最旧的非 system 消息）。

---

## 最终职责

| 组件 | TokenEstimator | 压缩逻辑 |
|------|---------------|---------|
| `CompressionAdvisor` | ✅ | 判断是否压缩、循环调 tryCompress、写回 prompt |
| `CompressionStrategy` | ❌ | 按自己规则压缩/再压缩，不关心 token |

## 文件变更

| 文件 | 变更 |
|------|------|
| `CompressionStrategy.java` | `tryDropOne` → `tryCompress`，完善注释 |
| `SlidingWindowStrategy.java` | 去掉 TokenEstimator 依赖，`tryDropOne` → `tryCompress` |
| `CompressionAdvisor.java` | 去掉硬截断，改为循环调 `tryCompress`，guard 防死循环 |
| `p2-sliding-window.md` | 同步更新架构图和代码示例 |
