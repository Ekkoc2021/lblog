# AI 绘图对话 — Assistant 消息持久化修复

> 状态：已完成 | 日期：2026-05-26

## 一、问题

1. **简单对话**：`ai_chat_messages` 表只有 `role='user'` 的行，没有 `role='assistant'` 的行
2. **工具调用**：触发 tool call 的绘图请求 400 错误，assistant 消息从未保存

## 二、根因

### Bug 1 — 流式持久化依赖不可靠的 Reactor 回调

原代码在 `ChatHistoryAdvisor.adviseStream()` 里用 `doOnNext(chunks::add) + doOnComplete(save)` 保存 assistant 消息。

`after()` 方法只对非流式 `.call()` 生效，`DrawController` 永远走 `chatStream()` 流式路径，所以 `after()` 不会执行。

### Bug 2 — 压缩破坏 tool 消息配对

`DeepSeekToolCallAdvisor.adviseStream()` 递归调用 `reasoningLoop()` 时，会调 `doBeforeStream()`，其中 `CompressionAdvisor.before()` 对递归传递的消息再次压缩。递归消息来自 `conversationHistory()`：

```
[SystemMessage(~3500 tokens)] [User] [Assistant(tool_calls)] [ToolResponse]
```

SystemMessage 自身就占 3500+ tokens，加上其他消息远超 `maxHistoryTokens(4000)`。`compressIfNeeded()` 的 while 循环不断调 `tryCompress()` 逐条删除最旧消息：

```
原始:     [User] [Assistant(tool_calls)] [ToolResponse]
第1次:    [Assistant(tool_calls)] [ToolResponse]    ← 删 User，OK
第2次:    [ToolResponse]                            ← 删 Assistant，孤儿!
```

发给 DeepSeek: `[System] [ToolResponse]` → **400 "Messages with role 'tool' must be a response to a preceding message with 'tool_calls'"**

Flux 异常 → `forEach` 抛异常 → `catch` 块 `return` → `saveAssistantMessage` 被跳过。

## 三、Draw 完整流程

```
DrawController.chat()
  → DrawService.chatStream()                    @Async("diagramTaskExecutor")
    → ChatClient.prompt()...stream().toStream().forEach(...)
      │
      ├─ ChatHistoryAdvisor.adviseStream()        order: HIGHEST_PRECEDENCE
      │    before: 加载历史 → 保存 user 消息 → 拼入 [System][History][User]
      │    → chain.nextStream() ──────────────────────────────┐
      │                                                        │
      ├─ DeepSeekToolCallAdvisor.adviseStream()  ←────────────┘  order: +2
      │    doInitializeLoopStream() │
      │    → 走 before 链 → CompressionAdvisor.before() ①       │
      │    reasoningLoop():         │                            │
      │      doBeforeStream()       │                            │
      │      → 走 before 链 → CompressionAdvisor.before() ②     │
      │      chainCopy.nextStream() ──────────────────────────┐ │
      │                                                        │ │
      ├─ CompressionAdvisor.adviseStream() ←──────────────────┘ │  order: +3
      │    compressIfNeeded() → 超过 4000 tokens 则压缩          │
      │    → 发给 Model                                         │
      │                                                         │
      ├─ Model 返回 AssistantMessage(tool_calls)                 │
      │    reasoningLoop 递归 ────────────────────────────┐      │
      │    ┌─────────────────────────────────────────────┘      │
      │    │ doBeforeStream():                                  │
      │    │ → CompressionAdvisor.before() ③  ← 第3次压缩       │
      │    │   消息: [System, User, Assistant(tc), ToolResp]     │
      │    │   tokens > 4000 → 循环 tryCompress                 │
      │    │   最终 ToolResponse 变孤儿 → 400 错误               │
      │    │                                                    │
      │    └── Flux error → forEach 抛异常 → catch return        │
      │                                                         │
      └── forEach 返回 → saveAssistantMessage()  ← 修复后在此保存
```

### 三次接触压缩

| # | 位置 | 场景 |
|---|------|------|
| ① | `doInitializeLoopStream` | 初始化请求 |
| ② | `doBeforeStream` (首次) | 第一次调模型前 |
| ③ | `doBeforeStream` (递归) | 工具执行后递归调模型前 ← **问题所在** |

## 四、修复

### 修复 1 — 持久化移至 DrawService

| 文件 | 改动 |
|------|------|
| `DrawService.java` | 注入 `ChatMessageService`；`chatStream()` 用 `StringBuilder` 累积 content + reasoning；`forEach` 返回后调 `saveAssistantMessage()` |
| `ChatHistoryAdvisor.java` | `adviseStream()` 删除 `doOnNext/doOnComplete` 保存逻辑（约 25 行），只保留历史注入和 user 消息保存 |

**关键原理**：`forEach` 是阻塞订阅，等待整个 Flux（含 `concatWith` 递归段）完成后才返回。在 `forEach` 返回后、`transport.send("done")` 之前保存，内容已完整。

### 修复 2 — 压缩保护 tool 消息对

| 文件 | 改动 |
|------|------|
| `SlidingWindowStrategy.java` | `tryCompress`：删除带 `tool_calls` 的 Assistant 时，一并删除紧随的 ToolResponse；`compress`：保留区第一条是 tool 消息时往回找对应 Assistant |

```java
// tryCompress
Message removed = result.removeFirst();
if (removed instanceof AssistantMessage am && am.hasToolCalls()) {
    while (!result.isEmpty() && result.getFirst().getMessageType() == MessageType.TOOL) {
        result.removeFirst();
    }
}
```

## 五、不需要改的文件

| 文件 | 原因 |
|------|------|
| `DeepSeekToolCallAdvisor.java` | 不涉及持久化，消息格式正确 |
| `DeepSeekMessageConverter.java` | `toStorageMessageFromChunks` 仍可用于非流式路径 |
| `CompressionAdvisor.java` | 问题不在它，而在 `tryCompress` 的实现 |
| 前端 | 接口和数据结构不变 |

## 六、验证结果

| 场景 | 结果 |
|------|------|
| 简单对话 `say hello` | user + assistant 都持久化，content + reasoning 完整 |
| 工具调用 `draw a login flow` | tool call → 递归推理成功 → 完整回复 → assistant 持久化 |
| SSE 输出 | 文本增量 + reasoning + tool-call + done 事件完整 |
| 错误日志 | 无 400 错误 |

## 七、数据库验证

```sql
-- Session 42（工具调用场景）
SELECT id, session_id, role, LEFT(content, 60), msg_index
FROM ai_chat_messages WHERE session_id = 42 ORDER BY msg_index;

-- 结果：
-- id=47, role=user,      content="draw a simple login flow with 2 steps"
-- id=48, role=assistant,  content="I'll lay out a left-to-right login flow..."
```
