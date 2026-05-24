# Phase 2 缓存模块测试场景

> 状态：待执行 | 日期：2026-05-24

## 测试方法

每个场景通过以下步骤验证：
1. 调公开接口确认初始状态（首次必 cache miss）
2. 再调一次确认 cache hit
3. 执行后台写操作
4. 调公开接口确认 cache miss（说明缓存已失效）或 cache hit（说明缓存未受影响）
5. 检查 `service-logs/debug.log` 确认日志链路

---

## 场景 1：categories 缓存 — Admin 分类 CRUD

### 1.1 首次访问 → cache miss
```
GET /api/v1/categories?limit=5
预期日志: "cache miss: categories limit=5, loading from DB"
```

### 1.2 二次访问 → cache hit
```
GET /api/v1/categories?limit=5
预期日志: "cache hit: categories limit=5"
```

### 1.3 Admin 创建分类 → 缓存失效
```
POST /admin/categories {"name":"测试分类","slug":"test-cat"}
GET /api/v1/categories?limit=5
预期: 返回数据包含新分类, 日志: "categories cache cleared" → "cache miss"
```

### 1.4 Admin 更新分类 → 缓存失效
```
PUT /admin/categories/{id} {"name":"改名分类"}
GET /api/v1/categories?limit=5
预期: 分类名已更新, 日志: "categories cache cleared" → "cache miss"
```

### 1.5 Admin 删除分类 → 缓存失效
```
DELETE /admin/categories/{id}  (需先清空该分类下的文章)
GET /api/v1/categories?limit=5
预期: 分类已消失, 日志: "categories cache cleared" → "cache miss"
```

---

## 场景 2：tags 缓存 — Admin 标签 CRUD

### 2.1 首次访问 → cache miss
```
GET /api/v1/tags?limit=10
预期日志: "cache miss: tags limit=10"
```

### 2.2 二次访问 → cache hit
```
GET /api/v1/tags?limit=10
预期日志: "cache hit: tags limit=10"
```

### 2.3 Admin 创建标签 → 缓存失效
```
POST /admin/tags {"name":"测试标签","slug":"test-tag"}
GET /api/v1/tags?limit=10
预期日志: "tags cache cleared" → "cache miss"
```

### 2.4 Admin 更新标签 → 缓存失效
```
PUT /admin/tags/{id} {"name":"改名标签"}
GET /api/v1/tags?limit=10
预期日志: "tags cache cleared" → "cache miss"
```

### 2.5 Admin 删除标签 → 缓存失效
```
DELETE /admin/tags/{id}
GET /api/v1/tags?limit=10
预期日志: "tags cache cleared" → "cache miss"
```

---

## 场景 3：series 缓存 — Admin 专栏 CRUD

### 3.1 首次访问 → cache miss
```
GET /api/v1/series?limit=5
预期日志: "cache miss: series limit=5"
```

### 3.2 二次访问 → cache hit
```
GET /api/v1/series?limit=5
预期日志: "cache hit: series limit=5"
```

### 3.3 Admin 创建专栏 → 缓存失效
```
POST /admin/series {"title":"测试专栏","slug":"test-series"}
GET /api/v1/series?limit=5
预期日志: "series cache cleared" → "cache miss"
```

### 3.4 Admin 关联文章 → 缓存失效
```
POST /admin/series/{id}/posts {"postIds":[1,2,3]}
GET /api/v1/series?limit=5
预期日志: "series cache cleared" → "cache miss"
```

### 3.5 Admin 移除文章 → 缓存失效
```
DELETE /admin/series/{id}/posts/{postId}
GET /api/v1/series?limit=5
预期日志: "series cache cleared" → "cache miss"
```

---

## 场景 4：hot_posts 缓存

### 4.1 首次访问 → cache miss
```
GET /api/v1/posts/hot?limit=5
预期日志: "cache miss: hot_posts limit=5"
```

### 4.2 二次访问 → cache hit
```
GET /api/v1/posts/hot?limit=5
预期日志: "cache hit: hot_posts limit=5"
```

---

## 场景 5：文章创建 → 跨模块通知 4 个缓存全部失效

这是最关键的场景——验证 Spring Event 跨模块通知是否正确工作。

### 前置条件
- 先预热 categories/tags/series/hot_posts 缓存（各调一次公开接口）
- 确认 4 个缓存都处于 hit 状态

### 执行
```
POST /author/posts {
  "title":"缓存测试文章",
  "slug":"cache-test-post",
  "categoryId":1,
  "tagIds":[1,2],
  "seriesId":1,
  "body":"测试内容",
  "status":1
}
```

### 验证
```
GET /api/v1/categories → 预期 cache miss（post_count +1）
GET /api/v1/tags → 预期 cache miss（选中的标签 post_count +1）
GET /api/v1/series → 预期 cache miss（该专栏 post_count +1）
GET /api/v1/posts/hot → 预期 cache miss（新文章出现）

日志验证: PostsService 发布了 4 个 CacheRefreshEvent
         4 个 CacheService 的 @EventListener 各收到一次
```

---

## 场景 6：文章删除 → 跨模块通知

### 执行
```
DELETE /author/posts/{新建文章的id}
```

### 验证
```
GET /api/v1/categories → cache miss（post_count -1）
GET /api/v1/tags → cache miss
GET /api/v1/series → cache miss
GET /api/v1/posts/hot → cache miss（文章消失）
```

---

## 场景 7：文章状态变更（Admin 直接操作 Mapper）

### 执行
```
PUT /admin/posts/{id}/status {"status":0}  (发布→草稿)
```

### 验证
```
GET /api/v1/posts/hot → cache miss（草稿不应出现在热榜）
GET /api/v1/categories → cache miss（post_count -1，状态=1的文章才被COUNT）
```

---

## 场景 8：浏览/点赞 → 不清 hot_posts

### 执行
```
POST /api/v1/posts/{id}/view
POST /api/v1/posts/{id}/like (Header: X-Visitor-Id: test-visitor-123)
```

### 验证
```
GET /api/v1/posts/hot → 预期 cache HIT（不受view/like影响）
```

---

## 场景 9：Admin 批量操作 → 缓存失效

### 执行
```
POST /admin/posts/batch {"ids":[1,2],"action":"PUBLISH"}
```

### 验证
```
GET /api/v1/categories → cache miss
GET /api/v1/tags → cache miss
GET /api/v1/series → cache miss
GET /api/v1/posts/hot → cache miss
```

---

## 场景 10：site_config 回归验证

确认 Phase 1 的 site_config 缓存不受 Phase 2 改动影响：

### 执行
```
GET /api/v1/config → cache miss → cache hit
PUT /admin/configs {"registration_enabled":"false"}
GET /api/v1/config → cache miss, registrationEnabled=false
```

---

## 场景 11：边界情况

### 11.1 不同 limit 参数独立缓存
```
GET /api/v1/categories?limit=5 → cache miss limit=5
GET /api/v1/categories?limit=10 → cache miss limit=10 (不同的key)
GET /api/v1/categories?limit=5 → cache hit limit=5
```

### 11.2 TTL 过期
```
等待 10 分钟 → GET /api/v1/categories → cache miss (自动过期)
```

### 11.3 空列表缓存
```
数据库无数据时 → GET /api/v1/categories → 返回空列表, cache miss → 空列表被缓存
再次调用 → cache hit, 返回空列表
```

---

## 测试通过标准

| 检查项 | 标准 |
|--------|------|
| 所有 cache miss 日志正确输出 | 首次访问/缓存失效后首次 |
| 所有 cache hit 日志正确输出 | 重复访问 |
| 所有 cache cleared/invalidated 日志正确输出 | 写操作后 |
| 跨模块事件正确触发 | PostsService 日志 + CacheService @EventListener 日志 |
| 数据一致性 | 缓存返回数据与 DB 一致 |
| 编译通过 | 零错误 |
| site_config 不受影响 | Phase 1 功能正常 |
