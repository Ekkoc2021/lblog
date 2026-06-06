package com.yang.lblogserver.auth.controller.admin;

import com.yang.lblogserver.auth.security.model.LoginUser;
import com.yang.lblogserver.auth.service.AuthorApplicationService;
import com.yang.lblogserver.auth.vo.ApplicationReviewRequest;
import com.yang.lblogserver.auth.vo.ApplicationVO;
import com.yang.lblogserver.common.ApiResponse;
import com.yang.lblogserver.common.PageResult;
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

@Tag(name = "管理端", description = "作者申请审核")
@Validated
@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminApplicationController {

    private final AuthorApplicationService applicationService;

    public AdminApplicationController(AuthorApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    private Long getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof LoginUser) {
            return ((LoginUser) authentication.getPrincipal()).getUserId();
        }
        return null;
    }

    @Operation(summary = "作者申请列表")
    @GetMapping("/applications")
    public ApiResponse<PageResult<ApplicationVO>> list(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int pageSize,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String keyword) {
        return ApiResponse.success(applicationService.getApplicationList(page, pageSize, status, keyword));
    }

    @Operation(summary = "审核作者申请", description = "通过/拒绝/要求补充")
    @PutMapping("/applications/{id}")
    public ApiResponse<?> review(@PathVariable Long id,
                                  @Valid @RequestBody ApplicationReviewRequest request) {
        Long reviewerId = getCurrentUserId();
        try {
            applicationService.review(id, request.getStatus(), request.getFeedback(), reviewerId);
            return ApiResponse.success(null);
        } catch (IllegalArgumentException e) {
            // "申请记录不存在" → 404, validation errors → 400
            int code = e.getMessage() != null && e.getMessage().contains("不存在") ? 404 : 400;
            return ApiResponse.error(code, e.getMessage());
        } catch (IllegalStateException e) {
            return ApiResponse.error(400, e.getMessage());
        }
    }
}
