# 专栏文章管理 API

## 概述

为专栏提供文章列表查询、排序、移除功能。Author 和 Admin 角色均可使用，路径前缀不同：

| 角色 | 前缀 |
|------|------|
| Author | `/api/v1/author` |
| Admin | `/api/v1/admin` |

---

## 1. 获取专栏文章列表

```
GET /api/v1/author/series/{seriesId}/posts
GET /api/v1/admin/series/{seriesId}/posts
```

### 响应

```json
{
  "code": 0,
  "message": "success",
  "data": [
    {
      "postId": 10,
      "title": "MySQL 索引优化实战",
      "slug": "mysql-index-optimization",
      "sortOrder": 0
    },
    {
      "postId": 1,
      "title": "Spring Boot 3.0 新特性全面解析",
      "slug": "spring-boot-3-features",
      "sortOrder": 1
    },
    {
      "postId": 6,
      "title": "Element Plus 组件库深度使用指南",
      "slug": "element-plus-guide",
      "sortOrder": 2
    }
  ]
}
```

### 实现要点

- 新增 VO：`com.yang.lblogserver.blog.vo.SeriesPostVO`（字段：`postId`、`title`、`slug`、`sortOrder`）
- 新增 Mapper 方法：`SeriesPostsMapper.selectPostsBySeriesId(Long seriesId)`
- SQL：

```sql
SELECT p.id AS postId, p.title, p.slug, sp.sort_order AS sortOrder
FROM series_posts sp
JOIN posts p ON sp.post_id = p.id
WHERE sp.series_id = #{seriesId}
  AND p.is_delelte = 0 AND p.deleted_at IS NULL
ORDER BY sp.sort_order ASC
```

- 新增 Service 方法：`SeriesService.getPostsBySeriesId(Long seriesId) -> List<SeriesPostVO>`

---

## 2. 调整文章排序（已有）

```
PUT /api/v1/author/series/{seriesId}/posts/sort
PUT /api/v1/admin/series/{seriesId}/posts/sort
```

### 请求体

```json
{
  "postIds": [10, 1, 6]
}
```

数组中 postId 的顺序即最终排序，索引 0 → sortOrder=0，索引 1 → sortOrder=1，以此类推。

> **已实现。** Controller、Service、Mapper 均已完成。

---

## 3. 从专栏移除文章

```
DELETE /api/v1/author/series/{seriesId}/posts/{postId}
DELETE /api/v1/admin/series/{seriesId}/posts/{postId}
```

### 响应

```json
{
  "code": 0,
  "message": "success",
  "data": null
}
```

### 实现要点

- 新增 Service 方法：`SeriesService.removePostFromSeries(Long seriesId, Long postId)`
- 逻辑：先查 `series_posts` 确认该文章确实属于该专栏，是则删除，否返回 404
- SQL：`DELETE FROM series_posts WHERE series_id = #{seriesId} AND post_id = #{postId}`（新增 Mapper 方法 `deleteBySeriesIdAndPostId`，或复用现有 `deleteByPostId` 配合查询校验）

---

## 需要新增的代码清单

| 层级 | 文件 | 新增内容 |
|------|------|----------|
| VO | `blog/vo/SeriesPostVO.java` | 新 VO，字段 postId/title/slug/sortOrder |
| Mapper | `blog/mapper/SeriesPostsMapper.java` | + `selectPostsBySeriesId`、`deleteBySeriesIdAndPostId` |
| XML | `resources/.../SeriesPostsMapper.xml` | 对应 SQL |
| Service 接口 | `blog/service/SeriesService.java` | + `getPostsBySeriesId`、`removePostFromSeries` |
| Service 实现 | `blog/service/impl/SeriesServiceImpl.java` | 实现上述方法 |
| Controller | `author/AuthorSeriesController.java` | + `GET /series/{id}/posts`、`DELETE /series/{id}/posts/{postId}` |
| Controller | `admin/AdminSeriesController.java` | 同上，Admin 版本 |
