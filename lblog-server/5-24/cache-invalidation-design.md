# 缓存失效策略完整设计

> 状态：设计阶段 | 日期：2026-05-24

## 1. 缓存范围

本次涉及 5 个缓存空间：

| 缓存 | TTL | 数据 | 被哪些公开接口使用 |
|------|-----|------|-------------------|
| site_config | 30 min | KV 配置 | `GET /config` |
| categories | 10 min | 分类列表（含 post_count） | `GET /categories` |
| tags | 10 min | 标签列表（含 post_count） | `GET /tags` |
| series | 10 min | 专栏列表（含 post_count） | `GET /series` |
| hot_posts | 5 min | 热门文章排行 | `GET /posts/hot` |

---

## 2. 完整写操作 → 缓存失效映射

以下穷举项目中**所有后台写接口**，逐一定义需要清哪些缓存。

### 2.1 Admin 分类管理 — AdminCategoryController

| 接口 | 方法 | 影响 | 清 categories | 清 tags | 清 series | 清 hot_posts | 清 site_config |
|------|------|------|:--:|:--:|:--:|:--:|:--:|
| `/admin/categories` | POST 创建 | 新分类出现 | ✅ | — | — | — | — |
| `/admin/categories/{id}` | PUT 更新 | 名称/slug 变更 | ✅ | — | — | — | — |
| `/admin/categories/{id}` | DELETE 删除 | 条目消失 | ✅ | — | — | — | — |

### 2.2 Admin 标签管理 — AdminTagController

| 接口 | 方法 | 影响 | 清 categories | 清 tags | 清 series | 清 hot_posts | 清 site_config |
|------|------|------|:--:|:--:|:--:|:--:|:--:|
| `/admin/tags` | POST 创建 | 新标签出现 | — | ✅ | — | — | — |
| `/admin/tags/{id}` | PUT 更新 | 名称/slug 变更 | — | ✅ | — | — | — |
| `/admin/tags/{id}` | DELETE 删除 | 条目消失 | — | ✅ | — | — | — |

### 2.3 Admin 专栏管理 — AdminSeriesController

| 接口 | 方法 | 影响 | 清 categories | 清 tags | 清 series | 清 hot_posts | 清 site_config |
|------|------|------|:--:|:--:|:--:|:--:|:--:|
| `/admin/series` | POST 创建 | 新专栏出现 | — | — | ✅ | — | — |
| `/admin/series/{id}` | PUT 更新 | 标题/slug 变更 | — | — | ✅ | — | — |
| `/admin/series/{id}` | DELETE 删除 | 条目消失 | — | — | ✅ | — | — |
| `/admin/series/{id}/posts` | POST 关联文章 | post_count + 条目变更 | — | — | ✅ | — | — |
| `/admin/series/{id}/posts/sort` | PUT 排序 | 排序变化 | — | — | ✅ | — | — |
| `/admin/series/{id}/posts/{postId}` | DELETE 移除文章 | post_count -1 | — | — | ✅ | — | — |

### 2.4 Admin 文章管理 — AdminPostController

| 接口 | 方法 | 影响 | 清 categories | 清 tags | 清 series | 清 hot_posts | 清 site_config |
|------|------|------|:--:|:--:|:--:|:--:|:--:|
| `/admin/posts/{id}` | PUT 更新元数据 | 可能换分类/标签/专栏 | ✅ | ✅ | ✅ | ✅ | — |
| `/admin/posts/{id}/status` | PUT 改状态 | 草稿↔发布，post_count 变化 | ✅ | ✅ | ✅ | ✅ | — |
| `/admin/posts/batch` | POST 批量操作 | 批量发布/草稿/删除 | ✅ | ✅ | ✅ | ✅ | — |

### 2.5 Author 文章管理 — AuthorPostController

| 接口 | 方法 | 影响 | 清 categories | 清 tags | 清 series | 清 hot_posts | 清 site_config |
|------|------|------|:--:|:--:|:--:|:--:|:--:|
| `/author/posts` | POST 创建文章 | category/tag/series post_count +1 | ✅ | ✅ | ✅ | ✅ | — |
| `/author/posts/{id}` | PUT 更新文章 | 可能换分类/标签/专栏 | ✅ | ✅ | ✅ | ✅ | — |
| `/author/posts/{id}` | DELETE 删除文章 | category/tag/series post_count -1 | ✅ | ✅ | ✅ | ✅ | — |

### 2.6 Admin 站点配置 — AdminConfigController

| 接口 | 方法 | 影响 | 清 categories | 清 tags | 清 series | 清 hot_posts | 清 site_config |
|------|------|------|:--:|:--:|:--:|:--:|:--:|
| `/admin/configs` | PUT 批量更新 | 配置变更 | — | — | — | — | ✅ per key |
| `/admin/configs` | POST 添加 | 新配置 | — | — | — | — | ✅ per key |
| `/admin/configs` | DELETE 删除 | 配置消失 | — | — | — | — | ✅ per key |

### 2.7 站点配置（Author 级别）— ConfigController

| 接口 | 方法 | 影响 | 清 categories | 清 tags | 清 series | 清 hot_posts | 清 site_config |
|------|------|------|:--:|:--:|:--:|:--:|:--:|
| `/author/site-config/registration` | PUT 设置注册开关 | 配置变更 | — | — | — | — | ✅ per key |

### 2.8 前台高频写操作 — 不主动清

| 接口 | 频率 | 处理 |
|------|------|------|
| `POST /posts/{id}/view` 浏览上报 | 极高（每次阅读） | **不清**，hot_posts 5min TTL 兜底 |
| `POST /posts/{id}/like` 点赞 | 中 | **不清**，hot_posts 5min TTL 兜底 |
| `DELETE /posts/{id}/like` 取消点赞 | 中 | **不清**，hot_posts 5min TTL 兜底 |

### 2.9 评论管理 — 暂不涉及

| 接口 | 原因 |
|------|------|
| `POST /posts/{id}/comments` 发表评论 | 文章详情页的 comment_count 暂未加入缓存 |

---

## 3. 跨模块通知方案：Spring Event

### 3.1 问题

文章增删改（AdminPostController + AuthorPostController）需要清 categories/tags/series/hot_posts 四个缓存。如果直接注入，PostsService 会依赖 4 个 CacheService——严重耦合。

### 3.2 方案

```java
// common/event/CacheRefreshEvent.java
public record CacheRefreshEvent(String cacheName) {
    // cacheName 取 CacheNames 中的常量
}
```

```java
// PostsServiceImpl — 文章写操作后发事件
@Transactional
public Long createPost(CreatePostRequest req, Long authorId) {
    // ... 写入逻辑
    applicationEventPublisher.publishEvent(new CacheRefreshEvent(CacheNames.CATEGORIES));
    applicationEventPublisher.publishEvent(new CacheRefreshEvent(CacheNames.TAGS));
    applicationEventPublisher.publishEvent(new CacheRefreshEvent(CacheNames.SERIES));
    applicationEventPublisher.publishEvent(new CacheRefreshEvent(CacheNames.HOT_POSTS));
}
```

```java
// CategoriesCacheService — 监听并清自己
@EventListener
public void onCacheRefresh(CacheRefreshEvent event) {
    if (CacheNames.CATEGORIES.equals(event.cacheName())) {
        invalidateAll();
    }
}
```

PostsService 只知道 `CacheNames` 常量，不知道任何 CacheService。零耦合。

### 3.3 事件流

```
PostsService.createPost/updatePost/deletePost
    │
    ├──→ CacheRefreshEvent("categories") ──→ CategoriesCacheService.invalidateAll()
    ├──→ CacheRefreshEvent("tags")       ──→ TagsCacheService.invalidateAll()
    ├──→ CacheRefreshEvent("series")     ──→ SeriesCacheService.invalidateAll()
    └──→ CacheRefreshEvent("hot_posts")  ──→ HotPostsCacheService.invalidateAll()

AdminCategoryController.createCategory/updateCategory/deleteCategory
    │
    └──→ 直接调 categoriesCacheService.refresh()
          （同模块，无需事件）

AdminTagController → 直接调 tagsCacheService.refresh()
AdminSeriesController → 直接调 seriesCacheService.refresh()
AdminConfigController → 直接调 siteConfigCacheService.refreshCache(key)
```

规则：**同模块直接调，跨模块走事件。**

### 3.4 为什么 hot_posts 也要在文章写操作时清

| 场景 | 理由 |
|------|------|
| 发布新文章 | 新文章应该出现在热榜中（初始 view=0，但"最新"维度也会影响排序权重） |
| 删除文章 | 已删除文章不应出现在热榜中 |
| 状态变更（发布↔草稿） | 草稿不应出现在热榜中 |

5min TTL 是兜底，写操作主动清是正确性保证。

---

## 4. 文件清单（最终版）

### 新增文件

```
common/cache/event/
└── CacheRefreshEvent.java              ← Spring Event 记录

blog/service/
├── CategoriesCacheService.java         ← Caffeine 10min TTL
├── TagsCacheService.java               ← Caffeine 10min TTL
├── SeriesCacheService.java             ← Caffeine 10min TTL
└── HotPostsCacheService.java           ← Caffeine 5min TTL
```

### 修改文件

```
common/cache/constant/CacheNames.java   ← 新增 CATEGORIES/TAGS/SERIES/HOT_POSTS

blog/service/impl/PostsServiceImpl.java ← 增删改后发 CacheRefreshEvent

blog/controller/public_/HomeController.java  ← 注入换 CacheService

blog/controller/admin/AdminCategoryController.java  ← 增删改后调 refresh()
blog/controller/admin/AdminTagController.java       ← 增删改后调 refresh()
blog/controller/admin/AdminSeriesController.java    ← 增删改/关联后调 refresh()
blog/controller/admin/AdminPostController.java      ← 增删改后调 refresh()
blog/controller/author/AuthorPostController.java    ← 增删改后调 refresh()
```

共 5 个新文件 + 8 个修改文件。

---

## 5. 缓存键与失效方式汇总

| CacheService | Key 类型 | 失效方式 | 谁触发 |
|-------------|---------|---------|--------|
| SiteConfigCacheService | String config_key | `invalidate(key)` 单 key | ConfigController, AdminConfigController |
| CategoriesCacheService | Integer limit | `invalidateAll()` 全清 | AdminCategoryController 直接调 + Post Event 间接 |
| TagsCacheService | Integer limit | `invalidateAll()` 全清 | AdminTagController 直接调 + Post Event 间接 |
| SeriesCacheService | Integer limit | `invalidateAll()` 全清 | AdminSeriesController 直接调 + Post Event 间接 |
| HotPostsCacheService | Integer limit | `invalidateAll()` 全清 | Post Event 触发 + 5min TTL 兜底 |

---

## 6. 完整失效矩阵（一图总结）

```
                   ┌────────────┬──────┬──────┬────────┬───────────┬────────────┐
                   │ categories │ tags │series│hot_posts│site_config│ 触发方式    │
┌──────────────────┼────────────┼──────┼──────┼────────┼───────────┼────────────┤
│ AdminCat CRUD    │     ✅     │  —   │  —   │   —    │     —     │ 直接调用    │
│ AdminTag CRUD    │     —      │  ✅  │  —   │   —    │     —     │ 直接调用    │
│ AdminSeries CRUD │     —      │  —   │  ✅  │   —    │     —     │ 直接调用    │
│ AdminPost CRUD   │     ✅     │  ✅  │  ✅  │   ✅   │     —     │ Spring Evt  │
│ AuthorPost CRUD  │     ✅     │  ✅  │  ✅  │   ✅   │     —     │ Spring Evt  │
│ AdminConfig CRUD │     —      │  —   │  —   │   —    │     ✅    │ 直接调用    │
│ Config Registr.  │     —      │  —   │  —   │   —    │     ✅    │ 直接调用    │
│ Post view/like   │     —      │  —   │  —   │   —    │     —    │ TTL 兜底    │
└──────────────────┴────────────┴──────┴──────┴────────┴───────────┴────────────┘
```
