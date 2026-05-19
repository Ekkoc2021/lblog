# Skill 模块测试计划

## 测试目标

验证 Skill 模块核心功能是否正常：
1. **工具分类** — `@SkillTool` 注解的工具与全局工具的注册和分类
2. **会话管理** — 技能在会话维度的加载/卸载
3. **工具可见性过滤** — 未加载技能时，其 `@SkillTool` 工具对 LLM 不可见
4. **全链路** — LLM 调用 `loadSkill` → 技能工具可见 → 执行技能工具

## 测试文件

| 文件 | 类型 | 依赖 | 覆盖范围 |
|------|------|------|---------|
| `SkillSessionManagerTest` | 纯单元 | 无 | 会话管理 CRUD、并发安全 |
| `SkillBasedFilterTest` | 纯单元 | Mock SkillToolRegistry | 可见性判断逻辑 |
| `SkillToolRegistryTest` | 单元 | Mock ApplicationContext | 注解扫描、工具分类 |
| `FilteringToolCallingManagerTest` | 单元 | Mock 所有依赖 | 工具定义过滤流程 |
| `SkillModuleIntegrationTest` | 集成 | Spring Boot 上下文 | 完整装配、调试端点 |

## 关键验证点

### 1. SkillSessionManager
- `loadSkill` → `getLoadedSkills` 返回包含加载的技能
- `unloadSkill` → 技能从会话中移除
- `clearSession` → 清空整个会话
- 多会话隔离（sessionId 不同互不影响）
- 并发写入无竞态

### 2. SkillBasedFilter

| 场景 | Tool 类型 | LoadedSkills | 期望 |
|------|-----------|-------------|------|
| 全局工具 | alwaysAvailable | 任意 | visible |
| 技能工具（技能未加载） | skill-gated | 空/不含所需技能 | 隐藏 |
| 技能工具（技能已加载） | skill-gated | 包含所需技能 | visible |
| 无注解工具 | 未注册 | 任意 | visible |

### 3. SkillToolRegistry
- `@Tool` + `@SkillTool` → 归入 `toolSkills`/`skillTools` 索引
- 仅 `@Tool` → 归入 `alwaysAvailable`
- `isSkillTool` / `isAlwaysAvailable` 查询正确
- `getToolsBySkill` 返回正确列表

### 4. FilteringToolCallingManager
- 调用 `resolveToolDefinitions` 时正确提取 sessionId
- 正确获取 loadedSkills 并传入 filter
- 过滤后只返回可见工具

### 5. 集成测试
- `SkillDebugController` 返回正确的工具分类
- DemoSkillController 可以正常流式响应
