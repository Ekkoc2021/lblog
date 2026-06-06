package com.yang.lblogserver.auth.controller;

import com.yang.lblogserver.auth.domain.PdfProgress;
import com.yang.lblogserver.auth.security.model.LoginUser;
import com.yang.lblogserver.auth.service.PdfService;
import com.yang.lblogserver.auth.vo.PdfProgressRequest;
import com.yang.lblogserver.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Tag(name = "PDF 阅读器", description = "阅读进度")
@Validated
@RestController
@RequestMapping("/api/v1/pdf")
@PreAuthorize("isAuthenticated()")
public class PdfProgressController {

    private final PdfService pdfService;

    public PdfProgressController(PdfService pdfService) { this.pdfService = pdfService; }

    private Long getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof LoginUser)
            return ((LoginUser) auth.getPrincipal()).getUserId();
        return null;
    }

    @Operation(summary = "获取阅读进度")
    @GetMapping("/{pdfId}/progress")
    public ApiResponse<PdfProgress> get(@PathVariable Long pdfId) {
        return ApiResponse.success(pdfService.getProgress(pdfId, getCurrentUserId()));
    }

    @Operation(summary = "更新阅读进度")
    @PutMapping("/{pdfId}/progress")
    public ApiResponse<?> save(@PathVariable Long pdfId,
                                @Valid @RequestBody PdfProgressRequest request) {
        pdfService.saveProgress(pdfId, getCurrentUserId(), request.getPageNum(), request.getScrollTop());
        return ApiResponse.success(null);
    }
}
