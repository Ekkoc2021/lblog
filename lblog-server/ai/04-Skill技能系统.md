# 04 — Skill 技能系统（P1）

## 解决了什么问题

不同 AI Agent 需要不同的领域知识。绘图 Agent 需要了解 draw.io XML 规范，未来的聊天 Agent 可能需要博客系统知识。如果这些指令全写死在一个十万字符的 system prompt 里，token 全浪费在"当前对话不需要"的部分。

Skill 系统做一件事：**把领域指令按需拆包，LLM 自己决定何时加载。**

### 最佳实践

- **一条 skill 一个领域。** 不要把"绘图规范"和"SEO 写作指南"塞进同一个 skill，LLM 无法按需加载其中一半
- **system prompt 只放懒加载提示。** `buildLazyHint("draw")` 一行 20 token，LLM 知道技能存在即可。不要把所有技能列表塞进 system prompt——那和把指令写死在 prompt 里没有区别
- **LLM 决定要不要加载，而不是后端。** 后端只管"提供哪些 skill"，不管"什么时候用"。LLM 根据用户意图自行判断，无参浏览后选择加载
- **skill prompt 是独立完整的指令块。** 加载后 LLM 应该拿到一套可以直接遵循的完整规则，不需要额外上下文
- **新 Agent 复用同一套基础设施。** 建表 insert agent_type="chat" 的 skill，注入 `buildLazyHint("chat")` 即可。不需要写新代码

---

## 设计决策：Skill = Prompt

P1 最早的设计是"后端按 skill 控制工具可见性"——未加载的 skill 对应的 tool 对 LLM 不可见。需要 `@SkillTool` 注解 + `SkillToolRegistry` + `SkillAwareToolCallAdvisor` 六个组件协同。

后来推翻了。实际代码简化到了 4 个文件：

```
ai/skill/
├── LoadSkillTool.java              # @Tool — LLM 按需加载技能（支持无参浏览）
├── SkillSystemPromptBuilder.java   # 懒加载提示 + 技能列表格式化（⚠️ 未接入 system prompt）
├── SkillPackage.java               # 领域模型
├── SkillPackageMapper.java + xml   # MyBatis CRUD
├── SkillService.java               # 接口
└── SkillServiceImpl.java           # Caffeine 缓存 30s TTL
```

| 组件 | 废弃原因 |
|------|---------|
| SkillAwareToolCallAdvisor | 不拦截/过滤 tools，skill 在 LLM 层通过 tool call 交互 |
| SkillToolRegistry | 不需要扫描 @SkillTool |
| @SkillTool 注解 | 不再需要标记 tool 归属 |
| SkillSessionManager | skill 加载结果在对话历史里，不需要额外会话状态 |

---

## 数据模型

```
ai_skill_packages 表
┌─────────────┬──────────┬──────────────────────────────┐
│ name        │ VARCHAR  │ 技能标识（loadSkill 参数）     │
│ agent_type  │ VARCHAR  │ 筛选目标 agent（null=通用）     │
│ display_name│ VARCHAR  │ 展示名                        │
│ description │ TEXT     │ 描述（skill list 时展示）       │
│ keywords    │ VARCHAR  │ 预留关键词字段                 │
│ prompt      │ TEXT     │ ★ 核心：LLM 收到的指令文本     │
│ is_active   │ TINYINT  │ 启用/停用                      │
└─────────────┴──────────┴──────────────────────────────┘
```

prompt 字段直接作为 `loadSkill` 的返回值，LLM 将其视为系统指令的一部分。

---

## 核心流程

```
1. System Prompt 构建
   注入 buildLazyHint("draw")  → "You can call loadSkill() without arguments to browse..."
   （一行不到 20 token，不枚举具体技能）

2. LLM 判定需要技能 → 调 loadSkill() 无参浏览
   → LoadSkillTool.listAvailableSkills() → promptBuilder.buildAvailableSkillsHint("draw")
   → "loadSkill(\"draw-expert\") — 绘图专家技能包"

3. LLM 选定 → 调 loadSkill("draw-expert")
   → 从 DB 读取 SkillPackage.prompt
   → 返回 "## Skill: Draw Expert\n\n{prompt}"
   → LLM 按技能指令回复

4. 后续对话中 LLM 已拥有技能上下文
   （prompt 在对话历史中作为 tool call 的 output 存在）
```

**两步加载：** system prompt 里只有一行懒加载提示 → LLM 需要时调 loadSkill() 浏览 → 选定后加载。skill 不常用的场景下几乎零 token 开销。

---

## 核心代码路径

### SkillSystemPromptBuilder — 两个方法两个用途

```java
// 注入 system prompt — 仅一行懒加载提示，不查 DB
public String buildLazyHint(String agentType) {
    return "You can call loadSkill() without arguments to browse and load specialized skills on demand.";
}

// loadSkill 无参返回 — 完整技能列表，格式紧凑
public String buildAvailableSkillsHint(String agentType) {
    List<SkillPackage> skills = resolveSkills(agentType);
    // → "loadSkill(\"draw-expert\") — 绘图专家技能包\nloadSkill(...) — ..."
}
```

### LoadSkillTool

```java
@Tool(name = "loadSkill", description = """
        Load a skill package to get specialized instructions for a specific domain.
        Call with a skill name to load its instructions into the conversation.
        Call without a name to list available skills.
        """)
public String loadSkill(
        @ToolParam(description = "Skill name to load, or empty to list available skills")
        String skillName, ToolContext ctx) { ... }
```

`listAvailableSkills` 委托给 `promptBuilder.buildAvailableSkillsHint(agentType)`，格式已统一。

### agentType 隔离

`toolContext("agentType", "draw")` → `LoadSkillTool.listAvailableSkills()` 根据 agentType 筛选 → `SkillPackageMapper.selectActiveByAgent("draw")` 只返回绘图相关技能。

### 缓存策略

Caffeine，30s TTL，按 `"all"` 和 `"agent:{agentType}"` 双键缓存。技能包热更新后最多 30s 生效。无手动 reload 接口。

---

## 设计评审

---

### 决策 1：Skill = Prompt vs 工具过滤

| 维度 | 工具过滤方案（废弃） | Skill = Prompt（当前） |
|------|-------------------|----------------------|
| 核心机制 | @SkillTool + Registry + Advisor | 一个 @Tool + CRUD |
| 组件数 | 6+ | 4 个文件 |
| LLM 自主性 | 低（后端控制可见性） | 高（LLM 自行决定何时加载） |
| 复杂度 | 高 | 低 |
| 灵活度 | 加工具需改代码 | 加 skill 只需 insert 一条数据 |

**评价：** 在初期没有明确"哪些 tool 该按 skill 隐藏"的需求时，选择轻方案是对的。如果未来出现"某些 tool 永远不该被某个 agent 调用"的硬约束，再考虑加回可见性控制——但那是 advisor 层的功能，和当前 LoadSkillTool 不冲突。

---

### 决策 2：缓存 TTL=30s vs 手动 reload

**当前做法：** Caffeine 30s 过期的被动缓存。没有管理端 API。

**替代方案：**

| 方案 | 生效方式 | 适用场景 |
|------|---------|---------|
| A. 30s TTL（当前） | 自动过期，最多等 30s | 技能包不频繁改动 |
| B. 手动 reload | 调 API 刷新 | 需要即时生效 |
| C. 事件驱动 | DB 变更 → Event → 缓存刷新 | 即时 + 无需手动 |

**评价：** 当前没有 Admin API，所以手动 reload 也没意义。等 Admin API 实现后，改为 B 或 C 更合适——参照提示词管理的 reload 模式。

---

## 实现层面发现的问题

### 🟡 SkillSystemPromptBuilder 未接入 system prompt

`buildLazyHint("draw")` 一行提示已就绪，但没有任何地方调用它拼入 system prompt。

**影响：** LLM 不知道有技能可加载，只能靠 tool description 偶然发现 loadSkill 的存在。修了它之后 LLM 看到那行提示就知道可以浏览和加载。

**修复：** 在 `DiagramService` 或 `PromptManager` 中：

```java
String lazyHint = skillSystemPromptBuilder.buildLazyHint("draw");
if (!lazyHint.isEmpty()) {
    systemPrompt += "\n" + lazyHint;
}
```

---

### 🟡 Skill Admin API 未实现

`AdminPromptController` 9 个接口齐全，但 Skill 没有任何管理端 REST API。增删改 skill 包只能直接操作数据库。

**建议：** 参照 AdminPromptController 的范式，新增 `AdminSkillController`：

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | /api/v1/admin/ai/skills | 列表 |
| POST | /api/v1/admin/ai/skills | 新增 |
| PUT | /api/v1/admin/ai/skills/{name} | 更新 |
| DELETE | /api/v1/admin/ai/skills/{name} | 停用/启用 |
| POST | /api/v1/admin/ai/skills/reload | 刷新缓存 |

---

### 🟡 无初始种子数据

Skill 表没有建表 SQL、没有 seed 数据。项目启动后表是空的，LLM 调 `loadSkill` 只能拿到 "No skill packages available."。

**建议：** 在 `resources/sql/` 或类似位置放建表 DDL + 初始数据（如 draw-expert 技能包），或用 seed API 从 skill/prompts/ 目录导入。

---

### 🟢 小问题速览

| 问题 | 说明 | 状态 |
|------|------|------|
| LoadSkillTool 无加载统计 | 不记录谁什么时候加载了哪个 skill，调试靠日志 | 待实现 |
| keywords 字段未使用 | 定义了但没在任何地方过滤或匹配 | 待实现 |
| 技能列表格式已统一 | `loadSkill` 无参和 `SkillSystemPromptBuilder` 共用同一套紧凑格式 | ✅ 已优化 |
| 懒加载提示 | `buildLazyHint` 一行 20 token，不枚举技能列表 | ✅ 已实现 |
| loadSkill tool 描述优化 | 聚焦核心功能，参数描述清晰 | ✅ 已优化 |
| 无多技能组合卸载 | LLM 加载技能后 prompt 永久存在于历史中 | 低优先级 |

---

## 总体评价

| 维度 | 评分 | 说明 |
|------|------|------|
| 设计简洁 | ⭐⭐⭐⭐⭐ | 4 个文件完成一个子系统，Skill = Prompt 决策优秀 |
| 实现完整度 | ⭐⭐⭐ | SkillSystemPromptBuilder 未接入 system prompt、Admin API 缺失 |
| 可扩展性 | ⭐⭐⭐⭐ | agentType 隔离天然支持多 Agent，加技能只需 insert |
| 生产就绪 | ⭐⭐⭐ | 核心能跑，但技能列表不在 system prompt 里，每轮多一次 API 调用 |

> 一句话：设计方向极简且正确。懒加载提示 + 紧凑列表格式 + tool 描述优化三项改动让 token 开销降到最低。接入 system prompt 后即可上线——这是最后一步。
