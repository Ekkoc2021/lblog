# P1 分隔符设计：上下文结构化排版

> 目标：利用 LLM 注意力机制的首尾效应，通过 XML/Markdown 分隔符优化上下文结构
> 状态：⏳ 待实现
> 前置依赖：P0（ChatHistoryAdvisor 基础）

---

## 一、问题

当前上下文的组织方式是**消息类型驱动**的（SystemMessage、UserMessage、AssistantMessage 等 Spring AI 原生类型），但各模块各自拼内容，缺乏统一的排版策略：

```
SystemMessage:  systemPrompt + xmlContext              ← PromptManager 负责
SystemMessage:  ...（ChatHistoryAdvisor 追加内容）      ← 历史注入
UserMessage:    用户问题
AssistantMessage + ToolResponseMessage: 工具调用循环    ← DeepSeekToolCallAdvisor 负责
```

几个问题：
1. **所有内容混在一起** — 模型需要自己分辨哪里是指令、哪里是工具结果
2. **首尾没有利用** — 最重要的系统指令和当前任务没有放在最优位置
3. **格式不统一** — XML 用 ````xml` 包裹，历史是纯文本，工具结果是 JSON，风格不一致
4. **中间信息被弱化** — 长对话时旧历史在中间，LLM 容易忽略

---

## 二、背景：注意力机制

LLM 的注意力分布存在明显的 **首尾偏差**（Primacy & Recency Effect）：

```
高注意力    低注意力        高注意力
  ┌──────┬──────────────────┬──────┐
  │ 开头  │     中间         │ 结尾  │
  │ 20-30%│     10-15%       │ 20-30%│
  └──────┴──────────────────┴──────┘
```

- **Primacy（首因效应）** — 上下文开头的 token 获得最多注意力 → 放最重要的系统指令
- **Recency（近因效应）** — 上下文末尾的 token 注意力次高 → 放当前任务和最新工具结果
- **Lost in the Middle** — 中间段落的注意力显著下降 → 放历史对话和旧文档

**应用到上下文排版：**

```
高注意力 ← ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ → 高注意力
┌──────────────────────────────────────────┐
│ 系统指令 / 角色定义              (Primacy)│
│──────────────────────────────────────────│
│ 历史对话（旧 → 新）                      │
│ 旧文档 / 引用                          │  ← Lost in the Middle
│──────────────────────────────────────────│
│ 最新工具结果                            │
│ 当前用户问题 / 结构化输出指令  (Recency)  │
└──────────────────────────────────────────┘
```

---

## 三、分隔符格式选择

### 候选方案

| 方案 | 示例 | Token 开销 | 模型兼容 | 可读性 |
|------|------|-----------|---------|--------|
| **XML tags** | `<system>...</system>` | ~8 token/对 | DeepSeek 原生支持 XML | 一般 |
| **Markdown 标题** | `## System` | ~4 token/个 | 几乎所有模型 | 好 |
| **混合方案（推荐）** | Markdown 标题外层 + XML 内容级 | ~6 token/段 | 兼顾两者 | 好 |

### 推荐：Markdown 外层 + XML 内容级

- **段落级用 Markdown 标题**（`## System`、`## History`）— 可读性好，token 开销低
- **内容内嵌用 XML 标签**（`<document id="1">`）— 精确标记引用范围
- **空行分隔段落** — 最简单的分隔，不占用 token

示例：
```
## System

You are a diagram expert...

## History

<message role="user">帮我画流程图</message>
<message role="assistant" thinking="true">...
<diagram>...</diagram></message>

## Tool Results

<tool name="displayDiagram" status="ok">
  <output>Diagram displayed</output>
</tool>

## Current Task

用户要求：修改上面的流程图，添加登录模块
```

---

## 四、上下文布局设计

### 标准布局（按注意力分布排列）

```
位置     Section                    注意力收益
──┼─────────────────────────┼───────────────
▲ │ ## System                ★ Primacy 最佳
│ │   [角色定义 + 约束条件]
│ │
│ │ ## Documents (可选)       ★ Primacy 次佳
│ │   <doc id="1">...</doc>   只在有外部文档时出现
│ │
│ │ ## History                △ 中间段
│ │   [历史消息，旧→新]
│ │
│ │ ## Tool Results (可选)    △ 中间段
│ │   [最近工具调用结果]       只在有工具调用时出现
│ │
▼ │ ## Current Task           ★ Recency 最佳
  │   [用户最新消息]           紧贴模型生成位置
──┴─────────────────────────┴───────────────
```

### 各 Section 说明

| Section | 必选 | 内容来源 | 位置策略 |
|---------|------|---------|---------|
| **System** | 是 | PromptManager + SkillSystemPromptBuilder | 开头，Primacy 最佳位置 |
| **Documents** | 否 | 外部知识/引用（P3 RAG 后启用） | System 之后，History 之前 |
| **History** | 是 | ChatHistoryAdvisor 加载的历史消息 | 中间段，按时间顺序 |
| **Tool Results** | 否 | DeepSeekToolCallAdvisor 的调用结果 | History 之后，Current Task 之前 |
| **Current Task** | 是 | 用户最新提问 + 结构化输出指令 | 末尾，Recency 最佳位置 |

### 布局策略在不同场景的应用

**场景 A：普通对话（无工具、无文档）**
```
## System
...
## History
...
## Current Task
...
```

**场景 B：画图对话（有工具、有文档）**
```
## System
...
## History
...
## Tool Results
  <tool name="displayDiagram">...</tool>
## Current Task
  用户: 帮我修改刚才的图
```

**场景 C：首次对话（无历史）**
```
## System
...
## Current Task
  用户: 帮我画一个登录页面
```

---

## 五、实现方案

### 整体架构

```
PromptManager.buildSystemPrompt()          → 注入 ## System 内容
ChatHistoryAdvisor.before()                → 注入 ## History + ## Tool Results
DeepSeekToolCallAdvisor                    → 工具结果格式化为 XML 标签
DiagramService / Controller                → 最新用户消息作为 ## Current Task
```

### 5.1 System Prompt 改造

**当前**（无分隔符）：
```java
String systemPrompt = promptManager.buildSystemPrompt(null, minimalStyle);
```

**改造后**：
```java
String systemPrompt = "## System\n\n"
    + promptManager.buildSystemPrompt(null, minimalStyle) + "\n\n"
    + skillHintBuilder.buildAvailableSkillsHint(agentType);
```

输出：
```
## System

You are a diagram expert...
Constraints: ...
Available skills: draw-expert(loadSkill("draw-expert"))
```

### 5.2 ChatHistoryAdvisor 改造

**当前**：历史消息直接拼入 messages 数组

**改造后**：在历史序列前后追加分隔符

```java
// ChatHistoryAdvisor 的 before() 中
var historyMessages = loadHistory(sessionId);
if (!historyMessages.isEmpty()) {
    // 在历史消息前后添加标记消息
    var delimiter = new SystemMessage("## History");
    // 将 delimiter + historyMessages + delimiter 拼入 prompt
}
```

更好的方式：在注入历史前插入一条 SystemMessage（纯分隔符），不作为历史内容，只作为排版标记。

但 Spring AI 的 advisor 机制对 messages 列表有约束。更实际的做法是**在 system prompt 末尾注入历史预览**，或者**修改第一条历史消息加前缀**。

**推荐方案：** 在 system prompt 中加 `## History` 锚点，然后通过修改第一条历史消息（UserMessage）的内容：

```java
// ChatHistoryAdvisor 中
List<Message> history = loadHistory(sessionId);
if (!history.isEmpty()) {
    // 找到第一个 user message，在其内容前插入 ## History 标记
    Message first = history.get(0);
    if (first instanceof UserMessage um) {
        String prefixed = "## History\n\n<history>\n" + um.getText();
        // 替换 first 为带标记的消息
    }
    // 最后一个消息后追加 </history>
    Message last = history.get(history.size() - 1);
    // 追加 </history> 标记
}
```

但这种方式对 Message 不可变对象不太友好。**更简洁的方式：**

**在 system prompt 末尾声明 context structure（零侵入）：**

```java
String systemPrompt = "## System\n\n..."
    + "\n\n## History\n\n<history>\n"
    + formatHistory(historyMessages)
    + "\n</history>\n\n"
    + "## Current Task\n\n";
```

即把 history 的格式化从 advisor 层移到 system prompt 构造层。但这样跟 ChatHistoryAdvisor 的分工就变了。

**实际上最干净的方案：**

**新增一个 `ContextStructureAdvisor`，放在 advisor 链最前面，负责在上下文中插入分隔符。**

```
ChatHistoryAdvisor  →  ContextStructureAdvisor  →  DeepSeekToolCallAdvisor
（加载历史）           （插入分隔符排版）           （工具执行）
```

但这个方向又回到了增加 advisor 的老路，与当前"精简"的理念冲突。

**折中方案：在 PromptManager 中做格式化**

```java
// DrawPromptManager 负责构建完整的上下文结构
public String buildStructuredContext(String systemPrompt, 
                                      String history, 
                                      String toolResults, 
                                      String currentTask) {
    return "## System\n\n" + systemPrompt + "\n\n"
         + (history != null ? "## History\n\n" + history + "\n\n" : "")
         + (toolResults != null ? "## Tool Results\n\n" + toolResults + "\n\n" : "")
         + "## Current Task\n\n" + currentTask;
}
```

这样 PromptManager 成为唯一的上下文排版入口，advisor 层只管加载数据，不管格式。

### 5.3 推荐实现路线

考虑到改动最小化，分两步走：

**Step 1：System + Current Task 分隔（低风险，立即收益）**

只在 `DiagramService` / `PromptManager` 层面改造，不涉及 advisor：

```java
// DrawPromptManager.buildSystemPrompt() 输出中加入标题
"## System\n\n" + systemInstructions + "\n\n"
+ (skillHint != null ? skillHint + "\n\n" : "")
+ "## Current Task\n\n"
```

改动范围：仅 `PromptManager.java`
收益：最重要的两个 Section 获得 Primacy + Recency 位置

**Step 2：History + Tool Results 包裹（改动稍大）**

- 在 `ChatHistoryAdvisor.before()` 中，将加载的历史消息用 `## History\n\n<history>...</history>` 包裹
- 在 `DeepSeekToolCallAdvisor` 中，工具调用结果格式化为 `## Tool Results\n\n<tool>...</tool>`

### 5.4 代码改动清单

| 文件 | 改动 |
|------|------|
| `PromptManager.java` | `buildSystemPrompt()` 输出追加 `## System\n\n` + `## Current Task` |
| `ChatHistoryAdvisor.java` | `before()` 中对 history 做标记包裹 |
| `DeepSeekToolCallAdvisor.java` | 工具结果添加 `## Tool Results` 标记 |
| `DiagramService.java` | 配合 PromptManager 调整 context 拼接 |

---

## 六、Token 开销评估

| Section | 分隔符内容 | Token 数 |
|---------|-----------|---------|
| `## System` | 3 token | 稳定 |
| `## History` + `<history></history>` | 6 token | 稳定 |
| `## Tool Results` + `<tool></tool>` | 6 token | 每次工具调用 |
| `## Current Task` | 3 token | 稳定 |
| **每轮对话总开销** | **~18 token** | 可忽略 |

对比每轮 2000-4000 token 的总消耗，分隔符开销不到 1%。

---

## 七、验收标准

- [ ] PromptManager 输出的 system prompt 以 `## System` 开头
- [ ] 用户消息前有 `## Current Task` 标记
- [ ] 有历史时以 `## History\n\n<history>` 开始
- [ ] 有工具结果时以 `## Tool Results` 标记
- [ ] 分隔符不影响正常对话（模型正确理解内容边界）
- [ ] 首次对话（无历史）不出现 `## History` 空段
- [ ] Token 开销在预期范围内（< 20 token / 轮）

---

## 八、相关文档

- [P1 路线图](../5-17/context-engineering-roadmap.md)
- [P1 现状总览](./p1-skill-status.md)
- [AI 模块架构总结](../5-17/ai-module-summary.md)
