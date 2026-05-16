# SSE 心跳检测与线程安全分析

> 基于 `ai/agent/draw/` 模块分析
> 日期：2026-05-16

---

## 一、心跳检测的必要性

### 1.1 心跳的两个作用

| 作用 | 说明 | 当前是否必要 |
|------|------|------------|
| 连接保活 | 防 Nginx/代理因空闲断开 SSE 连接 | **不必要**（Nginx `proxy_read_timeout` 默认 60s，AI 调用通常 3~30s） |
| 断连检测 | `emitter.send()` 失败时触发 `onCompletion`，清理心跳 + 中断 @Async 线程 | **有会更好**（Spring 的 `onCompletion` 在客户端断连时不一定可靠触发） |

### 1.2 断连检测的原理

```
客户端断连
    │
    ├── onCompletion 触发（不可靠，Spring 已知缺陷）
    │
    └── 下次心跳 send() → IOException → 发现断连（可靠兜底）

心跳间隔 5s，最多等 5s 就能检测到断连 → 中断 AI 调用 → 释放线程
```

没有心跳的话，客户端断连后 @Async 线程可能继续空转等待 AI 返回，浪费资源。

### 1.3 结论

心跳当前**有必要保留**，主要价值是断连检测的兜底手段。5s 间隔开销很小（每分钟 12 次空 JSON 发送）。

---

## 二、线程安全问题

### 2.1 SseEmitter 的非线程安全特性

`SseEmitter.send()` 底层写入 `HttpServletResponse` 的输出流，**不是线程安全的**。多线程同时调用会导致数据交错。

### 2.2 当前可能冲突的线程

| 线程 | 发送内容 | 发送时机 |
|------|---------|---------|
| 心跳线程（ScheduledExecutorService） | `data:{}` | 每 5 秒 |
| @Async 虚拟线程 | `data:{"type":"tool-call",...}` | DisplayDiagramTool 执行时（瞬间） |
| @Async 虚拟线程 | `data:{"type":"text-delta",...}` | AI 返回后（一次） |
| @Async 虚拟线程 | `data:{"type":"done",...}` | AI 流程结束后（一次） |

### 2.3 碰撞概率分析

```
时间轴：
│                                                         │
├───── 5s ─────├───── 5s ─────├───── 5s ─────├───── 5s ────┤
心跳           心跳           心跳           心跳
                              │
                          AI 调用（3~30s）
                          ├── tool-call send（微秒级） ← 唯一可能碰撞
                          ├── text-delta send（微秒级）
                          └── done send（微秒级）
```

碰撞条件：心跳发送和 AI 数据发送需要精确重叠在同一毫秒。**概率极低。**

### 2.4 如果碰撞会发生什么

```
理想情况：  data:{"type":"tool-call","arguments":{"xml":"<mxfile>..."}}
碰撞情况：  data:{"type":"tool"}{}data:-call","arguments":{"xml":"<mxfile>..."}
           ↑ 心跳包插在中间
```

前端 `JSON.parse()` 会抛出解析异常，数据丢失。**但实测从未复现过。**

### 2.5 修复方案（如果需要）

```java
// 统一使用同步发送
private void safeSend(SseEmitter emitter, SseEventBuilder data) {
    synchronized (emitter) {
        emitter.send(data);
    }
}
```

将心跳和 AI 发送都通过 `safeSend()` 串行化。

### 2.6 结论

**线程安全当前不是实际问题。** 心跳 5s 间隔 + AI send 次数极少，碰撞窗口接近零。当前渲染问题完全由 `deepseek-v4-flash` 的 400 错误导致（无 `done` 事件），与线程安全无关。
