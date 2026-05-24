# 博客缓存模块设计文档

> 状态：Phase 1 已完成，Phase 2 设计中 | 日期：2026-05-24

## Phase 1 — siteConfig 缓存（已完成）

### 架构

```
Controller → XxxCacheService → Mapper
                │
                └── 内置 Caffeine Cache 实例
```

不走 Spring `@Cacheable` + `CacheManager`，每个 XxxCacheService 直接持有 Caffeine `Cache` 对象，三步流程写在方法里：`查缓存 → 未命中查 DB → 回填缓存`。

### 实现文件

| 文件 | 说明 |
|------|------|
| `common/cache/constant/CacheNames.java` | 缓存名常量 `SITE_CONFIG` |
| `site/service/SiteConfigCacheService.java` | 缓存中间层，Caffeine 30min TTL，50 条上限 |
| `site/controller/ConfigController.java` | [改] 注入换 CacheService，读走缓存 |
| `site/controller/AdminConfigController.java` | [改] 增删改后调 `refreshCache(key)` |

### 缓存策略

| 维度 | 选择 |
|------|------|
| 缓存粒度 | 按 key（`registration_enabled`、`ai_draw_chat_enabled`），逐 key 缓存 |
| 过期策略 | 写入后 30 分钟 |
| 失效时机 | Admin 增删改配置时 `invalidate(key)` + TTL 兜底 |
| 最大条目 | 50 |

### 验证结果

```
首次访问 → cache miss（查 DB）→ 二次访问 → cache hit（不走 DB）
Admin 更新 → cache invalidated → 下次访问 → cache miss（查 DB 拿新值）
未被更新的 key 不受影响，继续 hit
```

---

## Phase 2 — categories / tags / series / hotPosts 缓存（设计中）

### 核心问题

文章增删改会导致分类/标签/专栏的 `post_count` 变化。要不要跨模块通知缓存失效？

**决定：不跨模块通知。** post_count 不需要秒级精确，用 TTL 兜底。

### 缓存策略总览

| 缓存 | TTL | 主动失效时机 | TTL 兜底场景 |
|------|-----|-------------|-------------|
| site_config | 30min | Admin 配置增删改 → `invalidate(key)` | — |
| categories | 10min | Admin 分类增删改 → `invalidateAll()` | 文章增删改导致 post_count 变化 |
| tags | 10min | Admin 标签增删改 → `invalidateAll()` | 文章增删改导致 post_count 变化 |
| series | 10min | Admin 专栏增删改/关联文章 → `invalidateAll()` | 文章增删导致 post_count 变化 |
| hot_posts | 5min | 无 | 浏览/点赞/发布全部靠 TTL |

### 失效触发点详细分析

**categories：**

| # | 触发操作 | 位置 | 刷新方式 |
|---|---------|------|---------|
| 1 | Admin 创建分类 | AdminCategoryController | `refresh()` 全清 |
| 2 | Admin 更新分类 | AdminCategoryController | `refresh()` 全清 |
| 3 | Admin 删除分类 | AdminCategoryController | `refresh()` 全清 |
| 4 | 文章增删改 → post_count 变 | PostsService | 不通知，TTL 兜底 |

**tags：**

| # | 触发操作 | 位置 | 刷新方式 |
|---|---------|------|---------|
| 1 | Admin 创建标签 | AdminTagController | `refresh()` 全清 |
| 2 | Admin 更新标签 | AdminTagController | `refresh()` 全清 |
| 3 | Admin 删除标签 | AdminTagController | `refresh()` 全清 |
| 4 | 文章增删改 → post_count 变 | PostsService | 不通知，TTL 兜底 |

**series：**

| # | 触发操作 | 位置 | 刷新方式 |
|---|---------|------|---------|
| 1 | Admin 创建专栏 | AdminSeriesController | `refresh()` 全清 |
| 2 | Admin 更新专栏 | AdminSeriesController | `refresh()` 全清 |
| 3 | Admin 删除专栏 | AdminSeriesController | `refresh()` 全清 |
| 4 | Admin 关联文章到专栏 | AdminSeriesController | `refresh()` 全清 |
| 5 | Admin 从专栏移除文章 | AdminSeriesController | `refresh()` 全清 |
| 6 | 文章增删 → post_count 变 | PostsService | 不通知，TTL 兜底 |

**hot_posts：**

| 触发操作 | 处理方式 |
|---------|---------|
| 浏览上报（高频） | TTL 5min 兜底 |
| 点赞（中频） | TTL 兜底 |
| 新文章发布（低频） | TTL 兜底 |

### 实现文件

```
blog/service/
├── CategoriesCacheService.java    ← 新增，Key=limit，invalidateAll()
├── TagsCacheService.java          ← 新增，Key=limit，invalidateAll()
├── SeriesCacheService.java        ← 新增，Key=limit，invalidateAll()
└── HotPostsCacheService.java      ← 新增，Key=limit，纯 TTL，无主动刷新

common/cache/constant/
└── CacheNames.java                ← 改，新增常量

blog/controller/public_/
└── HomeController.java            ← 改，4 处注入换 CacheService

blog/controller/admin/
├── AdminCategoryController.java   ← 改，增删改后调 refresh()
├── AdminTagController.java        ← 改，增删改后调 refresh()
└── AdminSeriesController.java     ← 改，增删改/关联后调 refresh()
```

共 4 个新文件 + 5 个修改文件。不改 post 模块。

### 缓存键设计差异

| CacheService | Key 类型 | 失效方式 | 原因 |
|-------------|---------|---------|------|
| SiteConfigCacheService | `String` config_key | `invalidate(key)` 单 key | KV 配置，逐 key 精确失效 |
| CategoriesCacheService | `Integer` limit | `invalidateAll()` 全清 | 聚合列表，任一变更影响全局排序 |
| TagsCacheService | `Integer` limit | `invalidateAll()` 全清 | 同上 |
| SeriesCacheService | `Integer` limit | `invalidateAll()` 全清 | 同上 |
| HotPostsCacheService | `Integer` limit | 不主动清 | 变化太频繁，纯 TTL |

---

## 设计决策记录

1. **不走 Spring `@Cacheable`** — AOP 黑盒，显式编排更清晰
2. **不引入 CacheService 接口** — YAGNI，单机 Caffeine 够用，切 Redis 时再抽
3. **不使用 CacheManager** — 每个 CacheService 直接 new Caffeine Cache，无需全局管理
4. **不跨模块通知缓存失效** — 保持模块边界干净，post_count 滞后由 TTL 兜底
5. **categories/tags/series 用 invalidateAll** — 聚合列表缓存，无法精确定位受影响的 key
