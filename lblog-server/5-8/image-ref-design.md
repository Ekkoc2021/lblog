# 图片管理表设计

> 通用图片管理方案，支持文章引用、头像、未来相册/图床扩展

---

## 设计原则

- **图片本身是独立实体**，不依附于引用者存在
- **引用关系多态化**，任何实体（文章、用户、未来相册）都可引用图片
- **软删除**，防止误删后恢复困难

---

## 表结构

### 1. images（图片主表）

```sql
CREATE TABLE images (
  id            BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
  url           VARCHAR(500)  NOT NULL COMMENT '访问URL',
  storage_path  VARCHAR(500)  NOT NULL COMMENT '存储路径（磁盘路径或OSS key）',
  original_name VARCHAR(255)  NOT NULL COMMENT '原始文件名',
  mime_type     VARCHAR(50)   NOT NULL COMMENT 'MIME类型',
  file_size     BIGINT        NOT NULL DEFAULT 0 COMMENT '文件大小（字节）',
  width         INT           DEFAULT NULL COMMENT '图片宽度',
  height        INT           DEFAULT NULL COMMENT '图片高度',
  md5           VARCHAR(32)   DEFAULT NULL COMMENT '文件MD5，用于去重',
  created_by    BIGINT        DEFAULT NULL COMMENT '上传者用户ID',
  created_at    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deleted_at    DATETIME      DEFAULT NULL COMMENT '软删除时间',

  INDEX idx_md5 (md5),
  INDEX idx_created_by (created_by),
  INDEX idx_created_at (created_at)
) COMMENT '图片库';
```

### 2. image_usages（图片引用关系）

```sql
CREATE TABLE image_usages (
  id        BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
  image_id  BIGINT        NOT NULL COMMENT '图片ID',
  ref_type  VARCHAR(20)   NOT NULL COMMENT '引用类型：post / user / album / ...',
  ref_id    BIGINT        NOT NULL COMMENT '引用对象ID',
  field     VARCHAR(20)   NOT NULL COMMENT '引用字段：body / featured_image / avatar / cover / ...',

  INDEX idx_image_id (image_id),
  INDEX idx_ref (ref_type, ref_id),
  UNIQUE KEY uk_usage (image_id, ref_type, ref_id, field)
) COMMENT '图片引用关系';
```

---

## 数据示例

### 文章正文引用

| images | | image_usages | | |
|--------|---|--------------|---|---|
| id | url | image_id | ref_type | ref_id | field |
| 1 | /uploads/2026/05/abc.jpg | 1 | post | 42 | body |
| 2 | /uploads/2026/05/def.jpg | 2 | post | 42 | body |
| 2 | /uploads/2026/05/def.jpg | 2 | post | 42 | featured_image |

### 头像引用

| images | | image_usages | | |
|--------|---|--------------|---|---|
| id | url | image_id | ref_type | ref_id | field |
| 3 | /uploads/2026/05/avatar.jpg | 3 | user | 1 | avatar |

### 同一张图被多篇文章引用（合理去重）

| images | | image_usages | | |
|--------|---|--------------|---|---|
| id | url | image_id | ref_type | ref_id | field |
| 1 | /uploads/2026/05/abc.jpg | 1 | post | 42 | body |
| 1 | /uploads/2026/05/abc.jpg | 1 | post | 7  | body |

---

## 核心操作

### 上传图片

```sql
INSERT INTO images (url, storage_path, original_name, mime_type, file_size, width, height, md5, created_by)
VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?);
-- 返回 image_id 用于后续关联
```

### 保存文章时同步引用

```
1. 解析 body 中所有图片 URL → 查到对应 image_id
2. 收集 featuredImage → 查到对应 image_id
3. 事务：
   DELETE FROM image_usages WHERE ref_type='post' AND ref_id=?
   INSERT INTO image_usages (image_id, ref_type, ref_id, field) VALUES ...
```

### 更换头像时

```
DELETE FROM image_usages WHERE ref_type='user' AND ref_id=? AND field='avatar';
INSERT INTO image_usages (image_id, ref_type, ref_id, field) VALUES (?,'user',?,'avatar');
UPDATE users SET avatar = ? WHERE id = ?;
```

### 查找未引用的图片

```sql
SELECT i.* FROM images i
LEFT JOIN image_usages u ON u.image_id = i.id
WHERE i.deleted_at IS NULL
  AND u.id IS NULL;
```

### 软删除未引用图片

```sql
UPDATE images SET deleted_at = NOW()
WHERE id IN (
  SELECT i.id FROM images i
  LEFT JOIN image_usages u ON u.image_id = i.id
  WHERE i.deleted_at IS NULL AND u.id IS NULL
);
```

---

## 未来扩展

| 功能 | 扩展方式 |
|------|----------|
| 相册 | 新建 `albums` 表 + `ref_type='album'` 的使用记录 |
| 图片标签 | 新建 `image_tags` 多对多表 |
| 图床 API | 基于 `images` 表提供公开/鉴权的图片访问接口 |
| 图片去重 | `md5` 索引，上传前检查是否已存在 |
| 缩略图 | 在 `images` 表加 `thumbnail_url` 字段，或建 `image_sizes` 子表 |
| CDN/OSS 迁移 | `url` 和 `storage_path` 分离，切换存储后端只需改 `storage_path` |

---

## 前后端改动

| 模块 | 改动 |
|------|------|
| 后端新建 | `Images` 实体、Mapper、Service、Controller |
| 后端新建 | `ImageUsage` 实体、Mapper、Service |
| 后端修改 | 保存文章时同步 `image_usages` |
| 后端修改 | 更新头像时同步 `image_usages` |
| 后端新增 | `GET /api/v1/author/images` — 图片管理列表 |
| 后端新增 | `GET /api/v1/author/images/unreferenced` — 未引用图片 |
| 后端新增 | `DELETE /api/v1/author/images/{id}` — 删除图片（软删除） |
| 后端修改 | 上传接口返回值增加 `image_id` |
| 前端新增 | 创作中心新增「图片管理」页面 |
