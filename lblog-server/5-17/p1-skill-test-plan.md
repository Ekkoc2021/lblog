# P1 Skill 系统测试计划

> 日期：2026-05-19
> 测试目标：验证 Skill = Prompt 模式下 loadSkill tool 的正确性
> 测试接口：`POST /api/v1/draw/chat`（SSE 流式）
> 对应实现：commit `e0ec154`（精简版，无工具过滤）

---

## 变更说明

本文档基于当前 P1 实现更新。**取消了工具可见性测试**（无 @SkillTool、无 SkillAwareToolCallAdvisor），只测试：
1. `loadSkill` tool 能被 LLM 正确调用
2. 技能 prompt 正确注入到对话
3. agentType 筛选生效

---

## 测试场景

### 前置数据

确认 `ai_skill_packages` 表存在以下数据：

```sql
-- 验证技能包已配置
SELECT name, agent_type, is_active FROM ai_skill_packages;
-- 期望: draw-expert | draw | 1
```

### 场景：绘图对话中加载技能

#### Step 0：验证技能列表提示存在于 System Prompt

通过调试日志确认 system prompt 中包含 skill 提示：

```
期望日志:
[SkillSystemPromptBuilder] Available skills for agent draw:
  - Draw Expert: 绘图专家（loadSkill("draw-expert")）
```

#### Step 1：发送绘图请求

```
请求:
  POST /api/v1/draw/chat
  Content-Type: application/json

  {
    "sessionId": "test-skill-001",
    "message": "帮我画一个登录页面流程图",
    "messages": [{"role": "user", "content": "帮我画一个登录页面流程图"}]
  }

期望:
  → 收到 SSE 流式回复
  → 回复中包含 draw.io XML
```

#### Step 2：验证 loadSkill 被调用

搜索日志确认 LLM 调用了 `loadSkill`：

```
期望日志:
[LoadSkillTool] Skill loaded: draw-expert (Draw Expert)
```

### 验收标准

- [ ] Step 0 system prompt 包含可用技能列表
- [ ] Step 1 正常返回 SSE 流
- [ ] Step 2 日志中出现 `Skill loaded: draw-expert`
- [ ] LLM 最终输出的图表符合 draw-expert 技能中的指令约束

### 不测试的范围（对比废弃方案）

| 之前测试项 | 现在状态 | 原因 |
|-----------|---------|------|
| 工具可见性过滤 | ❌ 不再测试 | 所有 tool 始终可见 |
| @SkillTool 注解扫描 | ❌ 不再测试 | 注解已删除 |
| SkillSessionManager 状态 | ❌ 不再测试 | 组件已删除 |
| HTTP 请求体中 tools 过滤 | ❌ 不再测试 | 不过滤 tools |
