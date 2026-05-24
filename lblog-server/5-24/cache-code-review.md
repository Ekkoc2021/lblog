# Phase 2 缓存模块代码审查

## 审查概要
- 审查文件: 12
- 问题数: 8
- 严重: 0 / 中等: 3 / 轻微: 5

---

## 问题列表

### [中等] reportView() 不刷新热门文章缓存
- 文件: PostsServiceImpl.java:156-158
- 描述: `reportView()` 调用 `postsMapper.incrementViewCount(id)` 增加浏览量，而 `selectHotPosts` SQL 按 `view_count DESC` 排序。浏览量变化直接影响热门文章排名，但 `reportView()` 没有发布 `CacheRefreshEvent(HOT_POSTS)`。热门缓存 TTL 5 分钟，意味着浏览量变化后最多 5 分钟才能反映到首页。
- 建议: 在 `reportView()` 末尾添加 `eventPublisher.publishEvent(new CacheRefreshEvent(CacheNames.HOT_POSTS));`

### [中等] AdminPostController 双重缓存失效
- 文件: AdminPostController.java:93-94, PostsServiceImpl.java:501
- 描述: `AdminPostController.updatePost()` 先调用 `postsService.updatePost()`（内部发布 4 个 CacheRefreshEvent），然后又调用 `refreshAllCaches()`（直接刷新 4 个缓存）。每个写操作导致每个缓存被失效 2 次。batchPosts DELETE 场景更严重：N 篇文章各产生 4 个事件，末尾再加 4 次直接刷新，总共 4N+4 次无效操作。虽然 `invalidateAll()` 幂等不引起正确性问题，但暴露出缓存失效职责不清晰：PostsService 通过事件机制、AdminPostController 又直接调用 refresh。
- 建议: 统一失效策略。方案 A：AdminPostController 移除 `refreshAllCaches()`，完全依赖 PostsService 的事件机制（但 batchPosts PUBLISH/DRAFT 绕过了 PostsService，需要补齐）。方案 B：移除 PostsService 中的事件发布，全部由 Controller 层负责直接 refresh。

### [中等] Mapper 的 limit 参数未在 SQL 中使用
- 文件: CategoriesMapper.xml:36-45, TagsMapper.xml:30-39, SeriesMapper.xml:89-104
- 描述: 三个 Mapper 接口都声明了 `limit` 参数（`CategoriesMapper.selectCategoriesWithCount(limit, createdBy)` 等），对应 Java 代码传入 `limit` 作为缓存 key，但 XML SQL 中均无 `LIMIT #{limit}` 子句。导致：
  - DB 始终返回全量数据，与 `limit` 参数语义不符
  - 不同 limit 值（5, 10, 20）产生不同缓存 key，但缓存值完全相同（全量数据），浪费缓存空间
  - HomeController 的 `subList(0, limit)` 截断掩盖了此问题
- 建议: 在三个 Mapper XML 的 SQL 末尾添加 `<if test="limit != null">LIMIT #{limit}</if>`。添加后 HomeController 的 subList 截断可移除。

### [轻微] PostsServiceImpl.getHotPosts() 已成为死代码
- 文件: PostsServiceImpl.java:118-120, PostsService.java:18
- 描述: `PostsServiceImpl.getHotPosts()` 和接口声明 `PostsService.getHotPosts()` 现在无任何调用方。HomeController 已改用 `HotPostsCacheService.getHotPosts()`。该方法保留在 PostsService 中会造成混淆。
- 建议: 移除 `PostsService.getHotPosts()` 接口方法和 `PostsServiceImpl.getHotPosts()` 实现。

### [轻微] 缓存服务未对 Mapper 返回值做空判断
- 文件: CategoriesCacheService.java:41-42, TagsCacheService.java:41-42, SeriesCacheService.java:42-43, HotPostsCacheService.java:41-42
- 描述: 4 个缓存服务的 miss 路径直接 `cache.put(key, list)` 不检查 `list` 是否为 null。虽然 MyBatis 通常返回空列表而非 null，但防御性编码缺失。若 mapper 因异常返回 null，后续 `catList.size()` (HomeController:65) 会 NPE。
- 建议: 在 put 前添加 null 检查：`if (list == null) list = Collections.emptyList();`

### [轻微] 日志格式与 Phase 1 不一致
- 文件: CategoriesCacheService.java:36, TagsCacheService.java:36, SeriesCacheService.java:37, HotPostsCacheService.java:36 (与 SiteConfigCacheService.java:31 对比)
- 描述: Phase 1 的 SiteConfigCacheService 使用点号格式 `"cache hit: site_config.{}"`，而 Phase 2 的 4 个缓存服务使用括号格式 `"cache hit: categories(limit={})"`。格式不一致会影响日志聚合查询。
- 建议: 统一为一种格式，推荐 `"cache hit: <cacheName> key=<key>"` 作为后续标准。

### [轻微] batchPosts PUBLISH/DRAFT 绕过 PostsService 直接操作 Mapper
- 文件: AdminPostController.java:136-142
- 描述: `batchPosts` 中 PUBLISH/DRAFT 分支直接调用 `postsMapper.updatePost(update)`，绕过了 `PostsServiceImpl.updatePost()`。这意味着批量发布/转草稿不会触发 PostsService 中的事件发布，虽然末尾有 `refreshAllCaches()` 兜底。这种双重失效路径增加了维护成本——未来若有人在 PostsService 中添加新的缓存逻辑，batchPosts 不会受益。
- 建议: 在 batchPosts 的 PUBLISH/DRAFT 分支中也调用 `postsService.updatePost(id, request)` 而非直接操作 Mapper。如果出于性能考虑需要批量更新，至少封装一个公共的 `publishCacheRefreshEvents()` 可复用方法。

### [轻微] AdminSeriesController.linkPosts/reorderPosts 不刷新 CATEGORIES/TAGS/HOT_POSTS
- 文件: AdminSeriesController.java:116-135
- 描述: `linkPosts()` 和 `reorderPosts()` 修改了专栏文章关联但只调用 `seriesCacheService.refresh()`。专栏文章变化会影响文章列表（文章列表包含系列关联信息），通常应同时刷新 HOT_POSTS（文章热度可能受影响）和 SERIES 缓存。但 PostsServiceImpl 在这些方法被调用后不会自动触发事件——因为 SeriesService 直接操作 Mapper，不会经过 PostsService。
- 建议: 确认业务需求。如果专栏内文章变化不影响首页热门文章，当前做法正确。否则应在这些端点也刷新相关缓存。

---

## 逐文件审查

### CategoriesCacheService.java
- ✅ 结构清晰，与 TagsCacheService 模式完全一致
- ✅ `@EventListener` 正确过滤 `CacheNames.CATEGORIES`
- ⚠️ Mapper 返回值未做 null 检查（见 [轻微] null 判断）
- ⚠️ `selectCategoriesWithCount(limit, null)` 的 limit 未在 SQL 中使用（见 [中等] limit 参数）

### TagsCacheService.java
- ✅ 与 CategoriesCacheService 模式完全一致
- ✅ 日志格式与同 Phase 的其他服务一致
- ⚠️ Mapper 返回值未做 null 检查
- ⚠️ `selectTagsWithCount(limit, null)` 的 limit 未在 SQL 中使用

### SeriesCacheService.java
- ✅ 复合 key 设计合理（`limit + "_" + categoryId`），区别于简单的单参数缓存
- ✅ categoryId 为 null 时 key 包含 "null" 字符串不会冲突
- ⚠️ Mapper 返回值未做 null 检查
- ⚠️ `selectSeriesWithCount(limit, categoryId, null)` 的 limit 未在 SQL 中使用

### HotPostsCacheService.java
- ✅ TTL 5 分钟短于其他缓存的 10 分钟，体现了"热度数据变化更快"的设计意图
- ✅ `maximumSize(5)` 合理——limit 取值范围小（默认 5，max 100），缓存条目少
- ⚠️ `reportView()` 未触发此缓存的刷新（见 [中等] reportView 不刷新）
- ⚠️ Mapper 返回值未做 null 检查

### CacheRefreshEvent.java
- ✅ 简洁的 Java record，语义清晰
- ✅ 单一字段 `cacheName` 精确表达失效目标

### CacheNames.java
- ✅ 常量命名一致，全大写蛇形
- ✅ 4 个新常量与 4 个缓存服务一一对应
- ✅ 与 CacheRefreshEvent 监听器的过滤条件匹配

### PostsServiceImpl.java
- ✅ `publishCacheRefreshEvents()` 方法封装了 4 个事件的发布
- ✅ `createPost()`、`updatePost()`、`deletePost()` 三个写操作均调用了 `publishCacheRefreshEvents()`
- ❌ `reportView()` 未调用 `publishCacheRefreshEvents()` 或发布 HOT_POSTS 事件（见 [中等] reportView）
- ❌ `getHotPosts()` 方法已成为死代码（见 [轻微] 死代码）
- ⚠️ `likePost()`/`unlikePost()` 未刷新缓存——取决于 `selectHotPosts` 是否按 like_count 排序。当前 SQL 只按 `view_count DESC`，所以不需要。但若后续加 like 权重，需注意

### HomeController.java
- ✅ 正确替换为 4 个 CacheService 调用
- ✅ 构造函数注入清晰
- ⚠️ `subList(0, limit)` 截断逻辑是 Mapper 缺少 LIMIT 的 workaround（见 [中等] limit 参数）
- ✅ 热门文章端点无截断逻辑，因为 `selectHotPosts` SQL 有 `LIMIT #{limit}`

### AdminCategoryController.java
- ✅ create/update/delete 三个写操作后均调用 `categoriesCacheService.refresh()`
- ✅ 读取操作（`getAdminCategories`）不使用缓存，直接调用 CategoriesService（会经过 PageHelper 分页，缓存无意义）

### AdminTagController.java
- ✅ create/update/delete 三个写操作后均调用 `tagsCacheService.refresh()`
- ✅ 模式与 AdminCategoryController 完全一致

### AdminSeriesController.java
- ✅ 6 个写操作（create/update/delete/linkPosts/reorderPosts/removePost）均调用 `seriesCacheService.refresh()`
- ✅ 覆盖了专栏的所有变更入口
- ⚠️ linkPosts/reorderPosts 只刷新 series 缓存（见 [轻微] 专栏关联）

### AdminPostController.java
- ✅ `refreshAllCaches()` 封装了 4 个缓存的一键刷新
- ⚠️ 双重失效问题（见 [中等] 双重失效）
- ⚠️ batchPosts PUBLISH/DRAFT 绕过 PostsService（见 [轻微] 绕过 Service）

### SiteConfigCacheService.java (Phase 1 参考)
- ✅ 作为 Phase 1 参考实现，模式清晰
- ℹ️ 与 Phase 2 的区别：Phase 1 没有事件机制，缓存失效由调用方直接调用 `refreshCache(key)`。Phase 2 引入了 `CacheRefreshEvent` 事件机制作为补充（Controller 直接调用 + Service 事件发布双重路径）

---

## 总结

Phase 2 缓存模块整体实现质量良好。4 个缓存服务遵循统一的代码模式，事件驱动的失效机制设计合理。5 分钟 TTL 对热门文章的差异化配置体现了对数据特性的理解。

核心问题 3 个：
1. **reportView 不刷新热门缓存**——浏览量变化直接影响热门排名，缓存应在浏览量变化时即时失效
2. **AdminPostController 双重失效**——Controller 和 Service 层各自维护一套失效逻辑，职责不清
3. **Mapper limit 参数未生效**——虽然这是落实现有的问题，但缓存层放大了其影响（同一数据多份缓存）

建议优先修复问题 1（功能正确性），问题 2 和 3 可在后续迭代中处理。
