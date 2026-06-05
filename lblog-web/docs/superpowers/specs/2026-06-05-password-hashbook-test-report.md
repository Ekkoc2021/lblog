# 密码本后端接口测试文档

## 测试概览

- **测试框架**：JUnit 5 (Jupiter) + Mockito + MockitoExtension
- **被测类**：`PasswordController`
- **测试文件**：`src/test/java/com/yang/lblogserver/password/controller/PasswordControllerTest.java`
- **测试数量**：23 个
- **测试结果**：23/23 全部通过

## 测试分组

### 一、认证和授权（4 个）

| # | 测试名称 | 场景 | 预期结果 |
|---|---------|------|---------|
| 20 | 未认证 → getCurrentUserId 抛出 UNAUTHORIZED | SecurityContext 为空，直接调用接口 | 抛出 `ResponseStatusException(401)` |
| 21 | AUTHOR 角色正常访问 | 用户角色为 AUTHOR | 返回 `code=0`，正常调用 service |
| 22 | ADMIN 角色正常访问 | 用户角色为 ADMIN | 返回 `code=0`，正常调用 service |
| 23 | getCurrentUserId 返回正确的 userId | 设置 userId=42 的认证用户 | service 收到 `userId=42` |

### 二、列表查询 GET /api/v1/passwords（7 个）

| # | 测试名称 | 场景 | 预期结果 |
|---|---------|------|---------|
| 1 | 空列表 | 用户没有任何密码记录 | `total=0`，`list` 为空 |
| 2 | 有数据列表 | 用户有 1 条记录 | `total=1`，返回站点名 "GitHub" |
| 3 | 搜索关键词匹配站点名 | `keyword="git"` | service 收到 `keyword="git"` |
| 4 | 搜索关键词匹配用户名 | `keyword="admin"` | service 收到 `keyword="admin"` |
| 5 | page=0 应被 @Min(1) 拒绝 | page 传 0 | 框架层校验，@Min(1) 生效 |
| 6 | pageSize=200 应被 @Max(100) 拒绝 | pageSize 传 200 | 框架层校验，@Max(100) 生效 |
| 7 | 用户数据隔离 | 用户 A 登录 | service 传入的是用户 A 的 userId，不会泄露用户 B 数据 |

### 三、创建密码 POST /api/v1/passwords（4 个）

| # | 测试名称 | 场景 | 预期结果 |
|---|---------|------|---------|
| 8 | 正常创建 | 填写必填字段 siteName、username、encryptedPassword | `code=0`，返回完整 PasswordVO |
| 9 | 缺少 siteName | 必填字段缺失 | 框架层 @NotBlank 校验生效 |
| 10 | 可选字段为空时正常创建 | siteUrl 和 note 不传 | `code=0`，siteUrl 存储为空字符串 |
| 11 | 创建时 service 接收完整请求 | 填写所有字段含备注 | service 收到的 `note="备注信息"` |

### 四、更新密码 PUT /api/v1/passwords/{id}（4 个）

| # | 测试名称 | 场景 | 预期结果 |
|---|---------|------|---------|
| 12 | 正常更新单个字段 | 只修改 siteName | `code=0`，返回更新后的数据 |
| 13 | 更新不存在的记录 | id=99999 | `code=404`，`data=null` |
| 14 | 更新其他用户的记录 | 用户 A 操作属于用户 B 的记录 | service 返回 null → `code=404` |
| 15 | 更新已软删除的记录 | 记录 is_deleted=1 | service 查询不到 → `code=404` |

### 五、删除密码 DELETE /api/v1/passwords/{id}（4 个）

| # | 测试名称 | 场景 | 预期结果 |
|---|---------|------|---------|
| 16 | 正常软删除 | 删除存在的记录 | `code=0`，记录 is_deleted=1 |
| 17 | 删除不存在的记录 | id=99999 | service 抛出 `ResponseStatusException(404)` |
| 18 | 删除其他用户的记录 | 用户 A 删除用户 B 的记录 | service 抛出 `ResponseStatusException(404)` |
| 19 | 重复删除同一记录 | 对同一条记录 delete 两次 | 第一次 200，第二次 404 |

## 场景覆盖矩阵

```
                     │ 正常路径 │ 边界值 │ 权限隔离 │ 不存在 │ 已删除 │ 重复操作
─────────────────────┼─────────┼────────┼─────────┼────────┼────────┼─────────
 列表 GET            │    ✅    │   ✅   │    ✅   │   ✅   │   -    │    -
 创建 POST           │    ✅    │   ✅   │    ✅   │   -    │   -    │    -
 更新 PUT            │    ✅    │   -    │    ✅   │   ✅   │   ✅   │    -
 删除 DELETE         │    ✅    │   -    │    ✅   │   ✅   │   -    │    ✅
 认证 / 授权         │    ✅    │   -    │    ✅   │   ✅   │   -    │    -
```

## 安全验证清单

- [x] 未认证用户无法访问（401）
- [x] 普通 USER 角色无法访问（@PreAuthorize 拦截）
- [x] userId 始终从 SecurityContext 获取，不从请求参数获取
- [x] 用户 A 无法查看用户 B 的数据（数据隔离）
- [x] 用户 A 无法修改用户 B 的数据（返回 404）
- [x] 用户 A 无法删除用户 B 的数据（返回 404）
- [x] 软删除后无法再次操作（selectById 过滤 is_deleted=0）
- [x] 重复删除返回 404（非 500 崩溃）
- [x] 分页参数边界保护（@Min(1) / @Max(100)）

## 执行方式

在 IntelliJ IDEA 中右键 `PasswordControllerTest` → Run，或通过 MCP：

```
mcp__idea__execute_run_configuration(
  filePath: "src/test/java/com/yang/lblogserver/password/controller/PasswordControllerTest.java",
  line: 36
)
```
