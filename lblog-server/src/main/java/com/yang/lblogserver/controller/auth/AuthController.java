package com.yang.lblogserver.controller.auth;

import com.yang.lblogserver.common.ApiResponse;
import com.yang.lblogserver.domain.Users;
import com.yang.lblogserver.mapper.SiteConfigMapper;
import com.yang.lblogserver.mapper.UsersMapper;
import com.yang.lblogserver.security.model.LoginUser;
import com.yang.lblogserver.security.service.LoginAttemptService;
import com.yang.lblogserver.security.service.RegisterProtectionService;
import com.yang.lblogserver.service.TokenService;
import com.yang.lblogserver.vo.request.ChangePasswordRequest;
import com.yang.lblogserver.vo.request.LoginRequest;
import com.yang.lblogserver.vo.request.RefreshRequest;
import com.yang.lblogserver.vo.request.RegisterRequest;
import com.yang.lblogserver.vo.response.TokenPairVO;
import com.yang.lblogserver.vo.response.UserInfoVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
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
    private final SiteConfigMapper siteConfigMapper;
    private final PasswordEncoder passwordEncoder;
    private final LoginAttemptService loginAttemptService;
    private final RegisterProtectionService registerProtectionService;

    public AuthController(AuthenticationManager authenticationManager, TokenService tokenService,
                          UsersMapper usersMapper, SiteConfigMapper siteConfigMapper,
                          PasswordEncoder passwordEncoder,
                          LoginAttemptService loginAttemptService,
                          RegisterProtectionService registerProtectionService) {
        this.authenticationManager = authenticationManager;
        this.tokenService = tokenService;
        this.usersMapper = usersMapper;
        this.siteConfigMapper = siteConfigMapper;
        this.passwordEncoder = passwordEncoder;
        this.loginAttemptService = loginAttemptService;
        this.registerProtectionService = registerProtectionService;
    }

    @Operation(summary = "用户登录", description = "5 分钟内连续失败 5 次将被临时封锁")
    @PostMapping("/login")
    public ApiResponse<TokenPairVO> login(@RequestBody LoginRequest request) {
        String username = request.getUsername();

        if (loginAttemptService.isBlocked(username)) {
            return ApiResponse.error(429, "登录失败次数过多，请 5 分钟后再试");
        }

        try {
            Authentication authenticate = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(username, request.getPassword()));
            LoginUser loginUser = (LoginUser) authenticate.getPrincipal();
            loginAttemptService.resetAttempts(username);
            usersMapper.updateLoginInfo(loginUser.getUserId());
            TokenPairVO tokenPair = tokenService.issueTokenPair(loginUser.getUserId());
            return ApiResponse.success(tokenPair);
        } catch (DisabledException e) {
            loginAttemptService.recordFailedAttempt(username);
            return ApiResponse.error(403, "账号已被禁用，请联系管理员");
        } catch (BadCredentialsException e) {
            loginAttemptService.recordFailedAttempt(username);
            int remain = Math.max(0, 5 - loginAttemptService.getAttemptCount(username));
            return ApiResponse.error(401, "用户名或密码错误，剩余 " + remain + " 次机会");
        }
    }

    @Operation(summary = "用户注册", description = "同一 IP 30 分钟仅可注册 1 次")
    @PostMapping("/register")
    public ApiResponse<TokenPairVO> register(@Valid @RequestBody RegisterRequest request,
                                             HttpServletRequest servletRequest) {
        // ——— 注册开关检查 ———
        String regEnabled = siteConfigMapper.selectConfigValue("registration_enabled");
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

        // 记录 IP
        registerProtectionService.markIpRegistered(ip);

        // 签发 token
        TokenPairVO tokenPair = tokenService.issueTokenPair(user.getId());
        return ApiResponse.success(tokenPair);
    }

    @Operation(summary = "获取当前用户信息")
    @GetMapping("/me")
    public ApiResponse<UserInfoVO> me() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof LoginUser)) {
            return ApiResponse.error(401, "未登录或 Token 已过期");
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
    public ApiResponse<Void> logout() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getCredentials() instanceof String) {
            String rawToken = (String) authentication.getCredentials();
            tokenService.revokeToken(rawToken);
            SecurityContextHolder.clearContext();
        }
        return ApiResponse.success(null);
    }

    @Operation(summary = "刷新令牌")
    @PostMapping("/refresh")
    public ApiResponse<TokenPairVO> refresh(@RequestBody RefreshRequest request) {
        if (request.getRefreshToken() == null || request.getRefreshToken().isEmpty()) {
            return ApiResponse.error(400, "refreshToken 不能为空");
        }
        TokenPairVO tokenPair = tokenService.refreshAccessToken(request.getRefreshToken());
        if (tokenPair == null) {
            return ApiResponse.error(401, "refreshToken 无效或已过期");
        }
        return ApiResponse.success(tokenPair);
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
