package com.yang.lblogserver.password.controller;

import com.yang.lblogserver.auth.security.model.LoginUser;
import com.yang.lblogserver.common.ApiResponse;
import com.yang.lblogserver.common.PageResult;
import com.yang.lblogserver.password.service.PasswordService;
import com.yang.lblogserver.password.vo.CreatePasswordRequest;
import com.yang.lblogserver.password.vo.PasswordVO;
import com.yang.lblogserver.password.vo.UpdatePasswordRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Tag(name = "密码本", description = "个人密码本管理")
@Validated
@RestController
@RequestMapping("/api/v1")
@PreAuthorize("hasAnyRole('ADMIN','AUTHOR')")
public class PasswordController {

    private final PasswordService passwordService;

    public PasswordController(PasswordService passwordService) {
        this.passwordService = passwordService;
    }

    private Long getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof LoginUser user)) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.UNAUTHORIZED, "用户未认证");
        }
        return user.getUserId();
    }

    @GetMapping("/passwords")
    @Operation(summary = "获取密码本列表")
    public ApiResponse<PageResult<PasswordVO>> list(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize,
            @RequestParam(required = false) String keyword) {
        return ApiResponse.success(passwordService.listPasswords(getCurrentUserId(), page, pageSize, keyword));
    }

    @PostMapping("/passwords")
    @Operation(summary = "新增密码记录")
    public ApiResponse<PasswordVO> create(@Valid @RequestBody CreatePasswordRequest req) {
        return ApiResponse.success(passwordService.createPassword(getCurrentUserId(), req));
    }

    @PutMapping("/passwords/{id}")
    @Operation(summary = "更新密码记录")
    public ApiResponse<PasswordVO> update(@PathVariable Long id, @Valid @RequestBody UpdatePasswordRequest req) {
        PasswordVO result = passwordService.updatePassword(getCurrentUserId(), id, req);
        if (result == null) return ApiResponse.error(404, "密码记录不存在");
        return ApiResponse.success(result);
    }

    @DeleteMapping("/passwords/{id}")
    @Operation(summary = "删除密码记录（软删除）")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        passwordService.deletePassword(getCurrentUserId(), id);
        return ApiResponse.success(null);
    }
}
