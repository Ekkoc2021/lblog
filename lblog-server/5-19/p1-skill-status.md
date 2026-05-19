# P1 Skill 模块现状总览

> 日期：2026-05-19
> 对应 commit：`e0ec154`（精简版：去掉工具过滤体系，skill = prompt）
> 前置依赖：P0 对话持久化（✅ 已完成）

---

## 设计决策

P1 最终落地方向与早期设计文档有根本差异：

| 维度 | 早期方案（已废弃） | 当前方案（已交付） |
|------|------------------|-------------------|
| 思路 | 后端控制工具可见性，skill 未加载时 tool 不可见 | skill 只是一段 prompt 文本，LLM 自主调用 `loadSkill` 获取 |
| 核心机制 | `@SkillTool` + `SkillToolRegistry` + `SkillAwareToolCallAdvisor` | 一个 `@Tool` 方法 + CRUD |
| 组件数 | 6+ 组件协同 | 4 个文件 |
| 灵活性 | 需改代码/注解才能加工具 | 加工具直接注册，skill 只需写 prompt |
| 废弃文件 | `SkillAwareToolCallAdvisor`, `SkillSessionManager`, `SkillToolRegistry`, `SkillTool.java` | 全部删除 |

---

## 已交付组件

```
ai/skill/
├── LoadSkillTool.java              # @Tool — LLM 调用的技能加载入口
├── SkillSystemPromptBuilder.java   # 构建可用技能列表提示文本
├── domain/SkillPackage.java        # 领域模型
├── mapper/SkillPackageMapper.java  # MyBatis 接口
├── mapper/SkillPackageMapper.xml   # SQL 映射
└── service/
    ├── SkillService.java           # 接口
    └── SkillServiceImpl.java       # 实现（Caffeine 30s 缓存）
```

### 集成点

| 文件 | 集成方式 |
|------|---------|
| `AiConfig.java` | 不涉及（skill 不侵入 advisor 链） |
| `DiagramService.java` | `.tools(displayDiagramTool, loadSkillTool)` + `toolContext("agentType", "draw")` |

---

## 已发现的缺口

### 🔴 Bug：SkillSystemPromptBuilder 未接入

`SkillSystemPromptBuilder.buildAvailableSkillsHint("draw")` 定义了但**没有任何地方调用它**，导致 system prompt 中没有技能列表提示，LLM 不知道可以调 `loadSkill`。

**修复方案：** 在 `DiagramService`（或 `PromptManager`）中拼入 system prompt 时追加 skill 列表。

### 🟡 待实现功能

| 功能 | 文档 | 优先级 |
|------|------|--------|
| Admin REST API（6 个接口） | `5-17/p1-skill-admin-api.md` | P1 补完 |
| skill 初始化 SQL（建表 + 初始数据） | — | P1 补完 |
| 前端技能面板（列表 + 加载状态） | — | 依赖前端 |
| `unloadSkill` 工具（从上下文移除 skill 影响） | — | 低 |

### 🟢 后续增强方向

| 增强 | 说明 |
|------|------|
| 自动意图识别 | 关键词匹配用户意图，自动注入对应 skill |
| 多技能组合 | LLM 可同时加载多个 skill |
| 加载统计 | 记录 skill 被加载次数、agent 分布 |

---

## 文档变更记录

| 文件 | 变更 |
|------|------|
| `5-17/context-engineering-roadmap.md` | P1 章节重写，架构图更新，状态标记 |
| `5-17/p1-skill-design.md` | 新建：当前实现设计文档 |
| `5-17/p1.1-toolcall-advisor-design.md` | 标记为废弃，保留历史参考 |
| `5-17/p1-skill-test-plan.md` | 移除工具可见性测试用例 |
| `5-17/p1-skill-admin-api.md` | 新建：管理接口设计 |

---

## 参考

- 路线图：[5-17/context-engineering-roadmap.md](../5-17/context-engineering-roadmap.md)
- 设计文档：[5-17/p1-skill-design.md](../5-17/p1-skill-design.md)
- 管理接口：[5-17/p1-skill-admin-api.md](../5-17/p1-skill-admin-api.md)
- 测试计划：[5-17/p1-skill-test-plan.md](../5-17/p1-skill-test-plan.md)
