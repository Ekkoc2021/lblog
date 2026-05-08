package com.yang.lblogserver.controller;

import com.yang.lblogserver.common.ApiResponse;
import com.yang.lblogserver.common.PageResult;
import com.yang.lblogserver.domain.Images;
import com.yang.lblogserver.security.model.LoginUser;
import com.yang.lblogserver.service.ImagesService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Tag(name = "图片管理", description = "作者图片库管理")
@Validated
@RestController
@RequestMapping("/api/v1/author/images")
public class ImageController {

    private final ImagesService imagesService;

    public ImageController(ImagesService imagesService) {
        this.imagesService = imagesService;
    }

    private Long getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof LoginUser) {
            return ((LoginUser) authentication.getPrincipal()).getUserId();
        }
        return null;
    }

    @Operation(summary = "图片列表", description = "分页查询当前作者的图片库")
    @GetMapping
    public ApiResponse<PageResult<Images>> getImageList(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize) {
        Long userId = getCurrentUserId();
        return ApiResponse.success(imagesService.getImageList(userId, page, pageSize));
    }

    @Operation(summary = "未引用图片列表", description = "分页查询未被任何文章引用的图片")
    @GetMapping("/unreferenced")
    public ApiResponse<PageResult<Images>> getUnreferencedImages(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize) {
        Long userId = getCurrentUserId();
        return ApiResponse.success(imagesService.getUnreferencedImages(userId, page, pageSize));
    }

    @Operation(summary = "删除图片", description = "软删除，已被引用的图片无法删除")
    @DeleteMapping("/{id}")
    public ApiResponse<?> deleteImage(@PathVariable Long id) {
        Long userId = getCurrentUserId();
        try {
            imagesService.deleteImage(id, userId);
            return ApiResponse.success(null);
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(400, e.getMessage());
        }
    }
}
