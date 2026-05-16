# AI 绘图模块技术债务清单

> 基于 `ai/draw/`、`draw/`、`ai/config/` 模块代码审查整理。
> 审查日期：2026-05-16

---

## 优先级说明

| 等级 | 含义 |
|------|------|
| 高 | 影响代码质量/可维护性/线上稳定性，需优先处理 |
| 中 | 架构可改进点，中长期有价值 |
| 低 | 体验/健壮性提升，有空再做 |

---

## 高优先级

### 1. 提示词外置

PromptManager 中约 300 行系统提示词以 Java 多行字符串硬编码，每次修改提示词都需要重新编译部署。

**建议：** 外置到数据库（`site_config` 表）或配置文件（如 `prompts/diagram-system-prompt.md`），支持运行时热加载。

**涉及文件：** `ai/draw/PromptManager.java`

---

### 2. MxCellValidator 每次 new

`DisplayDiagramTool.execute()` 中每次调用都 `new MxCellValidator()`，该类是无状态的 XML 工具，应为单例。

**建议：** 将 `MxCellValidator` 注册为 `@Component`，通过构造函数注入。

**涉及文件：** `ai/draw/DisplayDiagramTool.java:25`、`draw/util/MxCellValidator.java`

---

### 3. 死代码清理

- `DiagramService.handleChatResponse()`（第 129-149 行）定义了但从未被调用，实际响应处理在 `chatStream()` 内联完成。
- `SseEvent.java` 类存在但零引用，SSE 事件实际通过 `Map.of()` + `ObjectMapper` 内联拼装。

**建议：** 删除 `handleChatResponse()` 方法。`SseEvent.java` 要么用起来统一事件模型，要么删除。

**涉及文件：** `ai/draw/DiagramService.java`、`ai/draw/SseEvent.java`

---

### 4. SseEmitter 生命周期管理

客户端断连或超时时，当前只调 `emitter.onTimeout(() -> emitter.complete())`，但：

- `@Async("diagramTaskExecutor")` 虚拟线程仍在继续执行 AI 调用
- 心跳 `ScheduledFuture` 不会自动取消，泄漏到调度线程池
- 没有 `onCompletion` 回调来清理资源

**建议：** 在 `chatStream()` 中注册 `emitter.onCompletion(() -> heartbeat.cancel(false))`，在 AI call 前后检测 emitter 状态，提前终止不必要的处理。

**涉及文件：** `ai/draw/DiagramService.java`、`ai/config/DiagramConfig.java`

---

## 中优先级

### 5. SSE 事件模型统一

四种事件类型（`text-delta`、`tool-call`、`done`、`heartbeat`）在 `DiagramService` 和 `DisplayDiagramTool` 两处分头用 `Map.of()` + `ObjectMapper.writeValueAsString()` 拼接，没有统一的事件类和序列化逻辑。

**建议：** 利用现有的 `SseEvent.java`（或新建事件类型枚举+DTO），统一事件构建逻辑，消除重复的 `Map.of("type", ...)` 模式。

**涉及文件：** `ai/draw/DiagramService.java`、`ai/draw/DisplayDiagramTool.java`、`ai/draw/SseEvent.java`

---

### 6. .call() → .stream()

当前使用 `ChatClient.prompt().call().chatResponse()`，AI 回复一次性返回后再逐段发送 SSE。注释说 `.stream()` 不支持 tool schema，可以验证 Spring AI 1.1.5 是否已修复。

**建议：** 如果 1.1.5 已支持 tool calling + streaming，改为 `.stream()` 实现真正的流式响应，降低首字延迟。

**涉及文件：** `ai/draw/DiagramService.java`

---

### 7. 配置字段补充

`DiagramProperties` 目前只有 `enabled`、`model`、`maxTokens` 三个字段，缺少常见的 AI 调用参数。

**建议补充字段：**

| 字段 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `temperature` | `Double` | `0.7` | 生成随机性 |
| `topP` | `Double` | `0.9` | 核采样 |
| `heartbeatInterval` | `int` | `15` | 心跳间隔（秒） |

**涉及文件：** `ai/draw/DiagramProperties.java`

---

### 8. AI 端点缺少认证

`POST /api/v1/draw/chat` 没有 `@PreAuthorize`，任何 IP 都可调用（只受 `DrawRateLimiter` 限制），与 `UserDiagramController` 的统一 `isAuthenticated()` 策略不一致。

**建议：** 对齐权限策略，加上 `@PreAuthorize("isAuthenticated()")`，或至少确保匿名用户有更严格的速率限制。

**涉及文件：** `ai/draw/controller/DiagramController.java`

---

### 9. 心跳间隔硬编码

15 秒心跳间隔直接写死在 `DiagramService.java:59` 的 `scheduleAtFixedRate()` 调用中，不可配置。

**建议：** 纳入 `DiagramProperties.heartbeatInterval`。

**涉及文件：** `ai/draw/DiagramService.java`、`ai/draw/DiagramProperties.java`

---

### 10. PromptManager 模型匹配策略脆弱

通过 `modelId.contains("claude-opus-4-5")` 的字符串匹配来决定使用哪个系统提示词版本，新增模型必须改 Java 代码。

**建议：** 改为配置驱动的模型→提示词模板映射（如 `application.yml` 或数据库），`PromptManager` 只负责组装不负责选择。

**涉及文件：** `ai/draw/PromptManager.java`

---

### 11. 无单元测试

`ai/draw/` 和 `draw/` 模块没有任何测试。

**建议补充测试：**

| 测试目标 | 类型 | 说明 |
|----------|------|------|
| `MxCellValidator` | 参数化单元测试 | 各种畸形 XML 的验证/清理 |
| `MxCellHelper` | 单元测试 | XML 完整性、格式化 |
| `PromptManager` | 单元测试 | 提示词组装逻辑 |
| `DisplayDiagramTool` | 集成测试 | Tool calling 流程 |
| `DiagramService` | 集成测试 | AI 对话完整链路 |

---

## 低优先级

### 12. 对话持久化

刷新页面对话即丢失，`sessionId` 由前端生成但后端不存储，无法恢复历史对话。

**建议：** 利用 `user_diagrams` 表扩展字段，关联保存对话历史。

---

### 13. 降级兜底

DeepSeek API 不可用时直接返回 500，没有重试机制或备用模型切换。

**建议：** 引入 Spring Retry 重试（指数退避），可选的 fallback 模型配置。

---

### 14. Token 用量监控

不记录每次 AI 请求的 token 消耗，无法做成本分析和用量监控。

**建议：** 利用 Spring AI 的 `ChatClient` 回调/拦截器，记录 `promptTokens`、`completionTokens`、`totalTokens` 到日志或数据库。

---

### 15. 请求参数缺校验

`DrawChatRequest` 字段无 `@NotNull`/`@NotEmpty` 等声明式校验注解，空指针风险下沉到 service 层用防御性 null 检查处理。

**建议：** 在 DTO 字段上加 `jakarta.validation` 注解，Controller 类加 `@Validated`。

**涉及文件：** `ai/draw/DrawChatRequest.java`、`ai/draw/controller/DiagramController.java`

---

### 16. Tool 与传输层耦合

`DisplayDiagramTool` 通过 Spring AI 的 `ToolContext` 接收 `SseEmitter` 实例，工具方法与 SSE 传输层耦合，不利于单元测试，也无法复用于非 SSE 场景。

**建议：** 引入事件发布/订阅机制（如 `ApplicationEventPublisher`），工具只负责发布事件，由专门的 SSE 适配器订阅并转发。

**涉及文件：** `ai/draw/DisplayDiagramTool.java`

---

### 17. DrawRateLimiter 参数硬编码

每分钟 10 次的限制直接写死在字段 `MAX_REQUESTS_PER_MINUTE` 中。

**建议：** 纳入配置属性（如 `lblog.diagram.rate-limit`）。

**涉及文件：** `ai/config/DrawRateLimiter.java`

---

### 18. model 默认值名不副实

`DiagramProperties.model` 默认值为 `"gpt-4o"`，但实际项目使用的是 DeepSeek 模型。

**建议：** 改为 `"deepseek-chat"` 或移除默认值强制配置。

**涉及文件：** `ai/draw/DiagramProperties.java`

---

### 19. countList 接口未暴露

`UserDiagramsMapper.xml` 定义了 `countList` 查询语句，但对应的 Java 接口 `UserDiagramsMapper.java` 中没有声明该方法，当前靠 PageHelper 自动注入。IDE 会在 mapper 文件里标记红色警告。

**建议：** 在 `UserDiagramsMapper.java` 中添加 `countList()` 方法声明，消除 IDE 警告。

**涉及文件：** `draw/mapper/UserDiagramsMapper.java`、`draw/mapper/UserDiagramsMapper.xml`

---

## 模块依赖简图

```
┌─────────────────────────────┐
│      ai/config/             │
│  DiagramConfig              │  ← 线程池、心跳调度器
│  DrawRateLimiter            │  ← IP 级别速率限制
└──────────┬──────────────────┘
           │ 依赖
┌──────────▼──────────────────┐
│      ai/draw/               │
│  DiagramController          │  ← SSE 端点 /api/v1/draw/chat
│  DiagramService             │  ← AI 对话编排 (@Async)
│  PromptManager              │  ← 系统提示词组装
│  DisplayDiagramTool         │  ← @Tool display_diagram
│  DiagramProperties          │  ← 配置属性
│  DrawChatRequest            │  ← 请求 DTO
│  DrawConfigVO               │  ← 响应 VO
│  SseEvent (dead)            │  ← 未使用的 SSE 事件类
└──────────┬──────────────────┘
           │ 依赖 MxCellValidator
┌──────────▼──────────────────┐
│      draw/                   │
│  UserDiagramController      │  ← CRUD /api/v1/draw/diagrams
│  UserDiagramsService        │  ← 业务逻辑
│  UserDiagramsMapper         │  ← MyBatis 持久层
│  UserDiagram                │  ← 领域实体
│  util/MxCellValidator       │  ← XML 验证/清理
│  util/MxCellHelper          │  ← XML 工具方法
└─────────────────────────────┘
```
