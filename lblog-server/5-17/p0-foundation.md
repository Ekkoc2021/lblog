# P0 — 基础建设：对话持久化

> 阶段目标：从"无状态"到"有状态"，AI 对话可恢复、可回顾
> 前置依赖：无（基于现有 ai/agent/draw/ 模块改造）
> 预估工期：~8 天
> 设计原则：**模型无关**——核心存储和上下文注入机制与 LLM 解耦，通过策略模式支持不同模型

---

## 目录

1. [设计决策](#1-设计决策)
2. [数据库设计](#2-数据库设计)
3. [包结构与类设计](#3-包结构与类设计)
4. [模型消息转换](#4-模型消息转换)
5. [实现步骤](#5-实现步骤)
6. [前端适配](#6-前端适配)
7. [测试策略](#7-测试策略)

---

## 1. 设计决策

### 1.1 为什么不用 Spring AI 自带的 JdbcChatMemoryRepository

| 对比项 | Spring AI JdbcChatMemoryRepository | 自定义方案 |
|--------|-----------------------------------|-----------|
| 表结构 | 4 列：conversation_id, content, type, timestamp | 扩展字段：thinking/reasoning, tool_calls JSON, msg_index, metadata |
| 模型适配 | 只存 content，丢弃各模型特殊字段 | 通过 ModelMessageConverter 策略模式支持任意模型 |
| 消息顺序 | 依赖 timestamp | msg_index 显式排序 |
| 扩展性 | 表结构固定，无法扩展 | metadata JSON + converter 接口，加模型只需加实现 |
| 与 Advisor 集成 | 通过 MessageChatMemoryAdvisor | 自定义 ChatHistoryAdvisor，注入 converter 列表 |

### 1.2 架构模式：策略模式解耦

```
Spring AI Advisor 接口 (before / after / adviseStream)
        ▲
        │ 实现
        │
ChatHistoryAdvisor  ← 注入 → ChatMemoryStore (存储抽象)
  - before(): 加载历史 → 委托 converter 构建上下文   ← 依赖
  - after():  委托 converter 提取模型字段 → 保存     ← 依赖
                                                          │
                    ┌─────────────────────────────────────┤
                    │             策略列表                  │
                    ▼                                     ▼
          DeepSeekMessageConverter    OpenAiMessageConverter (未来)
          ClaudeMessageConverter      ... (未来)
```

### 1.3 会话归属

当前 AI 绘图功能要求登录（JWT），所以按 `user_id` 关联。

> 未来通用 chat 可能需要支持访客（fingerprint），预留 `user_id` 可为 NULL。

### 1.4 Agent 类型标识

用 `agent_type` 字段隔离不同 AI Agent，与模型无关。换模型只需换 `model_name`，不影响会话结构。

| agent_type | 说明 | model_name 示例 |
|-----------|------|----------------|
| `draw` | AI 绘图（当前） | deepseek-chat, deepseek-v4-flash |
| `chat` | 通用对话（未来） | gpt-4o, claude-sonnet-4-6 |
| `code` | 代码助手（未来） | deepseek-coder |

---

## 2. 数据库设计

### 2.1 DDL

```sql
-- ============================================================
-- Table: ai_chat_sessions
-- 说明：会话表，记录一次 AI 对话的元信息
-- ============================================================
CREATE TABLE IF NOT EXISTS `ai_chat_sessions` (
    `id`              BIGINT          NOT NULL AUTO_INCREMENT  COMMENT '主键',
    `user_id`         BIGINT          DEFAULT NULL             COMMENT '用户 ID（NULL 表示访客）',
    `agent_type`      VARCHAR(32)     NOT NULL                 COMMENT 'Agent 类型：draw/chat/code',
    `title`           VARCHAR(255)    DEFAULT NULL             COMMENT '会话标题，自动生成或用户命名',
    `model_name`      VARCHAR(64)     DEFAULT NULL             COMMENT '使用的模型名',
    `message_count`   INT             NOT NULL DEFAULT 0       COMMENT '消息总数',
    `total_tokens`    INT             NOT NULL DEFAULT 0       COMMENT '累计 tokens',
    `status`          TINYINT         NOT NULL DEFAULT 1       COMMENT '状态：1=活跃, 0=已归档, -1=已删除',
    `created_at`      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后活跃时间',
    PRIMARY KEY (`id`),
    KEY `idx_user_agent` (`user_id`, `agent_type`, `updated_at`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI 对话会话表';


-- ============================================================
-- Table: ai_chat_messages
-- 说明：消息表，存储完整对话消息（含 DeepSeek 特殊字段）
-- ============================================================
CREATE TABLE IF NOT EXISTS `ai_chat_messages` (
    `id`                BIGINT          NOT NULL AUTO_INCREMENT  COMMENT '主键',
    `session_id`        BIGINT          NOT NULL                 COMMENT '关联会话 ID',
    `role`              VARCHAR(16)     NOT NULL                 COMMENT '角色：user/assistant/tool/system',
    `content`           LONGTEXT        DEFAULT NULL             COMMENT '消息可见文本内容',
    `reasoning_content` LONGTEXT        DEFAULT NULL             COMMENT '模型思考/推理过程（DeepSeek reasoning_content / Claude thinking 等）',
    `tool_calls`        JSON            DEFAULT NULL             COMMENT '工具调用列表：[{"id":"...","name":"...","arguments":{}}]',
    `tool_call_id`      VARCHAR(64)     DEFAULT NULL             COMMENT '工具调用 ID（仅 tool role 使用）',
    `name`              VARCHAR(128)    DEFAULT NULL             COMMENT '工具名（仅 tool role 使用）',
    `msg_index`         INT             NOT NULL DEFAULT 0       COMMENT '会话内消息序号，从 0 递增',
    `tokens`            INT             DEFAULT 0                COMMENT '本条消息的 token 数（可选统计）',
    `metadata`          JSON            DEFAULT NULL             COMMENT '扩展字段，预留给未来使用',
    `created_at`        DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_session_order` (`session_id`, `msg_index`),
    CONSTRAINT `fk_message_session` FOREIGN KEY (`session_id`) REFERENCES `ai_chat_sessions` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI 对话消息表';
```

### 2.2 设计要点

| 字段 | 说明 |
|------|------|
| `tool_calls` JSON | 存储模型返回的工具调用数组，格式遵循 OpenAI function calling 规范，各模型兼容 |
| `reasoning_content` | 模型思考过程（各模型叫法不同，统一存此字段），上下文注入时可选择是否包含 |
| `msg_index` | 严格递增序号，保证消息顺序不依赖时间戳。每条新消息 = 当前会话最大 msg_index + 1 |
| `metadata` | 预留扩展字段，未来可存：用户评分、模型延迟、重试次数等 |
| `status` | 软删除：归档和删除都不真正移除数据，只改变状态 |
| `ON DELETE CASCADE` | 删除会话时自动级联删除所有消息 |

### 2.3 索引说明

- `idx_user_agent (user_id, agent_type, updated_at)`：首页会话列表按用户 + 类型 + 时间排序
- `idx_session_order (session_id, msg_index)`：加载会话消息时按序号排序
- `idx_status`：过滤已删除会话

---

## 3. 包结构与类设计

### 3.1 包结构

```
ai/
├── chat/                              # 对话记忆模块（新增，模型无关）
│   ├── domain/
│   │   ├── ChatSession.java           # 会话实体
│   │   ├── ChatMessage.java           # 消息实体
│   │   ├── ChatSessionVO.java         # 会话列表 VO
│   │   └── ChatMessageVO.java         # 消息展示 VO
│   ├── mapper/
│   │   ├── ChatSessionMapper.java     # MyBatis 接口
│   │   └── ChatMessageMapper.java     # MyBatis 接口
│   ├── service/
│   │   ├── ChatSessionService.java    # 会话 CRUD
│   │   └── ChatMessageService.java    # 消息存储 + 加载
│   └── controller/
│       ├── ChatSessionController.java # REST API：会话管理
│       └── ChatMessageController.java # REST API：消息查询
│
├── advisor/
│   └── DeepSeekToolCallAdvisor.java   # 已有：工具调用处理
│
├── memory/                            # 记忆抽象层（新增，模型无关）
│   ├── ChatMemoryStore.java           # 接口：存储抽象（不依赖任何模型类）
│   ├── ChatMemoryStoreImpl.java       # 实现：基于 ChatMessageService
│   ├── advisor/
│   │   └── ChatHistoryAdvisor.java    # 新增：通用上下文记忆 advisor
│   └── converter/
│       ├── ModelMessageConverter.java # 接口：模型消息双向转换
│       └── deepseek/
│           └── DeepSeekMessageConverter.java  # DeepSeek 实现
│                                              # （后续加模型在此同级建包）
```

### 3.2 核心类设计

#### ChatSession.java — 会话实体

```java
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatSession {
    private Long id;
    private Long userId;
    private String agentType;     // draw / chat / code
    private String title;
    private String modelName;
    private Integer messageCount;
    private Integer totalTokens;
    private Integer status;       // 1=活跃 0=归档 -1=删除
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
```

#### ChatMessage.java — 消息实体

```java
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessage {
    private Long id;
    private Long sessionId;
    private String role;              // user / assistant / tool / system
    private String content;
    private String reasoningContent;  // 模型思考/推理过程（模型无关字段名）
    private String toolCalls;         // JSON 字符串，标准 tool_calls 格式
    private String toolCallId;
    private String name;
    private Integer msgIndex;
    private Integer tokens;
    private String metadata;          // JSON 字符串，预留给未来使用
    private LocalDateTime createdAt;
}
```

#### ChatSessionVO.java — 会话列表展示

```java
@Data
public class ChatSessionVO {
    private Long id;
    private String title;
    private String agentType;
    private String modelName;
    private Integer messageCount;
    private String previewText;       // 最后一条消息的摘要（前端预览）
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
```

#### ChatMessageVO.java — 消息展示

```java
@Data
public class ChatMessageVO {
    private Long id;
    private Long sessionId;
    private String role;
    private String content;
    private String reasoningContent;  // 前端可折叠展示
    private List<ToolCallVO> toolCalls;
    private Integer msgIndex;
    private LocalDateTime createdAt;

    @Data
    public static class ToolCallVO {
        private String id;
        private String name;
        private Map<String, Object> arguments;
    }
}
```

#### ChatSessionService.java — 会话服务

```java
public interface ChatSessionService {

    /** 创建新会话 */
    ChatSession createSession(Long userId, String agentType, String modelName);

    /** 获取用户的会话列表（按更新时间倒序） */
    List<ChatSessionVO> listSessions(Long userId, String agentType, int page, int size);

    /** 获取单个会话详情 */
    ChatSession getSession(Long sessionId);

    /** 更新标题 */
    void updateTitle(Long sessionId, String title);

    /** 更新会话统计（消息数 + token 数） */
    void updateStats(Long sessionId, int messageCountDelta, int tokensDelta);

    /** 归档会话 */
    void archiveSession(Long sessionId);

    /** 软删除会话 */
    void deleteSession(Long sessionId);

    /** 确认会话归属权（校验 user_id 匹配） */
    boolean checkOwnership(Long sessionId, Long userId);
}
```

#### ChatMessageService.java — 消息服务

```java
public interface ChatMessageService {

    /** 保存用户消息 */
    ChatMessage saveUserMessage(Long sessionId, String content, int msgIndex);

    /** 保存助手消息（含推理内容） */
    ChatMessage saveAssistantMessage(Long sessionId, String content,
                                     String reasoningContent, String toolCallsJson,
                                     int msgIndex, int tokens);

    /** 保存工具调用结果消息 */
    ChatMessage saveToolMessage(Long sessionId, String toolCallId,
                                String name, String content, int msgIndex);

    /** 批量保存（原子操作，用于 tool call 多轮场景） */
    List<ChatMessage> batchSave(Long sessionId, List<ChatMessage> messages);

    /** 加载会话的所有消息（按 msg_index 排序） */
    List<ChatMessage> loadMessages(Long sessionId);

    /** 加载最近 N 条消息 */
    List<ChatMessage> loadRecentMessages(Long sessionId, int limit);

    /** 获取会话的当前最大 msg_index（用于递增） */
    int getMaxMsgIndex(Long sessionId);

    /** 删除会话的所有消息 */
    void deleteBySession(Long sessionId);
}
```

#### ChatMemoryStore.java — 记忆存储接口

这个接口是对上层的抽象，`ChatHistoryAdvisor` 通过它读写记忆，不直接依赖 MyBatis。

```java
/**
 * 记忆存储接口。
 * 抽象层：隔离 advisor 与存储实现。
 * 所有方法只操作 ChatMessage 实体（模型无关），不涉及任何模型特定的 Message 类。
 */
public interface ChatMemoryStore {

    /** 加载完整对话历史（按 msg_index 排序） */
    List<ChatMessage> loadHistory(Long sessionId);

    /** 保存新消息列表 */
    void saveMessages(Long sessionId, List<ChatMessage> messages);

    /** 获取或创建会话元信息 */
    ChatSession getOrCreateSession(Long userId, String agentType, String modelName);

    /** 更新会话最后活跃时间 */
    void touchSession(Long sessionId);
}
```

#### ChatMemoryStoreImpl.java — 实现

```java
@Service
@RequiredArgsConstructor
public class ChatMemoryStoreImpl implements ChatMemoryStore {

    private final ChatSessionService sessionService;
    private final ChatMessageService messageService;

    @Override
    public List<ChatMessage> loadHistory(Long sessionId) {
        return messageService.loadMessages(sessionId);
    }

    @Override
    public void saveMessages(Long sessionId, List<ChatMessage> messages) {
        messageService.batchSave(sessionId, messages);
    }

    @Override
    public ChatSession getOrCreateSession(Long userId, String agentType, String modelName) {
        return sessionService.createSession(userId, agentType, modelName);
    }

    @Override
    public void touchSession(Long sessionId) {
        // 由 saveMessages 触发的 ON UPDATE CURRENT_TIMESTAMP 自动完成
    }
}
```

#### ContextPolicy.java — 上下文注入策略

```java
/**
 * 上下文注入策略配置。
 * 控制"什么内容以什么形式注入上下文"。
 * P1 中会大幅扩展此类（技能包、分层加载等）。
 */
public class ContextPolicy {
    /** 是否在上下文中注入思考/推理过程（默认不注入） */
    private boolean includeReasoning = false;
    /** 历史消息最大 token 预算（超出需压缩或截断） */
    private int maxHistoryTokens = 4000;
    /** 是否注入工具调用结果 */
    private boolean includeToolResults = true;
    /** 保留的最近对话轮数（0=不限） */
    private int recentRounds = 20;
}
```

#### ModelMessageConverter.java — 模型消息转换器接口

**这是实现模型无关的关键抽象。** 每种 LLM 的消息格式不同（DeepSeekAssistantMessage 有 reasoningContent、Claude 的 thinking 在不同字段、OpenAI 的 assistant 消息格式），通过此接口将这些差异隔离在 converter 内部。

```java
/**
 * 模型消息转换器接口。
 * 职责：将 ChatMessage 实体与 Spring AI Message 对象进行双向转换。
 * 
 * 每种模型（DeepSeek / OpenAI / Claude ...）各自实现此接口，
 * ChatHistoryAdvisor 根据当前使用的模型名动态选择对应的 converter。
 */
public interface ModelMessageConverter {

    /**
     * 判断当前 converter 是否支持指定模型。
     * 支持多个模型名，例如 "deepseek-chat" 和 "deepseek-v4-flash" 用同一个 converter。
     */
    boolean supports(String modelName);

    /**
     * 将存储的 ChatMessage 转为 Spring AI Message（用于注入上下文）。
     *
     * @param stored  数据库中的消息实体
     * @param policy  上下文注入策略（如：是否包含 reasoning_content）
     * @return Spring AI 的 Message 对象
     */
    Message toContextMessage(ChatMessage stored, ContextPolicy policy);

    /**
     * 将 Spring AI 的输出转为存储实体（非流式场景）。
     *
     * @param output   Spring AI 的 Message 输出
     * @param sessionId 会话 ID
     * @param msgIndex  消息序号
     * @return 持久化用的 ChatMessage 实体
     */
    ChatMessage toStorageMessage(Message output, Long sessionId, int msgIndex);

    /**
     * 将流式输出的 chunks 聚合后转为存储实体。
     * 不同模型的流式 chunk 格式不同，各自实现聚合逻辑。
     */
    ChatMessage toStorageMessageFromChunks(
            List<ChatClientResponse> chunks, Long sessionId, int msgIndex);

    /**
     * 获取此 converter 支持的模型列表（用于注册和路由）。
     */
    List<String> supportedModels();
}
```

#### DeepSeekMessageConverter.java — DeepSeek 实现

```java
/**
 * DeepSeek 模型的消息转换器。
 * 处理 DeepSeekAssistantMessage 特有的 reasoningContent。
 */
@Component
public class DeepSeekMessageConverter implements ModelMessageConverter {

    @Override
    public boolean supports(String modelName) {
        return modelName != null && modelName.startsWith("deepseek");
    }

    @Override
    public List<String> supportedModels() {
        return List.of("deepseek-chat", "deepseek-v4-flash", "deepseek-v4-pro");
    }

    @Override
    public Message toContextMessage(ChatMessage stored, ContextPolicy policy) {
        if (!"assistant".equals(stored.getRole())) {
            return null; // 非 assistant 由 ChatHistoryAdvisor 统一处理
        }
        // 根据策略决定是否注入 reasoning_content
        if (policy.includeReasoning() && stored.getReasoningContent() != null) {
            return new DeepSeekAssistantMessage.Builder(stored.getContent())
                .reasoningContent(stored.getReasoningContent())
                .build();
        }
        return new AssistantMessage(stored.getContent());
    }

    @Override
    public ChatMessage toStorageMessage(Message output, Long sessionId, int msgIndex) {
        ChatMessage msg = new ChatMessage();
        msg.setSessionId(sessionId);
        msg.setMsgIndex(msgIndex);
        msg.setRole("assistant");
        msg.setContent(output.getContent());

        // DeepSeek 特有：提取 reasoningContent
        if (output instanceof DeepSeekAssistantMessage dsMsg) {
            msg.setReasoningContent(dsMsg.getReasoningContent());
        }

        // 提取工具调用信息（模型无关，标准 tool_calls 格式）
        if (output instanceof AssistantMessage asstMsg && asstMsg.hasToolCalls()) {
            msg.setToolCalls(serializeToolCalls(asstMsg.getToolCalls()));
        }

        return msg;
    }

    @Override
    public ChatMessage toStorageMessageFromChunks(
            List<ChatClientResponse> chunks, Long sessionId, int msgIndex) {
        StringBuilder content = new StringBuilder();
        StringBuilder reasoning = new StringBuilder();
        // ... 遍历 chunks，从各自结构体中提取 content 和 reasoning_content
        // DeepSeek 的流式 chunk 中 reasoning_content 在单独的字段
        ChatMessage msg = new ChatMessage();
        msg.setSessionId(sessionId);
        msg.setMsgIndex(msgIndex);
        msg.setRole("assistant");
        msg.setContent(content.toString());
        msg.setReasoningContent(reasoning.toString());
        return msg;
    }

    private String serializeToolCalls(List<ToolCall> toolCalls) {
        // 将 Spring AI ToolCall 列表转为 JSON 字符串
    }
}
```

> 未来加新模型（如 Claude）：只需新建 `ClaudeMessageConverter` 实现 `ModelMessageConverter`，注册为 Spring Bean。`ChatHistoryAdvisor` 自动发现并使用。

#### ChatHistoryAdvisor.java — 通用上下文记忆 Advisor

替换原来的 `DeepSeekChatMemoryAdvisor`，不再硬编码任何模型特定逻辑。

关键设计：**无 ThreadLocal**。Spring AI 的 `ChatClientRequest` 和 `ChatClientResponse` 都带有 `context()` 方法（即 advisor params），会话 ID 通过上下文链自然传递。

```java
/**
 * 通用 ChatMemory Advisor。
 * 模型无关——通过 ModelMessageConverter 策略处理不同模型的消息格式差异。
 *
 * 会话 ID 传递方式（Spring AI 标准做法）：
 *   before():  request.context().get("sessionId")   ← 请求上下文
 *   after():   response.context().get("sessionId")  ← 响应上下文（自动透传）
 *   adviseStream(): 捕获到局部变量后在 Reactor 回调中引用
 *
 * Advisor Chain 中的位置：
 *   请求进入 → ChatHistoryAdvisor.before()（加载历史）
 *           → DeepSeekToolCallAdvisor（工具调用）
 *           → ChatClient → LLM
 *           → DeepSeekToolCallAdvisor（工具调用循环）
 *           → ChatHistoryAdvisor.after()（保存响应）
 *           → 返回给客户端
 */
public class ChatHistoryAdvisor implements BaseAdvisor {

    private final ChatMemoryStore chatMemoryStore;
    private final List<ModelMessageConverter> converters;
    private final ContextPolicy defaultPolicy;
    private final int order;

    @Override
    public ChatClientRequest before(ChatClientRequest request, AdvisorChain chain) {
        // 1. 从 request.context() 获取 sessionId / modelName
        Map<String, Object> ctx = request.context();
        String sessionId = ctx != null ? (String) ctx.get("sessionId") : null;
        String modelName = ctx != null ? (String) ctx.get("modelName") : null;

        if (sessionId == null || sessionId.isBlank()) {
            return request;
        }

        // 2. 加载历史消息 + 委托 converter 构建上下文
        List<ChatMessage> history = chatMemoryStore.loadHistory(Long.parseLong(sessionId));
        saveCurrentUserMessage(request, sid);

        ModelMessageConverter converter = findConverter(modelName);
        List<Message> contextMessages = buildContextMessages(history, converter);

        // 3. system prompt 放前面，历史放中间，当前用户消息放最后
        List<Message> allMessages = new ArrayList<>();
        allMessages.addAll(systemMessages);
        allMessages.addAll(contextMessages);
        allMessages.addAll(nonSystemMessages);

        return request.mutate()
            .prompt(new Prompt(allMessages, request.prompt().getOptions()))
            .build();
    }

    @Override
    public ChatClientResponse after(ChatClientResponse response, AdvisorChain chain) {
        // 从 response.context() 获取 sessionId（Spring AI 自动透传请求上下文）
        Map<String, Object> ctx = response.context();
        String sessionId = ctx != null ? (String) ctx.get("sessionId") : null;
        if (sessionId == null) return response;

        // 委托 converter 将输出转为存储实体 + 保存
        ModelMessageConverter converter = findConverter(modelName);
        ChatMessage stored = converter.toStorageMessage(
            generation.getOutput(), Long.parseLong(sessionId), getNextMsgIndex(sessionId));
        chatMemoryStore.saveMessages(Long.parseLong(sessionId), List.of(stored));

        return response;
    }

    /** adviseStream(): 流式场景——拦截 Flux，聚合完成后委托 converter 保存 */
    @Override
    public Flux<ChatClientResponse> adviseStream(
            ChatClientRequest request, StreamAdvisorChain chain) {
        Map<String, Object> ctx = request.context();
        String sessionId = ctx != null ? (String) ctx.get("sessionId") : null;
        if (sessionId == null) return chain.nextStream(request);

        // 在进入 Reactor 链前捕获到局部变量（!!! 关键：避免 ThreadLocal 跨线程问题）
        final Long capturedSid = Long.parseLong(sessionId);
        final String capturedModelName = ...;

        // before 逻辑（同非流式）
        ...

        // 拦截返回的 Flux
        List<ChatClientResponse> chunks = new CopyOnWriteArrayList<>();
        return stream
            .doOnNext(chunks::add)
            .doOnComplete(() -> {
                // 使用 capturedSid / capturedModelName（安全，不是 ThreadLocal）
                ChatMessage stored = converter.toStorageMessageFromChunks(
                    chunks, capturedSid, nextMsgIndex);
                chatMemoryStore.saveMessages(capturedSid, List.of(stored));
            })
            .doOnError(e -> log.error("..."));
    }
}
```

#### 消息保存流程详细设计（after 和 adviseStream）

对于流式场景（SSE），after 的逻辑需要特殊处理：

```
adviseStream():
  1. 在 before() 中注入历史（同非流式）
  2. 拦截流式 Flux：
     - 逐块收集 response chunks
     - 在流完成时（doOnComplete）:
       a. 找对应模型的 converter
       b. 调用 converter.toStorageMessageFromChunks() 聚合完整响应
       c. 调用 chatMemoryStore.saveMessages() 保存
       d. 更新会话 message_count / total_tokens
  3. 将流原样传递给下游
```

流式 chunks 聚合示意（各模型的 chunks 结构不同，由 converter 处理）：

```
chunk 1: { content: "你好", reasoning_content: null }
chunk 2: { content: "，我", reasoning_content: "用户问好，准备回应" }
chunk 3: { content: "是助手", reasoning_content: "用友好的语气" }
...
聚合后: content="你好，我是助手", reasoning_content="用户问好，准备回应\n用友好的语气"
```

### 3.3 Mapper XML

#### ChatSessionMapper.xml

```xml
<?xml version="1.0" encoding="UTF-8" ?>
<!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN"
    "http://mybatis.org/dtd/mybatis-3-mapper.dtd">
<mapper namespace="com.yang.lblogserver.ai.chat.mapper.ChatSessionMapper">

    <insert id="insert" useGeneratedKeys="true" keyProperty="id">
        INSERT INTO ai_chat_sessions (user_id, agent_type, title, model_name, message_count, total_tokens, status)
        VALUES (#{userId}, #{agentType}, #{title}, #{modelName}, #{messageCount}, #{totalTokens}, #{status})
    </insert>

    <select id="selectByUserAndAgent" resultType="ChatSession">
        SELECT * FROM ai_chat_sessions
        WHERE user_id = #{userId}
          AND agent_type = #{agentType}
          AND status >= 0
        ORDER BY updated_at DESC
        LIMIT #{offset}, #{size}
    </select>

    <select id="selectById" resultType="ChatSession">
        SELECT * FROM ai_chat_sessions WHERE id = #{id}
    </select>

    <update id="updateTitle">
        UPDATE ai_chat_sessions SET title = #{title} WHERE id = #{id}
    </update>

    <update id="updateStats">
        UPDATE ai_chat_sessions
        SET message_count = message_count + #{delta},
            total_tokens  = total_tokens + #{tokens}
        WHERE id = #{sessionId}
    </update>

    <update id="updateStatus">
        UPDATE ai_chat_sessions SET status = #{status} WHERE id = #{id}
    </update>
</mapper>
```

#### ChatMessageMapper.xml

```xml
<?xml version="1.0" encoding="UTF-8" ?>
<!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN"
    "http://mybatis.org/dtd/mybatis-3-mapper.dtd">
<mapper namespace="com.yang.lblogserver.ai.chat.mapper.ChatMessageMapper">

    <insert id="insert" useGeneratedKeys="true" keyProperty="id">
        INSERT INTO ai_chat_messages
            (session_id, role, content, reasoning_content, tool_calls,
             tool_call_id, `name`, msg_index, tokens, metadata)
        VALUES
            (#{sessionId}, #{role}, #{content}, #{reasoningContent}, #{toolCalls},
             #{toolCallId}, #{name}, #{msgIndex}, #{tokens}, #{metadata})
    </insert>

    <!-- 批量插入（事务内） -->
    <insert id="batchInsert">
        INSERT INTO ai_chat_messages
            (session_id, role, content, reasoning_content, tool_calls,
             tool_call_id, `name`, msg_index, tokens, metadata)
        VALUES
        <foreach collection="list" item="msg" separator=",">
            (#{msg.sessionId}, #{msg.role}, #{msg.content}, #{msg.reasoningContent}, #{msg.toolCalls},
             #{msg.toolCallId}, #{msg.name}, #{msg.msgIndex}, #{msg.tokens}, #{msg.metadata})
        </foreach>
    </insert>

    <select id="selectBySessionId" resultType="ChatMessage">
        SELECT * FROM ai_chat_messages
        WHERE session_id = #{sessionId}
        ORDER BY msg_index ASC
    </select>

    <select id="selectRecentBySessionId" resultType="ChatMessage">
        SELECT * FROM ai_chat_messages
        WHERE session_id = #{sessionId}
        ORDER BY msg_index DESC
        LIMIT #{limit}
    </select>

    <select id="selectMaxMsgIndex" resultType="int">
        SELECT COALESCE(MAX(msg_index), -1) FROM ai_chat_messages
        WHERE session_id = #{sessionId}
    </select>

    <delete id="deleteBySessionId">
        DELETE FROM ai_chat_messages WHERE session_id = #{sessionId}
    </delete>
</mapper>
```

---

## 4. 模型消息转换

> 核心原则：ChatMessage 实体是"通用语言"，Spring AI Message 是"运行时语言"。转换器在两者之间翻译，每种模型一个实现。

### 4.1 消息角色映射

| 我们的 role | Spring AI Message 类型 | 说明 |
|------------|----------------------|------|
| `user` | `UserMessage` | 各模型通用，无需 converter |
| `assistant` | `AssistantMessage` / 模型子类 | **需要 converter**：DeepSeek → DeepSeekAssistantMessage，Claude → 自有类型 |
| `tool` | `ToolMessage` | 标准 OpenAI function calling 格式，通用 |
| `system` | `SystemMessage` | 由 PromptManager 构建，不来自历史 |

### 4.2 各模型特殊字段映射

| 模型 | 思考/推理字段 | converter 处理方式 |
|------|-------------|-------------------|
| DeepSeek | `DeepSeekAssistantMessage.reasoningContent` | 保存时提取 → ChatMessage.reasoningContent，加载时按策略注入 |
| OpenAI | `AssistantMessage` 扩展字段 `reasoning_content` | 同上，字段名兼容 |
| Claude | `thinking` 在响应中单独字段 | 保存时提取 → ChatMessage.reasoningContent |
| 其他 | 无思考字段 | converter.toContextMessage 忽略 reasoningContent 逻辑 |

### 4.3 保存消息流程图

```
DiagramService 收到响应
        │
        ▼
ChatHistoryAdvisor.after() / adviseStream()
        │
        ├─ 判断模型名 → 找对应 ModelMessageConverter
        │
        ├─ 非流式: converter.toStorageMessage(output, sessionId, msgIndex)
        │         → 提取 reasoningContent（如有）
        │         → 提取 toolCalls（如有）
        │         → 返回 ChatMessage
        │
        └─ 流式: converter.toStorageMessageFromChunks(chunks, sessionId, msgIndex)
                  → 遍历 chunks，拼接 content + reasoning_content
                  → 提取最后一个 chunk 的 tool_calls
                  → 返回 ChatMessage
        │
        ▼
chatMemoryStore.saveMessages(sessionId, List.of(msg))
```

### 4.4 加载消息进上下文流程图

```
ChatHistoryAdvisor.before() 收到请求
        │
        ├─ 获取 sessionId → chatMemoryStore.loadHistory(sessionId)
        ├─ 获取 modelName → findConverter(modelName)
        │
        ▼
遍历 List<ChatMessage>:
  role=user    → 通用: new UserMessage(content)
  role=tool    → 通用: new ToolMessage(content, toolCallId)
  role=assistant → delegate 给 converter:
                    converter.toContextMessage(msg, policy)
                      → policy.includeReasoning=true  && reasoningContent != null
                          → 构造含 reasoning 的模型特定 Message
                      → 否则 → new AssistantMessage(content)
  role=system  → 跳过（由 PromptManager 处理）
        │
        ▼
注入到 ChatClientRequest 的 messages 中
```

### 4.5 前端展示

前端无需关心后端是什么模型，统一展示：

```typescript
// 前端类型（模型无关）
interface ChatMessageVO {
  role: 'user' | 'assistant' | 'tool';
  content: string;
  reasoningContent?: string;   // 任何模型的思考过程都存此字段
  toolCalls?: ToolCallVO[];
  msgIndex: number;
}

// 渲染时
{message.role === 'assistant' && message.reasoningContent && (
  <details>
    <summary>AI 思考过程</summary>
    <pre>{message.reasoningContent}</pre>
  </details>
)}
{message.content}
```

### 4.6 如何添加新模型

假设以后要加 Claude，只需要：

1. 新建 `ai/memory/converter/claude/ClaudeMessageConverter.java`
2. 实现 `ModelMessageConverter` 接口，处理 Claude 的 thinking 字段
3. 注册为 `@Component`
4. `ChatHistoryAdvisor` 自动发现并使用

```java
@Component
public class ClaudeMessageConverter implements ModelMessageConverter {

    @Override
    public boolean supports(String modelName) {
        return modelName != null && modelName.contains("claude");
    }

    @Override
    public Message toContextMessage(ChatMessage stored, ContextPolicy policy) {
        // Claude 的 thinking 放在 ChatMessage.reasoning_content 里
        if ("assistant".equals(stored.getRole())
                && policy.includeReasoning()
                && stored.getReasoningContent() != null) {
            // 构造带有 thinking 的 Claude Message
        }
        return new AssistantMessage(stored.getContent());
    }

    @Override
    public ChatMessage toStorageMessage(Message output, Long sessionId, int msgIndex) {
        ChatMessage msg = new ChatMessage();
        // ... 从 Claude 响应中提取 thinking 存到 reasoning_content
        return msg;
    }

    // ...
}
```

**改动范围：** 仅新增一个文件，零修改现有代码。这就是策略模式的价值。

---

## 5. 实现步骤

### Step 1：建表（0.5d）

- 在 MySQL 中执行建表 DDL（或通过 Liquibase/Flyway，如项目有配置）
- 检查 `application.yml` 中 MyBatis 配置，确认支持新 mapper 路径

### Step 2：领域模型 + Mapper（1d）

- 创建 `ChatSession.java`、`ChatMessage.java` 实体
- 创建 `ChatSessionMapper.java` + `ChatSessionMapper.xml`
- 创建 `ChatMessageMapper.java` + `ChatMessageMapper.xml`
- 创建 VO 类 `ChatSessionVO.java`、`ChatMessageVO.java`
- 编写单元测试确认 CRUD 正确

### Step 3：Service 层（1d）

- 实现 `ChatSessionService`：create、list、update、archive、delete
- 实现 `ChatMessageService`：save、batchSave、load、delete
- 实现 `ChatMemoryStore` + `ChatMemoryStoreImpl`
- 编写 service 层单元测试

### Step 4：Converter + Advisor（1.5d）

- 定义 `ModelMessageConverter` 接口：`toContextMessage()` / `toStorageMessage()` / `toStorageMessageFromChunks()` / `supports()`
- 实现 `DeepSeekMessageConverter`：处理 DeepSeekAssistantMessage 的 reasoningContent
- 实现 `ContextPolicy`：配置类，控制 reasoning 是否注入上下文等策略
- 实现 `ChatHistoryAdvisor`：
  - `before()`：加载历史 → 找 converter → 构建消息列表 → 注入
  - `after()`：非流式场景 → 找 converter → 转换 → 保存
  - `adviseStream()`：流式场景 → 拦截 Flux → 聚合 chunks → 找 converter → 保存
- 处理 tool call 场景的多轮消息保存
- `@Configuration` 注册 converter 列表到 advisor
- 编写 advisor + converter 单元测试（mock ChatMemoryStore）

### Step 5：改造 DiagramService（1d）

- 修改 `chatStream()` 和 `chatNonStream()`：
  - 从请求体中提取 `sessionId`（如果没有，调用 `createSession` 创建）
  - 不再从请求体 `messages` 构建完整历史，只取最新用户消息
  - 历史由 `ChatHistoryAdvisor` 自动加载
  - 将 `sessionId` 传递给 advisor context

- `DiagramService.chatStream()` 改造对比：

```
改造前：
  List<Message> allMessages = buildMessages(request);  // 从请求体构建
  ChatClient prompt = chatClient.prompt()
      .messages(allMessages)
      ...;

改造后：
  // 1. modelName：从请求体 model 字段取，或从当前配置的模型名取
  String modelName = resolveModelName(request);
  // 2. sessionId：从请求体取（新会话由 controller 创建后传入）
  String sessionId = request.getSessionId();
  // 3. 只构建系统 prompt 和最新用户消息
  String systemPrompt = promptManager.buildSystemPrompt(...);
  UserMessage userMsg = new UserMessage(request.getMessages().getLast().getContent());

  ChatClient prompt = chatClient.prompt()
      .system(systemPrompt)
      .messages(userMsg)                    // 只传当前用户消息
      .advisorParams(spec -> spec           // 传递上下文给 advisor
          .param("sessionId", sessionId)
          .param("modelName", modelName))
      ...;
```

- 清理 `buildMessages()` 方法中不再需要的逻辑（或保留但简化）

### Step 6：Dashboard API（1d）

创建 `ChatSessionController.java`：

```java
@RestController
@RequestMapping("/api/v1/ai/chat")
public class ChatSessionController {

    @GetMapping("/sessions")
    public ApiResponse<List<ChatSessionVO>> listSessions(
        @RequestParam String agentType,
        @RequestParam(defaultValue = "1") int page,
        @RequestParam(defaultValue = "20") int size) {
        // 从 SecurityContext 获取当前用户
        Long userId = getCurrentUserId();
        return ApiResponse.success(sessionService.listSessions(userId, agentType, page, size));
    }

    @PostMapping("/sessions")
    public ApiResponse<ChatSessionVO> createSession(@RequestBody CreateSessionRequest request) {
        Long userId = getCurrentUserId();
        ChatSession session = sessionService.createSession(userId, request.getAgentType(), request.getModelName());
        return ApiResponse.success(convertToVO(session));
    }

    @PutMapping("/sessions/{id}/title")
    public ApiResponse<Void> updateTitle(@PathVariable Long id, @RequestBody UpdateTitleRequest request) {
        checkOwnership(id);
        sessionService.updateTitle(id, request.getTitle());
        return ApiResponse.success();
    }

    @DeleteMapping("/sessions/{id}")
    public ApiResponse<Void> deleteSession(@PathVariable Long id) {
        checkOwnership(id);
        sessionService.deleteSession(id);
        return ApiResponse.success();
    }

    @GetMapping("/sessions/{id}/messages")
    public ApiResponse<List<ChatMessageVO>> loadMessages(@PathVariable Long id) {
        checkOwnership(id);
        List<ChatMessage> messages = messageService.loadMessages(id);
        return ApiResponse.success(convertToVOList(messages));
    }
}
```

### Step 7：DiagramController 适配（0.5d）

- 修改 `DrawChatRequest`：将 `sessionId` 从可选改为在创建新会话时自动生成
- 修改 `DiagramController.chat()`：创建新会话时调用 `chatMemoryStore.getOrCreateSession()`
- 修改 SSE done 事件，返回 `sessionId`

```java
// DiagramController 中
@PostMapping("/chat")
public SseEmitter chat(@RequestBody DrawChatRequest request,
                       @AuthenticationPrincipal UserDetails user) {
    // 如果没有 sessionId，创建新会话
    if (request.getSessionId() == null) {
        ChatSession session = chatMemoryStore.getOrCreateSession(
            user.getId(), "draw", getCurrentModel());
        request.setSessionId(String.valueOf(session.getId()));
    }
    // 后续流程不变
    ...
}
```

### Step 8：前端适配（2d）

见下一节。

---

## 6. 前端适配

### 6.1 新增 API 调用

```typescript
// services/chatHistory.ts

export interface ChatSessionVO {
  id: number;
  title: string;
  agentType: string;
  messageCount: number;
  previewText: string;
  createdAt: string;
  updatedAt: string;
}

export interface ChatMessageVO {
  id: number;
  role: 'user' | 'assistant' | 'tool';
  content: string;
  reasoningContent?: string;
  toolCalls?: { id: string; name: string; arguments: Record<string, unknown> }[];
  msgIndex: number;
  createdAt: string;
}

// 会话列表
export function fetchSessions(agentType: string, page = 1, size = 20): ApiResponse<ChatSessionVO[]> {
  return request.get('/api/v1/ai/chat/sessions', { params: { agentType, page, size } });
}

// 创建会话
export function createSession(agentType: string, modelName?: string): ApiResponse<ChatSessionVO> {
  return request.post('/api/v1/ai/chat/sessions', { agentType, modelName });
}

// 更新标题
export function updateSessionTitle(id: number, title: string): ApiResponse<void> {
  return request.put(`/api/v1/ai/chat/sessions/${id}/title`, { title });
}

// 删除会话
export function deleteSession(id: number): ApiResponse<void> {
  return request.delete(`/api/v1/ai/chat/sessions/${id}`);
}

// 加载消息
export function fetchMessages(sessionId: number): ApiResponse<ChatMessageVO[]> {
  return request.get(`/api/v1/ai/chat/sessions/${sessionId}/messages`);
}
```

### 6.2 DrawPage 改造

现有 `DrawPage.tsx` 中使用 `messages = useState<DisplayMessage[]>([])` 纯内存管理。

改造点：

```
1. 页面加载时：
   → 调用 fetchSessions('draw') 获取会话列表
   → 如果有会话，默认选中最后一个活跃会话
   → 调用 fetchMessages(sessionId) 加载历史消息

2. 发起新对话时：
   → 调用 createSession('draw') 创建新会话
   → 将 sessionId 传入 drawChatStream()

3. 发送消息时：
   → drawChatStream() 请求体携带 sessionId（不再携带全量 messages）
   → 后端 advisor 自动加载历史

4. 收到 SSE 响应时：
   → 同现有逻辑：逐块追加到 messages 状态
   → （无需额外持久化操作，后端已自动保存）

5. UI 新增：会话侧栏
   → 左侧或下拉展示会话列表
   → 点击切换会话
   → 当前会话标题可编辑
   → 支持删除会话
```

### 6.3 UI 布局草图

```
┌──────────────────────────────────────────────────┐
│  ← 会话列表  │  绘图区域 (draw.io)  │  AI 聊天    │
│              │                      │             │
│  📁 历史会话  │                     │  ┌───────┐  │
│  ──────────  │   [draw.io editor]  │  │思考过程│  │
│  项目架构图  │                     │  └───────┘  │
│  ER 图设计   │                     │  消息1...    │
│  登录流程    │                     │  消息2...    │
│              │                     │             │
│  [新对话]    │                     │  [输入框]   │
└──────────────────────────────────────────────────┘
```

### 6.4 DrawChatRequest 改造

```typescript
// 改造前：每次都发完整 messages
interface DrawChatRequest {
  messages: ChatMessageDTO[];     // 完整历史 + 最新消息
  xml?: string;
  sessionId?: string;             // 可选，透传
}

// 改造后：只发最新消息 + sessionId
interface DrawChatRequest {
  messages: ChatMessageDTO[];     // 只包含最新一条用户消息
  xml?: string;
  sessionId: string;              // 必填，会话标识
}
```

### 6.5 SSE 响应调整

后端的 `done` 事件现在返回完整的 `sessionId`：

```json
{"type": "done", "sessionId": "42"}
```

前端保存此 `sessionId` 到会话列表状态中。

---

## 7. 测试策略

### 7.1 单元测试

| 测试目标 | 内容 | 工具 |
|---------|------|------|
| ChatSessionMapper | CRUD + 列表查询 | MyBatis Spring Test + H2 内存库 |
| ChatMessageMapper | 插入、批量插入、按 session 查询、maxMsgIndex | 同上 |
| ChatSessionService | 创建、权限校验、状态变更 | Mock Mapper |
| ChatMessageService | 保存、加载、批量保存 | Mock Mapper |
| ModelMessageConverter (DeepSeek) | toContextMessage / toStorageMessage 转换逻辑 | Mock Spring AI Message |
| ChatHistoryAdvisor | before/after 逻辑、converter 路由、历史加载过滤 | Mock ChatMemoryStore + Mock ModelMessageConverter |

### 7.2 集成测试

| 测试目标 | 内容 |
|---------|------|
| 完整消息保存 + 加载 | 模拟一次对话，存 5 条消息，加载验证顺序和内容 |
| DeepSeek 特殊字段 | 保存含 reasoning_content 的消息，加载验证字段完整 |
| Tool call 场景 | 保存 assistant(tool_call) + tool(result) + assistant(final)，加载验证 |
| 会话管理 | 创建 → 列表 → 详情 → 删除 |

### 7.3 手动测试

| 测试场景 | 验证点 |
|---------|--------|
| 新会话 → 发送消息 → 刷新 → 恢复 | 历史消息完整显示 |
| 多轮对话 → 查看数据库 | `msg_index` 连续递增 |
| 流式响应 → 断连 → 重连 | 已保存的消息不丢失 |
| 工具调用（画图）→ 查看消息 | tool_calls JSON 正确存储 |

---

## 8. 验收标准

1. ✅ 用户在 DrawPage 中发送一条消息，刷新页面后消息仍在
2. ✅ 会话列表显示所有历史会话，按时间倒序
3. ✅ 点击历史会话可加载完整对话记录
4. ✅ DeepSeek 的 `reasoning_content` 正常存储（数据库可见）
5. ✅ 工具调用消息（display_diagram）正常存储
6. ✅ 新建会话不影响旧会话
7. ✅ 删除会话后不再出现在列表中
8. ✅ 覆盖边界：空会话、超长消息、多轮 tool call

---

## 9. 风险与注意事项

| 风险 | 影响 | 缓解措施 |
|------|------|---------|
| Spring AI Advisor API 升级变化 | `ChatHistoryAdvisor` 需适配 | 只依赖 `BaseAdvisor` 接口，隔离变化 |
| Reactor 流式场景跨线程 | `doOnComplete` 在 Reactor 线程执行 | **禁止 ThreadLocal**，用局部变量捕获 `capturedSessionId` |
| 流式场景下消息聚合复杂 | 消息可能不完整 | 使用 `doOnComplete` 兜底 + 超时强制保存 |
| 高并发下 `msg_index` 冲突 | 序号错乱 | 使用 `SELECT MAX(msg_index)` + 事务隔离 |
| Spring AI 版本升级导致模型特定类变更 | converter 需要同步更新 | converter 是唯一受影响点，基础架构不受影响 |
| 新增模型时忘记注册 converter | 新模型无历史记忆 | 启动时检测：所有配置的模型都有对应 converter |
