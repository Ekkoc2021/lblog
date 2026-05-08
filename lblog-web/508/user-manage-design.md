# 用户管理与 RBAC 设计

---

## 一、现状分析

当前 `users` 表只有 `role`（admin / author / user）和 `status`（1=正常 / 0=禁用），权限判断散落在代码中硬编码，没有统一的权限管理。

## 二、RBAC 设计

### 核心概念

```
用户 (User)  ──→ 角色 (Role)  ──→ 权限 (Permission)
```

- 一个用户有一个角色（暂不设计多角色）
- 一个角色包含多个权限
- 权限是操作的最小单元

### 表设计

#### roles（角色表）

```sql
CREATE TABLE roles (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  name        VARCHAR(50)  NOT NULL COMMENT '角色名称：admin / author / user',
  label       VARCHAR(50)  NOT NULL COMMENT '显示名：管理员 / 作者 / 用户',
  description VARCHAR(255) DEFAULT NULL COMMENT '角色描述',
  sort_order  INT          NOT NULL DEFAULT 0 COMMENT '排序',
  created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) COMMENT '角色表';
```

#### permissions（权限表）

```sql
CREATE TABLE permissions (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  code        VARCHAR(100) NOT NULL COMMENT '权限编码：post.create / user.manage',
  label       VARCHAR(50)  NOT NULL COMMENT '权限显示名：创建文章',
  module      VARCHAR(50)  NOT NULL COMMENT '所属模块：post / user / config / comment',
  created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
) COMMENT '权限表';
```

#### role_permissions（角色权限关联）

```sql
CREATE TABLE role_permissions (
  id            BIGINT AUTO_INCREMENT PRIMARY KEY,
  role_id       BIGINT NOT NULL COMMENT '角色ID',
  permission_id BIGINT NOT NULL COMMENT '权限ID',
  UNIQUE KEY uk_role_perm (role_id, permission_id)
) COMMENT '角色权限关联';
```

### 权限列表

| 编码 | 显示名 | 模块 | admin | author | user |
|------|--------|------|:-----:|:------:|:----:|
| `user.manage` | 用户管理 | user | ✅ | | |
| `user.assign_role` | 分配角色 | user | ✅ | | |
| `post.create` | 创建文章 | post | ✅ | ✅ | |
| `post.edit` | 编辑文章 | post | ✅ | ✅ | |
| `post.delete` | 删除文章 | post | ✅ | ✅ | |
| `post.publish` | 发布文章 | post | ✅ | ✅ | |
| `post.manage_all` | 管理所有文章 | post | ✅ | | |
| `category.manage` | 分类管理 | category | ✅ | ✅ | |
| `tag.manage` | 标签管理 | tag | ✅ | ✅ | |
| `series.manage` | 专栏管理 | series | ✅ | ✅ | |
| `comment.manage` | 评论管理 | comment | ✅ | ✅ | |
| `comment.delete` | 删除评论 | comment | ✅ | ✅ | |
| `config.manage` | 配置管理 | config | ✅ | | |
| `image.manage` | 图片管理 | image | ✅ | | |
| `image.cleanup` | 清理图片 | image | ✅ | | |
| `statistics.view` | 查看统计 | statistics | ✅ | ✅ | |

### 权限判断（后端）

```java
// 方式一：注解（推荐）
@PreAuthorize("hasPermission('user.manage')")
@GetMapping("/users")
public ApiResponse<List<UserVO>> listUsers() { ... }

// 方式二：编码
@Service
public class PostServiceImpl {
    public void deletePost(Long id, Long userId) {
        if (!permissionService.hasPermission(userId, "post.delete")) {
            throw new ForbiddenException("无权限");
        }
        // 如果是 post.manage_all，可直接删除
        // 如果不是，只能删自己的
        if (!permissionService.hasPermission(userId, "post.manage_all")) {
            Post post = postMapper.selectById(id);
            if (!post.getAuthorId().equals(userId)) {
                throw new ForbiddenException("只能删除自己的文章");
            }
        }
    }
}
```

---

## 三、用户管理

### 后端接口

#### 用户列表

```
GET /api/v1/admin/users?page=1&pageSize=20&keyword=&role=&status=&inactiveDays=

参数:
  inactiveDays: 筛选超过 N 天未登录的用户（可选）

Response:
{
  "code": 0,
  "message": "success",
  "data": {
    "list": [
      {
        "id": 1,
        "username": "ekko",
        "nickname": "Ekko",
        "email": "ekko@example.com",
        "avatar": "/avatars/ekko.png",
        "role": "admin",
        "status": 1,
        "postCount": 23,
        "lastLoginAt": "2026-05-08T12:00:00",
        "loginCount": 128,
        "createdAt": "2026-01-01T00:00:00"
      }
    ],
    "total": 12,
    "page": 1,
    "pageSize": 20
  }
}
```

#### 获取用户详情

```
GET /api/v1/admin/users/{id}

Response:
{
  "code": 0,
  "message": "success",
  "data": {
    "id": 1,
    "username": "ekko",
    "nickname": "Ekko",
    "email": "ekko@example.com",
    "avatar": null,
    "role": "admin",
    "status": 1,
    "postCount": 23,
    "lastLoginAt": "2026-05-08T12:00:00",
    "loginCount": 128,
    "createdAt": "2026-01-01T00:00:00"
  }
}
```

#### 创建用户

```
POST /api/v1/admin/users
Content-Type: application/json

{
  "username": "newuser",
  "password": "password123",
  "nickname": "新用户",
  "email": "new@example.com",
  "role": "author"
}

Response:
{
  "code": 0,
  "message": "success",
  "data": { "id": 13 }
}
```

#### 更新用户

```
PUT /api/v1/admin/users/{id}
Content-Type: application/json

{
  "nickname": "新昵称",
  "email": "new@example.com",
  "role": "author",
  "status": 1
}
```

#### 重置密码

```
PUT /api/v1/admin/users/{id}/reset-password
Content-Type: application/json

{
  "newPassword": "newpass123"
}
```

#### 删除用户（软删除）

```
DELETE /api/v1/admin/users/{id}

Response: { "code": 0, "message": "success", "data": null }
```

#### 获取角色列表（含权限）

```
GET /api/v1/admin/roles

Response:
{
  "code": 0,
  "message": "success",
  "data": [
    {
      "id": 1,
      "name": "admin",
      "label": "管理员",
      "description": "拥有所有权限",
      "permissions": ["user.manage", "post.create", "config.manage", ...]
    },
    {
      "id": 2,
      "name": "author",
      "label": "作者",
      "description": "可以管理自己的文章和评论",
      "permissions": ["post.create", "post.edit", ...]
    },
    {
      "id": 3,
      "name": "user",
      "label": "用户",
      "description": "只能浏览和评论",
      "permissions": []
    }
  ]
}
```

#### 更新角色权限

```
PUT /api/v1/admin/roles/{id}/permissions
Content-Type: application/json

{
  "permissionCodes": ["post.create", "post.edit", "post.delete", "post.publish", "comment.manage"]
}

Response: { "code": 0, "message": "success", "data": null }
```

后端逻辑：删除该角色的所有旧权限关联 → 批量插入新权限关联。

#### 获取所有权限列表（用于前端勾选）

```
GET /api/v1/admin/permissions

Response:
{
  "code": 0,
  "message": "success",
  "data": [
    { "code": "user.manage", "label": "用户管理", "module": "user" },
    { "code": "post.create", "label": "创建文章", "module": "post" },
    { "code": "post.edit",   "label": "编辑文章", "module": "post" },
    { "code": "config.manage", "label": "配置管理", "module": "config" },
    ...
  ]
}
```

---

## 四、前端页面

### 路由

```
/admin/users         → 用户列表
```

### 入口

在 `AdminDashboard.tsx` 新增卡片。

### 页面功能

#### 用户列表

```
┌─ Card: 用户管理 ──────────────────────────────────┐
│                                                     │
│  搜索: [______] [全部角色▾] [全部状态▾] [活跃度▾] [+ 新建用户] │
│                                                     │
│  ┌─ Table ────────────────────────────────────────┐ │
│  │ 用户名  │ 昵称  │ 角色   │ 状态  │ 文章  │ 最后登录    │ 操作 │ │
│  │ ekko   │ Ekko  │ 管理员 │ 正常  │  23  │ 1 小时前    │ 编辑 │ │
│  │ alice  │ Alice │ 作者   │ 正常  │  12  │ 3 天前      │ 编辑 │ │
│  │ bob    │ Bob   │ 作者   │ 禁用  │   5  │ 45 天前     │ 编辑 │ │
│  │ ...    │ ...   │ ...    │ ...   │ ...  │ ...  │ │
│  │ charlie│ C     │ 用户    │ 正常  │   0  │ 编辑 │ │
│  │                 ↑  lastLoginAt 列显示最后登录时间/天数   │ │
│  └────────────────────────────────────────────────┘ │
│                                                     │
│  分页: < 1 2 3 >  共 12 人                          │
└─────────────────────────────────────────────────────┘
```

#### 新建/编辑用户 Modal

```
┌─ Modal: 新建用户 ─────────────────┐
│                                    │
│  用户名: [________] (新建时必填)    │
│  密码:   [________] (新建时必填)    │
│  昵称:   [________]                │
│  邮箱:   [________]                │
│  角色:   [管理员 ▾]                │  ← 从 roles 表动态加载
│  状态:   [正常 / 禁用]             │
│                                    │
│          [取消]  [保存]             │
└────────────────────────────────────┘
```

编辑时用户名和密码不可修改，改为「重置密码」按钮。

#### 重置密码

```
点击「重置密码」→ Modal 输入新密码 → 确认
```

### 状态处理

| 状态 | 表现 |
|------|------|
| Loading | Table 自带 Spin |
| Error | message.error + 重试 |
| Empty | 暂无用户 |
| 禁用用户 | Table 行显示灰色文字 + 禁用 Tag |
| 删除自己 | 不可删除自己（操作栏隐藏删除按钮） |

---

## 五、前端改动清单

| 文件 | 改动 |
|------|------|
| `api.ts` | 新增 `AdminUser`、`RoleInfo` 类型 + `getAdminUsers`、`getAdminUser`、`createAdminUser`、`updateAdminUser`、`resetUserPassword`、`deleteAdminUser`、`getRoles` 接口 |
| `App.tsx` | 新增路由 `/admin/users` |
| `AdminDashboard.tsx` | 新增用户管理卡片 |
| 新建 `UserManage.tsx` | 用户管理页面（`src/pages/admin/`，含列表/新建/编辑/删除/重置密码） |

---

## 六、数据库初始化 SQL

```sql
-- 角色表
INSERT INTO roles (name, label, description, sort_order) VALUES
('admin', '管理员', '拥有所有权限', 0),
('author', '作者', '可以管理自己的文章和评论', 1),
('user', '用户', '只能浏览和评论', 2);

-- 权限表
INSERT INTO permissions (code, label, module) VALUES
('user.manage', '用户管理', 'user'),
('user.assign_role', '分配角色', 'user'),
('post.create', '创建文章', 'post'),
('post.edit', '编辑文章', 'post'),
('post.delete', '删除文章', 'post'),
('post.publish', '发布文章', 'post'),
('post.manage_all', '管理所有文章', 'post'),
('category.manage', '分类管理', 'category'),
('tag.manage', '标签管理', 'tag'),
('series.manage', '专栏管理', 'series'),
('comment.manage', '评论管理', 'comment'),
('comment.delete', '删除评论', 'comment'),
('config.manage', '配置管理', 'config'),
('image.manage', '图片管理', 'image'),
('image.cleanup', '清理图片', 'image'),
('statistics.view', '查看统计', 'statistics');

-- admin 拥有所有权限
INSERT INTO role_permissions (role_id, permission_id)
SELECT 1, id FROM permissions;

-- author 拥有内容管理权限
INSERT INTO role_permissions (role_id, permission_id)
SELECT 2, id FROM permissions WHERE code IN (
  'post.create', 'post.edit', 'post.delete', 'post.publish',
  'category.manage', 'tag.manage', 'series.manage',
  'comment.manage', 'comment.delete',
  'statistics.view'
);
```
