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
import java.util.Set;

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

    private static final Set<String> VALID_STATUSES = Set.of("active", "revoked", "expired");

    @Operation(summary = "会话列表", description = "分页返回会话，支持按状态筛选：active(活跃)/revoked(已吊销)/expired(已过期)")
    @GetMapping("/sessions")
    public ApiResponse<PageResult<SessionVO>> listSessions(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "active") String status) {
        if (!VALID_STATUSES.contains(status)) {
            status = "active";
        }
        int offset = (page - 1) * pageSize;
        List<UserToken> tokens = userTokenMapper.selectActiveSessions(keyword, status, offset, pageSize);
        int total = userTokenMapper.countActiveSessions(keyword, status);

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
            vo.setRevoked(t.getRevoked() != null && t.getRevoked());
            if (t.getExpiresAt() != null && !vo.isRevoked()) {
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
