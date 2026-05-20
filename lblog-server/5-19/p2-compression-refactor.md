# P2 压缩重构记录

> 日期：2026-05-19
> 对应实现：commit `e43ba2e` 之后

---

## 改动概述

基于 CompressionAdvisor + CompressionStrategy 策略模式的审查和重构，核心目标：**策略不感知 token，上层负责预算控制**。

---

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

    /** compress/tryCompress 不保证传入的 messages 包含 system 消息，
     *  策略无需做首尾保护（由 CompressionAdvisor 负责）。 */
    List<Message> compress(List<Message> messages);
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
  → 剥离 system → strategy.compress(nonSystem)  → 补回 system
  → while (仍超预算) → 剥离 system → strategy.tryCompress(nonSystem) → 补回 system
```

### 3. 保护逻辑统一在 CompressionAdvisor

**之前：** 保护分散在三个地方：
- `compress()` 保留 `getFirst()`（策略层）
- `tryCompress()` 检查 `size <= 1`（策略层）
- `CompressionAdvisor` 检查 `size > 1`（重复）

**之后：** 全部集中在 CompressionAdvisor：

```java
// 1. 拆分 system，策略只操作非 system 部分
Message system = messages.getFirst();
List<Message> target = messages.subList(1, messages.size());

// 2. 策略压缩
List<Message> compressed = compressionStrategy.compress(target);

// 3. 补回 system
List<Message> result = new ArrayList<>();
result.add(system);
result.addAll(compressed);

// 4. 循环 tryCompress，保证至少保留 system
while (result.size() > 1 && estimateTokens(result) > maxHistoryTokens && guard-- > 0) {
    List<Message> after = compressionStrategy.tryCompress(result.subList(1, result.size()));
    if (after.isEmpty() || after.size() >= result.size() - 1) break;
    result = new ArrayList<>();
    result.add(system);
    result.addAll(after);
}
```

### 4. 循环条件修正

```java
int guard = 50;
while (result.size() > 1           // 至少保留 system
    && estimateTokens(result) > maxHistoryTokens  // 仍然超预算
    && guard-- > 0) {              // 防死循环
    result = strategy.tryCompress(result);
}
```

之前用 `result.size() < lastSize` 判断是否还有压缩空间，但某些策略可能减少 token 而不减少条数（如清 reasoning），所以改用 guard 计数器。

### 5. Fix: removeLast → remove(1)

硬截断时从 `removeLast()`（丢最新消息）改为 `remove(1)`（丢最旧的非 system 消息）。

---

## 最终职责

| 组件 | TokenEstimator | 消息保护 | 压缩逻辑 |
|------|---------------|---------|---------|
| `CompressionAdvisor` | ✅ | ✅ 拆分/补回 system，保证至少保留 1 条 | 判断时机、循环调策略 |
| `CompressionStrategy` | ❌ | ❌ 不感知 system 位置 | 只回答"怎么压缩一步" |

## 文件变更

| 文件 | 变更 |
|------|------|
| `CompressionStrategy.java` | `tryDropOne` → `tryCompress`，完善注释说明策略不感知 system |
| `SlidingWindowStrategy.java` | 去掉 `getFirst()` 保护和 `size <= 1` 检查，只管按条数裁 |
| `CompressionAdvisor.java` | 加入 system 剥离/补回逻辑，所有保护集中在 advisor |
| `p2-sliding-window.md` | 同步更新架构图和代码示例 |
