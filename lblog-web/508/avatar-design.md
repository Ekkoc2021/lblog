# 用户头像设计

---

## 设计原则

- 头像本质上是 `images` 表中的一张普通图片，通过 `image_usages` 关联到用户
- 未上传头像时展示默认首字母头像，不上传占位图文件
- 上传头像后替换显示，可删除恢复默认

---

## 接口设计

### 上传头像

```
PUT /api/v1/user/avatar
Content-Type: multipart/form-data

Request:
  file: 图片文件（同 uploadImage 的图片上传接口）

Response:
{
  "code": 0,
  "message": "success",
  "data": {
    "id": 3,             // images 表 ID
    "url": "/uploads/2026/05/avatar_ekko.jpg"
  }
}
```

权限：登录用户（修改自己的头像）

### 删除头像

```
DELETE /api/v1/user/avatar

Response:
{
  "code": 0,
  "message": "success",
  "data": null
}
```

权限：登录用户（删除自己的头像）
后端逻辑：软删除 `image_usages` 记录，将 `users.avatar` 置空

---

## 后端处理逻辑

### 上传头像

```
1. 校验文件类型（仅允许 image/*）
2. 校验文件大小（同 imageMaxSize 配置）
3. 保存文件到磁盘/OSS
4. INSERT INTO images (...) ← 写入图片库
5. 事务：
   a. DELETE FROM image_usages WHERE ref_type='user' AND ref_id=? AND field='avatar'
   b. INSERT INTO image_usages (image_id, ref_type, ref_id, field) VALUES (?,'user',?,'avatar')
   c. UPDATE users SET avatar = ? WHERE id = ?
6. 返回 { id, url }
```

### 删除头像

```
1. 事务：
   a. DELETE FROM image_usages WHERE ref_type='user' AND ref_id=? AND field='avatar'
   b. UPDATE users SET avatar = NULL WHERE id = ?
2. 返回成功（图片文件保留，仅解除引用）
```

### 获取用户信息（已有接口）

`GET /api/v1/auth/me` 返回的 `user.avatar`：
- 有头像 → 返回图片 URL
- 无头像 → 返回 `null`

---

## 前端展示

### 有头像时

```tsx
<Avatar src={user.avatar} />
```

Ant Design 的 `Avatar` 组件传入 `src` 会自动显示图片。

### 无头像时（当前已实现）

```tsx
<Avatar icon={<UserOutlined />} style={{ background: '#1e80ff' }} />
```
或首字母：
```tsx
<Avatar style={{ background: '#1e80ff' }}>
  {user.nickname?.[0] || 'U'}
</Avatar>
```

### 需要修改的前端文件

| 文件 | 改动 |
|------|------|
| UserSettingsDrawer.tsx | Avatar 增加 `src` 显示真实头像 + 上传/删除按钮 |
| MainLayout.tsx | 导航栏头像显示真实头像 |
| PostDetail.tsx | 文章作者头像（已有首字母，可加真实头像） |
| ArticleCard.tsx | 文章作者头像 |
| api.ts | 新增 `updateAvatar` / `deleteAvatar` 接口 |

---

## 无头像时的处理策略（纯前端）

不上传任何占位图，完全由前端 fallback 处理：

```tsx
<Avatar
  src={user.avatar || undefined}
  style={{ background: user.avatar ? undefined : '#1e80ff' }}
>
  {!user.avatar && (user.nickname?.[0] || 'U')}
</Avatar>
```

逻辑：
- `user.avatar` 有值 → 显示图片
- `user.avatar` 为 `null` → 显示首字母 + 蓝色背景

> 不引入默认头像图片文件，避免多余的网络请求和存储。

---

## 引用关联

上传头像后，`image_usages` 表记录示例：

| image_id | ref_type | ref_id | field |
|----------|----------|--------|-------|
| 3 | user | 1 | avatar |

删除头像后，该记录被删除，`users.avatar` 置空。图片文件保留在 `images` 表中，可通过"未引用图片"管理页面清理。
