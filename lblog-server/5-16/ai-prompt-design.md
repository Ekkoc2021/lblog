# AI 提示词管理系统设计文档

> 日期：2026-05-16
> 状态：设计阶段 v1

---

## 1. 背景与目标

### 1.1 现状

`ai/draw/PromptManager.java` 中约一万字符的系统提示词以 Java 15 文本块硬编码。

**问题：** 每次修改提示词需重新编译部署，无法快速迭代生产提示词，且修改无审计。

### 1.2 目标

- 提示词从代码中解耦，可独立修改
- 生产环境热修改即时生效
- 修改可追溯（审计日志）
- 支持多版本管理
- 统一管理多个 AI 模块的提示词

---

## 2. 核心策略：DB 优先 + 文件兜底

```
getPrompt(module, promptKey):
  1. 查询 DB ai_prompts WHERE module=? AND prompt_key=? AND is_active=1
     -> 有记录则返回 content
  2. 无 DB 数据 -> 加载 classpath:prompts/{module}/{prompt_key}.md
     -> 有文件则返回内容
  3. 全无 -> 报错
```

- DB 有就用 DB：生产环境通过管理端写入，即时生效
- DB 没有就用文件：开发环境直接编辑 markdown 快速调试
- 文件只在 seed 操作时写入 DB，不自动同步

---

## 3. 数据模型

### 3.1 ai_prompts 提示词主表

```sql
CREATE TABLE ai_prompts (
  id             BIGINT AUTO_INCREMENT PRIMARY KEY,
  module         VARCHAR(50)  NOT NULL COMMENT 'AI 模块标识，如 draw、chat、codegen',
  prompt_key     VARCHAR(100) NOT NULL COMMENT '提示词标识，如 system-default',
  content        TEXT         NOT NULL COMMENT '提示词内容 (Markdown)',
  version        INT          NOT NULL DEFAULT 1 COMMENT '版本号，递增',
  description    VARCHAR(500) DEFAULT NULL COMMENT '提示词说明',
  is_active      TINYINT(1)   NOT NULL DEFAULT 1 COMMENT '是否生效',
  effective_from DATETIME     DEFAULT NULL COMMENT '生效时间（未来灰度用）',
  effective_to   DATETIME     DEFAULT NULL COMMENT '失效时间（未来灰度用）',
  created_by     VARCHAR(100) DEFAULT NULL COMMENT '创建人',
  updated_by     VARCHAR(100) DEFAULT NULL COMMENT '最后修改人',
  created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_module_key_version (module, prompt_key, version),
  KEY idx_module (module),
  KEY idx_active (is_active)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='AI 提示词配置';
```

**版本策略：** INSERT-only。每次修改创建新版本记录，旧版本保留可回溯。

### 3.2 ai_prompts_audit 审计日志表

```sql
CREATE TABLE ai_prompts_audit (
  id            BIGINT AUTO_INCREMENT PRIMARY KEY,
  prompt_id     BIGINT       NOT NULL COMMENT '关联 ai_prompts.id',
  module        VARCHAR(50)  NOT NULL,
  prompt_key    VARCHAR(100) NOT NULL,
  old_content   TEXT         COMMENT '修改前内容',
  new_content   TEXT         COMMENT '修改后内容',
  old_version   INT          COMMENT '修改前版本',
  new_version   INT          COMMENT '修改后版本',
  action        VARCHAR(20)  NOT NULL COMMENT '操作类型',
  operator      VARCHAR(100) DEFAULT NULL COMMENT '操作人',
  remark        VARCHAR(500) DEFAULT NULL COMMENT '备注',
  created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_prompt_id (prompt_id),
  KEY idx_module_key (module, prompt_key),
  KEY idx_action_time (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='AI 提示词审计日志';
```

---

## 4. 文件约定

```
src/main/resources/prompts/
  draw/                     # module = draw
    system-default.md       # prompt_key = system-default
    system-extended.md
    style-normal.md
    style-minimal.md
  chat/                     # 未来
  codegen/                  # 未来
```

规则：目录名 = module，文件名(不含后缀) = prompt_key。

---

## 5. 包结构

```
ai/
  config/                     # AI 公共配置（已有）
    DiagramConfig.java
    DrawRateLimiter.java
  draw/                       # AI 绘图（已有，小幅改造）
    controller/DiagramController.java
    DiagramService.java
    PromptManager.java         # 改为注入 AiPromptService
    DisplayDiagramTool.java
    DrawChatRequest.java
    DrawConfigVO.java
    DiagramProperties.java
  prompt/                     # ★ 新增：公共提示词管理
    config/
      AiPromptProperties.java
    domain/
      AiPrompt.java
      AiPromptAudit.java
    mapper/
      AiPromptMapper.java
      AiPromptAuditMapper.java
      AiPromptMapper.xml
    service/
      AiPromptService.java
      impl/AiPromptServiceImpl.java
    controller/admin/
      AdminPromptController.java
    loader/
      FilePromptLoader.java
    vo/
      PromptVO.java
      PromptUpdateRequest.java
  chat/                       # 未来
  codegen/                    # 未来
```

### 数据流

```
DiagramController -> DiagramService
  -> PromptManager.buildSystemPrompt()
    -> AiPromptService.getPrompt("draw", "system-default")
      -> AiPromptMapper.selectActive()       (1. DB 查询)
      -> FilePromptLoader.load()             (2. 文件兜底)
      -> Caffeine cache                      (3. 缓存)
```

---

## 6. 管理端 API

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | /api/v1/admin/ai/prompts | 列表（支持筛选） |
| GET | /api/v1/admin/ai/prompts/{id} | 详情 |
| GET | /api/v1/admin/ai/prompts/{id}/versions | 历史版本 |
| POST | /api/v1/admin/ai/prompts | 新增 |
| PUT | /api/v1/admin/ai/prompts/{id} | 更新（INSERT 新版本） |
| DELETE | /api/v1/admin/ai/prompts/{id} | 软删除 |
| POST | /api/v1/admin/ai/prompts/reload | 清除缓存 |
| POST | /api/v1/admin/ai/prompts/seed | 文件导入 DB |
| GET | /api/v1/admin/ai/prompts/{id}/audit | 审计日志 |

---

## 7. 配置项

```yaml
lblog:
  ai:
    prompt:
      file-location: classpath:prompts/
      cache-enabled: true
      cache-max-size: 100
      cache-ttl-seconds: 0
```

---

## 8. PromptManager 改造要点

- 移除 5 个静态字符串常量（约 300 行）
- 注入 AiPromptService
- 组装逻辑不变：default vs extended、minimal 前置、{{MODEL_NAME}} 替换
- 方法签名 buildSystemPrompt(modelId, minimalStyle) 不变

---

## 9. 种子数据初始化

POST /api/v1/admin/ai/prompts/seed?module=draw
  -> 扫描 classpath:prompts/draw/*.md
  -> 检查 DB 是否已有记录
  -> 尚无则 INSERT version=1，写入审计日志

幂等：已存在的 (module, prompt_key) 不重复 seed。

---

## 10. 扩展性设计

### 多 AI 模块复用

每个模块只需：
1. 在 prompts/{module}/ 下放 markdown 文件
2. 通过 AiPromptService.getPrompt(module, key) 获取

表、Service、缓存、审计完全复用。

### 多版本控制

- 回滚：旧版本 is_active=1，当前 is_active=0
- 灰度：effective_from / effective_to 预留
- A/B 测试：可扩展 is_active 为权重字段

### 外部目录

file-location 默认 classpath，生产可改为 /opt/lblog/prompts/

---

## 11. 缓存策略

Caffeine 内存缓存，手动 reload 失效，上限 100 条，TTL=0（不过期）。

---

## 12. 涉及文件

### 新增（16 个）

1. ai/prompt/config/AiPromptProperties.java
2. ai/prompt/domain/AiPrompt.java
3. ai/prompt/domain/AiPromptAudit.java
4. ai/prompt/mapper/AiPromptMapper.java
5. ai/prompt/mapper/AiPromptAuditMapper.java
6. resources/.../mapper/AiPromptMapper.xml
7. ai/prompt/service/AiPromptService.java
8. ai/prompt/service/impl/AiPromptServiceImpl.java
9. ai/prompt/loader/FilePromptLoader.java
10. ai/prompt/controller/admin/AdminPromptController.java
11. ai/prompt/vo/PromptVO.java
12. ai/prompt/vo/PromptUpdateRequest.java
13-16. resources/prompts/draw/*.md (4 个文件)

### 修改（2 个）

1. ai/draw/PromptManager.java - 移除硬编码，注入 AiPromptService
2. application.yml - 添加 lblog.ai.prompt.* 配置

---

## 13. 依赖

无需新增。MyBatis + Caffeine + Spring ResourceLoader 均在 classpath 上。
