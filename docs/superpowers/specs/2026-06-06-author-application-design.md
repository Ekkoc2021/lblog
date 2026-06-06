# 作者申请渠道设计文档

> 日期：2026-06-06 | 版本：1

## 1. 概述

为普通用户（role=user）提供申请成为作者的渠道。目前点击"创作中心"仅弹出 toast 提示"申请成为作者后才能使用创作中心"，但没有实际申请能力。

**核心流程：** 用户提交申请 → 管理员审核 → 通过后自动升级为作者角色

## 2. 架构

```
UserApplicationController  (/api/v1/user/application)
       ↓
AuthorApplicationService  →  AuthorApplicationMapper  →  author_applications 表
       ↓ (审核通过时)
UsersMapper.updateUser(role='author') + UserRolesMapper 更新角色关联
       ↑
AdminApplicationController  (/api/v1/admin/applications)
```

**不改动：** 现有 UsersMapper.updateUser 签名、角色体系
**新增：** 1 张表、6 个后端文件、2 个前端文件

## 3. 数据库

### 3.1 新表 `author_applications`

| 列 | 类型 | 说明 |
|----|------|------|
| id | BIGINT PK | 自增 |
| user_id | BIGINT FK→users | 申请人用户ID |
| reason | TEXT NOT NULL | 申请理由/自我介绍 |
| status | TINYINT NOT NULL DEFAULT 0 | 0=待审核 1=通过 2=拒绝 3=需补充 |
| feedback | TEXT NULL | 管理员反馈/补充要求 |
| reviewed_by | BIGINT NULL | 审核人用户ID |
| reviewed_at | DATETIME NULL | 审核时间 |
| created_at | DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP | 申请时间 |
| updated_at | DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP | 更新时间 |

**约束：** 应用层保证同一用户只能有一条待审(0)或需补充(3)状态的记录。

## 4. 后端 API

### 4.1 用户端 `/api/v1/user/application`

需登录，无角色限制。

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/application` | 首次提交申请，body: `{reason}` |
| GET | `/application` | 查自己的申请记录（含 status + feedback） |
| PUT | `/application` | 更新申请理由后重新提交，body: `{reason}`，状态回到 0 |

**业务规则：**
- POST：仅当用户无申请记录或现有记录状态为已拒绝(2)或无记录时允许
- PUT：仅当状态为拒绝(2)或需补充(3)时允许，reason 覆盖旧值
- 待审(0)或已通过(1)时不允许再提交

### 4.2 管理端 `/api/v1/admin/applications`

需登录，ROLE_ADMIN。

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/applications` | 分页列表，参数: `page, pageSize, status, keyword` |
| PUT | `/applications/{id}` | 审核，body: `{status, feedback}` |

**审核规则：**
- status=1（通过）→ 更新 `users.role='author'` + 更新 `user_roles` 表添加 author 角色关联
- status=2（拒绝）→ 可选 feedback
- status=3（需补充）→ feedback 必填

## 5. 前端

### 5.1 创作中心入口改造（`MainLayout.tsx` 第 295 行）

当前行为：`user?.role === 'user'` → `message.info('申请成为作者后才能使用创作中心')` + return

改为：
- `user.role === 'user'` 且有申请记录 → 弹出 `AuthorApplicationModal` 展示状态
- `user.role === 'user'` 且无申请记录 → 弹出 `AuthorApplicationModal` 展示申请表单
- `user.role !== 'user'` → 直接导航 `/author/posts`

### 5.2 `AuthorApplicationModal.tsx`（新组件）

四种状态对应四种展示：

| 状态 | 内容 |
|------|------|
| 无申请（首次） | TextArea(申请理由) + "提交申请"按钮 |
| 待审核(0) | "审核中"提示 + 申请内容只读展示 |
| 拒绝(2) / 需补充(3) | feedback 展示 + 可编辑 TextArea(预填原内容) + "重新提交"按钮 |
| 通过(1) | "您已是作者" + "进入创作中心"跳转按钮 |

### 5.3 `ApplicationManage.tsx`（新页面）

- 路由：`/admin/applications`
- 表格列：申请人（昵称/用户名）、申请理由、状态 Tag、申请时间、操作
- 操作按钮：通过、拒绝、要求补充
- 拒绝/需补充时弹 Modal 填 feedback（TextArea）

### 5.4 类型定义

```ts
// types/index.ts 新增
export interface AuthorApplication {
  id: number;
  userId: number;
  username: string;
  nickname: string;
  reason: string;
  status: number;  // 0=待审核 1=通过 2=拒绝 3=需补充
  feedback: string | null;
  reviewedBy: number | null;
  reviewedAt: string | null;
  createdAt: string;
  updatedAt: string;
}
```

### 5.5 API 函数

```ts
// services/api.ts 新增
submitApplication(reason: string): Promise<ApiResponse<AuthorApplication>>
getMyApplication(): Promise<ApiResponse<AuthorApplication | null>>
updateApplication(reason: string): Promise<ApiResponse<AuthorApplication>>
getApplications(params): Promise<ApiResponse<PageResult<AuthorApplication>>>
reviewApplication(id: number, status: number, feedback?: string): Promise<ApiResponse<null>>
```

## 6. 文件清单

| 层 | 新增 | 文件 |
|----|------|------|
| 后端 domain | 新增 | `auth/domain/AuthorApplication.java` |
| 后端 VO | 新增 | `auth/vo/ApplicationRequest.java` |
| 后端 VO | 新增 | `auth/vo/ApplicationVO.java` |
| 后端 VO | 新增 | `auth/vo/ApplicationReviewRequest.java` |
| 后端 mapper | 新增 | `auth/mapper/AuthorApplicationMapper.java` + XML |
| 后端 service | 新增 | `auth/service/AuthorApplicationService.java` |
| 后端 controller | 新增 | `auth/controller/user/UserApplicationController.java` |
| 后端 controller | 新增 | `auth/controller/admin/AdminApplicationController.java` |
| 前端 types | 改动 | `types/index.ts`（新增类型） |
| 前端 API | 改动 | `services/api.ts`（新增 5 个函数） |
| 前端组件 | 新增 | `components/AuthorApplicationModal.tsx` |
| 前端页面 | 新增 | `pages/admin/ApplicationManage.tsx` |
| 前端路由 | 改动 | `App.tsx`（新增 `/admin/applications` 路由 + admin 导航） |
| 前端入口 | 改动 | `layouts/MainLayout.tsx`（创作中心入口逻辑） |

## 7. 边界与约束

- 每个用户只能有一条申请记录（覆盖式更新 reason）
- 审核通过后自动升级为 author 角色，无需额外操作
- 不涉及邮件/站内通知（保持最简）
- 已有 author/admin 角色的用户不受影响，创作中心入口直接跳转
