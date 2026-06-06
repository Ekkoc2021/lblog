package com.yang.lblogserver.auth.controller;

import com.yang.lblogserver.auth.security.model.LoginUser;
import com.yang.lblogserver.auth.service.PdfService;
import com.yang.lblogserver.auth.vo.PdfAnnotationRequest;
import com.yang.lblogserver.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Tag(name = "PDF 阅读器", description = "标注管理")
@Validated
@RestController
@RequestMapping("/api/v1/pdf")
@PreAuthorize("isAuthenticated()")
public class PdfAnnotationController {

    private final PdfService pdfService;

    public PdfAnnotationController(PdfService pdfService) { this.pdfService = pdfService; }

    private Long getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof LoginUser)
            return ((LoginUser) auth.getPrincipal()).getUserId();
        return null;
    }

    @Operation(summary = "获取某页标注")
    @GetMapping("/{pdfId}/annotations")
    public ApiResponse<String> get(@PathVariable Long pdfId, @RequestParam int page) {
        return ApiResponse.success(pdfService.getAnnotation(pdfId, page, getCurrentUserId()));
    }

    @Operation(summary = "保存某页标注")
    @PutMapping("/{pdfId}/annotations/page/{pageNum}")
    public ApiResponse<?> save(@PathVariable Long pdfId, @PathVariable int pageNum,
                                @Valid @RequestBody PdfAnnotationRequest request) {
        pdfService.saveAnnotation(pdfId, pageNum, getCurrentUserId(), request.getData());
        return ApiResponse.success(null);
    }
}
