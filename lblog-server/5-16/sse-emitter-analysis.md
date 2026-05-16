# SseEmitter 完整用法与分析

> 涵盖 Spring MVC SseEmitter 所有特性、方法、回调机制、交互流程
> 日期：2026-05-16

---

## 一、什么是 SseEmitter

**SseEmitter** 是 Spring MVC（基于 Servlet 3.0+ 异步处理）提供的 SSE（Server-Sent Events）推送工具。
它维护一个 HTTP 长连接，服务端可以主动向客户端推送多条数据，格式为 `data:{json}\n\n`。

### 核心特性

| 特性 | 说明 |
|------|------|
| 通信方向 | **单向**：服务器 -> 客户端 |
| 底层协议 | 标准 HTTP，基于 Servlet 3.0 AsyncContext |
| Content-Type | text/event-stream |
| 浏览器支持 | EventSource API 原生支持，自带断线重连 |
| 适用场景 | AI 流式输出、实时通知、日志推送、进度更新 |

---

## 二、构造函数

```java
// 默认超时 30 秒（Tomcat 默认连接超时）
SseEmitter emitter = new SseEmitter();

// 指定超时时间（毫秒），推荐 2-5 分钟
SseEmitter emitter = new SseEmitter(TimeUnit.MINUTES.toMillis(3));

// 永不超时（慎用！长时间连接会积压，导致文件描述符泄漏）
SseEmitter emitter = new SseEmitter(0L);
```

> **规则**：生产环境必须设有限超时，推荐 2-5 分钟。

---

## 三、核心方法

| 方法 | 说明 | 注意事项 |
|------|------|----------|
| send(Object object) | 发送数据，自动序列化为 SSE 格式 | 非线程安全，需串行化调用 |
| send(SseEventBuilder builder) | 使用构建器发送结构化 SSE 事件 | **推荐方式**，支持事件名/ID/重连时间 |
| complete() | 正常结束连接 | 触发 onCompletion 回调 |
| completeWithError(Throwable ex) | 异常结束连接 | 触发 onError -> onCompletion |

---

## 四、SseEventBuilder（事件构建器）

**强烈推荐**使用构建器而非直接 `send(String)`。

```java
// 构建完整 SSE 事件
emitter.send(SseEmitter.event()
    .id("msg-123")            // 事件 ID，断线重连时客户端会传回 Last-Event-ID
    .name("update")           // 事件名，前端可用 addEventListener("update") 监听
    .data("some data")        // 事件数据（自动序列化）
    .reconnectTime(3000)      // 客户端重连间隔（毫秒）
    .comment("heartbeat")     // 注释行（以 : 开头，前端忽略）
);

// 发送 JSON 对象
emitter.send(SseEmitter.event()
    .name("progress")
    .data(Map.of("percent", 50, "status", "processing"))
);
```

### SseEventBuilder 方法与对应 SSE 协议

| 构建器方法 | SSE 协议输出 | 前端接收方式 |
|-----------|-------------|-------------|
| .id("123") | `id: 123` | event.lastEventId |
| .name("update") | `event: update` | es.addEventListener("update", fn) |
| .data("text") | `data: text` | event.data |
| .reconnectTime(3000) | `retry: 3000` | 自动生效 |
| .comment("ping") | `: ping` | 忽略（注释行） |

---

## 五、生命周期回调（3 个）

### 5.1 方法定义

```java
// 连接完成时触发（任何原因：正常/异常/超时/断连）
emitter.onCompletion(() -> {
    // 清理业务资源
});

// 超时时触发
// 注意：不会自动调用 complete()，必须手动调用
emitter.onTimeout(() -> {
    emitter.complete();  // 必须手动 complete
});

// 异常时触发
// 注意：不会自动调用 completeWithError()，必须手动调用
emitter.onError(ex -> {
    emitter.completeWithError(ex);  // 必须手动 completeWithError
});
```

### 5.2 触发顺序

```
正常完成:  complete()
             -> onCompletion 触发

异常结束:  completeWithError(e)
             -> onError(e) 触发
             -> onCompletion 触发

超时结束:  构造函数指定的超时时间到达
             -> onTimeout 触发   (不会自动 complete)
             -> [手动调用 emitter.complete()]
             -> onCompletion 触发

客户端断连: 浏览器关闭连接
             -> 下次 emitter.send() 抛出 IOException
             -> Spring 内部清理
             -> onCompletion 触发
```

### 5.3 重要约束

| 回调 | 自动 complete? | 必须做的事 |
|------|---------------|-----------|
| onTimeout | 不自动 | 必须调 emitter.complete() 释放资源 |
| onError | 不自动 | 必须调 emitter.completeWithError(e) |
| onCompletion | - | 清理业务资源 |

不调 complete/completeWithError 会导致连接资源泄漏。

---

## 六、客户端断连检测

### 6.1 核心问题

**客户端断连时，onCompletion 不一定可靠触发。** 这是 Spring 社区的已知问题：

- Issue #17882: "onCompletion SseEmitter callback never gets called"
- Issue #21091: "Improve docs and handling of send errors"

原因：SSE 是单向协议。服务端不主动写数据时，无法感知客户端已断开。
只有下一次 `emitter.send()` 操作失败（抛出 IOException）时才能检测到。

### 6.2 可靠检测方式

```java
// 唯一可靠的断连检测：捕获 send() 的 IOException
private void trySend(SseEmitter emitter, SseEventBuilder event) {
    try {
        emitter.send(event);
    } catch (IOException e) {
        // 客户端已断开，清理业务缓存
        // 不要调 completeWithError(e)，容器会处理
        removeFromCache(id);
    }
}
```

### 6.3 心跳的双重作用

心跳不仅保活，还承担断连探测职责：

```java
// 每 15 秒发送一次
// 如果客户端已断开，send() 会抛出 IOException，从而触发后续清理
heartbeatScheduler.scheduleAtFixedRate(() -> {
    try {
        emitter.send(SseEmitter.event().comment("heartbeat"));
    } catch (IOException e) {
        // 检测到断连
    }
}, 15, 15, TimeUnit.SECONDS);
```

心跳间隔越短，断连检测越快。

---

## 七、线程安全

**SseEmitter 不是线程安全的。** 多线程同时调用 `send()` 会导致数据交错。

```java
// 需要串行化发送
synchronized (emitter) {
    emitter.send(event);
    emitter.send(event2);
}
```

当前项目中不存在并发 send 问题：
- 心跳独占一个调度线程
- @Async 线程只在 AI 返回后发一次 text-delta + done
- 两个线程不会同时 send

---

## 八、完整交互流程

### 8.1 正常流程

```
Controller                     @Async 线程                     客户端
  |                               |                             |
  |-- new SseEmitter(3min)        |                             |
  |-- onTimeout(complete)         |                             |
  |-- chatStream(request,em) -----|                             |
  |-- return emitter              |                             |
  |                               |                             |
  |                               |-- 心跳启动 (每15秒)         |
  |                               |-- AI call (阻塞)            |
  |                               |       |                     |
  |                               |  <--- AI 返回               |
  |                               |-- send(text-delta) ---------|
  |                               |-- send(done) --------------|
  |                               |-- complete()               |
  |                               |-- onCompletion             |
  |                               |     -- heartbeat.cancel()  |
```

### 8.2 客户端断连流程

```
@Async 线程                心跳线程                   客户端
  |                          |                        |
  |-- AI call (阻塞)         |                        |
  |                          |                        |-- [关闭页面]
  |                          |-- send(heartbeat)      |
  |                          |   IOException          |
  |                          |                        |
  |-- onCompletion 触发      |                        |
  |     -- heartbeat.cancel()|                        |
  |     -- thread.interrupt()|                        |
  |        |                                          |
  |    AI HTTP 中断 --> catch --> 退出                 |
```

### 8.3 超时流程

```
new SseEmitter(3min)
  |
  |-- @Async 线程 AI 调用超过 3 分钟
  |
  |-- onTimeout 触发
  |     -- emitter.complete()
  |            |
  |-- onCompletion 触发
  |     -- heartbeat.cancel()
  |     -- thread.interrupt()
```

---

## 九、当前项目用法分析

### 9.1 实现总结

| 组件 | 类 | 职责 |
|------|-----|------|
| 创建 | DiagramController.chat() | 创建 SseEmitter，注册 onTimeout |
| 发送 | DiagramService.chatStream() | @Async 线程发送 text-delta + done |
| 心跳 | heartbeatScheduler | 每 15 秒发 heartbeat |
| 工具 call | DisplayDiagramTool.execute() | 通过 ToolContext 获取 emitter 发 tool-call |

### 9.2 事件类型

| 事件 | 发送方式 | 当前问题 |
|------|---------|---------|
| heartbeat | send(Map.of("type", "heartbeat")) | 未使用 SseEventBuilder |
| text-delta | send(Map.of("type", "text-delta", ...)) | 同上 |
| tool-call | send(Map.of("type", "tool-call", ...)) | 同上 |
| done | send(Map.of("type", "done", ...)) | 同上 |
| error | completeWithError(e) | 前端 onerror 接收 |

### 9.3 与最佳实践的差距

| 最佳实践 | 当前项目 | 建议 |
|---------|---------|------|
| 使用 SseEventBuilder | 手动 Map.of() | 改用 event().name().data() |
| 命名事件区分 | 全部默认 message 事件 | 前端可用 addEventListener 区分 |
| 设置 .id() | 未设置 | 支持断线重连 |
| onError 回调 | 未注册 | 注册用于日志 |
| send IOException 处理 | 未捕获 | 捕获用于断连检测 |

---

## 十、常见坑与最佳实践

### 必须做的 6 件事

1. 设置有限超时：`new SseEmitter(300_000L)`
2. 注册所有 3 个回调：onCompletion / onTimeout / onError
3. onTimeout/onError 中调 complete/completeWithError
4. 异步发送数据：@Async 或线程池，不阻塞 Tomcat 线程
5. send() 包 try-catch：捕获 IOException 检测断连
6. 心跳保活 + 断连探测

### 不要做的 5 件事

1. `new SseEmitter(0L)` 永不超时
2. onTimeout/onError 不调 complete
3. 多线程并发 send
4. Controller 中 Thread.sleep
5. IOException 后调 completeWithError

### SseEmitter vs WebSocket vs ResponseBodyEmitter

| 场景 | SseEmitter | WebSocket | ResponseBodyEmitter |
|------|-----------|-----------|-------------------|
| 通信方向 | 单向 | 双向 | 单向 |
| 协议 | HTTP | WS | HTTP |
| 自动重连 | 浏览器内置 | 需手动实现 | 无 |
| 适用场景 | AI 流式、通知 | 聊天、游戏 | 文件下载 |

---

## 十一、前端接收

### EventSource API（推荐）

```javascript
const es = new EventSource("/api/v1/draw/chat");

es.onopen = () => console.log("SSE 连接已建立");

es.onmessage = (event) => {
    const data = JSON.parse(event.data);
    switch (data.type) {
        case "text-delta": appendText(data.delta); break;
        case "tool-call": renderDiagram(data.arguments); break;
        case "done": es.close(); break;
        case "heartbeat": break;  // 忽略心跳
    }
};

es.onerror = (err) => console.error("SSE 连接错误", err);

// 关闭连接
// es.close();
```

### 使用命名事件（改进方向）

如果后端改用 `SseEventBuilder` 发送命名事件：

```java
// 后端
emitter.send(SseEmitter.event().name("delta").data(text));
emitter.send(SseEmitter.event().name("tool").data(wrappedXml));
emitter.send(SseEmitter.event().name("done").data(sessionId));
emitter.send(SseEmitter.event().comment("heartbeat"));
```

```javascript
// 前端可以按事件类型分别监听
es.addEventListener("delta", (e) => appendText(e.data));
es.addEventListener("tool", (e) => renderDiagram(JSON.parse(e.data)));
es.addEventListener("done", (e) => es.close());
// heartbeat 作为注释行，前端自动忽略
```
