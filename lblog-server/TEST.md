# 后端模块化重构 — 测试验证文档

> 重构前后 API 响应完全一致，只需验证接口仍正常工作。

## 环境准备

```bash
BASE=http://localhost:8099/iblogserver/api/v1
```

## 一、冒烟测试（快速验证核心链路）

重构后先跑这 10 个接口，确认服务器正常启动、路由没断。

### 1.1 公开接口

```bash
# 文章列表
curl -s "$BASE/posts?page=1&pageSize=5" | jq .code
# 期望: 0

# 文章详情
curl -s "$BASE/posts/hello-world" | jq .code
# 期望: 0

# 分类列表
curl -s "$BASE/categories" | jq .code
# 期望: 0

# 站点配置
curl -s "$BASE/config" | jq .code
# 期望: 0
```

### 1.2 登录

```bash
# 正常登录（实际用户名密码以数据库为准）
curl -s -X POST "$BASE/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}' | jq .
# 期望: code=0, data.accessToken 有值

# 保存 token
TOKEN=<上一步返回的 accessToken>
```

### 1.3 AI 绘图

```bash
curl -s "$BASE/draw/config" | jq .
# 期望: code=0, data.enabled=true 或 false
```

### 1.4 管理端（需 ADMIN 角色）

```bash
curl -s "$BASE/admin/posts?page=1&pageSize=5" \
  -H "Authorization: Bearer $TOKEN" | jq .code
# 期望: code=0
```

### 1.5 创作中心（需 AUTHOR/ADMIN 角色）

```bash
curl -s "$BASE/author/posts?page=1&pageSize=5" \
  -H "Authorization: Bearer $TOKEN" | jq .code
# 期望: code=0
```

## 二、完整回归测试清单

按模块分组，重构后每个端点至少验证一次 HTTP 状态码和响应结构。

### 公开 API（10 个）

| # | 方法 | 路径 | 验证要点 | curl 命令 |
|---|------|------|---------|----------|
| 1 | GET | /posts?page=1&pageSize=5 | code=0, data.list 不为空 | `curl -s "$BASE/posts?page=1&pageSize=5"` |
| 2 | GET | /categories | code=0, 返回分类列表 | `curl -s "$BASE/categories"` |
| 3 | GET | /tags | code=0, 返回标签列表 | `curl -s "$BASE/tags"` |
| 4 | GET | /series | code=0, 返回专栏列表 | `curl -s "$BASE/series"` |
| 5 | GET | /posts/hot | code=0, 返回热门文章 | `curl -s "$BASE/posts/hot"` |
| 6 | GET | /posts/{slug} | code=0, 含正文 | `curl -s "$BASE/posts/hello-world"` |
| 7 | POST | /posts/{id}/view | code=0, 浏览量+1 | `curl -s -X POST "$BASE/posts/1/view"` |
| 8 | POST | /posts/{id}/like | code=0, 点赞成功 | `curl -s -X POST "$BASE/posts/1/like" \ -H "X-Visitor-Id: test-visitor"` |
| 9 | DELETE | /posts/{id}/like | code=0, 取消点赞 | `curl -s -X DELETE "$BASE/posts/1/like" \ -H "X-Visitor-Id: test-visitor"` |
| 10 | GET | /posts/{id}/like/status | code=0 | `curl -s "$BASE/posts/1/like/status?visitorId=test-visitor"` |

### 评论公开 API（3 个）

| # | 方法 | 路径 | 验证 | curl |
|---|------|------|------|------|
| 11 | GET | /posts/{postId}/comments | code=0 | `curl -s "$BASE/posts/1/comments?page=1"` |
| 12 | GET | /posts/{postId}/comments/{rootId}/replies | code=0 | `curl -s "$BASE/posts/1/comments/1/replies"` |

### 站点配置（2 个）

| # | 方法 | 路径 | 验证 | curl |
|---|------|------|------|------|
| 13 | GET | /config | code=0 | `curl -s "$BASE/config"` |

### 认证 API（6 个）

| # | 方法 | 路径 | 验证 | curl |
|---|------|------|------|------|
| 14 | POST | /auth/login | code=0, 返回 token | `curl -s -X POST "$BASE/auth/login" -H "Content-Type: application/json" -d '{"username":"admin","password":"admin"}'` |
| 15 | GET | /auth/me | code=0, 返回用户信息 | `curl -s "$BASE/auth/me" -H "Authorization: Bearer $TOKEN"` |
| 16 | POST | /auth/logout | code=0 | `curl -s -X POST "$BASE/auth/logout" -H "Authorization: Bearer $TOKEN"` |
| 17 | POST | /auth/register | 根据配置可能关闭 | `curl -s -X POST "$BASE/auth/register" -H "Content-Type: application/json" -d '{"username":"test","password":"test123","nickname":"test"}'` |

### 创作中心（需登录 + AUTHOR 角色，共约 28 个）

抽检关键 6 个：

| # | 方法 | 路径 | 验证 | curl |
|---|------|------|------|------|
| 18 | GET | /author/posts | code=0 | `curl -s "$BASE/author/posts?page=1" -H "Authorization: Bearer $TOKEN"` |
| 19 | GET | /author/posts/check-slug?slug=test | code=0 | `curl -s "$BASE/author/posts/check-slug?slug=test" -H "Authorization: Bearer $TOKEN"` |
| 20 | GET | /author/categories | code=0 | `curl -s "$BASE/author/categories" -H "Authorization: Bearer $TOKEN"` |
| 21 | GET | /author/tags | code=0 | `curl -s "$BASE/author/tags" -H "Authorization: Bearer $TOKEN"` |
| 22 | GET | /author/series | code=0 | `curl -s "$BASE/author/series" -H "Authorization: Bearer $TOKEN"` |
| 23 | GET | /author/comments | code=0 | `curl -s "$BASE/author/comments" -H "Authorization: Bearer $TOKEN"` |

### 管理端（需 ADMIN 角色，共约 43 个）

抽检关键 8 个：

| # | 方法 | 路径 | 验证 | curl |
|---|------|------|------|------|
| 24 | GET | /admin/posts | code=0 | `curl -s "$BASE/admin/posts?page=1" -H "Authorization: Bearer $TOKEN"` |
| 25 | GET | /admin/categories | code=0 | `curl -s "$BASE/admin/categories" -H "Authorization: Bearer $TOKEN"` |
| 26 | GET | /admin/tags | code=0 | `curl -s "$BASE/admin/tags" -H "Authorization: Bearer $TOKEN"` |
| 27 | GET | /admin/series | code=0 | `curl -s "$BASE/admin/series" -H "Authorization: Bearer $TOKEN"` |
| 28 | GET | /admin/comments | code=0 | `curl -s "$BASE/admin/comments" -H "Authorization: Bearer $TOKEN"` |
| 29 | GET | /admin/users | code=0 | `curl -s "$BASE/admin/users?page=1" -H "Authorization: Bearer $TOKEN"` |
| 30 | GET | /admin/configs | code=0 | `curl -s "$BASE/admin/configs" -H "Authorization: Bearer $TOKEN"` |
| 31 | GET | /admin/images/statistics | code=0 | `curl -s "$BASE/admin/images/statistics" -H "Authorization: Bearer $TOKEN"` |

### AI 绘图（2 个）

| # | 方法 | 路径 | 验证 | curl |
|---|------|------|------|------|
| 32 | GET | /draw/config | code=0 | `curl -s "$BASE/draw/config"` |
| 33 | POST | /draw/chat | 400 (空消息) | `curl -s -X POST "$BASE/draw/chat" -H "Content-Type: application/json" -d '{"messages":[]}'` |

## 三、回归测试脚本

```bash
#!/bin/bash
# save as test-regression.sh
BASE=http://localhost:8099/iblogserver/api/v1

echo "=== 1. 公开接口 ==="
curl -s "$BASE/posts?page=1&pageSize=1" | jq -r '"posts: \(.code)"'
curl -s "$BASE/categories" | jq -r '"categories: \(.code)"'
curl -s "$BASE/tags" | jq -r '"tags: \(.code)"'
curl -s "$BASE/series" | jq -r '"series: \(.code)"'
curl -s "$BASE/posts/hot" | jq -r '"hot: \(.code)"'
curl -s "$BASE/config" | jq -r '"config: \(.code)"'

echo "=== 2. 登录 ==="
RESP=$(curl -s -X POST "$BASE/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin"}')
echo "$RESP" | jq -r '"login: \(.code)"'
TOKEN=$(echo "$RESP" | jq -r '.data.accessToken')

echo "=== 3. AI 绘图 ==="
curl -s "$BASE/draw/config" | jq -r '"draw/config: \(.code)"'

echo "=== 4. 管理端 ==="
curl -s "$BASE/admin/posts?page=1" -H "Authorization: Bearer $TOKEN" | jq -r '"admin/posts: \(.code)"'
curl -s "$BASE/admin/categories" -H "Authorization: Bearer $TOKEN" | jq -r '"admin/categories: \(.code)"'
curl -s "$BASE/admin/tags" -H "Authorization: Bearer $TOKEN" | jq -r '"admin/tags: \(.code)"'
curl -s "$BASE/admin/series" -H "Authorization: Bearer $TOKEN" | jq -r '"admin/series: \(.code)"'
curl -s "$BASE/admin/comments" -H "Authorization: Bearer $TOKEN" | jq -r '"admin/comments: \(.code)"'
curl -s "$BASE/admin/users?page=1" -H "Authorization: Bearer $TOKEN" | jq -r '"admin/users: \(.code)"'
curl -s "$BASE/admin/configs" -H "Authorization: Bearer $TOKEN" | jq -r '"admin/configs: \(.code)"'

echo "=== 5. 创作中心 ==="
curl -s "$BASE/author/posts?page=1" -H "Authorization: Bearer $TOKEN" | jq -r '"author/posts: \(.code)"'
curl -s "$BASE/author/categories" -H "Authorization: Bearer $TOKEN" | jq -r '"author/categories: \(.code)"'
curl -s "$BASE/author/tags" -H "Authorization: Bearer $TOKEN" | jq -r '"author/tags: \(.code)"'
curl -s "$BASE/author/series" -H "Authorization: Bearer $TOKEN" | jq -r '"author/series: \(.code)"'
curl -s "$BASE/author/comments" -H "Authorization: Bearer $TOKEN" | jq -r '"author/comments: \(.code)"'

echo "=== 6. 上传 ==="
curl -s "$BASE/upload/image" -H "Authorization: Bearer $TOKEN" | jq -r '"upload: \(.code)"'

echo ""
echo "=== 全部完成 ==="
```

## 四、预期结果

所有接口返回 `code=0`（除 `/draw/chat` 空消息返回 `400`，以及 `/upload/image` 无文件返回 `400`）。

## 五、失败排查

| 现象 | 原因 |
|------|------|
| 404 | 路由未找到，检查 controller @RequestMapping 是否丢失 |
| 500 + ClassNotFoundException | package 声明未更新或 imports 错误 |
| 500 + NoSuchBeanDefinitionException | @Component/@Service/@Mapper 扫描路径不对 |
| MyBatis 绑定异常 | mapper XML namespace 或路径与 Java 接口不匹配 |
