# Phase 2 缓存模块复测场景

> 复测日期: 2026-05-24 | 复测原因: 修复代码审查问题后验证

## 本次修复内容

| # | 修复项 | 影响文件 |
|---|--------|---------|
| 1 | reportView() 刷新 hot_posts 缓存 | PostsServiceImpl.java |
| 2 | 4 个 CacheService 加 null 安全 | Categories/Tags/Series/HotPosts CacheService |
| 3 | 日志格式统一 | 同上 4 个 CacheService |

## 复测场景

### 场景 R1：浏览上报触发 hot_posts 缓存失效（修复验证）

**验证修复 #1**

前置条件：预热 hot_posts 缓存，确认 cache hit

步骤：
1. `GET /api/v1/posts/hot?limit=5` → 预期 cache hit
2. `POST /api/v1/posts/{id}/view` → 增加某文章浏览量
3. `GET /api/v1/posts/hot?limit=5` → 预期 cache miss（之前是 hit，修复后应为 miss）

验证点：
- 日志中 reportView 后出现 `cache invalidated: hotPosts`（来自 event listener）
- 下次 GET hot_posts 出现 `cache miss: hotPosts key=X`

---

### 场景 R2：空列表 null 安全（修复验证）

**验证修复 #2**

步骤：
1. 查询一个不存在的分类下的文章列表（确认该分类无文章）
2. `GET /api/v1/categories?limit=5` → 预期正常返回，不 NPE
3. 所有分类的 post_count 正常显示

验证点：无 NPE，空列表正常缓存

---

### 场景 R3：全量失效矩阵回归（修复后回归）

**验证所有 8 类写操作对应的缓存失效**

步骤：
1. 预热全部 5 个缓存
2. 逐类执行写操作，检查对应缓存是否失效：

| # | 写操作 | 应失效的缓存 | 
|---|--------|-------------|
| 1 | Admin 创建分类 | categories |
| 2 | Admin 更新分类 | categories |
| 3 | Admin 删除分类 | categories |
| 4 | Admin 创建标签 | tags |
| 5 | Admin 更新标签 | tags |
| 6 | Admin 删除标签 | tags |
| 7 | Admin 创建专栏 | series |
| 8 | Admin 更新专栏 | series |
| 9 | Admin 删除专栏 | series |
| 10 | Admin 关联文章到专栏 | series |
| 11 | Admin 调整专栏排序 | series |
| 12 | Admin 移除专栏文章 | series |
| 13 | Author 创建文章 | categories, tags, series, hot_posts |
| 14 | Author 更新文章 | categories, tags, series, hot_posts |
| 15 | Author 删除文章 | categories, tags, series, hot_posts |
| 16 | Admin 更新文章 | categories, tags, series, hot_posts |
| 17 | Admin 修改文章状态 | categories, tags, series, hot_posts |
| 18 | Admin 批量操作 | categories, tags, series, hot_posts |
| 19 | Admin 更新配置 | site_config |
| 20 | Admin 添加配置 | site_config |
| 21 | Admin 删除配置 | site_config |
| 22 | Config 设置注册开关 | site_config |

3. 每次操作后检查：该失效的缓存是否生效，不该失效的缓存是否未受影响

---

### 场景 R4：不应失效检查

**验证不该失效的缓存保持命中**

步骤：
1. 预热 tags 缓存（多次 GET /api/v1/tags）
2. Admin 创建分类（只应失效 categories）
3. `GET /api/v1/tags` → 预期 **cache hit**（tags 未被分类操作影响）
4. `GET /api/v1/series` → 预期 **cache hit**（series 未被分类操作影响）

同样验证：标签操作不影响分类，专栏操作不影响标签 等。

---

### 场景 R5：view/like 仅刷新 hot_posts（修复后精确验证）

**验证修复 #1 不影响其他缓存**

步骤：
1. 预热 categories 缓存 → cache hit
2. `POST /api/v1/posts/1/view`
3. `GET /api/v1/categories?limit=5` → 预期 **cache hit**（view 不应刷新 categories，只应刷新 hot_posts）
4. `GET /api/v1/posts/hot?limit=5` → 预期 **cache miss**（view 刷新了 hot_posts）

---

### 场景 R6：site_config 回归（Phase 1 不受影响）

**验证 Phase 1 功能完整**

步骤：
1. `GET /api/v1/config` → 预期首次 cache miss
2. `GET /api/v1/config` → 预期 cache hit  
3. Admin 更新 registration_enabled
4. `GET /api/v1/config` → 预期 cache miss（配置变更后失效）

---

## 测试通过标准

- R1～R6 全部通过
- 无 NPE
- 日志格式统一（cache miss/hit/invalidated 均使用 `key=` 格式）
- Phase 1 site_config 功能不受影响
