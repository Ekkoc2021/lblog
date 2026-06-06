package com.yang.lblogserver.auth.controller;

import com.yang.lblogserver.auth.domain.PdfFile;
import com.yang.lblogserver.auth.domain.PdfFolder;
import com.yang.lblogserver.auth.security.model.LoginUser;
import com.yang.lblogserver.auth.service.PdfService;
import com.yang.lblogserver.auth.vo.PdfFileVO;
import com.yang.lblogserver.auth.vo.PdfFolderVO;
import com.yang.lblogserver.common.ApiResponse;
import com.yang.lblogserver.storage.PdfStorage;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Tag(name = "PDF 阅读器", description = "文件管理与文件夹")
@Validated
@RestController
@RequestMapping("/api/v1/pdf")
@PreAuthorize("isAuthenticated()")
public class PdfController {

    private final PdfService pdfService;
    private final PdfStorage pdfStorage;

    public PdfController(PdfService pdfService, PdfStorage pdfStorage) {
        this.pdfService = pdfService;
        this.pdfStorage = pdfStorage;
    }

    private Long getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof LoginUser)
            return ((LoginUser) auth.getPrincipal()).getUserId();
        return null;
    }

    @Operation(summary = "上传 PDF")
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<PdfFile> upload(@RequestParam("file") MultipartFile file,
                                        @RequestParam(required = false) Long folderId) {
        Long userId = getCurrentUserId();
        try {
            return ApiResponse.success(pdfService.upload(userId, file, folderId));
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(400, e.getMessage());
        } catch (Exception e) {
            return ApiResponse.error(500, "上传失败");
        }
    }

    @Operation(summary = "PDF 文件流（支持 Range 请求）")
    @GetMapping("/files/{id}/download")
    public ResponseEntity<Resource> download(@PathVariable Long id) {
        PdfFile f = pdfService.getFileById(id);
        Resource resource = pdfStorage.getFile(f.getFilePath());
        if (!resource.exists()) return ResponseEntity.notFound().build();
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .body(resource);
    }

    @Operation(summary = "文件列表")
    @GetMapping("/files")
    public ApiResponse<List<PdfFileVO>> list(@RequestParam(required = false) Long folderId) {
        return ApiResponse.success(pdfService.getFiles(getCurrentUserId(), folderId));
    }

    @Operation(summary = "文件详情")
    @GetMapping("/files/{id}")
    public ApiResponse<PdfFile> detail(@PathVariable Long id) {
        return ApiResponse.success(pdfService.getFile(id, getCurrentUserId()));
    }

    @Operation(summary = "更新文件（重命名/移动）")
    @PutMapping("/files/{id}")
    public ApiResponse<?> update(@PathVariable Long id,
                                  @RequestParam(required = false) String originalName,
                                  @RequestParam(required = false) Long folderId) {
        pdfService.updateFile(id, originalName, folderId);
        return ApiResponse.success(null);
    }

    @Operation(summary = "删除文件")
    @DeleteMapping("/files/{id}")
    public ApiResponse<?> delete(@PathVariable Long id) {
        pdfService.deleteFile(id);
        return ApiResponse.success(null);
    }

    @Operation(summary = "更新总页数")
    @PutMapping("/files/{id}/total-pages")
    public ApiResponse<?> updateTotalPages(@PathVariable Long id, @RequestParam int totalPages) {
        pdfService.updateTotalPages(id, totalPages);
        return ApiResponse.success(null);
    }

    @Operation(summary = "文件夹树")
    @GetMapping("/folders")
    public ApiResponse<List<PdfFolderVO>> folders() {
        return ApiResponse.success(pdfService.getFolderTree(getCurrentUserId()));
    }

    @Operation(summary = "创建文件夹")
    @PostMapping("/folders")
    public ApiResponse<PdfFolder> createFolder(@RequestParam String name,
                                                @RequestParam(required = false) Long parentId) {
        return ApiResponse.success(pdfService.createFolder(getCurrentUserId(), name, parentId));
    }

    @Operation(summary = "更新文件夹")
    @PutMapping("/folders/{id}")
    public ApiResponse<?> updateFolder(@PathVariable Long id,
                                        @RequestParam String name,
                                        @RequestParam(required = false) Long parentId) {
        pdfService.updateFolder(id, name, parentId);
        return ApiResponse.success(null);
    }

    @Operation(summary = "删除文件夹")
    @DeleteMapping("/folders/{id}")
    public ApiResponse<?> deleteFolder(@PathVariable Long id) {
        pdfService.deleteFolder(id);
        return ApiResponse.success(null);
    }
}
