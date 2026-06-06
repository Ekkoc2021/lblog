package com.yang.lblogserver.auth.controller.user;

import com.yang.lblogserver.auth.domain.AuthorApplication;
import com.yang.lblogserver.auth.security.model.LoginUser;
import com.yang.lblogserver.auth.service.AuthorApplicationService;
import com.yang.lblogserver.auth.vo.ApplicationRequest;
import com.yang.lblogserver.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Tag(name = "用户", description = "作者申请")
@Validated
@RestController
@RequestMapping("/api/v1/user")
@PreAuthorize("isAuthenticated()")
public class UserApplicationController {

    private final AuthorApplicationService applicationService;

    public UserApplicationController(AuthorApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    private Long getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof LoginUser) {
            return ((LoginUser) authentication.getPrincipal()).getUserId();
        }
        return null;
    }

    @Operation(summary = "提交作者申请")
    @PostMapping("/application")
    public ApiResponse<AuthorApplication> submit(@Valid @RequestBody ApplicationRequest request) {
        Long userId = getCurrentUserId();
        try {
            AuthorApplication app = applicationService.submit(userId, request.getReason());
            return ApiResponse.success(app);
        } catch (IllegalStateException e) {
            return ApiResponse.error(400, e.getMessage());
        }
    }

    @Operation(summary = "查询申请状态")
    @GetMapping("/application")
    public ApiResponse<AuthorApplication> getStatus() {
        Long userId = getCurrentUserId();
        AuthorApplication app = applicationService.getByUserId(userId);
        return ApiResponse.success(app);
    }

    @Operation(summary = "补充材料后重新提交")
    @PutMapping("/application")
    public ApiResponse<?> resubmit(@Valid @RequestBody ApplicationRequest request) {
        Long userId = getCurrentUserId();
        try {
            applicationService.resubmit(userId, request.getReason());
            return ApiResponse.success(null);
        } catch (IllegalStateException e) {
            return ApiResponse.error(400, e.getMessage());
        }
    }
}
