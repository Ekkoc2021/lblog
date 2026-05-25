# 专栏顺序排序 — 设计文档

> 状态：设计中 | 日期：2026-05-25

## 一、背景

专栏页面（`/series/:slug`）的排序 Tab 直接复用了首页的「推荐 / 最新 / 最热」，没有「专栏顺序」。

`series_posts` 表已有 `sort_order` 字段，后端 `SeriesPostsMapper.selectPostsBySeriesId` 已按 `sort_order ASC` 排序。但前台通用文章列表接口 `GET /api/v1/posts` 不支持此排序。

## 二、需求

在专栏页面新增第 4 个排序 Tab「专栏顺序」，默认选中。文章按 `series_posts.sort_order ASC` 排列。

只在专栏页面出现此 Tab，首页、分类、标签、搜索页不变。

## 三、改动清单

| # | 文件 | 位置 | 改动 |
|---|------|------|------|
| 1 | `PostsMapper.xml` | `selectPostList` | FROM 后加条件 `LEFT JOIN`；`<choose>` 中新增 `sort == 'series'` ORDER BY 分支 |
| 2 | `PostsServiceImpl.java` | `getPostList` L66 | 白名单加 `"series"` |

前端改动：

| # | 文件 | 改动 |
|---|------|------|
| 3 | `SeriesPosts.tsx` L37 | `sort` 类型断言加 `'series'`（tab 和默认值已存在，无需新增） |
| 4 | `types/index.ts` | `getPosts` 参数类型 `sort` 加 `'series'`（如未包含） |

## 四、后端详细改动

### 4.1 `PostsMapper.xml` — `selectPostList`

**关键：JOIN 必须在 WHERE 之前，不能放在 `<choose>` 的 `<when>` 里。**

需要修改两处：

**A. FROM 子句 — 加条件 JOIN（第 52 行附近）：**

```xml
<!-- 修改前 -->
FROM posts p
<include refid="where_condition"/>

<!-- 修改后 -->
FROM posts p
<if test="sort == 'series' and seriesId != null">
    LEFT JOIN series_posts sp ON p.id = sp.post_id AND sp.series_id = #{seriesId}
</if>
<include refid="where_condition"/>
```

**B. `<choose>` 排序分支 — 新增 `series` when（第 54 行）：**

```xml
<choose>
    <when test="sort == 'newest'">
        ORDER BY p.published_at DESC
    </when>
    <when test="sort == 'hot'">
        ORDER BY p.view_count DESC, p.published_at DESC
    </when>
    <when test="sort == 'series'">
        ORDER BY COALESCE(sp.sort_order, 999999) ASC, p.published_at DESC
    </when>
    <otherwise>
        ORDER BY p.like_count DESC, p.published_at DESC
    </otherwise>
</choose>
```

**说明：**
- `<if test="sort == 'series' and seriesId != null">` — 只在需要专栏排序且传了 seriesId 时才 JOIN，不影响其他排序的性能
- `COALESCE(sp.sort_order, 999999)` — LEFT JOIN 无匹配时兜底排最后
- `p.published_at DESC` 作为二级排序（同 sort_order 时新的在前）
- `sp` 别名与 `where_condition` 子查询内的 `sp` 别名在不同作用域，不冲突

### 4.2 `PostsServiceImpl.java` — 白名单

L66：

```java
// 修改前
if (!Arrays.asList("recommend", "newest", "hot").contains(sort)) {

// 修改后
if (!Arrays.asList("recommend", "newest", "hot", "series").contains(sort)) {
```

## 五、前端改动

### 5.1 现状

`SeriesPosts.tsx` **已经存在**专栏顺序的 tab 和默认值（L16、L57），无需新增。只需修正类型：

**L37 类型断言：**

```tsx
// 修改前
sort: activeTab as 'recommend' | 'newest' | 'hot',

// 修改后
sort: activeTab as 'recommend' | 'newest' | 'hot' | 'series',
```

### 5.2 `types/index.ts`（如需要）

检查 `getPosts` 函数的参数类型定义，如果 `sort` 字段的类型为 `'recommend' | 'newest' | 'hot'`，追加 `| 'series'`。

## 六、测试要点

### 功能测试

| # | 场景 | 预期 |
|---|------|------|
| 1 | 进入专栏页面 | 默认选中「专栏顺序」，文章按 sort_order 升序排列 |
| 2 | 专栏顺序 → 切换到「最新」 | 按发布时间降序排列 |
| 3 | 专栏顺序 → 切换到「推荐」 | 按点赞数降序排列 |
| 4 | 「推荐」→ 切回「专栏顺序」 | 恢复按 sort_order 升序 |
| 5 | 专栏顺序 → 切换到「最热」 | 按浏览量降序排列 |
| 6 | 在首页 / 分类 / 标签 / 搜索页传 `sort=series` | backend 白名单拦截，降级为 recommend |

### 边界测试

| # | 场景 | 预期 |
|---|------|------|
| 7 | 未关联专栏的文章（LEFT JOIN 无匹配） | COALESCE 兜底排最后 |
| 8 | `sort=series` 但 `seriesId` 为 null | `<if>` 条件不满足，不 JOIN，降级排序 |
| 9 | 专栏下无文章 | 空列表，不报错 |
| 10 | 同 sort_order 的多篇文章 | 二级排序按 `published_at DESC` |
| 11 | 专栏只有 1 篇文章 | 正常显示 |

## 七、设计要点

### 为什么 JOIN 不在 `<choose>` 里

原始设计将 `LEFT JOIN` 写在 `<when test="sort == 'series'">` 内，这在 SQL 语法上不合法——JOIN 是 FROM 子句的一部分，必须先于 WHERE。MyBatis 的 `<choose>` 只适合在同一位置的条件内容中做互斥分支（如 ORDER BY），不能把 JOIN 语句塞进去。

正确做法是用 `<if>` 在 FROM 后条件性追加 JOIN 语句，用 `<choose>` 的 `<when>` 处理 ORDER BY 分支。

### 为什么用 `<if>` 而非无条件 JOIN

无条件 LEFT JOIN 会让所有查询（首页、分类、标签排序）都做一次无意义的 JOIN，影响性能。`<if>` 只在 `sort=series` 且有 `seriesId` 时才 JOIN。

## 八、实施检查清单

- [ ] `PostsMapper.xml` — FROM 后加 `<if>` 条件 JOIN + `<choose>` 加 `<when test="sort == 'series'">`
- [ ] `PostsServiceImpl.java` — 白名单加 `"series"`
- [ ] `SeriesPosts.tsx` L37 — 类型断言加 `'series'`
- [ ] `types/index.ts` — `getPosts` 参数 sort 类型加 `'series'`（如需要）
- [ ] 后端编译通过
- [ ] 前端 build 通过
- [ ] 按测试要点逐条验证
