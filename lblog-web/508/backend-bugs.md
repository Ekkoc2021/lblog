# 后端 Bug 清单

> 2026-05-08

---

## Bug 1：图片引用详情未返回用户昵称

**表现：** 图片管理 → 点击引用数 → 显示 `用户[]`，括号内为空。

**原因：** 查询 `image_usages` 时只 LEFT JOIN 了 `posts` 表拿标题，没有 JOIN `users` 表拿昵称。

**修复：** 在图片列表接口的 usages 查询中，`refType='user'` 时 JOIN `users` 表取 `nickname` 作为 `refTitle`。

**涉及接口：** `GET /api/v1/admin/images`

**期望：**
```json
// refType=post 时
{ "refType": "post", "refId": 10, "field": "featured_image", "refTitle": "MySQL 索引优化实战" }

// refType=user 时
{ "refType": "user", "refId": 1, "field": "avatar", "refTitle": "Ekko" }
```

---

## Bug 2：可清理图片数一直为 0

**表现：** 图片管理顶部统计 →「可清理」始终显示 0。

**原因：** `GET /api/v1/admin/images/statistics` 接口可能未实现，或 `site_config` 中 `image_cleanup_days` 未配置。

**涉及接口：** `GET /api/v1/admin/images/statistics`

---

## Bug 3：配置管理接口可能未实现

**表现：** 前端调 4 个配置接口（列表/更新/添加/删除），不确定后端是否已全部实现。

**涉及接口：**
- `GET /api/v1/admin/configs`
- `PUT /api/v1/admin/configs`
- `POST /api/v1/admin/configs`
- `DELETE /api/v1/admin/configs?key=xxx`

---

## Bug 4：图片上传未同步 image_usages

**表现：** 上传图片后 `image_usages` 表无记录，导致图片管理里所有图片都显示"未引用"。

**涉及操作：**
- 文章保存时：未解析 body 中的图片 URL 写入 `image_usages`
- 设置封面图时：未写入 `image_usages`
- 上传头像时：未写入 `image_usages`

**期望：**
```sql
-- 保存文章后应有记录
INSERT INTO image_usages (image_id, ref_type, ref_id, field) VALUES (?, 'post', ?, 'body');
INSERT INTO image_usages (image_id, ref_type, ref_id, field) VALUES (?, 'post', ?, 'featured_image');

-- 上传头像后应有记录
INSERT INTO image_usages (image_id, ref_type, ref_id, field) VALUES (?, 'user', ?, 'avatar');
```

---

## 汇总

| # | Bug | 优先级 |
|:-:|-----|:------:|
| 1 | 图片引用详情未返回用户昵称 | 🔴 高 |
| 2 | 可清理图片数一直为 0 | 🔴 高 |
| 3 | 配置管理接口可能未实现 | 🔴 高 |
| 4 | 图片上传未同步 image_usages | 🟡 中 |
