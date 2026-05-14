# QA Review: AI Draw.io Integration

> Author: QA Engineer  
> Date: 2026-05-13  
> Based on: `5-13/next-ai-draw-io-analysis.md`, reference `next-ai-draw-io/` source code

---

## 1. API Contract Review

### 1.1 Proposed SSE Endpoint

```
POST /iblogserver/api/v1/draw/chat
Content-Type: application/json
Accept: text/event-stream
```

**Request Body:**

```json
{
  "messages": [
    { "role": "user", "parts": [{ "type": "text", "text": "画一个微服务架构图" }] }
  ],
  "xml": "",
  "previousXml": "",
  "sessionId": "optional-session-uuid"
}
```

**SSE Response Format (text/event-stream) — PM Finalized:**

```
event: message
data: {"type":"text-delta","delta":"I'll create a microservice architecture diagram..."}

event: message
data: {"type":"tool-call","toolCallId":"call_xxx","toolName":"display_diagram","input":{"xml":"<mxCell id=\"2\"...完整XML一次性给出..."}}

event: message
data: {"type":"done","sessionId":"uuid","usage":{"promptTokens":500,"completionTokens":1200}}

// 每15s:
event: message
data: {"type":"heartbeat"}

event: error
data: {"type":"error","code":"AI_ERROR","message":"上游 AI 服务返回异常"}
```

### 1.2 Issues Found (Updated for Final SSE Format)

| # | Issue | Severity | Recommendation |
|---|-------|----------|----------------|
| 1 | ~~tool-end xml 不完整~~ — 新格式 `tool-call` 一次性给完整 XML，已解决 | **RESOLVED** | — |
| 2 | **缺少重试机制标识** — SSE 断连后前端无法判断能否安全重试。`start` 事件已移除，需另寻载体 | **Medium** | 可在 `tool-call` 事件中加 `retryable: true/false`，或在响应头加 `X-Retryable: true` |
| 3 | **error 事件格式不统一** — 没有标准 error code 枚举 | **Medium** | 定义统一 error code：`AI_UNAVAILABLE`、`AI_TIMEOUT`、`RATE_LIMITED`、`TOKEN_LIMIT`、`INVALID_REQUEST`、`INTERNAL_ERROR` |
| 4 | **缺少 heartbeat** — 长时间无响应的 AI 请求可能导致前端 Nginx/浏览器超时断开 | **Medium** | 每 15s 发一次 `{"type":"heartbeat"}` 事件，保持连接活跃 |
| 5 | **messages 数组中缺少 system role** — 请求体中未定义 system message 如何传递 | **Medium** | 明确约定：system prompt 由后端拼接，前端只需传 user/assistant messages。如果前端需要自定义 system message，加可选字段 `customSystemMessage` |
| 6 | ~~tool-start 缺少 expectedTokens~~ — 新格式无 tool-start，已不适用 | **RESOLVED** | — |
| 7 | **未定义 cancel 机制** — 用户如何中断正在生成的请求？ | **Medium** | 前端通过 `AbortController` 断开 SSE 即可取消。后端需要检测 `SseEmitter.onCompletion()` 或 request 取消信号并中断 AI 调用 |
| 8 | **sessionId 用途不明** — 目前后端无会话持久化，传了也白传 | **Low** | v1 阶段忽略 sessionId；v2 再做上下文管理 |
| 9 | **tool-call 中无重试标识** — 断连后重试时可能重复消耗 AI 配额 | **Medium** | 后端需保证同 sessionId 的重复请求不会重复调用 AI（幂等性），或前端收到 tool-call 后标记为不可重试 |

### 1.3 边界情况检查

| 场景 | 预期行为 | 当前设计覆盖 |
|------|---------|-------------|
| 空 messages 数组 | 返回 400 | 未定义 |
| messages 中没有 user 消息 | 返回 400 | 未定义 |
| xml 超长（>50KB） | 后端截断或返回 413 | 未定义 |
| sessionId 超长（>200 chars） | 后端忽略或截断 | 未定义 |
| 并发请求同一 IP | 限流返回 429 或排队 | 未定义 |
| AI 返回空 xml | 返回 error 事件 | 未定义 |
| AI 响应超时（>120s） | 关闭 SSE，发超时 error | 未定义 |

---

## 2. Testing Strategy

### 2.1 Test Pyramid

```
        ┌──────────┐
        │   E2E    │  ← Browser-level: Open page → Type prompt → See diagram rendered
        │  (1-2)   │    Tool: Playwright/Cypress
       ┌┴──────────┴┐
       │ Integration │  ← Backend: SSE endpoint with mock AI
       │   (5-8)    │    Frontend: SSE consumer + DrawChatModal
      ┌┴────────────┴┐
      │  Unit Tests  │  ← JsoupValidator, PromptManager, ToolSchemaBuilder
      │   (10-15)    │    XML repair, XML validation, Prompt concatenation
      └──────────────┘
```

### 2.2 Backend: Unit Tests

| Class/Component | Why Unit Test? | Key Scenarios |
|----------------|---------------|---------------|
| **JsoupValidator** | Pure logic: parse XML, check structure, no I/O | Valid XML, malformed XML, nested mxCell, truncated XML, missing attributes, special characters |
| **PromptManager** | String concatenation + template rendering, no I/O | Normal prompt, with custom instructions, with XML context, empty XML context, minimal style |
| **ToolSchemaBuilder** | JSON schema generation, deterministic | display_diagram schema, edit_diagram schema, all 4 tools combined |
| **MxCellRepair** | Rule-based string transformation, no I/O | Fix duplicate attributes, fix missing parent, fix illegal entities, fix truncated cells |
| **DrawRequestValidator** | Input validation rules | Empty messages, null fields, xml too long, invalid role values |
| **SseEventFormatter** | SSE event string formatting | Text delta, tool start/delta/end/error events, heartbeat, finish |

### 2.3 Backend: Integration Tests

| Test | What It Covers | Tooling |
|------|---------------|---------|
| **POST /draw/chat — 正常流** | SSE 事件序列完整 | `@SpringBootTest` + `TestRestTemplate` + `MockWebServer` (mock AI API) |
| **POST /draw/chat — AI 超时** | SSE 收到 timeout error | Mock AI 挂起直到超时 |
| **POST /draw/chat — AI 返回空 tool call** | SSE error 事件 | Mock AI 返回空 choices |
| **POST /draw/chat — AI 返回无效 JSON** | SSE error 事件 + 日志 | Mock AI 返回非法 JSON |
| **POST /draw/chat — 请求体不合法** | HTTP 400 | 空 body、缺少必填字段 |
| **POST /draw/chat — 并发限流** | HTTP 429 | 发 N+1 并发请求 |
| **SseEmitter 连接断开** | AI 调用中断 | `AbortController` → 后端 cancel |

**Mock AI Server Strategy:**

```java
// 使用 okhttp3.mockwebserver 模拟 OpenAI API
MockWebServer mockAiServer = new MockWebServer();
// 返回 SSE 格式的 stream response
mockAiServer.enqueue(new MockResponse()
    .setBody("data: {\"choices\":[{\"delta\":{\"content\":\"test\"}}]}\n\n")
    .setHeader("Content-Type", "text/event-stream"));
```

### 2.4 Frontend: Component Tests

| Component | What To Test | Tooling |
|-----------|-------------|---------|
| **DrawChatModal** | Renders correctly, prop change handling | Vitest + React Testing Library |
| **SSE Consumer (useDrawChat)** | Event sequence parsing, error handling, reconnection | Vitest + fake timers |
| **XmlViewer / DiagramPreview** | Loading state, error state, empty state | Vitest + RTL |
| **DrawChatModal — 交互** | Input change, submit, cancel | Vitest + RTL |

### 2.5 Frontend: Integration Tests

| Test | What It Covers |
|------|---------------|
| **SSE 事件流消费** | 收到 text-delta(0..N次) → tool-call(1次) → done 事件序列 |
| **SSE 错误恢复** | 收到 error 事件后显示错误提示，允许用户重试 |
| **SSE 断连重试** | 连接断开后 3s/5s/10s 指数退避重试 |
| **取消请求** | AbortController 触发 abort → 连接关闭 |
| **XML 校验 + 渲染** | 收到 tool-call 中的完整 XML 后调用 draw.io 渲染接口 |

### 2.6 E2E Tests

| Scenario | Steps | Tool |
|----------|-------|------|
| **完整绘图流程** | 打开页面 → 输入"画一个登录流程图" → 等待 AI 返回 → 查看 draw.io 画布上渲染出图表 | Playwright |
| **多轮对话** | 第一轮生成 → 输入"改成邮箱验证码登录" → 第二轮生成 → 画布更新 | Playwright |
| **错误恢复** | 网络断开 → 显示错误 → 重试 → 恢复 | Playwright (network mocking) |

---

## 3. Risk Points Analysis

### 3.1 AI Output Instability (HIGH)

**问题：** AI 输出的 XML 语法错误、结构不对、id 重复、特殊字符未转义。

**影响：** 前端 draw.io 渲染失败，用户看到空白画布或乱码。

**当前防御措施：**
- 第 1 层：XML 语法校验（DOMParser）
- 第 2 层：自动修复（autoFixXml）
- 第 3 层：截断检测（isMxCellXmlComplete）
- 第 4 层：VLM 视觉校验（v3）

**QA 建议：**
- v1 时必须实现第 1、2、3 层。第 4 层可以推迟。
- **额外建议：** 增加统计埋点，记录生成失败的次数和修复成功率。如果修复成功率 < 80%，预警提示。
- **兜底策略：** 校验失败后，向前端发一个 `warn` 类型事件（非 error），把"修复后的 XML"给前端，让用户决定是否重试。

### 3.2 SSE Connection Drop (HIGH)

**问题：** SSE 连接可能因网络波动、Nginx 超时、浏览器休眠、后端重启等原因中断。

**影响：** 用户等待半天的生成结果丢失，必须重新输入。

**当前设计：** 无。

**QA 建议：**
- **重连策略：** 前端实现指数退避重连（1s → 2s → 4s → 8s → max 30s），最多重试 5 次。
- **断连续传：** 如果连接在 AI 响应到达前断开，前端无法续传（因为 AI 调用已消费）。v2 才考虑 append_diagram 续传。
- **至少做到：** 断连后显示"连接已断开，请重试"，保留用户输入。
- **Nginx 配置：** 需要确保 Nginx 的 `proxy_read_timeout` 不小于 AI 最大响应时间（如 120s）。

### 3.3 Token Exhaustion / Rate Limit (MEDIUM)

**问题：** OpenAI/第三方 API 有 token 配额限制或 rate limit。

**影响：** 用户触发了配额限制，SSE 收到错误，体验中断。

**当前设计：** 无。

**QA 建议：**
- **主动告知：** 收到 rate limit 错误时，SSE 发 `{"type":"error","code":"RATE_LIMITED","message":"请求过于频繁，请 30 秒后重试"}`。
- **配额提示：** 如果 API 提供商返回了剩余配额信息，可以下发给前端显示。
- **后端限流：** 在 Controller 层加 Spring `@RateLimiter` 或 Filter 限流（每 IP 每分钟最多 10 次），防止恶意刷接口。

### 3.4 AI Provider API Changes (LOW)

**问题：** 使用的 AI API（如 OpenAI）的 function calling 接口变更。

**影响：** tool call 解析失败，空响应或报错。

**当前设计：** 依赖具体的 OpenAI JSON 响应结构。

**QA 建议：**
- 将 AI 提供商适配层（`AiProviderAdapter`）设为接口，v1 只实现 OpenAI，以后可扩展。
- 集成测试中使用 mock AI server，不依赖真实 API，这样 API 变更影响可控。
- 加版本号检测：如果 AI API 返回的 `object` 类型不符合预期，走错误处理路径。

### 3.5 Large Payload / Memory (LOW)

**问题：** AI 可能生成数万行的 XML（如 AWS 架构图），一次性返回导致前端 OOM。

**影响：** 前端内存溢出，白屏或崩溃。

**当前设计：** XML 大小限制 16000 tokens ≈ ~50KB。

**QA 建议：**
- v1 不做客户端分片渲染，但要在后端限制 XML 最大长度（如 100KB）。
- 如果超过限制，发 `{"type":"warn","code":"XML_TOO_LARGE","message":"图表过于复杂，已截断..."}`。

### 3.6 Security: Prompt Injection (MEDIUM)

**问题：** 用户输入可能包含 prompt injection payload，试图操纵 AI 行为。

**影响：** AI 可能输出恶意内容或泄露系统 prompt。

**当前设计：** 无专门防护。

**QA 建议：**
- v1 至少做到：用户输入用 `"""md\n${input}\n"""` 包裹，与 system prompt 隔离（参考原实现）。
- 服务端对用户输入做长度限制（如 5000 字符）。
- 不对 AI 输出做内容安全检测（v1 不做），但加一个 `Content-Security-Policy` 头防止 XSS。

---

## 4. Test Cases Draft

### 4.1 Backend Unit Tests: JsoupValidator

```
TC-UT-01: validateMxCellStructure — 合法完整 XML → 返回 valid
TC-UT-02: validateMxCellStructure — id 重复 → 返回 invalid + 错误信息
TC-UT-03: validateMxCellStructure — mxCell 嵌套 mxCell → 返回 invalid
TC-UT-04: validateMxCellStructure — XML 语法错误 → 返回 invalid
TC-UT-05: validateMxCellStructure — 空字符串 → 返回 invalid
TC-UT-06: autoFixXml — 缺少 parent 属性 → 修复后添加 parent="1"
TC-UT-07: autoFixXml — 非法 XML 实体 → 修复为合法实体
TC-UT-08: autoFixXml — 重复 attribute → 去重保留最后一个
TC-UT-09: autoFixXml — 截断的 mxCell 标签 → 补全闭合标签（如可能）
TC-UT-10: isMxCellXmlComplete — 完整 XML → true
TC-UT-11: isMxCellXmlComplete — 截断 XML → false
TC-UT-12: wrapWithMxFile — 裸 mxCell → 返回完整 mxfile
```

### 4.2 Backend Unit Tests: PromptManager

```
TC-UT-20: getSystemPrompt — 默认样式 → 包含角色设定 + 布局约束
TC-UT-21: getSystemPrompt — minimal 样式 → 无颜色样式指令
TC-UT-22: getSystemPrompt — 带 customSystemMessage → 追加 Custom Instructions
TC-UT-23: getSystemPrompt — customSystemMessage 超长 → 截断到 5000 字符
TC-UT-24: buildXmlContext — 有 previousXml → 两条上下文
TC-UT-25: buildXmlContext — xml 为空 → "当前图表 XML: (空)"
TC-UT-26: buildMessages — 历史消息替换 → 旧 tool call XML 被占位符替换
```

### 4.3 Backend Integration Tests: SSE Endpoint

```
TC-IT-01: POST /draw/chat — 正常流，AI 返回 display_diagram
  → 预期: 收到 text-delta(0..N次) → tool-call(1次) → done
  → 验证: tool-call 中的 xml 是完整可解析的 mxCell（一次性完整给出）
  → 验证: 如果响应时间超过15s，期间应收到至少1次 heartbeat

TC-IT-02: POST /draw/chat — AI 返回空 choices
  → 预期: 收到 error 事件, code=AI_ERROR

TC-IT-03: POST /draw/chat — AI 返回无效 JSON
  → 预期: 收到 error 事件, code=AI_ERROR

TC-IT-04: POST /draw/chat — AI 服务超时
  → 预期: 收到 error 事件, code=AI_TIMEOUT

TC-IT-05: POST /draw/chat — 请求体缺少 messages
  → 预期: HTTP 400

TC-IT-06: POST /draw/chat — messages 数组为空
  → 预期: HTTP 400

TC-IT-07: POST /draw/chat — xml 参数超过 100KB
  → 预期: HTTP 413 或截断

TC-IT-08: POST /draw/chat — 并发请求限流
  → 预期: 第 N+1 个请求 HTTP 429

TC-IT-09: POST /draw/chat — SSE 连接断开
  → 预期: 后端检测到 SseEmitter timeout/completion，取消 AI 调用

TC-IT-10: POST /draw/chat — AI 返回 edit_diagram (v2)
  → 预期: tool-call 事件中 toolName="edit_diagram"，input 包含 operations 数组
```

### 4.4 Frontend Tests (Updated for Final SSE Format)

```
TC-FE-01: SSEConsumer — 收到 text-delta → content 累加显示
TC-FE-02: SSEConsumer — 收到 tool-call → 从中提取完整 XML → 调 renderDiagram
TC-FE-03: SSEConsumer — 收到 done → state 切换到 idle
TC-FE-04: SSEConsumer — 收到 error → state 切换到 error
TC-FE-05: SSEConsumer — 收到 heartbeat → 无状态变化（仅保活）
TC-FE-06: SSEConsumer — 连接中断 → 自动重试（指数退避，max 5次）
TC-FE-07: SSEConsumer — 取消请求 → AbortController.abort() → 连接关闭
TC-FE-08: DrawChatModal — 空输入提交 → 按钮 disabled
TC-FE-09: DrawChatModal — loading 状态 → 输入框 disabled + 显示取消按钮
TC-FE-10: SSEConsumer — 收到 tool-call 前断连 → 重试后发新请求（不幂等）
```

### 4.5 E2E Tests

```
TC-E2E-01: 正常绘图流程
  1. 打开 AI 绘图页面
  2. 在聊天输入框输入"画一个登录流程图"
  3. 发送
  4. 等待 SSE 流式响应
  5. 验证 draw.io 画布渲染出登录流程图
  6. 验证图表节点包含"登录"相关文字

TC-E2E-02: 多轮对话 — 增量修改（v2）
  1. 第一轮生成架构图
  2. 输入"把数据库改成 MySQL"
  3. 等待第二次生成
  4. 验证画布更新，数据库节点标签变为 MySQL

TC-E2E-03: 错误恢复
  1. 开始生成图表
  2. 中断连接
  3. 验证显示错误提示
  4. 点击重试
  5. 验证重新发送请求
```

---

## 5. 测试数据 / Mock 建议

### 5.1 Mock Backend-to-Frontend SSE Events (Final Format)

正常流（后端发给前端的 SSE 事件序列，无 text-delta 时）：

```
event: message
data: {"type":"text-delta","delta":"I'll create a login flow diagram..."}

event: message
data: {"type":"tool-call","toolCallId":"call_abc123","toolName":"display_diagram","input":{"xml":"<mxCell id=\"2\" value=\"登录\" style=\"rounded=1;whiteSpace=wrap;\" vertex=\"1\" parent=\"1\"><mxGeometry x=\"40\" y=\"40\" width=\"120\" height=\"40\" as=\"geometry\"/></mxCell><mxCell id=\"3\" value=\"首页\" style=\"rounded=1;whiteSpace=wrap;\" vertex=\"1\" parent=\"1\"><mxGeometry x=\"40\" y=\"160\" width=\"120\" height=\"40\" as=\"geometry\"/></mxCell>"}}

event: message
data: {"type":"done","sessionId":"uuid-123","usage":{"promptTokens":350,"completionTokens":850}}
```

心跳保活（每 15s）：

```
event: message
data: {"type":"heartbeat"}
```

错误场景：

```
event: error
data: {"type":"error","code":"AI_TIMEOUT","message":"AI 服务响应超时，请重试"}
```

```
event: error
data: {"type":"error","code":"RATE_LIMITED","message":"请求过于频繁，请 30 秒后重试"}
```

### 5.3 Mock AI Upstream Response — OpenAI Stream Format（供后端集成测试 Mock AI Server 使用）

### 5.4 Mock AI Upstream Response — 错误

```
data: {"error":{"message":"Rate limit exceeded","type":"rate_limit_error","code":"rate_limit_exceeded"}}
```

### 5.5 Test XML — 正常（2 个矩形 + 1 条连线）

```xml
<mxCell id="2" value="登录" style="rounded=1;whiteSpace=wrap;" vertex="1" parent="1">
  <mxGeometry x="40" y="40" width="120" height="40" as="geometry"/>
</mxCell>
<mxCell id="3" value="首页" style="rounded=1;whiteSpace=wrap;" vertex="1" parent="1">
  <mxGeometry x="40" y="160" width="120" height="40" as="geometry"/>
</mxCell>
<mxCell id="4" value="" style="edgeStyle=orthogonalEdgeStyle;" edge="1" parent="1" source="2" target="3">
  <mxGeometry relative="1" as="geometry"/>
</mxCell>
```

### 5.6 Test XML — 截断

```xml
<mxCell id="2" value="登录" style="rounded=1;whiteSpace=wrap;" vertex="1" parent="1">
  <mxGeometry x="40" y="40" width="120" height="40" as="geometry"/>
</mxCe
```

### 5.7 Test XML — 嵌套（非法）

```xml
<mxCell id="2" value="容器" vertex="1" parent="1">
  <mxGeometry x="40" y="40" width="300" height="200" as="geometry"/>
  <mxCell id="3" value="子元素" vertex="1" parent="2">
    <mxGeometry x="20" y="20" width="120" height="40" as="geometry"/>
  </mxCell>
</mxCell>
```

---

## 6. 给开发组的建议

### 6.1 v1 必须实现

1. **SSE heartbeat** — 15s 间隔，防止 proxy 超时
2. **请求体校验** — `@Validated` + 自定义校验注解
3. **后端限流** — 至少按 IP 限流（Guava RateLimiter 或 Spring `@RateLimiter`）
4. **AI 调用超时** — 可配置，默认 60s，最长 120s
5. **XML 校验修复管线** — 第 1 层 + 第 2 层

### 6.2 v1 可推迟

1. VLM 视觉校验 → v3
2. edit_diagram → v2
3. 会话持久化 → v2
4. prompt caching → v3

### 6.3 阻断性问题（必须修复后才能发布）

- SSE 没有 heartbeat → 生产环境 30s 无数据会断开
- 没有限流 → 恶意用户可以直接拖垮 AI API 配额（产生费用）
- 没有请求体校验 → 空 messages 可能导致后端 NPE

---

以上为 QA 的完整评审。测试用例已经按照分层分类整理，开发组可以按标签（TC-UT、TC-IT、TC-FE、TC-E2E）取用。
