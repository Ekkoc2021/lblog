package com.yang.lblogserver.auth.controller.admin;

import com.yang.lblogserver.auth.service.PdfService;
import com.yang.lblogserver.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "管理 - PDF 配额")
@Validated
@RestController
@RequestMapping("/api/v1/admin/pdf-quotas")
@PreAuthorize("hasRole('ADMIN')")
public class AdminPdfController {

    private final PdfService pdfService;

    public AdminPdfController(PdfService pdfService) {
        this.pdfService = pdfService;
    }

    @Operation(summary = "所有用户 PDF 用量列表")
    @GetMapping
    public ApiResponse<List<Map<String, Object>>> list() {
        return ApiResponse.success(pdfService.getUserStatsList());
    }

    @Operation(summary = "设置用户配额（字节）")
    @PutMapping("/{userId}/quota")
    public ApiResponse<?> setQuota(@PathVariable Long userId, @RequestParam Long quotaBytes) {
        if (quotaBytes < 1048576) return ApiResponse.error(400, "配额不能小于 1 MB");
        pdfService.setUserQuota(userId, quotaBytes);
        return ApiResponse.success(null);
    }

    @Operation(summary = "设置用户上传开关")
    @PutMapping("/{userId}/allow-upload")
    public ApiResponse<?> setAllowUpload(@PathVariable Long userId, @RequestParam Integer allowUpload) {
        if (allowUpload != 0 && allowUpload != 1) return ApiResponse.error(400, "allowUpload 必须为 0 或 1");
        pdfService.setUserAllowUpload(userId, allowUpload);
        return ApiResponse.success(null);
    }
}
