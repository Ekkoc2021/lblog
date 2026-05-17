# P1 — Skill 技能包系统

> 阶段目标：实现基于注解的 Skill 技能包系统，按需加载工具，减少无效 token 消耗
> 前置依赖：P0（ChatHistoryAdvisor + DeepSeekToolCallAdvisor）
> 预估工期：~5 天

---

## 目录

1. [设计决策](#1-设计决策)
2. [注解定义](#2-注解定义)
3. [SkillToolRegistry](#3-skilltoolregistry)
4. [SkillAwareToolCallAdvisor](#4-skillawaretoolcalladvisor)
5. [LoadSkillTool](#5-loadskilltool)
6. [数据库设计](#6-数据库设计)
7. [包结构与类设计](#7-包结构与类设计)
8. [实现步骤](#8-实现步骤)
9. [测试策略](#9-测试策略)

---

## 1. 设计决策

### 1.1 核心逻辑

```
所有 @Bean @Tool 分为两类：

没有 @SkillTool 注解          有 @SkillTool 注解
       │                             │
  始终暴露给 LLM               属于某个 skill，初始不可见
  （如 loadSkill 本身）         LLM 调 loadSkill("draw-expert") 后可见
```

### 1.2 工具可见性

| 分类 | 条件 | 举例 |
|------|------|------|
| 始终暴露 | 没有 `@SkillTool` 注解 | `loadSkill`、`toolSearch` |
| Skill 工具 | 有 `@SkillTool("draw-expert")` | `displayDiagram` |
| 已加载 | `loadSkill("draw-expert")` 被调用过 | 同上，变为可见 |

### 1.3 流程

```
请求进入
  │
  ▼
SkillAwareToolCallAdvisor.doInitializeLoop():
  → 缓存当前请求中所有 ToolCallback
  → 初始化已加载技能列表（空）
  │
  ▼
doBeforeCall() — 每轮递归 LLM 前执行:
  → 从缓存取所有 ToolCallback
  → 筛选：始终暴露的 + 已加载技能的
  → 设置到请求中
  │
  ▼
LLM 看到: [loadSkill + 已加载技能的工具]
  → 调 loadSkill("draw-expert")
  → 记录到已加载列表
  │
  ▼
doBeforeCall() — 下一轮:
  → 筛选：始终暴露的 + draw-expert 的工具
  → LLM 看到: [loadSkill + displayDiagram]
```

---

## 2. 注解定义

### 2.1 @SkillTool

```java
package com.yang.lblogserver.ai.skill.annotation;

import java.lang.annotation.*;

/**
 * 标记一个 @Tool 方法属于哪个技能包。
 * 没有此注解的工具始终可见。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface SkillTool {
    String[] value();    // 所属技能包名，如 "draw-expert"
}
```

### 2.2 使用示例

```java
@Component
public class DisplayDiagramTool {

    @Tool(name = "display_diagram", description = "生成 draw.io 图表 XML")
    @SkillTool("draw-expert")        // ← 属于 draw-expert 技能
    public String execute(String xml, ToolContext ctx) { ... }
}

@Component
public class LoadSkillTool {

    @Tool(name = "loadSkill", description = "加载技能包。可用技能：draw-expert")
    public String loadSkill(String skillName, ToolContext ctx) { ... }
    // ↑ 没有 @SkillTool，始终暴露
}
```

---

## 3. SkillToolRegistry

### 3.1 职责

启动时扫描所有 Spring bean，找到 `@SkillTool` 注解，建立索引。

### 3.2 实现

```java
package com.yang.lblogserver.ai.skill;

import com.yang.lblogserver.ai.skill.annotation.SkillTool;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class SkillToolRegistry {

    /** 工具名 → 所属技能列表 */
    private final Map<String, List<String>> toolSkills = new ConcurrentHashMap<>();

    /** 技能名 → 工具名列表 */
    private final Map<String, List<String>> skillTools = new ConcurrentHashMap<>();

    /** 始终暴露的工具（无 @SkillTool） */
    private final Set<String> alwaysAvailable = ConcurrentHashMap.newKeySet();

    @PostConstruct
    public void init(ApplicationContext ctx) {
        // 遍历所有 bean
        for (String beanName : ctx.getBeanDefinitionNames()) {
            Object bean = ctx.getBean(beanName);
            Class<?> clazz = bean.getClass();

            for (Method method : clazz.getDeclaredMethods()) {
                Tool toolAnno = method.getAnnotation(Tool.class);
                if (toolAnno == null) continue;

                // @Tool 注解上定义的 name，或方法名
                String toolName = toolAnno.name();
                if (toolName.isBlank()) toolName = method.getName();

                SkillTool skillAnno = method.getAnnotation(SkillTool.class);
                if (skillAnno != null) {
                    // 有 @SkillTool → 记录所属技能
                    String[] skills = skillAnno.value();
                    toolSkills.put(toolName, Arrays.asList(skills));
                    for (String skill : skills) {
                        skillTools.computeIfAbsent(skill, k -> new ArrayList<>()).add(toolName);
                    }
                } else {
                    // 无 @SkillTool → 始终暴露
                    alwaysAvailable.add(toolName);
                }
            }
        }
    }

    /** 判断某个工具是否为技能工具（需要加载技能才能用） */
    public boolean isSkillTool(String toolName) {
        return toolSkills.containsKey(toolName);
    }

    /** 判断是否始终可用 */
    public boolean isAlwaysAvailable(String toolName) {
        return alwaysAvailable.contains(toolName);
    }

    /** 获取某个技能包含的工具列表 */
    public List<String> getToolsBySkill(String skillName) {
        return skillTools.getOrDefault(skillName, List.of());
    }

    /** 获取始终可用的工具 */
    public Set<String> getAlwaysAvailable() {
        return alwaysAvailable;
    }

    /** 获取工具所属的技能列表 */
    public List<String> getSkillByTool(String toolName) {
        return toolSkills.getOrDefault(toolName, List.of());
    }
}
```

---

## 4. SkillAwareToolCallAdvisor

### 4.1 职责

继承 `DeepSeekToolCallAdvisor`，在 `doBeforeCall` 中拦截并过滤工具列表。

### 4.2 实现

```java
package com.yang.lblogserver.ai.advisor;

import com.yang.lblogserver.ai.skill.SkillToolRegistry;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.ToolCallback;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class SkillAwareToolCallAdvisor extends DeepSeekToolCallAdvisor {

    private final SkillToolRegistry skillToolRegistry;
    private static final String LOADED_SKILLS_KEY = "loadedSkills";

    public SkillAwareToolCallAdvisor(
            ToolCallingManager toolCallingManager,
            int order,
            boolean conversationHistoryEnabled,
            boolean streamToolCallResponses,
            SkillToolRegistry skillToolRegistry) {
        super(toolCallingManager, order, conversationHistoryEnabled, streamToolCallResponses);
        this.skillToolRegistry = skillToolRegistry;
    }

    @Override
    protected ChatClientRequest doInitializeLoop(ChatClientRequest request, CallAdvisorChain chain) {
        // 初始化已加载技能列表
        if (!request.context().containsKey(LOADED_SKILLS_KEY)) {
            request.context().put(LOADED_SKILLS_KEY, new HashSet<String>());
        }

        // 缓存所有 ToolCallback 按名查找
        if (request.prompt().getOptions() instanceof ToolCallingChatOptions toolOptions) {
            Map<String, ToolCallback> callbackCache = new ConcurrentHashMap<>();
            if (toolOptions.getToolCallbacks() != null) {
                for (ToolCallback cb : toolOptions.getToolCallbacks()) {
                    callbackCache.put(cb.getToolDefinition().name(), cb);
                }
            }
            request.context().put("toolCallbackCache", callbackCache);
        }

        return super.doInitializeLoop(request, chain);
    }

    @Override
    @SuppressWarnings("unchecked")
    protected ChatClientRequest doBeforeCall(ChatClientRequest request, CallAdvisorChain chain) {
        if (request.prompt().getOptions() instanceof ToolCallingChatOptions toolOptions) {
            Map<String, ToolCallback> callbackCache =
                (Map<String, ToolCallback>) request.context().get("toolCallbackCache");
            Set<String> loadedSkills =
                (Set<String>) request.context().get(LOADED_SKILLS_KEY);

            Set<ToolCallback> selected = new HashSet<>();

            // 1. 始终暴露的
            for (String toolName : skillToolRegistry.getAlwaysAvailable()) {
                if (callbackCache.containsKey(toolName)) {
                    selected.add(callbackCache.get(toolName));
                }
            }

            // 2. 已加载技能的工具
            if (loadedSkills != null) {
                for (String skillName : loadedSkills) {
                    for (String toolName : skillToolRegistry.getToolsBySkill(skillName)) {
                        if (callbackCache.containsKey(toolName)) {
                            selected.add(callbackCache.get(toolName));
                        }
                    }
                }
            }

            // 只传选中的工具
            ToolCallingChatOptions optionsCopy = toolOptions.copy();
            optionsCopy.setToolCallbacks(new ArrayList<>(selected));
            optionsCopy.setToolNames(Set.of());

            return super.doBeforeCall(
                request.mutate()
                    .prompt(request.prompt().mutate().chatOptions(optionsCopy).build())
                    .build(),
                chain);
        }
        return super.doBeforeCall(request, chain);
    }

    /** 外部调用：记录已加载的技能（由 LoadSkillTool 触发） */
    @SuppressWarnings("unchecked")
    public void loadSkill(String skillName, Map<String, Object> context) {
        Set<String> loaded = (Set<String>) context.computeIfAbsent(LOADED_SKILLS_KEY, k -> new HashSet<>());
        loaded.add(skillName);
    }
}
```

### 4.3 Advisor Chain 位置

```java
// AiConfig.java
@Bean
public ChatClient drawChatClient(...) {
    return ChatClient.builder(chatModel)
        .defaultAdvisors(
            chatHistoryAdvisor,             // P0: 加载历史
            skillAwareToolCallAdvisor)      // P1: 控制工具可见性（继承 DTA）
        .build();
}
```

`SkillAwareToolCallAdvisor` 替代原来的 `DeepSeekToolCallAdvisor`，继承它所有的 reasoning_content 处理逻辑，只增加 doBeforeCall 中的工具过滤。

---

## 5. LoadSkillTool

### 5.1 实现

```java
package com.yang.lblogserver.ai.skill.tool;

import com.yang.lblogserver.ai.advisor.SkillAwareToolCallAdvisor;
import com.yang.lblogserver.ai.skill.SkillToolRegistry;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

@Component
public class LoadSkillTool {

    private final SkillAwareToolCallAdvisor skillAdvisor;
    private final SkillToolRegistry skillToolRegistry;

    public LoadSkillTool(SkillAwareToolCallAdvisor skillAdvisor,
                         SkillToolRegistry skillToolRegistry) {
        this.skillAdvisor = skillAdvisor;
        this.skillToolRegistry = skillToolRegistry;
    }

    @Tool(name = "loadSkill", description = """
            加载技能包以获取对应的工具能力。
            可用技能：draw-expert（绘图）、chat-general（通用对话）。
            调用后该技能的工具会变得可用。
            """)
    public String loadSkill(String skillName, ToolContext ctx) {
        List<String> tools = skillToolRegistry.getToolsBySkill(skillName);
        if (tools.isEmpty()) {
            return "未知技能：" + skillName;
        }

        skillAdvisor.loadSkill(skillName, ctx.getContext());

        return "已加载 " + skillName + " 技能，可用工具：" + String.join(", ", tools);
    }
}
```

### 5.2 为什么不走 Request Scope

`LoadSkillTool` 在 `ToolContext` 中拿到了原始 `context` Map，直接向其中写入 `loadedSkills` 条目。`doBeforeCall` 每次都从同一个 `request.context()` 读取该条目，所以不需要 Request Scope bean。

但因为工具在多轮递归中执行，`doBeforeCall` 每轮都重新构建可见列表，写入 context 的结果会在下一轮生效。

---

## 6. 数据库设计

### 6.1 ai_skill_packages 表

```sql
CREATE TABLE IF NOT EXISTS `ai_skill_packages` (
    `id`            INT             NOT NULL AUTO_INCREMENT,
    `name`          VARCHAR(64)     NOT NULL COMMENT '技能包标识，如 draw-expert',
    `display_name`  VARCHAR(128)    NOT NULL COMMENT '展示名',
    `description`   VARCHAR(512)    DEFAULT NULL,
    `keywords`      VARCHAR(512)    NOT NULL COMMENT '搜索关键词，逗号分隔',
    `tools`         JSON            NOT NULL COMMENT '工具 bean name 列表',
    `is_active`     TINYINT         NOT NULL DEFAULT 1,
    `created_at`    DATETIME        DEFAULT CURRENT_TIMESTAMP,
    `updated_at`    DATETIME        DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

### 6.2 种子数据

```sql
INSERT INTO ai_skill_packages (name, display_name, description, keywords, tools) VALUES
('draw-expert', '绘图专家', 'AI 辅助 draw.io 图表生成',
 '画图,diagram,流程图,架构图,ER图,UML,时序图',
 '["displayDiagramTool"]'),
('chat-general', '通用对话', '通用 AI 对话助手',
 '聊天,问答,帮助,咨询',
 '[]');
```

---

## 7. 包结构与类设计

### 7.1 包结构

```
ai/
├── advisor/
│   ├── DeepSeekToolCallAdvisor.java        # 已有，保留
│   └── SkillAwareToolCallAdvisor.java      # 新增，继承 DTA
│
├── skill/
│   ├── annotation/
│   │   └── SkillTool.java                  # 新增：注解
│   ├── SkillToolRegistry.java              # 新增：注解扫描 + 索引
│   └── tool/
│       └── LoadSkillTool.java              # 新增：加载技能的工具
│
├── config/
│   └── AiConfig.java                       # 改造：注入 SkillAwareToolCallAdvisor
│
└── agent/
    └── draw/
        ├── DisplayDiagramTool.java         # 改造：加 @SkillTool("draw-expert")
        └── ...
```

### 7.2 修改列表

| 文件 | 操作 | 说明 |
|------|------|------|
| `ai/skill/annotation/SkillTool.java` | 新增 | 注解定义 |
| `ai/skill/SkillToolRegistry.java` | 新增 | 启动时扫描 + 建索引 |
| `ai/skill/tool/LoadSkillTool.java` | 新增 | 加载技能的工具 |
| `ai/advisor/SkillAwareToolCallAdvisor.java` | 新增 | 工具可见性过滤 |
| `ai/config/AiConfig.java` | 修改 | 替换 DTA 为 SkillAwareToolCallAdvisor |
| `ai/agent/draw/DisplayDiagramTool.java` | 修改 | 加 `@SkillTool("draw-expert")` |

### 7.3 不改的文件

| 文件 | 理由 |
|------|------|
| `DiagramService.java` | 不涉及工具注册，不需要改 |
| `DiagramController.java` | 不需要改 |
| `ChatHistoryAdvisor.java` | P0 逻辑，无关 |
| `ModelMessageConverter.java` | 无关 |

---

## 8. 实现步骤

### Step 1：@SkillTool 注解（0.5h）

- 新建 `ai/skill/annotation/SkillTool.java`

### Step 2：SkillToolRegistry（1h）

- 新建 `SkillToolRegistry.java`
- `@PostConstruct` 扫描所有 bean 的方法
- 提取 `@Tool` 和 `@SkillTool` 注解
- 构建 `toolSkills` / `skillTools` / `alwaysAvailable` 三个索引

### Step 3：LoadSkillTool（0.5h）

- 新建 `LoadSkillTool.java`
- `@Tool` 方法：接收 skillName，调用 skillAdvisor.loadSkill()
- 返回已加载的工具列表

### Step 4：SkillAwareToolCallAdvisor（2h）

- 新建，继承 `DeepSeekToolCallAdvisor`
- 重写 `doInitializeLoop`：初始化缓存
- 重写 `doBeforeCall`：过滤工具 + 调用 super
- 新增 `loadSkill()` 方法供 LoadSkillTool 调用

### Step 5：修改 AiConfig（0.5h）

- 注册 `SkillAwareToolCallAdvisor` bean
- 替换 `DeepSeekToolCallAdvisor`

### Step 6：修改 DisplayDiagramTool（0.5h）

- 在 `execute()` 方法上加 `@SkillTool("draw-expert")`

### Step 7：建表 + 种子数据（0.5h）

- 执行 DDL
- 插入 draw-expert / chat-general

### Step 8：编译验证（0.5h）

- `mcp__idea__build_project(rebuild: true)`
- 检查所有文件的 problems

---

## 9. 测试策略

### 9.1 单元测试

| 目标 | 内容 |
|------|------|
| SkillToolRegistry | 扫描注解、alwaysAvailable 判断、getToolsBySkill |
| SkillAwareToolCallAdvisor | doBeforeCall 过滤、loadSkill 后工具可见 |
| LoadSkillTool | 有效技能/无效技能返回 |

### 9.2 手动测试

| 场景 | 预期 |
|------|------|
| 进入画图页面，发送"帮我画图" | **初始只看到 loadSkill**，因为 displayDiagram 有 @SkillTool |
| AI 调 loadSkill("draw-expert") | 返回"已加载"，下一轮 displayDiagram 可见 |
| AI 调 display_diagram(xml) | 正常执行画图 |
| 发送其他无关请求 | loadSkill 始终可见，其他技能工具不可见 |
