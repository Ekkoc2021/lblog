# Token 管理（会话管理 + Token 配置）设计文档

> 日期：2026-06-06 | 版本：1

## 1. 概述

为超级管理员（admin）提供后台会话管理和 Token 过期时间配置能力。

**核心能力：**
- 查看所有活跃会话（ACCESS + REFRESH token）
- 吊销单条 token / 踢某用户下线（吊销其全部 token）
- 动态设置 ACCESS / REFRESH token 过期时间，新签发 token 即时生效

## 2. 架构

```
TokenGenerator  ←→  SiteConfigCacheService  (读取 token_access_ttl / token_refresh_ttl)
       ↓
TokenServiceImpl    (签发 token 时使用动态 TTL)
       ↓
DbTokenRepository  →  UserTokenMapper  →  user_tokens 表
       ↑
AdminTokenController  (新增，会话查询 / 吊销 / 踢人 / 配置读写)
       ↑
SessionManage.tsx  (新增前端页面)
```

**不改动：** TokenService 接口签名、TokenRepository 接口签名
**新增：** AdminTokenController、UserTokenMapper 新增查询方法、TokenGenerator 支持动态 TTL

## 3. 数据库

### 3.1 现有表 `user_tokens`（不改动）

| 列 | 类型 | 说明 |
|----|------|------|
| id | BIGINT PK | 自增 |
| user_id | BIGINT FK→users | 用户ID |
| token_hash | VARCHAR(64) UNIQUE | SHA-256(token) |
| token_type | VARCHAR(10) | ACCESS / REFRESH |
| expires_at | DATETIME | 过期时间 |
| created_at | DATETIME | 创建时间 |
| revoked | TINYINT(1) | 0=正常 1=吊销 |
| replaced_by | VARCHAR(64) | rotation 链 |

### 3.2 新增 site_config 键

| config_key | 值示例 | 说明 |
|------------|--------|------|
| `token_access_ttl` | `7200` | ACCESS 过期秒数，默认 7200（2h） |
| `token_refresh_ttl` | `604800` | REFRESH 过期秒数，默认 604800（7d） |

### 3.3 UserTokenMapper 新增查询

```xml
<!-- 活跃会话列表（JOIN users 获取用户名/昵称） -->
<select id="selectActiveSessions" resultMap="SessionResultMap">
    SELECT ut.id, ut.user_id, u.username, u.nickname,
           ut.token_type, ut.token_hash, ut.created_at, ut.expires_at
    FROM user_tokens ut
    JOIN users u ON u.id = ut.user_id
    WHERE ut.revoked = 0
      AND ut.expires_at > NOW()
    <if test="keyword != null and keyword != ''">
      AND u.username LIKE CONCAT('%', #{keyword}, '%')
    </if>
    ORDER BY ut.created_at DESC
    LIMIT #{limit} OFFSET #{offset}
</select>

<!-- 活跃会话总数 -->
<select id="countActiveSessions" resultType="int">
    SELECT COUNT(*)
    FROM user_tokens ut
    JOIN users u ON u.id = ut.user_id
    WHERE ut.revoked = 0
      AND ut.expires_at > NOW()
    <if test="keyword != null and keyword != ''">
      AND u.username LIKE CONCAT('%', #{keyword}, '%')
    </if>
</select>

<!-- 清理过期 token -->
<delete id="deleteExpiredTokens">
    DELETE FROM user_tokens WHERE expires_at &lt; NOW()
</delete>
```

## 4. API 设计

所有接口均需 admin 角色：`@PreAuthorize("hasRole('ADMIN')")`

### 4.1 会话管理

#### GET /api/v1/admin/sessions

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| page | int | 否 | 默认 1 |
| pageSize | int | 否 | 默认 20，最大 100 |
| keyword | string | 否 | 模糊搜索用户名 |

**响应：**
```json
{
  "code": 0,
  "data": {
    "total": 13,
    "page": 1,
    "pageSize": 20,
    "list": [{
      "id": 807,
      "userId": 1,
      "username": "ekko",
      "nickname": "Ekko",
      "tokenType": "ACCESS",
      "tokenPreview": "a3f2c8e1****",
      "createdAt": "2026-06-06 10:30:00",
      "expiresAt": "2026-06-06 12:30:00",
      "expiringSoon": false
    }]
  }
}
```

- `tokenPreview`：取 hash 前 8 位 + `****`，后端脱敏
- `expiringSoon`：expiresAt 距当前 < 30 分钟为 true

#### DELETE /api/v1/admin/sessions/{id}

吊销单条 token。

**响应：** `200` / `404`

#### DELETE /api/v1/admin/sessions/user/{userId}

踢下线：吊销该用户所有有效 token。

**响应：** `200 { "data": { "revokedCount": 3 } }` — 返回实际吊销数量

#### DELETE /api/v1/admin/sessions/cleanup

手动清理所有过期 token。

**响应：** `200 { "data": { "deletedCount": 775 } }`

### 4.2 Token 配置

#### GET /api/v1/admin/token-config

```json
{
  "code": 0,
  "data": {
    "accessTtl": 7200,
    "refreshTtl": 604800
  }
}
```

若 config 不存在，返回默认值。

#### PUT /api/v1/admin/token-config

**请求：**
```json
{
  "accessTtl": 7200,
  "refreshTtl": 604800
}
```

**校验：** accessTtl 范围 [300, 86400]（5min ~ 24h），refreshTtl 范围 [1800, 2592000]（30min ~ 30d）

**响应：** 200，写入 site_config 表。已有值则更新，无则插入。

## 5. TokenGenerator 改造

```java
// 之前：硬编码 TTL
// 之后：从 SiteConfigCacheService 读取
long accessTtl = configService.getLong("token_access_ttl", DEFAULT_ACCESS_TTL);
pair.setAccessExpiresAt(LocalDateTime.now().plusSeconds(accessTtl));
```

对已登录用户：旧 token TTL 不变，仅在下次刷新/登录时使用新 TTL。

## 6. 前端

### 6.1 路由

`/admin/sessions` — 仅 admin 可访问

### 6.2 页面布局

```
┌─ Token 配置面板 ──────────────────────────────────────┐
│ ACCESS 过期 [  2  ] 小时  REFRESH 过期 [  7  ] 天    │
│ [保存]  当前：ACCESS 2h / REFRESH 7d                   │
└───────────────────────────────────────────────────────┘

┌─ 活跃会话 ──────────── [搜索用户...] ── [清理过期token] ──┐
│ 用户 │ 类型 │ Token │ 登录时间 │ 过期时间 │ 状态 │ 操作  │
│ ekko │ ACCESS │ a3f2**** │ 10:30 │ 12:30 │ 正常 │ [吊销] [踢下线] │
│ alice │ REFRESH │ b1c4**** │ 08:00 │ 06-13 │ 正常 │ [吊销] [踢下线] │
│ ...                                             │
│ 分页: < 1 2 3 >  共 13 条                        │
└───────────────────────────────────────────────────────┘
```

### 6.3 状态标签

- 正常（绿色）：expiresAt - now > 30min
- 即将过期（橙色）：expiresAt - now ≤ 30min

## 7. 脱敏规则

- token 原文全程不出库（hash 不可逆）
- 返回 `tokenPreview = hash.substring(0, 8) + "****"`
- hash 作用：供管理员辨识不同 token，但无法还原原文

## 8. API 测试场景

### 8.1 会话列表

| # | 场景 | 预期 |
|---|------|------|
| S1 | 无参数查询 | 200，返回活跃会话分页，tokenPreview 已脱敏 |
| S2 | page=1&pageSize=5 | 200，返回前 5 条 |
| S3 | keyword=ekko | 200，只返回 ekko 的会话 |
| S4 | keyword=不存在的用户 | 200，total=0 |
| S5 | 不传 Authorization | 401 |
| S6 | 非 admin 用户 | 403 |
| S7 | pageSize=200（超限） | 400，参数校验失败 |
| S8 | 空数据库（无活跃会话） | 200，total=0，list=[] |
| S9 | 验证 expiringSoon | 模拟即将过期 token，expiringSoon=true |

### 8.2 吊销单条 token

| # | 场景 | 预期 |
|---|------|------|
| R1 | 吊销存在的有效 token | 200，数据库 revoked=1 |
| R2 | 吊销已吊销的 token | 200，幂等（UPDATE 影响 0 行也算成功） |
| R3 | 吊销不存在的 id | 404 |
| R4 | 吊销后该 token 不能用于鉴权 | 下次请求 401 |

### 8.3 踢下线

| # | 场景 | 预期 |
|---|------|------|
| K1 | 踢用户下线（有 3 个活跃 token） | 200，revokedCount=3 |
| K2 | 踢用户下线（该用户无活跃 token） | 200，revokedCount=0 |
| K3 | 踢不存在的用户 | 200，revokedCount=0（不报错） |
| K4 | 踢自己（admin） | 200，自己的 token 也被吊销，下次请求 401 需重新登录 |
| K5 | 踢用户后验证其访问被拒绝 | 该用户用旧 token 请求返回 401 |

### 8.4 清理过期 token

| # | 场景 | 预期 |
|---|------|------|
| C1 | 手动清理 | 200，返回删除数量 |
| C2 | 无过期 token 时清理 | 200，deletedCount=0 |
| C3 | 清理不删活跃 token | 清理后会话列表数量不变 |

### 8.5 Token 配置

| # | 场景 | 预期 |
|---|------|------|
| T1 | 获取默认值 | 200，accessTtl=7200, refreshTtl=604800 |
| T2 | 设置合法值 | 200，写入成功 |
| T3 | 再次获取 | 200，返回新值 |
| T4 | accessTtl=100（低于 300） | 400，参数校验失败 |
| T5 | accessTtl=100000（超过 86400） | 400 |
| T6 | refreshTtl=100（低于 1800） | 400 |
| T7 | refreshTtl=99999999（超过 2592000） | 400 |
| T8 | 只传 accessTtl | 200，仅更新 access |
| T9 | 设置后新登录用户 token TTL | 新 token 用新 TTL |
| T10 | 设置后已登录用户 | 旧 token TTL 不变，刷新后更新 |
| T11 | 非 admin 用户修改 | 403 |

## 9. 涉及文件

| 层 | 文件 | 操作 |
|----|------|------|
| 后端 Controller | `auth/controller/admin/AdminTokenController.java` | 新增 |
| 后端 Mapper | `auth/mapper/UserTokenMapper.java` | 新增方法 |
| 后端 XML | `auth/mapper/UserTokenMapper.xml` | 新增查询 |
| 后端 Service | `auth/security/util/TokenGenerator.java` | 改造：动态 TTL |
| 后端 VO | `auth/vo/SessionVO.java`、`TokenConfigVO.java` | 新增 |
| 前端 | `pages/admin/SessionManage.tsx` | 新增 |
| 前端 | `services/api.ts` | 新增 API 函数 |
| 前端 | `App.tsx` | 新增路由 |
| 前端 | `pages/admin/AdminDashboard.tsx` | 新增菜单项 |
| 前端 | `types/index.ts` | 新增类型 |
