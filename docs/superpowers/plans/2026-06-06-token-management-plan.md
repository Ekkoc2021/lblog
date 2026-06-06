# Token 管理（会话管理 + Token 配置）实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为超级管理员提供在线会话管理和 Token 过期时间动态配置能力

**Architecture:** 新增 AdminTokenController（会话查询/吊销/踢人 + Token 配置读写），TokenGenerator 从 SiteConfigCacheService 读取动态 TTL，前端新增 SessionManage 页面

**Tech Stack:** Spring Boot + MyBatis + React + TypeScript + Ant Design

---

## 文件结构

| 层 | 文件 | 职责 |
|----|------|------|
| 后端 VO | `auth/vo/SessionVO.java` | 会话列表响应 |
| 后端 VO | `auth/vo/TokenConfigVO.java` | Token 配置请求/响应 |
| 后端 VO | `auth/vo/BatchOpResult.java` | 批量操作返回计数 |
| 后端 Controller | `auth/controller/admin/AdminTokenController.java` | 会话 + 配置 API |
| 后端 Mapper | `auth/mapper/UserTokenMapper.java` | 新增 3 个查询方法 |
| 后端 XML | `auth/mapper/UserTokenMapper.xml` | 新增 SQL + 新 ResultMap |
| 后端 Util | `auth/security/util/TokenGenerator.java` | 改为动态 TTL |
| 后端 Config | `common/init/DefaultConfig.java` | 新增两个默认 key |
| 前端 Page | `pages/admin/SessionManage.tsx` | 会话管理页面 |
| 前端 API | `services/api.ts` | 新增 API 函数 |
| 前端 Route | `App.tsx` | 新增路由导入 |
| 前端 Menu | `pages/admin/AdminDashboard.tsx` | 新增菜单卡片 |

---

### Task 1: 后端 VO 类

**Files:**
- Create: `lblog-server/src/main/java/com/yang/lblogserver/auth/vo/SessionVO.java`
- Create: `lblog-server/src/main/java/com/yang/lblogserver/auth/vo/TokenConfigVO.java`
- Create: `lblog-server/src/main/java/com/yang/lblogserver/auth/vo/BatchOpResult.java`

- [ ] **Step 1: 创建 SessionVO.java**

```java
package com.yang.lblogserver.auth.vo;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "活跃会话")
public class SessionVO {

    @Schema(description = "Token ID")
    private Long id;

    @Schema(description = "用户ID")
    private Long userId;

    @Schema(description = "用户名")
    private String username;

    @Schema(description = "昵称")
    private String nickname;

    @Schema(description = "Token 类型：ACCESS / REFRESH")
    private String tokenType;

    @Schema(description = "Token 脱敏预览（hash 前8位+****）")
    private String tokenPreview;

    @Schema(description = "创建时间")
    private String createdAt;

    @Schema(description = "过期时间")
    private String expiresAt;

    @Schema(description = "是否即将过期（<30min）")
    private boolean expiringSoon;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }
    public String getTokenType() { return tokenType; }
    public void setTokenType(String tokenType) { this.tokenType = tokenType; }
    public String getTokenPreview() { return tokenPreview; }
    public void setTokenPreview(String tokenPreview) { this.tokenPreview = tokenPreview; }
    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
    public String getExpiresAt() { return expiresAt; }
    public void setExpiresAt(String expiresAt) { this.expiresAt = expiresAt; }
    public boolean isExpiringSoon() { return expiringSoon; }
    public void setExpiringSoon(boolean expiringSoon) { this.expiringSoon = expiringSoon; }
}
```

- [ ] **Step 2: 创建 TokenConfigVO.java**

```java
package com.yang.lblogserver.auth.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

@Schema(description = "Token 过期时间配置")
public class TokenConfigVO {

    @Schema(description = "ACCESS 过期秒数，范围 [300, 86400]")
    @Min(value = 300, message = "ACCESS 过期时间不能小于 300 秒（5 分钟）")
    @Max(value = 86400, message = "ACCESS 过期时间不能大于 86400 秒（24 小时）")
    private Long accessTtl;

    @Schema(description = "REFRESH 过期秒数，范围 [1800, 2592000]")
    @Min(value = 1800, message = "REFRESH 过期时间不能小于 1800 秒（30 分钟）")
    @Max(value = 2592000, message = "REFRESH 过期时间不能大于 2592000 秒（30 天）")
    private Long refreshTtl;

    public Long getAccessTtl() { return accessTtl; }
    public void setAccessTtl(Long accessTtl) { this.accessTtl = accessTtl; }
    public Long getRefreshTtl() { return refreshTtl; }
    public void setRefreshTtl(Long refreshTtl) { this.refreshTtl = refreshTtl; }
}
```

- [ ] **Step 3: 创建 BatchOpResult.java**

```java
package com.yang.lblogserver.auth.vo;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "批量操作结果")
public class BatchOpResult {

    @Schema(description = "受影响数量")
    private long count;

    public BatchOpResult(long count) { this.count = count; }

    public long getCount() { return count; }
}
```

- [ ] **Step 4: 编译验证**

Run: IDE Build → expected success

---

### Task 2: DefaultConfig 添加默认值

**Files:**
- Modify: `lblog-server/src/main/java/com/yang/lblogserver/common/init/DefaultConfig.java:12-28`

- [ ] **Step 1: 新增 token TTL 默认配置项**

在 `static {}` 块中追加两行：

```java
ALL.put("token_access_ttl", "7200");   // ACCESS 2h
ALL.put("token_refresh_ttl", "604800"); // REFRESH 7d
```

加在 existing `ALL.put("reasoning_inject", "true");` 之后：

```java
ALL.put("reasoning_inject", "true");
// Token 过期时间
ALL.put("token_access_ttl", "7200");   // ACCESS 2h
ALL.put("token_refresh_ttl", "604800"); // REFRESH 7d
// 推荐排序
ALL.put("rank.recommend.weight.like", "2.0");
```

- [ ] **Step 2: 编译验证**

Run: IDE Build → expected success

---

### Task 3: UserTokenMapper 新增查询

**Files:**
- Modify: `lblog-server/src/main/java/com/yang/lblogserver/auth/mapper/UserTokenMapper.java:30-33`
- Modify: `lblog-server/src/main/resources/com/yang/lblogserver/auth/mapper/UserTokenMapper.xml:96-100`

- [ ] **Step 1: UserTokenMapper.java 新增方法**

在 `countValidByUserId` 方法后追加：

```java
List<UserToken> selectActiveSessions(@Param("keyword") String keyword,
                                     @Param("offset") int offset,
                                     @Param("limit") int limit);

int countActiveSessions(@Param("keyword") String keyword);

int deleteExpiredTokens();
```

- [ ] **Step 2: UserTokenMapper.xml 新增 ResultMap**

在现有 `BaseResultMap` 之前，新增 `SessionResultMap`：

```xml
<resultMap id="SessionResultMap" type="com.yang.lblogserver.auth.domain.UserToken">
        <id property="id" column="id" />
        <result property="userId" column="user_id" />
        <result property="tokenHash" column="token_hash" />
        <result property="tokenType" column="token_type" />
        <result property="createdAt" column="created_at" />
        <result property="expiresAt" column="expires_at" />
</resultMap>
```

- [ ] **Step 3: UserTokenMapper.xml 新增 3 个查询**

在 `countValidByUserId` 之后，`</mapper>` 之前追加：

```xml
<select id="selectActiveSessions" resultMap="SessionResultMap">
    SELECT ut.id, ut.user_id, u.username, u.nickname,
           ut.token_type, ut.token_hash, ut.created_at, ut.expires_at
    FROM user_tokens ut
    JOIN users u ON u.id = ut.user_id
    WHERE ut.revoked = 0
      AND ut.expires_at > NOW()
      AND u.deleted_at IS NULL
    <if test="keyword != null and keyword != ''">
      AND u.username LIKE CONCAT('%', #{keyword}, '%')
    </if>
    ORDER BY ut.created_at DESC
    LIMIT #{limit} OFFSET #{offset}
</select>

<select id="countActiveSessions" resultType="int">
    SELECT COUNT(*)
    FROM user_tokens ut
    JOIN users u ON u.id = ut.user_id
    WHERE ut.revoked = 0
      AND ut.expires_at > NOW()
      AND u.deleted_at IS NULL
    <if test="keyword != null and keyword != ''">
      AND u.username LIKE CONCAT('%', #{keyword}, '%')
    </if>
</select>

<delete id="deleteExpiredTokens">
    DELETE FROM user_tokens WHERE expires_at &lt; NOW()
</delete>
```

- [ ] **Step 4: 编译验证**

Run: IDE Build → expected success

---

### Task 4: TokenGenerator 改造为动态 TTL

**Files:**
- Modify: `lblog-server/src/main/java/com/yang/lblogserver/auth/security/util/TokenGenerator.java:18-49`

- [ ] **Step 1: 注入 SiteConfigCacheService 并改为动态读取 TTL**

```java
package com.yang.lblogserver.auth.security.util;

import com.yang.lblogserver.site.service.SiteConfigCacheService;
import lombok.Data;
import lombok.experimental.Accessors;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;

@Component
public class TokenGenerator {

    private static final long DEFAULT_ACCESS_TTL = 7200;
    private static final long DEFAULT_REFRESH_TTL = 604800;

    private final SiteConfigCacheService configService;

    @Value("${lblog.token.token-byte-size:32}")
    private int tokenByteSize;

    private final SecureRandom secureRandom = new SecureRandom();

    public TokenGenerator(SiteConfigCacheService configService) {
        this.configService = configService;
    }

    public TokenPairRaw generate() {
        String rawAccessToken = generateRandomToken();
        String rawRefreshToken = generateRandomToken();

        String accessHash = hash(rawAccessToken);
        String refreshHash = hash(rawRefreshToken);

        long accessTtl = getConfigLong("token_access_ttl", DEFAULT_ACCESS_TTL);
        long refreshTtl = getConfigLong("token_refresh_ttl", DEFAULT_REFRESH_TTL);

        LocalDateTime accessExpiresAt = LocalDateTime.now().plusSeconds(accessTtl);
        LocalDateTime refreshExpiresAt = LocalDateTime.now().plusSeconds(refreshTtl);

        return new TokenPairRaw()
                .setRawAccessToken(rawAccessToken)
                .setRawRefreshToken(rawRefreshToken)
                .setAccessHash(accessHash)
                .setRefreshHash(refreshHash)
                .setAccessExpiresAt(accessExpiresAt)
                .setRefreshExpiresAt(refreshExpiresAt);
    }
    // ... hash(), generateRandomToken(), bytesToHex(), TokenPairRaw 不变 ...

    private long getConfigLong(String key, long defaultValue) {
        String val = configService.getConfigValue(key);
        if (val != null) {
            try { return Long.parseLong(val); }
            catch (NumberFormatException e) { /* fall through */ }
        }
        return defaultValue;
    }
}
```

- [ ] **Step 2: 删除旧的 `@Value` 注解字段**

移除 `accessTokenExpireMinutes` 和 `refreshTokenExpireDays` 两个 `@Value` 字段及对应 import。保留 `@Value("${lblog.token.token-byte-size:32}")`。

- [ ] **Step 3: 添加 import**

加到文件头部：
```java
import com.yang.lblogserver.site.service.SiteConfigCacheService;
```

- [ ] **Step 4: 编译验证**

Run: IDE Build → expected success

---

### Task 5: AdminTokenController

**Files:**
- Create: `lblog-server/src/main/java/com/yang/lblogserver/auth/controller/admin/AdminTokenController.java`

- [ ] **Step 1: 创建完整的 AdminTokenController**

```java
package com.yang.lblogserver.auth.controller.admin;

import com.yang.lblogserver.auth.domain.UserToken;
import com.yang.lblogserver.auth.mapper.UserTokenMapper;
import com.yang.lblogserver.auth.vo.BatchOpResult;
import com.yang.lblogserver.auth.vo.SessionVO;
import com.yang.lblogserver.auth.vo.TokenConfigVO;
import com.yang.lblogserver.common.ApiResponse;
import com.yang.lblogserver.common.PageResult;
import com.yang.lblogserver.site.service.SiteConfigCacheService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

@Tag(name = "管理端", description = "会话管理和 Token 配置")
@Validated
@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminTokenController {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final long EXPIRING_THRESHOLD_MINUTES = 30;

    private final UserTokenMapper userTokenMapper;
    private final SiteConfigCacheService configService;

    public AdminTokenController(UserTokenMapper userTokenMapper,
                                SiteConfigCacheService configService) {
        this.userTokenMapper = userTokenMapper;
        this.configService = configService;
    }

    // ── 会话管理 ──

    @Operation(summary = "活跃会话列表", description = "分页返回所有未过期、未吊销的活跃会话")
    @GetMapping("/sessions")
    public ApiResponse<PageResult<SessionVO>> listSessions(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize,
            @RequestParam(required = false) String keyword) {
        int offset = (page - 1) * pageSize;
        List<UserToken> tokens = userTokenMapper.selectActiveSessions(keyword, offset, pageSize);
        int total = userTokenMapper.countActiveSessions(keyword);

        List<SessionVO> list = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();
        for (UserToken t : tokens) {
            SessionVO vo = new SessionVO();
            vo.setId(t.getId());
            vo.setUserId(t.getUserId());
            // username/nickname via extra columns manually filled after query
            // UserTokenMapper XML JOIN query returns them, but UserToken domain
            // doesn't have username/nickname fields. We use a simple approach:
            // the SessionResultMap in XML only maps token fields; we get user
            // info from the JOIN via manual assignment in Mapper layer.
            //
            // For simplicity, we extend UserToken domain with transient fields.
            // See Task 3 — the domain doesn't change; we use a DTO approach here.
            //
            // Actually: let's query separately or use a different result type.
            // Simpler: add username/nickname as @Transient to UserToken.
            // See note below — we add transient fields in Step 2.
            vo.setUsername(t.getUsername());
            vo.setNickname(t.getNickname());
            vo.setTokenType(t.getTokenType());
            vo.setTokenPreview(maskHash(t.getTokenHash()));
            vo.setCreatedAt(t.getCreatedAt() != null ? t.getCreatedAt().format(FMT) : null);
            vo.setExpiresAt(t.getExpiresAt() != null ? t.getExpiresAt().format(FMT) : null);
            if (t.getExpiresAt() != null) {
                long minutesToExpire = ChronoUnit.MINUTES.between(now, t.getExpiresAt());
                vo.setExpiringSoon(minutesToExpire < EXPIRING_THRESHOLD_MINUTES);
            }
            list.add(vo);
        }

        return ApiResponse.success(PageResult.of(page, pageSize, total, list));
    }

    @Operation(summary = "吊销单条 token")
    @DeleteMapping("/sessions/{id}")
    public ApiResponse<?> revokeSession(@PathVariable Long id) {
        // Token 不存在时按幂等处理：不报错
        userTokenMapper.revokeById(id);
        return ApiResponse.success(null);
    }

    @Operation(summary = "踢用户下线", description = "吊销该用户所有有效 token")
    @DeleteMapping("/sessions/user/{userId}")
    public ApiResponse<BatchOpResult> kickUser(@PathVariable Long userId) {
        int revoked = userTokenMapper.revokeAllByUserId(userId);
        return ApiResponse.success(new BatchOpResult(revoked));
    }

    @Operation(summary = "清理过期 token")
    @DeleteMapping("/sessions/cleanup")
    public ApiResponse<BatchOpResult> cleanup() {
        int deleted = userTokenMapper.deleteExpiredTokens();
        return ApiResponse.success(new BatchOpResult(deleted));
    }

    // ── Token 配置 ──

    @Operation(summary = "获取 Token 配置")
    @GetMapping("/token-config")
    public ApiResponse<TokenConfigVO> getTokenConfig() {
        TokenConfigVO vo = new TokenConfigVO();
        vo.setAccessTtl(getConfigLong("token_access_ttl", 7200L));
        vo.setRefreshTtl(getConfigLong("token_refresh_ttl", 604800L));
        return ApiResponse.success(vo);
    }

    @Operation(summary = "更新 Token 配置", description = "新签发的 token 即时生效，已登录用户不受影响")
    @PutMapping("/token-config")
    public ApiResponse<?> updateTokenConfig(@Valid @RequestBody TokenConfigVO vo) {
        configService.updateConfigValue("token_access_ttl", String.valueOf(vo.getAccessTtl()));
        configService.updateConfigValue("token_refresh_ttl", String.valueOf(vo.getRefreshTtl()));
        return ApiResponse.success(null);
    }

    // ── helpers ──

    private String maskHash(String hash) {
        if (hash == null || hash.length() < 8) return "****";
        return hash.substring(0, 8) + "****";
    }

    private long getConfigLong(String key, long defaultValue) {
        String val = configService.getConfigValue(key);
        if (val != null) {
            try { return Long.parseLong(val); }
            catch (NumberFormatException e) { /* fall through */ }
        }
        return defaultValue;
    }
}
```

Wait — the UserToken domain class doesn't have `username`/`nickname` transient fields. The JOIN query returns them but they can't be mapped to UserToken. We need to either:
1. Add `@Transient` fields to `UserToken.java` (simpler)
2. Create a new DTO class

Let's go with option 1 — add transient fields.

**Also: UserTokenMapper needs a `revokeById` method.** Let's add that in Task 3.

Let me revise the plan to account for these missing pieces.

---

**Revised Task 3** must also include:
- In `UserToken.java`: add transient `username`, `nickname` fields
- In `UserTokenMapper.java`: add `int revokeById(@Param("id") Long id);`
- In `UserTokenMapper.xml`: add `revokeById` update
- In `UserTokenMapper.xml` SessionResultMap: map `username`, `nickname` columns

**Revised Task 5 (the controller above)** needs adjustment to use those transient fields.

Let me rewrite Tasks 3 and 5 properly.

---

### Task 3 (Revised): UserToken domain + Mapper

**Files:**
- Modify: `lblog-server/src/main/java/com/yang/lblogserver/auth/domain/UserToken.java:15-16`
- Modify: `lblog-server/src/main/java/com/yang/lblogserver/auth/mapper/UserTokenMapper.java:30-33`
- Modify: `lblog-server/src/main/resources/com/yang/lblogserver/auth/mapper/UserTokenMapper.xml:96-100`

- [ ] **Step 1: UserToken.java 添加 transient 字段**

在 `private String replacedBy;` 之后追加：

```java
/**
 * JOIN 查询时的用户名（非持久化）
 */
private transient String username;

/**
 * JOIN 查询时的昵称（非持久化）
 */
private transient String nickname;
```

由于使用了 `@Data`，Lombok 会自动生成 getter/setter。

- [ ] **Step 2: UserTokenMapper.java 新增方法**

在 `deleteExpired` 方法后追加：

```java
List<UserToken> selectActiveSessions(@Param("keyword") String keyword,
                                     @Param("offset") int offset,
                                     @Param("limit") int limit);

int countActiveSessions(@Param("keyword") String keyword);

int deleteExpiredTokens();

int revokeById(@Param("id") Long id);
```

- [ ] **Step 3: UserTokenMapper.xml 新增 ResultMap + SQL**

在 `</resultMap>` 的 `BaseResultMap` 之后新增：

```xml
<resultMap id="SessionResultMap" type="com.yang.lblogserver.auth.domain.UserToken">
        <id property="id" column="id" />
        <result property="userId" column="user_id" />
        <result property="tokenHash" column="token_hash" />
        <result property="tokenType" column="token_type" />
        <result property="createdAt" column="created_at" />
        <result property="expiresAt" column="expires_at" />
        <result property="username" column="username" />
        <result property="nickname" column="nickname" />
</resultMap>
```

在 `countValidByUserId` 之后，`</mapper>` 之前追加 4 个查询：

```xml
<select id="selectActiveSessions" resultMap="SessionResultMap">
    SELECT ut.id, ut.user_id, u.username, u.nickname,
           ut.token_type, ut.token_hash, ut.created_at, ut.expires_at
    FROM user_tokens ut
    JOIN users u ON u.id = ut.user_id
    WHERE ut.revoked = 0
      AND ut.expires_at > NOW()
      AND u.deleted_at IS NULL
    <if test="keyword != null and keyword != ''">
      AND u.username LIKE CONCAT('%', #{keyword}, '%')
    </if>
    ORDER BY ut.created_at DESC
    LIMIT #{limit} OFFSET #{offset}
</select>

<select id="countActiveSessions" resultType="int">
    SELECT COUNT(*)
    FROM user_tokens ut
    JOIN users u ON u.id = ut.user_id
    WHERE ut.revoked = 0
      AND ut.expires_at > NOW()
      AND u.deleted_at IS NULL
    <if test="keyword != null and keyword != ''">
      AND u.username LIKE CONCAT('%', #{keyword}, '%')
    </if>
</select>

<delete id="deleteExpiredTokens">
    DELETE FROM user_tokens WHERE expires_at &lt; NOW()
</delete>

<update id="revokeById">
    UPDATE user_tokens SET revoked = 1 WHERE id = #{id} AND revoked = 0
</update>
```

- [ ] **Step 4: 编译验证**

Run: IDE Build → expected success

---

### Task 5: AdminTokenController (Complete)

**Files:**
- Create: `lblog-server/src/main/java/com/yang/lblogserver/auth/controller/admin/AdminTokenController.java`

- [ ] **Step 1: 创建 AdminTokenController.java**

```java
package com.yang.lblogserver.auth.controller.admin;

import com.yang.lblogserver.auth.domain.UserToken;
import com.yang.lblogserver.auth.mapper.UserTokenMapper;
import com.yang.lblogserver.auth.vo.BatchOpResult;
import com.yang.lblogserver.auth.vo.SessionVO;
import com.yang.lblogserver.auth.vo.TokenConfigVO;
import com.yang.lblogserver.common.ApiResponse;
import com.yang.lblogserver.common.PageResult;
import com.yang.lblogserver.site.service.SiteConfigCacheService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

@Tag(name = "管理端", description = "会话管理和 Token 配置")
@Validated
@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminTokenController {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final long EXPIRING_THRESHOLD_MINUTES = 30;

    private final UserTokenMapper userTokenMapper;
    private final SiteConfigCacheService configService;

    public AdminTokenController(UserTokenMapper userTokenMapper,
                                SiteConfigCacheService configService) {
        this.userTokenMapper = userTokenMapper;
        this.configService = configService;
    }

    @Operation(summary = "活跃会话列表", description = "分页返回所有未过期、未吊销的活跃会话")
    @GetMapping("/sessions")
    public ApiResponse<PageResult<SessionVO>> listSessions(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize,
            @RequestParam(required = false) String keyword) {
        int offset = (page - 1) * pageSize;
        List<UserToken> tokens = userTokenMapper.selectActiveSessions(keyword, offset, pageSize);
        int total = userTokenMapper.countActiveSessions(keyword);

        List<SessionVO> list = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();
        for (UserToken t : tokens) {
            SessionVO vo = new SessionVO();
            vo.setId(t.getId());
            vo.setUserId(t.getUserId());
            vo.setUsername(t.getUsername());
            vo.setNickname(t.getNickname());
            vo.setTokenType(t.getTokenType());
            vo.setTokenPreview(maskHash(t.getTokenHash()));
            vo.setCreatedAt(t.getCreatedAt() != null ? t.getCreatedAt().format(FMT) : null);
            vo.setExpiresAt(t.getExpiresAt() != null ? t.getExpiresAt().format(FMT) : null);
            if (t.getExpiresAt() != null) {
                long minutesToExpire = ChronoUnit.MINUTES.between(now, t.getExpiresAt());
                vo.setExpiringSoon(minutesToExpire < EXPIRING_THRESHOLD_MINUTES);
            }
            list.add(vo);
        }
        return ApiResponse.success(PageResult.of(page, pageSize, total, list));
    }

    @Operation(summary = "吊销单条 token")
    @DeleteMapping("/sessions/{id}")
    public ApiResponse<?> revokeSession(@PathVariable Long id) {
        userTokenMapper.revokeById(id);
        return ApiResponse.success(null);
    }

    @Operation(summary = "踢用户下线", description = "吊销该用户所有有效 token")
    @DeleteMapping("/sessions/user/{userId}")
    public ApiResponse<BatchOpResult> kickUser(@PathVariable Long userId) {
        int revoked = userTokenMapper.revokeAllByUserId(userId);
        return ApiResponse.success(new BatchOpResult(revoked));
    }

    @Operation(summary = "清理过期 token")
    @DeleteMapping("/sessions/cleanup")
    public ApiResponse<BatchOpResult> cleanup() {
        int deleted = userTokenMapper.deleteExpiredTokens();
        return ApiResponse.success(new BatchOpResult(deleted));
    }

    @Operation(summary = "获取 Token 配置")
    @GetMapping("/token-config")
    public ApiResponse<TokenConfigVO> getTokenConfig() {
        TokenConfigVO vo = new TokenConfigVO();
        vo.setAccessTtl(getConfigLong("token_access_ttl", 7200L));
        vo.setRefreshTtl(getConfigLong("token_refresh_ttl", 604800L));
        return ApiResponse.success(vo);
    }

    @Operation(summary = "更新 Token 配置")
    @PutMapping("/token-config")
    public ApiResponse<?> updateTokenConfig(@Valid @RequestBody TokenConfigVO vo) {
        configService.updateConfigValue("token_access_ttl", String.valueOf(vo.getAccessTtl()));
        configService.updateConfigValue("token_refresh_ttl", String.valueOf(vo.getRefreshTtl()));
        return ApiResponse.success(null);
    }

    private String maskHash(String hash) {
        if (hash == null || hash.length() < 8) return "****";
        return hash.substring(0, 8) + "****";
    }

    private long getConfigLong(String key, long defaultValue) {
        String val = configService.getConfigValue(key);
        if (val != null) {
            try { return Long.parseLong(val); }
            catch (NumberFormatException e) { /* fall through */ }
        }
        return defaultValue;
    }
}
```

- [ ] **Step 2: 编译验证**

Run: IDE Build → expected success

---

### Task 6: 前端类型定义 + API 函数

**Files:**
- Modify: `lblog-web/src/services/api.ts:559-564`
- Modify: `lblog-web/src/types/index.ts` (追加到末尾)

- [ ] **Step 1: types/index.ts 追加类型**

```typescript
// Token 管理
export interface SessionInfo {
  id: number;
  userId: number;
  username: string;
  nickname: string;
  tokenType: string;
  tokenPreview: string;
  createdAt: string;
  expiresAt: string;
  expiringSoon: boolean;
}

export interface BatchOpResult {
  count: number;
}

export interface TokenConfig {
  accessTtl: number;
  refreshTtl: number;
}
```

- [ ] **Step 2: api.ts 追加 API 函数**

在文件末尾追加：

```typescript
// ---- Token 管理 ----

export async function getSessions(params?: {
  page?: number;
  pageSize?: number;
  keyword?: string;
}): Promise<ApiResponse<PageResult<SessionInfo>>> {
  return request<PageResult<SessionInfo>>(`/api/v1/admin/sessions${buildQuery(params as Record<string, string | number | undefined>)}`);
}

export async function revokeSession(id: number): Promise<ApiResponse<null>> {
  return request<null>(`/api/v1/admin/sessions/${id}`, { method: 'DELETE' });
}

export async function kickUser(userId: number): Promise<ApiResponse<BatchOpResult>> {
  return request<BatchOpResult>(`/api/v1/admin/sessions/user/${userId}`, { method: 'DELETE' });
}

export async function cleanupTokens(): Promise<ApiResponse<BatchOpResult>> {
  return request<BatchOpResult>('/api/v1/admin/sessions/cleanup', { method: 'DELETE' });
}

export async function getTokenConfig(): Promise<ApiResponse<TokenConfig>> {
  return request<TokenConfig>('/api/v1/admin/token-config');
}

export async function updateTokenConfig(data: TokenConfig): Promise<ApiResponse<null>> {
  return request<null>('/api/v1/admin/token-config', {
    method: 'PUT',
    body: JSON.stringify(data),
  });
}
```

---

### Task 7: 前端 SessionManage 页面

**Files:**
- Create: `lblog-web/src/pages/admin/SessionManage.tsx`

- [ ] **Step 1: 创建 SessionManage.tsx**

```tsx
import { useState, useEffect, useCallback } from 'react';
import {
  Card, Input, Button, Table, Tag, message, Modal,
  Typography, Space, Pagination, InputNumber, Form, Row, Col, Divider
} from 'antd';
import {
  ReloadOutlined, LogoutOutlined, UserDeleteOutlined, DeleteOutlined
} from '@ant-design/icons';
import type { SessionInfo, TokenConfig } from '../../services/api';
import {
  getSessions, revokeSession, kickUser, cleanupTokens,
  getTokenConfig, updateTokenConfig,
} from '../../services/api';

const { Title, Text } = Typography;

const SessionManage: React.FC = () => {
  const [sessions, setSessions] = useState<SessionInfo[]>([]);
  const [loading, setLoading] = useState(false);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [pageSize] = useState(20);
  const [keyword, setKeyword] = useState('');

  // Token config
  const [config, setConfig] = useState<TokenConfig | null>(null);
  const [configLoading, setConfigLoading] = useState(false);
  const [accessHours, setAccessHours] = useState<number | null>(2);
  const [refreshDays, setRefreshDays] = useState<number | null>(7);

  const loadSessions = useCallback(async () => {
    setLoading(true);
    try {
      const res = await getSessions({ page, pageSize, keyword: keyword || undefined });
      setSessions(res.data.list);
      setTotal(res.data.total);
    } catch (e: unknown) {
      message.error((e as Error).message || '加载失败');
    } finally {
      setLoading(false);
    }
  }, [page, pageSize, keyword]);

  const loadConfig = useCallback(async () => {
    try {
      const res = await getTokenConfig();
      setConfig(res.data);
      setAccessHours(Math.round(res.data.accessTtl / 3600));
      setRefreshDays(Math.round(res.data.refreshTtl / 86400));
    } catch { /* ignore */ }
  }, []);

  useEffect(() => { loadSessions(); }, [loadSessions]);
  useEffect(() => { loadConfig(); }, [loadConfig]);

  const handleRevoke = async (id: number, name: string) => {
    Modal.confirm({
      title: '确认吊销',
      content: `确定要吊销「${name}」的这条 token 吗？`,
      okText: '吊销',
      okType: 'danger',
      onOk: async () => {
        try {
          await revokeSession(id);
          message.success('已吊销');
          loadSessions();
        } catch (e: unknown) {
          message.error((e as Error).message || '吊销失败');
        }
      },
    });
  };

  const handleKick = async (userId: number, name: string) => {
    Modal.confirm({
      title: '确认踢下线',
      content: `确定要将用户「${name}」踢下线吗？会吊销该用户所有有效 token。`,
      okText: '踢下线',
      okType: 'danger',
      onOk: async () => {
        try {
          const res = await kickUser(userId);
          message.success(`已踢下线，吊销了 ${res.data.count} 条 token`);
          loadSessions();
        } catch (e: unknown) {
          message.error((e as Error).message || '操作失败');
        }
      },
    });
  };

  const handleCleanup = async () => {
    Modal.confirm({
      title: '确认清理',
      content: '确定要清理所有已过期的 token 记录吗？',
      okText: '清理',
      okType: 'danger',
      onOk: async () => {
        try {
          const res = await cleanupTokens();
          message.success(`已清理 ${res.data.count} 条过期 token`);
        } catch (e: unknown) {
          message.error((e as Error).message || '清理失败');
        }
      },
    });
  };

  const handleSaveConfig = async () => {
    if (accessHours == null || refreshDays == null) return;
    setConfigLoading(true);
    try {
      const newConfig: TokenConfig = {
        accessTtl: accessHours * 3600,
        refreshTtl: refreshDays * 86400,
      };
      await updateTokenConfig(newConfig);
      setConfig(newConfig);
      message.success('配置已更新，新签发的 token 将使用新的过期时间');
    } catch (e: unknown) {
      message.error((e as Error).message || '保存失败');
    } finally {
      setConfigLoading(false);
    }
  };

  const columns = [
    {
      title: '用户', key: 'user', width: 160,
      render: (_: unknown, r: SessionInfo) => (
        <div>
          <Text strong>{r.username}</Text>
          <br />
          <Text type="secondary" style={{ fontSize: 12 }}>{r.nickname}</Text>
        </div>
      ),
    },
    {
      title: '类型', dataIndex: 'tokenType', key: 'tokenType', width: 90,
      render: (t: string) => <Tag color={t === 'ACCESS' ? 'blue' : 'green'}>{t}</Tag>,
    },
    {
      title: 'Token', dataIndex: 'tokenPreview', key: 'tokenPreview', width: 160,
      render: (v: string) => <Text code>{v}</Text>,
    },
    {
      title: '登录时间', dataIndex: 'createdAt', key: 'createdAt', width: 160,
    },
    {
      title: '过期时间', dataIndex: 'expiresAt', key: 'expiresAt', width: 160,
      render: (v: string, r: SessionInfo) => (
        <Text type={r.expiringSoon ? 'danger' : undefined}>{v}</Text>
      ),
    },
    {
      title: '状态', key: 'status', width: 100,
      render: (_: unknown, r: SessionInfo) => (
        <Tag color={r.expiringSoon ? 'orange' : 'green'}>
          {r.expiringSoon ? '即将过期' : '正常'}
        </Tag>
      ),
    },
    {
      title: '操作', key: 'action', width: 180,
      render: (_: unknown, r: SessionInfo) => (
        <Space>
          <Button type="link" size="small" danger icon={<LogoutOutlined />}
            onClick={() => handleRevoke(r.id, r.username)}>
            吊销
          </Button>
          <Button type="link" size="small" danger icon={<UserDeleteOutlined />}
            onClick={() => handleKick(r.userId, r.username)}>
            踢下线
          </Button>
        </Space>
      ),
    },
  ];

  return (
    <div style={{ padding: 24 }}>
      <Title level={4} style={{ marginBottom: 16 }}>会话管理</Title>

      {/* Token 配置面板 */}
      <Card title="Token 过期时间配置" style={{ marginBottom: 24 }}>
        <Row gutter={24} align="middle">
          <Col>
            <Text>ACCESS 过期：</Text>
            <InputNumber min={0.08} max={24} step={0.5} value={accessHours}
              onChange={v => setAccessHours(v as number | null)}
              style={{ width: 100, marginLeft: 8 }} /> 小时
          </Col>
          <Col>
            <Text>REFRESH 过期：</Text>
            <InputNumber min={0.5} max={30} step={1} value={refreshDays}
              onChange={v => setRefreshDays(v as number | null)}
              style={{ width: 100, marginLeft: 8 }} /> 天
          </Col>
          <Col>
            <Button type="primary" loading={configLoading} onClick={handleSaveConfig}>
              保存配置
            </Button>
          </Col>
        </Row>
        {config && (
          <Text type="secondary" style={{ display: 'block', marginTop: 12 }}>
            当前：ACCESS {Math.round(config.accessTtl / 3600)}h / REFRESH {Math.round(config.refreshTtl / 86400)}d
          </Text>
        )}
      </Card>

      {/* 活跃会话表格 */}
      <Card
        title={`活跃会话（${total}）`}
        extra={
          <Space>
            <Input.Search placeholder="搜索用户名" allowClear style={{ width: 200 }}
              onSearch={v => { setKeyword(v); setPage(1); }} />
            <Button icon={<DeleteOutlined />} danger onClick={handleCleanup}>
              清理过期 Token
            </Button>
            <Button icon={<ReloadOutlined />} onClick={loadSessions}>
              刷新
            </Button>
          </Space>
        }
      >
        <Table
          dataSource={sessions}
          columns={columns}
          rowKey="id"
          loading={loading}
          pagination={false}
          locale={{ emptyText: '暂无活跃会话' }}
        />
        {total > pageSize && (
          <div style={{ textAlign: 'center', marginTop: 16 }}>
            <Pagination
              current={page} pageSize={pageSize} total={total}
              onChange={p => setPage(p)}
              showTotal={t => `共 ${t} 条`}
            />
          </div>
        )}
      </Card>
    </div>
  );
};

export default SessionManage;
```

---

### Task 8: 前端路由注册 + 菜单

**Files:**
- Modify: `lblog-web/src/App.tsx:36-37`
- Modify: `lblog-web/src/App.tsx:87-88`
- Modify: `lblog-web/src/pages/admin/AdminDashboard.tsx:58-62`

- [ ] **Step 1: App.tsx 添加 import**

在现有 import 列表中追加（Line 37 附近）：
```typescript
import SessionManage from './pages/admin/SessionManage';
```

- [ ] **Step 2: App.tsx 添加路由**

在 `/admin/prompts` 路由之后追加（Line 88 附近）：
```tsx
<Route path="/admin/sessions" element={<SessionManage />} />
```

- [ ] **Step 3: AdminDashboard.tsx 添加菜单卡片**

首先添加 icon import，在现有 import 中添加 `SafetyCertificateOutlined`：
```typescript
import { SettingOutlined, PictureOutlined, UserOutlined, FileTextOutlined, FolderOutlined, TagsOutlined, BookOutlined, MessageOutlined, RobotOutlined, SafetyCertificateOutlined } from '@ant-design/icons';
```

然后读取 `features` 数组末尾（约在 comments 条目之后），追加：

```tsx
  {
    key: 'sessions',
    title: '会话管理',
    description: '管理在线会话，踢人下线，配置 Token 过期时间',
    icon: <SafetyCertificateOutlined style={{ fontSize: 32, color: '#eb2f96' }} />,
    path: '/admin/sessions',
  },
```

---

### Task 9: 编译验证 + 集成测试

**Files:** (验证，不修改)

- [ ] **Step 1: 后端编译**

Run: IDE Build → expected success for lblog-server

- [ ] **Step 2: 前端编译**

```bash
cd lblog-web && npx tsc --noEmit
```
Expected: 无类型错误

- [ ] **Step 3: 重启后端**

```bash
# 停止旧进程
netstat -ano | grep ":8099 " | grep LISTENING | awk '{print $5}' | xargs -r powershell -Command "Stop-Process -Id {} -Force"
# 通过 IDE 启动 LblogServerApplication
```

- [ ] **Step 4: 登录并测试 API**

```bash
TOKEN=$(curl -s -X POST "http://localhost:8099/iblogserver/api/v1/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"username":"ekko","password":"admin123"}' | python -c "import sys,json; print(json.load(sys.stdin)['data']['accessToken'])")

# 测试 S1: 会话列表
curl -s "http://localhost:8099/iblogserver/api/v1/admin/sessions" \
  -H "Authorization: Bearer $TOKEN" | python -c "import sys,json; d=json.load(sys.stdin); print(f'code={d[\"code\"]}, total={d[\"data\"][\"total\"]}')"
# Expected: code=0, total>=1

# 测试 S5: 无 token → 401
curl -s "http://localhost:8099/iblogserver/api/v1/admin/sessions" \
  -H "Content-Type: application/json" | python -c "import sys,json; d=json.load(sys.stdin); print(f'code={d[\"code\"]}')"
# Expected: code=401

# 测试 S2: 分页
curl -s "http://localhost:8099/iblogserver/api/v1/admin/sessions?page=1&pageSize=2" \
  -H "Authorization: Bearer $TOKEN" | python -c "import sys,json; d=json.load(sys.stdin); print(f'code={d[\"code\"]}, pageSize={d[\"data\"][\"pageSize\"]}, count={len(d[\"data\"][\"list\"])}')"
# Expected: code=0, <=2 items

# 测试 S3: 搜索
curl -s "http://localhost:8099/iblogserver/api/v1/admin/sessions?keyword=ekko" \
  -H "Authorization: Bearer $TOKEN" | python -c "import sys,json; d=json.load(sys.stdin); print(f'code={d[\"code\"]}, total={d[\"data\"][\"total\"]}')"
# Expected: code=0

# 测试 S4: 无结果搜索
curl -s "http://localhost:8099/iblogserver/api/v1/admin/sessions?keyword=nobodyexists" \
  -H "Authorization: Bearer $TOKEN" | python -c "import sys,json; d=json.load(sys.stdin); print(f'code={d[\"code\"]}, total={d[\"data\"][\"total\"]}')"
# Expected: code=0, total=0

# 测试 S7: 超限 pageSize
curl -s "http://localhost:8099/iblogserver/api/v1/admin/sessions?pageSize=200" \
  -H "Authorization: Bearer $TOKEN" | python -c "import sys,json; d=json.load(sys.stdin); print(f'code={d[\"code\"]}')"
# Expected: code=400

# 测试 T1: 获取配置
curl -s "http://localhost:8099/iblogserver/api/v1/admin/token-config" \
  -H "Authorization: Bearer $TOKEN" | python -c "import sys,json; d=json.load(sys.stdin); print(f'code={d[\"code\"]}, accessTtl={d[\"data\"][\"accessTtl\"]}, refreshTtl={d[\"data\"][\"refreshTtl\"]}')"
# Expected: code=0

# 测试 T2: 设置配置
curl -s -X PUT "http://localhost:8099/iblogserver/api/v1/admin/token-config" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"accessTtl":3600,"refreshTtl":604800}' | python -c "import sys,json; d=json.load(sys.stdin); print(f'code={d[\"code\"]}')"
# Expected: code=0

# 测试 T4: 参数校验失败
curl -s -X PUT "http://localhost:8099/iblogserver/api/v1/admin/token-config" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"accessTtl":100,"refreshTtl":604800}' | python -c "import sys,json; d=json.load(sys.stdin); print(f'code={d[\"code\"]}')"
# Expected: code=400

# 测试 R3: 吊销不存在 id
curl -s -X DELETE "http://localhost:8099/iblogserver/api/v1/admin/sessions/99999" \
  -H "Authorization: Bearer $TOKEN" | python -c "import sys,json; d=json.load(sys.stdin); print(f'code={d[\"code\"]}')"
# Expected: code=0 (幂等)

# 测试 C1: 清理过期 token
curl -s -X DELETE "http://localhost:8099/iblogserver/api/v1/admin/sessions/cleanup" \
  -H "Authorization: Bearer $TOKEN" | python -c "import sys,json; d=json.load(sys.stdin); print(f'code={d[\"code\"]}, deleted={d[\"data\"][\"count\"]}')"
# Expected: code=0
```

- [ ] **Step 5: 前端验证**

浏览器打开 `http://localhost:5173/admin/sessions`，验证：
- Token 配置面板显示当前值
- 活跃会话列表加载
- Token 脱敏预览显示正常
- 吊销按钮弹确认+吊销成功
- 踢下线按钮弹确认+踢下线成功
- 清理过期 token 按钮弹确认+清理成功
- 搜索功能正常
- 分页正常

- [ ] **Step 6: Commit**

```bash
git add -A
git commit -m "feat: add token management (session monitoring + dynamic TTL config)

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>"
```
