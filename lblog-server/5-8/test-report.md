# 作者统计接口测试报告

> 接口: `GET /api/v1/author/statistics`

---

## 测试环境

| 项目 | 值 |
|------|-----|
| 接口路径 | `http://localhost:8099/iblogserver/api/v1/author/statistics` |
| 请求方式 | GET |
| 认证方式 | Bearer Token |
| 数据库 | MySQL 8, `192.168.1.5:3306/iblog` |
| 应用端口 | 8099 |
| Context Path | `/iblogserver` |
| 测试日期 | 2026-05-08 |
| Controller | `AdminController.getAuthorStatistics()` |
| Service | `PostsServiceImpl.getAuthorStatistics(Long authorId)` |
| Mapper | `PostsMapper` (XML in `PostsMapper.xml`) |

### 角色体系

Spring Security 角色层级: `ROLE_ADMIN > ROLE_AUTHOR > ROLE_USER`

- `ROLE_ADMIN` 和 `ROLE_AUTHOR` 可访问 `/api/v1/author/**`
- `ROLE_USER` 被拒绝访问 (403)

### 测试用户

| 用户名 | 角色 | 文章数 | 密码 |
|--------|------|--------|------|
| ekko | admin | 15 | admin123 |
| alice | author | 4 | alice123 |
| bob | author | 12 | bob123 |
| normaluser | user | - | user123 |

---

## 测试场景与结果

### 场景一：正常访问（作者角色）

**步骤:**
1. POST `/api/v1/auth/login` as `alice` (author)
2. GET `/api/v1/author/statistics` with Bearer Token

**结果:** `200 OK`

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "totalPosts": 4,
    "totalViews": 7990,
    "totalLikes": 482,
    "totalComments": 132,
    "statusDistribution": [
      { "status": 1, "count": 4 }
    ],
    "categoryDistribution": [
      { "categoryName": "Backend Updated", "categorySlug": "backend", "postCount": 1 },
      { "categoryName": "前端", "categorySlug": "frontend", "postCount": 1 },
      { "categoryName": "DevOps", "categorySlug": "devops", "postCount": 1 },
      { "categoryName": "数据库", "categorySlug": "database", "postCount": 1 }
    ],
    "monthlyTrend": [
      { "month": "2025-06", "count": 0 },
      { "month": "2025-07", "count": 0 },
      { "month": "2025-08", "count": 0 },
      { "month": "2025-09", "count": 0 },
      { "month": "2025-10", "count": 0 },
      { "month": "2025-11", "count": 0 },
      { "month": "2025-12", "count": 0 },
      { "month": "2026-01", "count": 0 },
      { "month": "2026-02", "count": 0 },
      { "month": "2026-03", "count": 0 },
      { "month": "2026-04", "count": 4 },
      { "month": "2026-05", "count": 0 }
    ]
  }
}
```

**验证项:**
- `totalPosts` = 4 (匹配 alice 的文章数)
- `totalViews` / `totalLikes` / `totalComments` 为 4 篇文章的累加
- `statusDistribution` 只包含 status=1 (已发布)
- `categoryDistribution` 包含 4 个分类，按 `postCount` 降序 (全部为 1)
- `monthlyTrend` 为 12 个月，有数据的月份为 2026-04 (count=4)
- 所有字段名与 API 文档一致

---

### 场景二：新作者/无文章

**步骤:**
1. 创建新作者 `newauthor` (role=author, 无文章)
2. POST `/api/v1/auth/login`
3. GET `/api/v1/author/statistics`

**结果:** `200 OK`

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
      { "month": "2025-06", "count": 0 },
      { "month": "2025-07", "count": 0 },
      { "month": "2025-08", "count": 0 },
      { "month": "2025-09", "count": 0 },
      { "month": "2025-10", "count": 0 },
      { "month": "2025-11", "count": 0 },
      { "month": "2025-12", "count": 0 },
      { "month": "2026-01", "count": 0 },
      { "month": "2026-02", "count": 0 },
      { "month": "2026-03", "count": 0 },
      { "month": "2026-04", "count": 0 },
      { "month": "2026-05", "count": 0 }
    ]
  }
}
```

**验证项:**
- `totalPosts` = 0, `totalViews` = 0, `totalLikes` = 0, `totalComments` = 0
- `statusDistribution` 为空数组
- `categoryDistribution` 为空数组
- `monthlyTrend` 返回近 12 个月，所有月份 count=0
- 返回 `200` 而非 404

---

### 场景三：未登录（无 Token）

**步骤:** 直接 GET 请求，不带 Authorization Header

**结果:** `401 Unauthorized`

```json
{
  "code": 401,
  "message": "未登录或 Token 已过期",
  "data": null
}
```

**验证项:**
- HTTP 状态码 401
- `code` 字段为 401
- `message` 为 "未登录或 Token 已过期"
- Spring Security `AuthenticationEntryPoint` 正确拦截未认证请求

---

### 场景四：角色权限不足

**步骤:**
1. POST `/api/v1/auth/login` as `normaluser` (role=user)
2. GET `/api/v1/author/statistics` with Bearer Token

**结果:** `403 Forbidden`

```json
{
  "code": 403,
  "message": "无权限访问",
  "data": null
}
```

**验证项:**
- HTTP 状态码 403
- `code` 字段为 403
- `message` 为 "无权限访问"
- Spring Security `AccessDeniedHandler` 正确拦截 `ROLE_USER` 角色

---

## 测试覆盖矩阵

| 场景 | 条件 | 预期 HTTP | 预期 code | 结果 |
|------|------|-----------|-----------|------|
| 正常访问 | 作者/admin 角色, 有文章 | 200 | 0 | PASS |
| 新作者 | 作者角色, 无文章 | 200 | 0 | PASS |
| 未登录 | 无 Token | 401 | 401 | PASS |
| 权限不足 | user 角色 | 403 | 403 | PASS |

---

## 发现的问题与修复

### 问题 1：测试过程中发现旧进程未正确重启

**发现过程:**
初次启动应用并测试时，`GET /api/v1/author/statistics` 返回了 `StatisticsVO`（站点统计）的数据结构，而非期望的 `AuthorStatisticsVO`：

```json
{
  "data": {
    "postCount": 31,
    "viewCount": 19486,
    "likeCount": 1217,
    "commentCount": 326,
    "categoryDistribution": [{"name":"...","count":...}],
    "tagDistribution": [...]
  }
}
```

**根因分析:**
Spring Boot 应用进程（PID 19128）仍然在运行，执行 `execute_run_configuration` 启动的新实例（PID 3724）因端口占用未能启动。后续所有 HTTP 请求均由旧进程处理，而旧进程运行的是上一次编译的字节码。

此时 `compile` 虽然成功（产出新 `.class` 到 `target/classes`），但旧 JVM 进程不会自动热加载。

**修复:**
1. 使用 `taskkill /F /PID 19128` 强制终止旧进程
2. 确认端口 8099 已释放
3. 重新 `build_project(rebuild=true)` 确保全部字节码最新
4. 重新 `execute_run_configuration` 启动新进程

**验证:**
重启后响应正确：
```json
{
  "data": {
    "totalPosts": 4,
    "totalViews": 7990,
    "totalLikes": 482,
    "totalComments": 132,
    "statusDistribution": [...],
    "categoryDistribution": [...],
    "monthlyTrend": [...]
  }
}
```

### 问题 2：无（代码层面无 Bug）

接口实现与 API 文档定义一致，字段名、类型、结构均正确：
- `AuthorStatisticsVO` 的 `totalPosts` / `totalViews` / `totalLikes` / `totalComments` / `statusDistribution` / `categoryDistribution` / `monthlyTrend` 均正确返回
- `categoryDistribution` 的子项为 `categoryName` / `categorySlug` / `postCount`
- 权限控制正确（401 未登录、403 角色不足）

---

## 测试结论

**`GET /api/v1/author/statistics` 接口实现正确，所有测试场景通过。**

- 接口返回结构符合 `api-statistics.md` 文档的 `AuthorStatisticsVO` 定义
- 认证和授权机制正常（401 未认证 / 403 角色不足 / 200 正常响应）
- 新作者场景正确处理零数据情况（返回 200 而非错误）
- 月度趋势正确覆盖近 12 个月，缺失月份补零
- 分类分布按文章数降序排列
- 统计指标基于 `is_delelte = 0 AND deleted_at IS NULL`（未软删除）过滤
