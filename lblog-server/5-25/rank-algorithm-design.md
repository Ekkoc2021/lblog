# 文章排序算法重设计 — 设计文档

> 状态：设计中 | 日期：2026-05-25

## 一、现状分析

### 当前算法

| 排序 | SQL | 问题 |
|------|-----|------|
| 推荐 | `ORDER BY p.like_count DESC, p.published_at DESC` | 纯点赞降序，老文章永久霸榜，推荐列表僵化 |
| 最热 | `ORDER BY p.view_count DESC, p.published_at DESC` | 纯浏览量降序，同样是老文章占优，无法反映短期热度 |
| 最新 | `ORDER BY p.published_at DESC` | 无问题 |
| 专栏顺序 | `ORDER BY COALESCE(sp.sort_order, 999999) ASC` | 无问题 |

### 效果演示

| 文章 | likes | views | comments | 年龄 | 当前推荐排名 |
|------|-------|-------|----------|------|-------------|
| 老文 A | 100 | 5000 | 10 | 1000 天 | **#1** |
| 新文 B | 20 | 200 | 5 | 5 天 | #2 |

新文 B 质量明显不错（5 天就有 20 赞 5 评论），但永远排在 3 年前的老文后面。**推荐和最热本质上都是"谁活得久谁赢"。**

## 二、设计方案

### 2.1 核心公式：时间衰减加权评分

```
Score = Σ(signal × weight) / (age_days + base)^decay
```

- **signal**: 互动信号（like_count, comment_count, view_count）
- **weight**: 信号权重
- **age_days**: 距今天数（`DATEDIFF(NOW(), published_at)`）
- **base**: 年龄下限（防新文章除零，调"起飞窗口"大小）
- **decay**: 衰减指数（越大越激进，老文章沉得越快）

### 2.2 推荐算法（Recommend）

**目标**: 内容发现，质量优先，适度偏好新鲜内容

```sql
Score = (p.like_count * ${w.like}
      + p.comment_count * ${w.comment}
      + p.view_count * ${w.view})
      / POWER(GREATEST(DATEDIFF(NOW(), p.published_at), 0) + ${base}, ${exponent})
```

默认参数：

| 参数 | 值 | 说明 |
|------|-----|------|
| w.like | 2.0 | 点赞权重 |
| w.comment | 3.0 | 评论权重（互动深度最高） |
| w.view | 0.05 | 浏览权重（仅作辅助信号） |
| base | 2 天 | 前 2 天是"起飞窗口"，分母最小 |
| exponent | 1.2 | 温和衰减，2 周内仍有竞争力 |

### 2.3 最热算法（Hot）

**目标**: 近期流行，强调时效和流量

```sql
Score = (p.view_count * ${w.view}
      + p.like_count * ${w.like}
      + p.comment_count * ${w.comment})
      / POWER(GREATEST(DATEDIFF(NOW(), p.published_at), 0) + ${base}, ${exponent})
```

默认参数：

| 参数 | 值 | 说明 |
|------|-----|------|
| w.view | 0.1 | 浏览量权重（热度核心） |
| w.like | 1.0 | 点赞权重 |
| w.comment | 2.0 | 评论权重 |
| base | 1 天 | 起飞窗口更短 |
| exponent | 1.5 | 激进衰减，7 天后快速下沉 |

### 2.4 最新 & 专栏顺序

保持不变：`published_at DESC` 和 `sort_order ASC + published_at DESC`。

## 三、可配置化设计

### 3.1 site_config 键名规范

```
rank.<sort>.<param>
```

### 3.2 完整配置列表

| config_key | 默认值 | 类型 | 说明 |
|------------|--------|------|------|
| `rank.recommend.weight.like` | 2.0 | float | 推荐-点赞权重 |
| `rank.recommend.weight.comment` | 3.0 | float | 推荐-评论权重 |
| `rank.recommend.weight.view` | 0.05 | float | 推荐-浏览权重 |
| `rank.recommend.decay.base` | 2 | int | 推荐-年龄下限（天） |
| `rank.recommend.decay.exponent` | 1.2 | float | 推荐-衰减指数 |
| `rank.hot.weight.view` | 0.1 | float | 最热-浏览权重 |
| `rank.hot.weight.like` | 1.0 | float | 最热-点赞权重 |
| `rank.hot.weight.comment` | 2.0 | float | 最热-评论权重 |
| `rank.hot.decay.base` | 1 | int | 最热-年龄下限（天） |
| `rank.hot.decay.exponent` | 1.5 | float | 最热-衰减指数 |

共 10 个配置键，每个值都是简单的数字字符串（如 `"2.0"`），存在 `site_config.config_value`（varchar 500）。

## 四、架构设计

### 4.1 数据流

```
AdminConfigController           SiteConfigCacheService
   (更新配置)                       (Caffeine 30min)
       │                                │
       ▼                                ▼
  site_config 表 ←── RankConfigService ──→ RankConfig
                          │               (权重/衰减参数 POJO)
                          ▼
                   PostsServiceImpl
                          │
                          ▼
                   PostsMapper.selectPostList(sort, ..., rankConfig)
                          │
                          ▼
                   ORDER BY 公式(#{wLike}, #{wComment}, ...)
```

### 4.2 新增文件

| # | 文件 | 说明 |
|---|------|------|
| 1 | `blog/service/RankConfigService.java` | 从 site_config 加载 + 缓存排序参数 |
| 2 | `blog/service/RankConfig.java` | 参数 POJO（内部类或独立 DTO） |

### 4.3 修改文件

| # | 文件 | 变更 |
|---|------|------|
| 3 | `PostsMapper.java` | `selectPostList` 方法增加 `@Param("rankConfig") RankConfig` 参数；或拆成 wLike/wComment/wView/decayBase/decayExponent 5 个独立参数 |
| 4 | `PostsMapper.xml` | recommend/hot 的 ORDER BY 改为公式，newest/series 不变 |
| 5 | `PostsServiceImpl.java` | 注入 `RankConfigService`，调用 `selectPostList` 前加载配置并传入 |
| 6 | `v1.1.sql` | INSERT 10 条默认配置 |

## 五、实现细节

### 5.1 RankConfigService

```java
@Service
public class RankConfigService {

    private final SiteConfigCacheService configCache;

    public RankConfig getRecommendConfig() {
        return new RankConfig(
            getDouble("rank.recommend.weight.like", 2.0),
            getDouble("rank.recommend.weight.comment", 3.0),
            getDouble("rank.recommend.weight.view", 0.05),
            getInt("rank.recommend.decay.base", 2),
            getDouble("rank.recommend.decay.exponent", 1.2)
        );
    }

    public RankConfig getHotConfig() {
        return new RankConfig(
            getDouble("rank.hot.weight.like", 1.0),
            getDouble("rank.hot.weight.comment", 2.0),
            getDouble("rank.hot.weight.view", 0.1),
            getInt("rank.hot.decay.base", 1),
            getDouble("rank.hot.decay.exponent", 1.5)
        );
    }

    private double getDouble(String key, double defaultVal) {
        String val = configCache.getConfigValue(key);
        if (val == null || val.isEmpty()) return defaultVal;
        try { return Double.parseDouble(val.trim()); }
        catch (NumberFormatException e) { return defaultVal; }
    }

    private int getInt(String key, int defaultVal) { /* 同上 */ }
}
```

### 5.2 PostsMapper.java 参数传递

**推荐做法：传递 5 个独立参数**，避免 MyBatis 嵌套属性访问：

```java
List<Posts> selectPostList(@Param("sort") String sort,
                            @Param("categoryId") Long categoryId,
                            @Param("tagId") Long tagId,
                            @Param("seriesId") Long seriesId,
                            @Param("keyword") String keyword,
                            @Param("wLike") Double wLike,
                            @Param("wComment") Double wComment,
                            @Param("wView") Double wView,
                            @Param("decayBase") Integer decayBase,
                            @Param("decayExponent") Double decayExponent);
```

> 如果不在乎传 null（newest/series 场景），可接受 null 值。Mapper 里对未使用的排序不会引用这些参数。

### 5.3 PostsMapper.xml — ORDER BY 改造

```xml
<choose>
    <when test="sort == 'newest'">
        ORDER BY p.published_at DESC
    </when>
    <when test="sort == 'hot'">
        ORDER BY (
            p.view_count * #{wView} +
            p.like_count * #{wLike} +
            p.comment_count * #{wComment}
        ) / POWER(GREATEST(DATEDIFF(NOW(), p.published_at), 0) + #{decayBase}, #{decayExponent}) DESC,
        p.published_at DESC
    </when>
    <when test="sort == 'series'">
        ORDER BY COALESCE(sp.sort_order, 999999) ASC, p.published_at DESC
    </when>
    <otherwise>
        <!-- recommend -->
        ORDER BY (
            p.like_count * #{wLike} +
            p.comment_count * #{wComment} +
            p.view_count * #{wView}
        ) / POWER(GREATEST(DATEDIFF(NOW(), p.published_at), 0) + #{decayBase}, #{decayExponent}) DESC,
        p.published_at DESC
    </otherwise>
</choose>
```

`GREATEST(..., 0)` 防止 future 日期出现负数。

### 5.4 PostsServiceImpl 改动

```java
RankConfig rankConfig;
if ("hot".equals(sort)) {
    rankConfig = rankConfigService.getHotConfig();
} else {
    rankConfig = rankConfigService.getRecommendConfig(); // 默认 + recommend
}

List<Posts> posts = postsMapper.selectPostList(sort, categoryId, tagId,
    seriesId, keyword,
    rankConfig.getWeightLike(), rankConfig.getWeightComment(),
    rankConfig.getWeightView(), rankConfig.getDecayBase(),
    rankConfig.getDecayExponent());
```

## 六、效果验证

用当前数据库实测（选取部分文章模拟）：

| post | likes | views | comments | 年龄(天) | 当前 recommend 排名 | 新算法评分 | 新排名 |
|------|-------|-------|----------|----------|-------------------|-----------|--------|
| post 1 | 23 | 4345 | 1 | ~30 | #1 (likes=23) | ~1.1 | #2 |
| post 10 | 45 | 1345 | 1 | ~15 | #2 (likes=45) | ~5.7 | #1 |

> post 10 虽然总赞数高，但年龄适中，评分超过 post 1。说明算法能正确平衡"质量"和"时效"。

## 七、实现难度评估

| 组件 | 难度 | 原因 |
|------|------|------|
| `RankConfig` POJO | 低 | 简单数据类，5 个字段 |
| `RankConfigService` | 低 | 读取 site_config，解析数字，有默认值兜底 |
| `PostsMapper.xml` SQL | 中 | ORDER BY 公式复杂但无需新语法，MySQL 8 原生支持 |
| `PostsMapper.java` 参数 | 低 | 加 5 个新参数 |
| `PostsServiceImpl` | 低 | 注入 service，if/else 选配置 |
| v1.1.sql 初始化数据 | 低 | 10 条 INSERT |
| 后端编译验证 | 低 | IDEA build |
| 测试验证 | 中 | 需要不同年龄/互动的文章对比排序结果 |

**总评：中等偏低。** 核心工作是一次 SQL 重构 + 10 行配置读取。没有新数据库、新中间件、前后端接口变更。

## 八、已知局限

1. **Filesort 无法避免**：计算列不能走索引。但 WHERE 先过滤出符合条件的文章（通常 <1000 篇），每页只取 10 条，性能足够。
2. **配置热更新有延迟**：Caffeine 缓存 30 分钟 TTL，改配置后最多 30 分钟生效。如需即时生效可调用 `refreshCache`。
3. **DATEDIFF 精度为天**：同一天发布的文章 age 相同，依靠二级排序 `published_at DESC` 区分。
4. **不适用超大规模**：如果文章量达 10 万级，应改为定期预计算评分写入 `posts` 表加索引。当前博客场景无需。

## 九、实施检查清单

- [ ] `RankConfig.java` — POJO（wLike, wComment, wView, decayBase, decayExponent）
- [ ] `RankConfigService.java` — 从 SiteConfigCacheService 加载配置
- [ ] `PostsMapper.java` — selectPostList 加 5 个新参数
- [ ] `PostsMapper.xml` — recommend/hot ORDER BY 改为公式
- [ ] `PostsServiceImpl.java` — 注入 RankConfigService，传入参数
- [ ] `v1.1.sql` — INSERT 10 条默认配置
- [ ] 后端编译通过
- [ ] 按测试要点逐条验证
