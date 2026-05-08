# 作者统计接口文档

> 创作中心 · 个人数据统计
> 接口路径：`GET /api/v1/author/statistics`

---

## 接口说明

展示当前作者的个人统计数据（文章数、浏览量、点赞数、评论数、分类分布、月度发文趋势）。

| 项目 | 说明 |
|------|------|
| 请求方式 | GET |
| 认证方式 | Bearer Token（author/admin 角色） |
| 请求参数 | 无 |
| 响应格式 | `ApiResponse<AuthorStatisticsVO>` |
| 事务要求 | 四条查询在同一个事务内执行 |

---

## 权限校验

- `401` — 未登录或无 Token
- `403` — 角色为 `user`（普通用户无创作中心权限）
- `200` — 正常响应（即使文章数为 0 也返回 200，指标为 0 或空数组）

---

## 响应结构

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "totalPosts": 12,
    "totalViews": 3456,
    "totalLikes": 234,
    "totalComments": 89,
    "statusDistribution": [
      { "status": 0, "count": 3 },
      { "status": 1, "count": 8 },
      { "status": 2, "count": 1 }
    ],
    "categoryDistribution": [
      { "categoryName": "JavaScript", "categorySlug": "javascript", "postCount": 5 },
      { "categoryName": "Python",     "categorySlug": "python",     "postCount": 3 },
      { "categoryName": "DevOps",     "categorySlug": "devops",     "postCount": 2 }
    ],
    "monthlyTrend": [
      { "month": "2025-06", "count": 2 },
      { "month": "2025-07", "count": 0 },
      { "month": "2025-08", "count": 5 },
      { "month": "2025-09", "count": 3 },
      { "month": "2025-10", "count": 0 },
      { "month": "2025-11", "count": 1 },
      { "month": "2025-12", "count": 0 },
      { "month": "2026-01", "count": 4 },
      { "month": "2026-02", "count": 0 },
      { "month": "2026-03", "count": 6 },
      { "month": "2026-04", "count": 2 },
      { "month": "2026-05", "count": 1 }
    ]
  }
}
```

---

## 字段说明

### data 顶层字段

| 字段 | 类型 | 说明 |
|------|------|------|
| totalPosts | number | 文章总数（包含草稿/已发布/私密全部状态） |
| totalViews | number | 所有文章的 `viewCount` 之和 |
| totalLikes | number | 所有文章的 `likeCount` 之和 |
| totalComments | number | 所有文章的 `commentCount` 之和 |
| statusDistribution | StatusItem[] | 按状态分组的文章数 |
| categoryDistribution | CategoryItem[] | 按分类分组的文章数，按 postCount **降序** |
| monthlyTrend | MonthItem[] | 近 12 个月每月发文数（含 0 的月份） |

### StatusItem

| 字段 | 类型 | 说明 |
|------|------|------|
| status | number | 0=草稿 1=已发布 2=私密 |
| count | number | 该状态的文章数 |

### CategoryItem

| 字段 | 类型 | 说明 |
|------|------|------|
| categoryName | string | 分类名称。未分类文章归为 `"未分类"` |
| categorySlug | string | 分类标识。未分类时为 `""` |
| postCount | number | 该分类下的文章数 |

### MonthItem

| 字段 | 类型 | 说明 |
|------|------|------|
| month | string | 格式 `"YYYY-MM"`，如 `"2026-05"` |
| count | number | 该月创建的文章数，无文章时为 0 |

---

## 示例场景

### 场景一：有新作者

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "totalPosts": 0,
    "totalViews": 0,
    "totalLikes": 0,
    "totalComments": 0,
    "statusDistribution": [],
    "categoryDistribution": [],
    "monthlyTrend": [
      { "month": "2026-05", "count": 0 }
    ]
  }
}
```

### 场景二：文章很多，分类分布只返回有文章的

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "totalPosts": 156,
    "totalViews": 89234,
    "totalLikes": 4521,
    "totalComments": 1892,
    "statusDistribution": [
      { "status": 1, "count": 120 },
      { "status": 0, "count": 30 },
      { "status": 2, "count": 6 }
    ],
    "categoryDistribution": [
      { "categoryName": "JavaScript", "categorySlug": "javascript", "postCount": 45 },
      { "categoryName": "Python",     "categorySlug": "python",     "postCount": 38 },
      { "categoryName": "DevOps",     "categorySlug": "devops",     "postCount": 22 },
      { "categoryName": "Go",         "categorySlug": "go",         "postCount": 18 },
      { "categoryName": "未分类",       "categorySlug": "",          "postCount": 33 }
    ],
    "monthlyTrend": [
      { "month": "2025-06", "count": 8 },
      { "month": "2025-07", "count": 12 },
      { "month": "2025-08", "count": 15 },
      { "month": "2025-09", "count": 10 },
      { "month": "2025-10", "count": 6 },
      { "month": "2025-11", "count": 14 },
      { "month": "2025-12", "count": 9 },
      { "month": "2026-01", "count": 20 },
      { "month": "2026-02", "count": 18 },
      { "month": "2026-03", "count": 22 },
      { "month": "2026-04", "count": 16 },
      { "month": "2026-05", "count": 6 }
    ]
  }
}
```

---

## SQL 参考

基于 `posts` 表已有字段（`author_id`, `viewCount`, `likeCount`, `commentCount`, `status`, `category_id`, `created_at`），四条查询：

### 1. 汇总指标

```sql
SELECT
  COUNT(*)             AS totalPosts,
  COALESCE(SUM(viewCount), 0)    AS totalViews,
  COALESCE(SUM(likeCount), 0)    AS totalLikes,
  COALESCE(SUM(commentCount), 0) AS totalComments
FROM posts
WHERE author_id = ?;
```

### 2. 按状态分组

```sql
SELECT status, COUNT(*) AS count
FROM posts
WHERE author_id = ?
GROUP BY status;
```

### 3. 按分类分组

```sql
SELECT
  COALESCE(c.name, '未分类')  AS categoryName,
  COALESCE(c.slug, '')       AS categorySlug,
  COUNT(p.id)                 AS postCount
FROM posts p
LEFT JOIN categories c ON c.id = p.category_id
WHERE p.author_id = ?
GROUP BY p.category_id
ORDER BY postCount DESC;
```

### 4. 按月聚合 + 补足近 12 个月

```sql
-- 4a. 查询实际数据
SELECT
  DATE_FORMAT(p.created_at, '%Y-%m') AS month,
  COUNT(*) AS count
FROM posts p
WHERE p.author_id = ?
  AND p.created_at >= DATE_SUB(NOW(), INTERVAL 12 MONTH)
GROUP BY month
ORDER BY month;

-- 4b. 在 Java 层补足无数据的月份
-- 逻辑：生成近 12 个月的 "YYYY-MM" 列表，与查询结果 merge
-- 缺失的月份 count = 0，保证返回数组固定 12 条
```

> **索引建议：** `posts` 表需要 `(author_id, created_at)` 复合索引，覆盖 1、2、4 三条查询。分类查询走 `categories` 表主键即可。

---

## 前端对接

前端新增 API 函数：

```typescript
// api.ts

export interface AuthorStatistics {
  totalPosts: number;
  totalViews: number;
  totalLikes: number;
  totalComments: number;
  statusDistribution: Array<{ status: number; count: number }>;
  categoryDistribution: Array<{ categoryName: string; categorySlug: string; postCount: number }>;
  monthlyTrend: Array<{ month: string; count: number }>;
}

export async function getAuthorStatistics(): Promise<ApiResponse<AuthorStatistics>> {
  return request<AuthorStatistics>('/api/v1/author/statistics');
}
```

---

## 错误码

| code | message | 说明 |
|------|---------|------|
| 0 | success | 成功 |
| 401 | 未登录 | 无 Token 或已过期 |
| 403 | 无权限 | 当前角色无权访问 |
