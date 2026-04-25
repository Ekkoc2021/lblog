package com.yang.lblogserver.controller;

import com.yang.lblogserver.common.ApiResponse;
import com.yang.lblogserver.service.AuthService;
import com.yang.lblogserver.vo.LoginRequest;
import com.yang.lblogserver.vo.LoginResponse;
import com.yang.lblogserver.vo.UserInfoVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

// TODO: ─────────────────────────────────────────────
// TODO: 临时鉴权控制器，后续需：
// TODO: 1. 接入 JWT 拦截器保护 /auth/me 和 /auth/logout
// TODO: 2. 登录失败次数限制
// TODO: ─────────────────────────────────────────────
@Tag(name = "认证", description = "用户登录/登出/信息获取")
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @Operation(summary = "用户登录", description = "TODO: 临时直接比对密码，后续改为 bcrypt 验证 + JWT 签发")
    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@RequestBody LoginRequest request) {
        // TODO: 参数校验（非空检查）
        LoginResponse response = authService.login(request);
        if (response == null) {
            return ApiResponse.error(401, "用户名或密码错误");
        }
        return ApiResponse.success(response);
    }

    @Operation(summary = "获取当前用户信息", description = "TODO: 临时从固定 token 解析，后续改为 JWT 解析")
    @GetMapping("/me")
    public ApiResponse<UserInfoVO> me(
            @RequestHeader("Authorization") String authorization) {
        // TODO: 提取 token 逻辑可复用，后续抽取为工具方法
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return ApiResponse.error(401, "Token 已过期，请重新登录");
        }
        String token = authorization.substring(7);
        UserInfoVO user = authService.me(token);
        if (user == null) {
            return ApiResponse.error(401, "Token 已过期，请重新登录");
        }
        return ApiResponse.success(user);
    }

    @Operation(summary = "退出登录", description = "TODO: 临时实现，后续接入 Token 黑名单")
    @PostMapping("/logout")
    public ApiResponse<Void> logout(
            @RequestHeader("Authorization") String authorization) {
        // TODO: 临时不做 Token 校验
        if (authorization != null && authorization.startsWith("Bearer ")) {
            authService.logout(authorization.substring(7));
        }
        return ApiResponse.success(null);
    }
}
