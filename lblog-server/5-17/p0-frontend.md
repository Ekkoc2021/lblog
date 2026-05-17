# P0 前端适配：对话持久化

> 日期：2026-05-17
> 适用：lblog-web 前端
> 目标：让 AI 绘图页面的对话可存储、可恢复、可回顾

---

## 目录

1. [概述](#1-概述)
2. [API 接口](#2-api-接口)
3. [前端类型定义](#3-前端类型定义)
4. [DrawChatRequest 改造](#4-drawchatreform-改造)
5. [SSE 响应变化](#5-sse-响应变化)
6. [会话列表](#6-会话列表)
7. [DrawPage 改造](#7-drawpage-改造)
8. [用户交互流程](#8-用户交互流程)

---

## 1. 概述

### 当前问题

- 每次刷新页面，AI 对话内容就全部丢失
- 所有消息只存在于前端 `useState` 内存中
- 每次请求都需要把完整历史消息发送给后端

### 改造后效果

| 场景 | 改造前 | 改造后 |
|------|--------|--------|
| 刷新页面 | ❌ 对话全丢 | ✅ 自动恢复上次会话 |
| 查看历史 | ❌ 无法查看 | ✅ 左侧显示会话列表，点击切换 |
| 新对话 | ❌ 只能清空重来 | ✅ 点击"新对话"按钮 |
| 发消息 | 发送全部历史消息 | 只发 sessionId + 最新消息 |
| 会话管理 | ❌ 无 | ✅ 重命名、删除会话 |

---

## 2. API 接口

### 2.1 会话管理

---

获取会话列表

```
GET /api/v1/ai/chat/sessions?agentType=draw&page=1&size=20
Authorization: Bearer <token>
```

响应：

```json
{
  "code": 0,
  "data": [
    {
      "id": 42,
      "title": "ER 图设计",
      "agentType": "draw",
      "modelName": "deepseek-v4-flash",
      "messageCount": 12,
      "previewText": "帮我画一个用户表的 ER 图...",
      "createdAt": "2026-05-17T10:00:00",
      "updatedAt": "2026-05-17T10:30:00"
    }
  ]
}
```

---

创建新会话

```
POST /api/v1/ai/chat/sessions
Authorization: Bearer <token>
Content-Type: application/json

{
  "agentType": "draw",
  "modelName": "deepseek-v4-flash"
}
```

响应：

```json
{
  "code": 0,
  "data": {
    "id": 43,
    "title": null,
    "agentType": "draw",
    "messageCount": 0,
    "createdAt": "2026-05-17T11:00:00"
  }
}
```

---

更新会话标题

```
PUT /api/v1/ai/chat/sessions/{id}/title
Authorization: Bearer <token>
Content-Type: application/json

{
  "title": "电商系统 ER 图"
}
```

响应：

```json
{
  "code": 0,
  "data": null
}
```

---

删除会话

```
DELETE /api/v1/ai/chat/sessions/{id}
Authorization: Bearer <token>
```

响应：

```json
{
  "code": 0,
  "data": null
}
```

### 2.2 消息查询

---

加载会话消息

```
GET /api/v1/ai/chat/sessions/{id}/messages
Authorization: Bearer <token>
```

响应：

```json
{
  "code": 0,
  "data": [
    {
      "id": 1,
      "sessionId": 42,
      "role": "user",
      "content": "帮我画一个用户表 ER 图",
      "reasoningContent": null,
      "toolCalls": null,
      "msgIndex": 0,
      "createdAt": "2026-05-17T10:00:01"
    },
    {
      "id": 2,
      "sessionId": 42,
      "role": "assistant",
      "content": "好的，我来帮你设计用户表 ER 图",
      "reasoningContent": "用户需要用户表 ER 图，我需要...",
      "toolCalls": [
        {
          "id": "call_123",
          "name": "display_diagram",
          "arguments": { "xml": "..." }
        }
      ],
      "msgIndex": 1,
      "createdAt": "2026-05-17T10:00:05"
    }
  ]
}
```

---

## 3. 前端类型定义

### 新增类型文件：`src/types/chat.ts`

```typescript
// 会话列表项
export interface ChatSessionVO {
  id: number;
  title: string | null;
  agentType: string;
  modelName: string;
  messageCount: number;
  previewText: string;       // 最后一条消息的预览摘要
  createdAt: string;
  updatedAt: string;
}

// 单条消息（展示用）
export interface ChatMessageVO {
  id: number;
  sessionId: number;
  role: 'user' | 'assistant' | 'tool';
  content: string;
  reasoningContent?: string;   // 思考过程，前端可折叠展示
  toolCalls?: ToolCallVO[];
  msgIndex: number;
  createdAt: string;
}

export interface ToolCallVO {
  id: string;
  name: string;
  arguments: Record<string, unknown>;
}
```

### 更新 `src/types/draw.ts`

```typescript
// 改造前：messages 携带完整历史
interface DrawChatRequest {
  messages: ChatMessageDTO[];     // 完整历史 + 最新消息
  xml?: string;
  sessionId?: string;
  minimalStyle?: boolean;
  customSystemMessage?: string;
}

// 改造后：只发最新消息 + sessionId
interface DrawChatRequest {
  messages: ChatMessageDTO[];     // ⚡ 只包含最新一条用户消息
  xml?: string;
  sessionId: string;              // ⚡ 必填，标识当前会话
  minimalStyle?: boolean;
  customSystemMessage?: string;
}
```

---

## 4. 新增 API 调用文件

### `src/services/chatHistory.ts`

```typescript
import request from './request';  // 项目已有的 request 封装
import { ChatSessionVO, ChatMessageVO } from '@/types/chat';

/** 获取会话列表 */
export function fetchSessions(
  agentType: string,
  page = 1,
  size = 20
): Promise<ApiResponse<ChatSessionVO[]>> {
  return request.get('/api/v1/ai/chat/sessions', {
    params: { agentType, page, size }
  });
}

/** 创建新会话 */
export function createSession(
  agentType: string,
  modelName?: string
): Promise<ApiResponse<ChatSessionVO>> {
  return request.post('/api/v1/ai/chat/sessions', { agentType, modelName });
}

/** 更新会话标题 */
export function updateSessionTitle(
  id: number,
  title: string
): Promise<ApiResponse<void>> {
  return request.put(`/api/v1/ai/chat/sessions/${id}/title`, { title });
}

/** 删除会话 */
export function deleteSession(id: number): Promise<ApiResponse<void>> {
  return request.delete(`/api/v1/ai/chat/sessions/${id}`);
}

/** 加载会话消息 */
export function fetchMessages(
  sessionId: number
): Promise<ApiResponse<ChatMessageVO[]>> {
  return request.get(`/api/v1/ai/chat/sessions/${sessionId}/messages`);
}
```

---

## 5. SSE 响应变化

### 不变的事件

| 事件 | 格式 | 处理方式 |
|------|------|---------|
| heartbeat | `{"type":"heartbeat"}` | 静默处理，不变 |
| reasoning | `{"type":"reasoning","delta":"..."}` | 追加到 reasoningAccumRef，不变 |
| text-delta | `{"type":"text-delta","delta":"..."}` | 追加到当前 assistant 消息，不变 |
| tool-call | `{"type":"tool-call","name":"display_diagram","arguments":{...}}` | 加载 XML 到 draw.io，不变 |
| error | `{"type":"error","content":"..."}` | 显示错误，不变 |

### 变化的点

**`done` 事件现在返回 `sessionId`：**

```json
// 改造前
{"type": "done"}

// 改造后
{"type": "done", "sessionId": "42"}
```

前端收到 `done` 事件后，需要做：

```typescript
if (event.type === 'done') {
  // 保存 sessionId 到当前会话列表状态
  // 后续刷新时可自动恢复此会话
  setCurrentSessionId(event.sessionId);
  // 标记流结束
  setIsStreaming(false);
}
```

---

## 6. 会话列表

### UI 组件

新增一个**会话侧栏组件** `ChatSessionList.tsx`，放在 DrawPage 左侧。

```
┌──────┬──────────────────────────────────────┐
│ 会话  │  绘图区域 (draw.io)                   │
│ 列表  │                                      │
│      │                                      │
│ 🔍  │                                      │
│ ──── │                                      │
│ 📄   │                                      │
│  ER图 │                                      │
│  设计 │                                      │
│      │                                      │
│ 📄   │                                      │
│ 登录  │                                      │
│ 流程  │                                      │
│      │                                      │
│ [+新]│                                      │
└──────┴──────────────────────────────────────┘
```

### 组件状态

```typescript
// DrawPage 新增状态
const [sessions, setSessions] = useState<ChatSessionVO[]>([]);
const [currentSessionId, setCurrentSessionId] = useState<number | null>(null);
const [loadingSessions, setLoadingSessions] = useState(false);

// 页面加载时
useEffect(() => {
  loadSessions();
}, []);

async function loadSessions() {
  setLoadingSessions(true);
  const res = await fetchSessions('draw');
  if (res.code === 0 && res.data.length > 0) {
    setSessions(res.data);
    // 默认选中最近使用的会话
    setCurrentSessionId(res.data[0].id);
    // 加载该会话的历史消息
    await loadHistoryMessages(res.data[0].id);
  }
  setLoadingSessions(false);
}

async function loadHistoryMessages(sessionId: number) {
  const res = await fetchMessages(sessionId);
  if (res.code === 0) {
    setMessages(convertToDisplayMessages(res.data));
  }
}
```

### 交互

| 操作 | 行为 |
|------|------|
| 点击会话 | 切换当前会话 → 加载历史消息 → 清空聊天显示区域 |
| 点击"+ 新对话" | 调用 `createSession('draw')` → 添加到列表头部 → 切换到新会话 |
| 右键/双击标题 | 进入编辑模式 → 调用 `updateSessionTitle(id, title)` |
| 悬停显示删除按钮 | 点击删除 → 确认 → 调用 `deleteSession(id)` → 从列表移除 |
| 消息预览 | 列表项显示最后一条消息的前 30 字 |

---

## 7. DrawPage 改造

### 改造点清单

| # | 位置 | 改动内容 |
|---|------|---------|
| 1 | 页面入口 | `useEffect` 中调用 `fetchSessions('draw')` 加载会话列表 |
| 2 | 默认状态 | 有历史会话则加载最近一条，无历史则自动创建新会话 |
| 3 | 发送消息 | `drawChatStream()` 请求体携带 `sessionId` |
| 4 | SSE done | 保存返回的 `sessionId` |
| 5 | 新对话按钮 | 调用 `createSession()` → 重置聊天区域 |
| 6 | 会话切换 | 调用 `fetchMessages(sessionId)` → 用历史消息填充聊天显示区域 |

### 状态变化

```typescript
// DrawPage 原有的状态（不变）
const [messages, setMessages] = useState<DisplayMessage[]>([]);
const [isStreaming, setIsStreaming] = useState(false);

// DrawPage 新增的状态
const [currentSessionId, setCurrentSessionId] = useState<number | null>(null);
const [sessions, setSessions] = useState<ChatSessionVO[]>([]);
```

### 关键流程

**发送消息（改造后）：**

```typescript
async function handleSend(input: string) {
  // 1. 确保有当前会话
  let sessionId = currentSessionId;
  if (!sessionId) {
    const res = await createSession('draw', currentModel);
    sessionId = res.data.id;
    setCurrentSessionId(sessionId);
    setSessions(prev => [res.data, ...prev]);
  }

  // 2. 构造请求体——只传当前消息
  const request: DrawChatRequest = {
    messages: [{ role: 'user', content: input }],  // ⚡ 只传最新一条
    xml: currentXml,
    sessionId: String(sessionId),                   // ⚡ 必填
  };

  // 3. 发起 SSE 流式请求
  drawChatStream(request, {
    onText: (delta) => appendToLastMessage(delta),
    onReasoning: (delta) => appendReasoning(delta),
    onToolCall: (toolCall) => handleToolCall(toolCall),
    onDone: (event) => {
      setCurrentSessionId(Number(event.sessionId));  // ⚡ 保存 sessionId
      setIsStreaming(false);
    },
    onError: (error) => showError(error),
  });
}
```

**切换会话：**

```typescript
async function switchSession(sessionId: number) {
  // 1. 清空当前聊天区域
  setMessages([]);

  // 2. 加载历史消息
  const res = await fetchMessages(sessionId);
  if (res.code === 0) {
    // 3. 将历史消息转换为展示格式
    const displayMessages = res.data.map(msg => ({
      role: msg.role,
      content: msg.content,
      reasoningContent: msg.reasoningContent,
      isStreaming: false,
    }));
    setMessages(displayMessages);
  }

  // 4. 更新当前会话 ID
  setCurrentSessionId(sessionId);
}
```

---

## 8. 用户交互流程

### 首次使用

```
用户打开 DrawPage
  → 无历史会话，自动创建新会话
  → 聊天区域为空
  → 用户输入 "帮我画一个 ER 图"
  → 发送请求（携带新的 sessionId）
  → AI 流式回复
  → SSE done 事件返回 sessionId
  → 刷新页面 → 自动恢复此会话
```

### 有历史会话

```
用户打开 DrawPage
  → 调用 fetchSessions('draw') 获取列表
  → 默认加载最近一次会话的消息
  → 聊天区域显示历史消息
  → 用户可以：
     a. 继续在当前会话提问
     b. 点击"+ 新对话"开始新会话
     c. 点击其他会话切换查看
```

### 多会话管理

```
用户有 3 个历史会话
  → 会话 A: "项目架构设计"（最近使用）
  → 会话 B: "ER 图设计"
  → 会话 C: "登录流程"

用户点击会话 B
  → 加载会话 B 的消息
  → 聊天区域显示 B 的历史对话
  → 用户继续在 B 中提问
  → 新消息保存到 B 中

用户点击"+ 新对话"
  → 创建会话 D
  → 聊天区域清空
  → 从 D 开始新对话
```

---

## 9. 与现有代码的兼容性

### drawChatStream 服务（`src/services/draw.ts`）

这个不需要大改，只需注意：

```typescript
// 改造前：请求体可能带完整 messages
// 改造后：请求体只带最新一条消息 + sessionId
//
// 服务的内部逻辑不需要改，仍然：
// 1. POST /api/v1/draw/chat
// 2. 读取 SSE 流
// 3. 解析 JSON 事件
// 4. 通过回调通知 UI

// 唯一变化：服务调用方传入的请求体不同
```

### 原有 DisplayMessage 类型

保持兼容，新增字段可选：

```typescript
// 已有的展示消息类型（保持不变）
interface DisplayMessage {
  role: 'user' | 'assistant';
  content: string;
  reasoningContent?: string;        // 已有的可选字段
  isStreaming?: boolean;
}

// 从 ChatMessageVO 转换为 DisplayMessage 时：
function convertToDisplayMessage(msg: ChatMessageVO): DisplayMessage {
  return {
    role: msg.role as 'user' | 'assistant',
    content: msg.content,
    reasoningContent: msg.reasoningContent,
    isStreaming: false,
  };
}
```

---

## 10. 开发步骤（前端视角）

| 步骤 | 内容 | 预估 |
|------|------|------|
| 1 | 新增 `src/types/chat.ts`，定义 ChatSessionVO / ChatMessageVO 类型 | 0.5h |
| 2 | 新增 `src/services/chatHistory.ts`，实现 5 个 API 调用 | 1h |
| 3 | 改造 `DrawPage.tsx`：新增 session 状态 + 页面加载时获取会话列表 | 1h |
| 4 | 实现会话侧栏组件 `ChatSessionList.tsx`：列表、选中、高亮 | 2h |
| 5 | 改造 `handleSend`：绑定 sessionId，只发最新消息 | 1h |
| 6 | 实现会话切换逻辑：加载历史消息 → 填充聊天区域 | 1.5h |
| 7 | 实现新对话按钮 | 0.5h |
| 8 | 实现会话重命名（双击编辑标题） | 1h |
| 9 | 实现会话删除 | 0.5h |
| 10 | 手动测试全流程：创建 → 对话 → 切换 → 刷新 → 恢复 → 删除 | 1h |

---

## 11. 验收标准（前端视角）

1. ✅ 页面加载后自动加载会话列表
2. ✅ 点击会话可查看历史消息
3. ✅ 新消息发送后刷新页面，消息仍然存在
4. ✅ 新建会话不影响旧会话的数据
5. ✅ 可删除不再需要的会话
6. ✅ 可修改会话标题
7. ✅ SSE 流式响应不受改造影响，继续正常显示
8. ✅ AI 思考过程（reasoningContent）可折叠展示
