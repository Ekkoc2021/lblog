package com.yang.lblogserver.auth.controller;

import com.yang.lblogserver.auth.domain.PdfBookmark;
import com.yang.lblogserver.auth.security.model.LoginUser;
import com.yang.lblogserver.auth.service.PdfService;
import com.yang.lblogserver.auth.vo.PdfBookmarkRequest;
import com.yang.lblogserver.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "PDF 阅读器", description = "书签管理")
@Validated
@RestController
@RequestMapping("/api/v1/pdf")
@PreAuthorize("isAuthenticated()")
public class PdfBookmarkController {

    private final PdfService pdfService;

    public PdfBookmarkController(PdfService pdfService) { this.pdfService = pdfService; }

    private Long getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof LoginUser)
            return ((LoginUser) auth.getPrincipal()).getUserId();
        return null;
    }

    @Operation(summary = "书签列表")
    @GetMapping("/{pdfId}/bookmarks")
    public ApiResponse<List<PdfBookmark>> list(@PathVariable Long pdfId) {
        return ApiResponse.success(pdfService.getBookmarks(pdfId, getCurrentUserId()));
    }

    @Operation(summary = "添加书签")
    @PostMapping("/{pdfId}/bookmarks")
    public ApiResponse<PdfBookmark> add(@PathVariable Long pdfId,
                                         @Valid @RequestBody PdfBookmarkRequest request) {
        return ApiResponse.success(pdfService.addBookmark(pdfId, getCurrentUserId(),
                request.getPageNum(), request.getLabel(), request.getNote()));
    }

    @Operation(summary = "编辑书签（名称+笔记）")
    @PutMapping("/{pdfId}/bookmarks/{id}")
    public ApiResponse<?> update(@PathVariable Long pdfId, @PathVariable Long id,
                                  @Valid @RequestBody PdfBookmarkRequest request) {
        pdfService.updateBookmark(id, request.getLabel(), request.getNote(), getCurrentUserId());
        return ApiResponse.success(null);
    }

    @Operation(summary = "删除书签")
    @DeleteMapping("/{pdfId}/bookmarks/{id}")
    public ApiResponse<?> delete(@PathVariable Long pdfId, @PathVariable Long id) {
        pdfService.deleteBookmark(id, getCurrentUserId());
        return ApiResponse.success(null);
    }
}
