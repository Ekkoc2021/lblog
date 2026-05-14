# lblog 项目性能优化全面审计

## 一、优化维度总览

性能优化可以从以下 **10 个维度** 入手：

| 维度 | 当前状态 | 影响面 |
|------|----------|--------|
| 线程池/异步化 | 零异步基础设施 | 所有请求路径 |
| 缓存 | 只有安全限流用了 Caffeine | 所有读操作 |
| 数据库索引 | DDL 不在仓库，疑似缺索引 | 所有查询 |
| SQL 优化 | 多处 LIKE '%..%' 全表扫描 | 搜索/筛选 |
| 连接池配置 | 部分参数偏保守 | 高并发场景 |
| 日志优化 | 同步写盘 + DEBUG 级别 | 所有请求 |
| 压缩/序列化 | 无 Gzip，无 Jackson 调优 | 响应体积 |
| 前端数据加载 | 每次页面都重新请求 5 个 API | 首屏/导航 |
| N+1 查询 | 多处循环内查库 | 列表/详情 |
| 计数/热点数据 | 浏览量同步写库 | 高流量文章 |

---

## 二、各维度详细分析

### 维度 1：线程池/异步化

详见 `5-13/thread-pool-optimization.md`

**现状**: 无 `@EnableAsync`、无 `@Async`、无 `CompletableFuture`，所有操作在 Tomcat 线程上同步执行。

**优化方向**:
- 新建 `AsyncConfig`，定义 IO/CPU 两个线程池
- 统计接口、文章列表/详情独立查询并行化
- N+1 查询改为批量 + 并行
- 浏览/点赞计数异步化

---

### 维度 2：缓存

**现状**: `spring-boot-starter-cache` 已引入但 **未启用**（无 `@EnableCaching`）。Caffeine 只在 `LoginAttemptServiceImpl` 和 `RegisterProtectionService` 中手动创建，用于安全限流。

**适合缓存的数据**（变更频率低，访问频率高）：

| 数据 | 接口 | 变更频率 | 建议 TTL |
|------|------|----------|----------|
| 分类列表 | `/api/v1/categories` | 周/月级 | 5 min |
| 标签列表 | `/api/v1/tags` | 周/月级 | 5 min |
| 专栏列表 | `/api/v1/series` | 周/月级 | 5 min |
| 热门文章 | `/api/v1/posts/hot` | 小时级 | 1 min |
| 站点配置 | `/api/v1/config` | 月级 | 10 min |
| 文章详情 | `/api/v1/posts/{slug}` | 小时级 | 缓存直到内容变更时主动失效 |
| 站点统计 | `/api/v1/admin/statistics` | 小时级 | 1 min |
| 作者统计 | `/api/v1/author/statistics` | 小时级 | 1 min |

**实施方案**:
```java
// LblogServerApplication.java 加 @EnableCaching

// 分类/标签/专栏/配置 → Spring Cache 注解
@Cacheable(value = "categories", unless = "#result.isEmpty()")
public List<CategoryVO> getCategories(int limit) { ... }

// 文章详情 → 主动失效（创建/更新文章时 @CacheEvict）
@Cacheable(value = "posts", key = "#slug")
public PostDetailVO getPostBySlug(String slug) { ... }

@CacheEvict(value = "posts", key = "#req.slug")
public void updatePost(Long id, UpdatePostRequest req) { ... }
```

---

### 维度 3：数据库索引

**现状**: 仓库中只有 `user_tokens` 和 `images` 相关表的 DDL，核心表（posts、post_contents、categories、tags、post_tags、series、series_posts、comments）DDL 不在仓库中。

**根据查询分析需要确认/补充的索引**:

| 表 | 建议索引 | 对应查询 |
|----|----------|----------|
| `posts` | `(slug, status, is_delelte, deleted_at)` | selectBySlug |
| `posts` | `(published_at, status, is_delelte, deleted_at)` | 公开文章列表排序 |
| `posts` | `(view_count, published_at, status, is_delelte, deleted_at)` | 热门文章 |
| `posts` | `(author_id, is_delelte, deleted_at)` | 作者统计/管理列表筛选 |
| `posts` | `(created_at, author_id, is_delelte, deleted_at)` | 月度趋势 |
| `post_tags` | `(tag_id, post_id)` | 按标签筛选 |
| `post_tags` | `(post_id, tag_id)` | 批量查标签关系 |
| `series_posts` | `(series_id, sort_order)` | 上下篇查询 |
| `like_records` | `UNIQUE (post_id, visitor_id)` | 点赞去重 |
| `comments` | `(post_id, parent_id, status, created_at)` | 评论列表 |
| `comments` | `(created_at, status)` | 管理端评论列表 |

**关键查询 EXPLAIN 建议**: 运行以下 SQL 查看执行计划
```sql
EXPLAIN SELECT * FROM posts WHERE slug = 'xxx' AND status = 1 AND is_delelte = 0 AND deleted_at IS NULL;
EXPLAIN SELECT ... FROM posts WHERE is_delelte = 0 AND deleted_at IS NULL ORDER BY published_at DESC LIMIT 20;
EXPLAIN SELECT ... FROM posts WHERE is_delelte = 0 AND deleted_at IS NULL ORDER BY view_count DESC LIMIT 10;
```

---

### 维度 4：SQL 优化

#### 4.1 LIKE '%keyword%' 全表扫描

3 处使用前后模糊匹配，无法走 B-Tree 索引：

| 文件 | 位置 | SQL 片段 |
|------|------|----------|
| `CommentsMapper.xml` | line 101 | `c.content LIKE CONCAT('%', #{keyword}, '%')` |
| `UsersMapper.xml` | lines 94-96 | `username/nickname/email LIKE '%keyword%'` |
| `ImagesMapper.xml` | line 100 | `original_name LIKE CONCAT('%', #{keyword}, '%')` |

**优化方案**:

- **短期**: 确保搜索字段上有普通索引，虽然 `%keyword%` 不能走索引查找，但 InnoDB 全索引扫描比全表扫描快
- **中期**: 改用 MySQL 全文索引 `FULLTEXT(content)` + `MATCH ... AGAINST`
- **长期**: 集成 Elasticsearch 做全文搜索

#### 4.2 selectCleanupCandidates 无 LIMIT

`ImagesMapper.xml` lines 163-169，清理候选图片查询无 `LIMIT`，积累多时可能内存溢出。

**修复**: 加 `LIMIT 1000`

#### 4.3 聚合查询无索引

`selectStatistics` 对全表做 `SUM(view_count)`, `SUM(like_count)`, `SUM(comment_count)`，无可用的覆盖索引。

**优化**: 建覆盖索引 `(is_delelte, deleted_at, view_count, like_count, comment_count)`

#### 4.4 DATE_FORMAT 阻止索引使用

`selectMonthlyTrend` 用 `DATE_FORMAT(p.created_at, '%Y-%m')`，无法利用 `created_at` 索引。

**现状影响**: 已有 `DATE_SUB(NOW(), INTERVAL 12 MONTH)` 范围过滤，扫描量可控

#### 4.5 unlikePost 多余的 selectById

`PostsServiceImpl.java:166` 在 decrement 后重新查整个 post 对象，只为获取 like_count。

**优化**: 用 `UPDATE posts SET like_count = GREATEST(like_count - 1, 0) WHERE id = #{id}` 然后返回受影响行数

---

### 维度 5：Druid 连接池配置

**文件**: `application.yml`

| 参数 | 当前值 | 建议值 | 原因 |
|------|--------|--------|------|
| `max-wait` | 60000ms | 10000ms | 60s 太长，线程堆积会雪崩 |
| `slowSqlMillis` | 5000 | 1000 | 5s 对博客太宽松 |
| `statViewServlet.enabled` | false | true + IP 白名单 | 方便排查慢查询 |

当前 `max-active: 20` 对博客规模够用，`initial-size: 5` / `min-idle: 5` 合理。

---

### 维度 6：日志优化

**文件**: `log4j2.xml`

**问题**:
1. **所有 Appender 同步写盘** — 每条日志阻塞业务线程
2. **Root level = debug** — 生产环境写入海量日志
3. **druid.sql.Statement = debug** — 每条 SQL 都记日志，拖慢查询

**优化方案**:

```xml
<!-- 使用 AsyncLogger（LMAX Disruptor）全局异步 -->
<AsyncLogger name="druid.sql.Statement" level="info"/>

<!-- 或在 Appender 外套 <Async> -->
<Async name="asyncAllFile">
    <AppenderRef ref="allFileAppender"/>
</Async>
```

生产环境配置:
```xml
<Root level="info">  <!-- debug → info -->
    <AppenderRef ref="asyncConsole"/>
    <AppenderRef ref="asyncErrorFile"/>  <!-- 只保留必要的 -->
</Root>
```

或使用 Spring profile 区分 `log4j2-dev.xml` / `log4j2-prod.xml`。

---

### 维度 7：压缩与序列化

#### 7.1 Gzip 压缩

**现状**: 未启用。API 返回 JSON 无压缩。

**优化**: `application.yml` 添加
```yaml
server:
  compression:
    enabled: true
    mime-types: text/html,text/xml,text/plain,text/css,text/javascript,application/json
    min-response-size: 1024
```

文章详情包含完整的 Markdown body（几十 KB），压缩后体积减少 70-80%。

#### 7.2 Jackson 配置

**现状**: 使用 Spring Boot 默认 Jackson 配置，无定制。

**优化**:
```yaml
spring:
  jackson:
    default-property-inclusion: non_null      # 去掉 null 字段，减小响应体积
    date-format: yyyy-MM-dd HH:mm:ss
    serialization:
      write-dates-as-timestamps: false
```

#### 7.3 静态资源缓存头

**文件**: `WebConfig.java:15-18`，上传图片路径无缓存头。

**优化**:
```java
registry.addResourceHandler("/uploads/**")
        .addResourceLocations("file:" + uploadDir + "/")
        .setCacheControl(CacheControl.maxAge(365, TimeUnit.DAYS).cachePublic());
```

---

### 维度 8：前端数据加载

#### 8.1 SiteDataContext 每次页面加载请求 5 个 API

**文件**: `lblog-web/src/contexts/SiteDataContext.tsx:26-31`

```typescript
Promise.all([
  getCategories(10), getTags(20), getSeries(5), getHotPosts(5), getSiteConfig()
])
```

每次 `SiteDataProvider` 重新挂载（比如从公开页面 → admin → 公开页面），5 个 API 全部重拉。

**优化方案**:
- **后端合并**: 新增 `/api/v1/site-data` 一次性返回分类/标签/专栏/热门/配置，减少 4 次 HTTP 往返
- **前端缓存**: `sessionStorage` + TTL（如 sessionStorage 内 5 分钟有效）
- **React Query**: 使用 `@tanstack/react-query` 的 `staleTime` 机制

#### 8.2 文章列表返回字段过多

每个 `PostVO` 包含 `author` 对象、`category` 对象、`tags` 数组，而列表页可能只需要标题/摘要/时间。

**优化**: 考虑后端支持 `fields` 参数做字段级过滤，或列表接口返回精简 VO。

#### 8.3 浏览量上报每次都发 POST

浏览文章时 `api.reportView(slug)` 触发同步 SQL UPDATE。

**优化**: 后端 `@Async` 异步化（已在维度 1 中覆盖），或前端改为每 5 分钟批量上报。

---

### 维度 9：N+1 查询

已在 `5-13/thread-pool-optimization.md` 中详细列出，核心包括：

| 位置 | N+1 模式 | 优化方式 |
|------|----------|----------|
| `AdminImageController:76` | 循环内查 usage | 批量查询 + 分组 |
| `AdminUserController:276` | 嵌套 N+1（用户→角色→权限） | 批量查询 |
| `ImageUsageAspect:77` | 循环内查图片 | 并行化 |
| `AdminPostController:103` | 循环内查+改 | 并行化 + 批量 SQL |
| `SeriesServiceImpl:105` | N 次单条 UPDATE | CASE WHEN 批量 |

---

### 维度 10：计数/热点数据处理

**现状**: 浏览量和点赞数直接实时写 MySQL。

| 操作 | 调用方 | SQL | 问题 |
|------|--------|-----|------|
| reportView | 每次页面浏览 | `UPDATE posts SET view_count = view_count + 1` | 行锁竞争 |
| likePost | 每次点赞 | `INSERT like_records` + `UPDATE posts SET like_count = like_count + 1` | 非原子，需要两步 |
| unlikePost | 每次取消 | `DELETE like_records` + `UPDATE posts SET like_count = like_count - 1` | 之后还 SELECT 全文 |

**优化路径**（逐步升级）:
1. **立即**: `@Async` 异步化释放请求线程
2. **中期**: 内存缓冲 + 定时刷库（`ConcurrentHashMap<postId, delta>` + `@Scheduled` 每 30s 刷一次）
3. **长期**: 引入 Redis（`INCR` / `HINCRBY`），定时同步到 MySQL

---

## 三、优化优先级矩阵

按 **收益 × 实施成本** 排序：

| 优先级 | 优化项 | 维度 | 预期收益 | 实施成本 |
|--------|--------|------|----------|----------|
| P0 | 启用 Gzip 压缩 | 7 | API 响应体积 -70% | 1 行配置 |
| P0 | 加 `@EnableCaching` + 缓存分类/标签/专栏/配置 | 2 | 5 个 API 延迟趋近内存 | 半小时 |
| P0 | 统计接口并行化 | 1 | 统计 API 延迟 -60% | 半小时 |
| P1 | 文章列表/详情并行化 | 1 | 核心 API 延迟 -40% | 1 小时 |
| P1 | 降低 slowSqlMillis + max-wait | 5 | 慢查询发现 + 防雪崩 | 改配置 |
| P1 | 浏览/点赞计数异步化 | 1 | 释放请求线程 | 改注解 |
| P1 | Jackson non_null + 日期格式 | 7 | 响应体积 -10% | 配置 |
| P2 | N+1 消除（图片列表/专栏排序/批量操作） | 9 | 管理端 API 延迟降低 | 2-3 小时 |
| P2 | 生产环境 Root log level → info | 6 | 磁盘 I/O 大幅降低 | 配置 |
| P2 | 补全数据库索引 | 3 | 查询从全表扫描变索引 | 需 DBA 确认 |
| P2 | 前端 SiteDataContext 缓存或合并接口 | 8 | 5 次 HTTP → 1 次 | 前后端联动 |
| P3 | LIKE '%keyword%' → 全文索引 | 4 | 搜索性能提升 | 改 SQL + 索引 |
| P3 | Log4j2 AsyncLogger | 6 | 日志不阻塞业务线程 | 改配置 |
| P3 | unlikePost 去除多余 SELECT | 4 | 减少一次查询 | 1 行代码 |
| P3 | selectCleanupCandidates 加 LIMIT | 4 | 防止内存溢出 | 1 行 SQL |
| P4 | 浏览量内存缓冲 + 定时刷库 | 10 | 消除热点行锁 | 中等 |
| P4 | 引入 Redis 做热点数据 | 2/10 | 架构升级 | 高 |
| P4 | Elasticsearch 全文搜索 | 4 | 搜索能力质变 | 高 |

---

## 四、落地建议

**第一步（本周）** — 零代码/一行配置的速赢项:
1. `application.yml` 加 Gzip
2. `application.yml` 加 Jackson non_null
3. `application.yml` 调 Druid `max-wait: 10000`、`slowSqlMillis: 1000`

**第二步（本周）** — 线程池 + 缓存:
4. 按 `thread-pool-optimization.md` 实施异步化和并行化
5. 启用 `@EnableCaching`，缓存分类/标签/专栏/配置/热门文章

**第三步（下周）** — 查询优化:
6. 补充数据库索引（先 EXPLAIN 验证再建）
7. N+1 消除（批量 SQL 代替循环）

**第四步（按需）** — 架构升级:
8. Redis 做计数缓存
9. Elasticsearch 做全文搜索
10. React Query 做前端数据缓存

前三步完成后，核心 API 延迟预计降低 **50-70%**（Gzip 压缩 + 查询并行化 + 缓存）。
