package com.yang.lblogserver.controller;

import com.yang.lblogserver.common.ApiResponse;
import com.yang.lblogserver.common.PageResult;
import com.yang.lblogserver.security.model.LoginUser;
import com.yang.lblogserver.service.PostsService;
import com.yang.lblogserver.vo.PostDetailVO;
import com.yang.lblogserver.vo.PostVO;
import com.yang.lblogserver.vo.admin.CreatePostRequest;
import com.yang.lblogserver.vo.admin.IdResponse;
import com.yang.lblogserver.vo.admin.SlugCheckResponse;
import com.yang.lblogserver.vo.admin.UpdatePostRequest;
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

@Tag(name = "创作中心", description = "文章管理")
@Validated
@RestController
@RequestMapping("/api/v1/author")
@PreAuthorize("hasRole('AUTHOR')")
public class AuthorPostController {

    private final PostsService postsService;

    public AuthorPostController(PostsService postsService) {
        this.postsService = postsService;
    }

    private Long getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof LoginUser) {
            return ((LoginUser) authentication.getPrincipal()).getUserId();
        }
        return null;
    }

    @Operation(summary = "管理端文章列表", description = "含草稿/私密文章，分页返回")
    @GetMapping("/posts")
    public ApiResponse<PageResult<PostVO>> getAdminPostList(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int pageSize,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String keyword) {
        // 只能看到自己的文章
        Long authorId = getCurrentUserId();
        return ApiResponse.success(postsService.getAdminPostList(page, pageSize, status, keyword, authorId));
    }

    @Operation(summary = "校验 Slug 可用性")
    @GetMapping("/posts/check-slug")
    public ApiResponse<SlugCheckResponse> checkSlug(
            @RequestParam String slug,
            @RequestParam(required = false) Long excludeId) {
        boolean available = postsService.checkSlug(slug, excludeId);
        return ApiResponse.success(new SlugCheckResponse(available));
    }

    @Operation(summary = "获取单篇文章（编辑用）", description = "含正文，不过滤 status")
    @GetMapping("/posts/{id}")
    public ApiResponse<PostDetailVO> getAdminPostById(@PathVariable Long id) {
        PostDetailVO vo = postsService.getAdminPostById(id);
        if (vo == null) return ApiResponse.error(404, "文章不存在");
        if (!vo.getAuthorId().equals(getCurrentUserId())) {
            return ApiResponse.error(403, "只能操作自己的文章");
        }
        return ApiResponse.success(vo);
    }

    @Operation(summary = "创建文章")
    @PostMapping("/posts")
    public ApiResponse<IdResponse> createPost(@Valid @RequestBody CreatePostRequest request) {
        // 检查 slug 是否已存在
        if (!postsService.checkSlug(request.getSlug(), null)) {
            return ApiResponse.error(400, "URL 别名已存在");
        }
        Long id = postsService.createPost(request, getCurrentUserId());
        return ApiResponse.success(new IdResponse(id));
    }

    @Operation(summary = "更新文章")
    @PutMapping("/posts/{id}")
    public ApiResponse<?> updatePost(@PathVariable Long id, @RequestBody UpdatePostRequest request) {
        PostDetailVO vo = postsService.getAdminPostById(id);
        if (vo == null) return ApiResponse.error(404, "文章不存在");
        if (!vo.getAuthorId().equals(getCurrentUserId())) {
            return ApiResponse.error(403, "只能操作自己的文章");
        }
        // 检查 slug 是否已存在（如果传了 slug）
        if (request.getSlug() != null && !postsService.checkSlug(request.getSlug(), id)) {
            return ApiResponse.error(400, "URL 别名已存在");
        }
        postsService.updatePost(id, request);
        return ApiResponse.success(null);
    }

    @Operation(summary = "删除文章（软删除）")
    @DeleteMapping("/posts/{id}")
    public ApiResponse<?> deletePost(@PathVariable Long id) {
        PostDetailVO vo = postsService.getAdminPostById(id);
        if (vo == null) return ApiResponse.error(404, "文章不存在");
        if (!vo.getAuthorId().equals(getCurrentUserId())) {
            return ApiResponse.error(403, "只能操作自己的文章");
        }
        postsService.deletePost(id);
        return ApiResponse.success(null);
    }
}
