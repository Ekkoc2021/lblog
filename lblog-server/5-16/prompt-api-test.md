# AI 提示词管理模块 — API 集成测试文档

> 日期：2026-05-16
> 对应模块：`ai/prompt/` — AdminPromptController

---

## 概述

AI 提示词管理模块提供对 AI 系统提示词的集中式管理，支持多模块（draw、chat、codegen 等）的提示词版本控制、审计追溯和缓存管理。

核心路由：`/api/v1/admin/ai/prompts`（需 ADMIN 角色）

### 数据模型

- **ai_prompts** — 提示词主表，INSERT-only 版本策略，每次修改创建新版本
- **ai_prompts_audit** — 审计日志表，记录每次操作的 action、操作人、新旧版本

### 设计策略

```
getPrompt(module, promptKey):
  1. DB 查询 (is_active=1) -> 有则返回
  2. 文件兜底 (classpath:prompts/{module}/{key}.md) -> 有则返回
  3. 全无 -> 返回空
```

---

## 前置条件

| 条件 | 说明 |
|------|------|
| 后端服务运行中 | 端口 8099，context-path `/iblogserver` |
| 数据库已初始化 | MySQL 8，表 `ai_prompts`、`ai_prompts_audit` 已创建 |
| 管理员账号可用 | 测试默认用 `admin` / `admin`，失败降级到 `ekko` / `admin123` |
| prompt .md 文件存在 | `classpath:prompts/draw/` 下应有 4 个文件 |

---

## 测试环境

```python
BASE = "http://localhost:8099/iblogserver/api/v1"
# 认证方式: Authorization: Bearer <token>
```

---

## 测试用例清单

### 认证与基础

| ID | 场景 | 方法 | 路径 | 预期结果 |
|----|------|------|------|---------|
| AUTH-01 | 管理员登录 | POST | /auth/login | code=0, data.accessToken 有值 |
| AUTH-01B | 降级登录（备用账号） | POST | /auth/login | code=0 |

### 提示词 CRUD

| ID | 场景 | 方法 | 路径 | 预期结果 |
|----|------|------|------|---------|
| P-01 | 无筛选列表（基线） | GET | /admin/ai/prompts | code=0, data 为数组 |
| P-02 | 按 module 筛选 | GET | /admin/ai/prompts?module=draw | code=0, 仅返回 draw 模块 |
| P-03 | 创建 system-default | POST | /admin/ai/prompts | code=0, data.id 有值 |
| P-04 | 创建 system-extended | POST | /admin/ai/prompts | code=0, data.id 有值 |
| P-05 | 创建 style-normal | POST | /admin/ai/prompts | code=0, data.id 有值 |
| P-06 | 创建 style-minimal | POST | /admin/ai/prompts | code=0, data.id 有值 |
| P-07 | 验证 module 筛选 | GET | /admin/ai/prompts?module=draw | code=0, count >= 4 |
| P-08 | 按 ID 查询详情 | GET | /admin/ai/prompts/{id} | code=0, data.id 匹配 |
| P-09 | 查询不存在的 ID | GET | /admin/ai/prompts/99999 | code=404 |
| P-10 | 无认证访问 | GET | /admin/ai/prompts | code=401/403 |

### 版本控制

| ID | 场景 | 方法 | 路径 | 预期结果 |
|----|------|------|------|---------|
| P-11 | 更新内容（创建新版本） | PUT | /admin/ai/prompts/{id} | code=0, 返回新 id 且 version 递增 |
| P-12 | 验证旧版本被标记失效 | GET | /admin/ai/prompts/{old_id} | code=0, data.isActive=false |
| P-13 | 更新元数据（sort_order/description） | PATCH | /admin/ai/prompts/{id} | code=0, 返回新 id, sortOrder 更新 |
| P-14 | 查看版本历史 | GET | /admin/ai/prompts/{id}/versions | code=0, data 为数组, count >= 1 |

### 删除与状态

| ID | 场景 | 方法 | 路径 | 预期结果 |
|----|------|------|------|---------|
| P-15 | 软删除 | DELETE | /admin/ai/prompts/{id}?operator=admin | code=0 |
| P-16 | 验证删除效果 | GET | /admin/ai/prompts/{id} | code=0, data.isActive=false |

### 文件导入与缓存

| ID | 场景 | 方法 | 路径 | 预期结果 |
|----|------|------|------|---------|
| P-17 | 从文件 seed | POST | /admin/ai/prompts/seed?module=draw | code=0, 返回成功消息 |
| P-18 | 重复 seed（幂等性） | POST | /admin/ai/prompts/seed?module=draw | code=0, 跳过已存在 |
| P-19 | 清除缓存 | POST | /admin/ai/prompts/reload | code=0 |
| P-25 | 无认证清除缓存 | POST | /admin/ai/prompts/reload | code=401/403 |

### 审计日志

| ID | 场景 | 方法 | 路径 | 预期结果 |
|----|------|------|------|---------|
| P-20 | 查看审计日志 | GET | /admin/ai/prompts/{id}/audit | code=0, data 为数组, 含 action 字段 |
| P-21 | 更新后审计日志 | GET | /admin/ai/prompts/{new_id}/audit | code=0, entries > 0 |

### 边界与错误

| ID | 场景 | 方法 | 路径 | 预期结果 |
|----|------|------|------|---------|
| P-22 | 创建空内容提示词 | POST | /admin/ai/prompts | code=0（允许空内容）或非 0 |
| P-23 | 更新不存在的 ID | PATCH | /admin/ai/prompts/99999 | code=404 或 500 |
| P-24 | 删除不存在的 ID | DELETE | /admin/ai/prompts/99999 | code=0（幂等删除） |

### AI 绘图冒烟测试

| ID | 场景 | 方法 | 路径 | 预期结果 |
|----|------|------|------|---------|
| P-26 | 绘图配置（公开） | GET | /draw/config | code=0 |
| P-27 | 绘图对话空消息 | POST | /draw/chat | code != 0（400 预期） |

---

## 运行方式

```bash
# 1. 确保后端已在 IntelliJ IDEA 中启动（LblogServerApplication）
#    端口 8099，profile default
#
# 2. 确保数据库中 ai_prompts / ai_prompts_audit 表已存在
#
# 3. 运行测试
python 5-16/test_ai_prompt.py
```

### 输出示例

```
============================================================
  AI Prompt Management API Test Suite
  Base: http://localhost:8099/iblogserver/api/v1
============================================================
...
============================================================
  TEST SUMMARY
============================================================
  PASSED: 24
  FAILED: 0
  WARN/SKIP: 2
  TOTAL:  26
```

---

## 清理机制

测试脚本自动跟踪所有通过 API 创建的提示词 ID，在测试结束后逐个执行 DELETE 软删除。如需手动清理：

```sql
-- 查看测试创建的记录
SELECT id, module, prompt_key, version, is_active, description
FROM ai_prompts
WHERE description LIKE '%(API test)%'
   OR created_by = 'admin';

-- 软删除
UPDATE ai_prompts SET is_active = 0 WHERE id IN (...);
```

---

## 测试失败排查

| 现象 | 可能原因 |
|------|---------|
| 登录失败 (AUTH-01) | admin 用户不存在或密码不符，会降级到 ekko |
| 所有 admin 接口 401 | token 获取失败或认证头格式错误 |
| 创建提示词 500 | ai_prompts 表不存在或字段不匹配 |
| Seed 接口 500 | `classpath:prompts/draw/` 目录下无 .md 文件 |
| GET 返回 `code=0, data=[]` | 当前无匹配数据，可能是首次运行 |
| PUT/PATCH 返回 500 | ID 对应的记录不存在（已在之前被删除） |
