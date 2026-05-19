# P1 Skill 管理接口设计

> 状态：📄 接口设计待实现
> 前置：P1 skill 模块基础（SkillPackage CRUD 已完成）

---

## 接口总览

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/v1/admin/skills` | 技能包列表 |
| GET | `/api/v1/admin/skills/{name}` | 技能包详情 |
| POST | `/api/v1/admin/skills` | 创建技能包 |
| PUT | `/api/v1/admin/skills/{name}` | 更新技能包 |
| PATCH | `/api/v1/admin/skills/{name}/toggle` | 启用/停用 |
| DELETE | `/api/v1/admin/skills/{name}` | 删除技能包 |

---

## 模型

### SkillPackageVO（返回）

```json
{
  "name": "draw-expert",
  "agentType": "draw",
  "displayName": "Draw Expert",
  "description": "专业绘图助手，擅长 draw.io 流程图",
  "keywords": "画图,diagram,流程图,ER图",
  "prompt": "## Role\nYou are a draw.io expert...",
  "isActive": true,
  "createdAt": "2026-05-17T10:00:00",
  "updatedAt": "2026-05-17T10:00:00"
}
```

### SkillPackageCreateReq

```json
{
  "name": "draw-expert",
  "agentType": "draw",
  "displayName": "Draw Expert",
  "description": "专业绘图助手",
  "keywords": "画图,diagram",
  "prompt": "## Role\nYou are..."
}
```

## 接口详情

### GET `/api/v1/admin/skills`

Query 参数：
- `agentType` — 按 agent 类型筛选（可选）
- `activeOnly` — 仅返回启用项（可选，默认 false）

响应：
```json
{
  "code": 0,
  "data": [
    {
      "name": "draw-expert",
      "displayName": "Draw Expert",
      "description": "专业绘图助手",
      "agentType": "draw",
      "isActive": true,
      "updatedAt": "2026-05-17T10:00:00"
    }
  ]
}
```

### GET `/api/v1/admin/skills/{name}`

响应：
```json
{
  "code": 0,
  "data": {
    "name": "draw-expert",
    "agentType": "draw",
    "displayName": "Draw Expert",
    "description": "专业绘图助手",
    "keywords": "画图,diagram,流程图,ER图",
    "prompt": "## Role\nYou are a draw.io expert...",
    "isActive": true,
    "createdAt": "2026-05-17T10:00:00",
    "updatedAt": "2026-05-17T10:00:00"
  }
}
```

### POST `/api/v1/admin/skills`

请求体：`SkillPackageCreateReq`
- `name` 唯一性校验，重复返回 400
- `prompt` 不能为空

### PUT `/api/v1/admin/skills/{name}`

请求体：同 Create（全量更新）
- 不存在的 name 返回 404
- 不允许修改 `name`（主键标识）

### PATCH `/api/v1/admin/skills/{name}/toggle`

切换 `is_active` 状态。无请求体。

响应：
```json
{
  "code": 0,
  "data": {
    "name": "draw-expert",
    "isActive": false
  }
}
```

### DELETE `/api/v1/admin/skills/{name}`

软删除/硬删除待定（建议硬删除，因为无业务关联数据依赖）。

---

## 安全

- 全部接口需要 `ADMIN` 角色
- 统一前缀 `/api/v1/admin/skills`，走 Spring Security 已有的 admin 路径保护
- 请求体校验：`jakarta.validation` + `@Validated`

---

## 实现步骤

1. 创建 `SkillPackageVO`（避免 domain 直接暴露）
2. 创建 `SkillPackageCreateReq` / `SkillPackageUpdateReq`
3. 在 `SkillService` 新增：`create` / `update` / `delete` / `toggleActive`
4. 创建 `SkillPackageMapper` 新增：`insert` / `update` / `deleteByName`
5. 创建 `SkillPackageMapper.xml` 新增对应的 SQL
6. 创建 `ai/skill/controller/AdminSkillController.java`
7. 权限配置（确认 admin 路径已被 SecurityConfig 保护）
