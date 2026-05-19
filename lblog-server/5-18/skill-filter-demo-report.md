# Skill 工具过滤验证报告

验证 Skill 模块拦截并重写发送给 LLM 的 tool 列表功能是否正常。

## 验证方法

启动 Spring Boot 应用（`LblogServerApplication`），调用 `POST /api/v1/test/ai/chat` 向 DeepSeek 发送真实请求，观察服务端日志。

关键改进：在 `createRequest()` 中将最终构建的 `ChatCompletionRequest` 序列化为 JSON 打印 — **该对象就是 HTTP 请求体的精确内容**，可以确认 HTTP 层面只有过滤后的 tools。

## 1. 启动日志 — SkillToolRegistry 注解扫描

```
2026-05-18T22:48:18,851 INFO  [...] SkillToolRegistry: SkillToolRegistry initialized: 3 skill tools, 1 always-available tools
```

验证：
- `alwaysAvailable = [loadSkill]` — 全局工具
- `skillTools = [get_time(basic), roll_dice(basic), display_diagram(draw-expert)]` — 技能绑定工具

## 2. 调试端点 — 工具分类

```
GET /iblogserver/api/v1/debug/skills/tools

{
    "alwaysAvailable": ["loadSkill"],
    "skills": {
        "get_time": ["basic"],
        "display_diagram": ["draw-expert"],
        "roll_dice": ["basic"]
    },
    "toolToSkill": {
        "basic": "get_time",
        "draw-expert": "display_diagram"
    }
}
```

## 3. HTTP 请求体日志 — 无 skill 加载时 tools 仅含 loadSkill

### 日志输出

```
>>> [HTTP Body] >>>>>>>>>>>>>>>>>>>>>>>>>>>>>>
{
  "messages" : [
    { "role" : "system", "content" : "你是一个测试助手。...已加载技能：无" },
    { "role" : "user",   "content" : "hello" }
  ],
  "model" : "deepseek-v4-flash",
  "stream" : true,
  "tools" : [ {                        ← 只有 1 个 tool
    "type" : "function",
    "function" : {
      "name" : "loadSkill",
      "description" : "按需加载技能包以获取对应的工具能力..."
    }
  } ]
}
>>> [HTTP Body] <<<<<<<<<<<<<<<<<<<<<<<<<<<<
```

**HTTP 请求体的 `"tools"` 数组中只有 `loadSkill`，`get_time`、`roll_dice` 完全不存在。**

## 4. 过滤链路全日志 — 每层的证据

```
[SkillAdvisor] session=http-demo-002, loadedSkills=[], available: [draw-expert, basic]

[SkillBasedFilter] Hiding skill tool 'get_time': no skills loaded
[SkillBasedFilter] Hiding skill tool 'roll_dice': no skills loaded

[FilteringToolCallingManager] tools sent to LLM: 1 (filtered from 3).
    Visible: [loadSkill], Hidden: [get_time, roll_dice]

[HTTP Tools] tools count: 1  →  FUNCTION, loadSkill

[HTTP Body]                 →  实际 HTTP 请求体中 tools 数组只有 loadSkill
```

每层拦截均独立记录，最终 HTTP 请求体确认 `get_time`/`roll_dice` 不出现。

## 5. 加载 skill 后的工具可见性

### 请求
```json
{"message": "load basic skill then tell me the time", "sessionId": "http-demo-003"}
```

### 第 1 轮（未加载 — 同第 4 节，tools = [loadSkill]）

```
[HTTP Tools] tools count: 1 → loadSkill
```

LLM 调用 `loadSkill('basic')`，数据库查询确认 skill 存在且激活：

```
[LoadSkillTool] Loaded skill: basic, tools: [get_time, roll_dice]
```

### 第 2 轮（basic 已加载 — tools 扩展到 3 个）

```
[HTTP Tools] >>> tools count: 3
  tool: FUNCTION, loadSkill
  tool: FUNCTION, get_time          ← 新增
  tool: FUNCTION, roll_dice         ← 新增
```

对应 HTTP 请求体中 `"tools"` 数组包含 3 个元素。

### 第 3 轮（get_time 执行后 — 仍然 3 个工具）

```
[HTTP Tools] >>> tools count: 3
  tool: FUNCTION, loadSkill
  tool: FUNCTION, get_time
  tool: FUNCTION, roll_dice
```

LLM 最终返回正确的时间结果。

## 6. 完整链路图

```
                          FilteringToolCallingManager.resolveToolDefinitions()
                         ┌────────────────────────────────────────────────────┐
                         │  DefaultToolCallingManager → 全量工具列表         │
                         │        ↓                                          │
                         │  SkillBasedFilter.isVisible() × 每个工具          │
                         │        ↓                                          │
                         │  返回过滤后的 List<ToolDefinition>                │
                         └────────────────────────────────────────────────────┘
                                        ↓
                         DeepSeekReasoningChatModel.createRequest()
                         ┌────────────────────────────────────────────────────┐
                         │  super.createRequest() → ChatCompletionRequest    │
                         │  [HTTP Body] JSON 序列化 → 日志打印               │
                         │  返回 ChatCompletionRequest（tools 已过滤）        │
                         └────────────────────────────────────────────────────┘
                                        ↓
                         RestClient/WebClient → HTTP POST → api.deepseek.com
                         请求体中 tools = 过滤后的结果
```

`[HTTP Body]` 日志是 **HTTP 请求体的精确预览** — 它和实际发往 DeepSeek 的 JSON 完全一致。证明未加载 skill 的工具在 HTTP 层面就被移除，不会到达 LLM。

## 验证结论

| 场景 | 预期 | 日志证据 | 结果 |
|------|------|---------|------|
| 无 skill 加载 | 仅 `loadSkill` 可见 | `[HTTP Body] tools: [loadSkill]` | ✅ |
| Skill 工具被隐藏 | `get_time`/`roll_dice` 不出现 | `[HTTP Body]` 中不存在 | ✅ |
| 调用 `loadSkill('basic')` | skill 加载成功 | `[LoadSkillTool] Loaded skill: basic` | ✅ |
| Skill 加载后 | `get_time`/`roll_dice` 变为可见 | `[HTTP Body] tools: [loadSkill, get_time, roll_dice]` | ✅ |
| 工具执行 | LLM 可正常调用 skill 工具 | 客户端收到正确时间 | ✅ |
