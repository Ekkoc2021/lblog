# next-ai-draw-io 架构分析

> 来源：`E:\workspace\java\lblog\next-ai-draw-io\`  
> 目标：将 AI 绘图能力整合到 lblog 后端（Spring Boot）  
> 本文档保留源码行号，便于实现时回查

---

## 1. 整体流程

```
用户输入 "画一个微服务架构图"
        │
        ▼
┌──────────────────────────────────────────┐
│ 拼接 System Prompt                        │
│                                          │
│ 块① 角色 + 行为约束 + Tool说明 +          │
│      布局规则 + Edge路由 + XML规范        │
│ 块② 当前图表 XML（运行时注入）            │
│ 块③ 用户输入文本                          │
└──────────────────────────────────────────┘
        │
        ▼
   AI 调用 tool（function calling）
   输出结构化 JSON
        │
        ▼
   前端 react-drawio 渲染 XML
        │
        ▼
   (可选) VLM 视觉校验
   截图 → 视觉模型检查 → 不通过则重新生成
```

核心三步闭环：**Prompt 拼接 → AI 输出 XML → 前端渲染**。XML 是 AI 和画布之间的中间语言。

---

## 2. 多轮对话的 XML 传递机制

### 请求入口

> `app/api/chat/route.ts:867-869`

```typescript
export async function POST(req: Request) {
    return observedHandler(req)
}
```

### 请求体结构

> `app/api/chat/route.ts:93-98`

```typescript
const body = await req.json()
const { messages, xml, previousXml, sessionId } = body
```

### 前端如何发送

> `components/chat-panel.tsx:1056-1113` — `sendChatMessage()`  
> `components/chat-panel.tsx:850-863` — 发送前获取 previousXml 并保存快照

```typescript
// 每次发消息都带当前 chartXML
const previousXml = snapshotKeys.length > 0
    ? xmlSnapshotsRef.current.get(snapshotKeys[0]) || ""
    : ""
sendChatMessage(parts, chartXml, previousXml, sessionId)
```

### 每轮对话的实际结构

```
Turn 1:
┌─ System: 系统指令 + "当前图表 XML: (空)"
├─ User:   "画一个登录流程图"
└─ AI:     display_diagram(xml) → 前端渲染

Turn 2:
┌─ System: 系统指令 + "当前图表 XML: <mxCell id='2' ...>"
├─ AI 历史: [上一轮 tool call XML 被替换为占位符]
├─ User:   "把登录改成邮箱验证码登录"
└─ AI:     display_diagram(xml) 重新生成整张图

Turn 3:
┌─ System: 系统指令 + "当前图表 XML: <mxCell ...> (第二版)"
├─ AI 历史: [前两轮 tool call XML 都是占位符]
├─ User:   "加一个注册流程"
└─ AI:     display_diagram(xml) 再生成
```

关键设计：
- **对话历史里的旧 XML 被替换为 `[XML content replaced]`** — `lib/chat-helpers.ts:53-89` `replaceHistoricalToolInputs()`
- **每轮最新的图表 XML 走 system message 注入**，是"唯一真相来源"
- AI 看到的永远是：系统指令 + 当前图表 XML + 对话历史（不含旧 XML）+ 当前用户输入

---

## 3. 四个 Tool 设计

### 总览

| Tool | 用途 | v1 必要性 | 定义位置 |
|------|------|----------|---------|
| `display_diagram` | 生成全新图表，AI 输出完整 mxCell XML | **必须** | `app/api/chat/route.ts:608-645` |
| `edit_diagram` | 增量编辑，按 cell_id 增/删/改 | v2 | `app/api/chat/route.ts:646-686` |
| `append_diagram` | 截断续写，token 限制时续传 | 可跳过 | `app/api/chat/route.ts:688-707` |
| `get_shape_library` | 查图标库文档 | v2 | `app/api/chat/route.ts:708-770` |

### 为什么 AI 几乎总是用 display_diagram

不是 bug，是 AI 的理性选择：

| | display_diagram | edit_diagram |
|---|---|---|
| 做什么 | 输出完整 XML | 找 cell_id → JSON → 转义 XML |
| 要理解什么 | 用户需求 | 用户需求 + 当前图表 XML 结构 |
| 出错概率 | 低 | 高（id找不到、转义错、JSON格式错） |
| AI 偏好 | **高** | 低 |

代码没有 `toolChoice` 强制，AI 完全自由选择。`display_diagram` 对 AI 来说更可靠。

### display_diagram

> Tool 定义: `app/api/chat/route.ts:608-645`  
> 前端执行: `hooks/use-diagram-tool-handlers.ts:126-374`

AI 输出 `{xml: "<mxCell id='2'.../>"}`，前端调 `loadDiagram(wrapWithMxFile(xml))` 渲染。

### edit_diagram（增量编辑原理）

> Tool 定义: `app/api/chat/route.ts:646-686`  
> 前端执行: `hooks/use-diagram-tool-handlers.ts:376-491`  
> DOM 操作: `lib/utils.ts:498-597` `applyDiagramOperations()`

前端用 DOM API 直接操作 XML，不是 AI 操作：

```
AI 输出:
{"operations": [
  {"operation": "update", "cell_id": "3", "new_xml": "<mxCell id='3' ...>"},
  {"operation": "delete", "cell_id": "5"},
  {"operation": "add",    "cell_id": "new1", "new_xml": "<mxCell id='new1' ...>"}
]}

前端 applyDiagramOperations() [lib/utils.ts:498]:
  1. DOMParser 解析当前 XML
  2. querySelectorAll("mxCell") → Map<id, Element>
  3. update → parentNode.replaceChild(importedNode, existing)
     delete → element.remove() + 级联删子节点和连线
     add    → root.appendChild(importedNode)
  4. XMLSerializer 序列化 → loadDiagram(新XML)
```

### get_shape_library

> Tool 定义 + 执行体: `app/api/chat/route.ts:708-770`  
> 库文件: `docs/shape-libraries/` (31 个 md)

本质是后端读文件，`execute` 闭包内直接 `fs.readFile()`。31 个库文件含上万图标名。

### append_diagram

> Tool 定义: `app/api/chat/route.ts:688-707`  
> 前端执行: `hooks/use-diagram-tool-handlers.ts:493-577`

`max_tokens` 设够大（如 16000）就不会触发，可跳过。

---

## 4. 提示词系统（6 层结构）

> 核心文件: `lib/system-prompts.ts` (21KB, 411 行)

### 第 1 层：角色设定 + 行为约束

> `lib/system-prompts.ts:10-18` `DEFAULT_SYSTEM_PROMPT` 开头

```text
You are an expert diagram creation assistant specializing in draw.io XML generation.
ALWAYS respond in the same language as the user's last message.

When asked to create a diagram, briefly describe your plan (2-3 sentences max),
then use tool. After generating, DON'T describe it - the user can see the diagram.

NEVER return raw XML in text responses — only via tool calls.
NEVER include XML comments (<!-- -->) — draw.io strips them.
```

### 第 2 层：布局硬约束

> `lib/system-prompts.ts:77-86`

```text
- CRITICAL: 所有元素在一个页面视口内，避免分页线
- x: 0~800, y: 0~600
- 容器最大宽度: 700px, 最大高度: 550px
- 从合理边距开始 (x=40, y=40)
- 大图用垂直堆叠或网格布局，避免水平散开
```

### 第 3 层：Edge 路由规则（7 条）

> `lib/system-prompts.ts:141-187` — 这是踩坑最多的部分

```
Rule 1: 多条边不能共享同一路径 → 不同 exitY/entryY
Rule 2: 双向连接用对立面 → A→B 右侧出，B→A 左侧出
Rule 3: 每条边显式指定 exitX/exitY/entryX/entryY
Rule 4: 连线必须绕过中间形状（waypoint 绕障），加 20-30px 边距
Rule 5: 布局前规划，预留连线通道，间距 150-200px
Rule 6: 复杂路由用 2-3 个 waypoint 做 L 形/U 形路径
Rule 7: 选择自然连接方向，禁止角落连接（entryX=1,entryY=1 是错误的）
```

### 第 4 层：XML 格式规范

> `lib/system-prompts.ts:109-131`

```text
CRITICAL RULES:
1. 只输出 mxCell 元素 - 不输出 <mxfile>/<mxGraphModel>/<root>
2. 不输出 root cell (id="0"/id="1") - 前端自动补
3. 所有 mxCell 必须平级，禁止 mxCell 嵌套 mxCell
4. id 从 "2" 开始，唯一且连续
5. parent="1" 用于顶层，parent="<容器id>" 用于分组
6. 特殊字符转义: &lt; &gt; &amp; &quot;
7. 禁止 XML 注释 <!-- --> — draw.io 会吃掉
```

附带大量正确/错误示例：`lib/system-prompts.ts:113-131`（基础示例）、`:242-258`（swimlane 示例）、`:320-356`（edge 路由示例）。

### 第 5 层：运行时 XML 上下文

> `app/api/chat/route.ts:452-466` — 每次请求动态拼接

```text
Previous diagram XML: {previousXml}
Current diagram XML (AUTHORITATIVE): {xml}

IMPORTANT: Current XML 是唯一真相来源。
When using edit_diagram, COPY cell_id from Current XML.
历史对话中的旧 XML 已被替换为占位符，不要依赖。
```

支持多 system message 的 provider 分为两条（含 cachePoint），不支持的合并为一条：
> `app/api/chat/route.ts:468-496`

### 第 6 层：形状库文档（按需加载）

> Tool 定义: `app/api/chat/route.ts:708-770`  
> 库文件: `docs/shape-libraries/*.md` (31 个文件)  
> 示例: `docs/shape-libraries/aws4.md` (1032 个 AWS 图标)

### 风格模式

> `lib/system-prompts.ts:193-224`

- **Normal style**: 追加 `STYLE_INSTRUCTIONS`（`:194-199`）— 颜色、圆角、字体
- **Minimal style**: 前置 `MINIMAL_STYLE_INSTRUCTION`（`:202-224`）— 纯黑白、无样式

### Prompt 选择逻辑

> `lib/system-prompts.ts:375-410` `getSystemPrompt()`

根据 modelId 决定用 DEFAULT (~1900 tokens) 还是 EXTENDED (~4400 tokens，为 Opus/Haiku 4.5 的 4000 token cache 阈值优化)。

---

## 5. 消息拼装全过程

> `app/api/chat/route.ts:75-498` `handleChatRequest()`

### 请求体

```typescript
// route.ts:93-98
const { messages, xml, previousXml, sessionId } = body
const customSystemMessage = body.customSystemMessage  // 用户自定义，最多5000字符
```

### 用户输入包装

> `app/api/chat/route.ts:280-284`

```typescript
const formattedUserInput = `User input:
"""md
${userInputText}
"""`
```

### System prompt 组装

> `app/api/chat/route.ts:258-261`

```typescript
const systemMessage = getSystemPrompt(modelId, minimalStyle)  // 提示词文件
const finalSystemMessage = customSystemMessage
    ? `${systemMessage}\n\n## Custom Instructions\n${customSystemMessage}`
    : systemMessage
```

### 最终 messages 数组

> `app/api/chat/route.ts:468-498`

```
allMessages = [
  { role: "system", content: finalSystemMessage },    ← 提示词文件内容
  { role: "system", content: xmlContext },            ← 运行时 XML 上下文
  { role: "user", content: formattedUserInput },      ← 用户输入
  ...enhancedMessages                                   ← 历史对话(旧XML已替换)
]
```

### 历史 XML 替换

> `lib/chat-helpers.ts:53-89` `replaceHistoricalToolInputs()`

节省 token，防止 AI 基于旧 XML 做编辑。

---

## 6. AI 调用核心

> `app/api/chat/route.ts:500-775` — 你移植时最需要看的一段

```typescript
const result = streamText({
    model,                              // AI 模型实例
    abortSignal: req.signal,
    stopWhen: stepCountIs(5),           // 最多 5 步（防无限循环）
    experimental_repairToolCall: ...,   // 修复截断的 tool call JSON
    messages: allMessages,              // 拼好的 prompt
    tools: {                            // 4 个 tool
        display_diagram: { ... },
        edit_diagram: { ... },
        append_diagram: { ... },
        get_shape_library: { ... },
    },
    onFinish: ...,                      // 记录 token 用量
    temperature: ...,                   // 可选
})

return result.toUIMessageStreamResponse({
    sendReasoning: true,                // 流式推送 AI 推理过程
})
```

### Tool Call 修复

> `app/api/chat/route.ts:508-573` `experimental_repairToolCall`

AI 生成 tool call JSON 时被 token 限制截断，用 `jsonrepair` 尝试修复（补括号、修 `:=` → `: `、修正转义）。

### 消息过滤

> `app/api/chat/route.ts:327-360`

- 过滤空 content 的消息（Bedrock API 拒绝）
- 过滤 input 为空的 tool-call（中断的流式传输导致）

---

## 7. XML 校验修复管线

> 核心文件: `lib/utils.ts` (66KB)

```
AI 输出原始 XML
    │
    ▼
第1层: validateMxCellStructure()    ← DOMParser 语法检查 + 嵌套检查
    │ 失败            lib/utils.ts:879-983
    ▼
第2层: autoFixXml()                 ← 修重复属性/缺失parent/非法entity/重复id
    │ 仍失败          lib/utils.ts:985-1637
    ▼
第3层: isMxCellXmlComplete()        ← 检测是否被 token 限制截断
    │ 截断            lib/utils.ts:66-93
    ▼
第4层: append_diagram               ← 通知 AI 续写
    │ 完整
    ▼
第5层: VLM 视觉校验                 ← 截图给视觉模型检查（可选）
```

### 关键函数索引

| 函数 | 行号 | 用途 |
|------|------|------|
| `validateAndFixXml()` | `lib/utils.ts:1639` | 入口：校验+修复 |
| `validateMxCellStructure()` | `lib/utils.ts:879` | DOMParser 检查语法和结构 |
| `autoFixXml()` | `lib/utils.ts:985` | 自动修复常见错误 |
| `wrapWithMxFile()` | `lib/utils.ts:326` | 裸 mxCell 包装成完整 mxfile |
| `isMxCellXmlComplete()` | `lib/utils.ts:66` | 检测 XML 是否截断 |
| `extractDiagramXML()` | `lib/utils.ts:1676` | 从 draw.io SVG 导出中提取纯 XML |
| `applyDiagramOperations()` | `lib/utils.ts:498` | 增量编辑的 DOM 操作 |
| `convertToLegalXml()` | `lib/utils.ts:251` | XML 标准化 |
| `formatXML()` | `lib/utils.ts:202` | XML 格式化输出 |
| `isRealDiagram()` | `lib/utils.ts:27` | 判断是否为空模板 |
| `extractCompleteMxCells()` | `lib/utils.ts:95` | 截取完整 mxCell 部分 |

### 前端 Tool 执行

> `hooks/use-diagram-tool-handlers.ts` (580 行)

| 函数 | 行号 | 对应 Tool |
|------|------|----------|
| `handleToolCall()` | `107` | 入口分发 |
| `handleDisplayDiagram()` | `126` | 渲染 + VLM 校验 |
| `handleEditDiagram()` | `376` | DOM 操作 |
| `handleAppendDiagram()` | `493` | 拼接续写 |

### 图表状态管理

> `contexts/diagram-context.tsx` (420 行)

| 函数 | 行号 | 用途 |
|------|------|------|
| `loadDiagram()` | `169` | 加载 XML 到 draw.io 画布 |
| `handleDiagramExport()` | `207` | 处理画布导出回调 |
| `handleDiagramAutoSave()` | `256` | 画布自动保存回调 |
| `clearDiagram()` | `266` | 清空画布 |
| `saveDiagramToFile()` | `274` | 导出文件 (.drawio/.png/.svg) |
| `getThumbnailSvg()` | `110` | 获取 SVG 缩略图 |
| `captureValidationPng()` | `139` | 获取 PNG 用于 VLM 校验 |

---

## 8. VLM 视觉校验闭环

> API: `app/api/validate-diagram/route.ts` (137 行)  
> Schema: `lib/validation-schema.ts`  
> 提示词: `lib/validation-prompts.ts`  
> 校验反馈格式化: `lib/diagram-validator.ts`

```
AI 生成 XML → 前端渲染 → captureValidationPng() [diagram-context.tsx:139]
    │                          │
    │                    exportDiagram(png)
    │                          │
    │              POST /api/validate-diagram [route.ts]
    │                          │
    │              用视觉模型看截图，检查：
    │              - 元素重叠 (critical)
    │              - 连线穿越形状 (critical)
    │              - 文字截断/过小 (warning)
    │              - 布局质量 (warning)
    │                          │
    │              ┌───────────┴───────────┐
    │              │                       │
    │           有 critical            无问题
    │              │                       │
    │     formatValidationFeedback()     完成
    │     [diagram-validator.ts:18]
    │              │
    │     反馈给 AI 重新生成
    │     (最多重试 3 次)
    │     [use-diagram-tool-handlers.ts:40]
```

校验模型选择: `lib/ai-providers.ts:1387-1403` `getValidationModel()`
- 优先 `VALIDATION_MODEL` 环境变量
- 不设则复用 `AI_MODEL`
- 不支持图片自动跳过（`validate-diagram/route.ts:49-51`）

---

## 9. 关键文件清单（含尺寸和用途）

```
next-ai-draw-io/
├── app/api/chat/route.ts                 869行  Chat API 入口、Tool 定义、消息拼装
├── app/api/validate-diagram/route.ts     137行  VLM 视觉校验 API
│
├── lib/system-prompts.ts                 411行  主提示词（6 层中的前 4 层）
├── lib/utils.ts                          66KB   XML 校验/修复/包装/增量编辑
├── lib/ai-providers.ts                   54KB   20+ AI 提供商适配
├── lib/cached-responses.ts               57KB   预置图表模板缓存
├── lib/chat-helpers.ts                   90行   文件校验、空图判断、历史替换
├── lib/session-storage.ts                430行  IndexedDB 会话持久化
├── lib/diagram-validator.ts              65行   校验反馈格式化
├── lib/validation-prompts.ts             23行   VLM 校验提示词
├── lib/validation-schema.ts              39行   校验结果 Zod schema
│
├── hooks/use-diagram-tool-handlers.ts    580行  前端 4 个 Tool 执行逻辑
├── contexts/diagram-context.tsx          420行  图表状态管理（Provider 模式）
├── components/chat-panel.tsx             58KB   聊天面板（消息发送、XML 快照管理）
├── components/chat-message-display.tsx   88KB   消息渲染（含 ToolCall 卡片）
├── components/chat-input.tsx             24KB   输入框组件
│
└── docs/shape-libraries/                 31文件  图标库文档（AWS/K8s/GCP 等）
    ├── aws4.md                                   1032 个 AWS 图标
    ├── kubernetes.md                             K8s 图标
    ├── gcp2.md                                   GCP 图标
    ├── flowchart.md                              35 个流程图符号
    ├── basic.md                                  31 个基础形状
    └── ... (共 31 个)
```

---

## 10. 移植到 Spring Boot 的分阶段计划

### v1：最小可用

**只实现 `display_diagram`**

后端（~300 行 Java）：
- 定义 OpenAI tool schema（抄 `route.ts:608-645`）
- 复制 system prompt（抄 `system-prompts.ts:10-191` DEFAULT_SYSTEM_PROMPT）
- 调 AI API，开启 function calling
- SSE 流式推送 tool call 给前端
- 不需要 shape libraries、不需要 VLM 校验

前端：
- `react-drawio` 画布
- 简易聊天面板
- `wrapWithMxFile` + `validateAndFixXml`（用 Java DOM 重写）

### v2：补齐体验

- `edit_diagram`：增量编辑，`applyDiagramOperations`（`utils.ts:498`）用 Java DOM 重写
- `get_shape_library`：直接复用 `docs/shape-libraries/` 31 个 md 文件
- 对话历史管理
- XML 上下文注入（`route.ts:452-466`）

### v3：锦上添花

- VLM 视觉校验（需一个支持图片的模型，抄 `validate-diagram/route.ts`）
- Prompt Caching（如果以后用 AWS Bedrock）
- 会话持久化（前端 IndexedDB）

---

## 11. 核心代码量估算

`route.ts` 869 行中，去掉配额/缓存/追踪/多 provider/Debug log 后：

| 部分 | 源码位置 | 行数 |
|------|---------|------|
| System prompt 拼接 | `route.ts:258-262` | ~5 行 |
| XML 上下文注入 | `route.ts:452-496` | ~45 行 |
| Tool 定义（4 个） | `route.ts:608-770` | ~160 行 |
| streamText 调用 | `route.ts:500-575` | ~75 行 |
| 消息处理 | `route.ts:329-498` | ~170 行 |
| 错误处理 | `route.ts:794-853` | ~60 行 |
| **核心总计** | | **~500 行** |

> 注：Java 重写时消息处理和 Tool 定义会更简洁，因为没有 AI SDK 的复杂性。实际 Java 代码预计 ~300 行。
