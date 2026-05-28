# AI Prompt Management — Frontend Design

## Overview

在社区管理（AdminDashboard）中添加「提示词管理」页面，管理各 AI 模块的系统提示词。后端 API 已完整实现（`/api/v1/admin/ai/prompts`）。

### 核心用例

管理员通过此界面修改某个 Agent（如 draw、chat）的系统提示词，保存后自动生成新版本，旧版本自动失效。

## Route & Navigation

- **路由**: `/admin/prompts` → `PromptManage` 组件
- **入口**: `AdminDashboard` 新增卡片：`RobotOutlined`，标题「提示词管理」，描述「管理各 AI 模块的系统提示词，支持版本控制和审计」

### 文件清单

| 文件 | 操作 |
|------|------|
| `src/pages/admin/PromptManage.tsx` | 新建，主页面组件 |
| `src/pages/admin/AdminDashboard.tsx` | 修改，新增卡片 |
| `src/App.tsx` | 修改，新增路由 |
| `src/services/api.ts` | 修改，新增 API 函数 |
| `src/types/index.ts` | 修改，新增类型定义 |

## Page Layout

```
┌──────────────────────────────────────────────┐
│  提示词管理                     [重载缓存] [导入] │
├──────────────────────────────────────────────┤
│  [全部] [draw] [chat] [codegen] ...           │  ← Segmented/Tabs
├──────────────────────────────────────────────┤
│  module │ key │ 描述 │ 版本 │ 状态 │ 操作       │
│  ───────┼─────┼──────┼──────┼──────┼───────── │
│  draw   │sys..│ 默认  │ v3  ✓│ 编辑 版本 审计 删│  ← Table
│  draw   │sty..│ 简约  │ v1  ✓│ 编辑 版本 审计 删│
│  chat   │sys..│ 对话  │ v5  ✓│ 编辑 版本 审计 删│
│  ...    │ ... │ ...   │ ..   │ ...            │
└──────────────────────────────────────────────┘
```

### 组件层级

- **PromptManage** (page)
  - 工具栏: 标题 + 重载缓存按钮 + 导入文件按钮
  - 模块筛选: Ant Design `Segmented`，选项动态生成自列表数据
  - 数据表格: Ant Design `Table`，分页
  - **EditContentModal**: Markdown 内容编辑器 + 版本确认提示
  - **EditMetaModal**: 描述 / 排序 / 生效时间编辑
  - **VersionDrawer**: 版本历史时间线 + 各版本内容查看
  - **AuditDrawer**: 审计日志列表
  - **CreateModal**: 新建提示词表单
  - **SeedModal**: 输入 module 名，调用 seed API

## Data Types

```typescript
// src/types/index.ts

export interface AdminPrompt {
  id: number;
  module: string;
  promptKey: string;
  content: string;
  version: number;
  sortOrder: number;
  description: string | null;
  isActive: boolean;
  effectiveFrom: string | null;
  effectiveTo: string | null;
  createdBy: string;
  updatedBy: string;
  createdAt: string;
  updatedAt: string;
}

export interface AdminPromptAudit {
  id: number;
  promptId: number;
  module: string;
  promptKey: string;
  oldContent: string | null;
  newContent: string | null;
  oldVersion: number | null;
  newVersion: number | null;
  action: string;
  operator: string;
  remark: string | null;
  createdAt: string;
}
```

## API Functions

所有接口 base: `/api/v1/admin/ai/prompts`，复用 `request<T>()` 的统一 JWT 认证和错误处理。

| 函数 | 方法 | 路径 | 请求体 | 响应 |
|------|------|------|--------|------|
| `getAdminPrompts(module?, promptKey?, isActive?)` | GET | `/` | — | `PageResult<AdminPrompt>` (注: 后端返回 List，前端做客户端分页) |
| `getAdminPromptById(id)` | GET | `/{id}` | — | `AdminPrompt` |
| `createAdminPrompt(data)` | POST | `/` | `{ module, promptKey, content, description?, sortOrder? }` | `AdminPrompt` |
| `updateAdminPromptContent(id, content, operator)` | PUT | `/{id}` | `{ content, operator }` | `AdminPrompt` |
| `updateAdminPromptMeta(id, data)` | PATCH | `/{id}` | `{ description?, sortOrder?, operator }` | `AdminPrompt` |
| `deleteAdminPrompt(id, operator)` | DELETE | `/{id}?operator=xxx` | — | `null` |
| `getAdminPromptVersions(id)` | GET | `/{id}/versions` | — | `AdminPrompt[]` |
| `getAdminPromptAudit(id)` | GET | `/{id}/audit` | — | `AdminPromptAudit[]` |
| `reloadPromptCache()` | POST | `/reload` | — | `null` |
| `seedPrompts(module)` | POST | `/seed?module=xxx` | — | `string` |

> 注: 后端 `GET /` 返回 `List<PromptVO>` 而非分页。前端做客户端分页，每组 10 条。

## Error & Interaction States

| 场景 | 处理方式 |
|------|---------|
| 列表加载中 | Table `loading` prop |
| 列表为空 | `EmptyState` 组件 |
| 列表加载失败 | `message.error(err.message)` |
| 提交操作中 | `message.loading` → 按钮 loading |
| 提交成功 | `message.success` + 刷新列表 |
| 提交失败 | `message.error(err.message)` |
| 编辑内容确认 | Modal.confirm："修改后将生成新版本 v{n+1}，旧版本自动失效，确认？" |
| 删除确认 | Popconfirm："确定删除此提示词？将设为失效" |
| 401/403 | 由 `api.ts` 的 `request()` 统一拦截，页面无需处理 |

## Implementation Order

1. Types + API functions
2. PromptManage 页面主体（Table + 模块筛选）
3. EditContentModal + 版本确认
4. VersionDrawer + AuditDrawer
5. CreateModal + SeedModal
6. AdminDashboard 卡片 + App.tsx 路由
