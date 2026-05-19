# P1 Skill 设计 — Skill 即 Prompt

> 日期：2026-05-19
> 对应实现：commit `e0ec154`（P1 skill 模块精简 — 去掉工具过滤体系，skill = prompt）
> 状态：✅ 已交付

---

## 设计理念

P1 的核心假设：**初期不需要复杂的工具可见性过滤，让 LLM 自主决定何时加载技能指令**。

对比两个方向：

| 方向 | 思路 | 复杂度 | 当前选择 |
|------|------|--------|---------|
| 工具过滤 | 按 Skill 隐藏/显示 tool，未加载 skill 时 tool 不可见 | 高：需要 @SkillTool + Registry + Advisor | ❌ 放弃 |
| Skill = Prompt | Skill 只是一段 prompt 文本，LLM 自行调用 loadSkill 获取 | 低：一个 @Tool + CRUD | ✅ 当前实现 |

---

## 模块结构

```
ai/skill/
├── LoadSkillTool.java          # @Tool — LLM 调用的技能加载入口
├── SkillSystemPromptBuilder.java # 构建可用技能列表 → 拼入 system prompt
├── domain/SkillPackage.java    # 技能包领域模型
├── mapper/SkillPackageMapper.java # MyBatis CRUD
├── service/
│   ├── SkillService.java       # 接口
│   └── SkillServiceImpl.java   # 实现（Caffeine 缓存，30s TTL）
```

### 不存在的组件（对比早期设计）

以下在早期 p1.1 设计文档中规划但已全部移除：

| 组件 | 原因 |
|------|------|
| `SkillAwareToolCallAdvisor` | 不拦截/过滤 tools，skill 在 LLM 层通过 tool call 交互 |
| `SkillToolRegistry` | 不需要扫描 @SkillTool |
| `@SkillTool` 注解 | 不再需要标记 tool 归属 |
| `ToolVisibilityFilter` / `SkillBasedFilter` | 过滤体系已移除 |
| `SkillSessionManager` | 不需要维护会话技能状态（skill 加载结果在对话历史里） |

---

## 数据流

```
1. System Prompt 构建
   SkillSystemPromptBuilder.buildAvailableSkillsHint("draw")
   → "Available skill packages:\n  - Draw Expert: 绘图专家（loadSkill("draw-expert")）"

2. 用户提问 → LLM 判定需要绘图技能
   → LLM 调用 loadSkill("draw-expert")
   → LoadSkillTool 从 DB 读取 SkillPackage，返回 prompt 文本
   → LLM 拿到 prompt 后按指令回复

3. 后续对话中 LLM 已拥有技能上下文
   （prompt 在对话历史中作为 assistant tool call 的 output 存在）
```

### 组件协作图

```
DiagramService (chatStream / chatNonStream)
  │
  ├─ .system(promptManager.buildSystemPrompt() + "\n\n" + skillHint)
  │                    ↑ SkillSystemPromptBuilder.buildAvailableSkillsHint("draw")
  │
  ├─ .tools(displayDiagramTool, loadSkillTool)
  │                   ↑ LoadSkillTool 作为普通 @Tool 注册
  │
  └─ .toolContext(Map.of("agentType", "draw"))
                     ↑ 传给 LoadSkillTool，用于按 agentType 筛选技能列表
```

---

## 关键设计决策

### 1. Skill = Prompt

`SkillPackage` 的 7 个业务字段：

```
┌─────────────┬──────────┬──────────────────────────────┐
│ 字段         │ 类型     │ 说明                         │
├─────────────┼──────────┼──────────────────────────────┤
│ name        │ VARCHAR  │ 技能标识（loadSkill 参数）     │
│ agent_type  │ VARCHAR  │ 筛选目标 agent（null=通用）     │
│ display_name│ VARCHAR  │ 展示名                       │
│ description │ TEXT     │ 描述（skill list 时展示）       │
│ keywords    │ VARCHAR  │ 预留关键词字段                │
│ prompt      │ TEXT     │ ★ 核心：LLM 收到的指令文本     │
│ is_active   │ TINYINT  │ 启用/停用                     │
└─────────────┴──────────┴──────────────────────────────┘
```

prompt 字段直接作为 `loadSkill` 的返回值，LLM 将其视为系统指令的一部分。

### 2. 不入侵 Advisor 链

当前 advisor 链：

```
ChatHistoryAdvisor → DeepSeekToolCallAdvisor
```

Skill 不增加新的 advisor。`loadSkill` 是普通的 `@Tool`，由 `DeepSeekToolCallAdvisor` 执行工具调用循环。这保持了 advisor 链的简洁性。

### 3. 缓存策略

- Caffeine 缓存，30s TTL
- 按 `"all"` 和 `"agent:{agentType}"` 双键缓存
- 技能包热更新后最多 30s 生效

### 4. agentType 隔离

`toolContext` 传入 `agentType="draw"` → `LoadSkillTool.listAvailableSkills()` 根据 agentType 筛选 → `SkillPackageMapper.selectActiveByAgent("draw")` 只返回绘图相关技能。

---

## 扩展场景

### 添加新技能包

1. 在 `ai_skill_packages` 表插入一条记录，`name="xxx"`, `prompt="..."`, `agent_type="draw"`（或 null）
2. 30s 后缓存过期，`loadSkill` 可查到新技能

### 添加新 Agent 类型

1. 新建 Service，注入工具 + skill
2. `toolContext("agentType", "new-agent")`
3. 在 DB 中为 `agent_type="new-agent"` 创建技能包

### 未来恢复工具过滤

如果未来需要按 skill 控制工具可见性，可在 LoadSkillTool 中追加 `toolContext` 标记，再新增 advisor 读取该标记进行过滤——当前设计不阻止此方向。
