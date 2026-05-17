# AI 绘图模块开发总结

> 日期：2026-05-17
> 涵盖架构、工作流程、已知问题、开发注意事项

---

## 一、模块结构

```
ai/
├── config/
│   └── AiConfig.java                    # ChatClient 集中配置 + DeepSeekApi bean
├── agent/
│   └── draw/                            # AI 绘图 agent
│       ├── config/
│       │   ├── DiagramConfig.java       # 线程池、心跳调度器
│       │   └── DrawRateLimiter.java     # IP 级别速率限制
│       ├── controller/
│       │   └── DiagramController.java   # SSE 端点 /api/v1/draw/chat
│       ├── service/
│       │   └── DiagramService.java      # AI 对话编排（流式 + 非流式）
│       ├── tool/
│       │   └── DisplayDiagramTool.java  # @Tool display_diagram
│       ├── PromptManager.java           # 提示词组装
│       ├── DiagramProperties.java       # 配置绑定
│       ├── DrawChatRequest.java         # 请求 DTO
│       └── DrawConfigVO.java           # 响应 VO
├── advisor/
│   └── DeepSeekToolCallAdvisor.java    # 流式工具调用 + reasoning_content 捕获
└── prompt/                              # 公共提示词管理
```

### DeepSeek 定制类（在 Spring AI 包路径下）

```
src/main/java/org/springframework/ai/deepseek/
└── DeepSeekReasoningChatModel.java     # 修复 createRequest 丢失 reasoningContent
```

> 因 `createRequest` 是 package-private，必须放在同包下才能重写。
> 后续 Spring AI 修复后可删除此文件。

---

## 二、核心工作流程

### 2.1 SSE 事件流

```
连接建立 → 心跳 (每 N 秒) → AI 流式响应 → 结束
```

事件类型：

| 事件 | 格式 | 说明 |
|------|------|------|
| heartbeat | `{"type":"heartbeat"}` | 保活 + 断连检测 |
| reasoning | `{"type":"reasoning","delta":"..."}` | AI 思考过程（思考模式） |
| text-delta | `{"type":"text-delta","delta":"..."}` | AI 回复文本 |
| tool-call | `{"type":"tool-call","name":"display_diagram","arguments":{"xml":"..."}}` | 绘图指令 |
| done | `{"type":"done","sessionId":"..."}` | 流结束 |
| error | `emitter.completeWithError(e)` | 异常（触发前端 onerror） |

### 2.2 完整数据流

```
用户请求 POST /api/v1/draw/chat
  │
  ├─ DiagramController
  │   ├─ 检查 ai_draw_chat_enabled（site_config）
  │   ├─ 创建 SseEmitter（3分钟超时）
  │   └─ 调用 DiagramService.chatStream()
  │
  ├─ DiagramService (@Async 虚拟线程)
  │   ├─ 启动心跳（disconnectCheckIntervalSeconds）
  │   ├─ 构建 System Prompt（DB → 文件 → 硬编码常量）
  │   ├─ 调用 ChatClient.stream()
  │   │   └─ DeepSeekToolCallAdvisor 处理
  │   │       ├─ 捕获 reasoningContent
  │   │       ├─ 实时透传流式 chunks 到前端
  │   │       ├─ 聚合检测工具调用
  │   │       ├─ 执行工具（DisplayDiagramTool）
  │   │       └─ patchHistory(注入 reasoningContent) → 递归
  │   ├─ 发送 text-delta
  │   ├─ 发送 done
  │   └─ emitter.complete()
  │
  └─ DisplayDiagramTool
      ├─ 验证 XML（MxCellValidator，单例）
      ├─ 清理 XML（sanitize）
      ├─ 包装 mxfile
      └─ 发送 tool-call 事件到前端
```

### 2.3 心跳检测与断连处理

```
客户端断连 → 下次心跳 send() 抛出 IOException
  → asyncThread.interrupt()
  → AI 调用中断 → catch → 清理退出
```

| 保障 | 位置 |
|------|------|
| `onCompletion` 取消心跳 | `emitter.onCompletion(() -> heartbeat.cancel(false))` |
| `finally` 兜底取消 | `finally { heartbeat.cancel(false); }` |
| 心跳抛异常终止任务 | `ScheduledExecutorService` 自动抑制后续执行 |
| `setRemoveOnCancelPolicy(true)` | cancel 后立即出队，加速 GC |

---

## 三、Spring AI 兼容性问题

### 3.1 DeepSeek V4 思考模式

| 问题 | 说明 | 状态 |
|------|------|------|
| `reasoning_content` 未回传 | Spring AI `createRequest()` 硬编码为 null | ✅ 已修复（DeepSeekReasoningChatModel） |
| 流式 + 工具调用 + 思考模式 | 多轮对话时第二轮流式调用报 400 | ✅ 已修复 |
| `thinking: {type: enabled/disabled}` | Spring AI 1.1.5 不支持此参数 | ⚠️ 需等待版本升级 |
| `AssistantMessage` 无 reasoningContent | Spring AI 核心类缺少此字段 | ⚠️ 官方未修复 |

### 3.2 绕过的 workaround

| 方案 | 位置 | 原理 |
|------|------|------|
| `DeepSeekReasoningChatModel` | 重写 `createRequest` | 从 `DeepSeekAssistantMessage` 读取 reasoningContent |
| `DeepSeekToolCallAdvisor` | 重写 `adviseStream` | 捕获流式 chunk 中的 reasoningContent，注入 conversationHistory |

### 3.3 后续升级注意事项

升级 Spring AI 后需：
1. 检查 `DeepSeekReasoningChatModel` 是否已修复，可删除
2. 检查 `DeepSeekToolCallAdvisor` 是否仍需重写 `adviseStream`
3. 删除 `application.yml` 中多余的日志配置

---

## 四、数据源优先级

### 4.1 提示词

```
DB (ai_prompts) → Classpath 文件 (prompts/*.md) → 代码硬编码常量
```

### 4.2 配置来源

| 配置 | 位置 | 热修改 |
|------|------|--------|
| AI 绘图开关 | `site_config.ai_draw_chat_enabled` | ✅ 即时生效 |
| DeepSeek API Key | `application-ai.yml` | ❌ 需重启 |
| 模型名 | `application-ai.yml` | ❌ 需重启 |
| 心跳间隔 | `application.yml` | ❌ 需重启 |
| 速率限制 | `DrawRateLimiter.java` (100000) | ❌ 需重启 |

---

## 五、AI Agent 模块扩展规范

新增一个 AI Agent（如 `ai/agent/chat/`）的步骤：

1. **提示词**：在 `resources/prompts/{module}/` 下放 markdown 文件
2. **数据库**：`POST /api/v1/admin/ai/prompts/seed?module={module}` 导入
3. **代码**：新建 `PromptManager` 注入 `AiPromptService`
4. **调用**：`promptService.getPromptMap("{module}")` 一次获取全部提示词
5. **复用**：`ChatClient`、缓存、审计、管理 API 全部复用

```java
@Component
public class ChatPromptManager {
    private final AiPromptService promptService;

    public String buildSystemPrompt() {
        Map<String, String> p = promptService.getPromptMap("chat");
        return p.getOrDefault("system-default", "");
    }
}
```

---

## 六、关键配置

### application-ai.yml

```yaml
spring:
  ai:
    deepseek:
      api-key: sk-xxx
      chat:
        options:
          model: ${AI_DEEPSEEK_MODEL:deepseek-chat}
          temperature: 0.7
          max-tokens: 16384
```

| 模型 | 模式 | 说明 |
|------|------|------|
| `deepseek-chat` | 非思考 | 兼容性好，无 reasoning_content 问题 |
| `deepseek-v4-flash` | 思考 | 返回 reasoning_content，需 DeepSeekReasoningChatModel 修复 |
| `deepseek-v4-pro` | 思考 | 更高质量，同上 |

---

## 七、已知待办

| 项 | 优先级 | 说明 |
|----|--------|------|
| 升级 Spring AI | 低 | 等待官方修复 reasoning_content 问题 |
| 思考模式切非思考 | 低 | 通过 site_config 热切换模型 |
| v4-pro 兼容测试 | 低 | 当前未验证 |
| 非流式返回 thinking | 低 | 重写 adviseCall 即可，需配合前端 |
