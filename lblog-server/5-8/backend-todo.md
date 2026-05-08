# 后端待开发清单

> 汇总所有需要后端配合的功能

---

## 1. 图片管理 API

路径：`/api/v1/admin/images/*`（admin 角色权限，非 author）

### 1.1 图片统计概览

```
GET /api/v1/admin/images/statistics

Response:
{
  "code": 0, "message": "success",
  "data": {
    "totalImages": 156,
    "totalSize": 524288000,
    "referencedCount": 120,
    "unreferencedCount": 36,
    "utilizationRate": 76.9,
    "oldUnreferencedCount": 12,       // 超过 image_cleanup_days 天未引用
    "oldUnreferencedSize": 10240000    // 可释放空间
  }
}
```

### 1.2 图片列表（分页）

```
GET /api/v1/admin/images?page=1&pageSize=20&sort=newest&status=all&keyword=

参数:
  sort:   newest / oldest / largest / smallest / most_used / unused
  status: all / referenced / unreferenced
  keyword: 按原始文件名搜索

Response:
{
  "code": 0, "message": "success",
  "data": {
    "list": [{
      "id": 1,
      "url": "/uploads/2026/05/abc.jpg",
      "originalName": "screenshot.png",
      "mimeType": "image/png",
      "fileSize": 245760,
      "width": 1920,
      "height": 1080,
      "usageCount": 2,
      "usages": [
        { "refType": "post", "refId": 42, "field": "body", "refTitle": "文章标题" },
        { "refType": "post", "refId": 42, "field": "featured_image", "refTitle": "文章标题" }
      ],
      "createdAt": "2026-05-08T12:00:00"
    }],
    "total": 156, "page": 1, "pageSize": 20
  }
}
```

`usages.refTitle` 通过 LEFT JOIN `posts.title` 或 `users.nickname` 获取。

### 1.3 删除单张图片

```
DELETE /api/v1/admin/images/{id}
Response: { "code": 0, "message": "success", "data": null }
```

- 被引用的图片返回 400 + 提示信息
- 物理删除文件 + 硬删除 `images` 表记录

### 1.4 清理未引用图片

```
DELETE /api/v1/admin/images/cleanup?beforeDays=30&dryRun=true

参数:
  beforeDays:        清理超过多少天未引用的图片（默认从 site_config 读取 image_cleanup_days）
  dryRun:            true=仅预览不执行，false=实际删除

dryRun=true 响应:
{
  "code": 0, "message": "success",
  "data": {
    "dryRun": true,
    "currentUtilization": 65.4,
    "count": 12,
    "totalSize": 10240000,
    "images": [{ "id": 5, "url": "...", "originalName": "old.png", "fileSize": 1024000, "createdAt": "..." }]
  }
}

dryRun=false 响应:
{
  "code": 0, "message": "success",
  "data": {
    "dryRun": false,
    "beforeUtilization": 65.4,
    "afterUtilization": 82.1,
    "deletedCount": 12,
    "freedSize": 10240000
  }
}
```

> **注意**：物理删除文件 + 硬删除记录，不可恢复。

---

## 2. 配置管理 API

路径：`/api/v1/admin/configs`

### 2.1 获取所有配置

```
GET /api/v1/admin/configs
Response:
{
  "code": 0, "message": "success",
  "data": [{ "configKey": "registration_enabled", "configValue": "true" }]
}
```

### 2.2 批量更新配置

```
PUT /api/v1/admin/configs
Body: { "registration_enabled": "false", "site_title": "我的社区" }
Response: { "code": 0, "message": "success", "data": null }
```

### 2.3 添加配置

```
POST /api/v1/admin/configs
Body: { "configKey": "new_key", "configValue": "value" }
Response: { "code": 0, "message": "success", "data": null }
```

### 2.4 删除配置

```
DELETE /api/v1/admin/configs?key=obsolete_key
Response: { "code": 0, "message": "success", "data": null }
```

---

## 3. 用户头像 API

### 3.1 上传头像

```
PUT /api/v1/user/avatar
Content-Type: multipart/form-data
Body: file=图片文件

Response: { "code": 0, "message": "success", "data": { "id": 3, "url": "/uploads/2026/05/avatar.jpg" } }
```

后端逻辑：
1. 保存文件到磁盘
2. INSERT INTO images (url, storage_path, original_name, mime_type, file_size, width, height)
3. 事务内：DELETE image_usages WHERE ref_type='user' AND ref_id=? AND field='avatar'
4. INSERT image_usages (image_id, ref_type, ref_id, field) VALUES (?,'user',?,'avatar')
5. UPDATE users SET avatar = ? WHERE id = ?

### 3.2 删除头像

```
DELETE /api/v1/user/avatar
Response: { "code": 0, "message": "success", "data": null }
```

逻辑：DELETE image_usages + UPDATE users SET avatar = NULL（图片文件保留）

---

## 4. 用户管理 API

路径：`/api/v1/admin/users/*`（admin 角色权限）

### 4.1 用户列表

```
GET /api/v1/admin/users?page=1&pageSize=20&keyword=&role=&status=&inactiveDays=

参数:
  inactiveDays: 筛选超过 N 天未登录的用户

Response:
{
  "code": 0, "message": "success",
  "data": {
    "list": [{
      "id": 1,
      "username": "ekko",
      "nickname": "Ekko",
      "email": "ekko@example.com",
      "avatar": null,
      "roles": ["admin"],              // 从 user_roles 关联查
      "roleLabels": ["管理员"],
      "status": 1,
      "postCount": 23,
      "lastLoginAt": "2026-05-08T12:00:00",
      "loginCount": 128,
      "createdAt": "2026-01-01T00:00:00"
    }],
    "total": 12, "page": 1, "pageSize": 20
  }
}
```

### 4.2 创建用户

```
POST /api/v1/admin/users
Body: { "username": "newuser", "password": "password123", "nickname": "新用户", "email": "new@example.com", "roleIds": [2] }
Response: { "code": 0, "message": "success", "data": { "id": 13 } }
```

### 4.3 更新用户

```
PUT /api/v1/admin/users/{id}
Body: { "nickname": "新昵称", "email": "new@example.com", "roleIds": [2, 3], "status": 1 }
Response: { "code": 0, "message": "success", "data": null }
```

后端逻辑：更新角色时，先删 `user_roles` 中该用户的关联，再批量插入新的。

### 4.4 重置密码

```
PUT /api/v1/admin/users/{id}/reset-password
Body: { "newPassword": "newpass123" }
Response: { "code": 0, "message": "success", "data": null }
```

### 4.5 删除用户（软删除）

```
DELETE /api/v1/admin/users/{id}
Response: { "code": 0, "message": "success", "data": null }
```

### 4.6 获取角色列表（下拉用）

```
GET /api/v1/admin/roles
Response:
{
  "code": 0, "message": "success",
  "data": [
    { "id": 1, "name": "admin", "label": "管理员" },
    { "id": 2, "name": "author", "label": "作者" },
    { "id": 3, "name": "user", "label": "用户" }
  ]
}
```

---

## 5. 数据库变更

### 5.1 site_config 种子数据

```sql
INSERT INTO site_config (config_key, config_value) VALUES
('image_cleanup_days', '30')
ON DUPLICATE KEY UPDATE config_value = VALUES(config_value);
```

### 5.2 user_roles 表（新建）

```sql
CREATE TABLE user_roles (
  id       BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id  BIGINT NOT NULL COMMENT '用户ID',
  role_id  BIGINT NOT NULL COMMENT '角色ID',
  UNIQUE KEY uk_user_role (user_id, role_id)
) COMMENT '用户角色关联表';
```

### 5.3 roles 表

```sql
CREATE TABLE roles (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  name        VARCHAR(50) NOT NULL COMMENT '角色名称：admin/author/user',
  label       VARCHAR(50) NOT NULL COMMENT '显示名',
  description VARCHAR(255) DEFAULT NULL,
  sort_order  INT NOT NULL DEFAULT 0,
  created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) COMMENT '角色表';
```

### 5.4 角色种子数据

```sql
INSERT INTO roles (name, label, description, sort_order) VALUES
('admin', '管理员', '拥有所有权限', 0),
('author', '作者', '可以管理自己的文章和评论', 1),
('user', '用户', '只能浏览和评论', 2);
```

---

## 6. 现有用户数据迁移

```sql
-- 将现有 users.role 值迁移到 user_roles 表
INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id FROM users u, roles r WHERE u.role = r.name;
```

---

## 汇总

| 模块 | 接口 | 优先级 |
|------|------|:------:|
| 图片管理 | 统计 / 列表 / 删除 / 清理 | 🔴 高 |
| 配置管理 | 列表 / 更新 / 添加 / 删除 | 🔴 高 |
| 用户头像 | 上传 / 删除 + image_usages 维护 | 🟡 中 |
| 用户管理 | 列表 / 创建 / 更新 / 重置密码 / 删除 | 🟡 中 |
| 数据库 | user_roles 表 + roles 表 + site_config 种子数据 | 🟡 中 |
