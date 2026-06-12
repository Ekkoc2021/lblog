# AI 模块

## 这个模块做什么

把 AI 能力接入博客后台，当前落地的场景是 **AI 绘图**（自然语言描述 → draw.io 图表）。背后的技术骨架是一套 **上下文工程体系**：让 AI 有记忆（对话持久化）、能按需加载技能（Skill 系统）、上下文不越界（窗口压缩）。

## 架构全景

```
用户请求 (SSE)
    │
    ▼
DiagramController ──→ DiagramService (@Async 虚拟线程)
    │                      │
    │                      ├─ PromptManager.buildSystemPrompt()
    │                      │     └─ AiPromptService (DB → 文件 → 硬编码)
    │                      │
    │                      └─ ChatClient.stream()
    │                           │
    │                           ▼ Advisor 链（顺序执行）
    │                      ┌────────────────────────────────────┐
    │                      │ ChatHistoryAdvisor                 │  加载 DB 历史
    │                      │   → 注入历史消息 + 当前用户消息       │  保存新消息
    │                      ├────────────────────────────────────┤
    │                      │ DeepSeekToolCallAdvisor            │  工具调用循环
    │                      │   → 流式 chunk 透传                  │  reasoning_content 捕获
    │                      │   → 递归执行工具 → 拼回历史           │
    │                      ├────────────────────────────────────┤
    │                      │ CompressionAdvisor                 │  token 超限 → 压缩
    │                      │   → SlidingWindowStrategy           │  保留最近 N 条
    │                      └────────────────────────────────────┘
    │                           │
    │                           ▼
    │                      DeepSeek API
    │
    └─ SSE 事件 → 前端（text-delta / reasoning / tool-call / done）
```

## 模块地图

```
ai/
├── config/                          # 全局配置
│   └── AiConfig.java                # ChatClient Bean + advisor 链组装
│
├── agent/draw/                      # 业务 Agent：AI 绘图
│   ├── DiagramController.java       # SSE 端点 /api/v1/draw/chat
│   ├── DiagramService.java          # 核心编排（流式 + 非流式）
│   ├── DisplayDiagramTool.java      # @Tool display_diagram
│   ├── PromptManager.java           # 系统提示词组装
│   ├── config/                      # 线程池 + 心跳 + 限流
│   └── ...
│
├── prompt/                          # 公共提示词管理系统
│   ├── AiPromptService.java         # DB 优先 + 文件兜底 + 缓存
│   ├── FilePromptLoader.java        # classpath:prompts/ 文件加载
│   ├── AdminPromptController.java   # 管理端 CRUD + 审计
│   └── ...                          # 详见 02-提示词管理.md
│
├── conversation/                    # P0：对话持久化
│   ├── domain/                      # ChatSession + ChatMessage 实体
│   ├── mapper/                      # MyBatis CRUD
│   ├── service/                     # 会话/消息 Service
│   └── controller/                  # REST API /api/v1/ai/chat/sessions
│
├── memory/                          # 上下文记忆层
│   ├── ChatHistoryAdvisor.java      # 历史加载 + 消息保存
│   ├── CompressionAdvisor.java      # 压缩入口
│   ├── converter/                   # 模型消息双向转换
│   │   ├── ModelMessageConverter.java  # 策略接口
│   │   └── deepseek/DeepSeekMessageConverter.java
│   ├── compression/
│   │   ├── CompressionStrategy.java    # 策略接口
│   │   └── SlidingWindowStrategy.java  # 滑动窗口
│   ├── estimator/
│   │   ├── TokenEstimator.java         # token 估算接口
│   │   └── CharBasedTokenEstimator.java # 字符/2 估算
│   └── ChatMemoryStore.java + Impl  # 存储抽象层
│
├── skill/                           # P1：技能包系统
│   ├── LoadSkillTool.java           # @Tool loadSkill
│   ├── SkillSystemPromptBuilder.java # 技能列表提示（⚠️ 未接入）
│   ├── SkillService.java            # 技能包缓存查询
│   └── ...
│
└── advisor/                         # DeepSeek 专用 advisor
    └── DeepSeekToolCallAdvisor.java  # 流式工具调用 + reasoning 捕获

# 还需要关注 Spring AI 包路径下的补丁文件：
src/main/java/org/springframework/ai/deepseek/
└── DeepSeekReasoningChatModel.java   # 修复 createRequest 丢失 reasoningContent
```

## 六层 Adivsor 链（核心机制）

Spring AI 的 Advisor 链是"拦截器模式"，在每次 LLM 调用前后执行。本项目用 3 个 advisor 串起了整个上下文管理：

| 顺序 | Advisor | 做了什么 | 为什么需要 |
|------|---------|---------|-----------|
| 1st | ChatHistoryAdvisor | 从 DB 加载历史消息 → 拼入 prompt；调用结束后保存新消息 | 让 AI 记住上一轮说了什么 |
| 2nd | DeepSeekToolCallAdvisor | 拦截流式 chunk → 检测 tool call → 执行工具 → 递归调用 | 让 AI 能画出图来 |
| 3rd | CompressionAdvisor | 检查 token 用量 → 超限则压缩 → 保留最近 N 条消息 | 防止上下文越聊越大直到爆窗口 |

三个 advisor 通过 `AiConfig.drawChatClient()` 按序组装：

```java
chatClient.Builder.clone()
    .defaultAdvisors(
        chatHistoryAdvisor,          // HIGHEST_PRECEDENCE
        deepSeekToolCallAdvisor,     // +2
        compressionAdvisor           // +3
    )
    .build();
```

关键设计：**压缩只发生在 LLM 调用前**，历史保存和工具调用逻辑与压缩完全解耦。

## 学习路线

按"问题→方案→实现"的顺序，建议这样阅读：

```
01-架构总览.md          ← 这篇读完再读后面
    │
    ├─ 02-提示词管理.md  ← 横切基础：AI 说什么话谁说了算
    ├─ 03-对话持久化.md  ← P0：从无状态到有状态
    ├─ 04-Skill技能系统.md ← P1：让 AI 能加载额外能力
    ├─ 05-上下文窗口管理.md ← P2：上下文别爆
    ├─ 06-模型适配层.md    ← DeepSeek + Spring AI 的坑
    └─ 07-绘图Agent.md     ← 业务落地：SSE + tool call + 心跳
```

没有严格的前后依赖，但对新人来说按数字顺序读最自然。

## 已知问题快览

梳理过程中发现的问题，详见 [08-技术债务与问题清单.md](08-技术债务与问题清单.md)：

| 级别 | 数量 | 典型问题 |
|------|------|---------|
| 🔴 Bug | 1 | SkillSystemPromptBuilder 未接入 — LLM 不知道有哪些 skill 可用 |
| 🟡 缺失 | 2 | Skill Admin API 未实现、countList 接口缺失 |
| 🟢 改进 | 若干 | DrawRateLimiter 硬编码、PromptManager 模型匹配策略脆弱等 |

## 关键设计决策

### Skill = Prompt（而非工具过滤器）

早版方案是"按 skill 控制工具可见性"（需要 `@SkillTool` 注解 + `SkillAwareToolCallAdvisor`），后来发现过度设计。当前方案：skill 只是一段 prompt 文本，LLM 通过 `loadSkill` tool 自行按需加载。代码从 6+ 个组件简化到 4 个文件。

### DB 优先 + 文件兜底（提示词管理）

`AiPromptService.getPrompt("draw", "system-default")` 先查 DB，没有就加载 classpath 下的 md 文件，再没有就报错。好处：生产环境通过管理端热修改即时生效，开发环境直接编辑 markdown 快速调试。

### 模型无关（消息转换器策略）

`ModelMessageConverter` 接口隔离了不同 LLM 的消息格式差异。DeepSeek 的 `reasoningContent` 通过 `DeepSeekMessageConverter` 处理，未来加 Claude/OpenAI 只需加一个实现类。

### 压缩策略不感知 token（职责上收）

`CompressionStrategy` 只管"怎么裁切消息"，不管 token 预算。`CompressionAdvisor` 持有 `TokenEstimator`，循环调用 `strategy.tryCompress()` 直到预算内。这样换一种压缩策略（比如从"滑动窗口"换成"摘要压缩"）只改一个类。

## 相关目录

- `src/main/resources/prompts/draw/` — 提示词 md 文件（DB 兜底）
- `src/main/resources/com/yang/lblogserver/ai/` — MyBatis Mapper XML
- `spring-ai-custom/` — Spring AI 补丁（同包名重写，等项目升级后删除）
