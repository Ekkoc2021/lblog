package com.yang.lblogserver.auth.controller;

import com.yang.lblogserver.common.ApiResponse;
import com.yang.lblogserver.auth.domain.Roles;
import com.yang.lblogserver.auth.domain.UserRoles;
import com.yang.lblogserver.auth.domain.Users;
import com.yang.lblogserver.auth.mapper.RolesMapper;
import com.yang.lblogserver.site.mapper.SiteConfigMapper;
import com.yang.lblogserver.site.service.SiteConfigCacheService;
import com.yang.lblogserver.auth.mapper.UserRolesMapper;
import com.yang.lblogserver.auth.mapper.UsersMapper;
import com.yang.lblogserver.auth.security.model.LoginUser;
import com.yang.lblogserver.auth.service.LoginAttemptService;
import com.yang.lblogserver.auth.service.LoginProtectionService;
import com.yang.lblogserver.auth.service.RegisterProtectionService;
import com.yang.lblogserver.auth.service.RoleService;
import com.yang.lblogserver.auth.service.TokenService;
import com.yang.lblogserver.auth.vo.ChangePasswordRequest;
import com.yang.lblogserver.auth.vo.LoginRequest;
import com.yang.lblogserver.auth.vo.RefreshRequest;
import com.yang.lblogserver.auth.vo.RegisterRequest;
import com.yang.lblogserver.auth.vo.TokenPairVO;
import com.yang.lblogserver.auth.vo.UserInfoVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

@Tag(name = "认证", description = "用户登录/登出/信息获取")
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final TokenService tokenService;
    private final UsersMapper usersMapper;
    private final UserRolesMapper userRolesMapper;
    private final RolesMapper rolesMapper;
    private final SiteConfigMapper siteConfigMapper;
    private final SiteConfigCacheService siteConfigCacheService;
    private final PasswordEncoder passwordEncoder;
    private final LoginAttemptService loginAttemptService;
    private final LoginProtectionService loginProtectionService;
    private final RegisterProtectionService registerProtectionService;
    private final RoleService roleService;

    public AuthController(AuthenticationManager authenticationManager, TokenService tokenService,
                          UsersMapper usersMapper, UserRolesMapper userRolesMapper,
                          RolesMapper rolesMapper, SiteConfigMapper siteConfigMapper,
                          SiteConfigCacheService siteConfigCacheService,
                          PasswordEncoder passwordEncoder,
                          LoginAttemptService loginAttemptService,
                          LoginProtectionService loginProtectionService,
                          RegisterProtectionService registerProtectionService,
                          RoleService roleService) {
        this.authenticationManager = authenticationManager;
        this.tokenService = tokenService;
        this.usersMapper = usersMapper;
        this.userRolesMapper = userRolesMapper;
        this.rolesMapper = rolesMapper;
        this.siteConfigMapper = siteConfigMapper;
        this.siteConfigCacheService = siteConfigCacheService;
        this.passwordEncoder = passwordEncoder;
        this.loginAttemptService = loginAttemptService;
        this.loginProtectionService = loginProtectionService;
        this.registerProtectionService = registerProtectionService;
        this.roleService = roleService;
    }

    // ── Cookie helpers ──
    private static final String ACCESS_COOKIE = "lblog_access_token";
    private static final String REFRESH_COOKIE = "lblog_refresh_token";
    private static final int ACCESS_MAX_AGE = 7200;      // 120 min
    private static final int REFRESH_MAX_AGE = 604800;   // 7 days

    private void setAccessCookie(HttpServletResponse response, String token) {
        Cookie c = new Cookie(ACCESS_COOKIE, token);
        c.setHttpOnly(true);
        c.setPath("/");
        c.setMaxAge(ACCESS_MAX_AGE);
        c.setAttribute("SameSite", "Lax");
        response.addCookie(c);
    }

    private void setRefreshCookie(HttpServletResponse response, String token) {
        Cookie c = new Cookie(REFRESH_COOKIE, token);
        c.setHttpOnly(true);
        c.setPath("/api/v1/auth/refresh");
        c.setMaxAge(REFRESH_MAX_AGE);
        c.setAttribute("SameSite", "Lax");
        response.addCookie(c);
    }

    private void clearAuthCookies(HttpServletResponse response) {
        Cookie ac = new Cookie(ACCESS_COOKIE, "");
        ac.setHttpOnly(true); ac.setPath("/"); ac.setMaxAge(0);
        ac.setAttribute("SameSite", "Lax");
        response.addCookie(ac);
        Cookie rc = new Cookie(REFRESH_COOKIE, "");
        rc.setHttpOnly(true); rc.setPath("/api/v1/auth/refresh"); rc.setMaxAge(0);
        rc.setAttribute("SameSite", "Lax");
        response.addCookie(rc);
    }

    private String getRefreshCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie c : cookies) {
                if (REFRESH_COOKIE.equals(c.getName())) return c.getValue();
            }
        }
        return null;
    }

    /**
     * 用户登录。
     *
     * <p>三层防御体系（由宽到严）：</p>
     * <ol>
     *   <li><b>全局熔断</b>：5 秒内全站失败超过 50 次 → 封锁登录 1 小时。用于识别大规模攻击。</li>
     *   <li><b>IP 级限流</b>：同一 IP 2 分钟内失败超过 15 次 → 封锁该 IP 30 分钟。用于阻击单点暴力破解。</li>
     *   <li><b>用户名级限流</b>：同一用户名 5 分钟内失败超过 5 次 → 封锁 5 分钟。用于保护已知账号。</li>
     * </ol>
     * <p>三层任意一层触发均返回 HTTP 429，信息不区分具体原因（防枚举）。</p>
     * <p>登录成功后 IP 和用户名级的失败计数均清零，允许正常用户立即登录。</p>
     */
    @Operation(summary = "用户登录", description = "三层防御：全局熔断 → IP 级限流 → 用户名级限流")
    @PostMapping("/login")
    public ApiResponse<TokenPairVO> login(@RequestBody LoginRequest request,
                                          HttpServletRequest servletRequest,
                                          HttpServletResponse response) {
        String username = request.getUsername();
        String ip = loginProtectionService.getClientIp(servletRequest);

        // 第 1 层：全局熔断（5s > 50 次 → 封锁 1h）
        if (loginProtectionService.isGloballyBlocked()) {
            return ApiResponse.error(429, "登录服务暂时不可用，请 1 小时后再试");
        }

        // 第 2 层：IP 级封锁（2min > 15 次 → 封锁 30min）
        if (loginProtectionService.isIpBlocked(ip)) {
            return ApiResponse.error(429, "登录失败次数过多，请 30 分钟后再试");
        }

        // 第 3 层：用户名级封锁（5min > 5 次 → 封锁 5min）
        if (loginAttemptService.isBlocked(username)) {
            return ApiResponse.error(429, "登录失败次数过多，请 5 分钟后再试");
        }

        try {
            // Spring Security 认证（BCrypt 校验）
            Authentication authenticate = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(username, request.getPassword()));
            LoginUser loginUser = (LoginUser) authenticate.getPrincipal();

            // 登录成功 → 清零失败计数器
            loginAttemptService.resetAttempts(username);
            loginProtectionService.clearIp(ip);

            // 更新登录信息 + 签发双 token
            usersMapper.updateLoginInfo(loginUser.getUserId());
            TokenPairVO tokenPair = tokenService.issueTokenPair(loginUser.getUserId());
            setAccessCookie(response, tokenPair.getAccessToken());
            setRefreshCookie(response, tokenPair.getRefreshToken());
            return ApiResponse.success(tokenPair);
        } catch (DisabledException e) {
            recordFailure(username, ip);
            return ApiResponse.error(403, "账号已被禁用，请联系管理员");
        } catch (BadCredentialsException e) {
            // BadCredentialsException 可能是密码错误也可能是用户名不存在（Spring Security 统一返回）
            // 不透露剩余重试次数，防止攻击者通过次数差异枚举用户
            recordFailure(username, ip);
            return ApiResponse.error(401, "用户名或密码错误");
        }
    }

    /** 记录登录失败：同时计入 IP 级和用户名级计数器，并检查全局熔断阈值 */
    private void recordFailure(String username, String ip) {
        loginProtectionService.recordIpFailure(ip);
        loginProtectionService.checkGlobalRate();
        loginAttemptService.recordFailedAttempt(username);
    }

    @Operation(summary = "用户注册", description = "同一 IP 30 分钟仅可注册 1 次")
    @PostMapping("/register")
    public ApiResponse<TokenPairVO> register(@Valid @RequestBody RegisterRequest request,
                                             HttpServletRequest servletRequest,
                                             HttpServletResponse response) {
        // ——— 注册开关检查 ———
        String regEnabled = siteConfigCacheService.getConfigValue("registration_enabled");
        if (!"true".equals(regEnabled)) {
            return ApiResponse.error(403, "当前暂未开放注册");
        }

        // ——— 防滥用检查 ———

        // 1) 全局熔断（5 秒滑动窗口超过 10 次 → 今日关闭）
        if (registerProtectionService.isGloballyBlocked()) {
            return ApiResponse.error(429, "今日注册人数已满，请明天再试");
        }

        // 2) IP 频率限制（30 分钟仅 1 次）
        String ip = registerProtectionService.getClientIp(servletRequest);
        if (registerProtectionService.isIpBlocked(ip)) {
            return ApiResponse.error(429, "该 IP 注册过于频繁，请 30 分钟后再试");
        }

        // ——— 业务校验 ———

        // 检查用户名是否已存在
        if (usersMapper.findByUsername(request.getUsername()) != null) {
            return ApiResponse.error(400, "用户名已被注册");
        }

        // 检查邮箱是否已存在（如果传了邮箱）
        if (request.getEmail() != null && !request.getEmail().isEmpty()
                && usersMapper.findByEmail(request.getEmail()) != null) {
            return ApiResponse.error(400, "邮箱已被使用");
        }

        // ——— 注册 ———

        // 全局速率计数（需放在注册成功后、滑动窗口才准确）
        if (!registerProtectionService.checkGlobalRate()) {
            return ApiResponse.error(429, "今日注册人数已满，请明天再试");
        }

        // 构造用户
        Users user = new Users();
        user.setUsername(request.getUsername());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setNickname(request.getNickname() != null && !request.getNickname().isEmpty()
                ? request.getNickname() : request.getUsername());
        user.setEmail(request.getEmail());
        user.setRole("user");
        user.setStatus(1);
        usersMapper.insertUser(user);

        // 分配默认角色 "user"
        Roles defaultRole = roleService.getByName("user");
        if (defaultRole != null) {
            UserRoles ur = new UserRoles();
            ur.setUserId(user.getId());
            ur.setRoleId(defaultRole.getId());
            userRolesMapper.insert(ur);
        }

        // 记录 IP
        registerProtectionService.markIpRegistered(ip);

        // 签发 token
        TokenPairVO tokenPair = tokenService.issueTokenPair(user.getId());
        setAccessCookie(response, tokenPair.getAccessToken());
        setRefreshCookie(response, tokenPair.getRefreshToken());
        return ApiResponse.success(tokenPair);
    }

    @Operation(summary = "获取当前用户信息")
    @GetMapping("/me")
    public ApiResponse<UserInfoVO> me() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof LoginUser)) {
            return ApiResponse.error(401, "登录已过期，请重新登录");
        }
        LoginUser loginUser = (LoginUser) authentication.getPrincipal();
        UserInfoVO userInfo = new UserInfoVO();
        userInfo.setId(loginUser.getUserId());
        userInfo.setUsername(loginUser.getUsername());
        userInfo.setNickname(loginUser.getNickname());
        userInfo.setAvatar(loginUser.getAvatar());
        userInfo.setEmail(loginUser.getEmail());
        userInfo.setRole(loginUser.getRole());
        return ApiResponse.success(userInfo);
    }

    @Operation(summary = "退出登录")
    @PostMapping("/logout")
    public ApiResponse<Void> logout(HttpServletResponse response) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getCredentials() instanceof String) {
            String rawToken = (String) authentication.getCredentials();
            tokenService.revokeToken(rawToken);
            SecurityContextHolder.clearContext();
        }
        clearAuthCookies(response);
        return ApiResponse.success(null);
    }

    @Operation(summary = "刷新令牌")
    @PostMapping("/refresh")
    public ApiResponse<TokenPairVO> refresh(HttpServletRequest request,
                                            HttpServletResponse response,
                                            @RequestBody(required = false) RefreshRequest body) {
        // Cookie 优先，body 兜底（App/禁用Cookie的浏览器）
        String rawRefresh = getRefreshCookie(request);
        boolean fromCookie = rawRefresh != null;
        if (!fromCookie && body != null) rawRefresh = body.getRefreshToken();
        if (rawRefresh == null || rawRefresh.isEmpty()) {
            return ApiResponse.error(400, "refreshToken 不能为空");
        }
        TokenPairVO tokenPair = tokenService.refreshAccessToken(rawRefresh);
        if (tokenPair == null) {
            clearAuthCookies(response);
            return ApiResponse.error(401, "refreshToken 无效或已过期");
        }
        setAccessCookie(response, tokenPair.getAccessToken());
        setRefreshCookie(response, tokenPair.getRefreshToken());
        // Cookie 来源：只设 Cookie，不返回 token 在 body（防止 XSS 伪造刷新窃取）
        // Body 来源：返回 token 在 body（App / 禁用 Cookie 的浏览器需要）
        return ApiResponse.success(fromCookie ? null : tokenPair);
    }

    @Operation(summary = "修改密码")
    @PostMapping("/change-password")
    public ApiResponse<Void> changePassword(@RequestBody ChangePasswordRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof LoginUser)) {
            return ApiResponse.error(401, "未登录");
        }
        LoginUser loginUser = (LoginUser) authentication.getPrincipal();
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(loginUser.getUsername(), request.getOldPassword()));
        } catch (BadCredentialsException e) {
            return ApiResponse.error(400, "旧密码错误");
        }
        usersMapper.updatePassword(loginUser.getUserId(), passwordEncoder.encode(request.getNewPassword()));
        tokenService.revokeAllUserTokens(loginUser.getUserId());
        return ApiResponse.success(null);
    }
}
