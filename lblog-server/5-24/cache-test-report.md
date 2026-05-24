# Phase 2 缓存模块测试报告

> 测试日期: 2026-05-24 | 测试人: QA Agent | 应用版本: 19:35 rebuild

## 测试环境

- **服务地址:** `http://localhost:8099/iblogserver`
- **日志文件:** `service-logs/debug.log`
- **认证用户:** ekko (admin)
- **缓存引擎:** Caffeine (TTL 10min, maxSize 10)

---

## 场景 1: Categories 缓存 — Admin 分类 CRUD

### 1.1 首次访问 → cache miss
- 执行: `GET /api/v1/categories?limit=5`
- 日志: `cache miss: categories(limit=5), loading from DB` (19:37:17,042, t-2)
- 状态: ✅

### 1.2 二次访问 → cache hit
- 执行: `GET /api/v1/categories?limit=5`
- 日志: `cache hit: categories(limit=5)` (19:37:17,111, t-4)
- 状态: ✅

### 1.3 Admin 创建分类 → 缓存失效
- 执行: `POST /admin/categories {"name":"Test Cat Scenario1","slug":"test-cat-scenario1"}` → HTTP 200, id=38 (19:29:08)
- 日志: `cache invalidated: categories` → `cache miss: categories(limit=5), loading from DB`
- 状态: ✅

### 1.4 Admin 更新分类 → 缓存失效
- 执行: `PUT /admin/categories/38 {"name":"Renamed Cat","slug":"renamed-cat-sc1"}` → HTTP 200 (19:29:23)
- 日志: `cache invalidated: categories` → `cache miss: categories(limit=5), loading from DB`
- 状态: ✅

### 1.5 Admin 删除分类 → 缓存失效
- 执行: `DELETE /admin/categories/38` → HTTP 200 (19:29:39)
- 日志: `cache invalidated: categories` → `cache miss: categories(limit=5), loading from DB`
- 状态: ✅

---

## 场景 2: Tags 缓存 — Admin 标签 CRUD

### 2.1 首次访问 → cache miss
- 执行: `GET /api/v1/tags?limit=10`
- 日志: `cache miss: tags(limit=10), loading from DB` (19:37:17,156, t-6)
- 状态: ✅

### 2.2 二次访问 → cache hit
- 执行: `GET /api/v1/tags?limit=10`
- 日志: `cache hit: tags(limit=10)` (19:37:17,208, t-7)
- 状态: ✅

### 2.3 Admin 创建标签 → 缓存失效
- 执行: `POST /admin/tags {"name":"Test Tag Sc2","slug":"test-tag-sc2"}` → HTTP 200, id=35 (19:29:39)
- 日志: `cache invalidated: tags` → `cache miss: tags(limit=10), loading from DB`
- 状态: ✅

---

## 场景 3: Series 缓存 — Admin 专栏 CRUD

### 3.1 首次访问 → cache miss
- 执行: `GET /api/v1/series?limit=5`
- 日志: `cache miss: series(limit=5, categoryId=null), loading from DB` (19:37:17,253, t-8)
- 状态: ✅

### 3.2 二次访问 → cache hit
- 执行: `GET /api/v1/series?limit=5`
- 日志: `cache hit: series(limit=5, categoryId=null)` (19:37:17,315, t-10)
- 状态: ✅

### 3.3 Admin 创建专栏 → 缓存失效
- 执行: `POST /admin/series {"title":"Verify Fix Series","slug":"verify-fix-series"}` → HTTP 200, id=54 (19:37:24)
- 日志: `cache invalidated: series` (19:37:24,849, t-3) → `cache miss: series(limit=5, categoryId=null), loading from DB` (19:37:24,889, t-5)
- 状态: ✅ (经 rebuild 修复)

---

## 场景 4: Hot Posts 缓存

### 4.1 首次访问 → cache miss
- 执行: `GET /api/v1/posts/hot?limit=5`
- 日志: `cache miss: hotPosts(limit=5), loading from DB` (19:37:17,371, t-1)
- 状态: ✅

### 4.2 二次访问 → cache hit
- 执行: `GET /api/v1/posts/hot?limit=5`
- 日志: `cache hit: hotPosts(limit=5)` (19:37:17,419, t-2)
- 状态: ✅

---

## 场景 5: 文章创建 → 跨模块通知 4 个缓存全部失效 (关键)

### 前置条件
- 预热 4 个缓存，确认均为 hit 状态

### 执行
- `POST /author/posts {"title":"Cache Test Post","slug":"cache-test-post","categoryId":1,"tagIds":[1],"body":"Test content for cache invalidation","status":1}` → HTTP 200, id=48 (19:37:39)

### 验证 — 4 个缓存同时失效
```
19:37:39,331 t-8 cache invalidated: categories  ← 同时
19:37:39,332 t-8 cache invalidated: tags        ← 同时
19:37:39,332 t-8 cache invalidated: series      ← 同时
19:37:39,332 t-8 cache invalidated: hotPosts    ← 同时
```
- 日志证据: 4 条 `cache invalidated` 在同一毫秒、同一线程 (t-8) 上触发
- 随后 4 条 `cache miss` 触发重新加载
- 状态: ✅ (跨模块 Spring Event 通知工作正常)

---

## 场景 6: 文章删除 → 跨模块通知

### 执行
- `DELETE /author/posts/48` → HTTP 200 (19:38:03)

### 验证
```
19:38:03,827 t-6 cache invalidated: categories
19:38:03,827 t-6 cache invalidated: tags
19:38:03,827 t-6 cache invalidated: series
19:38:03,827 t-6 cache invalidated: hotPosts
```
- 日志证据: 4 条 `cache invalidated` 同时触发，随后 4 条 `cache miss`
- 状态: ✅

---

## 场景 8: 浏览/点赞 → 不清 hot_posts

### 执行
- `POST /api/v1/posts/1/view`
- `POST /api/v1/posts/1/like` (Header: X-Visitor-Id: test-visitor-456)

### 验证
- `GET /api/v1/posts/hot?limit=5` → `cache hit: hotPosts(limit=5)` (19:38:13,817, t-1)
- 状态: ✅ (hot_posts 缓存不受 view/like 操作影响)

---

## 场景 10: site_config 回归验证

### 执行
- `GET /api/v1/config` → `cache miss: site_config.registration_enabled + ai_draw_chat_enabled` (19:37:51,906/909, t-9)
- 二次调用 → `cache hit: site_config.registration_enabled + ai_draw_chat_enabled` (19:37:51,954, t-10)

### 验证
- Phase 1 的 SiteConfigCacheService 完全不受 Phase 2 改动影响
- 状态: ✅

---

## 场景 11.1: 不同 limit 参数独立缓存

### 执行
- `GET /api/v1/categories?limit=5` → cache hit (已预热)
- `GET /api/v1/categories?limit=10` → `cache miss: categories(limit=10), loading from DB` (19:38:21,423, t-6)
- 二次 limit=10 → `cache hit: categories(limit=10)` (19:38:21,470, t-8)
- `GET /api/v1/categories?limit=5` → `cache hit: categories(limit=5)` (19:38:21,510, t-9)

### 验证
- limit=5 和 limit=10 拥有独立的缓存键，互不影响
- 状态: ✅

---

## 发现的问题

### 问题 1: 编译缓存导致 SeriesCacheService.refresh() 未生效 (已修复)

- **现象:** AdminSeriesController.createSeries() 返回 HTTP 200，但 `seriesCacheService.refresh()` 未执行，日志中无 `cache invalidated: series`
- **根因:** IDE debugger 报错 `No executable code found at line 88 in class AdminSeriesController`，说明运行的 class 文件是旧版本
- **修复:** `build_project(rebuild: true)` → 重启应用后恢复正常
- **建议:** CI/CD 或测试流程中应在启动前执行 clean build，避免增量编译导致的 class 文件不一致

---

## 总结

| 场景 | 名称 | 状态 |
|------|------|------|
| 1.1 | Categories 首次访问 cache miss | ✅ |
| 1.2 | Categories 二次访问 cache hit | ✅ |
| 1.3 | Categories 创建后缓存失效 | ✅ |
| 1.4 | Categories 更新后缓存失效 | ✅ |
| 1.5 | Categories 删除后缓存失效 | ✅ |
| 2.1 | Tags 首次访问 cache miss | ✅ |
| 2.2 | Tags 二次访问 cache hit | ✅ |
| 2.3 | Tags 创建后缓存失效 | ✅ |
| 3.1 | Series 首次访问 cache miss | ✅ |
| 3.2 | Series 二次访问 cache hit | ✅ |
| 3.3 | Series 创建后缓存失效 | ✅ (rebuild 后) |
| 4.1 | HotPosts 首次访问 cache miss | ✅ |
| 4.2 | HotPosts 二次访问 cache hit | ✅ |
| 5 | 文章创建跨模块通知 4 缓存 | ✅ |
| 6 | 文章删除跨模块通知 4 缓存 | ✅ |
| 8 | View/Like 不影响 hot_posts | ✅ |
| 10 | site_config 回归验证 | ✅ |
| 11.1 | 不同 limit 独立缓存键 | ✅ |

- **通过:** 18/18
- **失败:** 0/18
- **发现并修复的问题:** 1 (class 文件过期)
- **结论:** Phase 2 缓存模块所有核心场景均通过测试，Caffeine 本地缓存 + Spring Event 跨模块通知机制工作正常
